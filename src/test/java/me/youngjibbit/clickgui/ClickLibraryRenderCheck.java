package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.library.ConfigLibrary;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Real library and editor handlers in the Dropdown render fixture, with isolated config/theme files. */
public final class ClickLibraryRenderCheck {
    private static Minecraft mc;
    private static final File OUT=new File("dropdown-preview");
    private static class Library extends ClickLibraryScreen {
        GuiScreen opened;boolean ctrl,shift;
        Library(ConfigManager manager,ThemeCatalog catalog){super(null,false,manager,catalog);}
        @Override protected void open(GuiScreen screen){opened=screen;}
        @Override protected boolean ctrlDown(){return ctrl;}
        @Override protected boolean shiftDown(){return shift;}
        void click(int id)throws Exception{ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
        void name(String text)throws Exception{ClipRect r=nameBounds();mouseClicked((int)r.x+5,(int)r.y+8,0);ctrl=true;keyTyped('a',Keyboard.KEY_A);ctrl=false;type(text);}
        void search(String text)throws Exception{ClipRect r=searchBounds();mouseClicked((int)r.x+5,(int)r.y+8,0);ctrl=true;keyTyped('a',Keyboard.KEY_A);ctrl=false;type(text);if(text.isEmpty())key(Keyboard.KEY_BACK);}
        void type(String text)throws Exception{for(char c:text.toCharArray())keyTyped(c,Keyboard.KEY_NONE);}
        void key(int key)throws Exception{keyTyped(' ',key);}
        void select(int index)throws Exception{
            for(int i=0;i<50&&itemBounds(index).y+itemBounds(index).height>listBounds().y+listBounds().height;i++){scrollAt((int)listBounds().x+4,(int)listBounds().y+4,-120);frame(this);}
            ClipRect r=itemBounds(index).intersect(listBounds());require(!r.isEmpty(),"Library item must be reachable");mouseClicked((int)r.x+10,(int)r.y+8,0);
        }
    }
    private static class Editor extends ThemeEditorScreen {
        Editor(ThemeCatalog catalog){super(null,catalog,ThemeRuntime::select);}
        @Override protected void leave(){}
        void click(int id)throws Exception{revealControl(id);ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
        void swatch(int id)throws Exception{revealControl(id);ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width-12),(int)(r.y+10),0);}
        void pointer(int x,int y)throws Exception{mouseClicked(x,y,0);mouseReleased(x,y,0);}
        void key(int key)throws Exception{keyTyped(' ',key);}
    }
    public static void run(Minecraft minecraft)throws Exception{
        mc=minecraft;ThemeCatalog previous=ThemeCatalog.global();
        Path data=Files.createTempDirectory(OUT.toPath(),"library-fixture-");
        ThemeCatalog catalog=new ThemeCatalog(new ThemeStore(data.resolve("themes.json")));
        ThemeCatalog.install(catalog);catalog.select("Neutron");
        ConfigManager manager=new ConfigManager(data.resolve("configs"),Pulsar.INSTANCE.getMm().getAllModules(),()->false,message->{});
        try{
            boolean repeatBefore=Keyboard.isCreated()&&Keyboard.areRepeatEventsEnabled();
            Library screen=new Library(manager,catalog);resize(screen,854,480,1);frame(screen);snapshot("12-configs-empty.png");
            screen.name("Practice");screen.click(0);
            Path profile=data.resolve("configs/Practice.json");require(Files.isRegularFile(profile),"Save as must reach ConfigManager");
            screen.select(0);byte[] original=Files.readAllBytes(profile);
            screen.click(0);ConfigLibrary actions=(ConfigLibrary)field(screen,"configs");
            require(actions.pending()==ConfigLibrary.Pending.OVERWRITE,"Overwrite needs confirmation");frame(screen);snapshot("13-config-overwrite.png");
            screen.key(Keyboard.KEY_ESCAPE);require(Arrays.equals(original,Files.readAllBytes(profile)),"Cancelled overwrite changed config");
            screen.click(0);screen.key(Keyboard.KEY_RETURN);require(actions.pending()==ConfigLibrary.Pending.NONE,"Confirm must disarm");
            screen.click(1);require(actions.status().startsWith("Loaded"),"Load must use the named config");
            screen.name("../escape");screen.click(0);require(!actions.status().isEmpty()&&actions.pending()==ConfigLibrary.Pending.NONE,"Invalid config name");
            screen.name("Throwaway");screen.click(0);screen.click(2);screen.key(Keyboard.KEY_ESCAPE);
            require(Files.exists(data.resolve("configs/Throwaway.json")),"Delete cancellation");
            screen.click(2);screen.key(Keyboard.KEY_RETURN);require(!Files.exists(data.resolve("configs/Throwaway.json")),"Delete confirmation");
            screen.select(0);frame(screen);snapshot("14-configs-saved.png");
            byte[] beforeRename=Files.readAllBytes(profile);
            screen.click(4);screen.type("Practice renamed");resize(screen,320,224,1);frame(screen);snapshot("14-config-rename-small.png");
            require(Boolean.TRUE.equals(field(screen,"renaming"))&&"Practice renamed".equals(field(screen,"renameBuffer")),"Config rename draft survives compact resize");
            require(Arrays.equals(beforeRename,Files.readAllBytes(profile)),"Resize committed a rename draft");
            resize(screen,854,480,1);screen.key(Keyboard.KEY_RETURN);
            require(actions.pending()==ConfigLibrary.Pending.RENAME&&actions.target().equals("Practice renamed"),"Rename edits the selected name and waits for confirmation");
            frame(screen);snapshot("14-config-rename.png");screen.key(Keyboard.KEY_ESCAPE);
            require(Files.exists(profile)&&Arrays.equals(beforeRename,Files.readAllBytes(profile)),"Cancel rename preserves original file");
            screen.click(4);screen.type("Practice renamed");screen.key(Keyboard.KEY_NUMPADENTER);screen.key(Keyboard.KEY_NUMPADENTER);
            Path renamed=data.resolve("configs/Practice renamed.json");
            require(!Files.exists(profile)&&Arrays.equals(beforeRename,Files.readAllBytes(renamed)),"Confirmed rename preserves config bytes");
            require(field(screen,"name").equals("Practice renamed"),"Save name follows a confirmed rename");
            configLayouts(screen,actions,mc,catalog);
            screen.click(7);frame(screen);snapshot("15-themes.png");
            ClipRect firstSwatch=screen.itemBounds(0);int swatch=sample(firstSwatch.x+13,firstSwatch.y+12);
            boolean gradients=ClientSettings.current().gradients.getValue();String active=catalog.selectedReference();
            ClientSettings.current().gradients.setValue(false);ThemeRuntime.select("Neon");frame(screen);
            require(swatch==sample(firstSwatch.x+13,firstSwatch.y+12),"Preset swatches must keep literal colors when global theme/gradients change");
            ClientSettings.current().gradients.setValue(gradients);ThemeRuntime.select(active);
            catalog.preview(Theme.custom("custom:00000000-0000-0000-0000-000000000009","Dark ink fixture",
                    Arrays.asList(new java.awt.Color(30,50,90),new java.awt.Color(70,30,100)),false,false,.18f,
                    new java.awt.Color(10,14,21,220),new java.awt.Color(0,0,0,0),java.awt.Color.CYAN,.1f,5,.4f,1));
            frame(screen);require(brightControlText(screen.actionBounds(0))>8,"Theme browser chrome stays readable with transparent black theme text");
            snapshot("15b-themes-dark-text.png");catalog.cancelPreview();
            screen.select(1);screen.click(0);require(catalog.selectedReference().equals("Cotton_candy"),"Theme selection must reach runtime");
            screen.click(1);require(screen.opened instanceof ThemeEditorScreen,"Edit opens the real editor");
            screen.opened.onGuiClosed();require(!catalog.isPreviewing(),"Unused editor closes draft preview");
            for(int[] size:new int[][]{{854,480,2},{1101,733,3},{320,224,1}}){
                resize(screen,size[0],size[1],size[2]);frame(screen);
                ClipRect list=screen.listBounds(),name=screen.nameBounds();
                require(list.y+list.height<=name.y,"Library list must not overlap footer");
                for(int id:new int[]{0,1,2,3,6,7,8}){ClipRect r=screen.actionBounds(id);require(r.x>=0&&r.y>=0&&r.x+r.width<=screen.width&&r.y+r.height<=screen.height,"Library action bounds");}
            }
            snapshot("16-library-small.png");screen.onGuiClosed();
            if(Keyboard.isCreated())require(Keyboard.areRepeatEventsEnabled()==repeatBefore,"Library restores keyboard repeat after resized sessions");
            Editor editor=new Editor(catalog);editor.editTheme(Theme.Neutron,true);resize(editor,854,480,1);frame(editor);snapshot("17-theme-editor.png");
            editor.swatch(10);frame(editor);snapshot("18-color-picker.png");
            ThemeColorPicker picker=(ThemeColorPicker)field(editor,"picker");ClipRect hue=picker.control(1),alpha=picker.control(2);
            editor.pointer((int)(hue.x+hue.width*.35f),(int)hue.y+3);
            editor.pointer((int)(alpha.x+alpha.width*.5f),(int)alpha.y+3);
            require(catalog.current().getPrimaryGradientColor().getAlpha()>=125&&catalog.current().getPrimaryGradientColor().getAlpha()<=130,"Picker alpha reaches draft");
            require(!Files.exists(data.resolve("themes.json")),"Picker must not persist preview");
            editor.key(Keyboard.KEY_ESCAPE);require(catalog.current().getPrimaryGradientColor().equals(Theme.Neutron.getPrimaryGradientColor()),"Picker Escape restores original color");
            editor.click(3);editor.onGuiClosed();require(Files.isRegularFile(data.resolve("themes.json")),"Theme Save commits definition");
            String id=catalog.selectedReference();require(id.startsWith("custom:"),"Theme Save selects stable custom id");
            editor=new Editor(catalog);resize(editor,320,224,1);editor.revealControl(23);frame(editor);snapshot("19-theme-editor-small.png");
            require(editor.actionBounds(23).intersect(editor.formBounds()).height==editor.actionBounds(23).height,"Offscreen editor controls must scroll into view");
            editor.click(6);require(catalog.find(id)!=null,"First Delete must only ask for confirmation");
            editor.click(4);editor.onGuiClosed();require(catalog.find(id)!=null,"Cancel must preserve saved theme");
            require(glGetError()==GL_NO_ERROR,"Library/editor GL state");
            System.out.println("PASS [Pbuffer/FBO]: actual config save/load/overwrite/delete/cancel, theme library/runtime, literal swatches, HSV-alpha draft/cancel/save, compact scrolling");
        }finally{catalog.cancelPreview();ThemeCatalog.install(previous);}
    }
    private static void configLayouts(Library screen,ConfigLibrary actions,Minecraft mc,ThemeCatalog catalog)throws Exception {
        String longName="Long profile 012345678901234567890123456789012345678901234567890";
        require(actions.save(longName),"Long valid names remain supported");
        for(int i=0;i<18;i++)require(actions.save("Layout "+i),"Create scroll fixture");
        for(int[] size:new int[][]{{854,480,1},{854,480,2},{1101,733,3},{320,224,1}}){
            resize(screen,size[0],size[1],size[2]);frame(screen);
            List<ClipRect> controls=new ArrayList<>();controls.add(screen.listBounds());controls.add(screen.searchBounds());controls.add(screen.nameBounds());
            for(int id:new int[]{0,1,2,3,4,6,7,8})controls.add(screen.actionBounds(id));
            require(screen.listBounds().height>=26,"Compact configs retain a full list row");
            for(int i=0;i<controls.size();i++){
                ClipRect a=controls.get(i);require(a.width>0&&a.height>0&&a.x>=0&&a.y>=0&&a.x+a.width<=screen.width&&a.y+a.height<=screen.height,"Config control inside viewport");
                for(int j=i+1;j<controls.size();j++)require(a.intersect(controls.get(j)).isEmpty(),"Config list/fields/actions must not overlap");
            }
            if(ThemeMode.LEGACY.equals(ThemeMode.selected())) {
                require(screen.actionBounds(0).y>screen.nameBounds().y+screen.nameBounds().height,"Legacy toolbar sits below the editable name");
                if(screen.listBounds().width>=328)require(screen.itemBounds(0).y==screen.itemBounds(1).y,"Wide configs use a horizontal card grid");
            }
            screen.search("");screen.key(Keyboard.KEY_DOWN);frame(screen);
            snapshot("configs-grid-"+size[0]+"-scale"+size[2]+".png");
            screen.search("Long profile");screen.key(Keyboard.KEY_DOWN);frame(screen);
            require(actions.selected().equals(longName),"Keyboard selects long result without loading");
            snapshot("configs-layout-"+size[0]+"-scale"+size[2]+".png");
            screen.search("No matching config");frame(screen);snapshot("configs-no-match-"+size[0]+"-scale"+size[2]+".png");
            screen.search("");screen.key(Keyboard.KEY_TAB);require(((Integer)field(screen,"focus"))==3,"Search tabs to Refresh");
            screen.key(Keyboard.KEY_TAB);require(((Integer)field(screen,"focus"))==-2,"Refresh tabs to list");
            for(int id:new int[]{1,2,-3,4,0,8,6,7,-1}){screen.key(Keyboard.KEY_TAB);require(((Integer)field(screen,"focus"))==id,"Config focus order "+id);}
            screen.shift=true;
            for(int id:new int[]{7,6,8,0,4,-3,2,1,-2,3,-1}){screen.key(Keyboard.KEY_TAB);require(((Integer)field(screen,"focus"))==id,"Reverse config focus order "+id);}
            screen.shift=false;
            screen.key(Keyboard.KEY_DOWN);for(int i=0;i<30;i++)screen.key(Keyboard.KEY_DOWN);
            int index=actions.entries().size()-1;ClipRect row=screen.itemBounds(index),list=screen.listBounds();
            require(row.y>=list.y-.1f&&row.y+row.height<=list.y+list.height+.1f,"Keyboard reveals last config row");
        }
        Library unavailable=new Library(null,catalog);resize(unavailable,320,224,1);frame(unavailable);snapshot("configs-storage-unavailable.png");
        require(!((ConfigLibrary)field(unavailable,"configs")).available(),"Unavailable storage keeps real state");unavailable.onGuiClosed();
        resize(screen,854,480,1);screen.search("");
    }
    private static Object field(Object value,String name)throws Exception{
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static void resize(GuiScreen screen,int width,int height,int scale){
        mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;mc.getFramebuffer().createBindFramebuffer(width,height);
        ScaledResolution sr=new ScaledResolution(mc);screen.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
    }
    private static void frame(GuiScreen screen){
        mc.getFramebuffer().bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);GlStateManager.disableDepth();
        glViewport(0,0,mc.displayWidth,mc.displayHeight);glMatrixMode(GL_PROJECTION);glLoadIdentity();
        ScaledResolution sr=new ScaledResolution(mc);glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();glClearColor(.12f,.17f,.23f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        screen.drawScreen(-1,-1,0);
    }
    private static int sample(float x,float y){
        int scale=new ScaledResolution(mc).getScaleFactor();java.nio.ByteBuffer p=org.lwjgl.BufferUtils.createByteBuffer(4);
        glReadPixels((int)(x*scale),mc.displayHeight-1-(int)(y*scale),1,1,GL_RGBA,GL_UNSIGNED_BYTE,p);
        return (p.get(0)&255)<<16|(p.get(1)&255)<<8|(p.get(2)&255);
    }
    private static int brightControlText(ClipRect control){
        int scale=new ScaledResolution(mc).getScaleFactor();
        int x=(int)((control.x+5)*scale),top=(int)((control.y+4)*scale),width=Math.max(1,(int)((control.width-10)*scale)),height=16*scale;
        java.nio.ByteBuffer pixels=org.lwjgl.BufferUtils.createByteBuffer(width*height*4);
        glReadPixels(x,mc.displayHeight-top-height,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        int bright=0;for(int i=0;i<width*height;i++)if((pixels.get(i*4)&255)>175&&(pixels.get(i*4+1)&255)>175&&(pixels.get(i*4+2)&255)>175)bright++;
        return bright;
    }
    private static void snapshot(String file)throws Exception{
        java.nio.ByteBuffer pixels=org.lwjgl.BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);
        glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(mc.displayWidth,mc.displayHeight,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int p=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,0xff000000|(pixels.get(p)&255)<<16|(pixels.get(p+1)&255)<<8|(pixels.get(p+2)&255));}
        javax.imageio.ImageIO.write(image,"png",new File(OUT,file));
    }
    private static void require(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
