package me.youngjibbit.release;

import org.junit.Test;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import static org.junit.Assert.*;

/** Explicit post-package fixture; never included in Surefire totals.
 * Runtime CP is fixture classes + Assembly + JUnit/Hamcrest ONLY. Artifact inputs
 * below are read as ZIPs, never added to the runtime ClassLoader. */
public class RelP02PackagedAbiCheck {
    private final ClassLoader loader = getClass().getClassLoader();
    private Path jarPath() { return Paths.get(Objects.requireNonNull(System.getProperty("neutron.release.jar"))); }
    private Map<String, List<byte[]>> overlaps() throws Exception {
        String cp = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(System.getProperty("neutron.release.runtimeClasspath")))), "UTF-8").trim();
        Map<String, List<byte[]>> owners = new TreeMap<>();
        for (String path : cp.split(java.io.File.pathSeparator)) {
            assertFalse("test artifact in runtime inventory", Paths.get(path).getFileName().toString().matches("(junit|hamcrest).*"));
            try (JarFile jar = new JarFile(path)) {
                for (JarEntry e : Collections.list(jar.entries())) {
                    String n = e.getName();
                    if (baseClass(n)) owners.computeIfAbsent(n, k -> new ArrayList<>()).add(hash(read(jar.getInputStream(e))));
                }
            }
        }
        owners.entrySet().removeIf(e -> e.getValue().size() < 2);
        assertFalse("missing overlap inputs", owners.isEmpty()); return owners;
    }
    private static boolean baseClass(String n) { return n.endsWith(".class") && !n.startsWith("META-INF/") && !n.equals("module-info.class"); }
    private static byte[] hash(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private static byte[] read(InputStream stream) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray();
        }
    }

    @Test public void everyOverlappingClassHasOnePackedOwnerAndLoadsFromAssembly() throws Exception {
        Map<String, List<byte[]>> overlaps = overlaps(); int different = 0;
        try (JarFile jar = new JarFile(jarPath().toFile())) {
            Map<String, Integer> counts = new HashMap<>();
            for (JarEntry e : Collections.list(jar.entries())) counts.merge(e.getName(), 1, Integer::sum);
            for (Map.Entry<String, List<byte[]>> e : overlaps.entrySet()) {
                String name = e.getKey(); assertEquals(name, Integer.valueOf(1), counts.get(name));
                byte[] packed = read(jar.getInputStream(jar.getJarEntry(name)));
                byte[] packedHash = hash(packed);
                assertTrue("packed bytes have no declared owner: " + name, e.getValue().stream().anyMatch(b -> Arrays.equals(b, packedHash)));
                if (e.getValue().stream().anyMatch(b -> !Arrays.equals(b, e.getValue().get(0)))) different++;
                assertEquals(name, 1, Collections.list(loader.getResources(name)).size());
                Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
                assertEquals(name, jarPath().toRealPath(), Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
                assertArrayEquals(name, packed, read(loader.getResourceAsStream(name)));
            }
        }
        System.out.println("REL_P02 packed overlapping classes=" + overlaps.size() + "; different bytes=" + different);
    }

    @Test public void applicationMemberReferencesToOverlapsResolveExactly() throws Exception {
        Set<String> target = overlaps().keySet(); int references = 0;
        try (JarFile jar = new JarFile(jarPath().toFile())) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                // The application uses desktop OpenGL. Exhaustive reflection on unused
                // dependency/GLES/platform implementations is separately inventoried,
                // including its unresolved results; this is the application ABI gate.
                String caller = entry.getName();
                if (!baseClass(caller) || !(caller.startsWith("me/youngjibbit/")
                        || caller.startsWith("net/minecraft/") || caller.startsWith("net/optifine/")
                        || caller.startsWith("net/minecraftforge/"))) continue;
                try (DataInputStream in = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xCAFEBABE, in.readInt()); in.readUnsignedShort(); in.readUnsignedShort();
                    Object[] cp = pool(in);
                    for (Object value : cp) {
                        if (!(value instanceof int[])) continue;
                        int[] ref = (int[]) value;
                        if (ref[0] < 9 || ref[0] > 11) continue;
                        String owner = (String) cp[((int[]) cp[ref[1]])[1]];
                        if (!target.contains(owner + ".class")) continue;
                        int[] nt = (int[]) cp[ref[2]];
                        String name = (String) cp[nt[1]], descriptor = (String) cp[nt[2]];
                        Class<?> type = Class.forName(owner.replace('/', '.'), false, loader);
                        assertTrue(entry.getName() + " -> " + owner + "." + name + descriptor,
                                resolves(type, ref[0] == 9, name, descriptor, new HashSet<>()));
                        references++;
                    }
                }
            }
        }
        assertTrue("empty ABI scan", references > 0);
        System.out.println("REL_P02 application exact member references resolved=" + references + "; static CP overapproximation, not execution coverage");
    }

    private static Object[] pool(DataInputStream in) throws IOException {
        Object[] cp = new Object[in.readUnsignedShort()];
        for (int i = 1; i < cp.length; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: cp[i] = in.readUTF(); break;
                case 3: case 4: in.readInt(); break;
                case 5: case 6: in.readLong(); i++; break;
                case 7: case 8: case 16: case 19: case 20: cp[i] = new int[]{tag, in.readUnsignedShort()}; break;
                case 9: case 10: case 11: case 12: case 17: case 18: cp[i] = new int[]{tag, in.readUnsignedShort(), in.readUnsignedShort()}; break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                default: throw new IOException("Unsupported constant pool tag " + tag);
            }
        }
        return cp;
    }
    private static boolean resolves(Class<?> type, boolean field, String name, String desc, Set<Class<?>> seen) {
        if (type == null || !seen.add(type)) return false;
        if (field) {
            for (Field f : type.getDeclaredFields()) if (f.getName().equals(name) && descriptor(f.getType()).equals(desc)) return true;
        } else if (name.equals("<init>")) {
            for (Constructor<?> c : type.getDeclaredConstructors()) if (methodDescriptor(c.getParameterTypes(), void.class).equals(desc)) return true;
            return false;
        } else {
            for (Method m : type.getDeclaredMethods()) if (m.getName().equals(name) && methodDescriptor(m.getParameterTypes(), m.getReturnType()).equals(desc)) return true;
        }
        if (resolves(type.getSuperclass(), field, name, desc, seen)) return true;
        for (Class<?> contract : type.getInterfaces()) if (resolves(contract, field, name, desc, seen)) return true;
        return false;
    }
    private static String methodDescriptor(Class<?>[] args, Class<?> result) {
        StringBuilder b = new StringBuilder("("); for (Class<?> a : args) b.append(descriptor(a)); return b.append(')').append(descriptor(result)).toString();
    }
    private static String descriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V"; if (type == boolean.class) return "Z"; if (type == byte.class) return "B";
        if (type == char.class) return "C"; if (type == short.class) return "S"; if (type == int.class) return "I";
        if (type == long.class) return "J"; if (type == float.class) return "F"; return "D";
    }
}
