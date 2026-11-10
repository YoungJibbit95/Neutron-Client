package me.youngjibbit.core.combat;

import me.youngjibbit.module.Module;
import me.youngjibbit.utils.math.FastNoiseLite;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;

/** Shared setting bindings, owned independently by Aura and visible AimAssist. */
public final class RotationSettings {
    public final ComboValue preset;
    public final SliderValue speedVariation;
    public final ComboValue speedCurve;
    public final SliderValue bezierStrength,bezierDuration;
    public final SliderValue predictionTicks,missChance;
    public final CheckBoxValue softArsenicMotion, hitpointVariation;
    public final ComboValue adaptiveSpeedPreset, adaptiveHitpointPreset;
    public final SliderValue adaptiveVariationStrength;
    public final ComboValue adaptiveAimPoint;
    public final CheckBoxValue adaptiveKeepAim;
    public final ComboValue adaptiveCurve, adaptiveNoiseProfile;
    public final SliderValue adaptiveMinAimSpeed, adaptiveMaxAimSpeed, adaptiveYawScale, adaptivePitchScale, adaptiveNoiseTempo;
    public final CheckBoxValue adaptiveAdvancedAim;
    private boolean adaptiveConfigImport, minAimImported, maxAimImported, yawScaleImported, pitchScaleImported, advancedAimImported;
    private final ComboValue hitpointProfile;
    private final SliderValue hitpointSpeed, hitpointStrength;
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
        mode=new ComboValue(modeName,parent,placement?new String[]{"Basic","Smooth","Advanced"}:assist?new String[]{"Legit","Test","Normal","Arsenic Lazy","Arsenic Heuristics"}:new String[]{"Basic","Smooth","Advanced","Arsenic Lazy","Arsenic Heuristics","Adaptive","AI Learned"},assist?2:0);
        softArsenicMotion = placement ? null : new CheckBoxValue("Arsenic Soft Motion", parent, false);
        hitpointVariation = placement || assist ? null : new CheckBoxValue("Hitpoint Variation", parent, false);
        hitpointProfile = hitpointVariation == null ? null : new ComboValue("Hitpoint Profile", parent,
                new String[]{"Ruhig", "Ausgewogen", "Dynamisch"}, 0);
        hitpointSpeed = hitpointVariation == null ? null : new SliderValue("Hitpoint Speed", parent, 1D, .1, 3, false);
        hitpointStrength = hitpointVariation == null ? null : new SliderValue("Hitpoint Strength", parent, .5, 0, 1, false);
        if (softArsenicMotion != null) softArsenicMotion.setVisibility(m -> arsenic());
        if (hitpointVariation != null) {
            hitpointVariation.setVisibility(m -> arsenic());
            for (Value<?> value : new Value<?>[]{hitpointProfile, hitpointSpeed, hitpointStrength})
                value.setVisibility(m -> arsenic() && hitpointVariation.getValue());
        }
        predictionTicks=placement?null:new SliderValue("Prediction Ticks",parent,assist?3D:1D,0,3,false);
        missChance=placement?null:new SliderValue("Miss Chance",parent,5D,0,50,true);
        preset=new ComboValue("Randomization Style",parent,new String[]{"Aus","Sanft","Organisch","Dynamisch","Benutzerdefiniert"},1);
        speedVariation=new SliderValue("Speed Variation",parent,0D,0,.9,false);
        speedCurve=placement?null:new ComboValue("Speed Curve",parent,new String[]{"Off","Bezier"},0);
        bezierStrength=placement?null:new SliderValue("Bezier Strength",parent,.5,0,.9,false);
        bezierDuration=placement?null:new SliderValue("Bezier Duration",parent,500D,100,2000,true);
        if(!placement) {
            bezierStrength.setVisibility(m->"Bezier".equals(speedCurve.getValue()));
            bezierDuration.setVisibility(m->"Bezier".equals(speedCurve.getValue()));
        }
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
        if(!placement) {
            predictionTicks.setVisibility(m->arsenic());
            missChance.setVisibility(m->"Arsenic Heuristics".equals(mode.getValue()));
            for(Value<?> v:new Value<?>[]{preset,speedVariation,speedCurve,bezierStrength,bezierDuration,smartAim,presetTiming,
                    heuristics,interpolateRotation,advancedPitch,randomMode,minSpeed,maxSpeed,interpolation,randomization,
                    strength,noise,frequency,yawOffset,pitchOffset,radius,cellularDistance,cellularReturn,cellularJitter,
                    fractal,gain,lacunarity,octaves,pingPong,weighted,warp,warpAmplitude,jitter,randomHitVec}) {
                java.util.function.Predicate<Module> visible=v.getVisibility();
                v.setVisibility(m->!arsenic() && !adaptive() && !aiLearned() && (visible==null || visible.test(m)));
            }
        }
        // Append new values after the legacy registration sequence. Only Aura owns them.
        boolean auraOnly = !assist && !placement;
        adaptiveSpeedPreset = auraOnly ? new ComboValue("Adaptive Speed", parent,
                new String[]{"Calm","Balanced","Swift"}, 1) {
            @Override public String getName() { return "Adaptive Response"; }
        } : null;
        adaptiveHitpointPreset = auraOnly ? new ComboValue("Adaptive Hitpoint", parent,
                new String[]{"Stable","Organic","Dynamic"}, 0) : null;
        adaptiveVariationStrength = auraOnly ? new SliderValue("Adaptive Variation Strength",
                parent, .5D, 0, 1, false) {
            @Override public String getName() { return "Adaptive Noise Amount"; }
        } : null;
        adaptiveAimPoint = auraOnly ? new ComboValue("Adaptive Aim Point",parent,
                new String[]{"Straight","Nearest","Legacy","Head","Torso","Legs"},0) : null;
        adaptiveKeepAim = auraOnly ? new CheckBoxValue("Adaptive Keep Valid Aim",parent,true) : null;
        adaptiveMinAimSpeed = auraOnly ? new SliderValue("Adaptive Min Aim Speed",parent,360D,20,1800,true) {
            @Override public String getName() { return "Adaptive Min Aim Speed (deg/s)"; }
            @Override public void setValue(Double value) { super.setValue(value); if(adaptiveConfigImport)minAimImported=true; }
        } : null;
        adaptiveMaxAimSpeed = auraOnly ? new SliderValue("Adaptive Max Aim Speed",parent,560D,20,1800,true) {
            @Override public String getName() { return "Adaptive Max Aim Speed (deg/s)"; }
            @Override public void setValue(Double value) { super.setValue(value); if(adaptiveConfigImport)maxAimImported=true; }
        } : null;
        adaptiveAdvancedAim = auraOnly ? new CheckBoxValue("Adaptive Advanced Aim",parent,false) {
            @Override public void setValue(Boolean value) { super.setValue(value); if(adaptiveConfigImport)advancedAimImported=true; }
        } : null;
        adaptiveYawScale = auraOnly ? new SliderValue("Adaptive Yaw Scale",parent,1D,1D/36,1,false) {
            @Override public void setValue(Double value) { super.setValue(value); if(adaptiveConfigImport)yawScaleImported=true; }
        } : null;
        adaptivePitchScale = auraOnly ? new SliderValue("Adaptive Pitch Scale",parent,1D,1D/36,1,false) {
            @Override public void setValue(Double value) { super.setValue(value); if(adaptiveConfigImport)pitchScaleImported=true; }
        } : null;
        adaptiveCurve = auraOnly ? new ComboValue("Adaptive Curve",parent,
                new String[]{"Adaptive","Linear","Relative"},2) : null;
        adaptiveNoiseProfile = auraOnly ? new ComboValue("Adaptive Noise Profile",parent,
                new String[]{"Off","Soft","Organic"},1) : null;
        adaptiveNoiseTempo = auraOnly ? new SliderValue("Adaptive Noise Tempo",parent,1D,.1,3,false) : null;
        if (auraOnly) {
            adaptiveSpeedPreset.setVisibility(m -> adaptive());
            adaptiveHitpointPreset.setVisibility(m -> adaptive() && !"Off".equals(adaptiveNoiseProfile.getValue())
                    && adaptiveVariationStrength.getValue()>0);
            adaptiveVariationStrength.setVisibility(m -> adaptive() && !"Off".equals(adaptiveNoiseProfile.getValue()));
            adaptiveAimPoint.setVisibility(m -> adaptive());
            adaptiveKeepAim.setVisibility(m -> adaptive());
            for(Value<?> value:new Value<?>[]{adaptiveMinAimSpeed,adaptiveMaxAimSpeed,adaptiveAdvancedAim,
                    adaptiveCurve,adaptiveNoiseProfile,adaptiveNoiseTempo})value.setVisibility(m->adaptive());
            adaptiveYawScale.setVisibility(m->adaptive() && adaptiveAdvancedAim.getValue());
            adaptivePitchScale.setVisibility(m->adaptive() && adaptiveAdvancedAim.getValue());
            adaptiveNoiseTempo.setVisibility(m->adaptive() && !"Off".equals(adaptiveNoiseProfile.getValue())
                    && adaptiveVariationStrength.getValue()>0);
            // These legacy controls remain persisted, but do not steer the closed Adaptive profile.
            yawSpeed.setVisibility(m->!adaptive());pitchSpeed.setVisibility(m->!adaptive());
            stopOnTarget.setVisibility(m->!adaptive());
        }
    }

    /** Called after legacy defaults, before incoming config values are assigned. */
    public void beginAdaptiveConfigUpdate() {
        if(adaptiveMinAimSpeed==null)return;
        adaptiveConfigImport=false;
        adaptiveMinAimSpeed.reset();adaptiveMaxAimSpeed.reset();adaptiveYawScale.reset();adaptivePitchScale.reset();
        adaptiveAdvancedAim.reset();
        adaptiveCurve.reset();adaptiveNoiseProfile.reset();adaptiveNoiseTempo.reset();
        minAimImported=maxAimImported=yawScaleImported=pitchScaleImported=advancedAimImported=false;
        adaptiveConfigImport=true;
    }

    /** Missing keys use the actual incoming legacy speeds, independent of JSON member order. */
    public void endAdaptiveConfigUpdate() {
        if(!adaptiveConfigImport)return;
        adaptiveConfigImport=false;
        double factor=AdaptivePresetMotion.Speed.values()[adaptiveSpeedPreset.getSelectedIndex()].speed;
        double yaw=Math.max(20,Math.min(720,yawSpeed.getValue()*20*factor));
        double pitch=Math.max(20,Math.min(720,pitchSpeed.getValue()*20*factor));
        double maximum=Math.max(yaw,pitch);
        if(!maxAimImported)adaptiveMaxAimSpeed.setValue(maximum);
        if(!minAimImported)adaptiveMinAimSpeed.setValue(Math.max(20,adaptiveMaxAimSpeed.getValue()*.65));
        if(!yawScaleImported)adaptiveYawScale.setValue(yaw/maximum);
        if(!pitchScaleImported)adaptivePitchScale.setValue(pitch/maximum);
        if(!advancedAimImported && (adaptiveYawScale.getValue()!=1 || adaptivePitchScale.getValue()!=1))adaptiveAdvancedAim.setValue(true);
        double lo=Math.min(adaptiveMinAimSpeed.getValue(),adaptiveMaxAimSpeed.getValue());
        double hi=Math.max(adaptiveMinAimSpeed.getValue(),adaptiveMaxAimSpeed.getValue());
        adaptiveMinAimSpeed.setValue(lo);adaptiveMaxAimSpeed.setValue(hi);
    }
    public boolean arsenic() { return !placement && mode.getValue().startsWith("Arsenic "); }
    /** Only Aura offers this appended option; legacy mode indices and defaults stay identical. */
    public boolean adaptive() { return !placement && "Adaptive".equals(mode.getValue()); }
    public boolean aiLearned() { return !placement && "AI Learned".equals(mode.getValue()); }
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
        p.adaptive=adaptive();
        p.aiLearned=aiLearned();
        if(p.adaptive) {
            p.adaptiveSpeed=AdaptivePresetMotion.Speed.values()[adaptiveSpeedPreset.getSelectedIndex()];
            p.adaptiveHitpoint=AdaptivePresetMotion.Hitpoint.values()[adaptiveHitpointPreset.getSelectedIndex()];
            p.adaptiveVariation=adaptiveVariationStrength.getValue().floatValue();
            p.adaptiveAimPoint=AdaptivePresetMotion.AimPoint.values()[adaptiveAimPoint.getSelectedIndex()];
            p.adaptiveKeepAim=adaptiveKeepAim.getValue();
            p.adaptiveExplicitControls=true;
            p.adaptiveMinAimSpeed=Math.min(f(adaptiveMinAimSpeed),f(adaptiveMaxAimSpeed));
            p.adaptiveMaxAimSpeed=Math.max(f(adaptiveMinAimSpeed),f(adaptiveMaxAimSpeed));
            p.adaptiveYawScale=f(adaptiveYawScale);p.adaptivePitchScale=f(adaptivePitchScale);p.adaptiveNoiseTempo=f(adaptiveNoiseTempo);
            p.adaptiveCurve=AdaptiveMotionController.Curve.values()[adaptiveCurve.getSelectedIndex()];
            p.adaptiveNoise=AdaptivePresetMotion.Noise.values()[adaptiveNoiseProfile.getSelectedIndex()];
            p.yawSpeed=p.adaptiveMaxAimSpeed*p.adaptiveYawScale/20;
            p.pitchSpeed=p.adaptiveMaxAimSpeed*p.adaptivePitchScale/20;
            // No hidden legacy setting is copied into the Adaptive geometry/motion path.
            return p;
        }
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
            p.speedCurve="Bezier".equals(speedCurve.getValue())?RotationProfile.SpeedCurve.BEZIER:RotationProfile.SpeedCurve.OFF;
            p.bezierStrength=f(bezierStrength);p.bezierDurationMs=f(bezierDuration);
            if(!presetTiming.getValue()) { p.minSpeed=f(minSpeed);p.maxSpeed=f(maxSpeed);p.interpolation=f(interpolation); }
            p.smartAim=smartAim.getValue();p.stopOnTarget=stopOnTarget.getValue();
            p.heuristics=heuristics.getValue();p.interpolateRotation=interpolateRotation.getValue();p.advancedPitch=advancedPitch.getValue();
            p.pattern="Basic".equals(randomMode.getValue())?RotationProfile.Pattern.BASIC:"OnlyRotation".equals(randomMode.getValue())?RotationProfile.Pattern.ONLY_ROTATION:"Doubled".equals(randomMode.getValue())?RotationProfile.Pattern.DOUBLED:RotationProfile.Pattern.NOISE;
        }
        // Placement randomizes a point on the actual face, never an angular offset away from it.
        if(placement) p.jitter=p.yawOffset=p.pitchOffset=0;
        if(arsenic()) {
            p.arsenic="Arsenic Lazy".equals(mode.getValue())?RotationProfile.ArsenicMode.LAZY:RotationProfile.ArsenicMode.HEURISTICS;
            p.softArsenicMotion = softArsenicMotion.getValue();
            if (hitpointVariation != null && hitpointVariation.getValue()) {
                p.hitpointNoise = RotationProfile.HitpointNoise.values()[hitpointProfile.getSelectedIndex()];
                p.hitpointStrength = f(hitpointStrength);
                p.hitpointSpeed = f(hitpointSpeed);
            }
            p.predictionTicks=f(predictionTicks);p.missChance=f(missChance)/100f;
        }
        return p;
    }
    public static String canonicalName(String name) {
        if(name==null)return null;
        switch(name.toLowerCase(java.util.Locale.ROOT).replace("_","").replace(" ","")) {
            case "adaptivespeed":case "adaptiveresponse":return "adaptive_speed";
            case "adaptivevariationstrength":case "adaptivenoiseamount":return "adaptive_variation_strength";
            case "adaptiveminaimspeed":return "adaptive_min_aim_speed";
            case "adaptivemaxaimspeed":return "adaptive_max_aim_speed";
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
