package moon.client;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import moon.visual.gui.*;

/** Destination format, not the recovered client's config format. Render-thread owned.
 * Snapshot polling observes original Bezier field setters and in-place choice maps
 * without changing their callback semantics. Lua retains its own config owner. */
public final class McpVisualConfigStore {
    private record Module(Map<String,SettingValue<?>> settings,BooleanSupplier enabled,Consumer<Boolean> enable){}
    private final Path file;
    private final Map<String,Module> modules=new LinkedHashMap<>();
    private JsonObject document=new JsonObject(),last;
    private boolean loaded,writable;
    private long nextPoll;
    public McpVisualConfigStore(Path file){this.file=file.toAbsolutePath();}
    public Path file(){return file;}
    public boolean writeProtected(){return !writable;}
    public void bind(String key,List<? extends SettingValue<?>> roots,BooleanSupplier enabled,Consumer<Boolean> enable){
        if(loaded)throw new IllegalStateException("Config already loaded");
        if((enabled==null)!=(enable==null))throw new IllegalArgumentException("Incomplete enabled binding");
        Map<String,SettingValue<?>> leaves=new LinkedHashMap<>();
        flatten(roots,leaves);
        if(modules.putIfAbsent(key,new Module(leaves,enabled,enable))!=null)throw new IllegalArgumentException("Duplicate module: "+key);
    }
    private static void flatten(List<? extends SettingValue<?>> roots,Map<String,SettingValue<?>> leaves){
        for(SettingValue<?> setting:roots){
            if(setting instanceof SettingGroup group){flatten(group.get(),leaves);continue;}
            switch(setting.getType()){
                case BUTTON -> { continue; }
                case BOOLEAN,COLOR,ENUM,FLOAT,INTEGER,STRING,MODULAR_LIST,MULTIPLE_CHOICE,BEZIER -> {}
                default -> throw new IllegalArgumentException("Unsupported persisted setting: "+setting.getType());
            }
            if(setting.getKey()==null||setting.getKey().isEmpty()||leaves.putIfAbsent(setting.getKey(),setting)!=null)
                throw new IllegalArgumentException("Empty/duplicate setting key: "+setting.getKey());
        }
    }
    /** Validate every recognized entry before any callback or live model changes.
     * Any failure leaves saving disabled, preserving the original user file. */
    public void load()throws IOException{
        if(loaded)throw new IllegalStateException("Config already loaded");loaded=true;
        if(!Files.exists(file)){last=snapshot();writable=true;return;}
        try{
            JsonObject parsed=object(new JsonParser().parse(Files.readString(file,StandardCharsets.UTF_8)));
            if(integer(parsed.get("version"))!=1)throw new IllegalArgumentException("Unsupported visual config version");
            JsonObject saved=object(parsed.get("modules"));
            boolean migrated=migrateLegacyEsp(saved);
            List<Runnable> apply=new ArrayList<>(),undo=new ArrayList<>();
            for(var moduleEntry:modules.entrySet()){
                if(!saved.has(moduleEntry.getKey()))continue;
                Module module=moduleEntry.getValue();JsonObject data=object(saved.get(moduleEntry.getKey()));
                if(data.has("enabled")&&module.enable()!=null){
                    boolean value=bool(data.get("enabled")),before=module.enabled().getAsBoolean();
                    apply.add(()->module.enable().accept(value));undo.add(()->module.enable().accept(before));
                }
                if(!data.has("settings"))continue;
                JsonObject settings=object(data.get("settings"));
                for(var entry:module.settings().entrySet())if(settings.has(entry.getKey())){
                    SettingValue<?> setting=entry.getValue();JsonObject value=object(settings.get(entry.getKey()));
                    if(setting instanceof ModularListSetting && "STRING".equals(string(value.get("type"))))migrated=true;
                    apply.add(decode(setting,value));undo.add(decode(setting,encode(setting)));
                }
            }
            int applied=0;
            try{for(Runnable action:apply){applied++;action.run();}}
            catch(RuntimeException failure){
                for(int i=applied-1;i>=0;i--)try{undo.get(i).run();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
                throw failure;
            }
            document=parsed;last=migrated?null:snapshot();writable=true;
        }catch(RuntimeException failure){throw new IOException("Visual config rejected; file preserved: "+file,failure);}
    }
    /** Merge the former destination-only Glow / ESP Box entries once. Keep both
     * source entries verbatim for recovery, and give an existing esp entry precedence.
     * Validation happens before replacing disabled feature flags or clamping old bounds. */
    private boolean migrateLegacyEsp(JsonObject saved){
        Module module=modules.get("esp");
        if(module==null||saved.has("esp")||!saved.has("glow")&&!saved.has("espbox"))return false;
        JsonObject glow=saved.has("glow")?object(saved.get("glow")):new JsonObject();
        JsonObject box=saved.has("espbox")?object(saved.get("espbox")):new JsonObject();
        validateLegacyEsp(module,glow,false);validateLegacyEsp(module,box,true);
        boolean glowOn=glow.has("enabled")&&bool(glow.get("enabled"));
        boolean boxOn=box.has("enabled")&&bool(box.get("enabled"));
        JsonObject settings=glow.has("settings")?copy(object(glow.get("settings"))):new JsonObject();
        JsonObject boxSettings=box.has("settings")?object(box.get("settings")):new JsonObject();
        for(var entry:boxSettings.entrySet())if(entry.getKey().equals("box")||entry.getKey().startsWith("box-"))
            settings.add(entry.getKey(),entry.getValue());
        boolean fill=legacyFlag(boxSettings,"box",true),edge=legacyFlag(boxSettings,"box-outline",true);
        settings.add("box",booleanValue(boxOn&&(fill||edge)));
        if(boxOn&&!fill&&edge)settings.add("box-opacity",floatValue(.01F,.01F));
        for(String key:List.of("box-opacity","box-width-expansion","box-height-expansion"))if(settings.has(key)){
            JsonObject old=object(settings.get(key));FloatSetting current=(FloatSetting)module.settings().get(key);
            float raw=Math.max(current.getMinimum(),finite(old.get("raw")));
            float value=(float)NumericSettingMath.round(NumericSettingMath.clamp(current.getStep()*Math.ceil(raw/current.getStep()),
                (double)current.getMinimum(),(double)current.getMaximum()),3);
            settings.add(key,floatValue(raw,value));
        }
        // Turning on a former box-only configuration must not also start 2D/glow/outline.
        if(boxOn&&!glowOn)for(String key:List.of("two-d","glow","outline"))settings.add(key,booleanValue(false));
        JsonObject merged=new JsonObject();merged.addProperty("enabled",glowOn||boxOn);merged.add("settings",settings);
        saved.add("esp",merged);return true;
    }
    private static boolean legacyFlag(JsonObject settings,String key,boolean fallback){
        return settings.has(key)?bool(object(settings.get(key)).get("value")):fallback;
    }
    private static JsonObject booleanValue(boolean value){
        JsonObject data=new JsonObject();data.addProperty("type","BOOLEAN");data.addProperty("value",value);return data;
    }
    private static JsonObject floatValue(float raw,float value){
        JsonObject data=new JsonObject();data.addProperty("type","FLOAT");data.addProperty("raw",raw);data.addProperty("value",value);return data;
    }
    private static void validateLegacyEsp(Module module,JsonObject data,boolean box){
        if(data.has("enabled"))bool(data.get("enabled"));
        if(!data.has("settings"))return;
        for(var entry:object(data.get("settings")).entrySet()){
            SettingValue<?> setting=module.settings().get(entry.getKey());if(setting==null)continue;
            JsonObject value=object(entry.getValue());
            if(box&&List.of("box-opacity","box-width-expansion","box-height-expansion").contains(entry.getKey())){
                if(!string(value.get("type")).equals("FLOAT"))throw new IllegalArgumentException("Legacy box float type");
                float raw=finite(value.get("raw")),v=finite(value.get("value"));
                float minimum=entry.getKey().equals("box-opacity")?0:-1;
                range(raw,minimum,1);range(v,minimum,1);
                float quantized=(float)NumericSettingMath.round(NumericSettingMath.clamp(.05F*Math.ceil(raw/.05F),(double)minimum,1D),3);
                if(Float.compare(v,quantized)!=0)throw new IllegalArgumentException("Legacy float raw/value mismatch");
            }else decode(setting,value);
        }
    }
    public JsonObject snapshotProfile(){return copy(snapshot());}
    public Runnable prepareProfile(JsonObject root){
        if(integer(root.get("version"))!=1)throw new IllegalArgumentException("Unsupported config version");JsonObject saved=object(root.get("modules"));List<Runnable> actions=new ArrayList<>();
        for(var entry:modules.entrySet())if(saved.has(entry.getKey())){Module module=entry.getValue();JsonObject data=object(saved.get(entry.getKey()));
            if(data.has("enabled")&&module.enable()!=null){boolean enabled=bool(data.get("enabled"));actions.add(()->module.enable().accept(enabled));}
            if(data.has("settings")){JsonObject settings=object(data.get("settings"));for(var setting:module.settings().entrySet())if(settings.has(setting.getKey()))actions.add(decode(setting.getValue(),object(settings.get(setting.getKey()))));}
        }
        return()->actions.forEach(Runnable::run);
    }
    /** Called once per client tick; snapshot at most once per second, including hidden leaves. */
    public void poll(long now)throws IOException{
        if(!writable||now<nextPoll)return;nextPoll=now+1000;save(false);
    }
    /** Force creation on clean shutdown, also catching edits since the last tick. */
    public boolean flush()throws IOException{return writable&&save(true);}
    private boolean save(boolean create)throws IOException{
        JsonObject current=snapshot();
        if(current.equals(last)&&(!create||Files.exists(file)))return false;
        JsonObject merged=copy(document);merged.addProperty("version",1);
        JsonObject saved=merged.has("modules")?object(merged.get("modules")):new JsonObject();merged.add("modules",saved);
        for(var entry:current.getAsJsonObject("modules").entrySet()){
            JsonObject data=saved.has(entry.getKey())?object(saved.get(entry.getKey())):new JsonObject();
            JsonObject value=entry.getValue().getAsJsonObject();
            // ClickGUI is an opener action; never persist/replay a synthetic enabled state.
            if(value.has("enabled"))data.add("enabled",value.get("enabled"));else data.remove("enabled");
            JsonObject settings=data.has("settings")?object(data.get("settings")):new JsonObject();
            for(var field:value.getAsJsonObject("settings").entrySet())settings.add(field.getKey(),field.getValue());
            data.add("settings",settings);saved.add(entry.getKey(),data);
        }
        byte[] bytes=(new GsonBuilder().setPrettyPrinting().create().toJson(merged)+"\n").getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"visual-config-",".tmp");
        try{
            try(FileChannel output=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())output.write(buffer);output.force(true);
            }
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        document=merged;last=current;return true;
    }
    private JsonObject snapshot(){
        JsonObject root=new JsonObject(),saved=new JsonObject();root.addProperty("version",1);root.add("modules",saved);
        for(var entry:modules.entrySet()){
            Module module=entry.getValue();JsonObject data=new JsonObject(),settings=new JsonObject();
            if(module.enabled()!=null)data.addProperty("enabled",module.enabled().getAsBoolean());
            for(var setting:module.settings().entrySet())settings.add(setting.getKey(),encode(setting.getValue()));
            data.add("settings",settings);saved.add(entry.getKey(),data);
        }
        return root;
    }
    private static JsonObject encode(SettingValue<?> setting){
        JsonObject value=setting.toJson();value.remove("name");value.addProperty("type",setting.getType().name());
        if(setting instanceof EnumSetting<?>)value.addProperty("value",((Enum<?>)setting.get()).name());
        if(setting instanceof FloatSetting number)value.addProperty("raw",number.getRawValue());
        if(setting instanceof IntegerSetting number)value.addProperty("raw",number.getRawValue());
        if(setting instanceof BezierSetting curve){value.addProperty("k",curve.getParameterK());value.addProperty("l",curve.getParameterL());}
        if(setting instanceof MultiChoiceSetting<?> choices){
            JsonArray names=new JsonArray();for(var option:choices.values())if(Boolean.TRUE.equals(choices.get().get(option)))names.add(new JsonPrimitive(option.name()));
            value.add("value",names);
        }
        // Reject invalid live values as well, rather than writing unreloadable data.
        decode(setting,value);return value;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Runnable decode(SettingValue<?> setting,JsonObject json){
        String savedType=string(json.get("type"));
        boolean legacyList=setting instanceof ModularListSetting && savedType.equals("STRING");
        if(!savedType.equals(setting.getType().name())&&!legacyList)throw new IllegalArgumentException("Type mismatch: "+setting.getKey());
        JsonElement value=json.get("value");
        if(setting instanceof BooleanSetting flag){boolean v=bool(value);return ()->flag.set(v);}
        if(setting instanceof ColorSetting color){int v=integer(value);return ()->color.set(v);}
        if(setting instanceof StringSetting text){String v=string(value);return ()->text.set(v);}
        if(setting instanceof ModularListSetting choices){
            String selected=string(value);List<String> available=new ArrayList<>();
            if(legacyList){available.addAll(choices.getAvailable());if(!available.contains(selected))available.add(selected);}
            else for(JsonElement item:array(json.get("available"))){
                String name=string(item);if(available.contains(name))throw new IllegalArgumentException("Duplicate list choice: "+name);available.add(name);
            }
            return ()->{
                choices.getAvailable().clear();choices.getAvailable().addAll(available);choices.set(selected);
                if(choices.getCollectionChange()!=null)choices.getCollectionChange().accept(selected);
            };
        }
        if(setting instanceof EnumSetting choice){
            String name=string(value);Enum selected=null;
            for(Object option:choice.values())if(((Enum)option).name().equals(name))selected=(Enum)option;
            if(selected==null)throw new IllegalArgumentException("Unknown enum: "+name);
            Enum result=selected;return ()->choice.set(result);
        }
        if(setting instanceof IntegerSetting number){
            int v=integer(value);float raw=finite(json.get("raw"));range(v,number.getMinimum(),number.getMaximum());range(raw,number.getMinimum(),number.getMaximum());
            int quantized=(int)NumericSettingMath.round(NumericSettingMath.clamp(number.getStep()*Math.ceil(raw/number.getStep()),(double)number.getMinimum(),(double)number.getMaximum()),2);
            if(quantized!=v)throw new IllegalArgumentException("Integer raw/value mismatch");
            return ()->{number.setFromDrag(raw);number.setRawValue(raw);};
        }
        if(setting instanceof FloatSetting number){
            float v=finite(value),raw=finite(json.get("raw"));range(v,number.getMinimum(),number.getMaximum());range(raw,number.getMinimum(),number.getMaximum());
            float quantized=(float)NumericSettingMath.round(NumericSettingMath.clamp(number.getStep()*Math.ceil(raw/number.getStep()),(double)number.getMinimum(),(double)number.getMaximum()),3);
            if(Float.compare(quantized,v)!=0)throw new IllegalArgumentException("Float raw/value mismatch");
            return ()->number.set(raw);
        }
        if(setting instanceof MultiChoiceSetting choices){
            Set<String> selected=new HashSet<>(),known=new HashSet<>();for(Object option:choices.values())known.add(((Enum)option).name());
            for(JsonElement element:array(value)){String name=string(element);if(!known.contains(name)||!selected.add(name))throw new IllegalArgumentException("Unknown/duplicate choice: "+name);}
            return ()->{for(Object option:choices.values())choices.setSelected((Enum)option,selected.contains(((Enum)option).name()));};
        }
        if(setting instanceof BezierSetting curve){
            float begin=finite(json.get("begin")),end=finite(json.get("end")),k=finite(json.get("k")),l=finite(json.get("l"));range(begin,0,1);range(end,0,1);
            JsonArray points=array(json.get("points"));if(points.size()!=2)throw new IllegalArgumentException("Bezier requires two control points");
            float[][] parsed=new float[2][2];
            for(int i=0;i<2;i++){JsonObject point=object(points.get(i));parsed[i][0]=finite(point.get("x"));parsed[i][1]=finite(point.get("y"));range(parsed[i][0],0,1);}
            // Control Y may exceed [0,1] in the original editor; preserve overshoot.
            return ()->{curve.setBegin(begin);curve.setEnd(end);curve.setParameterK(k);curve.setParameterL(l);curve.setPoints(new float[]{parsed[0][0],parsed[0][1]},new float[]{parsed[1][0],parsed[1][1]});};
        }
        throw new IllegalArgumentException("Unsupported setting: "+setting.getType());
    }
    private static JsonObject copy(JsonObject value){return object(new JsonParser().parse(value.toString()));}
    private static JsonObject object(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Expected object");return value.getAsJsonObject();}
    private static JsonArray array(JsonElement value){if(value==null||!value.isJsonArray())throw new IllegalArgumentException("Expected array");return value.getAsJsonArray();}
    private static JsonPrimitive primitive(JsonElement value){if(value==null||!value.isJsonPrimitive())throw new IllegalArgumentException("Expected primitive");return value.getAsJsonPrimitive();}
    private static boolean bool(JsonElement value){JsonPrimitive p=primitive(value);if(!p.isBoolean())throw new IllegalArgumentException("Expected boolean");return p.getAsBoolean();}
    private static String string(JsonElement value){JsonPrimitive p=primitive(value);if(!p.isString())throw new IllegalArgumentException("Expected string");return p.getAsString();}
    private static int integer(JsonElement value){JsonPrimitive p=primitive(value);if(!p.isNumber())throw new IllegalArgumentException("Expected integer");return new java.math.BigDecimal(p.getAsString()).intValueExact();}
    private static float finite(JsonElement value){JsonPrimitive p=primitive(value);if(!p.isNumber())throw new IllegalArgumentException("Expected number");float v=p.getAsFloat();if(!Float.isFinite(v))throw new IllegalArgumentException("Nonfinite number");return v;}
    private static void range(float value,float minimum,float maximum){if(value<minimum||value>maximum)throw new IllegalArgumentException("Setting outside bounds: "+value);}
}
