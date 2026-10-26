package me.youngjibbit.altmanager.ui;

import me.youngjibbit.account.Account;
import java.util.*;

/** Display-only filtering; provider tokens and credentials never become searchable UI text. */
public final class AccountBrowser {
    private AccountBrowser() { }
    public static List<Account> filter(List<Account> source,int provider,String query) {
        List<Account> visible=new ArrayList<>();String search=query.trim().toLowerCase(Locale.ROOT);
        for(Account account:source) {
            if(provider==1&&account.getProvider()!=Account.Provider.OFFLINE)continue;
            if(provider==2&&account.getProvider()!=Account.Provider.MICROSOFT)continue;
            String principal=account.getProvider()==Account.Provider.THEALTENING?"":account.getEmail();
            if((AccountUi.name(account)+" "+principal+" "+AccountUi.provider(account)).toLowerCase(Locale.ROOT).contains(search))visible.add(account);
        }
        return visible;
    }
}
