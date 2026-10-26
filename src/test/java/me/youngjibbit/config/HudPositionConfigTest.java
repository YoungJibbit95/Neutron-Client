package me.youngjibbit.config;
import me.youngjibbit.module.impl.client.ArrayListHUDModule;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.Collections;
import static org.junit.Assert.*;
public class HudPositionConfigTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 @Test public void positionsRoundTripThroughNamedAndAutomaticConfig()throws Exception{
  Path directory=folder.newFolder().toPath();ArrayListHUDModule first=new ArrayListHUDModule();
  ConfigManager manager=new ConfigManager(directory,Collections.singletonList(first),()->false,s->{});manager.initialize(Collections.emptyList());
  first.getNumberSetting("Position X").setValue(147.25);first.getNumberSetting("Position Y").setValue(82.5);assertTrue(manager.saveConfig("hud"));manager.shutdown();
  ArrayListHUDModule restored=new ArrayListHUDModule();ConfigManager next=new ConfigManager(directory,Collections.singletonList(restored),()->false,s->{});next.initialize(Collections.emptyList());
  assertEquals(147.25,restored.getNumberSetting("Position X").getValue(),0);assertEquals(82.5,restored.getNumberSetting("Position Y").getValue(),0);
  restored.getNumberSetting("Position X").setValue(10.0);assertTrue(next.loadConfig("hud"));assertEquals(147.25,restored.getNumberSetting("Position X").getValue(),0);
  try(java.util.stream.Stream<Path> entries=Files.list(directory)){assertEquals(2,entries.count());}
 }
}
