package me.youngjibbit.utils.font;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.Collections;
import static org.junit.Assert.*;

public class TypographySettingsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();

    @Test public void globalFamilyPreservesWeightSizeAndIconContracts(){
        assertEquals("Inter-Bold 23",FontManager.resolveKey("Product-Sans-Bold 23","Inter"));
        assertEquals("DmSansMedium 64",FontManager.resolveKey("Poppins-Medium 64","DM Sans"));
        assertEquals("SFProText-Regular 18",FontManager.resolveKey("DmSansRegular 18","SF Pro"));
        assertEquals("TenoriteRegular 20",FontManager.resolveKey("Poppins-Medium 20","Tenorite"));
        for(String key:new String[]{"icon 23","icon1 22","iOSIcons 18","unknown"}){
            assertEquals(key,FontManager.resolveKey(key,"Inter"));
        }
        assertEquals("Inter-Bold 16",FontManager.resolveKey("Verdana Bold 16","Inter"));
        assertNull(FontManager.resolveKey(null,"Inter"));
        assertEquals("Poppins-Bold 23",FontManager.resolveKey("Poppins-Bold 23","Original"));
    }

    @Test public void eachSelectedFamilyLoadsOnceAndLocalFontsRemainSelectable(){
        try(FontManager fonts=new FontManager()){
            for(String family:new ClientSettings().fontFamily.getOptions()){
                String key=FontManager.resolveKey("Product-Sans-Bold 23",family);
                assertSame(fonts.getLocalFont(key),fonts.getLocalFont(key));
                assertTrue(fonts.getLocalFont(key).getWidth("Session Info / FastPlace")>40);
            }
            assertNotSame(fonts.getLocalFont("Inter-Regular 18"),fonts.getLocalFont("Poppins-Regular 18"));
        }
    }

    @Test public void typographyRoundTripsAndOldConfigsResetOnlyTheNewDefaults()throws Exception{
        ClientSettings settings=new ClientSettings();
        ConfigManager configs=new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(settings),()->false,s->{});
        settings.fontFamily.setValue("Inter");settings.textGlow.setValue("On");
        settings.textGlowStrength.setValue(.7);settings.textGlowRadius.setValue(2.5);settings.textGlowColor.setValue("Theme");
        assertTrue(configs.saveConfig("type",configs.serialize()));
        settings.fontFamily.setValue("DM Sans");settings.textGlow.setValue("Off");
        assertTrue(configs.loadConfig("type"));
        assertEquals("Inter",settings.fontFamily.getValue());assertEquals("On",settings.textGlow.getValue());
        assertEquals(.7,settings.textGlowStrength.getValue(),0);assertEquals(2.5,settings.textGlowRadius.getValue(),0);
        assertEquals("Theme",settings.textGlowColor.getValue());
        JsonObject old=new JsonParser().parse(configs.serialize()).getAsJsonObject();
        JsonObject values=old.getAsJsonObject("modules").getAsJsonObject("client_settings").getAsJsonObject("settings");
        for(String id:new String[]{"ui_font","text_glow","text_glow_strength","text_glow_radius","text_glow_color"})values.remove(id);
        values.addProperty("roundness",11);values.addProperty("global_glow",false);
        assertTrue(configs.saveConfig("old",old.toString()));assertTrue(configs.loadConfig("old"));
        assertEquals("Original",settings.fontFamily.getValue());assertEquals("Module settings",settings.textGlow.getValue());
        assertEquals(11,settings.roundness.getValue(),0);assertFalse(settings.glow.getValue());
    }
}
