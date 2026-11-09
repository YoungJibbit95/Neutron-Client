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
import me.youngjibbit.module.impl.client.island.IslandPresentation;
import me.youngjibbit.module.impl.client.island.IslandStateMachine;
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
    private final IslandStateMachine islandState=new IslandStateMachine();
    private final IslandMorphMotion islandMotion=new IslandMorphMotion();
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
        islandTarget.setVisibility(m->islandSelected());
        islandClock.setVisibility(m->islandSelected());
        islandInventory.setVisibility(m->false);
        islandScaffold.setVisibility(m->islandSelected());
        islandLiveInventory.setVisibility(m->islandSelected());}
    /** Global opt-in upgraded the historical Glass default without rewriting stored Mode. */
    boolean islandSelected(){
        String m=mode.getValue();
        return "Dynamic Island".equals(m)||"Modern".equals(m)||"Creative".equals(m)||
                (ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())&&"Glass".equals(m));
    }
    @Subscribe private final Listener<Event2D> onRender2D=new Listener<>(event->{if(!mc.gameSettings.showDebugInfo)renderWatermark();else if(islandActive){resetIsland();surface.close();}});
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
        islandActive=true;
        // World object identity is a scene epoch: entity-id reuse cannot leak old content.
        if(islandWorld!=mc.theWorld){
            islandWorld=mc.theWorld;islandEpoch++;islandState.reset();islandMotion.reset();
            islandLastSelection=islandPrevious=null;islandLastKey=null;islandLastFrameNanos=0;
            inventoryFixture=null;inventoryFixtureUntil=0;
        }
        long now=System.nanoTime();
        float dt=islandLastFrameNanos==0||now<=islandLastFrameNanos?0
                :Math.min(.05f,(now-islandLastFrameNanos)/1_000_000_000f);
        islandLastFrameNanos=now;
        if(!mc.inGameHasFocus){islandMotion.reset();dt=0;}

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

        IslandStateMachine.Selection selected=islandState.resolve(islandEpoch,now,
                islandTargetSnapshot(),islandScaffoldSnapshot(now),clockRem,islandInventorySnapshot(now),
                islandTarget.getValue(),islandScaffold.getValue(),islandClock.getValue(),islandLiveInventory.getValue());
        String key=selected.state.name()+(selected.target==null?"":"#"+selected.target.entityId+":"+selected.target.name)
                +(selected.clock==null?"":"@"+selected.clock.time)
                +(selected.scaffold==null?"":"#"+selected.scaffold.status+":"+selected.scaffold.approach)
                +(selected.inventory==null?"":"#"+selected.inventory.producer+":"+selected.inventory.action);
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
        IslandLayout.Frame frame=islandMotion.sample(IslandLayout.forState(selected.state,
                sr.getScaledWidth(),sr.getScaledHeight(),modern),dt,animations,reduced,intensity);
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
        final boolean panel=background.getValue(), useBlur=blur.getValue(),
                useGlow=glow.getValue();
        float designBlurRadius=designCreative?3f:modern?3.5f:5f;
        float designFillAlpha=(designCreative?.92f:modern?.85f:.65f)*frame.opacity;
        float designOutlineAlpha=(designCreative?.22f:modern?.14f:.48f)*frame.opacity;
        final IslandPresentation present=IslandPresentation.from(selected,clientName,clientVersion,clockText,
                showName,showVersion,showClock).withClockFace(liveClock);
        final IslandPresentation fading=previous==null?null:IslandPresentation.from(previous,
                clientName,clientVersion,clockText,showName,showVersion,showClock).withClockFace(liveClock);
        final float clipInset=Math.min(2,Math.min(frame.width,frame.height)*.08f);
        // A queued bloom pass may outlive a world/mode/visibility change: fence it.
        final long passEpoch=islandEpoch;
        // Reuse the single existing surface and shared halo/blur/RenderState pools.
        final Runnable drawIsland=()->surface.designedPanel(frame.x,frame.y,frame.width,frame.height,
                frame.radius,theme,panel,useBlur,useGlow,designBlurRadius,
                designFillAlpha,designOutlineAlpha,false,
                ()->{
                    try(GuiClip clip=GuiClip.push(new ClipRect(frame.x+clipInset,frame.y+clipInset,
                            Math.max(.01f,frame.width-2*clipInset),Math.max(.01f,frame.height-2*clipInset)))){
                        if(modern&&panel&&present.clockFace==null){
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
                        if(fading!=null&&mix<1)paintIsland(fading,frame,font,wordmark,theme,
                                (1-mix)*frame.opacity,design);
                        paintIsland(present,frame,font,wordmark,theme,mix*frame.opacity,design);
                    }
                });
        GuiShaderBloom.afterBatch(()->{if(islandActive&&passEpoch==islandEpoch)drawIsland.run();});
    }

    private void paintIsland(IslandPresentation view,IslandLayout.Frame frame,
                             TTFFontRenderer font,TTFFontRenderer wordmark,Theme theme,float alpha,String design){
        if(alpha<=0)return;
        if(view.clockFace!=null){
            float diameter=Math.min(frame.width,frame.height)*.88f;
            renderClockFace(frame.x+(frame.width-diameter)/2,frame.y+(frame.height-diameter)/2,
                    diameter,view.clockFace,theme,alpha);
            return;
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
        if(view.state==IslandStateMachine.State.IDLE){
            float clockWidth=view.trailing.isEmpty()?0:font.getWidth(view.trailing)+11;
            float available=Math.max(0,w-(start-x)-clockWidth);
            float brandWidth=wordmark.getWidth(view.brand);
            if("Neutron".equals(view.brand)&&brandWidth<=available){
                final float brandY=y+(h-wordmark.getHeight(view.brand))/2;
                try(UiEffects.GradientSpace domain=UiEffects.gradientSpace(start,brandY,brandWidth,wordmark.getHeight(view.brand))){
                    TextEffects.direct(theme,()->wordmark.drawString(view.brand,start,brandY,ink(0xffffffff,alpha)));
                }
                float suffixX=start+brandWidth+6;
                font.drawString(fitIsland(view.brandVersion,font,Math.max(0,available-brandWidth-6)),
                        suffixX,y+(h-11)/2,secondary);
            }else font.drawString(fitIsland(view.title,font,available),start,y+(h-11)/2,primary);
            if(!view.trailing.isEmpty())
                font.drawString(view.trailing,x+w-clockWidth,y+(h-11)/2,secondary);
        }else if(view.state==IslandStateMachine.State.TARGET&&view.hasHealthBar){
            font.drawString(fitIsland(view.title,font,w-(start-x)-52),start,y+7,primary);
            font.drawString(fitIsland(view.detail,font,w-(start-x)-52),start,y+20,secondary);
            float barX=start,barY=y+h-11,barW=Math.max(0,w-(start-x)-52);
            UIUtils.drawRound(barX,barY,barW,4,2,new Color(55,67,80,Math.round(alpha*215)));
            float fill=barW*view.healthFraction;
            if(fill>.01f)UIUtils.drawGradientHorizontal(barX,barY,fill,4,Math.min(2,fill/2),
                    tint(theme.getPrimaryGradientColor(),alpha),tint(theme.getSecondaryGradientColor(),alpha));
            float absorbed=barW*view.absorptionFraction;
            if(absorbed>.01f)UIUtils.drawRound(barX+barW-absorbed,barY,absorbed,4,
                    Math.min(2,absorbed/2),new Color(255,205,105,Math.round(alpha*230)));
        }else if(view.state==IslandStateMachine.State.SCAFFOLD){
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
        islandState.reset();islandMotion.reset();islandNMorph.reset();islandWorld=null;islandEpoch++;
        islandLastFrameNanos=0;islandLastKey=null;islandLastSelection=islandPrevious=null;
        islandActive=false;inventoryFixture=null;inventoryFixtureUntil=0;
    }
}
