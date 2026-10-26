package me.youngjibbit.altmanager.ui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.*;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.altmanager.thread.AccountLoginThread;
import me.youngjibbit.clickgui.utils.ClipRect;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import static me.youngjibbit.altmanager.ui.AccountUi.*;

/** The three existing entry points share client controls, keyboard behavior, and password masking. */
public class AccountFormScreen extends GuiScreen {
    public enum Mode { ADD, DIRECT, TOKEN }
    protected final GuiScreen parent;
    protected final Mode mode;
    private final AccountUi ui=new AccountUi();
    protected final AccountTextInput identity=new AccountTextInput(false),password=new AccountTextInput(true),token=new AccountTextInput(true);
    private ClipRect panel,submit,back;
    private String status="";
    protected AccountLoginThread login;
    public AccountFormScreen(GuiScreen parent,Mode mode) { this.parent=parent;this.mode=mode; }
    protected AccountManager manager() { return parent instanceof GuiAltManager?((GuiAltManager)parent).accountManager():Pulsar.INSTANCE.getAccountManager(); }
    protected void openScreen(GuiScreen screen) { mc.displayGuiScreen(screen); }
    @Override public void initGui() {
        buttonList.clear();layout();identity.focused=mode!=Mode.TOKEN;token.focused=mode==Mode.TOKEN;
        if(Keyboard.isCreated())Keyboard.enableRepeatEvents(true);
    }
    private void layout() {
        float w=Math.min(410,width-28),h=Math.min(338,height-24);
        panel=new ClipRect((width-w)/2,(height-h)/2,w,h);
        boolean small=h<290;
        float first=panel.y+(small?65:99);
        identity.bounds(new ClipRect(panel.x+22,first,w-44,30));
        token.bounds(identity.bounds());
        password.bounds(new ClipRect(panel.x+22,first+(small?53:61),w-44,30));
        submit=new ClipRect(panel.x+22,panel.y+h-61,w-44,29);
        back=new ClipRect(panel.x+w-48,panel.y+14,27,23);
    }
    @Override public void drawScreen(int x,int y,float partialTicks) {
        layout();ui.window(panel,()->{
            gradient(new ClipRect(panel.x+22,panel.y+10,32,2),1,.8f);
            medium(27).drawString(mode==Mode.ADD?"Add an account":mode==Mode.TOKEN?"TheAltening":"Direct sign-in",panel.x+22,panel.y+27,TEXT);
            button(back,"x",x,y,false,true,false);
            boolean small=panel.height<290;
            if(!small)text(mode==Mode.ADD?"Keep your profiles together.":mode==Mode.TOKEN?"Provider token":"Use an offline profile or Microsoft in your browser.",panel.x+22,panel.y+62,15,MUTED);
            ClipRect field=identity.bounds();
            text(mode==Mode.TOKEN?"ACCOUNT TOKEN":"OFFLINE USERNAME",field.x,field.y-15,12,FAINT);
            if(mode==Mode.TOKEN) {
                token.draw("Paste your token");
                text("Token sign-in is currently unavailable.",field.x,field.y+45,14,MUTED);
                if(!small)text("Your existing entries remain saved.",field.x,field.y+66,14,FAINT);
            } else {
                identity.draw("Offline username");
                ClipRect p=password.bounds();button(p,"Sign in with Microsoft",x,y,true,true,false);
                if(!small)text("Microsoft opens your default browser. No password is stored.",field.x,p.y+42,13,FAINT);
            }
            boolean enabled=mode==Mode.TOKEN?!token.value().trim().isEmpty():!identity.value().trim().isEmpty();
            button(submit,mode==Mode.ADD?"Add to library":"Sign in",x,y,true,enabled,false);
            String message=login==null?status:login.getStatus();
            text(fit(font(13),message,panel.width-44),panel.x+22,panel.y+panel.height-22,13,message.contains("&c")||message.contains("&4")?RED:MUTED);
        });
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        if(button!=0)return;
        if(back.contains(x,y)){openScreen(parent);return;}
        if(submit.contains(x,y)){submit();return;}
        if(mode!=Mode.TOKEN&&password.bounds().contains(x,y)){submitMicrosoft();return;}
        if(mode==Mode.TOKEN)token.click(x,y);else identity.click(x,y);
    }
    @Override protected void keyTyped(char c,int key) throws IOException {
        if(key==Keyboard.KEY_ESCAPE){openScreen(parent);return;}
        if(key==Keyboard.KEY_TAB&&mode!=Mode.TOKEN){identity.focused=!identity.focused;return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){submit();return;}
        boolean ctrl=Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        boolean shift=Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        if(mode==Mode.TOKEN)token.key(c,key,ctrl,shift);else identity.key(c,key,ctrl,shift);

    }
    protected void submit() {
        try {
            if(mode==Mode.TOKEN) {
                if(token.value().trim().isEmpty())return;
                Account a=new Account(token.value().trim(),null,"TheAltening account",Account.Provider.THEALTENING);
                login=new AccountLoginThread(new LoginRequest(a,false,false),null);login.start();
            } else {
                if(identity.value().trim().isEmpty()){status="Enter an offline username.";return;}
                if(!identity.value().trim().matches("[a-zA-Z0-9_]{1,16}")) {
                    status="Use Microsoft browser sign-in for online accounts.";return;
                }
                Account a=new Account(identity.value(),"",identity.value(),Account.Provider.OFFLINE);
                if(mode==Mode.ADD) {
                    Account stored=manager().addAccount(a);boolean saved=manager().save();
                    if(parent instanceof GuiAltManager) {
                        GuiAltManager library=(GuiAltManager)parent;library.revealAccount(stored);
                        library.showStatus(!saved?"Account could not be saved. Check the account file.":stored==a?"Account added to your library.":"This account is already saved. Existing credentials were kept.");
                    }
                    openScreen(parent);
                } else {login=new AccountLoginThread(new LoginRequest(a,true,false),null);login.start();}
            }
        } catch(IllegalArgumentException invalid){login=null;status="Check the offline username before signing in.";}
    }
    protected void submitMicrosoft() {
        login=new AccountLoginThread(LoginRequest.microsoftBrowser(mode!=Mode.DIRECT),null);login.start();
    }
    @Override public void onGuiClosed() { if(login!=null)login.cancelLogin();ui.close();if(Keyboard.isCreated())Keyboard.enableRepeatEvents(false); }
}
