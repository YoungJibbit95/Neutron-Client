package me.youngjibbit.altmanager;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.*;
import me.youngjibbit.altmanager.impl.*;
import me.youngjibbit.altmanager.thread.AccountLoginThread;
import me.youngjibbit.altmanager.ui.*;
import me.youngjibbit.clickgui.utils.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;
import static me.youngjibbit.altmanager.ui.AccountUi.*;

/** Client-themed account library. Authentication and persistence remain in the existing services. */
public class GuiAltManager extends GuiScreen {
    public static GuiAltManager INSTANCE;
    public Account currentAccount;
    public AccountLoginThread loginThread;
    public String selectedAccountId;
    private final AccountUi ui=new AccountUi();
    private final AccountTextInput search=new AccountTextInput(false);
    private AccountLayout layout;
    private List<Account> visible=Collections.emptyList();
    private int filter;
    private float scroll,targetScroll;
    private long lastFrame=System.nanoTime(),lastClick;
    private String lastClickId,status="Ready when you are.";
    private boolean menu;
    private Account pendingRemoval;
    private boolean pendingClear;
    private final AccountManager suppliedManager;

    public GuiAltManager() { this(null); }
    public GuiAltManager(AccountManager manager) {
        suppliedManager=manager;
        if(INSTANCE!=null) { currentAccount=INSTANCE.currentAccount;selectedAccountId=INSTANCE.selectedAccountId;loginThread=INSTANCE.loginThread; }
        INSTANCE=this;
    }
    protected AccountManager manager() { return suppliedManager==null?Pulsar.INSTANCE.getAccountManager():suppliedManager; }
    public AccountManager accountManager() { return manager(); }
    protected void openScreen(GuiScreen screen) { mc.displayGuiScreen(screen); }
    protected void closeScreen() { openScreen(new me.youngjibbit.gui.MainMenu()); }

    @Override public void initGui() {
        buttonList.clear();layout=new AccountLayout(width,height);
        if(Keyboard.isCreated())Keyboard.enableRepeatEvents(true);
        refresh();lastFrame=System.nanoTime();
    }
    private void refresh() {
        layout=new AccountLayout(width,height);
        visible=AccountBrowser.filter(manager().getAccounts(),filter,search.value());
        targetScroll=Math.max(0,Math.min(targetScroll,layout.maxScroll(visible.size())));
        scroll=Math.max(0,Math.min(scroll,layout.maxScroll(visible.size())));
        search.bounds(layout.search);
    }
    protected AccountLayout geometry() { return layout; }
    protected List<Account> visibleAccounts() { return Collections.unmodifiableList(visible); }
    protected void query(String value) { search.value(value);scroll=targetScroll=0;refresh(); }
    protected void providerFilter(int value) { filter=value;scroll=targetScroll=0;refresh(); }
    private Account selected() {
        for(Account account:visible)if(account.getId().equals(selectedAccountId))return manager().getAccountById(selectedAccountId);
        return null;
    }
    public void revealAccount(Account account) {
        selectedAccountId=account.getId();filter=0;query("");
        for(int i=0;i<visible.size();i++)if(visible.get(i).getId().equals(account.getId()))targetScroll=Math.min(layout.maxScroll(visible.size()),i*AccountLayout.ROW);
    }
    private boolean confirming() { return pendingRemoval!=null||pendingClear; }

