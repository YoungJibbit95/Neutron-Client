package me.youngjibbit.account;

import me.youngjibbit.altmanager.ui.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import org.junit.Test;
import org.lwjgl.input.Keyboard;
import java.util.*;
import static org.junit.Assert.*;

public class AccountUiTest {
    private void contained(ClipRect parent,ClipRect child) {
        assertTrue(child.x>=parent.x);assertTrue(child.y>=parent.y);
        assertTrue(child.x+child.width<=parent.x+parent.width+.01f);
        assertTrue(child.y+child.height<=parent.y+parent.height+.01f);
    }
    @Test public void responsiveGeometryKeepsControlsAndAtLeastOneCardInsideTheWindow() {
        for(int[] size:new int[][]{{320,240},{427,240},{640,360},{640,480},{960,540},{1280,960}}) {
            AccountLayout l=new AccountLayout(size[0],size[1]);contained(new ClipRect(0,0,size[0],size[1]),l.window);
            for(ClipRect r:Arrays.asList(l.add,l.importButton,l.search,l.list,l.close,l.more,l.login,l.remove))contained(l.window,r);
            assertTrue(l.list.height>=50);assertTrue(l.search.y>=l.add.y+l.add.height);
            if(l.details.isEmpty())assertTrue(l.list.y+l.list.height<l.login.y);
            else {contained(l.window,l.details);assertTrue(l.list.x+l.list.width<l.details.x);}
            assertEquals(0,l.maxScroll(0),0);assertTrue(l.maxScroll(100)>0);
        }
    }
    @Test public void providerAndSearchFilteringPreservesStableIdentityAndNeverSearchesTokensOrPasswords() {
        Account offline=new Account("Aster","","Aster");
        Account microsoft=new Account("synthetic@example.invalid","private-password","Solstice");
        Account token=new Account("private-token@alt.com",null,"private-token@alt.com",Account.Provider.THEALTENING);
        List<Account> all=Arrays.asList(offline,microsoft,token);
        assertEquals(Collections.singletonList(offline),AccountBrowser.filter(all,1,""));
        assertEquals(Collections.singletonList(microsoft),AccountBrowser.filter(all,2,"SOLSTICE"));
        assertEquals(Collections.singletonList(microsoft),AccountBrowser.filter(all,0,"@example.invalid"));
        assertTrue(AccountBrowser.filter(all,0,"private-token").isEmpty());
        assertTrue(AccountBrowser.filter(all,0,"private-password").isEmpty());
        assertSame(offline,AccountBrowser.filter(all,0,"Aster").get(0));
    }
    @Test public void textEditingHandlesSelectionReplacementDeletionAndPasswordMasking() {
        AccountTextInput input=new AccountTextInput(true);input.focused=true;input.value(" p:ass ");
        assertEquals("*******",input.display());assertEquals(" p:ass ",input.value());
        input.key('a',Keyboard.KEY_A,true,false);input.key('x',Keyboard.KEY_X,false,false);assertEquals("x",input.value());
        input.key(' ',Keyboard.KEY_HOME,false,false);input.key('!',Keyboard.KEY_NONE,false,false);assertEquals("!x",input.value());
        input.key(' ',Keyboard.KEY_END,false,false);input.key(' ',Keyboard.KEY_LEFT,false,true);
        input.key(' ',Keyboard.KEY_BACK,false,false);assertEquals("!",input.value());
        input.key(' ',Keyboard.KEY_HOME,false,false);input.key(' ',Keyboard.KEY_DELETE,false,false);assertEquals("",input.value());
        input.value("hidden");input.focused=false;assertFalse(input.key('z',Keyboard.KEY_Z,false,false));assertEquals("hidden",input.value());
    }
}
