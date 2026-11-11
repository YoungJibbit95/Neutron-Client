package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.value.Value;
import java.util.Locale;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.clickgui.utils.GuiStyle;
import me.youngjibbit.clickgui.utils.MenuSurface;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.render.ClickGUI;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.Blur;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ClickGui extends GuiScreen {
    private final me.youngjibbit.utils.render.ArrayListSurface themedSurface=new me.youngjibbit.utils.render.ArrayListSurface();
    private final me.youngjibbit.utils.render.TextEffects headingEffects=new me.youngjibbit.utils.render.TextEffects();
    private static final Category[] CATEGORIES = Category.values();
    private static final float SIDEBAR = 102, HEADER = 36, CONTENT_HEADER = 24, FOOTER = 20, MARGIN = 8, CARD_GAP = 6;
    private float x, y, width = 450, height = 400;
    private float grabX, grabY, resizeWidth, resizeHeight;
    private boolean positioned, dragging, resizing;
    private Category selectedCategory = Category.Combat;
    private final List<ClickModule> modules = new ArrayList<>();
    private final Map<Category, List<ClickModule>> moduleCache = new EnumMap<>(Category.class);
    private String search="";
    private boolean searchFocus;
    private Component focus,hoverTarget;
    private ClickModule binding;
    private long hoverStarted;
    public float scrollOffset;
    private float targetScrollOffset, maxScroll, selectionOffset;
    private long lastFrame = System.nanoTime();
    private ClipRect viewport = new ClipRect(0, 0, 0, 0);
    private final me.youngjibbit.gui.UiSpring[] chromeMotion=new me.youngjibbit.gui.UiSpring[CATEGORIES.length+2];
    private me.youngjibbit.gui.UiSpring searchSpring;
    private me.youngjibbit.gui.UiSpring themeSpring;
    private final PanelMotion panelMotion=new PanelMotion();
    private int visualChrome=-1;
    private Component visualControl;

    public ClickGui() {
        for(int i=0;i<chromeMotion.length;i++)chromeMotion[i]=new me.youngjibbit.gui.UiSpring();
        searchSpring=new me.youngjibbit.gui.UiSpring();
        themeSpring=new me.youngjibbit.gui.UiSpring();
        setupModules();
    }

    /** Legacy panel presentation. */
    protected boolean modernPresentation(){return false;}
    /** Creative 2.0 presentation: spatial, dynamic, expressive. */
    protected boolean creativePresentation(){return ClientSettings.creativeScreenStyle();}
    private boolean two(){return modernPresentation()||ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());}
    private boolean modern(){return two()&&!creativePresentation();}
    private boolean creative(){return two()&&creativePresentation();}
    private float sidebarWidth(){return two()?Math.max(92,Math.min(148,width*.22f)):SIDEBAR;}
    private float headerHeight(){return two()?46:HEADER;}
    private float detailHeaderHeight(){return two()?39:CONTENT_HEADER;}
    private float footerHeight(){return two()?24:FOOTER;}
    private float rowGap(){return two()?5:CARD_GAP;}

    /** Readable local chrome; the enclosing frame keeps ownership of the shared material. */
    private static Color chromeBacking(Theme theme) {
        int rgb=Theme.interpolateRGB(0xff18202c,theme.getBackground().getRGB()|0xff000000,.14f);
        int alpha=Math.round(218*Math.max(0,Math.min(1,UiEffects.options().surfaceOpacity)));
        return new Color((rgb&0xffffff)|(alpha<<24),true);
    }

    protected ClipRect categoryBounds(int index){return new ClipRect(x+5,y+headerHeight()+4+index*categoryStep(),sidebarWidth()-10,categoryStep()-4);}
    private ClipRect chromeBounds(int id){return id==0?searchBounds():id==1?themeBounds():categoryBounds(id-2);}
    private void resetMotion(){
        visualControl=null;visualChrome=-1;
        panelMotion.reset(selectedCategory.ordinal()*categoryStep());
        for(me.youngjibbit.gui.UiSpring state:chromeMotion)state.reset();
        searchSpring.reset();themeSpring.reset();
        for(List<ClickModule> rows:moduleCache.values())for(ClickModule row:rows){
            row.getPresentation().reset();
            row.resetPanelMotion();
            for(Component.Sub<?> value:row.presentationValues())value.getPresentation().reset();
        }
    }
    protected void advanceMotion(float dt,int mx,int my){
        boolean available=!dragging&&!resizing&&binding==null;
        for(int i=0;i<chromeMotion.length;i++)chromeMotion[i].stepForInput(dt,available&&chromeBounds(i).contains(mx,my),i==0&&searchFocus,i==visualChrome,available,me.youngjibbit.module.impl.client.ClientSettings.current());
        searchSpring.stepForInput(dt,searchFocus||chromeBounds(0).contains(mx,my),searchFocus,visualChrome==0,available,me.youngjibbit.module.impl.client.ClientSettings.current());
        themeSpring.stepForInput(dt,chromeBounds(1).contains(mx,my),false,visualChrome==1,available,me.youngjibbit.module.impl.client.ClientSettings.current());
        for(List<ClickModule> rows:moduleCache.values())for(ClickModule row:rows){
            boolean present=available&&modules.contains(row);
            boolean visible=present&&!viewport.intersect(row.presentationBounds()).isEmpty();
            row.advanceMotion(dt,mx,my,focus==row,visualControl==row,visible);
            for(Component.Sub<?> value:row.presentationValues()){
                boolean shown=present&&row.isOpen()&&row.inputVisible(value)&&value.getClip()!=null&&!value.getClip().intersect(value.presentationBounds()).isEmpty();
                value.advanceMotion(dt,mx,my,focus==value,visualControl==value,shown);
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        resetMotion();
        dragging = resizing = false;
        cancelInputs();
        releaseControls();
        fitWindow();
        setupModules();
        selectionOffset = selectedCategory.ordinal() * categoryStep();
        panelMotion.reset(selectionOffset);
        lastFrame = System.nanoTime();
    }

    private void fitWindow() {
        if(!positioned&&two()){
            width=Math.min(700,creative()?Math.max(680,NeutronVisualTokens.width(super.width)):NeutronVisualTokens.width(super.width));
            height=Math.min(404,Math.max(1,super.height*.82f));
        }
        float availableWidth = Math.max(1, super.width - MARGIN * 2);
        float availableHeight = Math.max(1, super.height - MARGIN * 2);
        width = Math.max(Math.min(two()?560:320, availableWidth), Math.min(width, availableWidth));
        height = Math.max(Math.min(two()?360:224, availableHeight), Math.min(height, availableHeight));
        if (!positioned) {
            x = (super.width - width) / 2;
            y = (super.height - height) / 2;
            positioned = true;
        }
        x = Math.max(MARGIN, Math.min(x, super.width - width - MARGIN));
        y = Math.max(MARGIN, Math.min(y, super.height - height - MARGIN));
    }

    private float categoryStep() {
        return two()?
                Math.max(14,Math.min(34,(height-headerHeight()-footerHeight()-16)/CATEGORIES.length)):
                Math.min(24,(height-headerHeight()-FOOTER-8)/CATEGORIES.length);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        long now=System.nanoTime();
        float delta = Math.max(0,Math.min(0.05f, (now - lastFrame) / 1.0e9f));
        lastFrame = now;
        if (dragging) {
            x = mouseX - grabX;
            y = mouseY - grabY;
        } else if (resizing) {
            width = resizeWidth + mouseX - grabX;
            height = resizeHeight + mouseY - grabY;
        }
        fitWindow();
        float step = categoryStep();
        ClientSettings motionSettings=ClientSettings.current();
        selectionOffset=PanelMotion.clamp(panelMotion.category(selectedCategory.ordinal()*step,delta,motionSettings),
                0,(CATEGORIES.length-1)*step);
        float entry=panelMotion.entrance(delta,motionSettings);
        layoutModules(delta);
        advanceMotion(delta,mouseX,mouseY);

        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        boolean modern=two()&&!creativePresentation();
        boolean creative=two()&&creativePresentation();
        // Only the Approach-2 paint changes; all hit/drag/resize rectangles stay fixed.
        themedSurface.panel(x,y,width,height,two()?NeutronVisualTokens.RADIUS_FRAME:me.youngjibbit.gui.LegacyUiStyle.FRAME_RADIUS,
                theme,true,true,!two(),two()?4:8,two()?.98f:.72f,
                two()?.22f:1,()->{
        // The legacy bloom helper restores raw GL attributes, bypassing Minecraft's state cache.
        // Synchronize both sides before drawing coplanar UI surfaces over its fullscreen quad.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);



        try (GuiClip windowClip = GuiClip.push(new ClipRect(x + 2, y + 2, width - 4, height - 4))) {
            if(two()){
                // Local chrome stays legible on bright glass without covering the open content area.
                Color chromeFill=chromeBacking(theme);
                UIUtils.drawDecorationRound(x+5,y+5,sidebarWidth()-10,headerHeight()-10,6,chromeFill);
                UIUtils.drawDecorationRound(viewport.x,y+headerHeight()+4,viewport.width,
                        detailHeaderHeight()-3,6,chromeFill);
                float footerY=y+height-footerHeight()-2;
                UIUtils.drawDecorationRound(x+5,footerY,sidebarWidth()-10,footerHeight()-4,6,chromeFill);
                UIUtils.drawDecorationRound(viewport.x,footerY,viewport.width,footerHeight()-4,6,chromeFill);
                UIUtils.drawDecorationRound(x+5,y+headerHeight()+3,sidebarWidth()-10,
                        height-headerHeight()-footerHeight()-7,8,new Color(17,23,33,182));
                UIUtils.drawDecorationRound(x+sidebarWidth(),y+headerHeight()+14,
                        1,Math.max(1,height-headerHeight()-29),.5f,
                        NeutronVisualTokens.HAIRLINE);
                MenuSurface.card(x+15,y+10,24,24,theme,false,false);
                UIUtils.drawDecorationRound(x+16,y+12,2,20,1,
                        NeutronVisualTokens.accent(theme,175));
                TTFFontRenderer logo=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28");
                logo.drawString("N",x+22,y+13,theme.getPrimaryGradientRGB());
                TTFFontRenderer brand=Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 20");
                brand.drawString(ClickControlStyle.fit(brand,"Neutron",sidebarWidth()-54),x+48,y+10,NeutronVisualTokens.INK);
                TTFFontRenderer variant=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Regular 16");
                variant.drawString(ClickControlStyle.fit(variant,creative?"Creative":"2.0",sidebarWidth()-54),x+48,y+27,
                        creative?NeutronVisualTokens.accent(theme,200).getRGB():NeutronVisualTokens.MUTED);
                if(sidebarWidth()>=175){
                    float pillX=x+sidebarWidth()-40;
                    UIUtils.drawRound(pillX,y+18,30,16,7,NeutronVisualTokens.CONTROL);
                    font().drawCenteredString("2.0",pillX+15,y+21,
                            theme.getPrimaryGradientRGB());
                }
                if(entry>.01f)
                    UIUtils.drawLiteralGradient(x+sidebarWidth()+14,y+headerHeight()+1,
                            Math.max(.5f,(width-sidebarWidth()-40)*entry),
                            1.5f,.75f,NeutronVisualTokens.accent(theme,175),
                            NeutronVisualTokens.secondary(theme,95));
                if(creative&&UiEffects.motion()){
                    // Small ambient accent follows the user's motion preference.
                    float orbit=UiEffects.time()*0.5f;
                    float ox=x+width-34+(float)Math.sin(orbit)*6f;
                    float oy=y+14+(float)Math.cos(orbit*0.7f)*4f;
                    UIUtils.drawRound(ox,oy,10,10,5,
                            NeutronVisualTokens.accent(theme,(int)(120+60*Math.abs((float)Math.sin(orbit*0.3f)))));
                }
            }else{
                UIUtils.drawRound(x+1,y+1,width-2,height-2,8,me.youngjibbit.gui.LegacyUiStyle.panel(theme));
                LiquidGlass.inset(x+5,y+headerHeight()+3,sidebarWidth()-10,
                        Math.max(1,height-headerHeight()-9),6,theme);
                LiquidGlass.inset(x+sidebarWidth()+3,y+headerHeight()+3,
                        Math.max(1,width-sidebarWidth()-9),Math.max(1,height-headerHeight()-9),6,theme);
                LiquidGlass.divider(x+10,y+headerHeight()-1,width-20,theme);
                UIUtils.drawDecorationGradientVertical(x+10,y+10,2,14,1,
                        LiquidGlass.accent(theme,225),NeutronVisualTokens.secondary(theme,175));
                drawClientName(Pulsar.INSTANCE.getName(),(int)(x+17),(int)(y+10),theme);
                Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(
                        "o",x+width-21,y+13,me.youngjibbit.gui.LegacyUiStyle.MUTED);
            }
            ClipRect searchBox=searchBounds();
            if(two()){
                float sLift = searchSpring.lift();
                float sGlow = searchSpring.glow();
                MenuSurface.card(searchBox.x,searchBox.y,searchBox.width,searchBox.height,theme,searchFocus,sGlow);
                UIUtils.drawRoundOutline(searchBox.x+.5f,searchBox.y+.5f,
                        Math.max(1,searchBox.width-1),Math.max(1,searchBox.height-1),
                        NeutronVisualTokens.RADIUS_CONTROL,.5f,new Color(0,0,0,1),
                        searchFocus?NeutronVisualTokens.accent(theme,115):
                                NeutronVisualTokens.HAIRLINE);
                if (sGlow > .02f) {
                    UIUtils.drawDecorationRound(searchBox.x-sLift*2f, searchBox.y-sLift*2f,
                            searchBox.width+sLift*4f, searchBox.height+sLift*4f,
                            NeutronVisualTokens.RADIUS_CONTROL+2f,
                            me.youngjibbit.utils.render.UiEffects.accent(theme.getPrimaryGradientRGB(), .08f*sGlow));
                }
                if(creative&&sGlow>.05f){
                    UIUtils.drawDecorationRound(searchBox.x-sLift*3f, searchBox.y-sLift*3f,
                            searchBox.width+sLift*6f, searchBox.height+sLift*6f,
                            NeutronVisualTokens.RADIUS_CONTROL+4f,
                            me.youngjibbit.utils.render.UiEffects.accent(theme.getSecondaryGradientRGB(), .04f*sGlow));
                }
            }else{
                float searchHover=chromeMotion[0].amount();
                MenuSurface.plate(searchBox.x,searchBox.y,searchBox.width,searchBox.height,theme,
                        searchFocus&&showsKeyboardFocus(),searchHover);
                me.youngjibbit.gui.LegacyUiStyle.icon("search",searchBox.x+7,
                        searchBox.y+(searchBox.height-10)/2f,10,me.youngjibbit.gui.LegacyUiStyle.MUTED);
            }
            GuiStyle.feedback(searchBox,chromeMotion[0]);
            float searchTextX=searchBox.x+(two()?8:20);
            try(GuiClip ignored=GuiClip.push(new ClipRect(searchBox.x+(two()?8:20),searchBox.y,searchBox.width-(two()?28:32),searchBox.height))){
                String shown=search.isEmpty()&&!searchFocus?width<380?"Search...":"Search modules...":search+(searchFocus?"_":"");
                while(shown.length()>1&&font().getWidth(shown)>searchBox.width-(two()?32:36))shown=shown.substring(1);
                font().drawString(shown,searchTextX,textY(font(),"Search",searchBox),searchFocus?0xffffffff:0xffa4a9b3);
            }
            if(!search.isEmpty()||searchFocus)font().drawString("x",searchBox.x+searchBox.width-14,textY(font(),"x",searchBox),0xffb8bec8);
            ClipRect themes = GuiStyle.buttonBounds(themeBounds(), themeSpring);
            float tLift = themeSpring.lift();
            float tGlow = themeSpring.glow();
            MenuSurface.card(themes.x,themes.y,themes.width,themes.height,theme,false,tGlow);
            if (tGlow > .02f) {
                UIUtils.drawDecorationRound(themes.x-tLift*2f, themes.y-tLift*2f,
                        themes.width+tLift*4f, themes.height+tLift*4f,
                        NeutronVisualTokens.RADIUS_CONTROL+2f,
                        me.youngjibbit.utils.render.UiEffects.accent(theme.getPrimaryGradientRGB(), .10f*tGlow));
            }
            if(creative&&tGlow>.05f){
                UIUtils.drawDecorationRound(themes.x-tLift*3f, themes.y-tLift*3f,
                        themes.width+tLift*6f, themes.height+tLift*6f,
                        NeutronVisualTokens.RADIUS_CONTROL+4f,
                        me.youngjibbit.utils.render.UiEffects.accent(theme.getSecondaryGradientRGB(), .05f*tGlow));
            }
            GuiStyle.feedback(themes,themeSpring);
            font().drawString("Library",themes.x+(themes.width-font().getWidth("Library"))/2,textY(font(),"Library",themes),GuiStyle.ink(theme.isCustom()?theme.getTextColor().getRGB():0xffd4d9e3,chromeMotion[1]));
            float selectedY = y + headerHeight() + 4 + selectionOffset;
            MenuSurface.card(x+8,selectedY,sidebarWidth()-16,step-4,theme,true,false);
            String[] icons = {"c", "f", "d", "d", "e", "t", "f", "a"};
            for (int i = 0; i < CATEGORIES.length; i++) {
                float rowY = y + headerHeight() + 4 + i * step;
                ClipRect category = categoryBounds(i);
                rowY += category.y - chromeBounds(i+2).y;
                GuiStyle.feedback(category,chromeMotion[i+2]);
                // Elastischer Hover: leichter Lift, Scale und Glow-Follow über die UiSpring-Menge.
                float hover = chromeMotion[i+2].amount();
                float lift = chromeMotion[i+2].lift();
                float scale = chromeMotion[i+2].scale();
                float glow = chromeMotion[i+2].glow();
                if (two()) {
                    if(i!=selectedCategory.ordinal()&&hover>.02f)
                        MenuSurface.card(category.x+3,category.y,category.width-6,category.height,theme,false,hover);
                } else if(i!=selectedCategory.ordinal()&&hover>.02f){
                    float wash=LiquidGlass.radius(5,sidebarWidth()-16,step-4);
                    UIUtils.drawDecorationRound(x+8,rowY,sidebarWidth()-16,step-4,wash,
                            LiquidGlass.fill(theme,Math.round(34+58*hover)));
                    if(hover>.5f)LiquidGlass.highlight(x+8,rowY,sidebarWidth()-16,step-4,wash);
                }
                String font = i == 2 || i == 4 || i == 6 ? "icon1 21" : "icon 21";
                float textX = x + (two() ? 17 : 14);
                float textY = rowY + (step-14)/2;
                Pulsar.INSTANCE.getFontManager().getFont(font).drawString(
                        icons[i], textX, textY,
                        two() ? i==selectedCategory.ordinal()?NeutronVisualTokens.INK:
                                NeutronVisualTokens.MUTED :
                                i==selectedCategory.ordinal()?Color.WHITE.getRGB():0xffc4cad5);
                Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString(
                        ClickControlStyle.fit(Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18"),CATEGORIES[i].name(),sidebarWidth()-(two()?44:38)), x + (two() ? 35 : 30), textY,
                        two() ? i==selectedCategory.ordinal()?NeutronVisualTokens.INK:
                                NeutronVisualTokens.MUTED :
                                i==selectedCategory.ordinal()?Color.WHITE.getRGB():0xffc4cad5);
            }
            String title=search.trim().isEmpty()?selectedCategory.name():"Search results";
            String count=modules.size()+(!search.trim().isEmpty()?(modules.size()==1?" result":" results"):(modules.size()==1?" module":" modules"));
            TTFFontRenderer heading=Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18");
            heading.drawString(two()?ClickControlStyle.fit(heading,title,
                            Math.max(0,viewport.width-font().getWidth(count)-26)):title,
                    viewport.x+(two()?8:1),y+headerHeight()+8,
                    two()?NeutronVisualTokens.INK:0xffedf0f7);
            font().drawString(count,viewport.x+viewport.width-font().getWidth(count)-(two()?8:2),
                    y+headerHeight()+8,two()?NeutronVisualTokens.MUTED:0xffaab3c3);
            if(two()){
                TTFFontRenderer small=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Regular 16");
                small.drawString(ClickControlStyle.fit(small,NeutronVisualTokens.categoryDescription(selectedCategory),viewport.width-16),
                        viewport.x+8,y+headerHeight()+24,NeutronVisualTokens.MUTED);
                float footerTop=y+height-footerHeight()-5;
                UIUtils.drawDecorationRound(x+sidebarWidth()+15,footerTop,
                        Math.max(1,width-sidebarWidth()-30),1,.5f,NeutronVisualTokens.HAIRLINE);
                UIUtils.drawDecorationRound(x+15,footerTop,
                        Math.max(1,sidebarWidth()-30),1,.5f,NeutronVisualTokens.HAIRLINE);
                small.drawString(creative?"Creative":"Neutron 2.0",x+18,footerTop+8,NeutronVisualTokens.MUTED);
            }
            if(!two()){
                String shortcuts=width>=400?"/ Search  ·  Tab Navigate  ·  K Bind":
                        "/ Search  ·  Tab  ·  K Bind";
                font().drawString(shortcuts,viewport.x,y+height-FOOTER+5,0xffaab3c3);
            }else{
                TTFFontRenderer hints=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Regular 16");
                hints.drawString(ClickControlStyle.fit(hints,"Tab: focus   ·   /: search   ·   K: bind",
                                viewport.width-16),viewport.x+8,y+height-footerHeight()+2,NeutronVisualTokens.MUTED);
            }
            if(maxScroll>0){
                float thumb=Math.max(16,viewport.height*viewport.height/(viewport.height+maxScroll));
                float thumbY=viewport.y+(-scrollOffset/maxScroll)*(viewport.height-thumb);
                UIUtils.drawRound(x+width-7,viewport.y,2,viewport.height,1,
                        two()?NeutronVisualTokens.TRACK:new Color(42,46,56));
                UIUtils.drawRound(x+width-7,thumbY,2,thumb,1,
                        two()?(creative?NeutronVisualTokens.accent(theme,200):NeutronVisualTokens.secondary(theme,195)):
                                theme.getSecondaryGradientColor());
            }
            try (GuiClip contentClip = GuiClip.push(viewport)) {
                if(modules.isEmpty()){
                    font().drawString("No matching modules",viewport.x+12,viewport.y+12,0xffe6e8ed);
                    font().drawString("Clear search with Esc or x",viewport.x+12,viewport.y+26,0xff969da8);
                }
                for (ClickModule module : modules) {
                    if (!viewport.intersect(new ClipRect(module.getX(), module.getY(), module.getWidth(), module.getHeight())).isEmpty()) {
                        module.drawScreen(dragging || resizing ? -1 : mouseX, dragging || resizing ? -1 : mouseY);
                    }
                }
            }
            drawTooltip(mouseX,mouseY);
            if(binding!=null)drawBinding();
        }
        });
    }

    private boolean hoverSelected(Category c){
        return c==selectedCategory;
    }

    private void layoutModules(float delta) {
        viewport = new ClipRect(x + sidebarWidth() + (two()?16:8),
                y + headerHeight() + detailHeaderHeight() + 4,
                width - sidebarWidth() - (two()?30:20),
                height - headerHeight() - detailHeaderHeight() - footerHeight() - 8);
        int columns=creative()&&viewport.width>=490?2:1;
        float contentHeight=0;
        for(ClickModule module:modules)module.updatePanelAnimation(delta);
        for(int start=0;start<modules.size();start+=columns){
            float rowHeight=0;
            for(int col=0;col<columns&&start+col<modules.size();col++)
                rowHeight=Math.max(rowHeight,modules.get(start+col).getHeight());
            contentHeight+=rowHeight+rowGap();
        }
        maxScroll = Math.max(0, contentHeight - (modules.isEmpty()?0:rowGap()) - viewport.height);
        targetScrollOffset = Math.max(-maxScroll, Math.min(0, targetScrollOffset));
        scrollOffset += (targetScrollOffset - scrollOffset) * me.youngjibbit.utils.render.UiEffects.response(delta,20);
        scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));
        float moduleY=viewport.y+scrollOffset;
        float columnWidth=(viewport.width-(columns-1)*rowGap())/columns;
        for(int start=0;start<modules.size();start+=columns){
            float rowHeight=0;
            for(int col=0;col<columns&&start+col<modules.size();col++){
                ClickModule module=modules.get(start+col);
                module.layout(viewport.x+col*(columnWidth+rowGap())+1,moduleY+1,columnWidth-2,viewport);
                rowHeight=Math.max(rowHeight,module.getHeight());
            }
            moduleY+=rowHeight+rowGap();
        }
        if(focus!=null&&!focusables().contains(focus)){
            Component replacement=null;
            for(ClickModule row:modules)if(row.ownsComponent(focus)){replacement=row.focusParent(focus);break;}
            setFocus(replacement);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * super.width / mc.displayWidth;
        int mouseY = super.height - Mouse.getEventY() * super.height / mc.displayHeight - 1;
        if (wheel != 0 && !dragging && !resizing && viewport.contains(mouseX, mouseY)) {
            releaseControls();if(focus instanceof ClickSlider)((ClickSlider)focus).cancelInput();
            targetScrollOffset = Math.max(-maxScroll, Math.min(0, targetScrollOffset + (wheel > 0 ? 28 : -28)));
        }
    }

    private List<ClickModule> categoryModules(Category category){
        return moduleCache.computeIfAbsent(category,key->{
            List<ClickModule> result=new ArrayList<>();
            for(Module module:Pulsar.INSTANCE.getMm().getModules(key))
                result.add(ClickModule.forPanel(module,width-sidebarWidth()-12,two()));
            return result;
        });
    }
    private void setupModules(){
        modules.clear();String query=search.trim().toLowerCase(Locale.ROOT);
        if(query.isEmpty())modules.addAll(categoryModules(selectedCategory));
        else for(Category category:CATEGORIES)for(ClickModule row:categoryModules(category)){
            Module module=row.getModule();if((module.getName()+" "+module.getDescription()).toLowerCase(Locale.ROOT).contains(query))modules.add(row);
        }
    }
    private void updateSearch(){releaseControls();targetScrollOffset=scrollOffset=0;setupModules();layoutModules(0);}

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        visualControl=null;visualChrome=-1;
        if(button==0&&binding==null&&!dragging&&!resizing){for(int i=0;i<chromeMotion.length;i++)if(chromeBounds(i).contains(mouseX,mouseY)){visualChrome=i;break;}if(visualChrome<0)visualControl=componentAt(mouseX,mouseY);}
        if(binding!=null)return;
        if(button==0&&themeBounds().contains(mouseX,mouseY)){openLibrary();return;}        ClipRect searchBox=searchBounds();
        if(button==0&&searchBox.contains(mouseX,mouseY)){
            setFocus(null);searchFocus=true;
            if(mouseX>=searchBox.x+searchBox.width-17){search="";updateSearch();}return;
        }
        searchFocus=false;
        if (button == 0 && new ClipRect(x + width - 26, y + 2, 24, headerHeight() - 2).contains(mouseX, mouseY)) {
            releaseControls();
            setFocus(null);resizing = true;
            grabX = mouseX;
            grabY = mouseY;
            resizeWidth = width;
            resizeHeight = height;
            return;
        }
        if (button == 0 && new ClipRect(x, y, width, headerHeight()).contains(mouseX, mouseY)) {
            releaseControls();
            setFocus(null);dragging = true;
            grabX = mouseX - x;
            grabY = mouseY - y;
            return;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (button == 0 && categoryBounds(i).contains(mouseX, mouseY)) {
                releaseControls();
                cancelInputs();search="";
                selectedCategory = CATEGORIES[i];
                targetScrollOffset = scrollOffset = maxScroll = 0;
                setupModules();
                layoutModules(0);
                return;
            }
        }
        if (!dragging && !resizing && viewport.contains(mouseX, mouseY)) {
            Component target=componentAt(mouseX,mouseY);setFocus(target);
            if(button==2&&target instanceof ClickModule){binding=(ClickModule)target;releaseControls();return;}
            for (ClickModule module : modules) module.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        visualControl=null;visualChrome=-1;
        dragging = resizing = false;
        releaseControls();
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void releaseControls() {
        for (List<ClickModule> category : moduleCache.values()) {
            for (ClickModule module : category) module.mouseReleased();
        }
    }

    @Override protected void mouseClickMove(int mouseX,int mouseY,int button,long elapsed) {
        if(button==0 && !dragging && !resizing && binding==null && visualControl!=null)
            visualControl.mouseDragged(mouseX,mouseY);
    }

    @Override
    public void onGuiClosed() {
        resetMotion();
        headingEffects.close();themedSurface.close();cancelInputs();hoverTarget=null;
        dragging = resizing = false;
        releaseControls();
        super.onGuiClosed();
    }

    @Override protected void keyTyped(char typedChar,int keyCode)throws IOException{
        visualControl=null;visualChrome=-1;
        if(binding!=null){
            if(keyCode==Keyboard.KEY_ESCAPE){binding=null;return;}
            if(keyCode==Keyboard.KEY_NONE)return;
            binding.getModule().setKey(keyCode==Keyboard.KEY_DELETE||keyCode==Keyboard.KEY_BACK?0:keyCode);binding=null;return;
        }
        if(focus instanceof ClickSlider&&((ClickSlider)focus).isEditing()){
            if(keyCode==Keyboard.KEY_TAB){((ClickSlider)focus).cancelInput();navigate(shiftDown()?-1:1);}else focus.keyTyped(typedChar,keyCode);return;
        }
        if(keyCode==Keyboard.KEY_T&&ctrlDown()){openThemeEditor();return;}
        if(keyCode==Keyboard.KEY_O&&ctrlDown()){mc.displayGuiScreen(new ClickLibraryScreen(this,false));return;}
        if((keyCode==Keyboard.KEY_F&&ctrlDown())||(typedChar=='/'&&!searchFocus)){setFocus(null);searchFocus=true;return;}
        if(keyCode==Keyboard.KEY_TAB){navigate(shiftDown()?-1:1);return;}
        if(searchFocus){
            if(keyCode==Keyboard.KEY_ESCAPE){if(!search.isEmpty()){search="";updateSearch();}else searchFocus=false;return;}
            if(keyCode==Keyboard.KEY_RETURN||keyCode==Keyboard.KEY_DOWN){if(!modules.isEmpty()){setFocus(modules.get(0));reveal(focus);}return;}
            if(keyCode==Keyboard.KEY_BACK){if(!search.isEmpty()){search=search.substring(0,search.length()-1);updateSearch();}return;}
            if(keyCode==Keyboard.KEY_A&&ctrlDown()){search="";updateSearch();return;}
            if(typedChar>=32&&typedChar!=127&&search.length()<96){search+=typedChar;updateSearch();}return;
        }
        if(keyCode==Keyboard.KEY_PRIOR||keyCode==Keyboard.KEY_NEXT){
            int index=(selectedCategory.ordinal()+(keyCode==Keyboard.KEY_PRIOR?CATEGORIES.length-1:1))%CATEGORIES.length;
            cancelInputs();search="";selectedCategory=CATEGORIES[index];targetScrollOffset=scrollOffset=0;setupModules();layoutModules(0);return;
        }
        if(keyCode==Keyboard.KEY_ESCAPE&&focus instanceof ClickCombo&&((ClickCombo)focus).isOpen()){((ClickCombo)focus).close();return;}
        if(keyCode==Keyboard.KEY_ESCAPE&&focus instanceof me.youngjibbit.clickgui.component.impl.ClickSubcategory
                &&((me.youngjibbit.clickgui.component.impl.ClickSubcategory)focus).isOpen()){
            ((me.youngjibbit.clickgui.component.impl.ClickSubcategory)focus).setOpen(false);layoutModules(0);return;
        }
        if(keyCode==Keyboard.KEY_ESCAPE){
            for(ClickModule row:modules)row.cancelInputs();
            Module owner=Pulsar.INSTANCE.getMm().getModule(ClickGUI.class);
            if(owner!=null&&owner.isToggled())owner.setToggled(false);
            else mc.displayGuiScreen(null);
            return;
        }
        if(keyCode==Keyboard.KEY_UP||keyCode==Keyboard.KEY_DOWN){navigate(keyCode==Keyboard.KEY_UP?-1:1);return;}
        if(focus instanceof ClickModule&&keyCode==Keyboard.KEY_K){binding=(ClickModule)focus;return;}
        if(focus!=null){focus.keyTyped(typedChar,keyCode);layoutModules(0);reveal(focus);}
    }
    protected boolean shiftDown(){return Keyboard.isCreated()&&isShiftKeyDown();}
    protected boolean ctrlDown(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    private TTFFontRenderer font(){return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18");}
    protected ClipRect searchBounds(){return two()?new ClipRect(x+sidebarWidth()+10,y+9,width-sidebarWidth()-102,28):new ClipRect(x+sidebarWidth()+8,y+6,width-sidebarWidth()-104,24);}
    protected ClipRect themeBounds(){return two()?new ClipRect(x+width-78,y+9,52,28):new ClipRect(x+width-88,y+6,56,24);}
    private static float textY(TTFFontRenderer font,String text,ClipRect box){return box.y+(box.height-font.getHeight(text))/2;}
    protected ClickLibraryScreen createThemeLibrary(){return new ClickLibraryScreen(this,true);}
    protected void openThemeEditor(){mc.displayGuiScreen(createThemeLibrary());}
    protected void openLibrary(){mc.displayGuiScreen(new SideGuiScreen(this));}
    private List<Component> focusables(){List<Component> result=new ArrayList<>();for(ClickModule row:modules){result.add(row);result.addAll(row.focusableValues());}return result;}
    private void setFocus(Component next){if(focus!=next&&focus instanceof ClickSlider)((ClickSlider)focus).cancelInput();focus=next;searchFocus=false;}
    private void navigate(int direction){
        List<Component> list=focusables();int index=searchFocus||focus==null?0:list.indexOf(focus)+1;
        int next=(index+direction+list.size()+1)%(list.size()+1);
        if(next==0){setFocus(null);searchFocus=true;}else{setFocus(list.get(next-1));reveal(focus);}
    }
    private void reveal(Component component){
        if(component==null)return;
        float top=component.getY(),bottom=top+24;
        float offset=top<viewport.y?viewport.y-top:bottom>viewport.y+viewport.height?viewport.y+viewport.height-bottom:0;
        targetScrollOffset=Math.max(-maxScroll,Math.min(0,targetScrollOffset+offset));scrollOffset=targetScrollOffset;layoutModules(0);
    }
    private Component componentAt(int mx,int my){
        if(!viewport.contains(mx,my))return null;
        for(ClickModule row:modules){
            if(row.isMouseOver(mx,my))return row;
            for(Component.Sub<?> value:row.focusableValues())if(value.getClip().contains(mx,my)&&new ClipRect(value.getX(),value.getY(),value.getWidth(),value.getHeight()).contains(mx,my))return value;
        }return null;
    }
    private void cancelInputs(){for(List<ClickModule> category:moduleCache.values())for(ClickModule row:category)row.cancelInputs();focus=null;binding=null;searchFocus=false;}
    private void drawBinding(){
        float w=Math.min(270,width-16),h=94,bx=x+(width-w)/2,by=y+(height-h)/2;
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        try(GuiClip ignored=GuiClip.push(new ClipRect(bx,by,w,h))){
            UIUtils.drawDecorationRound(bx,by,w,h,5,LiquidGlass.fill(theme,150));
            LiquidGlass.inset(bx,by,w,h,5,theme);
            font().drawString("Bind "+binding.getModule().getName(),bx+10,by+9,-1);
            font().drawString("Press a key / Esc: cancel",bx+10,by+27,0xffb5bac4);
            font().drawString("Delete / Backspace: remove",bx+10,by+42,0xffb5bac4);
            int key=binding.getModule().getKey(),count=0;for(Module m:Pulsar.INSTANCE.getMm().getAllModules())if(key!=0&&m!=binding.getModule()&&m.getKey()==key)count++;
            font().drawString("Current: "+(key==0?"None":Keyboard.getKeyName(key)),bx+10,by+60,0xffe3e6ed);
            if(count>0)font().drawString("Shared with "+count+" other module(s)",bx+10,by+76,0xffffb975);
        }
    }
    private void drawTooltip(int mx,int my){
        Component target=dragging||resizing||binding!=null||(focus instanceof ClickSlider&&((ClickSlider)focus).isEditing())?null:componentAt(mx,my);
        if(target!=hoverTarget){hoverTarget=target;hoverStarted=System.nanoTime();return;}
        if(target==null||System.nanoTime()-hoverStarted<450_000_000L)return;
        String text;
        if(target instanceof ClickModule){Module m=((ClickModule)target).getModule();int count=0;for(Module other:Pulsar.INSTANCE.getMm().getAllModules())if(other!=m&&m.getKey()!=0&&other.getKey()==m.getKey())count++;
            text=m.getDescription()+"  |  Space: toggle / Right: settings / K or middle click: bind"+(count>0?"  |  Shared key: "+count+" other module(s)":"");
        }else if(target instanceof me.youngjibbit.clickgui.component.impl.ClickSubcategory)
            text=((me.youngjibbit.clickgui.component.impl.ClickSubcategory)target).getValue().description()+" | Right click: expand / Left, Right: collapse, expand";
        else if(target instanceof ClickSlider)text="Arrows: adjust / Enter or right click: exact number / Esc: cancel";
        else text="Tab: focus / Space or Enter: activate / Arrows: choose";
        float w=Math.min(250,width-16);List<String> lines=new ArrayList<>();String line="";
        for(String word:text.split(" ")){String next=line.isEmpty()?word:line+" "+word;if(font().getWidth(next)>w-16&&!line.isEmpty()){lines.add(line);line=word;}else line=next;}if(!line.isEmpty())lines.add(line);
        float h=12+lines.size()*11,tx=Math.max(x+5,Math.min(mx+10,x+width-w-5)),ty=Math.max(y+5,Math.min(my+16,y+height-h-5));
        try(GuiClip ignored=GuiClip.push(new ClipRect(tx,ty,w,h))){LiquidGlass.inset(tx,ty,w,h,4,Theme.resolve(Theme.getDefaultTheme()));for(int i=0;i<lines.size();i++)font().drawString(lines.get(i),tx+7,ty+6+i*11,0xffd4d8e0);}
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    public void drawClientName(String text, int x, int y, Theme theme) {
        ClickGUI settings=(ClickGUI)Pulsar.INSTANCE.getMm().getModule(ClickGUI.class);
        boolean glow=settings!=null&&settings.getBooleanSetting("Heading Glow").getValue();
        headingEffects.draw(Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28"),text,x,y,
                theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),theme.isCustom()&&theme.isVertical()?2:1,theme.isCustom()&&theme.isAnimated(),theme.getAnimationSpeed(),glow,theme.isCustom()?theme.getGlowStrength():.4f,theme.isCustom()?theme.getGlowRadius():3,false);
    }

    public float getScrollOffset() { return scrollOffset; }
}

