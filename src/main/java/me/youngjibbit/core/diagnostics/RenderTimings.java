package me.youngjibbit.core.diagnostics;

import java.util.*;
import java.util.function.LongSupplier;

/** Opt-in, client-thread CPU submission timings. Never enables the vanilla/F3 profiler or waits for the GPU. */
public final class RenderTimings {
    public static final RenderTimings INSTANCE = new RenderTimings(System::nanoTime);
    private final LongSupplier clock;
    private Object profiler;
    private Thread thread;
    private volatile boolean enabled;
    private boolean recording;
    private int depth, overflow, frames;
    private long lastFrame, windowStart;
    private final String[] paths = new String[128];
    private final long[] started = new long[128], children = new long[128];
    private final Map<String, Long> totals = new HashMap<>(), surfaces = new HashMap<>();
    private final Map<String, Long> frameTotals = new HashMap<>(), frameSurfaces = new HashMap<>();
    private final double[] history = new double[60];
    private int historyIndex, historySize;
    public RenderTimings(LongSupplier clock) { this.clock = clock; }

    public void enable(Object profiler) {
        clear(); this.profiler = profiler; thread = Thread.currentThread(); enabled = true;
    }
    public void disable() { enabled = false; clear(); profiler = null; thread = null; }
    private void clear() {
        recording = false; depth = overflow = frames = historyIndex = historySize = 0;
        Arrays.fill(paths, null); totals.clear(); surfaces.clear();frameTotals.clear();frameSurfaces.clear(); lastFrame = windowStart = 0;
    }
    public boolean isEnabled() { return enabled; }
    private boolean current() { return enabled && Thread.currentThread() == thread; }
    public void frame() {
        if (!current()) return;
        long now = clock.getAsLong();
        if (recording) {
            frameTotals.forEach((name,value)->add(totals,name,value));frameSurfaces.forEach((name,value)->add(surfaces,name,value));
            history[historyIndex] = Math.max(0, (now - lastFrame) / 1e6);
            historyIndex = (historyIndex + 1) % history.length; historySize = Math.min(history.length, historySize + 1);
            frames++;
        } else { windowStart = now; recording = true; }
        frameTotals.clear();frameSurfaces.clear();
        lastFrame = now; depth = overflow = 0; Arrays.fill(paths, null);
    }
    public void start(Object source, String name) {
        if (!current() || source != profiler || !recording) return;
        if (depth == paths.length || overflow > 0) { overflow++; return; }
        paths[depth] = depth == 0 ? name : paths[depth - 1] + "/" + name;
        started[depth] = clock.getAsLong(); children[depth] = 0; depth++;
    }
    public void end(Object source) {
        if (!current() || source != profiler || !recording) return;
        if (overflow > 0) { overflow--; return; }
        if (depth == 0) return;
        int i = --depth;
        long elapsed = Math.max(0, clock.getAsLong() - started[i]);
        if (i > 0) children[i - 1] += elapsed;
        String path = paths[i]; paths[i] = null;
        if (path.equals("root/render/gameRenderer")||path.startsWith("root/render/gameRenderer/")) add(frameTotals, path, Math.max(0, elapsed - children[i]));
    }
    private static void add(Map<String, Long> map, String key, long elapsed) {
        if (map.containsKey(key) || map.size() < 256) map.put(key, map.getOrDefault(key, 0L) + elapsed);
    }
    public long surfaceStart() { return current() && recording ? clock.getAsLong() : -1; }
    public void surfaceEnd(String name, long token) {
        if (token >= 0 && current() && recording) add(frameSurfaces, name, Math.max(0, clock.getAsLong() - token));
    }
    public Scope scope(String name) {long token=surfaceStart();return token<0?Scope.NONE:new Scope(this,name,token);}
    public static final class Scope implements AutoCloseable {
        private static final Scope NONE=new Scope(null,null,-1);
        private final RenderTimings owner;private final String name;private final long token;
        private boolean closed;
        private Scope(RenderTimings owner,String name,long token){this.owner=owner;this.name=name;this.token=token;}
        @Override public void close(){if(owner!=null&&!closed){closed=true;owner.surfaceEnd(name,token);}}
    }
    public Snapshot sample() {
        if (!current() || !recording) return Snapshot.EMPTY;
        // Publish completed frames only: a HUD/tick sample cannot divide half of this frame by an older frame count.
        long now = clock.getAsLong();
        double[] graph = new double[historySize];
        for (int i = 0; i < graph.length; i++) graph[i] = history[(historyIndex - historySize + i + history.length) % history.length];
        Snapshot result = new Snapshot(rows(totals, frames), rows(surfaces, frames), graph, frames, (now - windowStart) / 1e6);
        if (frames > 0) { totals.clear(); surfaces.clear(); frames = 0; windowStart = now; }
        return result;
    }
    private static List<Row> rows(Map<String, Long> values, int frames) {
        long sum = 0; for (long value : values.values()) sum += value;
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) rows.add(new Row(entry.getKey(),
                entry.getValue() / 1e6 / Math.max(1, frames), sum == 0 ? 0 : entry.getValue() / (double) sum));
        rows.sort(Comparator.comparingDouble((Row r) -> r.milliseconds).reversed().thenComparing(r -> r.name));
        return Collections.unmodifiableList(rows);
    }
    public static final class Row {
        public final String name;
        public final double milliseconds, share;
        public Row(String name, double milliseconds, double share) { this.name = name; this.milliseconds = milliseconds; this.share = share; }
    }
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(Collections.emptyList(), Collections.emptyList(), new double[0], 0, 0);
        public final List<Row> sections, surfaces;
        public final double[] frameMilliseconds;
        public final int frames;
        public final double windowMilliseconds;
        Snapshot(List<Row> sections, List<Row> surfaces, double[] graph, int frames, double window) {
            this.sections = sections; this.surfaces = surfaces; frameMilliseconds = graph; this.frames = frames; windowMilliseconds = window;
        }
    }
}
