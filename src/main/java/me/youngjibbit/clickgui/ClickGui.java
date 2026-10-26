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
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.render.ClickGUI;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.Blur;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.UIUtils;
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
    private static final float SIDEBAR = 102, HEADER = 36, CONTENT_HEADER = 24, FOOTER = 20, MARGIN = 8;
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

    public ClickGui() {
        setupModules();
    }

    @Override
    public void initGui() {
        super.initGui();
        dragging = resizing = false;
        cancelInputs();
        releaseControls();
        fitWindow();
        setupModules();
        selectionOffset = selectedCategory.ordinal() * categoryStep();
        lastFrame = System.nanoTime();
    }

    private void fitWindow() {
        float availableWidth = Math.max(1, super.width - MARGIN * 2);
        float availableHeight = Math.max(1, super.height - MARGIN * 2);
        width = Math.max(Math.min(320, availableWidth), Math.min(width, availableWidth));
        height = Math.max(Math.min(224, availableHeight), Math.min(height, availableHeight));
        if (!positioned) {
            x = (super.width - width) / 2;
            y = (super.height - height) / 2;
            positioned = true;
        }
        x = Math.max(MARGIN, Math.min(x, super.width - width - MARGIN));
        y = Math.max(MARGIN, Math.min(y, super.height - height - MARGIN));
    }

    private float categoryStep() {
        return Math.min(24, (height - HEADER - FOOTER - 8) / CATEGORIES.length);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        float delta = Math.min(0.05f, (System.nanoTime() - lastFrame) / 1.0e9f);
        lastFrame = System.nanoTime();
        if (dragging) {
            x = mouseX - grabX;
            y = mouseY - grabY;
        } else if (resizing) {
            width = resizeWidth + mouseX - grabX;
            height = resizeHeight + mouseY - grabY;
        }
        fitWindow();
        float step = categoryStep();
        selectionOffset += (selectedCategory.ordinal() * step - selectionOffset) * me.youngjibbit.utils.render.UiEffects.response(delta,20);
        layoutModules(delta);

        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        themedSurface.panel(x,y,width,height,5,theme,true,true,true,8,.72f,1,()->{
        // The legacy bloom helper restores raw GL attributes, bypassing Minecraft's state cache.
        // Synchronize both sides before drawing coplanar UI surfaces over its fullscreen quad.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);


        try (GuiClip windowClip = GuiClip.push(new ClipRect(x + 2, y + 2, width - 4, height - 4))) {
            UIUtils.drawRound(x + 1, y + 1, SIDEBAR - 2, height - 2, 4, new Color(8, 8, 9));
            UIUtils.drawRound(x + 1, y + 1, width - 2, HEADER - 1, 4, new Color(13, 13, 14));
            drawClientName(Pulsar.INSTANCE.getName(), (int) (x + 9), (int) (y + 10), theme);
            Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString("o", x + width - 21, y + 13,
                    theme.getSecondaryGradientRGB());
            ClipRect searchBox=searchBounds();
            GuiStyle.panel(searchBox.x,searchBox.y,searchBox.width,searchBox.height,3,searchFocus?1:0,new Color(22,22,24));
            try(GuiClip ignored=GuiClip.push(new ClipRect(searchBox.x+8,searchBox.y,searchBox.width-28,searchBox.height))){
                String shown=search.isEmpty()&&!searchFocus?width<380?"Search...":"Search modules...":search+(searchFocus?"_":"");
                while(shown.length()>1&&font().getWidth(shown)>searchBox.width-32)shown=shown.substring(1);
                font().drawString(shown,searchBox.x+8,textY(font(),"Search",searchBox),searchFocus?0xffffffff:0xffa4a9b3);
            }
            if(!search.isEmpty()||searchFocus)font().drawString("x",searchBox.x+searchBox.width-14,textY(font(),"x",searchBox),0xffb8bec8);
            ClipRect themes=themeBounds();GuiStyle.panel(themes.x,themes.y,themes.width,themes.height,3,0,new Color(22,22,24));
            font().drawString("Themes",themes.x+(themes.width-font().getWidth("Themes"))/2,textY(font(),"Themes",themes),theme.isCustom()?theme.getTextColor().getRGB():0xffd4d9e3);
            float selectedY = y + HEADER + 4 + selectionOffset;
            GuiStyle.panel(x + 5, selectedY, SIDEBAR - 10, step - 4, 5, 1, new Color(11, 11, 12));
            UIUtils.drawRound(x + SIDEBAR - 16, selectedY + (step - 4) / 2 - 2, 4, 4, 2,
                    theme.getPrimaryGradientColor());
            String[] icons = {"c", "f", "d", "d", "e", "t", "f", "a"};
            for (int i = 0; i < CATEGORIES.length; i++) {
                float rowY = y + HEADER + 4 + i * step;
                String font = i == 2 || i == 4 || i == 6 ? "icon1 21" : "icon 21";
                Pulsar.INSTANCE.getFontManager().getFont(font).drawString(icons[i], x + 9, rowY + (step-4-10)/2, i==selectedCategory.ordinal()?Color.WHITE.getRGB():0xffb6bdca);
                Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString(
                        CATEGORIES[i].name(), x + 24, rowY + (step-4-10)/2, i==selectedCategory.ordinal()?Color.WHITE.getRGB():0xffb6bdca);
            }
            String title=search.trim().isEmpty()?selectedCategory.name():"Search results";
            String count=modules.size()+(!search.trim().isEmpty()?(modules.size()==1?" result":" results"):(modules.size()==1?" module":" modules"));
            TTFFontRenderer heading=Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18");
            heading.drawString(title,viewport.x+1,y+HEADER+8,0xffedf0f7);
            font().drawString(count,viewport.x+viewport.width-font().getWidth(count)-2,y+HEADER+8,0xffaab3c3);
            String shortcuts=width>=400?"/ Search  \u00b7  Tab Navigate  \u00b7  K Bind":"/ Search  \u00b7  Tab  \u00b7  K Bind";
            font().drawString(shortcuts,viewport.x,y+height-FOOTER+5,0xffaab3c3);
            if(maxScroll>0){
                float thumb=Math.max(16,viewport.height*viewport.height/(viewport.height+maxScroll));
                float thumbY=viewport.y+(-scrollOffset/maxScroll)*(viewport.height-thumb);
                UIUtils.drawRound(x+width-7,viewport.y,2,viewport.height,1,new Color(42,46,56));
                UIUtils.drawRound(x+width-7,thumbY,2,thumb,1,theme.getSecondaryGradientColor());
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
            if(focus!=null&&!searchFocus&&binding==null){
                try(GuiClip ignored=GuiClip.push(viewport)){
                    float fh=24;
                    UIUtils.drawRound(focus.getX()+2,focus.getY()+2,2,fh-4,1,theme.getSecondaryGradientColor());
                }
            }
            drawTooltip(mouseX,mouseY);
            if(binding!=null)drawBinding();
        }
        });
    }

    private void layoutModules(float delta) {
        viewport = new ClipRect(x + SIDEBAR + 8, y + HEADER + CONTENT_HEADER + 4, width - SIDEBAR - 20, height - HEADER - CONTENT_HEADER - FOOTER - 8);
        float contentHeight = 0;
        for (ClickModule module : modules) {
            module.updateAnimation();
            contentHeight += module.getHeight() + 4;
        }
        maxScroll = Math.max(0, contentHeight - 4 - viewport.height);
        targetScrollOffset = Math.max(-maxScroll, Math.min(0, targetScrollOffset));
        scrollOffset += (targetScrollOffset - scrollOffset) * me.youngjibbit.utils.render.UiEffects.response(delta,20);
        scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));
        float moduleY = viewport.y + scrollOffset;
        for (ClickModule module : modules) {
            module.layout(viewport.x + 1, moduleY + 1, viewport.width - 2, viewport);
            moduleY += module.getHeight() + 4;
        }
        if(focus!=null&&!focusables().contains(focus)){
            Component replacement=null;
            if(focus instanceof Component.Sub){Object v=((Component.Sub<?>)focus).getValue();if(v instanceof Value)for(ClickModule row:modules)if(row.getModule()==((Value<?>)v).getParent())replacement=row;}
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
        return moduleCache.computeIfAbsent(category,key->{List<ClickModule> result=new ArrayList<>();for(Module module:Pulsar.INSTANCE.getMm().getModules(key))result.add(new ClickModule(module,0,0,width-SIDEBAR-12,24));return result;});
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
        if(binding!=null)return;
        if(button==0&&themeBounds().contains(mouseX,mouseY)){openThemeEditor();return;}
        ClipRect searchBox=searchBounds();
        if(button==0&&searchBox.contains(mouseX,mouseY)){
            setFocus(null);searchFocus=true;
            if(mouseX>=searchBox.x+searchBox.width-17){search="";updateSearch();}return;
        }
        searchFocus=false;
        if (button == 0 && new ClipRect(x + width - 26, y + 2, 24, HEADER - 2).contains(mouseX, mouseY)) {
            releaseControls();
            setFocus(null);resizing = true;
            grabX = mouseX;
            grabY = mouseY;
            resizeWidth = width;
            resizeHeight = height;
            return;
        }
        if (button == 0 && new ClipRect(x, y, width, HEADER).contains(mouseX, mouseY)) {
            releaseControls();
            setFocus(null);dragging = true;
            grabX = mouseX - x;
            grabY = mouseY - y;
            return;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (button == 0 && new ClipRect(x + 5, y + HEADER + 4 + i * categoryStep(), SIDEBAR - 10, categoryStep() - 4).contains(mouseX, mouseY)) {
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
        dragging = resizing = false;
        releaseControls();
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void releaseControls() {
        for (List<ClickModule> category : moduleCache.values()) {
            for (ClickModule module : category) module.mouseReleased();
        }
    }

    @Override
    public void onGuiClosed() {
        headingEffects.close();themedSurface.close();cancelInputs();hoverTarget=null;
        dragging = resizing = false;
        releaseControls();
        super.onGuiClosed();
    }

    @Override protected void keyTyped(char typedChar,int keyCode)throws IOException{
        if(binding!=null){
            if(keyCode==Keyboard.KEY_ESCAPE){binding=null;return;}
            if(keyCode==Keyboard.KEY_NONE)return;
            binding.getModule().setKey(keyCode==Keyboard.KEY_DELETE||keyCode==Keyboard.KEY_BACK?0:keyCode);binding=null;return;
        }
        if(focus instanceof ClickSlider&&((ClickSlider)focus).isEditing()){
            if(keyCode==Keyboard.KEY_TAB){((ClickSlider)focus).cancelInput();navigate(shiftDown()?-1:1);}else focus.keyTyped(typedChar,keyCode);return;
        }
        if(keyCode==Keyboard.KEY_T&&ctrlDown()){openThemeEditor();return;}
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
        if(keyCode==Keyboard.KEY_ESCAPE){
            for(ClickModule row:modules)row.cancelInputs();
            Pulsar.INSTANCE.getMm().getModule(ClickGUI.class).setToggled(false);return;
        }
        if(keyCode==Keyboard.KEY_UP||keyCode==Keyboard.KEY_DOWN){navigate(keyCode==Keyboard.KEY_UP?-1:1);return;}
        if(focus instanceof ClickModule&&keyCode==Keyboard.KEY_K){binding=(ClickModule)focus;return;}
        if(focus!=null){focus.keyTyped(typedChar,keyCode);layoutModules(0);reveal(focus);}
    }
    protected boolean shiftDown(){return Keyboard.isCreated()&&isShiftKeyDown();}
    protected boolean ctrlDown(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    private TTFFontRenderer font(){return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18");}
    protected ClipRect searchBounds(){return new ClipRect(x+SIDEBAR+8,y+6,width-SIDEBAR-104,24);}
    protected ClipRect themeBounds(){return new ClipRect(x+width-88,y+6,56,24);}
    private static float textY(TTFFontRenderer font,String text,ClipRect box){return box.y+(box.height-font.getHeight(text))/2;}
    protected ThemeEditorScreen createThemeEditor(){return new ThemeEditorScreen(this,me.youngjibbit.theme.ThemeCatalog.global(),me.youngjibbit.theme.ThemeRuntime::select);}
    protected void openThemeEditor(){mc.displayGuiScreen(createThemeEditor());}
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
        try(GuiClip ignored=GuiClip.push(new ClipRect(bx,by,w,h))){
            GuiStyle.panel(bx,by,w,h,5,1,new Color(11,11,13));
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
        }else if(target instanceof ClickSlider)text="Arrows: adjust / Enter or right click: exact number / Esc: cancel";
        else text="Tab: focus / Space or Enter: activate / Arrows: choose";
        float w=Math.min(250,width-16);List<String> lines=new ArrayList<>();String line="";
        for(String word:text.split(" ")){String next=line.isEmpty()?word:line+" "+word;if(font().getWidth(next)>w-16&&!line.isEmpty()){lines.add(line);line=word;}else line=next;}if(!line.isEmpty())lines.add(line);
        float h=12+lines.size()*11,tx=Math.max(x+5,Math.min(mx+10,x+width-w-5)),ty=Math.max(y+5,Math.min(my+16,y+height-h-5));
        try(GuiClip ignored=GuiClip.push(new ClipRect(tx,ty,w,h))){GuiStyle.panel(tx,ty,w,h,4,.5f,new Color(12,12,14));for(int i=0;i<lines.size();i++)font().drawString(lines.get(i),tx+7,ty+6+i*11,0xffd4d8e0);}
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
