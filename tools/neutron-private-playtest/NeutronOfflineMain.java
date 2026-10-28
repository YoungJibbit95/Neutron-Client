import java.io.File;
import java.io.FilePermission;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.Permission;
import java.util.*;

/** Java 8 only, local developer harness. Never a session provider or multiplayer launcher. */
public final class NeutronOfflineMain {
    private static final String USER = "NeutronDevOffline";
    private static Path root;
    private static boolean probing = true;

    private static final class OfflineGuard extends SecurityManager {
        private final List<Path> reads;
        private int denials;
        OfflineGuard(List<Path> reads) { this.reads = reads; }
        private synchronized void deny(String operation) {
            denials++;
            // Do not print requested private paths, usernames or URL query strings.
            System.err.println("DEV_GUARD_DENIED " + operation + " thread=" + Thread.currentThread().getName());
            SecurityException exception = new SecurityException("NEUTRON_DEV_OFFLINE: " + operation);
            if (!probing && operation.startsWith("FILE_")) exception.printStackTrace(System.err);
            throw exception;
        }
        @Override public void checkConnect(String host, int port) { deny(port == -1 ? "DNS" : "CONNECT"); }
        @Override public void checkConnect(String host, int port, Object context) { checkConnect(host, port); }
        @Override public void checkListen(int port) { deny("LISTEN"); }
        @Override public void checkAccept(String host, int port) { deny("ACCEPT"); }
        @Override public void checkMulticast(InetAddress address) { deny("MULTICAST"); }
        @Override public void checkExec(String command) { deny("CHILD_PROCESS"); }
        @Override public void checkPermission(Permission p, Object context) { checkPermission(p); }
        @Override public void checkPermission(Permission p) {
            if (p instanceof SocketPermission || p instanceof URLPermission) deny("NETWORK_PERMISSION");
            if (p instanceof RuntimePermission && ("setSecurityManager".equals(p.getName()) ||
                    "preferences".equals(p.getName()))) deny("GUARD_OR_PREFERENCES");
            if (p instanceof FilePermission) {
                String actions = p.getActions();
                Path path;
                String name = p.getName().replace('/', File.separatorChar);
                // URLClassPath.check passes file URL paths as /C:/... on Windows Java 8.
                if (name.matches("^\\\\[A-Za-z]:\\\\.*")) name = name.substring(1);
                if (name.endsWith(File.separator + "*") || name.endsWith(File.separator + "-")) name = name.substring(0, name.length()-2);
                try { path = Paths.get(name).toAbsolutePath().normalize(); }
                catch (RuntimeException invalid) { deny("FILE_PATH"); return; }
                boolean writing = actions.contains("write") || actions.contains("delete");
                if (writing && !path.startsWith(root)) deny("FILE_WRITE_OUTSIDE_TEST");
                if (actions.contains("execute")) deny("FILE_EXECUTE");
                if (actions.contains("read") && !path.startsWith(root)) {
                    boolean allowed = false;
                    for (Path r : reads) if (path.startsWith(r)) { allowed = true; break; }
                    if (!allowed) deny("FILE_READ_OUTSIDE_ALLOWLIST");
                }
            }
        }
    }

    private interface Attempt { void run() throws Exception; }
    private static void denied(String label, Attempt action) throws Exception {
        try { action.run(); }
        catch (SecurityException expected) {
            if (!expected.getMessage().startsWith("NEUTRON_DEV_OFFLINE:")) throw expected;
            System.out.println("DEV_GUARD_PROBE_PASS " + label); return;
        }
        throw new IllegalStateException("Guard failed: " + label);
    }
    private static void probes(OfflineGuard guard) throws Exception {
        denied("direct-connect", () -> guard.checkConnect("192.0.2.1", 80));
        denied("direct-dns", () -> guard.checkConnect("neutron-probe.invalid", -1));
        denied("permission-context", () -> guard.checkPermission(new SocketPermission("*", "connect"), new Object()));
        denied("tcp", () -> { try (Socket s = new Socket()) { s.connect(new InetSocketAddress("192.0.2.1", 80), 50); } });
        denied("dns", () -> InetAddress.getByName("neutron-probe.invalid"));
        denied("http-optifine", () -> new URL("http://optifine.net/version/1.8.9/HD_U.txt").openConnection().connect());
        denied("https", () -> new URL("https://neutron-probe.invalid/").openConnection().connect());
        denied("nio-tcp", () -> { try (SocketChannel c = SocketChannel.open()) { c.connect(new InetSocketAddress("192.0.2.1", 80)); } });
        denied("udp", () -> { try (DatagramSocket s = new DatagramSocket()) { } });
        denied("nio-udp", () -> { try (DatagramChannel c = DatagramChannel.open()) { c.bind(null); } });
        denied("listen", () -> { try (ServerSocket s = new ServerSocket(0)) { } });
        denied("multicast", () -> guard.checkMulticast(InetAddress.getByAddress(new byte[]{(byte)224,0,0,1})));
        denied("child-process", () -> Runtime.getRuntime().exec("neutron-nonexistent-probe.exe"));
        denied("guard-removal", () -> System.setSecurityManager(null));
        denied("outside-read", () -> new java.io.FileInputStream(root.getParent().resolve("guard-nonpersonal-probe.txt").toFile()).close());
        denied("outside-write", () -> new java.io.FileOutputStream(root.getParent().resolve("guard-must-not-create.txt").toFile()).close());
        Files.write(root.resolve("tmp/guard-write-probe.txt"), Collections.singletonList("isolated fixture"));
        System.out.println("DEV_GUARD_PROBES_PASS count=16; write-root=PASS; native-code-is-not-a-hostile-code-sandbox");
    }

