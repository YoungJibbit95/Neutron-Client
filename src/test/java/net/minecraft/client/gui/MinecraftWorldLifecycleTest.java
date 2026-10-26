package net.minecraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Failure recovery can unload before all optional client services have been constructed. */
public class MinecraftWorldLifecycleTest {
    public static class Renderer extends RenderGlobal {
        int calls; WorldClient received;
        public Renderer() { super(null); }
        @Override public void setWorldAndLoadRenderers(WorldClient world) { calls++; received=world; }
    }
    public static class Effects extends EffectRenderer {
        int calls;
        public Effects() { super(null,null); }
        @Override public void clearEffects(net.minecraft.world.World world) { calls++; assertNull(world); }
    }
    private static <T> T bare(Class<T> type) throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    @Test public void unloadBeforeOptionalServicesExistIsSafe() throws Exception {
        Minecraft mc=bare(Minecraft.class);
        mc.loadWorld(null);
        assertNull(mc.theWorld); assertNull(mc.thePlayer);
    }
    @Test public void availableConsumersStillReceiveUnloadWhenOtherServicesAreMissing() throws Exception {
        for(int present=1;present<=3;present++) {
            Minecraft mc=bare(Minecraft.class);
            Renderer renderer=bare(Renderer.class); Effects effects=bare(Effects.class);
            mc.renderGlobal=(present&1)!=0?renderer:null;
            mc.effectRenderer=(present&2)!=0?effects:null;
            mc.loadWorld(null);
            assertEquals((present&1)!=0?1:0,renderer.calls);
            assertEquals((present&2)!=0?1:0,effects.calls);
            assertNull(renderer.received);
        }
    }
}
