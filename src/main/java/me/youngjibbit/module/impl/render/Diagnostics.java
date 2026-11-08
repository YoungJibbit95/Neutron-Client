package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.diagnostics.CorrectionHistory;
import me.youngjibbit.core.diagnostics.RenderTimings;
import me.youngjibbit.core.network.PacketDelays;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.network.PlaySessionEvent;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.exploit.*;
import me.youngjibbit.notifications.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Passive diagnostics: no packet sends, queue pumping, authority predicates or automatic configuration changes. */
@ModuleInfo(name="Diagnostics", description="Movable render, rotation, network and possible setback diagnostics", category=Category.Render)
public final class Diagnostics extends Module {
    private static Diagnostics active;
    final ComboValue displayMode = new ComboValue("Display Mode", this, "Text", new String[]{"Text", "Simple"});
    private final Deque<Integer> pingHistory = new ArrayDeque<>();
    private final CheckBoxValue performanceWidget=new CheckBoxValue("Performance Widget",this,true);
    private final CheckBoxValue rotationWidget=new CheckBoxValue("Rotation Widget",this,true);
    private final CheckBoxValue networkWidget=new CheckBoxValue("Network Widget",this,true);
    private final CheckBoxValue flagWidget=new CheckBoxValue("Flag Widget",this,true);
    private final CheckBoxValue rotationUser=new CheckBoxValue("Rotation User Details",this,true);
    private final CheckBoxValue rotationDeveloper=new CheckBoxValue("Rotation Developer Details",this,false);
    private final CheckBoxValue networkValues=new CheckBoxValue("Network Values",this,false);
    private final CheckBoxValue flagDetector=new CheckBoxValue("Flag Detector",this,true);
    private final CheckBoxValue flagDeveloper=new CheckBoxValue("Flag Developer Details",this,false);
    private final CheckBoxValue notifications=new CheckBoxValue("Setback Notifications",this,false);
    private final CheckBoxValue consolePerformance=new CheckBoxValue("Performance Console",this,false);
    private final CheckBoxValue consoleRotation=new CheckBoxValue("Rotation Console",this,false);
    private final CheckBoxValue consoleNetwork=new CheckBoxValue("Network Console",this,false);
    private final CheckBoxValue consoleValues=new CheckBoxValue("Console Active Values",this,false);
    private final CheckBoxValue consoleFlags=new CheckBoxValue("Flag Console",this,false);
    private final SliderValue logInterval=new SliderValue("Console Interval MS",this,2000.0,500,10000,true);
    private final SliderValue notificationCooldown=new SliderValue("Notification Cooldown MS",this,3000.0,1000,15000,true);
    private final SliderValue scale=new SliderValue("Widget Scale",this,1.0,.6,1.5);
    private final CheckBoxValue animate=new CheckBoxValue("Animate Meters",this,true);
    private final CheckBoxValue background=new CheckBoxValue("Background",this,true);
    private final CheckBoxValue blur=new CheckBoxValue("Blur",this,false);
    private final CheckBoxValue glow=new CheckBoxValue("Glow",this,false);
    // All four panels share this compositor and the module's single set of local effect controls.
    private final ArrayListSurface surface=new ArrayListSurface(this);
    final Widget[] widgets={new Widget("Performance",12,14),new Widget("Rotation",276,14),
            new Widget("Network",12,174),new Widget("Flags",276,160)};
    private final CorrectionHistory corrections=new CorrectionHistory();
    private RenderTimings.Snapshot renderSnapshot=RenderTimings.Snapshot.EMPTY;
    private RotationManager.DiagnosticSnapshot rotation;
    private PacketDelays.Diagnostics queue;
    private List<String> network=Collections.emptyList(), values=Collections.emptyList();
    private List<Panel> cachedPanels;
    private int ping=-1;
    private int captureFailures;
    private long sampledAt, loggedAt, notifiedAt, lastPaint,runId;
    private boolean notified, previousButton;
    private WeakReference<Object> world=new WeakReference<>(null), player=new WeakReference<>(null), connection=new WeakReference<>(null);

