package me.youngjibbit.gui;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.client.ArrayListHUDModule;
import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.theme.ThemeStore;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Read-only HUD labels stay useful without mutating module values or network activity. */
public class ModulePrefixesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @ModuleInfo(id="aura", name="Aura", category=Category.Combat)
    public static class AuraFixture extends Module {
        final ComboValue mode = new ComboValue("Mode", this, new String[]{"Single", "Switch"}, 0);
        int changes;
        @Override public void onValueChange() { changes++; }
    }
    @ModuleInfo(id="scaffold", name="Scaffold", category=Category.Movement)
    public static class ScaffoldFixture extends Module {
        final ComboValue approach = new ComboValue("Scaffold Approach", this, new String[]{"Neutron", "Adaptive Bridge"}, 0);
        final ComboValue rotation = new ComboValue("Rotation Mode", this, new String[]{"Back", "Godbridge"}, 0);
    }
    @ModuleInfo(name="Prefix fixture", category=Category.Client)
    public static class GenericFixture extends Module { }

    @Test public void auraAndScaffoldDetailsFollowLiveModesWithoutChangingValues() {
        AuraFixture aura = new AuraFixture();
        List<Value<?>> original = new ArrayList<>(aura.getValues());
        assertEquals("Single", ModulePrefixes.detail(aura));
        aura.mode.setValue("Switch");
        int changes = aura.changes;
        for (int i=0; i<100; i++) assertEquals("Switch", ModulePrefixes.detail(aura));
        assertEquals(changes, aura.changes);
        assertEquals(original, aura.getValues());
        ScaffoldFixture scaffold = new ScaffoldFixture();
        assertEquals("Back", ModulePrefixes.detail(scaffold));
        scaffold.rotation.setValue("Godbridge");
        assertEquals("Godbridge", ModulePrefixes.detail(scaffold));
        scaffold.approach.setValue("Adaptive Bridge");
        assertEquals("Adaptive Bridge", ModulePrefixes.detail(scaffold));
    }

    @Test public void backtrackUsesActualOwnerAgeAndDoesNotInventConfiguredDelay() {
        assertEquals("Idle \u00b7 Pulse", ModulePrefixes.backtrack("Pulse", null));
        ExploitActivitySnapshot first = ExploitActivitySnapshot.holding(
                ExploitActivitySnapshot.Producer.BACKTRACK, 100, 2, 4, 34);
        ExploitActivitySnapshot second = ExploitActivitySnapshot.holding(
                ExploitActivitySnapshot.Producer.BACKTRACK, 200, 2, 6, 81);
        assertEquals("Hold 34 ms \u00b7 Pulse", ModulePrefixes.backtrack("Pulse", first));
        assertEquals("Hold 81 ms \u00b7 Pulse", ModulePrefixes.backtrack("Pulse", second));
        assertEquals(34, first.ageMillis);
        assertEquals(4, first.heldPackets);
        assertEquals("Idle", ModulePrefixes.backtrack("", null));
    }

    @Test public void themeUsesReadableDisplayNameAndHasNoPersistenceSideEffect() throws Exception {
        ThemeCatalog old = ThemeCatalog.global();
        String previousMode = ThemeMode.selected();
        java.nio.file.Path file = temporary.getRoot().toPath().resolve("themes.json");
        ThemeCatalog catalog = new ThemeCatalog(new ThemeStore(file));
        try {
            ThemeCatalog.install(catalog);
            ThemeModule theme = new ThemeModule();
            catalog.select("Neutron");
            assertEquals("Neutron", ModulePrefixes.detail(theme));
            catalog.select("SynthWave");
            assertEquals("SynthWave", ModulePrefixes.detail(theme));
            assertFalse(java.nio.file.Files.exists(file));
        } finally { ThemeCatalog.install(old); ThemeMode.select(previousMode); }
    }

    @Test public void genericDetailsAreOptionalPlainTextWithUnicodeIntact() {
        GenericFixture module = new GenericFixture();
        assertEquals("", ModulePrefixes.detail(module));
        module.info = "\u00a7c\u00a7lLive 34 ms\u00a7r\n";
        assertEquals("Live 34 ms", ModulePrefixes.detail(module));
        module.info = "";
        new ComboValue("Mode", module, new String[]{"Default", "Compact"}, 1);
        assertEquals("Compact", ModulePrefixes.detail(module));
        assertEquals("\ud83d\ude80", ModulePrefixes.clean("\u00a76\ud83d\ude80"));
        assertEquals("", ModulePrefixes.clean(null));
        StringBuilder text = new StringBuilder();
        for (int i=0; i<100; i++) text.append("\ud83d\ude80");
        String bounded = ModulePrefixes.clean(text.toString());
        assertEquals(80, bounded.codePointCount(0, bounded.length()));
        assertTrue(Character.isLowSurrogate(bounded.charAt(bounded.length()-1)));
    }

    @Test public void prefixesDefaultOnAndRetainTheHistoricalConfigIdAndLookup() {
        ArrayListHUDModule hud = new ArrayListHUDModule();
        assertTrue(hud.getBooleanSetting("Module Prefixes").getValue());
        assertEquals("show_module_info", hud.getBooleanSetting("Module Prefixes").getId());
        assertSame(hud.getBooleanSetting("Module Prefixes"), hud.getBooleanSetting("Show Module Info"));
        hud.getBooleanSetting("show_module_info").setValue(false);
        assertFalse(hud.getBooleanSetting("Module Prefixes").getValue());
    }

    @Test public void delaySamplingIsBoundedAndRestartsAfterHudReset() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        ModulePrefixes.Sampler sampler = new ModulePrefixes.Sampler(clock::get, module -> "Read " + reads.incrementAndGet());
        Module backtrack = new me.youngjibbit.module.impl.exploit.Backtrack();
        assertEquals("Read 1", sampler.detail(backtrack));
        for(int i=0;i<300;i++) { clock.set(i*100_000L); assertEquals("Read 1", sampler.detail(backtrack)); }
        clock.set(50_000_000L);assertEquals("Read 2", sampler.detail(backtrack));
        sampler.clear();assertEquals("Read 3", sampler.detail(backtrack));
        clock.set(1);assertEquals("Read 4", sampler.detail(backtrack));
        GenericFixture generic=new GenericFixture();
        assertEquals("Read 5",sampler.detail(generic));assertEquals("Read 6",sampler.detail(generic));
    }

    @Test public void allLiveSuffixesRefreshIndependentlyWithoutPerFrameQueueReads() {
        java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        ModulePrefixes.Sampler sampler=new ModulePrefixes.Sampler(clock::get,module->"Read "+reads.incrementAndGet());
        Module[] modules={new me.youngjibbit.module.impl.exploit.Backtrack(),
                new me.youngjibbit.module.impl.exploit.LagRange(),new me.youngjibbit.module.impl.exploit.Tickbase()};
        for(int i=0;i<modules.length;i++)assertEquals("Read "+(i+1),sampler.detail(modules[i]));
        clock.set(49_000_000L);
        for(int frame=0;frame<20;frame++)for(int i=0;i<modules.length;i++)
            assertEquals("Read "+(i+1),sampler.detail(modules[i]));
        assertEquals(3,reads.get());
        clock.set(50_000_000L);
        for(int i=0;i<modules.length;i++)assertEquals("Read "+(i+4),sampler.detail(modules[i]));
        sampler.clear();
        for(Module module:modules)sampler.detail(module);
        assertEquals(9,reads.get());
    }

    @Test public void markersAndShortLabelsDescribeOnlyObservedActivity() {
        assertEquals(ModulePrefixes.Marker.IDLE,ModulePrefixes.activity("MS",null).marker);
        ExploitActivitySnapshot charged=ExploitActivitySnapshot.steps(ExploitActivitySnapshot.Producer.TICKBASE,10,1,3,0,false);
        ModulePrefixes.Detail ready=ModulePrefixes.activity("",charged);
        assertEquals("Ready 3 ticks",ready.text);assertEquals(ModulePrefixes.Marker.READY,ready.marker);
        ExploitActivitySnapshot step=ExploitActivitySnapshot.steps(ExploitActivitySnapshot.Producer.LAGRANGE,20,1,0,2,true);
        ModulePrefixes.Detail releasing=ModulePrefixes.activity("Ticks",step);
        assertEquals("Step +2 \u00b7 Ticks",releasing.text);assertEquals(ModulePrefixes.Marker.RELEASING,releasing.marker);
        assertEquals(3,charged.credit);assertEquals(2,step.extraSteps);
    }
}
