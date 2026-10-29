package moon.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import dev.pier.lua.*;
import org.luaj.vm2.LuaValue;

/** Explicit named configurations. Lua enabled states are replayed only on an explicit load. */
public final class McpClientProfiles {
    private final McpVisualServices visual;
    private final Path directory;
    McpClientProfiles(McpVisualServices visual){this.visual=visual;directory=visual.mc.mcDataDir.toPath().resolve("moon/configs").toAbsolutePath();}
    private Path path(String name){
        if(!name.matches("[\\p{L}\\p{N} _-]{1,64}")||name.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])"))throw new IllegalArgumentException("Invalid config name");
        return directory.resolve(name+".json");
    }
    private static JsonObject object(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Expected object");return value.getAsJsonObject();}
    private static boolean bool(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean");return value.getAsBoolean();}
    private JsonObject luaSnapshot(){
        JsonObject saved=new JsonObject();Gson gson=new Gson();
        for(var module:LuaManager.get().getModulesSnapshot()){
            JsonObject data=new JsonObject(),settings=new JsonObject();data.addProperty("enabled",module.isEnabled());data.add("settings",settings);
            for(var setting:module.getSettings()){
                JsonObject value=new JsonObject();value.addProperty("type",setting.getType().name());value.add("value",gson.toJsonTree(setting.getValue()));settings.add(setting.getId(),value);
            }
            saved.add(module.getName().toLowerCase(Locale.ROOT),data);
        }
        return saved;
    }
    JsonObject snapshot(){
        LuaManager.get().init();JsonObject saved=new JsonObject();saved.addProperty("version",1);saved.add("visual",visual.config.snapshotProfile());saved.add("native",visual.nativeModules.configuration().snapshotProfile());saved.add("lua",luaSnapshot());return saved;
    }
    void save(String name)throws IOException{
        Path target=path(name);byte[] bytes=(new GsonBuilder().setPrettyPrinting().create().toJson(snapshot())+"\n").getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(directory);Path temporary=Files.createTempFile(directory,"profile-",".tmp");
        try{Files.write(temporary,bytes);try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temporary);}
    }
    private LuaValue value(LuaSetting setting,JsonObject data){
        if(!setting.getType().name().equals(data.get("type").getAsString()))throw new IllegalArgumentException("Lua setting type changed: "+setting.getId());
        JsonElement value=data.get("value");if(value==null||!value.isJsonPrimitive())throw new IllegalArgumentException("Invalid Lua setting value");
        var p=value.getAsJsonPrimitive();
        return switch(setting.getType()){
            case BOOLEAN->LuaValue.valueOf(bool(value));
            case TEXT->{if(!p.isString())throw new IllegalArgumentException("Expected text");yield LuaValue.valueOf(value.getAsString());}
            case INTEGER,COLOR->{
                if(!p.isNumber()||value.getAsBigDecimal().stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Expected integer");
                long n=value.getAsBigDecimal().longValueExact();long maximum=setting.getType()==LuaSetting.Type.COLOR?0xffffffffL:Integer.MAX_VALUE;
                if(n<Integer.MIN_VALUE||n>maximum)throw new IllegalArgumentException("Lua integer out of range");yield LuaValue.valueOf((double)n);
            }
            case FLOAT->{if(!p.isNumber()||!Float.isFinite(value.getAsFloat()))throw new IllegalArgumentException("Invalid Lua float");yield LuaValue.valueOf(value.getAsDouble());}
        };
    }
    private Runnable prepareLua(JsonObject saved){
        List<Runnable> values=new ArrayList<>(),states=new ArrayList<>();var modules=LuaManager.get().getModulesSnapshot();
        for(var module:modules){
            String id=module.getName().toLowerCase(Locale.ROOT);JsonObject data=saved.has(id)?object(saved.get(id)):new JsonObject();
            boolean enabled=data.has("enabled")&&bool(data.get("enabled"));
            if(enabled&&module.isFailed())throw new IllegalArgumentException("Lua must be reloaded after its error: "+module.getName());
            JsonObject settings=data.has("settings")?object(data.get("settings")):new JsonObject();
            for(var setting:module.getSettings())if(settings.has(setting.getId())){
                LuaValue parsed=value(setting,object(settings.get(setting.getId())));values.add(()->module.setSetting(setting.getId(),parsed));
            }
            states.add(()->{module.setEnabled(enabled);if(enabled&&!module.isEnabled())throw new IllegalStateException("Lua failed to enable: "+module.getName());});
        }
        return()->{for(var module:modules)module.setEnabled(false);values.forEach(Runnable::run);states.forEach(Runnable::run);};
    }
    void load(String name)throws IOException{
        LuaManager.get().init();JsonObject saved;
        try{saved=object(new JsonParser().parse(Files.readString(path(name),StandardCharsets.UTF_8)));if(saved.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported config version");if(!saved.has("lua"))saved.add("lua",new JsonObject());}
        catch(RuntimeException invalid){throw new IOException("Config rejected: "+invalid.getMessage(),invalid);}
        JsonObject before=snapshot();
        try{
            // Every value is checked before any toggle or setting callback is invoked.
            Runnable a=visual.config.prepareProfile(object(saved.get("visual"))),b=visual.nativeModules.configuration().prepareProfile(object(saved.get("native"))),c=prepareLua(object(saved.get("lua")));
            try{a.run();b.run();c.run();}
            catch(RuntimeException failure){
                for(Runnable undo:List.of(prepareLua(object(before.get("lua"))),visual.nativeModules.configuration().prepareProfile(object(before.get("native"))),visual.config.prepareProfile(object(before.get("visual")))))try{undo.run();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}throw failure;
            }
            visual.hud.pipeline.setDirty(true);visual.post.requestShadowUpdate();visual.post.requestBlurUpdate();
        }catch(RuntimeException invalid){throw new IOException("Config rejected: "+invalid.getMessage(),invalid);}
    }
    boolean command(String message){
        String text=message.stripLeading();if(!text.regionMatches(true,0,".config",0,7)||(text.length()>7&&!Character.isWhitespace(text.charAt(7))))return false;
        String[] parts=text.trim().split("\\s+",3);String action=parts.length>1?parts[1].toLowerCase(Locale.ROOT):"help";
        try{
            if(action.equals("list")){
                if(!Files.isDirectory(directory))LuaManager.get().chat("No saved configs.");
                else try(var files=Files.list(directory)){LuaManager.get().chat("Configs: "+String.join(", ",files.map(p->p.getFileName().toString()).filter(n->n.endsWith(".json")).map(n->n.substring(0,n.length()-5)).sorted().toList()));}
            }else if(parts.length==3&&(action.equals("save")||action.equals("load"))){
                String name=parts[2].trim();if(name.startsWith("\"")&&name.endsWith("\""))name=name.substring(1,name.length()-1);
                if(action.equals("save"))save(name);else load(name);LuaManager.get().chat((action.equals("save")?"Saved config: ":"Loaded config: ")+name);
            }else LuaManager.get().chat(".config save <name> | .config load <name> | .config list");
        }catch(IOException|RuntimeException failure){LuaManager.get().chat("Config error: "+failure.getMessage());}
        return true;
    }
    public static boolean handleCommand(String message){var v=McpVisualServices.current();return v!=null&&v.profiles!=null&&v.profiles.command(message);}
}
