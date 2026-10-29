package moon.client;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import moon.visual.hud.DraggableHudComponent;

/** Destination layout format. Original fW load/save semantics remain untouched.
 * Render-thread owner; late component registration is supported. Bad files are
 * preserved with saving disabled. Unknown component ids and metadata survive.
 */
final class McpHudLayoutStore {
    private final Path file;
    private final Map<String,DraggableHudComponent> components=new LinkedHashMap<>();
    private JsonObject document=new JsonObject(),last;
    private boolean loaded,writable;
    private long nextPoll;
    McpHudLayoutStore(Path file){this.file=file.toAbsolutePath();}
    Path file(){return file;}
    boolean writeProtected(){return !writable;}
    void load()throws IOException{
        if(loaded)throw new IllegalStateException("Layout already loaded");loaded=true;
        if(!Files.exists(file)){writable=true;return;}
        try{
            JsonObject parsed=object(new JsonParser().parse(Files.readString(file,StandardCharsets.UTF_8)));
            JsonElement version=parsed.get("version");
            if(version==null||!version.isJsonPrimitive()||!version.getAsJsonPrimitive().isNumber()||version.getAsDouble()!=1)
                throw new IllegalArgumentException("Unsupported HUD layout version");
            for(var entry:object(parsed.get("components")).entrySet()){
                if(entry.getKey().isEmpty())throw new IllegalArgumentException("Empty component id");
                JsonObject position=object(entry.getValue());coordinate(position.get("x"));coordinate(position.get("y"));
            }
            document=parsed;writable=true;
        }catch(RuntimeException failure){throw new IOException("HUD layout rejected; original file preserved: "+file,failure);}
    }
    void register(DraggableHudComponent component){
        if(!loaded)throw new IllegalStateException("Layout not loaded");String id=component.getId();
        if(id==null||id.isEmpty()||components.containsKey(id))throw new IllegalArgumentException("Empty/duplicate HUD component id: "+id);
        finite(component.getX());finite(component.getY());
        if(writable&&document.has("components")&&document.getAsJsonObject("components").has(id)){
            JsonObject saved=document.getAsJsonObject("components").getAsJsonObject(id);
            float x=coordinate(saved.get("x")),y=coordinate(saved.get("y"));
            float beforeX=component.getX(),beforeY=component.getY();
            try{component.setX(x);component.setY(y);}
            catch(RuntimeException failure){
                writable=false;
                try{component.setX(beforeX);component.setY(beforeY);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
                throw failure;
            }
        }
        components.put(id,component);
    }
    void unregister(DraggableHudComponent component){components.remove(component.getId(),component);}
    void poll(long now)throws IOException{if(writable&&now>=nextPoll){nextPoll=now+1000;save(false);}}
    boolean flush()throws IOException{return writable&&save(true);}
    private boolean save(boolean create)throws IOException{
        JsonObject current=new JsonObject();
        for(var entry:components.entrySet()){
            DraggableHudComponent component=entry.getValue();JsonObject position=new JsonObject();
            position.addProperty("x",finite(component.getX()));position.addProperty("y",finite(component.getY()));current.add(entry.getKey(),position);
        }
        if(current.equals(last)&&(!create||Files.exists(file)))return false;
        JsonObject merged=object(new JsonParser().parse(document.toString()));merged.addProperty("version",1);
        JsonObject saved=merged.has("components")?merged.getAsJsonObject("components"):new JsonObject();merged.add("components",saved);
        for(var entry:current.entrySet()){
            JsonObject position=saved.has(entry.getKey())?saved.getAsJsonObject(entry.getKey()):new JsonObject();
            position.add("x",entry.getValue().getAsJsonObject().get("x"));position.add("y",entry.getValue().getAsJsonObject().get("y"));saved.add(entry.getKey(),position);
        }
        byte[] bytes=(new GsonBuilder().setPrettyPrinting().create().toJson(merged)+"\n").getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"hud-layout-",".tmp");
        try{
            try(FileChannel output=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())output.write(buffer);output.force(true);
            }
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        document=merged;last=current;return true;
    }
    private static JsonObject object(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Expected layout object");return value.getAsJsonObject();}
    private static float coordinate(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected numeric HUD position");
        return finite(value.getAsFloat());
    }
    private static float finite(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite HUD position");return value;}
}
