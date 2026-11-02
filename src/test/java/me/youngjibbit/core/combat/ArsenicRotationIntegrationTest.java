package me.youngjibbit.core.combat;

import me.youngjibbit.event.motion.EventMotion;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class ArsenicRotationIntegrationTest {
    static RotationBehaviorHookTest.Player enemy() throws Exception {
        RotationBehaviorHookTest.Player target=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        target.posX=target.lastTickPosX=3;target.posY=target.lastTickPosY=0;target.posZ=target.lastTickPosZ=0;
        target.setEntityBoundingBox(new AxisAlignedBB(2.7,0,-.3,3.3,1.8,.3));return target;
    }
    static void setup(RotationBehaviorHookTest.Fixture f) throws Exception {
        f.mc.gameSettings=RotationBehaviorHookTest.bare(GameSettings.class);f.mc.gameSettings.mouseSensitivity=.5f;
        f.player.setEntityBoundingBox(new AxisAlignedBB(-.3,0,-.3,.3,1.8,.3));
    }
    @Test public void requestsUseAcceptedGridRemainIdempotentAndDoNotGrantOwnership() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            setup(f);AtomicLong time=new AtomicLong();RotationManager r=new RotationManager(time::get,312);
            RotationProfile p=ArsenicAimTrajectoryTest.profile(RotationProfile.ArsenicMode.LAZY);RotationBehaviorHookTest.Player target=enemy();
            r.beginMotion(f.player,f.world,17,3);r.request("aura",20,17,3);r.finishMotion(17,3,true);
            r.beginMotion(f.player,f.world,130,-50);
            float[] next=r.calculateRotations("aura",130,-50,target,false,p,false);
            assertArrayEquals(next,r.calculateRotations("aura",130,-50,target,false,p,false),0);
            float gcd=RotationSensitivity.mouseStep(.5f);
            assertEquals(Math.rint((next[0]-17)/gcd),(next[0]-17)/gcd,1e-4);
            assertEquals(Math.rint((next[1]-3)/gcd),(next[1]-3)/gcd,1e-4);
            r.request("aura",20,next[0],next[1]);r.finishMotion(next[0],next[1],false);
            assertNull(r.getConfirmedOwner());assertFalse(r.canUseRotation("aura",false));
            assertArrayEquals(new float[]{17,3},r.calculateRotations("aura",130,-50,target,false,p,false),0);
        }
    }
    @Test public void visibleAssistancePreservesLaterMouseInputAndLosesToScaffold() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            setup(f);RotationManager r=new RotationManager(()->0,84);RotationBehaviorHookTest.Player target=enemy();
            RotationProfile p=ArsenicAimTrajectoryTest.profile(RotationProfile.ArsenicMode.HEURISTICS);
            r.beginMotion(f.player,f.world,0,0);
            float[] next=r.calculateRotations("aimassist",0,0,target,false,p,true);
            r.requestVisible("aimassist",10,next[0],next[1],0,0,()->true);
            f.player.rotationYaw=11;f.player.rotationPitch=7;
            EventMotion event=new EventMotion(0,0,0,11,7,true);r.resolve(event);
            assertEquals(11+next[0],f.player.rotationYaw,1e-5);assertEquals(7+next[1],f.player.rotationPitch,1e-5);
            r.finishMotion(event.getYaw(),event.getPitch(),true);
            r.beginMotion(f.player,f.world,11,7);
            r.requestVisible("aimassist",10,-50,10,11,7,()->true);r.request("scaffold",30,70,80);
            float before=f.player.rotationYaw;r.resolve(event);assertEquals(before,f.player.rotationYaw,0);assertEquals(70,event.getYaw(),0);
        }
    }
}
