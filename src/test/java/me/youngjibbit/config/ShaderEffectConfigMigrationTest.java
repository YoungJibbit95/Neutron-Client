package me.youngjibbit.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.util.Collections;
import static org.junit.Assert.*;

/** Real codec migrations: the former palette aliases must not corrupt or reject old profiles. */
public class ShaderEffectConfigMigrationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private ConfigManager codec(ClientSettings settings)throws Exception {
        return new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(settings),()->false,s->{});
    }
    private JsonObject profile(String legacy,String explicit,boolean explicitFirst) {
        JsonObject values=new JsonObject();
        if(explicit!=null&&explicitFirst)values.addProperty("ui_glow_mode",explicit);
        values.addProperty("gradient_method",legacy);
        if(explicit!=null&&!explicitFirst)values.addProperty("ui_glow_mode",explicit);
        JsonObject module=new JsonObject();module.add("settings",values);
        JsonObject modules=new JsonObject();modules.add("client_settings",module);
        JsonObject root=new JsonObject();root.addProperty("version",2);root.add("modules",modules);return root;
    }
    @Test public void oldGeometricModesLoadAndSaveAsExplicitUiEffects()throws Exception {
        for(String old:new String[]{"Flame","Neon","Lines"}) {
            ClientSettings settings=new ClientSettings();ConfigManager codec=codec(settings);
            settings.uiGlowMode.setValue("Neon");
            assertTrue(codec.saveConfig("old",profile(old,null,false).toString()));
            assertTrue(codec.loadConfig("old"));
            String expected=old.equals("Lines")?"Standard":old;
            assertEquals("Flow",settings.gradientMethod.getValue());
            assertEquals(expected,settings.uiGlowMode.getValue());
            JsonObject saved=new JsonParser().parse(codec.serialize()).getAsJsonObject().getAsJsonObject("modules")
                    .getAsJsonObject("client_settings").getAsJsonObject("settings");
            assertEquals("Flow",saved.get("gradient_method").getAsString());
            assertEquals(expected,saved.get("ui_glow_mode").getAsString());
            assertEquals(3,settings.lineSpacing.getValue(),0);
        }
    }
    @Test public void explicitUiChoiceWinsForEveryLegacyAliasAndKeyOrder()throws Exception {
        for(String old:new String[]{"Flame","Neon","Lines"})for(String explicit:new String[]{"Standard","Flame","Neon"})for(boolean first:new boolean[]{false,true}) {
            ClientSettings settings=new ClientSettings();ConfigManager codec=codec(settings);
            assertTrue(codec.saveConfig("explicit",profile(old,explicit,first).toString()));
            assertTrue(codec.loadConfig("explicit"));
            assertEquals(explicit,settings.uiGlowMode.getValue());
            assertEquals("Flow",settings.gradientMethod.getValue());
        }
    }
    @Test public void legacyArrayNamesAndMixedCaseMigrate()throws Exception {
        ClientSettings settings=new ClientSettings();ConfigManager codec=codec(settings);
        JsonObject setting=new JsonObject();setting.addProperty("name","Gradient Method");setting.addProperty("value","nEoN");
        JsonArray values=new JsonArray();values.add(setting);
        JsonObject module=new JsonObject();module.add("settings",values);
        JsonObject root=new JsonObject();root.add("Client Settings",module);
        assertTrue(codec.saveConfig("legacy",root.toString()));assertTrue(codec.loadConfig("legacy"));
        assertEquals("Flow",settings.gradientMethod.getValue());assertEquals("Neon",settings.uiGlowMode.getValue());
    }
    @Test public void invalidUiChoiceIsRejectedWithoutApplyingLegacyMigration()throws Exception {
        ClientSettings settings=new ClientSettings();ConfigManager codec=codec(settings);
        settings.gradientMethod.setValue("Pulse");settings.uiGlowMode.setValue("Neon");
        assertFalse(codec.saveConfig("invalid",profile("Flame","Lines",false).toString()));
        assertEquals("Pulse",settings.gradientMethod.getValue());assertEquals("Neon",settings.uiGlowMode.getValue());
    }
    @Test public void newControlsRoundTripAndMissingKeysUseStableDefaults()throws Exception {
        ClientSettings settings=new ClientSettings();ConfigManager codec=codec(settings);
        settings.uiGlowMode.setValue("Flame");settings.flameMotion.setValue(.37);settings.lineSpacing.setValue(6.5);
        assertTrue(codec.saveConfig("new",codec.serialize()));
        settings.uiGlowMode.setValue("Standard");settings.flameMotion.setValue(.9);settings.lineSpacing.setValue(2D);
        assertTrue(codec.loadConfig("new"));assertEquals("Flame",settings.uiGlowMode.getValue());
        assertEquals(.37,settings.flameMotion.getValue(),0);assertEquals(6.5,settings.lineSpacing.getValue(),0);
        assertTrue(codec.saveConfig("old",profile("Flow",null,false).toString()));assertTrue(codec.loadConfig("old"));
        assertEquals("Standard",settings.uiGlowMode.getValue());assertEquals(.65,settings.flameMotion.getValue(),0);
        assertEquals(3,settings.lineSpacing.getValue(),0);
    }
}
