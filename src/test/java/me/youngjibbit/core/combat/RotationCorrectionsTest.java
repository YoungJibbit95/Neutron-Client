package me.youngjibbit.core.combat;

import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class RotationCorrectionsTest {
    private RotationCorrections.Selection select(RotationManager manager,String source) {
        return RotationCorrections.select(manager,source,true,MoveFixController.Mode.PRECISE,BodyRotationController.Mode.NATURAL);
    }
    private void accept(RotationManager manager,String source,float yaw,float pitch) {
        manager.beginMotion(12,3);manager.request(source,source.equals("scaffold")?30:source.equals("aura")?20:10,yaw,pitch);
        manager.finishMotion(yaw,pitch,true);
    }

    @Test public void pendingRotationsNeverGrantCorrectionAndOnlyConfirmedPriorityWinnerSelects() {
        RotationManager manager=new RotationManager();manager.beginMotion(12,3);
        manager.request("aimassist",10,20,0);manager.request("aura",20,40,10);manager.request("scaffold",30,80,70);
        assertNull(manager.getConfirmedOwner());assertNull(select(manager,"scaffold"));
        EventMotion resolved=new EventMotion(1,2,3,12,3,true);manager.resolve(resolved);
        manager.finishMotion(resolved.getYaw(),resolved.getPitch(),true);
        assertNull(select(manager,"aura"));assertNull(select(manager,"aimassist"));
        RotationCorrections.Selection selected=select(manager,"scaffold");
        assertNotNull(selected);assertEquals(80,selected.yaw,0);assertEquals(70,selected.pitch,0);
    }

    @Test public void preUsesPreviousAcceptedOwnerAndRejectedOrRewrittenMovementRemovesCorrection() {
        RotationManager manager=new RotationManager();accept(manager,"aura",40,10);
        manager.beginMotion(12,3);manager.request("scaffold",30,80,70);
        assertNotNull(select(manager,"aura"));assertNull(select(manager,"scaffold"));
        manager.finishMotion(80,70,false);assertNull(manager.getConfirmedOwner());assertNull(select(manager,"aura"));
        accept(manager,"scaffold",80,70);manager.observeOutboundRotation(90,60);
        assertNull(manager.getConfirmedOwner());assertNull(select(manager,"scaffold"));
        manager.beginMotion(12,3);manager.request("aura",20,40,10);manager.finishMotion(41,10,true);
        assertNull(manager.getConfirmedOwner());
    }

    @Test public void disableReleaseAndAimAssistHandoffNeverFallbackToAStaleAuraProfile() {
        RotationManager manager=new RotationManager();accept(manager,"aura",40,10);accept(manager,"scaffold",80,70);
        assertNull(RotationCorrections.select(manager,"scaffold",false,MoveFixController.Mode.SIMPLE,BodyRotationController.Mode.DIRECT));
        assertNull(select(manager,"aura"));manager.reset("scaffold");assertNull(select(manager,"aura"));
        accept(manager,"aimassist",20,0);assertNull(select(manager,"aura"));assertNull(select(manager,"scaffold"));
        accept(manager,"aura",40,10);assertNotNull(select(manager,"aura"));
        manager.reset();assertNull(select(manager,"aura"));
    }

    @Test public void sceneChangeInvalidatesEvenThePreviousConfirmedOwnerDuringPre() {
        Object player=new Object(),world=new Object();RotationManager manager=new RotationManager();
        manager.beginMotion(player,world,12,3);manager.request("aura",20,40,10);manager.finishMotion(40,10,true);
        assertEquals("aura",manager.getConfirmedOwner(player,world));
        assertNull(manager.getConfirmedOwner(player,new Object()));
        assertNull(manager.getConfirmedOwner(new Object(),world));
        assertNotNull(select(manager,"aura"));manager.beginMotion(player,new Object(),12,3);
        assertNull(select(manager,"aura"));
    }

    @Test public void inputAndVisualCorrectionsCannotChangeVerifiedRotationOrMotionSequence() {
        RotationManager manager=new RotationManager();accept(manager,"scaffold",179,80);
        long sequence=manager.getMotionSequence();RotationCorrections.Selection selected=select(manager,"scaffold");
        PlayerMoveUpdateEvent event=new PlayerMoveUpdateEvent(.3f,.3f,.02f,-179,3);
        RotationCorrections.applyMovement(selected,event);
        BodyRotationController body=new BodyRotationController();Object player=new Object(),world=new Object();
        body.sample(player,world,selected.owner,selected.body,selected.yaw,selected.pitch,0,0);
        body.sample(player,world,selected.owner,selected.body,selected.yaw,selected.pitch,0,500_000_000L);
        assertEquals(sequence,manager.getMotionSequence());assertEquals(179,manager.getEffectiveYaw(),0);
        assertEquals(80,manager.getEffectivePitch(),0);assertTrue(manager.matchesEffective(179,80));
        assertEquals("scaffold",manager.getConfirmedOwner());assertTrue(manager.isMotionAccepted());
    }
}
