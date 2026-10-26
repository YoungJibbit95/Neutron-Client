package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Silent suppresses packet events, but an applied correction still invalidates evidence for subsequent player actions. */
public class SilentContextHookTest {
    private void correction(ExploitHookFixture f) throws Exception {
        Field loaded=NetHandlerPlayClient.class.getDeclaredField("doneLoadingTerrain");loaded.setAccessible(true);loaded.setBoolean(f.connection.play,true);
        Field config=net.minecraft.src.Config.class.getDeclaredField("minecraft");config.setAccessible(true);Object previous=config.get(null);
        f.client.renderGlobal=AttackHookTest.bare(net.minecraft.client.renderer.RenderGlobal.class);
        config.set(null,f.client);
        try {f.connection.network.receivePacketSilent(new S08PacketPlayerPosLook(0,0,0,0,0,Collections.emptySet()));}
        finally {config.set(null,previous);}
    }
    @Test public void silentCorrectionNeedsFreshTickbaseEvidenceEvenWithTheSamePlayerWorldAndConnection() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Tickbase tick=new Tickbase(()->0,f.client);tick.getBooleanSetting("Debug").setValue(true);tick.setToggled(true);
            try {
                f.cursor(f.enemy(7,3));Tickbase.beginFrame(f.client,false,1,50);
                Tickbase.runPlayerStep(f.player,()->true);assertEquals(1,tick.getDiagnostics().credit);
                correction(f);AtomicInteger calls=new AtomicInteger();
                Tickbase.runPlayerStep(f.player,()->{calls.incrementAndGet();return true;});
                assertEquals(0,tick.getDiagnostics().credit);assertEquals(1,calls.get());
                Tickbase.beginFrame(f.client,false,1,50);Tickbase.runPlayerStep(f.player,()->true);assertEquals(1,tick.getDiagnostics().credit);
            }finally {tick.setToggled(false);}
        }
    }
    @Test public void silentCorrectionDropsAnOldAttackLockInsteadOfOverridingFreshCursorEvidence() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Backtrack back=new Backtrack(()->0,f.client);back.getBooleanSetting("Diagnostics").setValue(true);back.setToggled(true);
            try {
                ExploitHookFixture.Enemy a=f.enemy(7,3),b=f.enemy(8,3);f.cursor(b);
                assertTrue(f.controller.tryAttackEntity(f.player,a,()->true));assertEquals(7,back.getDiagnostics().targetId);
                correction(f);Pulsar.BUS.post(new TickEvent());assertEquals(8,back.getDiagnostics().targetId);
                assertEquals("cursor",back.getDiagnostics().source);
            }finally {back.setToggled(false);}
        }
    }
}