    public Diagnostics() {
        displayMode.setOnChange(mode -> restoreConfiguredPositions());
        rotationUser.setVisibility(m->rotationWidget.getValue()); rotationDeveloper.setVisibility(m->rotationWidget.getValue());
        networkValues.setVisibility(m->networkWidget.getValue()); flagDeveloper.setVisibility(m->flagWidget.getValue());
        notifications.setVisibility(m->flagDetector.getValue()); consoleFlags.setVisibility(m->flagDetector.getValue());
    }
    @Override public void onEnable() {
        active=this; resetRun(); previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0); updateProfiling(); super.onEnable();
    }
    @Override public void onDisable() {
        if(active==this) {active=null;RenderTimings.INSTANCE.disable();}
        cancelDrag();surface.close();resetRun();super.onDisable();
    }
    @Override public void onValueChange() {
        cachedPanels=null;
        if(widgets!=null)for(Widget widget:widgets)widget.syncPosition();
        if(active==this)updateProfiling();
        if(flagDetector!=null&&!flagDetector.getValue()){corrections.clear();runId++;}
    }
    private void updateProfiling() {
        boolean requested=performanceWidget.getValue()||consolePerformance.getValue();
        if(requested&&!RenderTimings.INSTANCE.isEnabled())RenderTimings.INSTANCE.enable(mc.mcProfiler);
        else if(!requested&&RenderTimings.INSTANCE.isEnabled()){RenderTimings.INSTANCE.disable();renderSnapshot=RenderTimings.Snapshot.EMPTY;}
    }
    private void cancelDrag() {if(widgets!=null)for(Widget w:widgets){w.dragging=false;HudDrag.cancel(w);}}
    /** Routes one wheel event only to a visible, overflowing diagnostics panel. Chat otherwise keeps its normal scroll. */
    public static boolean scrollAt(float mouseX,float mouseY,int wheel) {
        Diagnostics d=active;
        if(d==null||wheel==0||!HUD.visible()||!HUD.editable()||!editing(mc.currentScreen)||mc.gameSettings.showDebugInfo)return false;
        for(int i=d.widgets.length-1;i>=0;i--) {
            Widget w=d.widgets[i];
            if(w.visible&&w.maxScroll>0&&HudDrag.roundedContains(mouseX,mouseY,w.x,w.y,w.width,w.height,6)) {
                w.scroll=Math.max(0,Math.min(w.maxScroll,w.scroll+(wheel>0?-1:1)*2));return true;
            }
        }
        return false;
    }
    public void resetPositions() {
        for(Widget w:widgets){w.positionX.reset();w.positionY.reset();w.x=w.positionX.getValue().floatValue();w.y=w.positionY.getValue().floatValue();w.scroll=0;}
        cancelDrag();
    }
    void restoreConfiguredPositions() {
        for(Widget w:widgets){w.x=w.positionX.getValue().floatValue();w.y=w.positionY.getValue().floatValue();}
        cancelDrag();
    }
    private static int contentHeight(int height) {return mc!=null&&mc.currentScreen instanceof DiagnosticsEditor?Math.max(1,height-DiagnosticsEditor.CONTROL_STRIP):height;}
    public void clearHistory() {corrections.clear();notified=false;cachedPanels=null;runId++;}
    private void resetRun() {
        runId++;
        if(active==this&&RenderTimings.INSTANCE.isEnabled())RenderTimings.INSTANCE.enable(mc.mcProfiler);
        corrections.clear();renderSnapshot=RenderTimings.Snapshot.EMPTY;rotation=null;queue=null;network=values=Collections.emptyList();
        cachedPanels=null;
        pingHistory.clear();
        for(Widget w:widgets){w.graphSpring.reset();w.graphTail=Float.NaN;}
        ping=-1;sampledAt=loggedAt=notifiedAt=lastPaint=0;notified=false;
        world.clear();player.clear();connection.clear();cancelDrag();
    }
    private PacketPipeline pipeline() {return mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager().getClientPackets();}
    private void refreshContext() {
        PacketPipeline pipe=pipeline();
        if(world.get()!=mc.theWorld||player.get()!=mc.thePlayer||connection.get()!=pipe) {
            resetRun();world=new WeakReference<>(mc.theWorld);player=new WeakReference<>(mc.thePlayer);connection=new WeakReference<>(pipe);
        }
    }
    @Subscribe private final Listener<WorldEvent> worldChanged=new Listener<>(event->resetRun());
    @Subscribe private final Listener<PlaySessionEvent> sessionChanged=new Listener<>(event->{
        if(event.getState()==PlaySessionEvent.State.ENDED)resetRun();
    });
    @Subscribe private final Listener<TickEvent> tick=new Listener<>(event->{
        refreshContext();sample(System.nanoTime());
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0);
        if(!editing(mc.currentScreen)){cancelDrag();previousButton=down;}
    });
    @Subscribe private final Listener<Event2D> render=new Listener<>(event->{
        refreshContext();sample(System.nanoTime());
        if(!HUD.visible()||mc.gameSettings.showDebugInfo||mc.thePlayer==null&&!editing(mc.currentScreen)) {cancelDrag();previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0);return;}
        renderWidgets(event.getSr());
    });
    public static boolean editing(GuiScreen screen) {return screen instanceof GuiChat||screen instanceof DiagnosticsEditor;}
    private void sample(long now) {
        if(sampledAt!=0&&now-sampledAt<500_000_000L)return;sampledAt=now;cachedPanels=null;
        if(RenderTimings.INSTANCE.isEnabled())renderSnapshot=RenderTimings.INSTANCE.sample();
        PacketPipeline pipe=pipeline();
        rotation=mc.thePlayer==null?null:RuntimeServices.rotation().diagnosticSnapshot(mc.thePlayer,mc.theWorld,pipe);
        queue=pipe==null?null:pipe.delays().diagnosticSnapshot();
        NetworkPlayerInfo info=mc.thePlayer==null||mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        ping=info==null?-1:Math.max(0,info.getResponseTime());
        // Both displays use this same half-second observation. Preserve missing samples as gaps.
        if (pingHistory.size() == 60) pingHistory.removeFirst();
        pingHistory.addLast(ping);
        List<String> states=new ArrayList<>(), settings=new ArrayList<>();
        if(Pulsar.INSTANCE.getMm()!=null)for(Module module:Pulsar.INSTANCE.getMm().getAllModules())
            if(module.isToggled()&&module.getCategory()==Category.Exploit) {
                states.add(module.getName()+": "+status(module));
                if(networkValues.getValue()||consoleValues.getValue())settings.addAll(activeValues(module));
            }
        network=Collections.unmodifiableList(states);values=Collections.unmodifiableList(settings);
        if(loggedAt==0||now-loggedAt>=logInterval.getValue()*1_000_000L){loggedAt=now;logSnapshots(now);}
    }
    private static String status(Module module) {
        if(module instanceof Backtrack)return ((Backtrack)module).getStatus();
        if(module instanceof LagRange)return ((LagRange)module).getStatus();
        if(module instanceof Tickbase)return ((Tickbase)module).getStatus();
        return module.getInfo().isEmpty()?"enabled; no runtime telemetry":module.getInfo();
    }
    private static List<String> activeValues(Module module) {
        List<String> result=new ArrayList<>();
        for(Value<?> value:module.getValues())if(value.isVisible()&&(value instanceof SliderValue||value instanceof ComboValue||value instanceof CheckBoxValue))
            result.add(module.getName()+" / "+value.getName()+" = "+value.getValue());
        return result;
    }
    private void logSnapshots(long now) {
        if(consolePerformance.getValue())for(RenderTimings.Row row:renderSnapshot.sections)
            log("render",now,"section="+row.name+" cpuMsPerFrame="+fmt(row.milliseconds)+" measuredShare="+fmt(row.share*100)+" frames="+renderSnapshot.frames);
        if(consolePerformance.getValue())for(RenderTimings.Row row:renderSnapshot.surfaces)
            log("render-surface",now,"scope="+row.name+" inclusiveCpuMsPerFrame="+fmt(row.milliseconds)+" overlapsSections=true");
        if(consoleRotation.getValue()&&rotation!=null)log("rotation",now,rotationDescription(rotation));
        if(consoleNetwork.getValue()) {
            log("network",now,"serverReportedPingMs="+ping+" "+queueDescription()+" serverRttEffect=UNKNOWN");
            for(String state:network)log("network",now,state);
            if(consoleValues.getValue())for(String value:values)log("value",now,value);
        }
    }
    private static void log(String channel,long now,String content) {System.out.println("[NeutronDiagnostics]["+channel+"] nanos="+now+" "+content);}
    private static String rotationDescription(RotationManager.DiagnosticSnapshot r) {
        return "seq="+r.sequence+" phase="+(r.collecting?"collecting":"finished")+" requestedBy="+r.requestedBy+" priority="+r.priority+
                " localAccepted="+r.accepted+" lastWireOwner="+r.owner+" lastWire="+(r.hasEffective?fmt(r.wireYaw)+"/"+fmt(r.wirePitch):"UNKNOWN")+
                " requested="+fmt(r.requestedYaw)+"/"+fmt(r.requestedPitch)+" contenders="+r.contenders+" returnLeasePresent="+r.returning+" serverAccepted=UNKNOWN";
    }
    private String queueDescription() {return queue==null?"queue=unavailable":"in="+queue.inbound+" out="+queue.outbound+" owners="+queue.owners+
            " oldestMs="+queue.oldestMs+" remainingHoldMs="+queue.remainingMs+" requestedMaxMs="+queue.configuredMs;}

    /** Captured before pipeline lifecycle invalidation; only published after successful real S08 application. */
    public static CorrectionProbe captureCorrection(S08PacketPlayerPosLook packet,PacketPipeline pipeline,boolean replay) {
        try {return captureCorrectionSafely(packet,pipeline,replay);}
        catch(RuntimeException failure){if(active!=null)active.captureFailures++;return null;}
    }
    private static CorrectionProbe captureCorrectionSafely(S08PacketPlayerPosLook packet,PacketPipeline pipeline,boolean replay) {
        Diagnostics d=active;
        if(d==null||!d.flagDetector.getValue()||mc==null||mc.thePlayer==null||mc.theWorld==null||d.pipeline()!=pipeline)return null;
        d.refreshContext();
        RotationManager.DiagnosticSnapshot r=RuntimeServices.rotation().diagnosticSnapshot(mc.thePlayer,mc.theWorld,pipeline);
        List<String> contexts=new ArrayList<>(), settings=new ArrayList<>(), advice=new ArrayList<>();
        if(r.hasEffective&&(r.accepted||r.collecting)&&r.owner!=null)contexts.add("last local rotation: "+r.owner+" (seq "+r.sequence+")");
        if(Pulsar.INSTANCE.getMm()!=null)for(Module m:Pulsar.INSTANCE.getMm().getAllModules())if(m.isToggled()&&
                (m.getCategory()==Category.Exploit||m.getCategory()==Category.Movement||"scaffold".equals(m.getId())||"aura".equals(m.getId()))) {
            contexts.add(m.getName()+" enabled"+(m.getCategory()==Category.Exploit?": "+status(m):""));
            settings.addAll(activeValues(m));
            for(Value<?> v:m.getValues())if(v.isVisible()&&v instanceof SliderValue) {
                String name=v.getName().toLowerCase(Locale.ROOT);
                if(name.contains("speed")||name.contains("range")||name.contains("delay")||name.contains(" ms")||name.contains("ticks"))
                    advice.add(m.getName()+" / "+v.getName()+"="+v.getValue());
            }
        }
        PacketDelays.Diagnostics q=pipeline.delays().diagnosticSnapshot();
        contexts.add("queued in/out="+q.inbound+"/"+q.outbound+", oldest="+q.oldestMs+"ms");
        String suggestion=advice.isEmpty()?"No setting-specific evidence; compare vanilla and server logs."
                :"A/B test one setting at a time: "+String.join(", ",advice.subList(0,Math.min(6,advice.size())))+". No safe value can be inferred.";
        return new CorrectionProbe(d,mc.thePlayer,mc.theWorld,mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ,
                mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch,mc.thePlayer.ticksExisted<100||!mc.thePlayer.isEntityAlive(),replay,
                String.join("; ",contexts),String.join("; ",settings),suggestion);
    }
    public static void appliedCorrection(CorrectionProbe probe) {
        try {appliedCorrectionSafely(probe);}
        catch(RuntimeException failure){if(active!=null)active.captureFailures++;}
    }
    private static void appliedCorrectionSafely(CorrectionProbe probe) {
        Diagnostics d=active;
        if(probe==null||d!=probe.diagnostics||d.runId!=probe.runId||!d.flagDetector.getValue()||mc.thePlayer!=probe.player.get()||mc.theWorld!=probe.world.get())return;
        long now=System.nanoTime();
        // The vanilla handler resolves relative flags. Compare the position that it actually applied.
        double distance=Math.sqrt(sq(mc.thePlayer.posX-probe.x)+sq(mc.thePlayer.posY-probe.y)+sq(mc.thePlayer.posZ-probe.z));
        float angle=Math.max(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw-probe.yaw)),Math.abs(mc.thePlayer.rotationPitch-probe.pitch));
        CorrectionHistory.Entry entry=d.corrections.record(now,distance,angle,probe.initialization,probe.replay,probe.context,probe.values,probe.advice);
        if(entry==null)return;
        d.cachedPanels=null;
        if(d.consoleFlags.getValue()) {
            log("correction",now,"event="+entry.id+" kind="+entry.kind()+" displacement="+fmt(distance)+" angle="+fmt(angle)+" cause=UNCONFIRMED serverCheck=UNKNOWN context="+entry.context);
            log("correction",now,"event="+entry.id+" review="+entry.advice);
            if(d.consoleValues.getValue())log("correction-values",now,"event="+entry.id+" "+entry.values);
        }
        if(entry.possibleSetback&&d.notifications.getValue()&&(!d.notified||now-d.notifiedAt>=d.notificationCooldown.getValue()*1_000_000L)) {
            d.notified=true;d.notifiedAt=now;
            NotificationManager.show(new Notification(NotificationType.WARNING,"Possible setback",fmt(distance)+" blocks / "+fmt(angle)+" deg. Cause unconfirmed; see Diagnostics.",4));
        }
    }
    private static double sq(double n){return n*n;}
    public static final class CorrectionProbe {
        final Diagnostics diagnostics;
        final long runId;
        final WeakReference<Object> player,world;
        final double x,y,z;final float yaw,pitch;final boolean initialization,replay;
        final String context,values,advice;
        CorrectionProbe(Diagnostics d,Object player,Object world,double x,double y,double z,float yaw,float pitch,boolean init,boolean replay,String context,String values,String advice) {
            diagnostics=d;runId=d.runId;this.player=new WeakReference<>(player);this.world=new WeakReference<>(world);
            this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;initialization=init;this.replay=replay;this.context=context;this.values=values;this.advice=advice;
        }
    }

    List<Panel> panels() {
        if(cachedPanels!=null)return cachedPanels;
        if ("Simple".equals(displayMode.getValue())) {
            cachedPanels = simplePanels();
            return cachedPanels;
        }
        List<Panel> panels=new ArrayList<>();
        if(performanceWidget.getValue()) {
            Panel p=new Panel(widgets[0],"Performance",MinecraftFps()+" FPS");
            p.line("Render CPU / frame",fmt(renderSnapshot.sections.stream().mapToDouble(r->r.milliseconds).sum())+" ms");
            int shown=0;for(RenderTimings.Row row:renderSnapshot.sections) {if(shown++==3)break;p.meter(shortSection(row.name),fmt(row.milliseconds)+" ms  "+Math.round(row.share*100)+"%",row.share);}
            if(renderSnapshot.sections.isEmpty())p.line("Waiting for render samples","");
            int surfaces=0;for(RenderTimings.Row row:renderSnapshot.surfaces){if(surfaces++==2)break;p.line("UI: "+row.name,fmt(row.milliseconds)+" ms");}
            p.graph=renderSnapshot.frameMilliseconds.clone();p.footer="CPU submission; HUD overlaps. GPU unmeasured.";panels.add(p);
        }
        if(rotationWidget.getValue()) {
            Panel p=new Panel(widgets[1],"Rotation Manager",rotation==null?"NO WORLD":rotation.collecting?"COLLECTING":rotation.accepted&&rotation.owner!=null?"ACTIVE":"IDLE");
            if(rotationUser.getValue()) {
                p.line("Local owner",rotation==null||!rotation.accepted||rotation.owner==null?"Vanilla / none":rotation.owner);
                p.line("Camera yaw / pitch",mc.thePlayer==null?"--":fmt(mc.thePlayer.rotationYaw)+" / "+fmt(mc.thePlayer.rotationPitch));
                p.line("Last wire yaw / pitch",rotation==null||!rotation.hasEffective?"Unknown":fmt(rotation.wireYaw)+" / "+fmt(rotation.wirePitch));
            }
            if(rotationDeveloper.getValue()&&rotation!=null) {
                p.line("Sequence / candidates",rotation.sequence+" / "+rotation.contenders);
                p.line("Request / priority",(rotation.requestedBy==null?"none":rotation.requestedBy)+" / "+rotation.priority);
                p.line("Requested yaw / pitch",fmt(rotation.requestedYaw)+" / "+fmt(rotation.requestedPitch));
                p.line("Local acceptance / return lease",rotation.accepted+" / "+rotation.returning);
            }
            if(p.lines.isEmpty())p.line("Details hidden in settings","");
            p.footer="Local transport evidence; server acceptance unknown.";panels.add(p);
        }
        if(networkWidget.getValue()) {
            Panel p=new Panel(widgets[2],"Network",ping<0?"NO PING":ping+" ms");
            p.line("Server-reported ping",ping<0?"Unknown":ping+" ms");
            p.line("Queued inbound / outbound",queue==null?"--":queue.inbound+" / "+queue.outbound);
            p.meter("Local hold remaining",queue==null?"--":queue.remainingMs+" ms",queue==null?0:queue.remainingMs/(double)PacketDelays.MAX_DELAY_MS);
            p.line("Requested delay / oldest",queue==null?"--":queue.configuredMs+" / "+queue.oldestMs+" ms");
            if(network.isEmpty())p.line("Exploit modules","None active");
            for(String state:network)p.wrap(state);
            if(networkValues.getValue())for(String v:values)p.wrap(v);
            p.footer="Local hold is not RTT. Ping effect unknown.";panels.add(p);
        }
        if(flagWidget.getValue()) {
            CorrectionHistory.Entry last=corrections.latest();
            Panel p=new Panel(widgets[3],"Flag Detector",!flagDetector.getValue()?"OFF":last==null?"LISTENING":last.possibleSetback?"CORRECTION":"SYNC");
            if(!flagDetector.getValue())p.line("Enable Flag Detector in settings","");
            else if(last==null){p.line("No corrections observed","");p.line("Server flags require server logs","");}
            else {
                p.line(last.kind(),"#"+last.id);
                p.line("Displacement / rotation",fmt(last.distance)+" blocks / "+fmt(last.angle)+" deg");
                p.line("Observed",Math.max(0,(System.nanoTime()-last.nanos)/1_000_000_000L)+"s ago");
                p.wrap("Context (unconfirmed): "+last.context);
                if(flagDeveloper.getValue()){p.wrap(last.advice);p.wrap("Values at correction: "+last.values);p.line("Capture failures",Integer.toString(captureFailures));}
                if(flagDeveloper.getValue())for(CorrectionHistory.Entry entry:corrections.snapshot())if(entry!=last)p.line("#"+entry.id+" "+entry.kind(),fmt(entry.distance)+" blocks");
            }
            p.footer="Observed S08; cause and server check unconfirmed.";panels.add(p);
        }
        cachedPanels=panels;return panels;
    }
    private List<Panel> simplePanels() {
        List<Panel> panels = new ArrayList<>(4);
        if (performanceWidget.getValue()) {
            double[] frames = renderSnapshot.frameMilliseconds;
            double[] display=DiagnosticsPlots.easedTail(frames,widgets[0].graphTail);
            String latest = display.length == 0 ? "No samples" : fmt(display[display.length-1]) + " ms";
            Panel p = new Panel(widgets[0], "Frame interval", latest);
            p.plot = (theme, font, ink) -> DiagnosticsPlots.series(display, 16.7, theme, font, ink, "Frames");
            panels.add(p);
        }
        if (rotationWidget.getValue()) {
            boolean available = rotation != null && rotation.hasEffective;
            float yaw = available ? rotation.wireYaw : Float.NaN;
            float pitch = available ? rotation.wirePitch : Float.NaN;
            Panel p = new Panel(widgets[1], "Wire rotation", available ? Math.round(yaw) + "\u00b0 / " + Math.round(pitch) + "\u00b0" : "No sample");
            p.plot = (theme, font, ink) -> DiagnosticsPlots.direction(yaw, pitch, theme, font, ink);
            panels.add(p);
        }
        if (networkWidget.getValue()) {
            double[] samples = pingHistory.stream().mapToDouble(Integer::doubleValue).toArray();
            double[] display=DiagnosticsPlots.easedTail(samples,widgets[2].graphTail);
            Panel p = new Panel(widgets[2], "Reported ping", ping < 0 ? "No sample" :
                    Math.round(Float.isFinite(widgets[2].graphTail)?widgets[2].graphTail:ping) + " ms");
            p.plot = (theme, font, ink) -> DiagnosticsPlots.series(display, 50, theme, font, ink, "Samples");
            panels.add(p);
        }
        if (flagWidget.getValue()) {
            List<CorrectionHistory.Entry> entries = corrections.snapshot();
            long now = System.nanoTime();
            boolean enabled = flagDetector.getValue();
            Panel p = new Panel(widgets[3], "Corrections", enabled ? "Last 30 s" : "Detector off");
            p.plot = (theme, font, ink) -> DiagnosticsPlots.timeline(entries, now, theme, font, ink);
            panels.add(p);
        }
        return panels;
    }

    private static int MinecraftFps(){return net.minecraft.client.Minecraft.getDebugFPS();}
    private static String shortSection(String path){String p=path.replace("root/render/gameRenderer", "render");return p.length()>32?"..."+p.substring(p.length()-29):p;}
    static String fmt(double value){return Double.isFinite(value)?String.format(Locale.ROOT,"%.1f",value):"--";}

    void renderWidgets(ScaledResolution sr) {
        long now=System.nanoTime();float dt=lastPaint==0?0:Math.min(.1f,(now-lastPaint)/1e9f);lastPaint=now;
        // Animate only the newest observed graph point, never the underlying history.
        ClientSettings settings=ClientSettings.current();
        boolean animated=animate.getValue()&&(settings==null||settings.animations.getValue());
        boolean reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        double[] frameTimes=renderSnapshot.frameMilliseconds;
        float lastMs=frameTimes.length==0?Float.NaN:(float)frameTimes[frameTimes.length-1];
        updateGraphTail(widgets[0],lastMs,dt,animated,reduced,intensity);
        updateGraphTail(widgets[2],ping>=0?ping:Float.NaN,dt,animated,reduced,intensity);
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0),edit=editing(mc.currentScreen);
        float mx=Mouse.isCreated()?Mouse.getX()/(float)sr.getScaleFactor():-1;
        float my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/(float)sr.getScaleFactor():-1;
        List<Panel> panels=panels();
        for(Widget w:widgets){w.visible=panels.stream().anyMatch(p->p.widget==w);if(!w.visible){w.dragging=false;HudDrag.cancel(w);}}
        for(Panel panel:panels) {
            layout(panel,sr.getScaledWidth(),sr.getScaledHeight());
            interact(panel,edit,down,!previousButton,mx,my,sr.getScaledWidth(),sr.getScaledHeight());
            paint(panel,dt);
        }
        previousButton=down;
    }
    private static void updateGraphTail(Widget w,float value,float dt,
                                        boolean animated,boolean reduced,String intensity){
        if(!Float.isFinite(value)||value<0){w.graphSpring.reset();w.graphTail=Float.NaN;return;}
        w.graphTail=Math.max(0,w.graphSpring.step(value,dt,animated,reduced,intensity));
    }
    void layout(Panel p,int sw,int sh) {
        sh=contentHeight(sh);
        p.visibleRows=Math.min(p.lines.size(),rotationDeveloper.getValue()&&p.widget==widgets[1]||flagDeveloper.getValue()&&p.widget==widgets[3]||networkValues.getValue()&&p.widget==widgets[2]?12:7);
        p.widget.scroll=Math.max(0,Math.min(p.widget.scroll,p.lines.size()-p.visibleRows));
        p.widget.maxScroll=p.lines.size()-p.visibleRows;
        p.height=p.plot == null ? 38+p.visibleRows*15+(p.graph==null?0:31) : DiagnosticsPlots.HEIGHT;
        p.scale=(float)Math.max(.01,Math.min(scale.getValue(),Math.min((sw-8)/252.0,(sh-8)/(double)p.height)));
        if(p.widget.defaultPosition()) {
            // Untouched widgets form a responsive two-column grid. A drag or exact position opts that widget out.
            int index=Arrays.asList(widgets).indexOf(p.widget);
            float cellWidth=Math.max(1,(sw-36)/2f),cellHeight=Math.max(1,(sh-36)/2f);
            p.scale=Math.min(p.scale,Math.min(cellWidth/252,cellHeight/p.height));
            p.widget.x=12+(index%2)*(cellWidth+12);p.widget.y=12+(index/2)*(cellHeight+12);
        }
        p.widget.width=252*p.scale;p.widget.height=p.height*p.scale;p.widget.clamp(sw,sh);
    }
    void interact(Panel p,boolean edit,boolean down,boolean pressed,float mx,float my,int sw,int sh) {
        sh=contentHeight(sh);
        Widget w=p.widget;boolean was=w.dragging;
        w.dragging=HudDrag.capture(w,edit,down,pressed,my<sh&&HudDrag.roundedContains(mx,my,w.x,w.y,w.width,w.height,6*p.scale));
        if(w.dragging&&!was){w.grabX=mx-w.x;w.grabY=my-w.y;}
        if(w.dragging){w.x=HUD.snap(mx-w.grabX);w.y=HUD.snap(my-w.grabY);w.clamp(sw,sh);w.positionX.setValue((double)w.x);w.positionY.setValue((double)w.y);}
    }
    void paint(Panel p,float dt) {
        Widget w=p.widget;float x=w.x,y=w.y,s=p.scale;
        final boolean modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        final boolean filled=background.getValue(),blurred=blur.getValue(),
                glowing=!modern&&glow.getValue();
        // Immutable geometry, policy and theme are frozen before deferred execution.
        Theme theme=surface.theme(Theme.resolve(Theme.getDefaultTheme()));
        GuiShaderBloom.afterBatch(()->surface.panel(x,y,252*s,p.height*s,
                (modern?10:6)*s,theme,filled,blurred,glowing,modern?3.5f:4,
                modern?.84f:.91f,modern?.14f:.4f,()->{
            try(RenderState state=new RenderState()) {
                state.screenSpace();glTranslatef(x,y,0);glScalef(s,s,1);
                if(modern&&filled)
                    UIUtils.drawRound(2,2,248,Math.max(1,p.height-4),9,
                            new Color(20,22,32,188));
                TTFFontRenderer heading=font("Product-Sans-Bold 18"),body=font("Product-Sans-Regular 16"),small=font("Product-Sans-Regular 12");
                int ink=modern?0xfff5f0f8:theme.isCustom()?theme.getTextColor().getRGB():0xfff0f3fa;
                int muted=modern?0xffc6bdd0:
                        Theme.interpolateRGB(ink,theme.getPrimaryGradientRGB(),.22f)|0xff000000;
                heading.drawString(p.title,modern?12:9,7,ink);
                small.drawString(p.status,243-small.getWidth(p.status),10,muted);
                if (p.plot != null) {
                    p.plot.draw(theme, small, muted);
                    return;
                }
                UIUtils.drawGradientHorizontal(9,23,234,modern?1.2f:1,.5f,
                        theme.getPrimaryGradientColor(),theme.getSecondaryGradientColor());
                float yy=29;
                for(Line line:p.lines.subList(w.scroll,Math.min(p.lines.size(),w.scroll+p.visibleRows))) {
                    String right=fit(line.value,body,107),left=fit(line.label,body,right.isEmpty()?234:Math.max(0,226-body.getWidth(right)));
                    body.drawString(left,9,yy,ink);
                    if(!right.isEmpty())body.drawString(right,243-body.getWidth(right),yy,muted);
                    if(line.ratio>=0) {
                        double target=Math.max(0,Math.min(1,line.ratio));Double prior=w.meters.get(line.label);
                        double value=prior==null||!animate.getValue()||!UiEffects.motion()?target:prior+(target-prior)*UiEffects.response(dt,24);
                        if(w.meters.size()>64)w.meters.clear();w.meters.put(line.label,value);
                        UIUtils.drawRound(9,yy+11,234,2,1,new Color(255,255,255,22));
                        if(value>0)UIUtils.drawGradientHorizontal(9,yy+11,(float)(234*value),2,(float)Math.min(1,117*value),theme.getPrimaryGradientColor(),theme.getSecondaryGradientColor());
                    }
                    yy+=15;
                }
                if(p.graph!=null){graph(p.graph,9,yy+1,234,23,theme);small.drawString("Frame interval (ms)",10,yy+24,muted);}
                small.drawString(fit(p.lines.size()>p.visibleRows?"Scroll in chat/editor  "+(w.scroll+1)+"-"+(w.scroll+p.visibleRows)+" / "+p.lines.size():p.footer,small,234),9,p.height-9,muted);
            }
        }));
    }
    static boolean graphSampleValid(double sample){
        return Double.isFinite(sample)&&sample>=0;
    }
    /** No invented point for absent frame measurements; data gaps stay visible. */
    private static void graph(double[] samples,float x,float y,float width,float height,Theme theme) {
        try(RenderState state=new RenderState()) {
            glDisable(GL_TEXTURE_2D);glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            double maximum=33.3;
            for(double sample:samples)if(graphSampleValid(sample))maximum=Math.max(maximum,sample);
            glLineWidth(1);glColor4f(1,1,1,.14f);
            glBegin(GL_LINES);glVertex2f(x,y+height);glVertex2f(x+width,y+height);glEnd();
            if(samples.length<2)return;
            boolean connected=false;
            for(int i=0;i<samples.length;i++){
                double sample=samples[i];
                if(!graphSampleValid(sample)){
                    if(connected){glEnd();connected=false;}
                    continue;
                }
                if(!connected){glBegin(GL_LINE_STRIP);connected=true;}
                int rgb=Theme.interpolateRGB(theme.getPrimaryGradientRGB(),
                        theme.getSecondaryGradientRGB(),i/(float)(samples.length-1));
                glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,1);
                glVertex2f(x+width*i/(samples.length-1),
                        y+height-(float)(Math.min(maximum,sample)/maximum*height));
            }
            if(connected)glEnd();
        }
    }
    private static TTFFontRenderer font(String key){return Pulsar.INSTANCE.fontManager.getFont(key);}
    static String fit(String text,TTFFontRenderer font,float width) {
        if(font.getWidth(text)<=width)return text;
        int end=text.length();while(end>0&&font.getWidth(text.substring(0,end)+"...")>width)end=text.offsetByCodePoints(end,-1);
        return end==0?"":text.substring(0,end)+"...";
    }
    final class Widget {
        final SliderValue positionX,positionY;final Map<String,Double> meters=new HashMap<>();
        final HudSpring graphSpring=new HudSpring();
        float graphTail=Float.NaN;
        float x,y,width,height,grabX,grabY;boolean dragging,visible;int scroll,maxScroll;
        double lastX,lastY;
        Widget(String label,float x,float y){this.x=x;this.y=y;lastX=x;lastY=y;positionX=new SliderValue(label+" X",Diagnostics.this,(double)x,-16384,16384);positionY=new SliderValue(label+" Y",Diagnostics.this,(double)y,-16384,16384);}
        void syncPosition(){if(positionX!=null&&lastX!=positionX.getValue()){lastX=positionX.getValue();x=(float)lastX;}if(positionY!=null&&lastY!=positionY.getValue()){lastY=positionY.getValue();y=(float)lastY;}}
        boolean defaultPosition(){return positionX.getValue().equals(positionX.getDefaultValue())&&positionY.getValue().equals(positionY.getDefaultValue());}
        void clamp(int sw,int sh){if(HUD.bounded()||mc!=null&&mc.currentScreen instanceof DiagnosticsEditor){x=Math.max(4,Math.min(x,sw-width-4));y=Math.max(4,Math.min(y,sh-height-4));}}
    }
    static final class Line {
        final String label,value;final double ratio;
        Line(String label,String value,double ratio){this.label=label;this.value=value;this.ratio=ratio;}
    }
    final class Panel {
        final Widget widget;final String title,status;final List<Line> lines=new ArrayList<>();
        DiagnosticsPlots.Plot plot;
        String footer="";double[] graph;float scale;int height,visibleRows;
        Panel(Widget w,String title,String status){widget=w;this.title=title;this.status=status;}
        void line(String label,String value){lines.add(new Line(label,value,-1));}
        void meter(String label,String value,double ratio){lines.add(new Line(label,value,ratio));}
        void wrap(String text) {
            // The compact HUD stays bounded. The console contains complete frozen evidence and active values.
            int limit=flagDeveloper.getValue()||networkValues.getValue()?10:4;
            TTFFontRenderer font=Pulsar.INSTANCE.fontManager==null?null:font("Product-Sans-Regular 16");
            int count=0;
            while(!text.isEmpty()&&count++<limit) {
                int end=text.length();
                if(font!=null)while(end>0&&font.getWidth(text.substring(0,end))>234)end=text.offsetByCodePoints(end,-1);
                else end=Math.min(end,48);
                if(end==0)break;
                int space=text.lastIndexOf(' ',end);if(end<text.length()&&space>end/2)end=space;
                line(text.substring(0,end).trim(),"");text=text.substring(end).trim();
            }
            if(!text.isEmpty())line("More detail: enable console output","");
        }
    }
}
