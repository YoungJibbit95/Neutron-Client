package me.youngjibbit.module.impl.client;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.gui.IslandMorphMotion;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.module.impl.client.island.IslandLayout;
import me.youngjibbit.module.impl.client.island.IslandClockFace;
import me.youngjibbit.module.impl.client.island.IslandObservations;
import me.youngjibbit.module.impl.client.island.IslandNotice;
import me.youngjibbit.module.impl.client.island.IslandNoticeFlow;
import me.youngjibbit.module.impl.client.island.IslandContentLayout;
import me.youngjibbit.core.activity.ModuleToggleJournal;
import me.youngjibbit.module.impl.client.island.IslandPresentation;
import me.youngjibbit.module.impl.client.island.IslandScaffoldIndicator;
import me.youngjibbit.module.impl.client.island.IslandTargetComposition;
import me.youngjibbit.module.impl.client.island.IslandStateMachine;
import me.youngjibbit.module.impl.client.island.IslandPlacementMetrics;
import me.youngjibbit.module.impl.client.island.IslandWidget;
import me.youngjibbit.module.impl.client.island.IslandWidgetLayout;
import me.youngjibbit.module.impl.client.island.IslandContentKey;
import me.youngjibbit.module.impl.client.island.IslandExploitGlyph;
import me.youngjibbit.module.impl.client.island.IslandAttachmentLayout;
import me.youngjibbit.core.activity.WidgetIncident;
import me.youngjibbit.core.activity.WidgetIncidentJournal;
import me.youngjibbit.core.activity.InputClickJournal;
import me.youngjibbit.notifications.NotificationManager;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.utils.render.Roundedutil;
import net.minecraft.entity.EntityLivingBase;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import java.awt.Color;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import static org.lwjgl.opengl.GL11.*;
@ModuleInfo(id="watermark_hud",name="Watermark",description="Renders the client watermark",category=Category.Client)
public class WatermarkHUDModule extends Module {
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Simple","Glass","Outline","Rise","iOS","Logo 1","Logo 2","Wordmark","Status","Dynamic Island","Modern","Creative"},1);
    private final CheckBoxValue name=new CheckBoxValue("Client Name",this,true),version=new CheckBoxValue("Version",this,true),fps=new CheckBoxValue("FPS",this,true),player=new CheckBoxValue("Player",this,true);
    private final CheckBoxValue background=new CheckBoxValue("Background",this,true),blur=new CheckBoxValue("Blur",this,true),glow=new CheckBoxValue("Glow",this,true);
    private final ComboValue textStyle=new ComboValue("Text Style",this,new String[]{"Solid","Horizontal","Vertical","Theme"},3);
    private final CheckBoxValue localRadius=new CheckBoxValue("Local Text Glow Extent",this,false);
    private final CheckBoxValue animated=new CheckBoxValue("Animated Text",this,false),textGlow=new CheckBoxValue("Text Glow",this,true);
    private final SliderValue speed=new SliderValue("Text Speed",this,.18,.02,1),strength=new SliderValue("Text Glow Strength",this,.45,0,2),radius=new SliderValue("Text Glow Radius",this,4.0,1,12);
    private final ArrayListSurface surface=new ArrayListSurface(this);
    private final TextEffects textEffects=new TextEffects();
    // New settings follow the legacy surface's registrations: no existing Value index shifts.
    private final CheckBoxValue islandTarget=new CheckBoxValue("Island Target",this,true);
    private final CheckBoxValue islandClock=new CheckBoxValue("Island Clock",this,true);
    private final CheckBoxValue islandInventory=new CheckBoxValue("Island Inventory (Fixture)",this,false);
    // Real activity choices append after all previously registered settings.
    private final CheckBoxValue islandScaffold=new CheckBoxValue("Island Scaffold",this,true);
    private final CheckBoxValue islandLiveInventory=new CheckBoxValue("Island Inventory",this,false);
    private final CheckBoxValue islandModuleToasts=new CheckBoxValue("Island Module Toasts",this,true);
    private final CheckBoxValue islandLocalWarnings=new CheckBoxValue("Island Local Warnings",this,true);
    private final ComboValue islandClockStyle=new ComboValue("Island Clock Style",this,new String[]{"Text","Modern"},0);
    private final CheckBoxValue islandNotifications=new CheckBoxValue("Island Notifications",this,false);
    private final CheckBoxValue islandCpsWidget=new CheckBoxValue("Island CPS Widget",this,false);
    private final CheckBoxValue islandFpsWidget=new CheckBoxValue("Island FPS Widget",this,false);
    private final CheckBoxValue islandExploitWidget=new CheckBoxValue("Island Exploit Widget",this,false);
    private final ComboValue islandWidgetPlacement=new ComboValue("Island Widget Placement",this,new String[]{"Inside","Outside"},0);
    private final IslandStateMachine islandState=new IslandStateMachine();
    private final IslandPlacementMetrics islandPlacements=new IslandPlacementMetrics();
    private final IslandNoticeFlow islandNotices=new IslandNoticeFlow();
    private final IslandMorphMotion islandMotion=new IslandMorphMotion();
    private final HudSpring[] islandWidgetOpen={new HudSpring(),new HudSpring(),new HudSpring()};
    private final HudSpring islandTargetTrail=new HudSpring();
    private int islandTrailTarget=Integer.MIN_VALUE;
    private IslandStateMachine.State islandAttachmentKind;
    // Creative N-morph: pulsed by the existing HudSpring oscillator, no parallel motion engine.
    private final HudSpring islandNMorph=new HudSpring();
    private float islandNMorphAmount=0;
    private Object islandWorld;
    private long islandEpoch, islandLastFrameNanos, islandClockMinute=Long.MIN_VALUE;
    private String islandClockText="", islandLastKey;
    private IslandStateMachine.Selection islandLastSelection, islandPrevious;
    private boolean islandActive;
    // Logo1-N resource: geschwungenes, leuchtendes N als echte Form (keine Glyphe).
    private static final ResourceLocation LOGO1 = new ResourceLocation("pulsar/Logo1.png");
    // Clock Reminder: lokale 10-Minuten-Marken, dedupliziert pro Slot.
    private final me.youngjibbit.module.impl.client.island.ClockReminderSchedule clockSchedule = new me.youngjibbit.module.impl.client.island.ClockReminderSchedule();
    // Inventory Activity: synthetische Headless-Fixture bis TECH InventoryActivitySnapshot freigegeben.
    private IslandStateMachine.InventoryActivity inventoryFixture = null;
    private long inventoryFixtureUntil = 0;

