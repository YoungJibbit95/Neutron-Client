package me.youngjibbit.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.litarvan.openauth.microsoft.MicrosoftAuthResult;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class Account {
    public enum Provider { OFFLINE, MICROSOFT, THEALTENING }
    private String id, email, password, name;
    private boolean banned;
    private Provider provider;
    private String microsoftAccessToken, microsoftProfileId;
    private JsonObject template = new JsonObject();

    public Account(String email, String password, String name) {
        this(email, password, name, inferProvider(email, password, null, null));
    }

    public Account(String email, String password, String name, Provider provider) {
        if (email == null || email.trim().isEmpty() || provider == null) throw new IllegalArgumentException("Account identity is required.");
        this.email = email.trim();
        this.password = password;
        this.name = name == null || name.isEmpty() ? this.email : name;
        this.provider = provider;
        this.id = legacyId();
    }

    public Account(MicrosoftAuthResult result) {
        this(result.getProfile().getName(), null, result.getProfile().getName(), Provider.MICROSOFT);
        microsoftAccessToken = result.getAccessToken();
        microsoftProfileId = result.getProfile().getId();
    }

    public Account() { }
    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public boolean isBanned() { return banned; }
    public String getMicrosoftAccessToken() { return microsoftAccessToken; }
    public String getMicrosoftProfileId() { return microsoftProfileId; }
    public void setName(String name) { this.name = name; }
    public void setBanned(boolean banned) { this.banned = banned; }
    public void setMicrosoftAccessToken(String token) { microsoftAccessToken = token; }
    public void setMicrosoftProfileId(String profileId) { microsoftProfileId = profileId; }

    /** First entry wins for the same provider and principal; passwords are never merged. */
    public String identityKey() {
        return provider.name() + ":" + (provider == Provider.THEALTENING ? email : email.toLowerCase(Locale.ROOT));
    }
    private String legacyId() {
        return UUID.nameUUIDFromBytes(identityKey().getBytes(StandardCharsets.UTF_8)).toString();
    }

    public JsonObject toJson() {
        JsonObject json = template.deepCopy();
        json.addProperty("id", id);
        json.addProperty("provider", provider.name());
        json.addProperty("email", email);
        json.addProperty("password", password);
        json.addProperty("name", name);
        json.addProperty("banned", banned);
        json.addProperty("microsoftAccessToken", microsoftAccessToken);
        json.addProperty("microsoftProfileId", microsoftProfileId);
        return json;
    }

    public void fromJson(JsonObject json) {
        String loadedEmail = text(json, "email");
        String loadedPassword = text(json, "password");
        String loadedName = text(json, "name");
        String token = text(json, "microsoftAccessToken");
        String profile = text(json, "microsoftProfileId");
        if (loadedEmail == null || loadedEmail.trim().isEmpty()) throw new IllegalArgumentException("Missing account identity.");
        String providerName = text(json, "provider");
        Provider loadedProvider = providerName == null ? inferProvider(loadedEmail, loadedPassword, token, profile) : Provider.valueOf(providerName);
        JsonElement ban = json.get("banned");
        if (ban != null && (!ban.isJsonPrimitive() || !ban.getAsJsonPrimitive().isBoolean())) throw new IllegalArgumentException("Invalid account flag.");
        String loadedId = text(json, "id");
        if (loadedId != null) UUID.fromString(loadedId);
        email = loadedEmail.trim();
        password = loadedPassword;
        name = loadedName == null || loadedName.isEmpty() ? email : loadedName;
        provider = loadedProvider;
        banned = ban != null && ban.getAsBoolean();
        microsoftAccessToken = token;
        microsoftProfileId = profile;
        id = loadedId == null ? legacyId() : loadedId;
        template = json.deepCopy();
    }

    private static Provider inferProvider(String email, String password, String token, String profile) {
        if (token != null || profile != null) return Provider.MICROSOFT;
        if (email != null && email.toLowerCase(Locale.ROOT).endsWith("@alt.com")) return Provider.THEALTENING;
        return password == null || password.isEmpty() ? Provider.OFFLINE : Provider.MICROSOFT;
    }

    private static String text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid account field.");
        return value.getAsString();
    }
}
