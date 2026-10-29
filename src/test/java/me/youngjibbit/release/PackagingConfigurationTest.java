package me.youngjibbit.release;

import org.junit.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import static org.junit.Assert.*;

/** Configuration regression; actual JAR checks run separately after package. */
public class PackagingConfigurationTest {
    @Test public void assemblyNamesTheExistingDirectMain() throws Exception {
        Path root = Paths.get(System.getProperty("basedir", "..")).toAbsolutePath();
        Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(root.resolve("pom.xml").toFile());
        // Profile overrides may repeat the plugin without replacing the default entrypoint.
        Node mainClass = (Node) XPathFactory.newInstance().newXPath().evaluate(
                "/project/build/plugins/plugin[artifactId='maven-assembly-plugin']"
                        + "/configuration/archive/manifest/mainClass", pom, XPathConstants.NODE);
        assertNotNull("existing default Assembly entrypoint must remain", mainClass);
        assertEquals("net.minecraft.client.main.Main", mainClass.getTextContent());
    }
}