    /** Read-only observations are dispatched on the actual client thread. */
    private static void observe() {
        Thread observer = new Thread(() -> {
            String[] last = {""};
            boolean[] labelled = {false};
            while (true) {
                try {
                    Class<?> mcType = Class.forName("net.minecraft.client.Minecraft");
                    Object mc = mcType.getMethod("getMinecraft").invoke(null);
                    if (mc != null) mcType.getMethod("addScheduledTask", Runnable.class).invoke(mc, (Runnable) () -> {
                        try {
                            Object screen = mcType.getField("currentScreen").get(mc);
                            boolean world = mcType.getField("theWorld").get(mc) != null;
                            boolean player = mcType.getField("thePlayer").get(mc) != null;
                            Class<?> pulsar = Class.forName("me.youngjibbit.Pulsar");
                            Object client = pulsar.getField("INSTANCE").get(null);
                            Object mm = pulsar.getMethod("getMm").invoke(client);
                            String scaffold = "NOT_INITIALIZED";
                            if (mm != null) {
                                Object module = mm.getClass().getMethod("getModule", String.class).invoke(mm, "scaffold");
                                scaffold = module == null ? "MISSING" : String.valueOf(module.getClass().getMethod("isToggled").invoke(module));
                                if (!"false".equals(scaffold)) {
                                    System.err.println("DEV_FREEZE_VIOLATION Scaffold=" + scaffold);
                                    mcType.getMethod("shutdown").invoke(mc);
                                }
                                if (!labelled[0]) {
                                    Class.forName("org.lwjgl.opengl.Display").getMethod("setTitle", String.class)
                                        .invoke(null, "NEUTRON DEV OFFLINE - NO AUTH - Singleplayer test");
                                    labelled[0] = true;
                                }
                            }
                            String status = "screen=" + (screen == null ? "NONE" : screen.getClass().getName()) +
                                    " world=" + world + " player=" + player + " Scaffold=" + scaffold;
                            if (!status.equals(last[0])) { System.out.println("DEV_OBSERVATION " + status); last[0] = status; }
                        } catch (Throwable e) { System.err.println("DEV_OBSERVER_ERROR " + e); }
                    });
                    Thread.sleep(500);
                } catch (InterruptedException e) { return; }
                catch (Throwable e) { System.err.println("DEV_OBSERVER_WAIT " + e.getClass().getName()); try {Thread.sleep(1000);} catch (InterruptedException stop) {return;} }
            }
        }, "Neutron DEV observer");
        observer.setDaemon(true); observer.start();
    }

    public static void main(String[] args) throws Throwable {
        if (!System.getProperty("java.version").startsWith("1.8.") || !"64".equals(System.getProperty("sun.arch.data.model")))
            throw new IllegalStateException("Oracle-compatible Java 8 x64 required");
        root = Paths.get(System.getProperty("neutron.dev.root")).toAbsolutePath().normalize();
        String assets = System.getProperty("neutron.dev.assets");
        List<Path> reads = new ArrayList<>();
        for (String p : new String[]{System.getProperty("java.home"), Paths.get(System.getProperty("java.home")).getParent().toString(), System.getProperty("neutron.dev.classes"),
                System.getProperty("neutron.dev.jar"), System.getProperty("org.lwjgl.librarypath"),
                assets + "/indexes", assets + "/objects", System.getenv("SystemRoot")}) reads.add(Paths.get(p).toAbsolutePath().normalize());
        // Assembly inherits Netty's stale INDEX.LIST. URLClassPath probes this absent sibling
        // while enumerating Log4j providers. It is NOT a second classpath input.
        Path staleIndexSibling = Paths.get(System.getProperty("neutron.dev.jar")).resolveSibling("netty-all-4.0.25.Final.jar");
        if (Files.exists(staleIndexSibling)) throw new IllegalStateException("Unexpected indexed classpath sibling; do not load an unpinned second JAR");
        reads.add(staleIndexSibling.toAbsolutePath().normalize());
        OfflineGuard guard = new OfflineGuard(reads);
        System.setSecurityManager(guard);
        System.out.println("NEUTRON DEV OFFLINE / NO AUTH / accessToken=0 fixture / no multiplayer");
        probes(guard);
        probing = false;
        if (args.length == 1 && "--probe".equals(args[0])) return;
        if (args.length != 0) throw new IllegalArgumentException("Harness accepts no game/session overrides");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("DEV_PROCESS_SHUTDOWN guardDenials=" + guard.denials), "DEV receipt"));
        String[] gameArgs = {"--version", "Neutron-1.8.9-DEV-OFFLINE", "--username", USER,
                "--uuid", UUID.nameUUIDFromBytes(("OfflinePlayer:" + USER).getBytes("UTF-8")).toString().replace("-", ""),
                "--accessToken", "0", "--userType", "legacy", "--userProperties", "{}", "--profileProperties", "{}",
                "--gameDir", root.resolve("game").toString(), "--resourcePackDir", root.resolve("game/resourcepacks").toString(),
                "--assetsDir", assets, "--assetIndex", "1.8", "--width", "1100", "--height", "700"};
        // Let game static initialization occur on its launching thread, never in the observer.
        Class.forName("net.minecraft.client.Minecraft");
        observe();
        try { Class.forName("net.minecraft.client.main.Main").getMethod("main", String[].class).invoke(null, (Object)gameArgs); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
}
