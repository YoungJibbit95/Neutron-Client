package me.youngjibbit.module.impl.client;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.gui.IslandMorphMotion;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.module.impl.client.island.IslandLayout;
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
import net.minecraft.client.gui.Gui;
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
    private String islandClockLastSlot = "";
    // Inventory Activity: synthetische Headless-Fixture bis TECH InventoryActivitySnapshot freigegeben.
    private IslandStateMachine.InventoryActivity inventoryFixture = null;
    private long inventoryFixtureUntil = 0;

    public WatermarkHUDModule(){radius.setVisibility(m->localRadius.getValue());animated.setVisibility(m->!textStyle.getValue().equals("Theme"));speed.setVisibility(m->!textStyle.getValue().equals("Theme")&&animated.getValue());
        islandTarget.setVisibility(m->islandSelected());
        islandClock.setVisibility(m->islandSelected());
        islandInventory.setVisibility(m->islandSelected());}
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

    /** Prüft lokale 10-Minuten-Marken (:00, :10, ...) als Clock-Reminder-Kandidat. */
    private boolean isClockReminderTime(){
        if(!islandClock.getValue())return false;
        long now=System.currentTimeMillis();
        long minute=now/60_000L;
        int minOfHour=(int)(minute%60);
        if(minOfHour%10!=0)return false;
        String slot=new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(now));
        if(slot.equals(islandClockLastSlot))return false;
        islandClockLastSlot=slot;
        return true;
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
        if(islandClock.getValue()&&isClockReminderTime()){
            long wall=System.currentTimeMillis();
            String t=new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(wall));
            islandState.offer(IslandStateMachine.State.CLOCK_REMINDER,"clock:"+t,t,islandEpoch,now);
            clockRem=new IslandStateMachine.ClockReminder(t,false);
        }

        IslandStateMachine.Selection selected=islandState.resolve(islandEpoch,now,
                islandTargetSnapshot(),null,clockRem,inventoryFixture(),
                islandTarget.getValue(),false,islandClock.getValue(),islandInventory.getValue());
        String key=selected.state.name()+(selected.target==null?"":"#"+selected.target.entityId+":"+selected.target.name)
                +(selected.clock==null?"":"@"+selected.clock.time)
                +(selected.inventory==null?"":"#"+selected.inventory.action);
        if(!key.equals(islandLastKey)){
            // Only the neutral idle label may remain during a crossfade. Never retain lost targets.
            islandPrevious=islandLastSelection!=null&&islandLastSelection.state==IslandStateMachine.State.IDLE
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
        final String clockText=islandClock.getValue()?islandTime():"";
        final String clientName=Pulsar.INSTANCE.getName(), clientVersion=Pulsar.INSTANCE.getVersion();
        final boolean showName=name.getValue(), showVersion=version.getValue(), showClock=islandClock.getValue();
        final boolean panel=background.getValue(), useBlur=blur.getValue(),
                useGlow=glow.getValue();
        float designBlurRadius=designCreative?3f:modern?3.5f:5f;
        float designFillAlpha=(designCreative?.92f:modern?.85f:.65f)*frame.opacity;
        float designOutlineAlpha=(designCreative?.22f:modern?.14f:.48f)*frame.opacity;
        final IslandPresentation present=IslandPresentation.from(selected,clientName,clientVersion,clockText,
                showName,showVersion,showClock);
        final IslandPresentation fading=previous==null?null:IslandPresentation.from(previous,
                clientName,clientVersion,clockText,showName,showVersion,showClock);
        // A queued bloom pass may outlive a world/mode/visibility change: fence it.
        final long passEpoch=islandEpoch;
        // Reuse the single existing surface and shared halo/blur/RenderState pools.
        final Runnable drawIsland=()->surface.designedPanel(frame.x,frame.y,frame.width,frame.height,
                frame.radius,theme,panel,useBlur,useGlow,designBlurRadius,
                designFillAlpha,designOutlineAlpha,false,
                ()->{
                    try(GuiClip clip=GuiClip.push(new ClipRect(frame.x+2,frame.y+2,
                            Math.max(1,frame.width-4),Math.max(1,frame.height-4)))){
                        if(modern&&panel){
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
                        if(fading!=null&&mix<1)paintIsland(fading,frame,font,theme,
                                (1-mix)*frame.opacity,design);
                        paintIsland(present,frame,font,theme,mix*frame.opacity,design);
                    }
                });
        GuiShaderBloom.afterBatch(()->{if(islandActive&&passEpoch==islandEpoch)drawIsland.run();});
    }

    private void paintIsland(IslandPresentation view,IslandLayout.Frame frame,
                             TTFFontRenderer font,Theme theme,float alpha,String design){
        if(alpha<=0)return;
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

        // Logo1-N: echtes geschwungenes N als alpha-geclippte Form mit animiertem Theme-Gradient
        // innerhalb der N sowie kontrolliertem Edge-Glow entlang der Kontur.
        renderLogo1N(badgeX,badgeY,nSize,theme,alpha,modern,creative,morph);

        float start=creative?badgeX+nSize+10:modern?(badgeX+badge+8):(x+39);
        if(view.state==IslandStateMachine.State.IDLE){
            float clockWidth=view.trailing.isEmpty()?0:font.getWidth(view.trailing)+11;
            font.drawString(fitIsland(view.title,font,w-(start-x)-clockWidth),
                    start,y+(h-11)/2,primary);
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
            float sx=startX(design);
            font.drawString(fitIsland(view.title,font,w-sx-53),sx,y+(h-11)/2,
                    view.state==IslandStateMachine.State.POSSIBLE_SETBACK
                            ?ink(UiEffects.semanticColor(UiEffects.SemanticRole.WARNING,accent,true),alpha):primary);
        }
    }

    private float startX(String design){return "Modern".equals(design)||"Creative".equals(design)?21:9;}

    private float clockWidthFor(String text,TTFFontRenderer font){return font==null?0:font.getWidth(text);}

    /** Rendert Logo1-N als alpha-geclippte Form mit animiertem Theme-Gradient innerhalb der N. */
    private void renderLogo1N(float bx,float by,float size,Theme theme,float alpha,
                              boolean modern,boolean creative,float morph){
        if(alpha<=0)return;
        // Animated gradient phase: cycle between primary and secondary theme colors.
        // Modern runs the cycle slower for a subtle, restrained feel.
        double phase=(UiEffects.time()/1e9)%1.0;
        if(modern)phase*=0.5f;
        int c1=theme.getPrimaryGradientRGB();
        int c2=theme.getSecondaryGradientRGB();
        int r=(int)(((c1>>16)&255)*(1-phase)+((c2>>16)&255)*phase);
        int g=(int)(((c1>>8)&255)*(1-phase)+((c2>>8)&255)*phase);
        int b=(int)((c1&255)*(1-phase)+(c2&255)*phase);

        // Hintergrund-Platte der N-Form (solid, leicht abgedunkelt).
        float plateRadius=modern||creative?7:11;
        Color plateColor;
        if(creative){
            plateColor=new Color(38,34,50,Math.round(alpha*205));
        } else if(modern){
            plateColor=NeutronVisualTokens.alpha(NeutronVisualTokens.CARD.getRGB(),
                    Math.round(alpha*200));
        } else {
            plateColor=new Color(26,47,60,Math.round(alpha*200));
        }
        UIUtils.drawRound(bx,by,size,size,plateRadius,plateColor);

        // Logo1 als Alpha-Mask: zeichne die N mit dem animierten Gradient-Farbwert getönt.
        // Das Bild selbst trägt die geschwungene N-Form; der Gradient-Farbton animiert sich über die Zeit.
        GlStateManager.pushMatrix();
        glEnable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        glColor4f(((r&255)/255f),((g&255)/255f),((b&255)/255f),alpha);
        mc.getTextureManager().bindTexture(LOGO1);
        Gui.drawModalRectWithCustomSizedTexture((int)bx,(int)by,0.0f,0.0f,(int)size,(int)size,size,size);
        glColor4f(1,1,1,1);
        GlStateManager.popMatrix();

        // Kontur-Glow entlang der N-Form (weicher, außerhalb).
        float glow;
        if(creative){
            // Ausdrücklicher Glow, verstärkt durch den Morph-Effekt.
            glow=2.2f+1.6f*morph;
        } else if(modern){
            glow=1.0f; // zurückhaltend
        } else {
            glow=2.0f;
        }
        UIUtils.drawRound(bx-glow,by-glow,size+glow*2,size+glow*2,plateRadius,
                new Color(r,g,b,Math.round(Math.min(1,.35f*glow)*alpha*255)));
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
