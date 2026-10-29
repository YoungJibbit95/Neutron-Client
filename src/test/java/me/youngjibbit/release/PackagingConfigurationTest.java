package me.youngjibbit.release;

import org.junit.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

/** Configuration regression; actual JAR checks run separately after package. */
public class PackagingConfigurationTest {
    @Test public void assemblyNamesTheExistingDirectMain() throws Exception {
        Path root = Paths.get(System.getProperty("basedir", "..")).toAbsolutePath();
        NodeList plugins = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(root.resolve("pom.xml").toFile()).getElementsByTagName("plugin");
        Element assembly = null;
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if ("maven-assembly-plugin".equals(plugin.getElementsByTagName("artifactId").item(0).getTextContent())) assembly = plugin;
        }
        assertNotNull("existing Assembly packaging must remain", assembly);
        assertEquals("net.minecraft.client.main.Main", assembly.getElementsByTagName("mainClass").item(0).getTextContent());
    }
}
