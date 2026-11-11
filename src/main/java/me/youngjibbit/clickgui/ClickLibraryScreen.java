package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.library.ConfigLibrary;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.config.*;
import me.youngjibbit.gui.UiSpring;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.List;

/** The ClickGUI's local library. Browsing never changes the active config or theme. */
public class ClickLibraryScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ConfigLibrary configs;
    private final ThemeCatalog catalog;
    private final ArrayListSurface surface = new ArrayListSurface();
    private final UiSpring[] motion = new UiSpring[9];
    private final Map<String,UiSpring> cardMotion = new HashMap<>();
    private List<String> cachedItems = Collections.emptyList();
    private String cachedQuery;
    private boolean cachedThemes;
    private boolean themes, replace, repeatBefore, ownsKeyboardRepeat, renaming;
    private String query = "", name = "", selectedTheme = "", message = "", renameBuffer = "";
    private float x, y, w, h, scroll, targetScroll;
    private int focus = -1, pressed = -99;
    private long lastFrame = System.nanoTime();

    public ClickLibraryScreen(GuiScreen parent, boolean themes) {
        this(parent, themes, Pulsar.INSTANCE.getConfigManager(), ThemeCatalog.global());
    }
    public ClickLibraryScreen(GuiScreen parent, boolean themes, ConfigManager manager, ThemeCatalog catalog) {
        this.parent = parent; this.themes = themes; this.configs = new ConfigLibrary(manager); this.catalog = catalog;
        selectedTheme = catalog.selectedReference();
        for (int i = 0; i < motion.length; i++) motion[i] = new UiSpring();
    }
    /** Legacy presentation. */
    protected boolean modernPresentation(){return false;}
    /** Creative 2.0 presentation: spatial, dynamic, expressive. */
    protected boolean creativePresentation(){return ClientSettings.creativeScreenStyle();}
    private boolean two(){return modernPresentation()||ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());}
    private boolean modern(){return two()&&!creativePresentation();}
    private boolean creative(){return two()&&creativePresentation();}
    @Override public void initGui() {
        // A viewport resize keeps the active rename draft. Leaving this screen
        // clears it in onGuiClosed, including navigation into a child editor.
        configs.refresh(); invalidateItems(); layout(); if(catalog.find(selectedTheme)==null)selectedTheme=catalog.selectedReference(); lastFrame = System.nanoTime(); pressed = -99;
        if (Keyboard.isCreated()) {
            if(!ownsKeyboardRepeat){repeatBefore=Keyboard.areRepeatEventsEnabled();ownsKeyboardRepeat=true;}
            Keyboard.enableRepeatEvents(true);
        }
    }
    private TTFFontRenderer font() { return ClickControlStyle.font(); }
    private void text(String text, float x, float y, float width, int ink) {
        font().drawString(ClickControlStyle.fit(font(), text, width), x, y, ink);
    }
    private void layout() {
        w = Math.min(two()?780:720, Math.max(1,width-20));
        h = Math.min(two()||!themes?460:420, Math.max(1,height-20));
        x = (width - w) / 2; y = (height - h) / 2;
        float max = Math.max(0, rowsHeight() - listBounds().height);
        targetScroll = Math.max(0, Math.min(max, targetScroll)); scroll = Math.max(0, Math.min(max, scroll));
    }
    private boolean legacyConfigs() { return !themes && !two(); }
    private boolean compactConfigs() { return h < 300; }
    private boolean inlineSearch() { return w >= 520; }
    private boolean splitConfigs() { return !themes && w >= 540 && h >= 300; }
    private float configListWidth() { return splitConfigs() ? (w - 40) * (creative()?.5f:.58f) : w - 24; }
    private float detailsX() { return x + 28 + configListWidth(); }
    private float detailsWidth() { return w - 40 - configListWidth(); }
    protected ClipRect listBounds() {
        if(legacyConfigs()) {
            float top=compactConfigs()?94:inlineSearch()?88:118;
            float bottom=nameBounds().y-y-(compactConfigs()?6:14);
            return new ClipRect(x+16,y+top,w-32,Math.max(24,bottom-top));
        }
        if (!themes) return new ClipRect(x + 12, y + (splitConfigs() ? 108 : 82), configListWidth(), Math.max(28, h - (splitConfigs() ? 140 : 170)));
        return new ClipRect(x + 12, y + 90, w - 24, Math.max(8, h - 170));
    }
    protected ClipRect searchBounds() {
        if(legacyConfigs())return inlineSearch()?new ClipRect(x+218,y+43,w-234,25)
                :new ClipRect(x+16,y+(compactConfigs()?62:76),w-32,25);
        if (!themes) return new ClipRect(x + 12, y + (splitConfigs() ? 82 : 56), configListWidth() - 70, 20);
        return new ClipRect(x + 12, y + 62, w - 24, 22);
    }
    protected ClipRect nameBounds() {
        if(legacyConfigs())return new ClipRect(x+16,y+h-(compactConfigs()?74:84),w-32,compactConfigs()?22:25);
        if (!themes) return splitConfigs() ? new ClipRect(detailsX(), y + 208, detailsWidth(), 22)
                : new ClipRect(x + 12, y + h - 44, w - 102, 22);
        return new ClipRect(x + 12, y + h - 78, w - 24, 22);
    }
    protected ClipRect actionBounds(int id) {
        if(legacyConfigs()) {
            if(id==6||id==7)return new ClipRect(x+16+(id-6)*92,y+(compactConfigs()?34:43),86,compactConfigs()?22:25);
            if(id==8)return new ClipRect(x+w-66,y+10,50,24);
            int[] order={0,1,4,2,3};int index=0;
            for(int i=0;i<order.length;i++)if(order[i]==id)index=i;
            float available=Math.min(548,w-32),gap=5,bw=(available-gap*4)/5;
            return new ClipRect(x+(w-available)/2+index*(bw+gap),y+h-(compactConfigs()?45:48),bw,26);
        }
        if (id == 6 || id == 7) return new ClipRect(x + 12 + (id - 6) * 88, y + (themes ? 34 : 30), 82, themes ? 22 : 20);
        if (id == 8) return new ClipRect(x + w - 50, y + 10, 38, 22);
        if (!themes) {
            if (id == 3) { ClipRect search = searchBounds(); return new ClipRect(search.x + search.width + 6, search.y, 64, 20); }
            if (splitConfigs()) {
                if (id == 4) return new ClipRect(detailsX(), y + 172, detailsWidth(), 26);
                return new ClipRect(detailsX(), y + (id == 0 ? 236 : id == 1 ? 112 : 144), detailsWidth(), 26);
            }
            if (id == 0) return new ClipRect(x + w - 84, y + h - 44, 72, 22);
            float third = Math.max(1,(w - 36) / 3);
            int column = id == 4 ? 2 : id - 1;
            return new ClipRect(x + 12 + column * (third + 6), y + h - 86, third, 22);
        }
        int tb = themes ? 5 : 4;
        float bw = Math.max(1,(w - 24 - 6 * (tb - 1)) / tb);
        return new ClipRect(x + 12 + id * (bw + 6), y + h - 50, bw, 24);
    }
    private int columns() {
        if(legacyConfigs())return Math.max(1,Math.min(4,(int)((listBounds().width+8)/168)));
        return themes ? Math.max(1, Math.min(creative()?4:3, (int)((w - 18) / 150))) : 1;
    }
    private boolean compactList() { return h < 300; }
    private float rowHeight() {
        if(legacyConfigs())return Math.min(compactConfigs()?46:60,Math.max(30,listBounds().height))+6;
        return themes ? compactList() ? 36 : creative()?66:52 : splitConfigs() ? creative()?40:34 : 28;
    }
    private float rowsHeight() { return ((items().size() + columns() - 1) / columns()) * rowHeight(); }
    protected ClipRect itemBounds(int index) {
        if(legacyConfigs()) {
            ClipRect list=listBounds();int cols=columns();float cell=(list.width-8*(cols-1))/cols;
            return new ClipRect(list.x+index%cols*(cell+8),list.y+index/cols*rowHeight()-scroll,cell,rowHeight()-6);
        }
        ClipRect list = listBounds(); float cell = list.width / columns();
        return new ClipRect(list.x + index % columns() * cell, list.y + index / columns() * rowHeight() - scroll,
                cell - (themes ? 6 : 0), rowHeight() - (themes ? 5 : 2));
    }
    private List<String> items() {
        if(query.equals(cachedQuery)&&themes==cachedThemes)return cachedItems;
        List<String> names = new ArrayList<>(); String q = query.toLowerCase(Locale.ROOT);
        if (themes) {
            for (Theme theme : catalog.all()) if (theme.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) names.add(theme.name());
        } else for (LocalConfig config : configs.entries()) if (config.getName().toLowerCase(Locale.ROOT).contains(q)) names.add(config.getName());
        cachedItems=Collections.unmodifiableList(names);cachedQuery=query;cachedThemes=themes;
        cardMotion.keySet().retainAll(names);
        return cachedItems;
    }
    private void invalidateItems(){cachedQuery=null;}
    private String selected() { return themes ? selectedTheme : configs.selected(); }
    private String visibleConfigSelection() {
        String selected = configs.selected();
        if (selected.isEmpty() || query.isEmpty()) return selected;
        return selected.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)) ? selected : "";
    }
    private boolean enabled(int id) {
        if (id == 6 || id == 7 || id == 8 || id == 3) return true;
        if (themes) {
            Theme theme = catalog.find(selectedTheme);
            return theme != null && (id == 0 || (!catalog.isReadOnly() && (id != 4 || theme.isCustom())));
        }
        return configs.available() && (id == 0 || !visibleConfigSelection().isEmpty());
    }
    private String label(int id) {
        if (id == 6) return "Configs"; if (id == 7) return "Themes"; if (id == 8) return "Done";
        if (!themes) return new String[]{"Save as", "Load config", "Delete", "Refresh", "Rename"}[id];
        return new String[]{"Use theme", "Edit", "Duplicate", "Effects", "Rename"}[id];
    }
    @Override public void drawScreen(int mx, int my, float partialTicks) {
        layout(); long now = System.nanoTime(); float dt = Math.min(.05f, Math.max(0, (now - lastFrame) / 1e9f)); lastFrame = now;
        scroll += (targetScroll - scroll) * UiEffects.response(dt, 22);
        if(legacyConfigs()){drawLegacyConfigs(mx,my,dt);return;}
        Theme theme=Theme.getDefaultTheme();
        int ink=two()?NeutronVisualTokens.ink(theme):LegacyUiStyle.ink(theme);
        boolean modern=two()&&!creativePresentation();
        boolean creative=two()&&creativePresentation();
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            if(mc.theWorld==null)me.youngjibbit.gui.MainMenu.drawBackdrop(theme);
            surface.panel(x,y,w,h,two()?14:6,theme,true,true,!two(),
                    two()?4:8,two()?.98f:.92f,two()?.2f:.8f,()->{
                try(GuiClip clip=GuiClip.push(new ClipRect(x+2,y+2,w-4,h-4))){
                    if(two()){
                        // The outer panel retains the chosen material; readable backing is local to each control.
                        UIUtils.drawDecorationRound(x+17,y+31,
                                Math.max(1,w-34),1,.5f,NeutronVisualTokens.HAIRLINE);
                        UIUtils.drawDecorationRound(x+12,y+11,2,18,1,
                                NeutronVisualTokens.accent(theme,200));
                        if(creative&&UiEffects.motion()){
                            // Ambient accent follows the user's motion preference.
                            float spark=UiEffects.time()*1.5f;
                            float sx=x+w-28+(float)Math.sin(spark)*8f;
                            float sy=y+15+(float)Math.cos(spark*.6f)*5f;
                            UIUtils.drawRound(sx,sy,6,6,3,NeutronVisualTokens.accent(theme,(int)(150+80*Math.abs((float)Math.sin(spark*.3f)))));
                        }
                    }
                    TTFFontRenderer brand=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28");
                    brand.drawString(two()?"Library":"Neutron",x+(two()?21:12),y+12,
                            two()?NeutronVisualTokens.INK:ink);
                    TTFFontRenderer sub=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 16");
                    sub.drawString(ClickControlStyle.fit(sub,two()?(themes?"Theme palettes":"Saved configurations"):
                            themes?"Configs & themes":"Local configs",w-170),
                            x+(two()?115:107),y+17,
                            two()?(creative?NeutronVisualTokens.accent(theme,180).getRGB():NeutronVisualTokens.MUTED):ink);
                    if (!themes) configSections(ink);
                    for (int id : new int[]{0,1,2,3,4,6,7,8}) {
                        ClipRect r = actionBounds(id); boolean active = enabled(id);
                        motion[id].stepForInput(dt, r.contains(mx,my), focus == id, pressed == id, active, ClientSettings.current());
                        r = GuiStyle.buttonBounds(r, motion[id]);
                        if(two()){
                            MenuSurface.card(r.x,r.y,r.width,r.height,theme,active&&(id==6&&!themes||id==7&&themes),motion[id].amount());
                            if(active&&(id==1||id==6||id==7))
                                UIUtils.drawDecorationRound(r.x+3,r.y+r.height-3,
                                        Math.max(1,r.width-6),1,.5f,
                                        NeutronVisualTokens.accent(theme,152));
                            if(creative&&active&&motion[id].glow()>.03f){
                                UIUtils.drawDecorationRound(r.x-motion[id].lift()*2f, r.y-motion[id].lift()*2f,
                                        r.width+motion[id].lift()*4f, r.height+motion[id].lift()*4f,
                                        9, me.youngjibbit.utils.render.UiEffects.accent(theme.getSecondaryGradientRGB(), .05f*motion[id].glow()));
                            }
                        }else if(themes)
                            MenuSurface.card(r.x,r.y,r.width,r.height,theme,false,motion[id].amount());
                        else {
                            boolean primary=id==1||id==6;
                            int fill=Theme.interpolateRGB(0xff202630,
                                    theme.getPrimaryGradientRGB(),primary&&active?.32f:.03f);
                            UIUtils.drawDecorationRound(r.x,r.y,r.width,r.height,3,new Color(fill,true));
                        }
                        GuiStyle.feedback(r,motion[id]);
                        text(label(id), r.x + 6, r.y + 6, r.width - 12, UiEffects.alpha(ink, active ? 1 : .42f));
                    }
                    field(searchBounds(), query.isEmpty() && focus != -1 ? "Search " + (themes ? "themes" : "configs") : query + (focus == -1 ? "_" : ""), focus == -1, ink);
                    try (GuiClip list = GuiClip.push(listBounds())) {
                        List<String> items = items();
                        for (int i = 0; i < items.size(); i++) {
                            ClipRect r = itemBounds(i); if (r.intersect(listBounds()).isEmpty()) continue;
                            String ref = items.get(i); boolean chosen = ref.equals(selected());
                            UiSpring itemMotion=cardMotion.computeIfAbsent((themes?"theme:":"config:")+ref,k->new UiSpring());
                            itemMotion.stepForInput(dt,listBounds().contains(mx,my)&&r.contains(mx,my),chosen&&focus==-2,false,true,ClientSettings.current());
                            if(two()){
                                MenuSurface.card(r.x+2,r.y+2,Math.max(1,r.width-5),Math.max(1,r.height-4),theme,chosen,itemMotion.amount());
                                if(chosen)UIUtils.drawDecorationRound(r.x+4,r.y+7,2,
                                        Math.max(1,r.height-14),1,
                                        NeutronVisualTokens.accent(theme,215));
                                if(chosen&&focus==-2&&showsKeyboardFocus())
                                    UIUtils.drawDecorationRound(r.x+8,r.y+r.height-4,
                                            Math.max(1,r.width-16),1,.5f,
                                            NeutronVisualTokens.secondary(theme,166));
                                if(creative&&r.contains(mx,my)){
                                    float itemGlow=itemMotion.glow();
                                    if(itemGlow>.02f){
                                        UIUtils.drawDecorationRound(r.x+2-itemGlow*2f, r.y+2-itemGlow*2f,
                                                Math.max(1,r.width-5)+itemGlow*4f, Math.max(1,r.height-4)+itemGlow*4f,
                                                10, me.youngjibbit.utils.render.UiEffects.accent(theme.getSecondaryGradientRGB(), .04f*itemGlow));
                                    }
                                }
                            }else if(themes)
                                MenuSurface.card(r.x,r.y,r.width,r.height,theme,chosen,itemMotion.amount());
                            else {
                                UIUtils.drawDecorationRound(r.x,r.y,r.width,r.height,3,
                                        new Color(Theme.interpolateRGB(0xff171c25,
                                                theme.getPrimaryGradientRGB(),
                                                chosen?.2f:r.contains(mx,my)?.07f:0),true));
                                if(chosen)UIUtils.drawDecorationRound(r.x+3,r.y+5,1,
                                        r.height-10,0,theme.getPrimaryGradientColor());
                                if(chosen&&focus==-2&&showsKeyboardFocus())
                                    UIUtils.drawDecorationRound(r.x+8,r.y+r.height-3,
                                            r.width-16,1,0,new Color(ink,true));
                            }
                            if (themes) {
                                Theme tile = catalog.find(ref);
                                // Literal swatches: the selected global gradient must never recolor another preset.
                                if(creative()&&!compactList())
                                    UIUtils.drawLiteralGradient(r.x+10,r.y+8,r.width-20,24,12,tile.getPrimaryGradientColor(),tile.getSecondaryGradientColor());
                                else UIUtils.drawLiteralGradient(r.x+5,r.y+4,r.width-10,compactList()?11:18,2,tile.getPrimaryGradientColor(),tile.getSecondaryGradientColor());
                                float labelY=r.y+(compactList()?19:creative()?40:31);
                                text(tile.getDisplayName(), r.x+7,labelY,r.width-27,ink);
                                if (chosen) text("+",r.x+r.width-16,labelY,10,ink);
                            } else {
                                text(ref,r.x+10,r.y+(r.height-10)/2,r.width-(chosen&&splitConfigs()?70:20),ink);
                                if(chosen&&splitConfigs()) text("Selected",r.x+r.width-55,r.y+11,48,UiEffects.alpha(ink,.75f));
                            }
                        }
                        if (items.isEmpty()) text(query.isEmpty() ? themes ? "No themes available." : configs.available() ? "No saved configs. Save your current setup." : "Config storage unavailable." : "No matches. Try another search.",
                                listBounds().x+5,listBounds().y+8,listBounds().width-10,ink);
                    }
                    float content=rowsHeight(); if(content>listBounds().height) {
                        ClipRect r=listBounds(); float thumb=Math.max(10,r.height*r.height/content);
                        UIUtils.drawDecorationRound(r.x+r.width-2,r.y+scroll/(content-r.height)*(r.height-thumb),2,thumb,1,UiEffects.color(ink,.7f));
                    }
                    if (!themes) field(nameBounds(),renaming&&focus==-4?(renameBuffer.isEmpty()?"New name":renameBuffer+(focus==-4?"_":"")):(name.isEmpty()&&focus!=-3?"Config name":name+(focus==-3?"_":"")),renaming?focus==-4:focus==-3,ink);
                    else {
                        Theme selection=catalog.find(selectedTheme);
                        String info=selection==null?"Select a theme":selection.getDisplayName()+(selection.isCustom()?" / Custom":" / Preset")+
                                (selection.name().equals(catalog.selectedReference())?" / Active":"");
                        text(info,nameBounds().x,nameBounds().y+6,nameBounds().width,ink);
                    }
                    String status = !message.isEmpty() ? message : themes ? catalog.isReadOnly() ? "Theme library is read-only. Presets can still be used." : "Select to inspect. Use theme applies; Edit previews before saving."
                            : configs.status().isEmpty() ? "Local files · Module values, binds & client settings" : configs.status();
                    text(status,x+12,y+h-(themes?20:16),w-24,UiEffects.alpha(ink,.85f));
                    if(configs.pending()!=ConfigLibrary.Pending.NONE) confirmation(ink);
                }
            });
        }
    }
    private void drawLegacyConfigs(int mx,int my,float dt) {
        UiEffects.beginFrame();
        Theme theme=Theme.resolve(Theme.getDefaultTheme());int ink=LegacyUiStyle.ink(theme);
        try(RenderState state=new RenderState()) {
            state.screenSpace();
            if(mc.theWorld==null)me.youngjibbit.gui.MainMenu.drawBackdrop(theme);
            surface.panel(x,y,w,h,9,theme,true,true,true,6,.94f,.65f,()->{
                try(GuiClip root=GuiClip.push(new ClipRect(x+2,y+2,w-4,h-4))) {
                    LegacyUiStyle.icon("save",x+16,y+13,16,theme.getPrimaryGradientRGB());
                    TTFFontRenderer title=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28");
                    title.drawString(ClickControlStyle.fit(title,"Configurations",w-116),x+42,y+13,ink);
                    for(int id:new int[]{6,7,8})legacyAction(id,mx,my,dt,theme,ink);
                    legacyField(searchBounds(),query.isEmpty()&&focus!=-1?"Search configs":query+(focus==-1?"_":""),focus==-1,"search",theme,ink);
                    ClipRect list=listBounds();
                    if(!compactConfigs()) {
                        String count=items().size()==configs.entries().size()?items().size()+" saved configs":items().size()+" matching configs";
                        text(count,list.x,list.y-16,list.width,LegacyUiStyle.MUTED);
                    }
                    String hover="";
                    try(GuiClip content=GuiClip.push(list)) {
                        List<String> names=items();
                        for(int i=0;i<names.size();i++) {
                            ClipRect hit=itemBounds(i);if(hit.intersect(list).isEmpty())continue;
                            String ref=names.get(i);boolean selected=ref.equals(configs.selected());
                            boolean hovered=list.contains(mx,my)&&hit.contains(mx,my);
                            if(hovered)hover=ref;
                            UiSpring animation=cardMotion.computeIfAbsent(ref,k->new UiSpring());
                            animation.stepForInput(dt,hovered,selected&&focus==-2,false,true,ClientSettings.current());
                            // Selection and hover only move the local card; grid geometry stays stable.
                            float lift=UiEffects.motion()?Math.min(1.5f,Math.max(0,animation.lift())):0;
                            ClipRect r=new ClipRect(hit.x,hit.y-lift,hit.width,hit.height);
                            legacyConfigCard(r,theme,selected,animation.amount());
                            GuiStyle.feedback(r,animation);
                            if(r.height>=50) {
                                LegacyUiStyle.icon("settings",r.x+12,r.y+12,17,theme.getPrimaryGradientRGB());
                                text(ref,r.x+38,r.y+12,r.width-64,ink);
                                if(selected)LegacyUiStyle.icon("check",r.x+r.width-22,r.y+14,10,theme.getPrimaryGradientRGB());
                                text(selected?"Selected · Enter to load":"Local profile",r.x+38,r.y+32,r.width-50,LegacyUiStyle.MUTED);
                            } else {
                                LegacyUiStyle.icon("settings",r.x+10,r.y+(r.height-12)/2,12,selected?theme.getPrimaryGradientRGB():LegacyUiStyle.MUTED);
                                text(ref,r.x+30,r.y+(r.height-10)/2,r.width-42,ink);
                            }
                        }
                        if(names.isEmpty()) {
                            String empty=query.isEmpty()?configs.available()?"Save your first configuration below.":"Config storage unavailable.":"No configs match your search.";
                            text(empty,list.x+8,list.y+Math.min(20,Math.max(6,(list.height-10)/2)),list.width-16,LegacyUiStyle.MUTED);
                        }
                    }
                    float contentHeight=rowsHeight();
                    if(contentHeight>list.height) {
                        float thumb=Math.max(10,list.height*list.height/contentHeight);
                        UIUtils.drawDecorationRound(list.x+list.width-2,list.y+scroll/(contentHeight-list.height)*(list.height-thumb),2,thumb,1,LegacyUiStyle.alpha(ink,.35f));
                    }
                    if(!compactConfigs())text(renaming?"Rename selected config":"Save current setup as",nameBounds().x,nameBounds().y-12,nameBounds().width,LegacyUiStyle.MUTED);
                    String editable=renaming?renameBuffer:name;
                    int fieldFocus=renaming?-4:-3;
                    String fieldText=editable.isEmpty()&&focus!=fieldFocus?(renaming?"New config name":"Config name"):editable+(focus==fieldFocus?"_":"");
                    legacyField(nameBounds(),fieldText,focus==fieldFocus,renaming?"rename":"plus",theme,ink);
                    ClipRect first=actionBounds(0),last=actionBounds(3);
                    UIUtils.drawDecorationRound(first.x-5,first.y-4,last.x+last.width-first.x+10,first.height+8,8,LegacyUiStyle.alpha(LegacyUiStyle.panel(theme).getRGB(),.9f));
                    for(int id:new int[]{0,1,4,2,3})legacyAction(id,mx,my,dt,theme,ink);
                    String status=!message.isEmpty()?message:!configs.status().isEmpty()?configs.status():!hover.isEmpty()?hover:"Select a card · Enter loads · Tab moves focus";
                    text(status,x+16,y+h-13,w-32,LegacyUiStyle.FAINT);
                    if(configs.pending()!=ConfigLibrary.Pending.NONE)confirmation(ink);
                }
            });
        }
    }
    private void legacyConfigCard(ClipRect r,Theme theme,boolean selected,float hover) {
        MenuSurface.card(r.x,r.y,r.width,r.height,theme,selected,hover);
    }
    private void legacyAction(int id,int mx,int my,float dt,Theme theme,int ink) {
        ClipRect hit=actionBounds(id);boolean active=enabled(id);
        motion[id].stepForInput(dt,hit.contains(mx,my),focus==id,pressed==id,active,ClientSettings.current());
        ClipRect r=GuiStyle.buttonBounds(hit,motion[id]);
        boolean selected=id==6;
        MenuSurface.card(r.x,r.y,r.width,r.height,theme,selected,motion[id].amount());
        GuiStyle.feedback(r,motion[id]);
        String[] names={"Save","Load","Delete","Refresh","Rename"};
        String title=id<5?names[id]:id==6?"Configs":id==7?"Themes":"Back";
        String[] icons={"save","load","delete","refresh","rename"};
        String icon=id<5?icons[id]:id==6?"save":id==7?"palette":"back";
        int color=UiEffects.alpha(id==2?0xffe8a5a9:ink,active?1:.38f);
        if(r.width>=72) {
            LegacyUiStyle.icon(icon,r.x+10,r.y+(r.height-12)/2,12,color);
            text(title,r.x+29,r.y+(r.height-10)/2,r.width-35,color);
        } else if(r.width>=44) {
            float textWidth=Math.min(font().getWidth(title),r.width-8);
            text(title,r.x+(r.width-textWidth)/2,r.y+(r.height-10)/2,r.width-8,color);
        } else LegacyUiStyle.icon(icon,r.x+(r.width-13)/2,r.y+(r.height-13)/2,13,color);
    }
    private void legacyField(ClipRect r,String value,boolean focused,String icon,Theme theme,int ink) {
        MenuSurface.card(r.x,r.y,r.width,r.height,theme,focused&&showsKeyboardFocus(),false);
        LegacyUiStyle.icon(icon,r.x+9,r.y+(r.height-12)/2,12,focused?theme.getPrimaryGradientRGB():LegacyUiStyle.FAINT);
        try(GuiClip field=GuiClip.push(new ClipRect(r.x+28,r.y+2,r.width-36,r.height-4))) {
            if(focused)while(value.length()>1&&font().getWidth(value)>r.width-42)value=value.substring(value.offsetByCodePoints(0,1));
            text(value,r.x+29,r.y+(r.height-10)/2,r.width-40,ink);
        }
    }
    private void configSections(int ink) {
        if (splitConfigs()) {
            text("Saved configs · " + configs.entries().size(),x+12,y+65,configListWidth(),ink);
            UIUtils.drawDecorationRound(detailsX()-8,y+64,1,h-96,0,UiEffects.color(ink,.12f));
            text("Selected config",detailsX(),y+66,detailsWidth(),UiEffects.alpha(ink,.72f));
            String visible = visibleConfigSelection();
            text(visible.isEmpty() ? "Choose a config" : visible, detailsX(), y+87, detailsWidth(), ink);
            UIUtils.drawDecorationRound(detailsX(),y+181,detailsWidth(),1,0,UiEffects.color(ink,.12f));
            text("Save current setup",detailsX(),y+190,detailsWidth(),ink);
        } else text("Save current setup as",x+12,y+h-59,w-24,UiEffects.alpha(ink,.8f));
    }
    private void field(ClipRect r,String value,boolean focused,int ink) {
        if(ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())){
            MenuSurface.card(r.x,r.y,r.width,r.height,Theme.getDefaultTheme(),focused&&showsKeyboardFocus(),false);
            if(focused&&showsKeyboardFocus())UIUtils.drawDecorationRound(r.x+4,
                    r.y+r.height-2,Math.max(1,r.width-8),1,.5f,
                    NeutronVisualTokens.accent(Theme.getDefaultTheme(),185));
        }else MenuSurface.card(r.x,r.y,r.width,r.height,Theme.getDefaultTheme(),focused&&showsKeyboardFocus(),false);
        try(GuiClip clip=GuiClip.push(new ClipRect(r.x+5,r.y+2,r.width-10,r.height-4))) {
            if(focused)while(value.length()>1&&font().getWidth(value)>r.width-14)value=value.substring(value.offsetByCodePoints(0,1));
            text(value,r.x+7,r.y+6,r.width-14,ink);
        }
    }
    private ClipRect confirmBounds(boolean accept) {
        float cw=Math.min(340,w-24),left=x+(w-cw)/2;
        return new ClipRect(left+12+(accept?0:(cw-30)/2+6),y+h/2+21,(cw-30)/2,24);
    }
    private void confirmation(int ink) {
        UIUtils.drawDecorationRound(x,y,w,h,5,new Color(4,6,10,205));
        float cw=Math.min(340,w-24),left=x+(w-cw)/2,top=y+h/2-48;
        GuiStyle.panel(left,top,cw,105,5,1,new Color(19,22,29));
        String confirmTitle = configs.pending()==ConfigLibrary.Pending.DELETE ? "Delete config?"
                : configs.pending()==ConfigLibrary.Pending.RENAME ? "Rename config?"
                : "Replace config?";
        text(confirmTitle,left+12,top+12,cw-24,ink);
        text(configs.target(),left+12,top+33,cw-24,ink);
        for(boolean accept:new boolean[]{true,false}) {
            ClipRect r=confirmBounds(accept);GuiStyle.panel(r.x,r.y,r.width,r.height,3,accept?.7f:.1f,new Color(26,29,37));
            text(accept?"Confirm":"Cancel",r.x+8,r.y+7,r.width-16,ink);
        }
    }
    protected void open(GuiScreen screen) { mc.displayGuiScreen(screen); }
    protected void activate(int id) {
        if(!enabled(id))return;
        message="";
        if(id!=4){renaming=false;renameBuffer="";}
        if(id==8){open(parent);return;}
        if(id==6||id==7){themes=id==7;query="";scroll=targetScroll=0;configs.cancel();invalidateItems();focus=-1;layout();return;}
        if(themes) {
            if(id==4){
                // The editor changes the display name and preserves the stable custom ID.
                open(new ThemeEditorScreen(this,catalog,ThemeRuntime::select).editTheme(catalog.find(selectedTheme),false));
                return;
            }
            Theme theme=catalog.find(selectedTheme);
            if(id==0){ThemeRuntime.select(selectedTheme);message="Using "+theme.getDisplayName();}
            else if(id==1||id==2)open(new ThemeEditorScreen(this,catalog,ThemeRuntime::select).editTheme(theme,id==2||!theme.isCustom()));
            else if(id==3)open(new SettingsMenu(this));
        } else {
            if(id==4){renaming=true;renameBuffer=configs.selected();replace=true;focus=-4;message="";layout();return;}
            if(id==0)configs.save(name);
            else if(id==1){configs.load();if(mc.currentScreen!=this)open(this);}
            else if(id==2)configs.requestDelete();
            else if(id==3)configs.refresh();
        }
        invalidateItems();layout();
    }
    private void select(int index) {
        renaming=false;renameBuffer="";
        List<String> entries=items();if(index<0||index>=entries.size())return;
        if(themes)selectedTheme=entries.get(index);else{configs.select(entries.get(index));name=configs.selected();}
        focus=-2;message="";
        revealItem(index);
    }
    private void revealItem(int index) {
        if(index<0)return;
        ClipRect row=itemBounds(index),viewport=listBounds();
        if(row.y<viewport.y)scroll+=row.y-viewport.y;
        else if(row.y+row.height>viewport.y+viewport.height)scroll+=row.y+row.height-viewport.y-viewport.height;
        scroll=Math.max(0,scroll);targetScroll=scroll;
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException {
        if(button!=0)return;layout();
        if(configs.pending()!=ConfigLibrary.Pending.NONE) {
            if(confirmBounds(true).contains(mx,my)){finishConfirmation(true);return;}
            else if(confirmBounds(false).contains(mx,my)){finishConfirmation(false);return;}
            return;
        }
        if(searchBounds().contains(mx,my)){focus=-1;replace=false;return;}
        if(!themes&&nameBounds().contains(mx,my)){focus=renaming?-4:-3;replace=false;return;}
        for(int id:new int[]{0,1,2,3,4,6,7,8})if(actionBounds(id).contains(mx,my)){focus=id;pressed=id;activate(id);return;}
        if(listBounds().contains(mx,my))for(int i=0;i<items().size();i++)if(itemBounds(i).contains(mx,my)){select(i);return;}
    }
    @Override protected void mouseReleased(int mx,int my,int button) { pressed=-99; }
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();
        int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
        scrollAt(mx,my,Mouse.getEventDWheel());
    }
    protected void scrollAt(int mx,int my,int wheel) {
        if(configs.pending()!=ConfigLibrary.Pending.NONE||!listBounds().contains(mx,my)||wheel==0)return;
        targetScroll-=Math.signum(wheel)*rowHeight()*2;layout();
    }
    private void finishConfirmation(boolean confirm) {
        ConfigLibrary.Pending action=configs.pending();
        if(confirm){if(configs.confirm()&&action==ConfigLibrary.Pending.RENAME)name=configs.selected();}
        else configs.cancel();
        renaming=false;renameBuffer="";
        // Return to the originating action. A fresh Enter must open a new dialog.
        focus=action==ConfigLibrary.Pending.DELETE?2:action==ConfigLibrary.Pending.RENAME?4:0;
        invalidateItems();layout();
    }
    protected boolean ctrlDown(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    protected boolean shiftDown(){return Keyboard.isCreated()&&isShiftKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException {
        pressed=-99;
        boolean enter=key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER;
        if(configs.pending()!=ConfigLibrary.Pending.NONE) {
            if(key==Keyboard.KEY_ESCAPE){finishConfirmation(false);return;}
            else if(enter){finishConfirmation(true);return;}
            return;
        }
        if(key==Keyboard.KEY_ESCAPE){if(renaming){renaming=false;renameBuffer="";focus=-1;return;}open(parent);return;}
        if(key==Keyboard.KEY_TAB) {
            if(renaming){renaming=false;renameBuffer="";focus=4;}
            int[] order=themes?new int[]{6,7,-1,-2,0,1,2,3,4,8}:new int[]{6,7,-1,3,-2,1,2,-3,4,0,8};
            int current=0;for(int i=0;i<order.length;i++)if(order[i]==focus)current=i;
            focus=order[(current+(shiftDown()?order.length-1:1))%order.length];replace=false;
            if(focus==-2)revealItem(items().indexOf(selected()));
            return;
        }
        if(focus==-1||focus==-3) {
            if(ctrlDown()&&key==Keyboard.KEY_A){replace=true;return;}
            String value=focus==-1?query:name;
            if(key==Keyboard.KEY_BACK){value=replace?"":value.substring(0,value.offsetByCodePoints(value.length(),value.isEmpty()?0:-1));replace=false;}
            else if (enter || key == Keyboard.KEY_DOWN) {
                if (focus == -1) {
                    if (!items().isEmpty()) select(0);
                } else if (key != Keyboard.KEY_DOWN) {
                    activate(0);
                }
                return;
            }
            else if(c>=32&&c!=127&&(replace||value.length()<64)){value=replace?String.valueOf(c):value+c;replace=false;}
            else return;
            configs.cancel();message="";
            if(focus==-1){query=value;scroll=targetScroll=0;}else name=value;
            return;
        }
        if(focus==-4 && renaming && !themes) {
            if(key==Keyboard.KEY_ESCAPE){renaming=false;renameBuffer="";focus=-1;return;}
            if(enter){renaming=false;if(configs.rename(renameBuffer)){message="Renamed "+renameBuffer;}renameBuffer="";focus=-1;invalidateItems();layout();return;}
            if(ctrlDown()&&key==Keyboard.KEY_A){replace=true;return;}
            if(key==Keyboard.KEY_BACK){renameBuffer=replace?"":renameBuffer.substring(0,renameBuffer.offsetByCodePoints(renameBuffer.length(),renameBuffer.isEmpty()?0:-1));replace=false;return;}
            else if(c>=32&&c!=127&&(replace||renameBuffer.length()<64)){renameBuffer=replace?String.valueOf(c):renameBuffer+c;replace=false;return;}
            else return;
        }
        if(focus==-2) {
            List<String> entries=items();int index=entries.indexOf(selected());
            if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN||key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT) {
                int step=key==Keyboard.KEY_UP?-columns():key==Keyboard.KEY_DOWN?columns():key==Keyboard.KEY_LEFT?-1:1;
                select(Math.max(0,Math.min(entries.size()-1,index+step)));
            } else if(enter)activate(themes?0:1);
            return;
        }
        if(enter||key==Keyboard.KEY_SPACE)activate(focus);
    }
    @Override public void onGuiClosed() {
        pressed=-99;configs.cancel();renaming=false;renameBuffer="";surface.close();for(UiSpring state:motion)state.reset();cardMotion.clear();
        if(Keyboard.isCreated()&&ownsKeyboardRepeat)Keyboard.enableRepeatEvents(repeatBefore);
        ownsKeyboardRepeat=false;
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
