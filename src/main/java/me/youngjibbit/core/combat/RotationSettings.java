package me.youngjibbit.core.combat;

import me.youngjibbit.module.Module;
import me.youngjibbit.utils.math.FastNoiseLite;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;

/** Shared setting bindings, owned independently by Aura and visible AimAssist. */
public final class RotationSettings {
    public final ComboValue preset;
    public final SliderValue speedVariation;
    private final boolean placement;
    public final ComboValue mode,noise,cellularDistance,cellularReturn,fractal,warp;
    public final SliderValue minSpeed,maxSpeed,interpolation,yawOffset,pitchOffset,radius,frequency,strength;
    public final SliderValue cellularJitter,gain,lacunarity,octaves,pingPong,weighted,warpAmplitude;
    public final CheckBoxValue randomization;
    public final CheckBoxValue smartAim,stopOnTarget,presetTiming;
    public final CheckBoxValue heuristics,interpolateRotation,advancedPitch;
    public final ComboValue randomMode;
    private final SliderValue yawSpeed,pitchSpeed,jitter;
    private final CheckBoxValue randomHitVec;
    public RotationSettings(Module parent,boolean assist,SliderValue yawSpeed,SliderValue pitchSpeed,SliderValue jitter,CheckBoxValue randomHitVec) {
        this(parent,assist,assist?"Rotations":"Rot Mode",yawSpeed,pitchSpeed,jitter,randomHitVec);
    }
    public RotationSettings(Module parent,String modeName,SliderValue yawSpeed,SliderValue pitchSpeed) {
        this(parent,false,modeName,yawSpeed,pitchSpeed,new SliderValue("Jitter",parent,0D,0,5,false),new CheckBoxValue("Random Hit Vec",parent,false));
    }
    private RotationSettings(Module parent,boolean assist,String modeName,SliderValue yawSpeed,SliderValue pitchSpeed,SliderValue jitter,CheckBoxValue randomHitVec) {
        this.placement="Rotation Smoothing".equals(modeName);
        this.yawSpeed=yawSpeed;this.pitchSpeed=pitchSpeed;this.jitter=jitter;this.randomHitVec=randomHitVec;
        smartAim=placement?null:new CheckBoxValue("Smart Aim",parent,false);
        stopOnTarget=placement?null:new CheckBoxValue("Stop on Target",parent,false);
        presetTiming=placement?null:new CheckBoxValue("Preset Timing",parent,false);
        heuristics=placement?null:new CheckBoxValue("Heuristics",parent,false);
        interpolateRotation=placement?null:new CheckBoxValue("Interpolate Rotation",parent,false);
        advancedPitch=placement?null:new CheckBoxValue("Advanced Pitch",parent,false);
        randomMode=placement?null:new ComboValue("Random Mode",parent,new String[]{"Noise","Basic","OnlyRotation","Doubled"},0);
        mode=new ComboValue(modeName,parent,assist?new String[]{"Legit","Test","Normal"}:new String[]{"Basic","Smooth","Advanced"},assist?2:0);
        preset=new ComboValue("Randomization Style",parent,new String[]{"Aus","Sanft","Organisch","Dynamisch","Benutzerdefiniert"},1);
        speedVariation=new SliderValue("Speed Variation",parent,0D,0,.9,false);
        minSpeed=new SliderValue("Min Speed",parent,30D,1,180,true);maxSpeed=new SliderValue("Max Speed",parent,40D,1,180,true);
        interpolation=new SliderValue("Interpolation",parent,5D,1,20,false);
        randomization=new CheckBoxValue("Randomization",parent,true);
        strength=new SliderValue("Noise Strength",parent,1D,0,1,false);
        noise=new ComboValue("Noise Mode",parent,new String[]{"Aus","OpenSimplex2","OpenSimplex2S","Cellular","Perlin","ValueCubic","Value"},1);
        frequency=new SliderValue("Frequency",parent,1D,.1,placement?10:30,false);
        yawOffset=new SliderValue("Yaw Offset",parent,0D,0,5,false);pitchOffset=new SliderValue("Pitch Offset",parent,0D,0,5,false);
        radius=new SliderValue("Hit Vec Radius",parent,.5,0,5,false);
        cellularDistance=new ComboValue("Cellular Distance Mode",parent,names(FastNoiseLite.CellularDistanceFunction.values()),1);
        cellularReturn=new ComboValue("Cellular Return Mode",parent,names(FastNoiseLite.CellularReturnType.values()),1);
        cellularJitter=new SliderValue("Cellular Jitter",parent,1D,0,10,false);
        fractal=new ComboValue("Fractal Mode",parent,names(FastNoiseLite.FractalType.values()),0);
        gain=new SliderValue("Gain",parent,.5,0,10,false);lacunarity=new SliderValue("Lacunarity",parent,2D,.1,10,false);
        octaves=new SliderValue("Octaves",parent,3D,1,10,true);pingPong=new SliderValue("Ping Pong Strength",parent,2D,0,10,false);
        weighted=new SliderValue("Weighted Strength",parent,0D,0,10,false);
        warp=new ComboValue("Domain Warp Mode",parent,names(FastNoiseLite.DomainWarpType.values()),0);
        warpAmplitude=new SliderValue("Domain Warp Amp",parent,1D,0,10,false);
        minSpeed.setVisibility(m->timingEditable() && advanced());maxSpeed.setVisibility(m->timingEditable() && advanced());
        interpolation.setVisibility(m->timingEditable() && (!basic() || (!placement && interpolateRotation.getValue())));
        if(presetTiming!=null)presetTiming.setVisibility(m->!custom() && !"Aus".equals(preset.getValue()) && (!basic() || interpolateRotation.getValue()));
        if(randomMode!=null)randomMode.setVisibility(m->!"Aus".equals(preset.getValue()) && (!custom() || randomization.getValue()));
        randomization.setVisibility(m->custom());
        strength.setVisibility(m->!"Aus".equals(preset.getValue()));noise.setVisibility(m->custom() && randomization.getValue());
        speedVariation.setVisibility(m->custom() && noiseOn());
        randomHitVec.setVisibility(m->custom() && noiseOn());
        frequency.setVisibility(m->custom() && (noiseOn() || angularOn() || (heuristics!=null && heuristics.getValue() && randomization.getValue() && strength.getValue()>0)));
        fractal.setVisibility(m->custom() && noiseOn());
        for(Value<?> v:new Value<?>[]{jitter,yawOffset,pitchOffset})v.setVisibility(m->!placement && custom() && angularOn());
        radius.setVisibility(m->custom() && noiseOn() && randomHitVec.getValue());
        for(Value<?> v:new Value<?>[]{cellularDistance,cellularReturn,cellularJitter})v.setVisibility(m->custom() && noiseOn() && "Cellular".equals(noise.getValue()));
        octaves.setVisibility(m->custom() && noiseOn() && !"None".equals(fractal.getValue()));
        gain.setVisibility(m->octaves.isVisible() && octaves.getValue()>1);
        lacunarity.setVisibility(m->gain.isVisible() && gain.getValue()>0);
        weighted.setVisibility(m->gain.isVisible() && gain.getValue()>0 && !domain());
        pingPong.setVisibility(m->custom() && noiseOn() && "PingPong".equals(fractal.getValue()));
        warp.setVisibility(m->custom() && noiseOn() && domain());warpAmplitude.setVisibility(m->warp.isVisible());
    }
    private boolean custom() { return "Benutzerdefiniert".equals(preset.getValue()); }
    private boolean timingEditable() { return placement?custom():custom() || "Aus".equals(preset.getValue()) || !presetTiming.getValue(); }
    private boolean noiseOn() { return randomization.getValue() && strength.getValue()>0 && !"Aus".equals(noise.getValue()); }
    private boolean angularOn() { return randomization.getValue() && strength.getValue()>0 && (noiseOn() || (randomMode!=null && !"Noise".equals(randomMode.getValue()))); }
    private boolean basic() { return "Basic".equals(mode.getValue()) || "Normal".equals(mode.getValue()); }
    private boolean advanced() { return "Advanced".equals(mode.getValue()) || "Test".equals(mode.getValue()); }
    private boolean domain() { return fractal.getValue().startsWith("DomainWarp"); }
    private static String[] names(Enum<?>[] values) { String[] names=new String[values.length];for(int i=0;i<values.length;i++)names[i]=values[i].name();return names; }
    private static float f(SliderValue v) { return v.getValue().floatValue(); }
    public RotationProfile snapshot() {
        RotationProfile p=new RotationProfile();
        p.mode=basic()?RotationProfile.Mode.BASIC:advanced()?RotationProfile.Mode.ADVANCED:RotationProfile.Mode.SMOOTH;
        p.yawSpeed=f(yawSpeed);p.pitchSpeed=f(pitchSpeed);p.jitter=f(jitter);p.minSpeed=f(minSpeed);p.maxSpeed=f(maxSpeed);p.interpolation=f(interpolation);
        p.randomization=randomization.getValue();p.noiseEnabled=!"Aus".equals(noise.getValue());p.strength=f(strength);
        p.noise=FastNoiseLite.NoiseType.valueOf(p.noiseEnabled?noise.getValue():"OpenSimplex2");
        p.yawOffset=f(yawOffset);p.pitchOffset=f(pitchOffset);p.radius=f(radius);p.frequency=f(frequency);p.randomHitVec=randomHitVec.getValue();
        p.cellularDistance=FastNoiseLite.CellularDistanceFunction.valueOf(cellularDistance.getValue());
        p.cellularReturn=FastNoiseLite.CellularReturnType.valueOf(cellularReturn.getValue());p.cellularJitter=f(cellularJitter);
        p.fractal=FastNoiseLite.FractalType.valueOf(fractal.getValue());p.gain=f(gain);p.lacunarity=f(lacunarity);p.octaves=octaves.getValue().intValue();
        p.pingPong=f(pingPong);p.weighted=f(weighted);p.warp=FastNoiseLite.DomainWarpType.valueOf(warp.getValue());p.warpAmplitude=f(warpAmplitude);
        p.speedVariation=f(speedVariation);p.preset(preset.getValue());
        if(!placement) {
            if(!presetTiming.getValue()) { p.minSpeed=f(minSpeed);p.maxSpeed=f(maxSpeed);p.interpolation=f(interpolation); }
            p.smartAim=smartAim.getValue();p.stopOnTarget=stopOnTarget.getValue();
            p.heuristics=heuristics.getValue();p.interpolateRotation=interpolateRotation.getValue();p.advancedPitch=advancedPitch.getValue();
            p.pattern="Basic".equals(randomMode.getValue())?RotationProfile.Pattern.BASIC:"OnlyRotation".equals(randomMode.getValue())?RotationProfile.Pattern.ONLY_ROTATION:"Doubled".equals(randomMode.getValue())?RotationProfile.Pattern.DOUBLED:RotationProfile.Pattern.NOISE;
        }
        // Placement randomizes a point on the actual face, never an angular offset away from it.
        if(placement) p.jitter=p.yawOffset=p.pitchOffset=0;
        return p;
    }
    public static String canonicalName(String name) {
        if(name==null)return null;
        switch(name.toLowerCase(java.util.Locale.ROOT).replace("_","").replace(" ","")) {
            case "smartaim":return "smart_aim";
            case "stopontarget":return "stop_on_target";
            case "interpolaterotation":case "interpolationtoggle":return "interpolate_rotation";
            case "advancedrots":return "advanced_pitch";
            case "randommode":return "random_mode";
            case "randomstrength":case "jitterstrength":return "jitter";
            case "minrotationspeed":return "min_speed";
            case "maxrotationspeed":return "max_speed";
            case "smoothness":return "interpolation";
            case "jitterspeed":return "frequency";
            default:return name;
        }
    }
}
