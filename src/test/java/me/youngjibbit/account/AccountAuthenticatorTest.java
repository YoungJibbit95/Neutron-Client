package me.youngjibbit.account;

import net.minecraft.util.Session;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import static org.junit.Assert.*;

/** The production account adapter has one Microsoft path: the system browser. */
public class AccountAuthenticatorTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    @Test public void addDirectAndSavedAccountsUseTheCompleteBrowserFlow() throws Exception {
        Account saved=new Account("saved@example.invalid","legacy-password","Saved",Account.Provider.MICROSOFT);
        saved.setMicrosoftProfileId(MicrosoftBrowserAuthTest.PROFILE);
        try(MicrosoftBrowserAuthTest.Services services=new MicrosoftBrowserAuthTest.Services()) {
            AccountAuthenticator authenticator=new AccountAuthenticator(services.auth());
            for(LoginRequest request:Arrays.asList(LoginRequest.microsoftBrowser(),LoginRequest.microsoftBrowser(false),
                    new LoginRequest(saved,true,true))) {
                assertEquals("BrowserPlayer",authenticator.authenticate(request,()->true).getUsername());
            }
            assertEquals("BrowserPlayer",authenticator.authenticate(LoginRequest.microsoftBrowser()).getUsername());
            assertEquals(20,services.calls.size());
            authenticator.cancel();
        }
    }

    @Test public void missingInvalidAndUnreadableConfigurationRequestSetupWithoutFallback() throws Exception {
        for(String contents:Arrays.asList("clientId=", "clientId=not-an-app-id", "clientId=\\uNOTHEX")) {
            Path file=temporary.newFolder().toPath().resolve("microsoft-auth.properties");
            Files.write(file,contents.getBytes(StandardCharsets.UTF_8));
            MicrosoftAuthConfig config=new MicrosoftAuthConfig(new Properties(),Collections.emptyMap(),file);
            MicrosoftBrowserAuth browser=new MicrosoftBrowserAuth(null,null,uri->fail("No browser before setup"),100,config);
            AccountAuthenticator authenticator=new AccountAuthenticator(browser);
            try { authenticator.authenticate(LoginRequest.microsoftBrowser(),()->true);fail(); }
            catch(LoginCoordinator.LoginUnavailableException expected) { assertTrue(expected.isSetupRequired()); }
            authenticator.cancel();
        }
    }

    @Test public void offlineAuthenticationDoesNotReadMicrosoftConfigurationOrOpenBrowser() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("microsoft-auth.properties");
        Files.write(file,"clientId=\\uNOTHEX".getBytes(StandardCharsets.UTF_8));
        MicrosoftAuthConfig config=new MicrosoftAuthConfig(new Properties(),Collections.emptyMap(),file);
        AccountAuthenticator authenticator=new AccountAuthenticator(new MicrosoftBrowserAuth(null,null,
                uri->fail("Offline login must not open a browser"),100,config));
        Session result=authenticator.authenticate(new LoginRequest(new Account("Offline","","Offline",Account.Provider.OFFLINE),false,false));
        assertEquals("Offline",result.getUsername());assertEquals("",result.getToken());
        authenticator.cancel();
        assertEquals("Offline",new AccountAuthenticator().authenticate(new LoginRequest(
                new Account("Offline","","Offline",Account.Provider.OFFLINE),false,false)).getUsername());
    }

    @Test public void cancelledAttemptCannotLaunchBrowserOrRequestSetup() throws Exception {
        AccountAuthenticator authenticator=new AccountAuthenticator(new MicrosoftBrowserAuth("",null,
                uri->fail("Cancelled login must not open a browser"),100));
        try { authenticator.authenticate(LoginRequest.microsoftBrowser(),()->false);fail(); }
        catch(LoginCoordinator.LoginUnavailableException expected) {
            assertFalse(expected.isSetupRequired());assertTrue(expected.getUserMessage().contains("cancelled"));
        }
    }
}
