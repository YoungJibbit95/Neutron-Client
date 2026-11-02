package me.youngjibbit.core.combat;

import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.value.Value;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One stable Minecraft singleton; each start resets only the scene/module/service under test. */
final class RotationBezierFixture implements AutoCloseable {
    final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
    final AtomicLong clock=new AtomicLong();
    RotationManager rotation;
    RotationBehaviorHookTest.Player target;
    Module module;
    boolean visible;
    RotationBezierFixture() throws Exception { }
    void start(boolean visible,Consumer<Module> settings) throws Exception {
        stop();this.visible=visible;clock.set(0);
        f.mc.inGameHasFocus=true;f.mc.currentScreen=null;f.player.isDead=false;
        f.player.rotationYaw=f.player.rotationPitch=0;f.player.usingItem=true;
        RotationBehaviorHookTest.field(EntityPlayerSP.class,"lastReportedYaw").set(f.player,0F);
        RotationBehaviorHookTest.field(EntityPlayerSP.class,"lastReportedPitch").set(f.player,0F);
        RotationBehaviorHookTest.field(EntityPlayerSP.class,"positionUpdateTicks").set(f.player,0);
        ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);
        f.player.sendQueue.clientWorldController=scene;
        f.mc.gameSettings.keyBindUseItem=new net.minecraft.client.settings.KeyBinding("synthetic.use",0,"synthetic");
        f.mc.gameSettings.keyBindAttack=new net.minecraft.client.settings.KeyBinding("synthetic.attack",0,"synthetic");
        rotation=new RotationManager(clock::get,424242);
        RotationBehaviorHookTest.field(ClientRuntime.class,"rotationManager").set(f.runtime,rotation);
        RotationBehaviorHookTest.field(Module.class,"toggled").set(f.aura,!visible);
        RotationBehaviorHookTest.field(Module.class,"toggled").set(f.scaffold,false);
        target=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        target.posX=-1;target.posY=64;target.posZ=2;target.worldObj=scene;target.setEntityId(9);
        target.setEntityBoundingBox(new AxisAlignedBB(-1.3,64,1.7,-.7,65.8,2.3));
        RotationBehaviorHookTest.field(World.class,"loadedEntityList").set(scene,new ArrayList<>(Collections.singletonList(target)));
        module=visible?new AimAssist():f.aura;
        for(Value<?> value:module.getValues())value.reset();
        RotationBehaviorHookTest.field(Module.class,"toggled").set(module,true);
        module.getModeSetting(visible?"Rotations":"Rot Mode").setValue(visible?"Normal":"Basic");
        module.getNumberSetting("Yaw Speed").setValue(7D);module.getNumberSetting("Pitch Speed").setValue(9D);
        module.getBooleanSetting(visible?"Best Hit Vec":"Best Aim Vec").setValue(false);
        module.getModeSetting("Randomization Style").setValue("Benutzerdefiniert");
        settings.accept(module);module.onEnable();drain();
    }
    List<Object> walk(long nanos) {
        clock.set(nanos);ScaffoldClientFlowTest.ordinaryWalking(f);return drain();
    }
    List<Object> drain() {
        List<Object> packets=new ArrayList<>();Object packet;while((packet=f.channel.readOutbound())!=null)packets.add(packet);return packets;
    }
    void stop(){if(module!=null){module.onDisable();module=null;}}
    @Override public void close() throws Exception {stop();f.close();}
}
