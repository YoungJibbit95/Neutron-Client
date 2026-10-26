package me.youngjibbit.theme;

import com.google.gson.*;
import com.google.gson.stream.*;
import me.youngjibbit.utils.Theme;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned definitions only. Selection belongs to the existing module/config setting. */
public class ThemeStore {
    public static final int VERSION=1;
    private final Path file;
    private byte[] expected;
    private boolean readOnly;
    private String error="";
    public ThemeStore(Path file){this.file=file.toAbsolutePath().normalize();}
    public boolean isReadOnly(){return readOnly;}
    public String getError(){return error;}
    public Path getFile(){return file;}
    public java.util.List<Theme> load(){
        try{
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)){expected=null;return Collections.emptyList();}
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>524288)throw new IOException("Unsupported theme file");
            expected=Files.readAllBytes(file);JsonObject root;
            try(JsonReader reader=new JsonReader(new StringReader(new String(expected,StandardCharsets.UTF_8)))){
                reader.setLenient(false);root=read(reader,0).getAsJsonObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing JSON data");
            }
            keys(root,"version","themes");if(integer(root.get("version"))!=VERSION)throw new IOException("Unsupported theme file version; file preserved");
            JsonArray array=root.getAsJsonArray("themes");if(array==null||array.size()>64)throw new IOException("Too many themes");
            java.util.List<Theme> result=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(JsonElement entry:array){JsonObject o=entry.getAsJsonObject();keys(o,"id","name","stops","vertical","animated","speed","background","text","accent","glowStrength","glowRadius","outlineStrength","outlineWidth");
                String id=string(o.get("id"));if(!ids.add(id))throw new IOException("Duplicate theme ID");JsonArray stops=o.getAsJsonArray("stops");if(stops==null||stops.size()!=2)throw new IOException("Two RGBA stops required");
                result.add(Theme.custom(id,string(o.get("name")),Arrays.asList(color(stops.get(0)),color(stops.get(1))),bool(o.get("vertical")),bool(o.get("animated")),number(o.get("speed")),color(o.get("background")),color(o.get("text")),color(o.get("accent")),number(o.get("glowStrength")),number(o.get("glowRadius")),number(o.get("outlineStrength")),number(o.get("outlineWidth"))));
            }return result;
        }catch(Exception ex){readOnly=true;error=ex.getMessage()==null?"Invalid theme file":ex.getMessage();return Collections.emptyList();}
    }
    public void save(Collection<Theme> themes)throws IOException{
        if(readOnly)throw new IOException("Theme library is read-only: "+error);
        if(themes.size()>64)throw new IOException("Theme limit is 64");
        // Do not overwrite a file replaced or modified since loading, including a newer external version.
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>524288||expected==null||!Arrays.equals(expected,Files.readAllBytes(file))){readOnly=true;error="Theme file changed externally; reload required";throw new IOException(error);}
        }else if(expected!=null){readOnly=true;error="Theme file removed externally; reload required";throw new IOException(error);}
        JsonObject root=new JsonObject();root.addProperty("version",VERSION);JsonArray array=new JsonArray();Set<String> ids=new HashSet<>();
        for(Theme theme:themes){if(!theme.isCustom()||!ids.add(theme.name()))throw new IOException("Invalid custom definition");JsonObject o=new JsonObject();o.addProperty("id",theme.name());o.addProperty("name",theme.getDisplayName());JsonArray stops=new JsonArray();for(Color c:theme.getGradientColors())stops.add(c.getRGB());o.add("stops",stops);o.addProperty("vertical",theme.isVertical());o.addProperty("animated",theme.isAnimated());o.addProperty("speed",theme.getAnimationSpeed());o.addProperty("background",theme.getBackground().getRGB());o.addProperty("text",theme.getTextColor().getRGB());o.addProperty("accent",theme.getAccentColor().getRGB());o.addProperty("glowStrength",theme.getGlowStrength());o.addProperty("glowRadius",theme.getGlowRadius());o.addProperty("outlineStrength",theme.getOutlineStrength());o.addProperty("outlineWidth",theme.getOutlineWidth());array.add(o);}
        root.add("themes",array);byte[] bytes=new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),".theme-",".tmp");
        try{try(FileOutputStream output=new FileOutputStream(temporary.toFile())){output.write(bytes);output.getFD().sync();}replace(temporary,file);expected=bytes;}finally{Files.deleteIfExists(temporary);}
    }
    protected void replace(Path temporary,Path target)throws IOException{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    private static JsonElement read(JsonReader r,int depth)throws IOException{
        if(depth>12)throw new IOException("Theme nesting limit");switch(r.peek()){
            case BEGIN_OBJECT:JsonObject o=new JsonObject();r.beginObject();while(r.hasNext()){String k=r.nextName();if(o.has(k))throw new IOException("Duplicate JSON field");o.add(k,read(r,depth+1));}r.endObject();return o;
            case BEGIN_ARRAY:JsonArray a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>1024)throw new IOException("Theme array limit");a.add(read(r,depth+1));}r.endArray();return a;
            case STRING:return new JsonPrimitive(r.nextString());case NUMBER:return new JsonPrimitive(new BigDecimal(r.nextString()));case BOOLEAN:return new JsonPrimitive(r.nextBoolean());default:throw new IOException("Invalid theme JSON");
        }
    }
    private static void keys(JsonObject object,String...keys)throws IOException{Set<String> expected=new HashSet<>(Arrays.asList(keys));if(object.size()!=expected.size())throw new IOException("Missing or unsupported theme fields");for(Map.Entry<String,JsonElement> e:object.entrySet())if(!expected.contains(e.getKey()))throw new IOException("Unsupported theme field");}
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected text");return e.getAsString();}
    private static boolean bool(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean");return e.getAsBoolean();}
    private static float number(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!Double.isFinite(e.getAsDouble()))throw new IllegalArgumentException("Expected finite number");return e.getAsFloat();}
    private static int integer(JsonElement e){number(e);return new BigDecimal(e.getAsString()).intValueExact();}
    private static Color color(JsonElement e){return new Color(integer(e),true);}
}