    @Override public void drawScreen(int mx,int my,float partialTicks) {
        refresh();
        long now=System.nanoTime();float dt=Math.min(.05f,(now-lastFrame)/1e9f);lastFrame=now;
        scroll+=(targetScroll-scroll)*me.youngjibbit.utils.render.UiEffects.response(dt,20);
        ui.window(layout.window,()->drawLibrary(mx,my));
    }
    private void drawLibrary(int mx,int my) {
        ClipRect w=layout.window;
        try(GuiClip ignored=GuiClip.push(new ClipRect(w.x+1,w.y+1,w.width-2,w.height-2))) {
            round(new ClipRect(w.x+1,w.y+1,w.width-2,32),10,0xc70e1017);
            gradient(new ClipRect(w.x+18,w.y+31,w.width-36,1),1,.4f);
            medium(15).drawString(Pulsar.INSTANCE.getName().toUpperCase(Locale.ROOT),w.x+17,w.y+11,TEXT);
            text(" /  ACCOUNT LIBRARY",w.x+84,w.y+11,13,FAINT);
            button(layout.more,"...",mx,my,false,true,false);button(layout.close,"x",mx,my,false,true,false);
            if(layout.sidebar>0)drawSidebar(mx,my);
            medium(layout.compact?22:28).drawString("Accounts",layout.contentX,w.y+(layout.compact?37:45),TEXT);
            if(!layout.compact)text("Manage your saved profiles.",layout.contentX,w.y+72,15,MUTED);
            button(layout.importButton,"Import",mx,my,false,true,false);
            button(layout.add,w.width<440?"+ Add":"+ Add account",mx,my,true,true,false);
            search.draw("Search accounts...");
            if(layout.sidebar==0&&!layout.compact)drawFilters(mx,my);
            else if(!layout.compact)text("SAVED ACCOUNTS  /  "+visible.size(),layout.list.x,layout.list.y-15,13,FAINT);
            drawRows(mx,my);
            drawDetails(mx,my);
            String error=manager().getLastError();
            String message=!error.isEmpty()?"Account file needs attention. "+error:loginThread==null?status:loginThread.getStatus();
            int statusColor=!error.isEmpty()?RED:message.contains("&c")||message.contains("&4")?RED:message.contains("&a")?GREEN:MUTED;
            round(new ClipRect(layout.contentX,w.y+w.height-21,4,4),2,statusColor);
            text(fit(font(14),message,layout.contentWidth-14),layout.contentX+10,w.y+w.height-24,14,statusColor);
            if(menu)drawMenu(mx,my);
            if(confirming())drawConfirmation(mx,my);
        }
    }
    private ClipRect filterBounds(int index) {
        return layout.sidebar>0?new ClipRect(layout.window.x+10,layout.window.y+78+index*32,layout.sidebar-18,27)
                :new ClipRect(layout.search.x+index*(layout.search.width/3),layout.search.y+35,layout.search.width/3-5,22);
    }
    private void drawFilters(int mx,int my) {
        String[] labels={"All accounts","Offline","Microsoft"};
        for(int i=0;i<3;i++) {
            ClipRect r=filterBounds(i);boolean active=filter==i;
            if(active)gradient(r,5,.18f);else if(r.contains(mx,my))round(r,5,0xff232631);
            medium(15).drawString(labels[i],r.x+9,r.y+(r.height-medium(15).getHeight(labels[i]))/2,active?TEXT:MUTED);
            if(active&&layout.sidebar>0)round(new ClipRect(r.x+1,r.y+7,2,r.height-14),1,accent());
        }
    }
    private void drawSidebar(int mx,int my) {
        ClipRect w=layout.window;
        round(new ClipRect(w.x+1,w.y+34,layout.sidebar-1,w.height-35),8,0xb90c0f16);
        text("YOUR LIBRARY",w.x+18,w.y+58,12,FAINT);
        drawFilters(mx,my);
        line(w.x+17,w.y+184,layout.sidebar-32);
        text("QUICK ACCESS",w.x+18,w.y+200,12,FAINT);
        String[] labels={"Direct sign-in","Random account","Last used"};
        for(int i=0;i<3;i++) {
            ClipRect r=quickBounds(i);if(r.contains(mx,my))round(r,5,0xff222630);
            text(labels[i],r.x+8,r.y+7,15,MUTED);
        }
        if(w.height>370) {
            ClipRect card=new ClipRect(w.x+12,w.y+w.height-74,layout.sidebar-24,54);
            round(card,7,0xff191d27);text("CURRENT SESSION",card.x+9,card.y+8,10,FAINT);
            String session=mc.getSession()==null?"No session":mc.getSession().getUsername();
            text(fit(medium(15),session,card.width-16),card.x+8,card.y+25,15,TEXT);
        }
    }
    private ClipRect quickBounds(int index) { return new ClipRect(layout.window.x+10,layout.window.y+218+index*29,layout.sidebar-18,26); }

