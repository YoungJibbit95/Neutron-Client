package me.youngjibbit.release;

import org.junit.Test;
import static org.junit.Assert.*;

/** Runtime contracts independent of target JAR existence; repeated on the packed classpath. */
public class RelP02AssemblyContractTest {
    @Test public void gsonAndAuthlibPropertyAdapterExecuteWithoutAnAccount() throws Exception {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().registerTypeAdapter(
                com.mojang.authlib.properties.PropertyMap.class,
                new com.mojang.authlib.properties.PropertyMap.Serializer()).create();
        com.mojang.authlib.properties.PropertyMap properties = gson.fromJson("{}", com.mojang.authlib.properties.PropertyMap.class);
        assertTrue(properties.isEmpty()); assertEquals("[]", gson.toJson(properties));
        Class<?> auth = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService", false, getClass().getClassLoader());
        assertNotNull(auth.getConstructor(java.net.Proxy.class, String.class));
        assertNotNull(auth.getMethod("createUserAuthentication", com.mojang.authlib.Agent.class));
    }

    @Test public void log4jDiscoversRealPluginsAndSlf4jBinding() {
        org.apache.logging.log4j.core.config.plugins.util.PluginManager plugins =
                new org.apache.logging.log4j.core.config.plugins.util.PluginManager("Core");
        plugins.collectPlugins();
        assertNotNull("packed Log4j plugin index", plugins.getPluginType("console"));
        assertNotNull(plugins.getPluginType("patternlayout"));
        assertEquals("org.apache.logging.slf4j.Log4jLoggerFactory", org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName());
    }
}
