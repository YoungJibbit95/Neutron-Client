package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.library.ConfigLibrary;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.config.*;
import me.youngjibbit.gui.UiSpring;
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
    private boolean themes, replace, repeatBefore;
    private String query = "", name = "", selectedTheme = "", message = "";
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
    @Override public void initGui() {
        layout(); configs.refresh(); if(catalog.find(selectedTheme)==null)selectedTheme=catalog.selectedReference(); lastFrame = System.nanoTime(); pressed = -99;
        if (Keyboard.isCreated()) { repeatBefore = Keyboard.areRepeatEventsEnabled(); Keyboard.enableRepeatEvents(true); }
    }
    private TTFFontRenderer font() { return ClickControlStyle.font(); }
    private void text(String text, float x, float y, float width, int ink) {
        font().drawString(ClickControlStyle.fit(font(), text, width), x, y, ink);
    }
    private void layout() {
        w = Math.min(640, Math.max(1, width - 20)); h = Math.min(420, Math.max(1, height - 20));
        x = (width - w) / 2; y = (height - h) / 2;
        float max = Math.max(0, rowsHeight() - listBounds().height);
        targetScroll = Math.max(0, Math.min(max, targetScroll)); scroll = Math.max(0, Math.min(max, scroll));
    }
    private boolean splitConfigs() { return !themes && w >= 540 && h >= 300; }
    private float configListWidth() { return splitConfigs() ? (w - 40) * .58f : w - 24; }
    private float detailsX() { return x + 28 + configListWidth(); }
    private float detailsWidth() { return w - 40 - configListWidth(); }
    protected ClipRect listBounds() {
        if (!themes) return new ClipRect(x + 12, y + (splitConfigs() ? 108 : 82), configListWidth(), Math.max(28, h - (splitConfigs() ? 140 : 170)));
        return new ClipRect(x + 12, y + 90, w - 24, Math.max(8, h - 170));
    }
    protected ClipRect searchBounds() {
        if (!themes) return new ClipRect(x + 12, y + (splitConfigs() ? 82 : 56), configListWidth() - 70, 20);
        return new ClipRect(x + 12, y + 62, w - 24, 22);
    }
    protected ClipRect nameBounds() {
        if (!themes) return splitConfigs() ? new ClipRect(detailsX(), y + 208, detailsWidth(), 22)
                : new ClipRect(x + 12, y + h - 44, w - 102, 22);
        return new ClipRect(x + 12, y + h - 78, w - 24, 22);
    }
    protected ClipRect actionBounds(int id) {
        if (id == 6 || id == 7) return new ClipRect(x + 12 + (id - 6) * 88, y + (themes ? 34 : 30), 82, themes ? 22 : 20);
        if (id == 8) return new ClipRect(x + w - 50, y + 10, 38, 22);
        if (!themes) {
            if (id == 3) { ClipRect search = searchBounds(); return new ClipRect(search.x + search.width + 6, search.y, 64, 20); }
            if (splitConfigs()) return new ClipRect(detailsX(), y + (id == 0 ? 236 : id == 1 ? 112 : 144), detailsWidth(), 26);
            if (id == 0) return new ClipRect(x + w - 84, y + h - 44, 72, 22);
            float half = (w - 30) / 2;
            return new ClipRect(x + 12 + (id - 1) * (half + 6), y + h - 86, half, 22);
        }
        float bw = (w - 42) / 4;
        return new ClipRect(x + 12 + id * (bw + 6), y + h - 50, bw, 24);
    }
    private int columns() { return themes ? Math.max(1, Math.min(3, (int)((w - 18) / 150))) : 1; }
    private boolean compactList() { return h < 300; }
    private float rowHeight() { return themes ? compactList() ? 36 : 56 : splitConfigs() ? 34 : 28; }
    private float rowsHeight() { return ((items().size() + columns() - 1) / columns()) * rowHeight(); }
    protected ClipRect itemBounds(int index) {
        ClipRect list = listBounds(); float cell = list.width / columns();
        return new ClipRect(list.x + index % columns() * cell, list.y + index / columns() * rowHeight() - scroll,
                cell - (themes ? 6 : 0), rowHeight() - (themes ? 5 : 2));
    }
    private List<String> items() {
        List<String> names = new ArrayList<>(); String q = query.toLowerCase(Locale.ROOT);
        if (themes) {
            for (Theme theme : catalog.all()) if (theme.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) names.add(theme.name());
        } else for (LocalConfig config : configs.entries()) if (config.getName().toLowerCase(Locale.ROOT).contains(q)) names.add(config.getName());
        return names;
    }
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
            return theme != null && (id == 0 || !catalog.isReadOnly());
        }
        return configs.available() && (id == 0 || !visibleConfigSelection().isEmpty());
    }
    private String label(int id) {
        if (id == 6) return "Configs"; if (id == 7) return "Themes"; if (id == 8) return "Done";
        if (!themes) return new String[]{"Save as", "Load config", "Delete", "Refresh"}[id];
        return new String[]{"Use theme", "Edit", "Duplicate", "Effects"}[id];
    }
    @Override public void drawScreen(int mx, int my, float partialTicks) {
        layout(); long now = System.nanoTime(); float dt = Math.min(.05f, Math.max(0, (now - lastFrame) / 1e9f)); lastFrame = now;
        scroll += (targetScroll - scroll) * UiEffects.response(dt, 22);
        Theme theme = Theme.getDefaultTheme(); int ink = theme.getTextColor().getRGB();
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            if(mc.theWorld==null)me.youngjibbit.gui.MainMenu.drawBackdrop(theme);else UIUtils.drawDecorationRound(0, 0, width, height, 0, new Color(5, 7, 12, 115));
            surface.panel(x, y, w, h, 6, theme, true, true, true, 8, .92f, .8f, () -> {
                try (GuiClip clip = GuiClip.push(new ClipRect(x + 2, y + 2, w - 4, h - 4))) {
                    Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28").drawString("Neutron", x + 12, y + 12, ink);
                    if (w > 370) text(themes ? "Configs & themes" : "Local configs", x + 107, y + 17, w - 170, ink);
                    if (!themes) configSections(ink);
                    for (int id : new int[]{0,1,2,3,6,7,8}) {
                        ClipRect r = actionBounds(id); boolean active = enabled(id);
                        motion[id].step(dt, r.contains(mx,my), focus == id, pressed == id, active, ClientSettings.current());
                        if (themes) GuiStyle.panel(r.x,r.y,r.width,r.height,3, id == 7 ? 1 : .18f, new Color(21,24,31));
                        else {
                            boolean primary = id == 1 || id == 6;
                            int fill = Theme.interpolateRGB(0xff202630, theme.getPrimaryGradientRGB(), primary && active ? .32f : .03f);
                            UIUtils.drawDecorationRound(r.x,r.y,r.width,r.height,3,new Color(fill,true));
                            if (focus == id) UIUtils.drawDecorationRound(r.x+5,r.y+r.height-2,r.width-10,1,0,new Color(ink,true));
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
                            if (themes) GuiStyle.panel(r.x,r.y,r.width,r.height,3,chosen?1:r.contains(mx,my)?.4f:.05f,new Color(17,20,27));
                            else {
                                UIUtils.drawDecorationRound(r.x,r.y,r.width,r.height,3,new Color(Theme.interpolateRGB(0xff171c25,theme.getPrimaryGradientRGB(),chosen?.2f:r.contains(mx,my)?.07f:0),true));
                                if (chosen) UIUtils.drawDecorationRound(r.x+3,r.y+5,1,r.height-10,0,theme.getPrimaryGradientColor());
                                if (chosen && focus == -2) UIUtils.drawDecorationRound(r.x+8,r.y+r.height-3,r.width-16,1,0,new Color(ink,true));
                            }
                            if (themes) {
                                Theme tile = catalog.find(ref);
                                // Literal swatches: the selected global gradient must never recolor another preset.
                                UIUtils.drawLiteralGradient(r.x+5,r.y+4,r.width-10,compactList()?11:18,2,tile.getPrimaryGradientColor(),tile.getSecondaryGradientColor());
                                float labelY=r.y+(compactList()?19:31);
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
                    if (!themes) field(nameBounds(),name.isEmpty()&&focus!=-3?"Config name":name+(focus==-3?"_":""),focus==-3,ink);
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
        GuiStyle.panel(r.x,r.y,r.width,r.height,3,focused?1:.12f,new Color(15,18,24));
        try(GuiClip clip=GuiClip.push(new ClipRect(r.x+5,r.y+2,r.width-10,r.height-4))) {
            if(focused)while(value.length()>1&&font().getWidth(value)>r.width-14)value=value.substring(1);
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
        text(configs.pending()==ConfigLibrary.Pending.DELETE?"Delete config?":"Replace config?",left+12,top+12,cw-24,ink);
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
        if(id==8){open(parent);return;}
        if(id==6||id==7){themes=id==7;query="";scroll=targetScroll=0;configs.cancel();focus=-1;return;}
        if(themes) {
            Theme theme=catalog.find(selectedTheme);
            if(id==0){ThemeRuntime.select(selectedTheme);message="Using "+theme.getDisplayName();}
            else if(id==1||id==2)open(new ThemeEditorScreen(this,catalog,ThemeRuntime::select).editTheme(theme,id==2||!theme.isCustom()));
            else if(id==3)open(new SettingsMenu(this));
        } else {
            if(id==0)configs.save(name);
            else if(id==1){configs.load();if(mc.currentScreen!=this)open(this);}
            else if(id==2)configs.requestDelete();
            else if(id==3)configs.refresh();
        }
        layout();
    }
    private void select(int index) {
        List<String> entries=items();if(index<0||index>=entries.size())return;
        if(themes)selectedTheme=entries.get(index);else{configs.select(entries.get(index));name=configs.selected();}
        focus=-2;message="";
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException {
        if(button!=0)return;layout();
        if(configs.pending()!=ConfigLibrary.Pending.NONE) {
            if(confirmBounds(true).contains(mx,my))configs.confirm();
            else if(confirmBounds(false).contains(mx,my))configs.cancel();
            return;
        }
        if(searchBounds().contains(mx,my)){focus=-1;replace=false;return;}
        if(!themes&&nameBounds().contains(mx,my)){focus=-3;replace=false;return;}
        for(int id:new int[]{0,1,2,3,6,7,8})if(actionBounds(id).contains(mx,my)){focus=id;pressed=id;activate(id);return;}
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
    protected boolean ctrlDown(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    protected boolean shiftDown(){return Keyboard.isCreated()&&isShiftKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException {
        pressed=-99;
        if(configs.pending()!=ConfigLibrary.Pending.NONE) {
            if(key==Keyboard.KEY_ESCAPE)configs.cancel();
            else if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER)configs.confirm();
            return;
        }
        if(key==Keyboard.KEY_ESCAPE){open(parent);return;}
        if(key==Keyboard.KEY_TAB) {
            int[] order=themes?new int[]{6,7,-1,-2,0,1,2,3,8}:new int[]{6,7,-1,3,-2,1,2,-3,0,8};
            int current=0;for(int i=0;i<order.length;i++)if(order[i]==focus)current=i;
            focus=order[(current+(shiftDown()?order.length-1:1))%order.length];replace=false;return;
        }
        if(focus==-1||focus==-3) {
            if(ctrlDown()&&key==Keyboard.KEY_A){replace=true;return;}
            String value=focus==-1?query:name;
            if(key==Keyboard.KEY_BACK){value=replace?"":value.substring(0,Math.max(0,value.length()-1));replace=false;}
            else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_DOWN) {
                if (focus == -1) {
                    focus = -2;
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
        if(focus==-2) {
            List<String> entries=items();int index=entries.indexOf(selected());
            if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN||key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT) {
                int step=key==Keyboard.KEY_UP?-columns():key==Keyboard.KEY_DOWN?columns():key==Keyboard.KEY_LEFT?-1:1;
                select(Math.max(0,Math.min(entries.size()-1,index+step)));
                int next=items().indexOf(selected());if(next>=0){ClipRect r=itemBounds(next),v=listBounds();if(r.y<v.y)scroll=targetScroll=Math.max(0,scroll+r.y-v.y);else if(r.y+r.height>v.y+v.height)scroll=targetScroll=scroll+r.y+r.height-v.y-v.height;}
            } else if(key==Keyboard.KEY_RETURN)activate(themes?0:1);
            return;
        }
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE)activate(focus);
    }
    @Override public void onGuiClosed() {
        pressed=-99;configs.cancel();surface.close();for(UiSpring state:motion)state.reset();
        if(Keyboard.isCreated())Keyboard.enableRepeatEvents(repeatBefore);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
