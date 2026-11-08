package me.youngjibbit.core.combat.ai;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Inert until explicit user consent. No global registration, automatic sampling,
 * listener, upload or telemetry. A future opt-in UI must feed REAL physical mouse
 * samples only, after verifying that no Aura/other automated rotation was active.
 */
public final class NeutronAiManualRecorder {
    public enum Origin { MANUAL_PHYSICAL_MOUSE, AI_INFERENCE, ORDINARY_GAME_EVENT }
    /** Identity-only lifetime token. Never serialize account/world/network objects. */
    public static final class Scope {
        public final Object account, world, player, connection, target;
        public final long connectionEpoch, generation;
        public Scope(Object account, Object world, Object player, Object connection,
                     Object target, long connectionEpoch, long generation) {
            this.account=account;this.world=world;this.player=player;
            this.connection=connection;this.target=target;
            this.connectionEpoch=connectionEpoch;this.generation=generation;
        }
        boolean usable() {
            return account!=null && world!=null && player!=null && connection!=null
                    && target!=null && connectionEpoch>=0 && generation>=0;
        }
        boolean same(Scope s) {
            return s!=null && account==s.account && world==s.world
                    && player==s.player && connection==s.connection
                    && target==s.target && connectionEpoch==s.connectionEpoch
                    && generation==s.generation;
        }
    }

    private static final String PARENT="neutron-ai", DIR="manual-v4", PENDING=".pending";
    private final Path root;
    private final Thread owningThread=Thread.currentThread();
    private final List<NeutronAiManualDataset.Frame> samples=new ArrayList<>();
    private final float[] history=new float[NeutronAiCheckpoint.INPUTS];
    private final float[] features=new float[NeutronAiCheckpoint.WIDTH];
    private Scope session;
    private boolean recording;
    private long lastElapsedMs=-1;

    /** gameDataDirectory is a trusted caller-supplied Minecraft data directory. */
    public NeutronAiManualRecorder(Path gameDataDirectory) {
        if(gameDataDirectory==null)throw new IllegalArgumentException("missing game data root");
        root=gameDataDirectory.toAbsolutePath().normalize();
    }
    public boolean isRecording() { return ownThread() && recording; }
    public int bufferedFrames() { return ownThread() ? samples.size() : 0; }

    /** Explicit per-session consent only. Never restore this flag from config/disk. */
    public boolean start(boolean explicitUserConsent,Scope scope) {
        if(!ownThread())return false;
        discard();
        if(!explicitUserConsent || scope==null || !scope.usable())return false;
        session=scope;recording=true;
        return true;
    }

    /** Immediate fail-closed scope change; call from world/account/connection lifecycle. */
    public void onScopeChange(Scope live) {
        if(ownThread() && recording && !session.same(live))discard();
    }

    /**
     * The caller must attest to raw physical mouse input, not pose deltas emitted
     * by Aura or an inference engine. The second independent automation gate
     * prevents accidental relabeling by a future event adapter.
     * One sample is at most 160+2 finite normalized floats in memory.
     */
    public boolean record(Scope live,Origin origin,boolean automatedRotationActive,
                          NeutronAiFeatures.Observation observed,long elapsedMs,
                          double physicalYawDegreesPerSecond,double physicalPitchDegreesPerSecond,
                          boolean physicalClick,boolean manualReaction) {
        if(!ownThread() || !recording)return false;
        if(!session.same(live) || automatedRotationActive || origin==Origin.AI_INFERENCE) {
            discard();return false;
        }
        if(origin!=Origin.MANUAL_PHYSICAL_MOUSE)return false;
        if(samples.size()>=NeutronAiManualDataset.MAX_FRAMES
                || elapsedMs<0 || elapsedMs>NeutronAiManualDataset.MAX_DURATION_MS
                || (lastElapsedMs<0 ? elapsedMs!=0 :
                elapsedMs<=lastElapsedMs || elapsedMs-lastElapsedMs>NeutronAiManualDataset.MAX_GAP_MS)
                || !Double.isFinite(physicalYawDegreesPerSecond)
                || !Double.isFinite(physicalPitchDegreesPerSecond)
                || Math.abs(physicalYawDegreesPerSecond)>NeutronAiCore.MAX_YAW_DPS
                || Math.abs(physicalPitchDegreesPerSecond)>NeutronAiCore.MAX_PITCH_DPS
                || (!physicalClick && !manualReaction && physicalYawDegreesPerSecond==0
                    && physicalPitchDegreesPerSecond==0)) {
            discard();return false;
        }
        if(!NeutronAiFeatures.populate(observed,elapsedMs*1_000_000L,features))return false;
        System.arraycopy(history,0,history,NeutronAiCheckpoint.WIDTH,
                NeutronAiCheckpoint.INPUTS-NeutronAiCheckpoint.WIDTH);
        System.arraycopy(features,0,history,0,features.length);
        float[] label={
                (float)(physicalYawDegreesPerSecond/NeutronAiCore.MAX_YAW_DPS),
                (float)(physicalPitchDegreesPerSecond/NeutronAiCore.MAX_PITCH_DPS)};
        samples.add(new NeutronAiManualDataset.Frame(elapsedMs,physicalClick,
                manualReaction,history,label));
        lastElapsedMs=elapsedMs;
        return true;
    }