    private void drawRows(int mx,int my) {
        try(GuiClip ignored=GuiClip.push(layout.list)) {
            if(visible.isEmpty()) {
                float cy=layout.list.y+Math.max(2,(layout.list.height-80)/2);
                String title=manager().getAccounts().isEmpty()?"No accounts yet":"No matching accounts";
                center(title,new ClipRect(layout.list.x,cy,layout.list.width,24),18,TEXT);
                center(manager().getAccounts().isEmpty()?"Add a profile to get started.":"Try another name or provider.",new ClipRect(layout.list.x,cy+28,layout.list.width,20),14,MUTED);
            }
            for(int i=0;i<visible.size();i++) {
                ClipRect r=layout.row(i,scroll);if(r.intersect(layout.list).isEmpty())continue;
                Account a=visible.get(i);boolean active=a.getId().equals(selectedAccountId);
                GuiStyle.panel(r.x,r.y,r.width,r.height,8,active?1:r.contains(mx,my)?.55f:.1f,color(active?0xff202332:r.contains(mx,my)?0xff222630:0xff1a1e28));
                avatar(a,new ClipRect(r.x+9,r.y+9,32,32),active);
                float textWidth=r.width-64;
                medium(18).drawString(fit(medium(18),name(a),textWidth),r.x+51,r.y+9,TEXT);
                String note=provider(a)+(a.isBanned()?"  /  Marked banned":"");
                text(fit(font(13),note,textWidth),r.x+51,r.y+29,13,a.isBanned()?RED:MUTED);
                if(currentAccount!=null&&currentAccount.getId().equals(a.getId()))round(new ClipRect(r.x+r.width-13,r.y+11,5,5),2.5f,GREEN);
            }
        }
        float max=layout.maxScroll(visible.size());
        if(max>0) {
            float height=Math.max(18,layout.list.height*layout.list.height/(layout.list.height+max));
            round(new ClipRect(layout.list.x+layout.list.width-2,layout.list.y+scroll/max*(layout.list.height-height),2,height),1,0xff656b7e);
        }
    }

    private void drawDetails(int mx,int my) {
        Account account=selected();boolean exists=account!=null;
        ClipRect d=layout.details;
        if(!d.isEmpty()) {
            round(d,9,0xff191d27);
            if(exists) {
                avatar(account,new ClipRect(d.x+14,d.y+16,45,45),true);
                medium(20).drawString(fit(medium(20),name(account),d.width-28),d.x+14,d.y+74,TEXT);
                text(provider(account)+" profile",d.x+14,d.y+96,14,MUTED);
                line(d.x+14,d.y+122,d.width-28);
                if(d.height>245) {
                    text("IDENTITY",d.x+14,d.y+140,11,FAINT);
                    String identity=account.getProvider()==Account.Provider.THEALTENING?"Token kept private":account.getEmail();
                    text(fit(font(14),identity,d.width-28),d.x+14,d.y+158,14,MUTED);
                    String signIn=account.getProvider()==Account.Provider.OFFLINE?"Offline sign-in"
                            :account.getProvider()==Account.Provider.THEALTENING?"Provider unavailable"
                            :"Microsoft browser sign-in";
                    text(fit(font(14),account.isBanned()?"Marked as banned":signIn,d.width-28),d.x+14,d.y+183,14,account.isBanned()?RED:MUTED);
                }
            } else {
                center("Your next session",new ClipRect(d.x,d.y+58,d.width,24),18,TEXT);
                center("Select an account",new ClipRect(d.x,d.y+89,d.width,20),14,MUTED);
            }
        }
        button(layout.login,"Sign in",mx,my,true,exists,false);
        button(layout.remove,"Remove",mx,my,false,exists,true);
    }

