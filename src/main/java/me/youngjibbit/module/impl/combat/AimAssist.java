package me.youngjibbit.module.impl.combat;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationSettings;
import me.youngjibbit.core.combat.RotationProfile;
import me.youngjibbit.core.combat.TargetingService;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "AimAssist",
        description = "Legit rotational assist",
        category = Category.Combat
)
public class AimAssist extends Module {

    private static final String ROTATION_SOURCE = "aimassist";

    private EntityLivingBase target;

    private final SliderValue aimRange = new SliderValue("Range", this, 4.0, 1.0, 6.0, false);
    private final SliderValue fov = new SliderValue("FOV",this,360D,1,360,false);
    private final SliderValue yawSpeed = new SliderValue("Yaw Speed", this, 24.0, 1.0, 90.0, true);
    private final SliderValue pitchSpeed = new SliderValue("Pitch Speed", this, 20.0, 1.0, 90.0, true);
    private final SliderValue jitter = new SliderValue("Jitter", this, 0.35, 0.0, 5.0, false);

    private final ComboValue sortMode = new ComboValue("Sort Mode", this, new String[]{"Range", "Health", "Hurt Time"}, 0);
    private final CheckBoxValue players = new CheckBoxValue("Players", this, true);
    private final CheckBoxValue animals = new CheckBoxValue("Animals", this, false);
    private final CheckBoxValue mobs = new CheckBoxValue("Mobs", this, false);
    private final CheckBoxValue invisibles = new CheckBoxValue("Invisibles", this, false);
    private final CheckBoxValue throughWalls = new CheckBoxValue("Through Walls", this, false);
    private final CheckBoxValue teams = new CheckBoxValue("Teams", this, false);
    private final CheckBoxValue bestHitVec = new CheckBoxValue("Best Hit Vec", this, true);
    private final CheckBoxValue randomHitVec = new CheckBoxValue("Random Hit Vec", this, false);

    private final RotationSettings rotationSettings=new RotationSettings(this,true,yawSpeed,pitchSpeed,jitter,randomHitVec);

    public AimAssist() {
        this.setKey(Keyboard.KEY_F);
    }

    private boolean usable() {
        return isToggled() && mc!=null && mc.thePlayer!=null && mc.theWorld!=null && mc.currentScreen==null
                && mc.inGameHasFocus && mc.getRenderViewEntity()==mc.thePlayer && mc.thePlayer.isEntityAlive()
                && !mc.thePlayer.isDead && !mc.thePlayer.isSpectator() && !mc.thePlayer.isRiding();
    }
    private void clearAim() {
        target=null;RuntimeServices.rotation().reset(ROTATION_SOURCE);CombatTargetTracker.clear(ROTATION_SOURCE);
    }
    @Override public void onEnable() { clearAim();super.onEnable(); }
    @Override public void onDisable() { clearAim();super.onDisable(); }
    @Subscribe private final Listener<WorldEvent> world=new Listener<>(event->clearAim());
    @Subscribe private final Listener<TickEvent> context=new Listener<>(event->{if(!usable() || (target!=null && !validTarget(target,options())))clearAim();});

    private TargetingService.Options options() {
        TargetingService.Options options=new TargetingService.Options();
        options.range=aimRange.getValue();options.fov=fov.getValue();options.players=players.getValue();
        options.animals=animals.getValue();options.mobs=mobs.getValue();options.invisibles=invisibles.getValue();
        options.throughWalls=throughWalls.getValue();options.sameTeam=teams.getValue();return options;
    }
    private boolean validTarget(EntityLivingBase selected,TargetingService.Options options) {
        return selected.worldObj==mc.theWorld && mc.theWorld.loadedEntityList.contains(selected)
                && RuntimeServices.targeting().isValidTarget(selected,options);
    }

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if(!usable()) { clearAim();return; }
        if(!event.isPre()) return;
        RotationManager rotationManager = RuntimeServices.rotation();
        String confirmed=rotationManager.getConfirmedOwner();
        if("aura".equals(confirmed) || "scaffold".equals(confirmed)) { clearAim();return; }

        TargetingService.Options options=options();

        target = RuntimeServices.targeting().getBestTarget(options, mapSort(sortMode.getValue()));
        if (target == null) {
            RuntimeServices.rotation().reset(ROTATION_SOURCE);
            CombatTargetTracker.clear("aimassist");
            return;
        }
        CombatTargetTracker.update(target, "aimassist");

        RotationProfile profile=rotationSettings.snapshot();profile.reach=aimRange.getValue().floatValue();profile.throughWalls=throughWalls.getValue();
        float[] targetRotations = rotationManager.calculateRotations(
                ROTATION_SOURCE,
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                target,
                bestHitVec.getValue(),
                profile, true
        );

        if (targetRotations == null) {
            clearAim();
            return;
        }
        EntityLivingBase selected=target;
        rotationManager.requestVisible(ROTATION_SOURCE, RotationManager.AIM_ASSIST_PRIORITY, targetRotations[0], targetRotations[1],
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                ()->usable() && target==selected && validTarget(selected,options()));
    });

    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) {
        if(name!=null && (name.equalsIgnoreCase("Jitter Range") || name.equalsIgnoreCase("jitter_range"))) name="jitter";
        if(name!=null && (name.equalsIgnoreCase("Target Filter Method") || name.equalsIgnoreCase("target_filter_method"))) name="sort_mode";
        return super.getValueByName(RotationSettings.canonicalName(name));
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    private TargetingService.SortMode mapSort(String mode) {
        if ("Health".equalsIgnoreCase(mode)) {
            return TargetingService.SortMode.HEALTH;
        }
        if ("Hurt Time".equalsIgnoreCase(mode)) {
            return TargetingService.SortMode.HURT_TIME;
        }
        return TargetingService.SortMode.DISTANCE;
    }
}
