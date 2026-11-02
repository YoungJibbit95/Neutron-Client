package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
@ModuleInfo(name="NoHurtCam",description="Removes the camera tilt caused by damage",category=Category.Render)
public final class NoHurtCam extends Module {
    public static boolean active(){Module m=Pulsar.INSTANCE.getMm()==null?null:Pulsar.INSTANCE.getMm().getModule(NoHurtCam.class);return m!=null&&m.isToggled();}
}