    private static final String[] MENU={"Direct sign-in","Random account","Last used","Reload library","Microsoft browser","TheAltening","Clear library"};
    private ClipRect menuBounds() { return new ClipRect(layout.window.x+layout.window.width-180,layout.window.y+32,164,181); }
    private ClipRect menuRow(int index) { ClipRect m=menuBounds();return new ClipRect(m.x+5,m.y+6+index*24,m.width-10,23); }
    private void drawMenu(int mx,int my) {
        ClipRect m=menuBounds();GuiStyle.panel(m.x,m.y,m.width,m.height,8,.8f,color(0xff181c26));
        for(int i=0;i<MENU.length;i++) {
            ClipRect r=menuRow(i);if(r.contains(mx,my))round(r,4,0xff2a2e3d);
            text(MENU[i],r.x+7,r.y+6,15,i==6?RED:TEXT);
        }
    }
    private ClipRect confirmBounds() {
        ClipRect w=layout.window;float width=Math.min(324,w.width-28),height=Math.min(164,w.height-20);
        return new ClipRect(w.x+(w.width-width)/2,w.y+(w.height-height)/2,width,height);
    }
    private ClipRect confirmButton(boolean accept) {
        ClipRect c=confirmBounds();float w=(c.width-44)/2;return new ClipRect(c.x+16+(accept?w+12:0),c.y+c.height-45,w,28);
    }
    private void drawConfirmation(int mx,int my) {
        decoration(layout.window,10,0xc9080a10);
        ClipRect c=confirmBounds();GuiStyle.panel(c.x,c.y,c.width,c.height,9,1,color(0xff191d28));
        medium(23).drawString(pendingClear?"Clear your library?":"Remove this account?",c.x+16,c.y+19,TEXT);
        text(fit(font(16),pendingClear?"This removes every saved entry.":name(pendingRemoval),c.width-32),c.x+16,c.y+56,16,MUTED);
        text("Your current session stays active.",c.x+16,c.y+79,14,FAINT);
        button(confirmButton(false),"Cancel",mx,my,false,true,false);
        button(confirmButton(true),pendingClear?"Clear library":"Remove",mx,my,false,true,true);
    }

