package me.youngjibbit.altmanager;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.account.*;
import me.youngjibbit.altmanager.ui.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Actual account screens in a GPU pbuffer; synthetic accounts only, no network or world bootstrap. */
public final class AccountManagerRenderCheck {
    private static Minecraft mc;
    private static final File OUTPUT=new File("account-ui-preview");
    private static class Library extends GuiAltManager {
        int logins;GuiScreen opened;
        Library(AccountManager manager){super(manager);}
        @Override public void login(Account account){logins++;}
        @Override protected void openScreen(GuiScreen screen){opened=screen;}
        void click(ClipRect r)throws Exception{mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);}
        void key(int key)throws Exception{keyTyped(' ',key);}
        void search(String value){query(value);}
        void filter(int provider){providerFilter(provider);}
        int count(){return visibleAccounts().size();}
        AccountLayout layout(){return geometry();}
        void scroll(){wheel(1);}
    }
    private static class Form extends AccountFormScreen {
        GuiScreen opened;
        Form(GuiScreen parent,Mode mode){super(parent,mode);}
        @Override protected void openScreen(GuiScreen screen){opened=screen;}
        void fill(String name,String pass){identity.value(name);password.value(pass);}
        void accept(){submit();}
        String masked(){return password.display();}
    }
    public static void main(String[] args)throws Exception {
        OUTPUT.mkdirs();System.setProperty("user.home",new File(OUTPUT,"home").getAbsolutePath());
        File data=Files.createTempDirectory(OUTPUT.toPath(),"fixture-").toFile();
        try(PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(
                    new GameConfiguration.UserInformation(new Session("PreviewProfile","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1280,960,false,false),
                    new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                    new GameConfiguration.GameInformation(false,"Account UI render check"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
            set(mc,"framebufferMc",new Framebuffer(mc.displayWidth,mc.displayHeight,true));
            Pulsar.INSTANCE.fontManager=new FontManager();Theme.setDefaultTheme(Theme.SynthWave);
            AccountManager manager=new AccountManager(new File(data,"accounts"));set(Pulsar.INSTANCE,"accountManager",manager);
            Account a=manager.addAccount(new Account("Aster","","Aster"));
            manager.addAccount(new Account("synthetic@example.invalid","never-render-this","Solstice"));
            manager.addAccount(new Account("Nightfall","","Nightfall"));
            Account banned=manager.addAccount(new Account("Orbit","","Orbit"));banned.setBanned(true);
            manager.addAccount(new Account("private-token@alt.com",null,"private-token@alt.com",Account.Provider.THEALTENING));
            require(manager.save(),"Synthetic account persistence");
            Library library=new Library(manager);library.selectedAccountId=a.getId();library.currentAccount=a;
            resize(library,1280,960,2);RenderDiagnostics.enabled=true;RenderDiagnostics.reset();frame(library);snapshot("01-library.png");
            require(RenderDiagnostics.maskPasses==1&&RenderDiagnostics.blurPasses==4&&RenderDiagnostics.composites==1,"Effects must be composed once for the panel, not per row");
            require(library.count()==5,"Library count");
            library.search("solstice");require(library.count()==1,"Search actual screen");frame(library);snapshot("02-search.png");
            library.click(library.layout().login);require(library.logins==0,"Hidden selection remained an actionable sign-in target");
            library.search("no-matching-account");frame(library);snapshot("03-no-results.png");
            library.search("");library.filter(1);require(library.count()==3,"Offline provider filter");library.filter(0);
            library.click(library.layout().remove);frame(library);snapshot("04-remove-confirmation.png");library.key(Keyboard.KEY_ESCAPE);require(manager.getAccounts().size()==5,"Cancel removal changed data");
            library.click(library.layout().login);require(library.logins==1,"Sign-in activation must occur once");
            library.click(library.layout().row(0,0));library.click(library.layout().row(0,0));require(library.logins==2,"Double-click dispatch");
            Form add=new Form(library,AccountFormScreen.Mode.ADD);resize(add,1280,960,2);add.fill("Nova","synthetic-password");
            require(!add.masked().contains("synthetic"),"Password exposed in visible input");frame(add);snapshot("05-add-account.png");
            add.accept();require(manager.getAccounts().size()==6&&add.opened==library,"Add UI flow");require(manager.load(),"Saved add reload");add.onGuiClosed();
            Form direct=new Form(library,AccountFormScreen.Mode.DIRECT);resize(direct,1280,960,2);frame(direct);snapshot("06-direct-login.png");direct.onGuiClosed();
            resize(library,854,480,2);frame(library);snapshot("07-small.png");
            require(library.layout().list.height>=50,"Small layout loses all rows");
            resize(library,1280,960,4);frame(library);snapshot("08-scale4.png");
            resize(library,1280,960,2);Theme.setDefaultTheme(Theme.Cherry);frame(library);snapshot("09-cherry-theme.png");
            for(int i=0;i<30;i++)manager.addAccount(new Account("Preview"+i,"","Preview"+i));
            library.initGui();library.scroll();for(int i=0;i<8;i++)frame(library);snapshot("10-scrolling.png");
            RenderDiagnostics.reset();frame(library);
            require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.maskPasses==1&&RenderDiagnostics.blurPasses==4,"Account count caused per-row effect passes or framebuffer allocations");
            me.youngjibbit.theme.ThemeCatalog.global().preview(Theme.custom("custom:12345678-1234-1234-1234-123456789abc","Account preview",Arrays.asList(new java.awt.Color(25,210,230),new java.awt.Color(145,240,160)),true,false,.3f,
                    new java.awt.Color(24,28,34,220),java.awt.Color.WHITE,new java.awt.Color(25,210,230),.5f,8,.8f,1));
            require(Theme.getDefaultTheme().isCustom(),"Custom palette fixture did not activate the shared theme catalog");
            frame(library);snapshot("11-custom-theme.png");
            Form small=new Form(library,AccountFormScreen.Mode.ADD);resize(small,854,480,2);frame(small);snapshot("12-small-form.png");small.onGuiClosed();
            resize(library,1280,960,2);library.revealAccount(manager.getAccountById(a.getId()));frame(library);
            library.click(library.layout().remove);int before=manager.getAccounts().size();
            Method accept=GuiAltManager.class.getDeclaredMethod("confirmButton",boolean.class);accept.setAccessible(true);library.click((ClipRect)accept.invoke(library,true));
            require(manager.getAccounts().size()==before-1&&manager.getAccountById(a.getId())==null,"Confirmed UI removal");
            require(manager.load()&&manager.getAccountById(a.getId())==null,"Removal reload");
            require(glGetError()==GL_NO_ERROR,"OpenGL error after account UI");context.check();library.onGuiClosed();
            Pulsar.INSTANCE.fontManager.close();
        }
        System.out.println("PASS: account shader UI, search/filter, masked fields, add/save/reload, selection, confirmation cancel, sign-in dispatch, scroll, GUI scales, themes. No real authentication or game session started.");
    }
    private static void resize(GuiScreen screen,int width,int height,int scale) {
        screen.onGuiClosed();mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;
        mc.getFramebuffer().createBindFramebuffer(width,height);ScaledResolution sr=new ScaledResolution(mc);
        screen.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());mc.currentScreen=screen;
    }
    private static void frame(GuiScreen screen) {
        mc.getFramebuffer().bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);GlStateManager.disableDepth();GlStateManager.disableAlpha();
        glClearColor(.03f,.04f,.06f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        try(RenderState state=new RenderState()){state.screenSpace();screen.drawScreen(-1,-1,0);}glFinish();
    }
    private static void snapshot(String name)throws Exception {
        ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++) {
            int i=(y*mc.displayWidth+x)*4;
            image.setRGB(x,mc.displayHeight-y-1,0xff000000|(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));
        }
        ImageIO.write(image,"png",new File(OUTPUT,name));
    }
    private static void set(Object object,String name,Object value)throws Exception {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
