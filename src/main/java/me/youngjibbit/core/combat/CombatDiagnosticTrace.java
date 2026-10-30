package me.youngjibbit.core.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Small, bounded, in-memory client diagnostic history.
 * This is intentionally not an anticheat flag detector or a packet interceptor. */
public final class CombatDiagnosticTrace {
    public enum Kind { OPPORTUNITY, PACKET, CONTROLLER, STEP }
    public enum Stage { BEGIN, ADMISSION, HIT_CHECK, SCHEDULER_RESULT, END, ATTEMPT, SELECTED, CANCELLED, HELD, BARRIER, DENIED, LOCAL_ACCEPTED, FAILED, COMPLETED, INTERRUPTED }
    public enum PacketType { NONE, C03, C0A, C02, OTHER }
    public enum Origin { AURA_AUTOMATIC, UNKNOWN }
    public enum Outcome { TRUE, FALSE, UNKNOWN }
    public enum StepState { CURRENT_IN_PROGRESS, COMPLETED, INTERRUPTED, NO_STEP }

    /** No game objects or payloads are retained by records or delayed correlation tokens. */
    public static final class ScalarRecord {
        public final long run, event, action, packet, causeAction, nanos, outerTick, generation, epoch;
        public final int stepIndex;
        public final Kind kind;
        public final Stage stage;
        public final PacketType type;
        public final Origin origin;
        public final Outcome outcome;
        public final StepState stepState;
        public final me.youngjibbit.core.contract.ExecutedStep.Kind stepKind;
        public final Outcome server = Outcome.UNKNOWN;
        public ScalarRecord(long run, long event, long action, long packet, long causeAction, long nanos,
                            long outerTick, long generation, long epoch, int stepIndex, Kind kind, Stage stage,
                            PacketType type, Origin origin, Outcome outcome, StepState stepState,
                            me.youngjibbit.core.contract.ExecutedStep.Kind stepKind) {
            this.run=run;this.event=event;this.action=action;this.packet=packet;this.causeAction=causeAction;
            this.nanos=nanos;this.outerTick=outerTick;this.generation=generation;this.epoch=epoch;
            this.stepIndex=stepIndex;this.kind=kind;this.stage=stage;this.type=type;this.origin=origin;
            this.outcome=outcome;this.stepState=stepState;this.stepKind=stepKind;
        }
    }

    public static final class ActionToken {
        public final long run, action, parentAction;
        public final Origin parentOrigin;
        private ActionToken(long run, long action, long parentAction, Origin parentOrigin) {
            this.run=run;this.action=action;this.parentAction=parentAction;this.parentOrigin=parentOrigin;
        }
    }

    public static final class PacketToken {
        public final long run, action, packet;
        public final Origin origin;
        private PacketToken(long run, long action, long packet, Origin origin) {
            this.run=run;this.action=action;this.packet=packet;this.origin=origin;
        }
    }

    public static final class StepToken {
        public final long run, outerTick, entryGeneration, entryEpoch;
        public final int stepIndex;
        public final me.youngjibbit.core.contract.ExecutedStep.Kind stepKind;
        private StepToken(long run, long outerTick, int stepIndex,
                          me.youngjibbit.core.contract.ExecutedStep.Kind stepKind, long generation, long epoch) {
            this.run=run;this.outerTick=outerTick;this.stepIndex=stepIndex;this.stepKind=stepKind;
            this.entryGeneration=generation;this.entryEpoch=epoch;
        }
    }

    private static long runIds;
    private static synchronized long nextRun() { return ++runIds; }
    private final int capacity;
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final ArrayDeque<ScalarRecord> records = new ArrayDeque<>();
    private long run, event, actionIds, packetIds, activeAction, overwritten, captureFailures, staleTokens;
    private Origin activeOrigin = Origin.UNKNOWN;
    private boolean recording;

    public CombatDiagnosticTrace(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void append(String event) {
        if (event == null || event.isEmpty()) return;
        if (history.size() == capacity) history.removeFirst();
        history.addLast(event);
    }

    /** Newest first. Returning a copy prevents a renderer from mutating the history. */
    public synchronized List<String> recent(int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<String> items = new ArrayList<>(Math.min(limit, history.size()));
        Iterator<String> newest = history.descendingIterator();
        while (newest.hasNext() && items.size() < limit) items.add(newest.next());
        return items;
    }

    public synchronized int size() {
        return history.size();
    }

    public synchronized void startRun() {
        recording=true;run=nextRun();event=actionIds=packetIds=activeAction=0;activeOrigin=Origin.UNKNOWN;
    }
    public synchronized void stop() { recording=false;activeAction=0;activeOrigin=Origin.UNKNOWN; }
    public synchronized boolean isRecording() { return recording; }
    public synchronized long getRunId() { return run; }
    public synchronized long getActiveAction() { return activeAction; }
    public synchronized Origin getActiveOrigin() { return activeOrigin; }
    public synchronized long nextEvent() { return ++event; }
    public synchronized long getOverwrittenCount() { return overwritten; }
    public synchronized long getCaptureFailureCount() { return captureFailures; }
    public synchronized long getStaleTokenCount() { return staleTokens; }
    public synchronized void captureFailed() { captureFailures++; }
    public synchronized boolean current(long tokenRun) {
        if (!recording || tokenRun != run) { staleTokens++;return false; }
        return true;
    }
    public synchronized ActionToken beginOpportunity() {
        if (!recording) return null;
        ActionToken token=new ActionToken(run,++actionIds,activeAction,activeOrigin);
        activeAction=token.action;activeOrigin=Origin.AURA_AUTOMATIC;
        return token;
    }
    public synchronized void endOpportunity(ActionToken token) {
        if (token == null || !current(token.run)) return;
        if (activeAction == token.action) {activeAction=token.parentAction;activeOrigin=token.parentOrigin;}
        else staleTokens++;
    }
    public synchronized PacketToken beginPacket() {
        return recording ? new PacketToken(run,activeAction,++packetIds,activeOrigin) : null;
    }
    public synchronized StepToken beginStep(long outer, int index,
                                           me.youngjibbit.core.contract.ExecutedStep.Kind kind, long generation, long epoch) {
        return recording ? new StepToken(run,outer,index,kind,generation,epoch) : null;
    }
    public synchronized void appendScalar(ScalarRecord record) {
        if (!recording || record == null) return;
        if (records.size() == capacity) { records.removeFirst();overwritten++; }
        records.addLast(record);
    }
    /** Bounded oldest-first scalar snapshot, obtained only by an explicit inspector. */
    public synchronized List<ScalarRecord> scalarSnapshot() { return new ArrayList<>(records); }

    public synchronized void clear() {
        history.clear();
        records.clear();overwritten=captureFailures=staleTokens=0;
        activeAction=0;activeOrigin=Origin.UNKNOWN;
    }
}