    /** Stop without writing (including consent withdrawal). No captured data survives. */
    public void stopAndDiscard() { if(ownThread())discard(); }

    /**
     * Explicit, bounded local commit. No auto-save on lifecycle cancellation.
     * A failed write closes consent, clears RAM and removes our partial file.
     */
    public Path stopAndSave() throws IOException {
        if(!ownThread() || !recording)return null;
        NeutronAiManualDataset dataset;
        try {
            dataset=NeutronAiManualDataset.of(samples);
        } finally { discard(); }
        byte[] bytes=dataset.encode();
        if(bytes.length>NeutronAiManualDataset.MAX_FILE_BYTES)
            throw new IOException("oversize manual dataset");
        Path directory=directory(true);
        Path staging=directory.resolve(PENDING);
        if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("pending recording already exists");
        Path destination=null;
        for(int i=0;i<NeutronAiManualDataset.MAX_FILES;i++) {
            Path candidate=directory.resolve(name(i));
            if(!Files.exists(candidate,LinkOption.NOFOLLOW_LINKS)){destination=candidate;break;}
        }
        if(destination==null)throw new IOException("personal dataset file limit");
        try {
            Files.write(staging,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } catch(AtomicMoveNotSupportedException unsupported) {
            throw new IOException("atomic local filesystem required",unsupported);
        } finally {
            // Never follow a symlink or remove an arbitrary user-selected file.
            Files.deleteIfExists(staging);
        }
    }

    /** Explicit local retrieval; unknown paths and all implicit imports are impossible. */
    public NeutronAiManualDataset read(int slot,boolean explicitConsent) throws IOException {
        if(!ownThread() || !explicitConsent || slot<0 || slot>=NeutronAiManualDataset.MAX_FILES)
            return null;
        Path directory=directory(false);
        if(directory==null)return null;
        Path path=directory.resolve(name(slot));
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return null;
        long length=Files.size(path);
        if(length<=0 || length>NeutronAiManualDataset.MAX_FILE_BYTES)return null;
        return NeutronAiManualDataset.decode(Files.readAllBytes(path));
    }

    /** Revokes in RAM first, then removes only known local versioned owned files. */
    public boolean revokeAndDelete() {
        if(!ownThread())return false;
        discard();
        try {
            Path directory=directory(false);
            if(directory==null)return true;
            for(int i=0;i<NeutronAiManualDataset.MAX_FILES;i++)
                Files.deleteIfExists(directory.resolve(name(i)));
            Files.deleteIfExists(directory.resolve(PENDING));
            return true;
        } catch(IOException failure){return false;}
    }

    private Path directory(boolean create) throws IOException {
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("unsafe game data root");
        Path first=folder(root.resolve(PARENT),create);
        return first==null?null:folder(first.resolve(DIR),create);
    }
    private static Path folder(Path directory,boolean create) throws IOException {
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)) {
            if(!create)return null;
            Files.createDirectory(directory);
        }
        if(Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("unsafe manual recording directory");
        return directory;
    }
    private static String name(int slot) {return String.format(java.util.Locale.ROOT,"demo-%02d.nmd",slot);}
    private boolean ownThread(){return Thread.currentThread()==owningThread;}
    private void discard() {
        recording=false;session=null;lastElapsedMs=-1;
        samples.clear();Arrays.fill(features,0);Arrays.fill(history,0);
    }
}