    public WatermarkHUDModule(){radius.setVisibility(m->localRadius.getValue());animated.setVisibility(m->!textStyle.getValue().equals("Theme"));speed.setVisibility(m->!textStyle.getValue().equals("Theme")&&animated.getValue());
        fps.setVisibility(m->!islandSelected());player.setVisibility(m->!islandSelected());
        islandTarget.setVisibility(m->islandSelected());
        islandClock.setVisibility(m->islandSelected());
        islandInventory.setVisibility(m->false);
        islandScaffold.setVisibility(m->islandSelected());
        islandLiveInventory.setVisibility(m->islandSelected());
        islandModuleToasts.setVisibility(m->islandSelected());
        islandLocalWarnings.setVisibility(m->islandSelected());
        islandClockStyle.setVisibility(m->islandSelected()&&islandClock.getValue());
        islandNotifications.setVisibility(m->islandSelected());
        islandCpsWidget.setVisibility(m->islandSelected());
        islandFpsWidget.setVisibility(m->islandSelected());
        islandExploitWidget.setVisibility(m->islandSelected());
        islandWidgetPlacement.setVisibility(m->islandSelected()&&(islandCpsWidget.getValue()
                ||islandFpsWidget.getValue()||islandExploitWidget.getValue()));}
    /** Global opt-in upgraded the historical Glass default without rewriting stored Mode. */
    boolean islandSelected(){
        String m=mode.getValue();
        return "Dynamic Island".equals(m)||"Modern".equals(m)||"Creative".equals(m)||
                (ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())&&"Glass".equals(m));
    }
    /** The chosen Island route remains exclusive; visibility controls when its one queue may advance. */
    public boolean routesNotificationsToIsland(){
        return islandNotifications.getValue();
    }
    public boolean canPresentIslandNotifications(){
        return routesNotificationsToIsland()&&isToggled()&&islandSelected()&&HUD.visible()
                &&mc!=null&&mc.theWorld!=null&&mc.thePlayer!=null&&!mc.skipRenderWorld
                &&mc.gameSettings!=null&&!mc.gameSettings.hideGUI&&!mc.gameSettings.showDebugInfo
                &&(mc.currentScreen==null||mc.currentScreen instanceof net.minecraft.client.gui.GuiChat);
    }
    @Subscribe private final Listener<Event2D> onRender2D=new Listener<>(event->{if(!mc.gameSettings.showDebugInfo)renderWatermark();else if(islandActive){resetIsland();surface.close();}});
    @Subscribe private final Listener<me.youngjibbit.event.world.WorldEvent> islandWorldChanged=new Listener<>(event->resetIsland());
    @Subscribe private final Listener<me.youngjibbit.event.update.EventUpdate> islandVisibility=new Listener<>(event->{
        if(!canSampleIslandFrames())WidgetIncidentJournal.global().suspendFrames();
    });
    private boolean canSampleIslandFrames(){
        return islandSelected()&&HUD.visible()&&mc.theWorld!=null&&mc.thePlayer!=null&&!mc.skipRenderWorld
                &&mc.gameSettings!=null&&!mc.gameSettings.hideGUI&&!mc.gameSettings.showDebugInfo
                &&mc.inGameHasFocus&&mc.currentScreen==null&&!mc.isGamePaused();
    }
    @Override public void onDisable(){resetIsland();surface.close();textEffects.close();super.onDisable();}
    void renderWatermark(){
        if(!HUD.visible()){resetIsland();surface.close();textEffects.close();return;}
        if(islandSelected()){renderIsland();return;}
        if(islandActive)resetIsland();
        String design=mode.getValue();boolean logo=design.startsWith("Logo"),word=design.equals("Wordmark"),status=design.equals("Status"),rise=design.equals("Rise"),simple=design.equals("Simple");
        TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont(rise?"Product-Sans-Medium 41":word?"Poppins-Bold 23":design.equals("iOS")?"SFProText-Medium 18":simple?"KhmerOureang 20":"DmSansMedium 18");
        TTFFontRenderer detail=(word||rise)?Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 18"):font;
        String title=(word||rise)&&name.getValue()?Pulsar.INSTANCE.getName():"";
        java.util.List<String> fields=new ArrayList<>();if(name.getValue()&&!logo&&!word&&!rise)fields.add(Pulsar.INSTANCE.getName());if(version.getValue())fields.add(Pulsar.INSTANCE.getVersion());
        if(fps.getValue())fields.add("FPS: "+(mc.getDebugFPS()>0?Integer.toString(mc.getDebugFPS()):"--"));
        if(player.getValue())fields.add(mc.thePlayer!=null?mc.thePlayer.getNameClear():mc.getSession().getUsername());
        String label=String.join(status?"   |   ":" | ",fields);if(label.isEmpty()&&title.isEmpty()&&(!logo||!name.getValue()))return;
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        boolean follow=textStyle.getValue().equals("Theme");int style=follow?(theme.isVertical()?2:1):textStyle.getSelectedIndex();
        boolean motion=follow?theme.isAnimated():animated.getValue();float rate=follow?theme.getAnimationSpeed():speed.getValue().floatValue();
        float titleHeight=title.isEmpty()?0:font.getHeight(title)+4;
        float textWidth=Math.max(title.isEmpty()?0:font.getWidth(title),label.isEmpty()?0:detail.getWidth(label)),textHeight=(label.isEmpty()?0:detail.getHeight(label))+titleHeight;
        float contentWidth=Math.max(logo&&name.getValue()?84:0,textWidth),contentHeight=(logo&&name.getValue()?58:0)+textHeight;
        float w=contentWidth+14,h=contentHeight+12;ScaledResolution sr=new ScaledResolution(mc);float scale=Math.min(1,Math.min((sr.getScaledWidth()-8)/w,(sr.getScaledHeight()-8)/h));
        final float fw=w,fh=h,fs=scale;boolean panel=!simple&&!rise&&!word&&!logo;float rounding=status?3:UiEffects.radius(design.equals("iOS")?7:5,w*scale,h*scale);
        GuiShaderBloom.afterBatch(()->{
            Runnable content=()->{
            try(RenderState state=new RenderState()){
                state.screenSpace();glTranslatef(4,4,0);glScalef(fs,fs,1);
                if(logo&&name.getValue())Roundedutil.drawImage(new ResourceLocation(design.equals("Logo 1")?"pulsar/Logo1.png":"pulsar/Logo2.png"),-24,-22,140,111,255);
                float ty=6+(logo&&name.getValue()?58:0);
                if(!title.isEmpty())textEffects.drawLocal(font,title,7,ty,theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),style,motion,rate,textGlow.getValue(),strength.getValue().floatValue(),radius.getValue().floatValue(),!follow,localRadius.getValue(),false);
                textEffects.drawLocal(detail,label,7,ty+titleHeight,theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),style,motion,rate,textGlow.getValue(),strength.getValue().floatValue(),radius.getValue().floatValue(),!follow,localRadius.getValue(),simple||rise);
                if(status&&background.getValue()){
                    float offset=7;for(int i=0;i<fields.size();i++){if(i>0)UIUtils.drawRound(offset-5,fh-4,font.getWidth(fields.get(i)),1,0.5f,new Color(theme.getGradientRGB(i/(float)Math.max(1,fields.size()-1))));offset+=font.getWidth(fields.get(i))+font.getWidth("   |   ")-2;}
                }
            }
            };
            if(panel)surface.designedPanel(4,4,fw*fs,fh*fs,rounding,theme,panel&&background.getValue(),panel&&blur.getValue(),panel&&glow.getValue(),4,.52f,panel&&background.getValue()?(design.equals("Outline")?1:.55f):0,false,content);else surface.text(content);
        });
    }

    /** Render-side only: a valid, recent Aura/AimAssist-owned entity in this exact world. */
    private IslandStateMachine.Target islandTargetSnapshot(){
        if(!islandTarget.getValue()||mc.theWorld==null||mc.thePlayer==null)return null;
        String source=CombatTargetTracker.getSource();
        if(!"aura".equalsIgnoreCase(source)&&!"aimassist".equalsIgnoreCase(source))return null;
        EntityLivingBase entity=CombatTargetTracker.getPrimaryTarget(250L);
        if(entity==null||entity==mc.thePlayer||entity.isDead||!entity.isEntityAlive()
                ||mc.theWorld.getEntityByID(entity.getEntityId())!=entity)return null;
        String visible=net.minecraft.util.StringUtils.stripControlCodes(entity.getCommandSenderName());
        if(visible==null||visible.trim().isEmpty())return null;
        return new IslandStateMachine.Target(entity.getEntityId(),IslandLayout.limitText(visible,64),
                entity.getHealth(),entity.getMaxHealth(),entity.getAbsorptionAmount());
    }

    private IslandStateMachine.Scaffold islandScaffoldSnapshot(long now){
        if(!islandScaffold.getValue()||mc.theWorld==null||mc.thePlayer==null||Pulsar.INSTANCE.getMm()==null)return null;
        me.youngjibbit.module.impl.world.Scaffold scaffold=(me.youngjibbit.module.impl.world.Scaffold)
                Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.world.Scaffold.class);
        return scaffold==null||!scaffold.isToggled()?null:IslandObservations.scaffold(scaffold.getActivitySnapshot(),now);
    }

    private IslandStateMachine.InventoryActivity islandInventorySnapshot(long now){
        if(!islandLiveInventory.getValue()||mc.theWorld==null||mc.thePlayer==null||Pulsar.INSTANCE.getMm()==null)return null;
        me.youngjibbit.module.impl.player.InventoryManager manager=(me.youngjibbit.module.impl.player.InventoryManager)
                Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.player.InventoryManager.class);
        me.youngjibbit.module.impl.player.Stealer stealer=(me.youngjibbit.module.impl.player.Stealer)
                Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.player.Stealer.class);
        return IslandObservations.inventory(manager==null||!manager.isToggled()?null:manager.getActivitySnapshot(),
                stealer==null||!stealer.isToggled()?null:stealer.getActivitySnapshot(),now);
    }

    private IslandNotice islandWarningSnapshot(long now){
        if(mc.theWorld==null||mc.thePlayer==null||Pulsar.INSTANCE.getMm()==null)return null;
        me.youngjibbit.module.impl.render.Diagnostics diagnostics=(me.youngjibbit.module.impl.render.Diagnostics)
                Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.render.Diagnostics.class);
        return diagnostics==null?null:IslandObservations.correction(diagnostics.getLocalCorrectionSnapshot(now),now);
    }

    /** Synthetische Inventory-Aktivitäts-Fixture (opt-in, nur für Entwicklung/Tests). */
    private IslandStateMachine.InventoryActivity inventoryFixture(){
        if(!islandInventory.getValue())return null;
        long now=System.currentTimeMillis();
        if(inventoryFixture!=null&&now<inventoryFixtureUntil)return inventoryFixture;
        return null;
    }

    /** Setzt eine synthetische Inventory-Fixture mit TTL (nur für Entwicklung/Headless-Tests). */
    void setInventoryFixture(String action,int processed,int total,boolean complete,long ttlMillis){
        inventoryFixture=new IslandStateMachine.InventoryActivity(action,processed,total,complete);
        inventoryFixtureUntil=System.currentTimeMillis()+ttlMillis;
    }

    private void renderIsland(){
        long now=System.nanoTime();
        WidgetIncidentJournal incidentsJournal=WidgetIncidentJournal.global();
        me.youngjibbit.core.network.PacketPipeline packetContext=mc.getNetHandler()==null?null:
                mc.getNetHandler().getNetworkManager().getClientPackets();
        incidentsJournal.context(mc.theWorld,packetContext,packetContext==null?0:packetContext.getContextVersion());
        if(islandFpsWidget.getValue()&&canSampleIslandFrames())
            incidentsJournal.observeFrame(now);else incidentsJournal.suspendFrames();
        if(!islandActive)islandNotices.reset(now,ModuleToggleJournal.currentSequence());
        islandActive=true;
        // World object identity is a scene epoch: entity-id reuse cannot leak old content.
        if(islandWorld!=mc.theWorld){
            islandWorld=mc.theWorld;islandEpoch++;islandState.reset();islandMotion.reset();islandPlacements.reset();
            islandAttachmentKind=null;islandTargetTrail.reset();islandTrailTarget=Integer.MIN_VALUE;
            for(HudSpring spring:islandWidgetOpen)spring.reset();
            islandLastSelection=islandPrevious=null;islandLastKey=null;islandLastFrameNanos=0;
            inventoryFixture=null;inventoryFixtureUntil=0;
            islandNotices.reset(now,ModuleToggleJournal.currentSequence());
        }
        float dt=islandLastFrameNanos==0||now<=islandLastFrameNanos?0
                :Math.min(.05f,(now-islandLastFrameNanos)/1_000_000_000f);
        islandLastFrameNanos=now;
        if(!mc.inGameHasFocus){islandMotion.reset();dt=0;}
        IslandNotice moduleNotice=IslandObservations.module(ModuleToggleJournal.getSnapshot(),now);
        IslandNotice warningNotice=islandWarningSnapshot(now);
        islandNotices.update(islandState,moduleNotice,warningNotice,islandModuleToasts.getValue(),
                islandLocalWarnings.getValue(),islandEpoch,now);

        // Clock Reminder: diskrete 10-Minuten-Marke als Event anbieten.
        IslandStateMachine.ClockReminder clockRem=null;
        long wall=System.currentTimeMillis();
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        if(islandClock.getValue()&&clockSchedule.poll(wall,zone)){
            String t=java.time.Instant.ofEpochMilli(wall).atZone(zone)
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT));
            islandState.offer(IslandStateMachine.State.CLOCK_REMINDER,"clock:"+(wall/60_000L),t,islandEpoch,now);
            clockRem=new IslandStateMachine.ClockReminder(t,false);
        }

        IslandStateMachine.Scaffold scaffoldView=islandScaffoldSnapshot(now);
        final IslandPlacementMetrics.Snapshot placementMetrics=islandPlacements.sample(
                scaffoldView==null?-1:scaffoldView.placementCount,now);
        NotificationManager.IslandNotification notification=canPresentIslandNotifications()
                ?NotificationManager.islandNotification():null;
        IslandStateMachine.Selection selected=islandState.resolve(islandEpoch,System.nanoTime(),
                islandTargetSnapshot(),scaffoldView,clockRem,islandInventorySnapshot(now),
                islandTarget.getValue(),islandScaffold.getValue(),islandClock.getValue(),islandLiveInventory.getValue(),notification);
        String key=IslandContentKey.of(selected);
        if(!key.equals(islandLastKey)){
            // Idle and a frozen local clock may crossfade; never retain a lost gameplay target.
            islandPrevious=islandLastSelection!=null&&(islandLastSelection.state==IslandStateMachine.State.IDLE
                    ||islandLastSelection.state==IslandStateMachine.State.CLOCK_REMINDER)
                    ?islandLastSelection:null;
            islandMotion.contentChanged();islandLastKey=key;
        }
        islandLastSelection=selected;
        ClientSettings settings=ClientSettings.current();
        boolean animations=settings==null||settings.animations.getValue();
        boolean reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        ScaledResolution sr=new ScaledResolution(mc);
        final String design=mode.getValue();
        final boolean themeModern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        final boolean designModern="Modern".equals(design);
        final boolean designCreative="Creative".equals(design);
        final boolean modern=themeModern||designModern||designCreative;
        final boolean creative=designCreative;
        final String paintDesign=creative?"Creative":modern?"Modern":design;
        final boolean outsideWidgets="Outside".equals(islandWidgetPlacement.getValue());
        final boolean attached=IslandAttachmentLayout.attached(selected.state);
        if(attached)islandAttachmentKind=selected.state;
        final boolean showWidgets=IslandWidgetLayout.visible(selected.state,outsideWidgets);
        final float islandSeconds=UiEffects.time();
        final boolean glyphMotion=animations&&!reduced&&UiEffects.motion();
        final IslandWidget[] widgets=islandWidgets(now,islandSeconds,glyphMotion);
        final WidgetIncident[] incidents=new WidgetIncident[widgets.length];
        float[] widgetWidths=new float[widgets.length];
        for(int i=0;i<widgets.length;i++){
            IslandWidget.Kind kind=widgets[i].kind;
            incidents[i]=kind==IslandWidget.Kind.CPS?null:incidentsJournal.latest(
                    kind==IslandWidget.Kind.FPS?WidgetIncident.Kind.FPS:WidgetIncident.Kind.EXPLOIT,System.nanoTime());
            float open=islandWidgetOpen[kind.ordinal()].step(incidents[i]==null?0:1,dt,
                    animations,reduced,kind==IslandWidget.Kind.FPS?"Calm":intensity);
            if(incidents[i]==null&&Math.abs(open)<.002f)open=islandWidgetOpen[kind.ordinal()].snap(0);
            widgetWidths[i]=34+Math.max(0,Math.min(1.06f,open))*(kind==IslandWidget.Kind.FPS?158:186);
        }
        final int widgetCount=showWidgets
                ?(islandCpsWidget.getValue()?1:0)+(islandFpsWidget.getValue()?1:0)+(islandExploitWidget.getValue()?1:0):0;
        final float baseLogicalWidth=IslandWidgetLayout.idleWidth(modern,widgetCount,outsideWidgets);
        IslandLayout.Frame desired=IslandLayout.forState(selected.state,sr.getScaledWidth(),sr.getScaledHeight(),modern);
        if(selected.state==IslandStateMachine.State.IDLE)
            desired=desired.withGeometry(baseLogicalWidth,
                    desired.height,desired.radius,desired.opacity);
        else if(attached)desired=desired.withGeometry(Math.max(desired.width,baseLogicalWidth),desired.height,17,desired.opacity);
        final IslandWidgetLayout widgetLayout=IslandWidgetLayout.capture(
                islandMotion.sample(desired,dt,animations,reduced,intensity,selected.state),widgetCount,outsideWidgets,widgetWidths);
        final IslandLayout.Frame frame=widgetLayout.frame;
        if(!attached&&frame.height<=34.25f)islandAttachmentKind=null;
        final IslandAttachmentLayout attachment=attached||selected.state==IslandStateMachine.State.IDLE&&islandAttachmentKind!=null
                ?new IslandAttachmentLayout(frame,baseLogicalWidth,attached?selected.state:islandAttachmentKind,widgetLayout.scale):null;
        // Creative N-morph: driven by the existing HudSpring oscillator.
        islandNMorphAmount=islandNMorph.step(creative?1f:0f,dt,animations&&!reduced,reduced,intensity);
        float mix=islandMotion.contentAlpha();
        if(mix>=.999f)islandPrevious=null;
        final IslandStateMachine.Selection previous=islandPrevious;
        final Theme theme=Theme.resolve(Theme.getDefaultTheme());
        final TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 18");
        // Freeze settings and text before scheduling a deferred surface pass.
        final IslandClockFace liveClock=IslandClockFace.capture(wall,zone,animations&&!reduced);
        final String clockText=islandClock.getValue()?liveClock.time:"";
        final String clientName=Pulsar.INSTANCE.getName(), clientVersion=Pulsar.INSTANCE.getVersion();
        final TTFFontRenderer wordmark=Pulsar.INSTANCE.getFontManager().getWordmarkFont(clientName,"DmSansMedium 18");
        final boolean showName=name.getValue(), showVersion=version.getValue(), showClock=islandClock.getValue();
        final boolean modernClock=showClock&&"Modern".equals(islandClockStyle.getValue());
        final boolean panel=background.getValue(), useBlur=blur.getValue(),
                useGlow=glow.getValue();
        float designBlurRadius=designCreative?3f:modern?3.5f:5f;
        float designFillAlpha=(designCreative?.92f:modern?.85f:.65f)*frame.opacity;
        float designOutlineAlpha=(designCreative?.22f:modern?.14f:.48f)*frame.opacity;
        final IslandPresentation present=IslandPresentation.from(selected,clientName,clientVersion,clockText,
                showName,showVersion,showClock).withClockFace(liveClock);
        final IslandPresentation basePresent=IslandPresentation.from(null,clientName,clientVersion,clockText,
                showName,showVersion,showClock).withClockFace(liveClock);
        final IslandPresentation fading=previous==null?null:IslandPresentation.from(previous,
                clientName,clientVersion,clockText,showName,showVersion,showClock).withClockFace(liveClock);
        final IslandScaffoldIndicator scaffoldIndicator=IslandScaffoldIndicator.capture(present.scaffold,
                islandSeconds,glyphMotion);
        final IslandTargetComposition targetComposition=IslandTargetComposition.capture(present,islandSeconds,glyphMotion);
        if(selected.target==null){islandTargetTrail.reset();islandTrailTarget=Integer.MIN_VALUE;}
        else if(islandTrailTarget!=selected.target.entityId){
            islandTrailTarget=selected.target.entityId;islandTargetTrail.snap(selected.target.fraction());
        }
        final float targetTrail=selected.target==null?0:Math.max(selected.target.fraction(),Math.min(1,
                islandTargetTrail.step(selected.target.fraction(),dt,animations,reduced,"Calm")));
        final float clipInset=Math.min(2,Math.min(frame.width,frame.height)*.08f);
        // A queued bloom pass may outlive a world/mode/visibility change: fence it.
        final long passEpoch=islandEpoch;
        final Object passWorld=mc.theWorld,passPlayer=mc.thePlayer;
        // Reuse the single existing surface and shared halo/blur/RenderState pools.
        final ArrayListSurface.Shape silhouette=attachment!=null
                ?attachment.silhouette()
                :ArrayListSurface.Shape.roundedRectangle(frame.x,frame.y,frame.width,frame.height,frame.radius);
        final Runnable drawIsland=()->surface.islandContour(silhouette,theme,panel,useBlur,useGlow,designBlurRadius,
                designFillAlpha,designOutlineAlpha,
                ()->{
                    try(GuiClip clip=GuiClip.push(new ClipRect(frame.x+clipInset,frame.y+clipInset,
                            Math.max(.01f,frame.width-2*clipInset),Math.max(.01f,frame.height-2*clipInset)))){
                        if(attachment==null&&modern&&panel&&present.state!=IslandStateMachine.State.CLOCK_REMINDER
                                &&present.notice==null&&present.notification==null){
                            // A quiet graphite ink plate: blur/world color remains perceptible,
                            // but high-contrast glyphs no longer blend with bright terrain.
                            UIUtils.drawRound(frame.x+2,frame.y+2,Math.max(1,frame.width-4),
                                    Math.max(1,frame.height-4),Math.max(0,frame.radius-2),
                                    new Color(17,19,29,Math.round(173*frame.opacity)));
                            if(creative){
                                // Expressive geometric accent: a thin luminous top band.
                                UIUtils.drawRound(frame.x+2,frame.y+2,
                                        Math.max(1,frame.width-4),2.5f,1.25f,
                                        tint(theme.getPrimaryGradientColor(),.45f*frame.opacity));
                            } else {
                                UIUtils.drawRound(frame.x+11,frame.y+3,
                                        Math.max(0,Math.min(48,frame.width-22)),1.1f,.5f,
                                        tint(theme.getSecondaryGradientColor(),.62f*frame.opacity));
                            }
                        }
                        if(attachment!=null){
                            paintIslandIdle(basePresent,attachment.base,font,wordmark,theme,frame.opacity,paintDesign,
                                    widgets,outsideWidgets,modernClock);
                            float bodyHeight=frame.y+frame.height-attachment.detail.y;
                            if(attached&&bodyHeight>.01f)try(GuiClip body=GuiClip.push(new ClipRect(attachment.detail.x,
                                    attachment.detail.y,attachment.detail.width,bodyHeight))){
                                try(RenderState slide=new RenderState()){
                                    glTranslatef(0,(1-attachment.reveal)*7,0);
                                    if(targetComposition!=null)paintIslandTarget(targetComposition,attachment.detail,font,theme,
                                            frame.opacity*attachment.reveal,targetTrail);
                                    else if(present.notification!=null)
                                        paintIslandNotification(present,attachment.detail,font,theme,frame.opacity*attachment.reveal);
                                    else if(present.notice!=null)
                                        paintIslandNotice(present,attachment.detail,font,frame.opacity*attachment.reveal);
                                }
                            }
                            return;
                        }
                        if(fading!=null&&mix<1)paintIsland(fading,frame,font,wordmark,theme,
                                (1-mix)*frame.opacity,paintDesign,null,null,placementMetrics,widgets,outsideWidgets,modernClock);
                        paintIsland(present,frame,font,wordmark,theme,mix*frame.opacity,paintDesign,scaffoldIndicator,
                                targetComposition,placementMetrics,widgets,outsideWidgets,modernClock);
                    }
                });
        GuiShaderBloom.afterBatch(()->{
            if(!islandActive||passEpoch!=islandEpoch||mc.theWorld!=passWorld||mc.thePlayer!=passPlayer)return;
            if(!outsideWidgets||widgetLayout.count==0){drawIsland.run();return;}
            // One captured scene is shared by the capsule and all detached circles.
            surface.withScene(theme,panel,useBlur,designBlurRadius,()->{
                drawIsland.run();
                if(outsideWidgets&&widgetCount>0)
                    for(int i=0;i<widgetLayout.count;i++){
                        final int index=i;
                        float diameter=widgetLayout.diameter;
                        surface.islandContour(ArrayListSurface.Shape.roundedRectangle(widgetLayout.x(i),widgetLayout.y,
                                        widgetLayout.width(i),diameter,diameter/2),
                                theme,panel,useBlur,useGlow,designBlurRadius,designFillAlpha,designOutlineAlpha,
                                ()->paintIslandWidgetBar(widgets[index],incidents[index],widgetLayout.x(index),widgetLayout.y,
                                        widgetLayout.width(index),diameter,theme,frame.opacity));
                    }
            });
        });
    }

    private void paintIsland(IslandPresentation view,IslandLayout.Frame frame,
                             TTFFontRenderer font,TTFFontRenderer wordmark,Theme theme,float alpha,String design,
                             IslandScaffoldIndicator scaffoldIndicator,IslandTargetComposition targetComposition,
                             IslandPlacementMetrics.Snapshot placementMetrics,IslandWidget[] widgets,
                             boolean outsideWidgets,boolean modernClock){
        if(alpha<=0)return;
        if(targetComposition!=null){paintIslandTarget(targetComposition,frame,font,theme,alpha);return;}
        if(scaffoldIndicator!=null&&view.scaffold!=null&&view.scaffold.observed){
            paintIslandScaffold(view,frame,font,theme,alpha,design,scaffoldIndicator,placementMetrics);return;
        }
        if(view.notification!=null){paintIslandNotification(view,frame,font,theme,alpha);return;}
        if(view.notice!=null){paintIslandNotice(view,frame,font,alpha);return;}
        if(view.inventory!=null&&view.inventory.observed){paintIslandInventory(view,frame,font,theme,alpha);return;}
        if(view.state==IslandStateMachine.State.CLOCK_REMINDER&&view.clockFace!=null){
            float diameter=Math.min(frame.width,frame.height)*.88f;
            renderClockFace(frame.x+(frame.width-diameter)/2,frame.y+(frame.height-diameter)/2,
                    diameter,view.clockFace,theme,alpha);
            return;
        }
        if(view.state==IslandStateMachine.State.IDLE){
            paintIslandIdle(view,frame,font,wordmark,theme,alpha,design,widgets,outsideWidgets,modernClock);return;
        }
        boolean modern="Modern".equals(design)||"Creative".equals(design);
        boolean creative="Creative".equals(design);
        float x=frame.x,y=frame.y,w=frame.width,h=frame.height;
        int primary=ink(0xfff4f6fc,alpha);
        int secondary=ink(modern?0xffc7bdcf:0xffb8cad8,alpha);
        int accent=ink(theme.getPrimaryGradientRGB()|0xff000000,alpha);
        float badge=creative?22:modern?20:24;
        float badgeX=x+(modern?10:9),badgeY=y+(h-badge)/2;
        // Creative N-morph: the badge pulses subtly while morphed.
        float morph=creative?islandNMorphAmount:0f;
        float nSize=badge*(1f+0.06f*morph);

        if(view.scaffold==null||!view.scaffold.observed||!renderScaffoldItem(badgeX,badgeY,badge,view.scaffold,alpha))
            renderLogo1N(badgeX,badgeY,nSize,theme,alpha,modern,creative,morph);

        float start=creative?badgeX+nSize+10:modern?(badgeX+badge+8):(x+39);
        if(view.state==IslandStateMachine.State.SCAFFOLD){
            font.drawString(fitIsland(view.title,font,w-(start-x)-53),start,y+7,primary);
            font.drawString(fitIsland(view.detail,font,w-(start-x)-53),start,y+20,secondary);
        }else if(view.state==IslandStateMachine.State.CLOCK_REMINDER){
            font.drawString(fitIsland(view.title,font,w-(start-x)-53),start,y+(h-11)/2,primary);
            if(!view.trailing.isEmpty())
                font.drawString(view.trailing,x+w-clockWidthFor(view.trailing,font),y+(h-11)/2,secondary);
        }else if(view.state==IslandStateMachine.State.INVENTORY_ACTIVITY){
            font.drawString(fitIsland(view.title,font,w-(start-x)-53),start,y+7,primary);
            font.drawString(fitIsland(view.detail,font,w-(start-x)-53),start,y+20,secondary);
        }else{
            float sx=start;
            font.drawString(fitIsland(view.title,font,w-(sx-x)-14),sx,y+(h-11)/2,
                    view.state==IslandStateMachine.State.POSSIBLE_SETBACK
                            ?ink(UiEffects.semanticColor(UiEffects.SemanticRole.WARNING,accent,true),alpha):primary);
        }
    }

    private float clockWidthFor(String text,TTFFontRenderer font){return font==null?0:font.getWidth(text);}

    private IslandWidget[] islandWidgets(long now,float seconds,boolean motion){
        java.util.List<IslandWidget> result=new ArrayList<>();
        if(islandCpsWidget.getValue()){
            InputClickJournal.Snapshot clicks=InputClickJournal.global().snapshot(mc.theWorld,
                    mc.thePlayer!=null&&mc.currentScreen==null&&mc.inGameHasFocus,now);
            result.add(new IslandWidget(IslandWidget.Kind.CPS,"CPS",clicks.left+"·"+clicks.right,
                    "L / R",clicks.total()>0,seconds,motion));
        }
        if(islandFpsWidget.getValue()){
            int current=net.minecraft.client.Minecraft.getDebugFPS();
            result.add(new IslandWidget(IslandWidget.Kind.FPS,"FPS",current>0?Integer.toString(current):"--",
                    "live",current>0,seconds,motion));
        }
        if(islandExploitWidget.getValue()){
            me.youngjibbit.core.activity.ExploitActivitySnapshot[] observations=new me.youngjibbit.core.activity.ExploitActivitySnapshot[3];
            if(mc.theWorld!=null&&mc.thePlayer!=null&&Pulsar.INSTANCE.getMm()!=null){
                me.youngjibbit.module.impl.exploit.Backtrack backtrack=(me.youngjibbit.module.impl.exploit.Backtrack)
                        Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.exploit.Backtrack.class);
                me.youngjibbit.module.impl.exploit.LagRange lagRange=(me.youngjibbit.module.impl.exploit.LagRange)
                        Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.exploit.LagRange.class);
                me.youngjibbit.module.impl.exploit.Tickbase tickbase=(me.youngjibbit.module.impl.exploit.Tickbase)
                        Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.exploit.Tickbase.class);
                observations[0]=backtrack==null?null:backtrack.getActivitySnapshot();
                observations[1]=lagRange==null?null:lagRange.getActivitySnapshot();
                observations[2]=tickbase==null?null:tickbase.getActivitySnapshot();
            }
            long capturedAt=System.nanoTime();
            for(me.youngjibbit.core.activity.ExploitActivitySnapshot observation:observations)
                WidgetIncidentJournal.global().observeActivity(observation,capturedAt);
            result.add(IslandWidget.exploit(observations,capturedAt,seconds,motion));
        }
        return result.toArray(new IslandWidget[0]);
    }

    /** Brand, clock and widgets use one fitted canvas, so no part can collide on a small viewport. */
    private void paintIslandIdle(IslandPresentation view,IslandLayout.Frame frame,TTFFontRenderer font,
                                 TTFFontRenderer wordmark,Theme theme,float alpha,String design,
                                 IslandWidget[] widgets,boolean outside,boolean modernClock){
        boolean modern="Modern".equals(design)||"Creative".equals(design);
        boolean creative="Creative".equals(design);
        int count=outside?0:widgets.length;
        float width=IslandWidgetLayout.idleWidth(modern,count,outside),height=34;
        float scale=Math.min(1,Math.min(frame.width/width,frame.height/height));
        try(RenderState state=new RenderState()){
            glTranslatef(frame.x+(frame.width-width*scale)/2,frame.y+(frame.height-height*scale)/2,0);
            glScalef(scale,scale,1);
            renderLogo1N(10,7,20,theme,alpha,modern,creative,creative?islandNMorphAmount:0);
            float clockWidth=view.trailing.isEmpty()?0:modernClock?32:font.getWidth(view.trailing)+10;
            float right=width-9-clockWidth;
            float widgetStart=right-count*IslandWidgetLayout.INSIDE_WIDTH;
            float start=38,available=Math.max(0,widgetStart-start-5);
            float brandWidth=wordmark.getWidth(view.brand);
            if("Neutron".equals(view.brand)&&brandWidth<=available){
                float brandY=(height-wordmark.getHeight(view.brand))/2;
                try(UiEffects.GradientSpace domain=UiEffects.gradientSpace(start,brandY,brandWidth,wordmark.getHeight(view.brand))){
                    TextEffects.direct(theme,()->wordmark.drawString(view.brand,start,brandY,ink(0xffffffff,alpha)));
                }
                font.drawString(fitIsland(view.brandVersion,font,Math.max(0,available-brandWidth-6)),
                        start+brandWidth+6,(height-font.getHeight(view.brandVersion))/2,ink(0xffbdcad8,alpha));
            }else font.drawString(fitIsland(view.title,font,available),start,(height-11)/2,ink(0xfff4f6fc,alpha));
            for(int i=0;i<count;i++)paintIslandWidget(widgets[i],widgetStart+i*IslandWidgetLayout.INSIDE_WIDTH,
                    0,34,theme,alpha,false);
            if(!view.trailing.isEmpty()){
                if(modernClock&&view.clockFace!=null)renderClockFace(width-36,4,26,view.clockFace,theme,alpha);
                else font.drawString(view.trailing,width-9-font.getWidth(view.trailing),(height-11)/2,ink(0xffbdcad8,alpha));
            }
        }
    }

    private void paintIslandWidget(IslandWidget widget,float x,float y,float diameter,Theme theme,float alpha,boolean outside){
        if(diameter<=0||alpha<=0)return;
        TTFFontRenderer tiny=Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 12");
        TTFFontRenderer valueFont=Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 14");
        int accent=widget.active?theme.getPrimaryGradientRGB():0xff8290a4;
        try(RenderState state=new RenderState()){
            glTranslatef(x,y,0);glScalef(diameter/34,diameter/34,1);
            if(!outside)UIUtils.drawDecorationRound(3,3,28,28,10,new Color(255,255,255,Math.round(8*alpha)));
            String label=fitIsland(widget.label,tiny,24),value=fitIsland(widget.value,valueFont,28);
            float labelX=17-tiny.getWidth(label)/2+(widget.kind==IslandWidget.Kind.EXPLOIT?4:0);
            tiny.drawString(label,labelX,5,ink(accent,alpha*.9f));
            if(widget.kind==IslandWidget.Kind.EXPLOIT)
                paintExploitGlyph(IslandExploitGlyph.from(widget),labelX-5,8,accent,alpha*widget.pulse);
            valueFont.drawString(value,17-valueFont.getWidth(value)/2,13,ink(0xfff4f6fc,alpha));
            String detail=fitIsland(widget.detail,tiny,24);
            tiny.drawString(detail,17-tiny.getWidth(detail)/2,23,ink(0xffa9b6c7,alpha));
            // The tiny rim notch is activity animation, never a fabricated progress meter.
            UIUtils.drawDecorationRound(14,31,6,1,0.5f,tint(new Color(accent,true),alpha*.65f*widget.pulse));
            if(widget.kind==IslandWidget.Kind.EXPLOIT&&widget.active)
                UIUtils.drawDecorationRound(26,8,2,2,1,tint(new Color(accent,true),alpha*widget.pulse));
        }
    }

    private void paintIslandWidgetBar(IslandWidget widget,WidgetIncident incident,float x,float y,float width,
                                      float diameter,Theme theme,float alpha){
        paintIslandWidget(widget,x,y,diameter,theme,alpha,true);
        if(incident==null||width<=diameter+4)return;
        float scale=diameter/34;
        TTFFontRenderer tiny=Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 12");
        try(GuiClip clip=GuiClip.push(new ClipRect(x+diameter,y+2,Math.max(.01f,width-diameter-5),Math.max(.01f,diameter-4)))){
            try(RenderState state=new RenderState()){
                glTranslatef(x+diameter+2*scale,y,0);glScalef(scale,scale,1);
                float available=(width-diameter)/scale-10;
                int accent=incident.kind==WidgetIncident.Kind.FPS?0xffffcd78:theme.getPrimaryGradientRGB();
                tiny.drawString(fitIsland(incident.headline+" · "+incident.value,tiny,available),0,5,ink(accent,alpha));
                String[] detail=wrapIsland(incident.detail,tiny,available,2);
                for(int i=0;i<detail.length;i++)tiny.drawString(detail[i],0,14+i*8,ink(0xffc5cedc,alpha));
            }
        }
    }

    private void paintIslandNotification(IslandPresentation view,IslandLayout.Frame frame,TTFFontRenderer font,Theme theme,float alpha){
        if(view==null||view.notification==null)return;
        IslandContentLayout canvas=IslandContentLayout.forFrame(frame,view.state);
        me.youngjibbit.notifications.NotificationType type=view.notification.type;
        boolean warning=type==me.youngjibbit.notifications.NotificationType.WARNING;
        boolean error=type==me.youngjibbit.notifications.NotificationType.ERROR;
        boolean success=type==me.youngjibbit.notifications.NotificationType.SUCCESS
                ||type==me.youngjibbit.notifications.NotificationType.ENABLED;
        int accent=error?0xffff7f89:warning?0xffffc674:success?0xff79dbb5:theme.getPrimaryGradientRGB();
        TTFFontRenderer detailFont=Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 14");
        try(RenderState state=new RenderState()){
            glTranslatef(canvas.x,canvas.y,0);glScalef(canvas.scale,canvas.scale,1);
            UIUtils.drawDecorationRound(10,14,25,25,12.5f,tint(new Color(accent,true),.15f*alpha));
            if(warning||success||type==me.youngjibbit.notifications.NotificationType.DISABLED)
                drawNoticeSymbol(22.5f,26,warning,success,accent,alpha);
            else drawNotificationInfo(22.5f,26,error,accent,alpha);
            font.drawString(fitIsland(view.title,font,218),43,9,ink(0xfff4f6fc,alpha));
            String[] lines=wrapIsland(view.detail,detailFont,218,2);
            for(int i=0;i<lines.length;i++)detailFont.drawString(lines[i],43,25+i*10,ink(0xffb8c6d7,alpha));
            UIUtils.drawDecorationRound(43,50,216,2,1,new Color(255,255,255,Math.round(20*alpha)));
            float progress=Math.max(0,Math.min(1,view.notification.remaining));
            if(progress>0)UIUtils.drawDecorationRound(43,50,216*progress,2,Math.min(1,108*progress),
                    tint(new Color(accent,true),.8f*alpha));
        }
    }

    private static String[] wrapIsland(String input,TTFFontRenderer font,float width,int lines){
        java.util.List<String> result=new ArrayList<>();
        String remaining=IslandLayout.limitText(input,160);
        while(!remaining.isEmpty()&&result.size()<lines){
            if(result.size()==lines-1||font.getWidth(remaining)<=width){
                result.add(fitIsland(remaining,font,width));break;
            }
            int end=remaining.length();
            while(end>0&&font.getWidth(remaining.substring(0,end))>width)
                end=remaining.offsetByCodePoints(end,-1);
            if(end==0)break;
            int word=remaining.lastIndexOf(' ',end);
            if(word>end/2)end=word;
            result.add(remaining.substring(0,end).trim());remaining=remaining.substring(end).trim();
        }
        return result.toArray(new String[0]);
    }

    private void paintExploitGlyph(IslandExploitGlyph glyph,float x,float y,int rgb,float alpha){
        if(glyph==null)return;
        try(RenderState state=new RenderState()){
            org.lwjgl.opengl.GL20.glUseProgram(0);
            GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
            GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.disableLighting();glDisable(GL_LIGHTING);
            GlStateManager.enableBlend();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glLineWidth(1.1f);glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha);
            glBegin(GL_LINES);
            for(int i=0;i<glyph.segments();i++){
                glVertex2f(x+glyph.x1(i),y+glyph.y1(i));glVertex2f(x+glyph.x2(i),y+glyph.y2(i));
            }
            glEnd();
        }
    }

    private void drawNotificationInfo(float cx,float cy,boolean error,int rgb,float alpha){
        try(RenderState state=new RenderState()){
            org.lwjgl.opengl.GL20.glUseProgram(0);
            GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
            GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.disableLighting();glDisable(GL_LIGHTING);
            GlStateManager.enableBlend();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glLineWidth(1.4f);glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha);
            glBegin(GL_LINE_LOOP);
            for(int i=0;i<32;i++){double angle=i*Math.PI/16;glVertex2f(cx+(float)Math.cos(angle)*7,cy+(float)Math.sin(angle)*7);}
            glEnd();glBegin(GL_LINES);
            if(error){glVertex2f(cx-3,cy-3);glVertex2f(cx+3,cy+3);glVertex2f(cx+3,cy-3);glVertex2f(cx-3,cy+3);}
            else{glVertex2f(cx,cy-4);glVertex2f(cx,cy-3);glVertex2f(cx,cy-1);glVertex2f(cx,cy+4);}
            glEnd();
        }
    }

    /** Frozen target facts get their own reticle, exact health arcs and separate absorption rail. */
    private void paintIslandTarget(IslandTargetComposition target,IslandLayout.Frame frame,
                                   TTFFontRenderer font,Theme theme,float alpha){
        paintIslandTarget(target,frame,font,theme,alpha,target.healthFraction);
    }
    private void paintIslandTarget(IslandTargetComposition target,IslandLayout.Frame frame,
                                   TTFFontRenderer font,Theme theme,float alpha,float trailingHealth){
        IslandTargetComposition.Layout layout=new IslandTargetComposition.Layout(frame);
        int health=theme.getPrimaryGradientRGB(),absorption=0xffffcd69;
        try(RenderState state=new RenderState()){
            glTranslatef(layout.x,layout.y,0);glScalef(layout.scale,layout.scale,1);
            try(RenderState glyph=new RenderState()){
                org.lwjgl.opengl.GL20.glUseProgram(0);
                GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
                GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
                GlStateManager.disableLighting();glDisable(GL_LIGHTING);
                GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
                GlStateManager.enableBlend();glEnable(GL_BLEND);
                GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
                drawIslandTargetArc(IslandTargetComposition.HEALTH_TRACK,0xffa3afc0,alpha*.22f,2);
                drawIslandTargetArc(target.health,health,alpha,2);
                if(target.absorptionFraction>0){
                    drawIslandTargetArc(IslandTargetComposition.ABSORPTION_TRACK,absorption,alpha*.18f,1.5f);
                    drawIslandTargetArc(target.absorption,absorption,alpha,1.5f);
                }
                glLineWidth(1);glColor4f(.88f,.92f,.97f,alpha*target.reticleAlpha);
                glBegin(GL_LINES);
                for(int i=0;i<target.reticleSegments();i++){
                    glVertex2f(target.x1(i),target.y1(i));glVertex2f(target.x2(i),target.y2(i));
                }
                glEnd();
            }
            drawIslandTargetText(target.name,font,IslandTargetComposition.Layout.NAME_Y,
                    IslandTargetComposition.Layout.NAME_HEIGHT,ink(0xfff4f6fc,alpha));
            drawIslandTargetText(target.healthText,font,IslandTargetComposition.Layout.DETAIL_Y,
                    IslandTargetComposition.Layout.DETAIL_HEIGHT,ink(0xffbdcad8,alpha));
            float x=IslandTargetComposition.Layout.TEXT_X,width=IslandTargetComposition.Layout.TEXT_WIDTH;
            float healthY=IslandTargetComposition.Layout.HEALTH_Y,healthHeight=IslandTargetComposition.Layout.HEALTH_HEIGHT;
            UIUtils.drawDecorationRound(x,healthY,width,healthHeight,healthHeight/2,
                    new Color(163,175,192,Math.round(alpha*38)));
            float trail=Math.max(target.healthWidth,Math.min(width,width*trailingHealth));
            if(trail>target.healthWidth+.1f)UIUtils.drawDecorationRound(x,healthY,trail,healthHeight,healthHeight/2,
                    new Color(255,177,112,Math.round(alpha*120)));
            if(target.healthWidth>0)UIUtils.drawGradientHorizontal(x,healthY,target.healthWidth,healthHeight,
                    Math.min(healthHeight/2,target.healthWidth/2),tint(theme.getPrimaryGradientColor(),alpha),
                    tint(theme.getSecondaryGradientColor(),alpha));
            if(target.absorptionWidth>0){
                float y=IslandTargetComposition.Layout.ABSORPTION_Y,h=IslandTargetComposition.Layout.ABSORPTION_HEIGHT;
                UIUtils.drawDecorationRound(x,y,width,h,h/2,new Color(255,205,105,Math.round(alpha*30)));
                UIUtils.drawDecorationRound(x,y,target.absorptionWidth,h,Math.min(h/2,target.absorptionWidth/2),
                        new Color(255,205,105,Math.round(alpha*230)));
            }
        }
    }
    private static void drawIslandTargetArc(IslandTargetComposition.Arc arc,int rgb,float alpha,float width){
        if(arc.segments==0)return;
        glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha);glLineWidth(width);
        glBegin(GL_LINE_STRIP);
        for(int i=0;i<=arc.segments;i++)glVertex2f(arc.x(i),arc.y(i));
        glEnd();
    }
    private void drawIslandTargetText(String value,TTFFontRenderer font,float y,float maxHeight,int color){
        String text=fitIsland(value,font,IslandTargetComposition.Layout.TEXT_WIDTH);
        float scale=Math.min(1,maxHeight/Math.max(1,font.getHeight(text)));
        try(RenderState state=new RenderState()){
            glTranslatef(IslandTargetComposition.Layout.TEXT_X,y,0);glScalef(scale,scale,1);
            font.drawString(text,0,0,color);
        }
    }

    /** Status row and measured-correction dashboard have their own geometry, without a decorative N. */
    private void paintIslandScaffold(IslandPresentation view,IslandLayout.Frame frame,TTFFontRenderer font,
                                     Theme theme,float alpha,String design,IslandScaffoldIndicator indicator,
                                     IslandPlacementMetrics.Snapshot metrics){
        IslandScaffoldIndicator.Layout layout=new IslandScaffoldIndicator.Layout(frame);
        try(RenderState state=new RenderState()){
            glTranslatef(layout.x,layout.y,0);glScalef(layout.scale,layout.scale,1);
            if(!renderScaffoldItem(IslandScaffoldIndicator.Layout.ITEM_X,IslandScaffoldIndicator.Layout.ITEM_Y,
                    IslandScaffoldIndicator.Layout.ITEM_SIZE,view.scaffold,alpha))
                renderLogo1N(IslandScaffoldIndicator.Layout.ITEM_X,IslandScaffoldIndicator.Layout.ITEM_Y,
                        IslandScaffoldIndicator.Layout.ITEM_SIZE,theme,alpha,
                        "Modern".equals(design)||"Creative".equals(design),"Creative".equals(design),0);
            TTFFontRenderer tiny=Pulsar.INSTANCE.getFontManager().getFont("DmSansMedium 12");
            String blocks=view.scaffold.blockCount<0?"-- blocks":view.scaffold.blockCount+" blocks";
            font.drawString(fitIsland(blocks,font,80),IslandScaffoldIndicator.Layout.TEXT_X,7,ink(0xfff4f6fc,alpha));
            String cps=(metrics!=null&&metrics.known?Integer.toString(metrics.cps):"--")+" CPS";
            UIUtils.drawDecorationRound(126,5,50,15,7.5f,tint(theme.getPrimaryGradientColor(),.11f*alpha));
            tiny.drawString(cps,151-tiny.getWidth(cps)/2,9,ink(theme.getPrimaryGradientRGB(),alpha));
            String profile=view.scaffold.approach+(view.scaffold.profile.isEmpty()?"":" / "+view.scaffold.profile);
            tiny.drawString(fitIsland(profile,tiny,IslandScaffoldIndicator.Layout.TEXT_WIDTH),
                    IslandScaffoldIndicator.Layout.TEXT_X,23,ink(0xffbdcad8,alpha));
            int color=indicator.warning()?UiEffects.semanticColor(UiEffects.SemanticRole.WARNING,
                    theme.getPrimaryGradientRGB(),true):indicator.status==IslandScaffoldIndicator.Status.READY
                    ?UiEffects.semanticColor(UiEffects.SemanticRole.SUCCESS,theme.getPrimaryGradientRGB(),true)
                    :theme.getPrimaryGradientRGB();
            try(RenderState glyph=new RenderState()){
                org.lwjgl.opengl.GL20.glUseProgram(0);
                GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
                GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
                GlStateManager.disableLighting();glDisable(GL_LIGHTING);
                GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
                GlStateManager.enableBlend();glEnable(GL_BLEND);
                GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
                glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha*indicator.opacity);
                glLineWidth(1.25f);glTranslatef(IslandScaffoldIndicator.Layout.STATUS_X,IslandScaffoldIndicator.Layout.STATUS_Y,0);
                glBegin(GL_LINES);
                for(int i=0;i<indicator.segmentCount();i++){
                    glVertex2f(indicator.x1(i),indicator.y1(i));glVertex2f(indicator.x2(i),indicator.y2(i));
                }
                glEnd();
            }
            String label=fitIsland(indicator.label,tiny,42);
            tiny.drawString(label,IslandScaffoldIndicator.Layout.STATUS_X+12-tiny.getWidth(label)/2,
                    IslandScaffoldIndicator.Layout.LABEL_Y,ink(color,alpha));
            paintPlacementTrend(metrics,theme.getPrimaryGradientRGB(),alpha);
        }
    }

    private void paintPlacementTrend(IslandPlacementMetrics.Snapshot metrics,int color,float alpha){
        float x=IslandScaffoldIndicator.Layout.GRAPH_X,y=IslandScaffoldIndicator.Layout.GRAPH_Y;
        float width=IslandScaffoldIndicator.Layout.GRAPH_WIDTH,height=IslandScaffoldIndicator.Layout.GRAPH_HEIGHT;
        UIUtils.drawDecorationRound(x,y+height,width,1,.5f,new Color(255,255,255,Math.round(22*alpha)));
        if(metrics==null||!metrics.known)return;
        try(RenderState state=new RenderState()){
            org.lwjgl.opengl.GL20.glUseProgram(0);
            GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
            GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.disableLighting();glDisable(GL_LIGHTING);
            GlStateManager.enableBlend();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glLineWidth(1.2f);glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha*.8f);
            glBegin(GL_LINE_STRIP);
            for(int i=0;i<metrics.samples();i++)
                glVertex2f(x+width*i/Math.max(1,metrics.samples()-1),y+height-height*metrics.sample(i)/metrics.ceiling());
            glEnd();
        }
    }

    private void paintIslandNotice(IslandPresentation view,IslandLayout.Frame frame,TTFFontRenderer font,float alpha){
        IslandContentLayout canvas=IslandContentLayout.forFrame(frame,view.state);
        IslandNotice notice=view.notice;
        int primary=ink(0xfff4f6fc,alpha),secondary=ink(0xffb8c1cf,alpha);
        try(RenderState state=new RenderState()){
            glTranslatef(canvas.x,canvas.y,0);glScalef(canvas.scale,canvas.scale,1);
            if(view.state==IslandStateMachine.State.MODULE_TOAST){
                int status=notice.enabled?0xff70d8ae:0xffa3afc0;
                UIUtils.drawDecorationRound(9,9,20,20,10,tint(new Color(status,true),.16f*alpha));
                drawNoticeSymbol(19,19,false,notice.enabled,status,alpha);
                font.drawString(fitIsland(view.title,font,121),36,13,primary);
                UIUtils.drawDecorationRound(163,10,34,18,9,tint(new Color(status,true),.13f*alpha));
                String label=notice.enabled?"ON":"OFF";
                font.drawString(label,180-font.getWidth(label)/2,13,ink(status,alpha));
            }else{
                UIUtils.drawDecorationRound(11,10,3,22,1.5f,new Color(244,180,83,Math.round(230*alpha)));
                drawNoticeSymbol(27,17,true,false,0xfff4b453,alpha);
                font.drawString(fitIsland(view.title,font,202),41,9,primary);
                font.drawString(fitIsland(view.detail,font,224),15,27,secondary);
                UIUtils.drawDecorationRound(13,45,228,1,.5f,new Color(244,180,83,Math.round(45*alpha)));
                font.drawString("Distance",15,49,secondary);font.drawString("Rotation",139,49,secondary);
                font.drawString(fitIsland(notice.distanceText,font,108),15,60,primary);
                font.drawString(fitIsland(notice.angleText,font,99),139,60,primary);
            }
        }
    }

    /** Actual inventory work uses a tray symbol and optional known progress, without an invented denominator. */
    private void paintIslandInventory(IslandPresentation view,IslandLayout.Frame frame,TTFFontRenderer font,Theme theme,float alpha){
        IslandContentLayout canvas=IslandContentLayout.forFrame(frame,view.state);
        IslandStateMachine.InventoryActivity activity=view.inventory;
        try(RenderState state=new RenderState()){
            glTranslatef(canvas.x,canvas.y,0);glScalef(canvas.scale,canvas.scale,1);
            int accent=theme.getPrimaryGradientRGB();
            UIUtils.drawDecorationRound(10,12,22,18,4,tint(new Color(accent,true),.20f*alpha));
            UIUtils.drawDecorationRound(14,16,6,5,1,tint(new Color(accent,true),.68f*alpha));
            UIUtils.drawDecorationRound(23,16,5,5,1,tint(new Color(accent,true),.48f*alpha));
            UIUtils.drawDecorationRound(14,24,14,2,1,tint(new Color(accent,true),.72f*alpha));
            font.drawString(fitIsland(view.title,font,184),40,6,ink(0xfff4f6fc,alpha));
            font.drawString(fitIsland(view.detail,font,184),40,20,ink(0xffb8c1cf,alpha));
            if(activity.processedCount>=0&&activity.totalCount>0){
                float fraction=Math.max(0,Math.min(1,activity.processedCount/(float)activity.totalCount));
                UIUtils.drawDecorationRound(40,35,184,2,1,new Color(255,255,255,Math.round(25*alpha)));
                if(fraction>0)UIUtils.drawDecorationRound(40,35,184*fraction,2,1,tint(new Color(accent,true),alpha));
            }
        }
    }

    private void drawNoticeSymbol(float cx,float cy,boolean warning,boolean enabled,int rgb,float alpha){
        try(RenderState state=new RenderState()){
            org.lwjgl.opengl.GL20.glUseProgram(0);
            GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
            GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.disableLighting();glDisable(GL_LIGHTING);
            GlStateManager.enableBlend();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glLineWidth(1.5f);glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha);
            if(warning){
                glBegin(GL_LINE_LOOP);glVertex2f(cx,cy-7);glVertex2f(cx+7,cy+6);glVertex2f(cx-7,cy+6);glEnd();
                glBegin(GL_LINES);glVertex2f(cx,cy-2);glVertex2f(cx,cy+1);glVertex2f(cx,cy+3);glVertex2f(cx,cy+4);glEnd();
            }else if(enabled){
                glBegin(GL_LINE_STRIP);glVertex2f(cx-4,cy);glVertex2f(cx-1,cy+3);glVertex2f(cx+5,cy-4);glEnd();
            }else{glBegin(GL_LINES);glVertex2f(cx-4,cy);glVertex2f(cx+4,cy);glEnd();}
        }
    }

    /** Rebuild only a display copy from scalar registry metadata; no inventory object is retained. */
    private boolean renderScaffoldItem(float x,float y,float size,IslandStateMachine.Scaffold snapshot,float alpha){
        if(snapshot.itemId<0||snapshot.metadata<0||size<=0||alpha<=0)return false;
        net.minecraft.item.Item item=net.minecraft.item.Item.getItemById(snapshot.itemId);
        if(item==null)return false;
        net.minecraft.item.ItemStack display=new net.minecraft.item.ItemStack(item,1,snapshot.metadata);
        try(RenderState state=new RenderState()){
            glTranslatef(x,y,0);glScalef(size/16,size/16,1);
            GlStateManager.enableDepth();glEnable(GL_DEPTH_TEST);GlStateManager.depthFunc(GL_LEQUAL);
            GlStateManager.depthMask(true);GlStateManager.enableAlpha();glEnable(GL_ALPHA_TEST);
            GlStateManager.enableBlend();glEnable(GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
            GlStateManager.color(1,1,1,alpha);glColor4f(1,1,1,alpha);
            float oldZ=mc.getRenderItem().zLevel;
            try{
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().zLevel=100;
                mc.getRenderItem().renderItemAndEffectIntoGUI(display,0,0);
            }finally{mc.getRenderItem().zLevel=oldZ;net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();}
        }
        return true;
    }

    /** Real hour/minute hands, drawn without a new framebuffer, shader or animation clock. */
    private void renderClockFace(float x,float y,float size,IslandClockFace face,Theme theme,float alpha){
        if(face==null||size<=0||alpha<=0)return;
        float cx=x+size/2,cy=y+size/2,r=size*.43f;
        UIUtils.drawDecorationRound(x,y,size,size,size/2,new Color(19,22,31,Math.round(220*alpha)));
        try(RenderState state=new RenderState()){
            org.lwjgl.opengl.GL20.glUseProgram(0);
            GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
            GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
            GlStateManager.disableLighting();glDisable(GL_LIGHTING);
            GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.enableBlend();glEnable(GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
            glEnable(GL_LINE_SMOOTH);glLineWidth(1.1f);
            Color rim=theme.getPrimaryGradientColor();
            glColor4f(rim.getRed()/255f,rim.getGreen()/255f,rim.getBlue()/255f,alpha*.78f);
            glBegin(GL_LINE_LOOP);
            for(int i=0;i<48;i++){double a=i*Math.PI/24;glVertex2f(cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r);}
            glEnd();
            glColor4f(.91f,.94f,.98f,alpha);
            glBegin(GL_LINES);
            for(int i=0;i<12;i++){
                double a=i*Math.PI/6;float dx=(float)Math.cos(a),dy=(float)Math.sin(a),inner=i%3==0?.72f:.83f;
                glVertex2f(cx+dx*r*inner,cy+dy*r*inner);glVertex2f(cx+dx*r*.94f,cy+dy*r*.94f);
            }
            glEnd();
            glLineWidth(1.6f);
            glBegin(GL_LINES);
            glVertex2f(cx,cy);glVertex2f(cx+face.hourX*r*.52f,cy+face.hourY*r*.52f);
            glVertex2f(cx,cy);glVertex2f(cx+face.minuteX*r*.76f,cy+face.minuteY*r*.76f);
            glEnd();
            glLineWidth(1);
            glColor4f(rim.getRed()/255f,rim.getGreen()/255f,rim.getBlue()/255f,alpha);
            glBegin(GL_LINES);
            glVertex2f(cx-face.secondX*r*.12f,cy-face.secondY*r*.12f);
            glVertex2f(cx+face.secondX*r*.84f,cy+face.secondY*r*.84f);
            glEnd();
        }
    }

    /** The original N texture supplies both its stroke alpha and its restrained existing halo. */
    private void renderLogo1N(float bx,float by,float size,Theme theme,float alpha,
                              boolean modern,boolean creative,float morph){
        if(alpha<=0||size<=0)return;
        // UiEffects.time() is already seconds and freezes with reduced/disabled motion.
        double phase=UiEffects.time()*(creative?.14:modern?.08:.12);
        float blend=.5f-.5f*(float)Math.cos(2*Math.PI*phase);
        int first=theme.getPrimaryGradientRGB(),second=theme.getSecondaryGradientRGB();
        int left=Theme.interpolateRGB(first,second,blend);
        int right=Theme.interpolateRGB(first,second,1-blend);
        float opacity=Math.max(0,Math.min(1,alpha));
        float plateRadius=modern||creative?7:11;
        Color plate=creative?new Color(38,34,50,Math.round(opacity*205))
                :modern?NeutronVisualTokens.alpha(NeutronVisualTokens.CARD.getRGB(),Math.round(opacity*200))
                :new Color(26,47,60,Math.round(opacity*200));
        UIUtils.drawDecorationRound(bx,by,size,size,plateRadius,plate);
        try(RenderState state=new RenderState()){
            org.lwjgl.opengl.GL20.glUseProgram(0);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            org.lwjgl.opengl.GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
            GlStateManager.enableBlend();glEnable(GL_BLEND);
            GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.disableLighting();glDisable(GL_LIGHTING);
            GlStateManager.shadeModel(GL_SMOOTH);glShadeModel(GL_SMOOTH);
            GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
            glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
            mc.getTextureManager().bindTexture(LOGO1);
            // Padded UV region around the unchanged original stroke, not a replacement glyph.
            float u0=.30f,u1=.70f,v0=.33f,v1=.73f;
            glBegin(GL_QUADS);
            glColor4f(((left>>16)&255)/255f,((left>>8)&255)/255f,(left&255)/255f,opacity);
            glTexCoord2f(u0,v0);glVertex2f(bx,by);
            glTexCoord2f(u0,v1);glVertex2f(bx,by+size);
            glColor4f(((right>>16)&255)/255f,((right>>8)&255)/255f,(right&255)/255f,opacity);
            glTexCoord2f(u1,v1);glVertex2f(bx+size,by+size);
            glTexCoord2f(u1,v0);glVertex2f(bx+size,by);
            glEnd();
        }
    }

    private static Color tint(Color color,float alpha){
        return new Color(color.getRed(),color.getGreen(),color.getBlue(),Math.round(Math.max(0,Math.min(1,alpha))*255));
    }
    private static int ink(int rgb,float alpha){
        return (Math.round(Math.max(0,Math.min(1,alpha))*255)<<24)|(rgb&0xffffff);
    }
    private static String fitIsland(String value,TTFFontRenderer font,float available){
        String text=IslandLayout.limitText(value,64);
        if(available<=0)return "";
        if(font.getWidth(text)<=available)return text;
        while(!text.isEmpty()&&font.getWidth(text+"...")>available)
            text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
        return text.isEmpty()?"":text+"...";
    }
    private String islandTime(){
        long minute=System.currentTimeMillis()/60_000L;
        if(minute!=islandClockMinute){
            islandClockMinute=minute;
            islandClockText=new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(minute*60_000L));
        }
        return islandClockText;
    }
    private void resetIsland(){
        islandState.reset();islandMotion.reset();islandNMorph.reset();islandPlacements.reset();islandWorld=null;islandEpoch++;
        islandAttachmentKind=null;islandTargetTrail.reset();islandTrailTarget=Integer.MIN_VALUE;
        for(HudSpring spring:islandWidgetOpen)spring.reset();
        WidgetIncidentJournal.global().clear();
        islandNotices.reset(System.nanoTime(),ModuleToggleJournal.currentSequence());
        islandLastFrameNanos=0;islandLastKey=null;islandLastSelection=islandPrevious=null;
        islandActive=false;inventoryFixture=null;inventoryFixtureUntil=0;
    }
}