    @Override protected void mouseClicked(int mx,int my,int button) throws IOException {
        if(button!=0)return;refresh();
        if(confirming()) {
            if(confirmButton(false).contains(mx,my)){pendingRemoval=null;pendingClear=false;}
            else if(confirmButton(true).contains(mx,my))confirmRemoval();
            return;
        }
        if(menu) {
            for(int i=0;i<MENU.length;i++)if(menuRow(i).contains(mx,my)){menu=false;activateMenu(i);return;}
            menu=false;return;
        }
        if(layout.close.contains(mx,my)){closeScreen();return;}
        if(layout.more.contains(mx,my)){search.focused=false;menu=true;return;}
        if(layout.add.contains(mx,my)){openScreen(new GuiAddAlt(this));return;}
        if(layout.importButton.contains(mx,my)){showStatus("Choose an account text file...");AccountImport.chooseAndImport(this::showStatus);return;}
        if(!layout.compact)for(int i=0;i<3;i++)if(filterBounds(i).contains(mx,my)){providerFilter(i);return;}
        if(layout.sidebar>0)for(int i=0;i<3;i++)if(quickBounds(i).contains(mx,my)){activateMenu(i);return;}
        if(layout.login.contains(mx,my)&&selected()!=null){login(selected());return;}
        if(layout.remove.contains(mx,my)&&selected()!=null){pendingRemoval=selected();search.focused=false;return;}
        search.click(mx,my);
        if(layout.list.contains(mx,my))for(int i=0;i<visible.size();i++)if(layout.row(i,scroll).contains(mx,my)) {
            Account account=visible.get(i);
            if(!account.getId().equals(selectedAccountId))AccountLoginThread.cancelPending();
            selectedAccountId=account.getId();
            long now=System.nanoTime();
            if(account.getId().equals(lastClickId)&&now-lastClick<300_000_000L)login(account);
            lastClickId=account.getId();lastClick=now;return;
        }
    }
    private void activateMenu(int action) {
        switch(action) {
            case 0:openScreen(new GuiAltLogin(this));break;
            case 1:List<Account> eligible=manager().getNotBannedAccounts();if(!eligible.isEmpty())login(eligible.get(new Random().nextInt(eligible.size())));else showStatus("No eligible accounts.");break;
            case 2:if(manager().getLastAlt()!=null)login(manager().getLastAlt());else showStatus("No recently used account.");break;
            case 3:AccountLoginThread.cancelPending();boolean ok=manager().load();if(ok&&currentAccount!=null)currentAccount=manager().getAccountById(currentAccount.getId());showStatus(ok?"Accounts reloaded.":"Reload failed. The account file was retained.");break;
            case 4:loginThread=new AccountLoginThread(LoginRequest.microsoftBrowser(),null);loginThread.start();break;
            case 5:openScreen(new GuiAlteningLogin(this));break;
            case 6:pendingClear=true;break;
            default:break;
        }
    }
    private void confirmRemoval() {
        AccountLoginThread.cancelPending();
        if(pendingClear){manager().clear();currentAccount=null;selectedAccountId=null;}
        else if(pendingRemoval!=null) {
            manager().remove(pendingRemoval);
            if(currentAccount!=null&&currentAccount.getId().equals(pendingRemoval.getId()))currentAccount=null;
            if(pendingRemoval.getId().equals(selectedAccountId))selectedAccountId=null;
        }
        pendingClear=false;pendingRemoval=null;
        showStatus(manager().save()?"Saved account entry removed.":"Changed in memory; saving failed. The previous file was retained.");refresh();
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();int x=Mouse.getEventX()*width/mc.displayWidth,y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(!confirming()&&!menu&&wheel!=0&&layout.list.contains(x,y))wheel(wheel>0?-1:1);
    }
    protected void wheel(int direction) { targetScroll=Math.max(0,Math.min(layout.maxScroll(visible.size()),targetScroll+direction*36)); }
    @Override protected void keyTyped(char c,int key) throws IOException {
        if(confirming()) {
            if(key==Keyboard.KEY_ESCAPE){pendingClear=false;pendingRemoval=null;}
            else if(key==Keyboard.KEY_RETURN)confirmRemoval();
            return;
        }
        if(menu){if(key==Keyboard.KEY_ESCAPE)menu=false;return;}
        if(key==Keyboard.KEY_ESCAPE) {
            if(!search.value().isEmpty()){query("");return;}
            closeScreen();return;
        }
        boolean ctrl=Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        if(ctrl&&key==Keyboard.KEY_F){search.focused=true;return;}
        if(key==Keyboard.KEY_TAB){search.focused=!search.focused;return;}
        String before=search.value();
        if(search.key(c,key,ctrl,Keyboard.isCreated()&&Keyboard.isKeyDown(Keyboard.KEY_LSHIFT))){if(!before.equals(search.value())){targetScroll=scroll=0;refresh();}return;}
        if(key==Keyboard.KEY_RETURN){if(selected()!=null)login(selected());return;}
        if((key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)&&!visible.isEmpty()) {
            int index=-1;for(int i=0;i<visible.size();i++)if(visible.get(i).getId().equals(selectedAccountId))index=i;
            index=Math.max(0,Math.min(visible.size()-1,index+(key==Keyboard.KEY_UP?-1:1)));
            AccountLoginThread.cancelPending();selectedAccountId=visible.get(index).getId();
            targetScroll=Math.max(0,Math.min(layout.maxScroll(visible.size()),index*AccountLayout.ROW));
        }
    }
    public void showStatus(String message) { if(loginThread!=null)loginThread.cancelLogin();loginThread=null;status=message; }
    public void login(Account account) {
        if(account==null)return;selectedAccountId=account.getId();loginThread=new AccountLoginThread(account);loginThread.start();
    }
    @Override public void onGuiClosed() { if(loginThread!=null)loginThread.cancelLogin();menu=false;pendingRemoval=null;pendingClear=false;search.focused=false;ui.close();if(Keyboard.isCreated())Keyboard.enableRepeatEvents(false); }
}
