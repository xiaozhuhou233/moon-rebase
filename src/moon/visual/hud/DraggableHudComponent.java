package moon.visual.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** fW, including its asymmetric persistence errors and nonnull-fZ File cast.
 * Files.writeString's UTF-8/REPORT semantics are also available on Java8 here.
 * The original FileReader is deliberately not given a new close/finally path.
 */
public abstract class DraggableHudComponent implements HudRenderer {
    public interface Persistence {
        void info(String message);
        void error(String message,Exception exception);
        Object getMarker(); // original r2.h()
    }
    private static final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private final String id;
    protected final HudRenderPipeline hud;
    private float x,y,width,height,contentX,contentY;

    public DraggableHudComponent(String id,HudRenderPipeline hud){this.id=id;this.hud=hud;}
    public abstract DraggableComponentProperties properties();
    public abstract boolean isEnabled();

    public void save(File directory,Persistence host) {
        File file=new File(directory,id+".json");
        String json=gson.toJson(new DraggableComponentData(x,y)); // serialization failures escape before try
        try {
            ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(json));
            byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);
            Files.write(file.toPath(),bytes,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
        } catch(IOException exception) {host.error("Failed to save component data of "+id,exception);}
    }
    public void load(File directory,Persistence host) {
        Object[] captured=DraggableComponentProperties.getSentinel();
        File file=new File(directory,id+".json");
        if(captured==null&&!file.exists()) {
            host.info("Failed to load component data of "+id+" as the component does not exist!");
            return; // original skips the marker query too
        }
        try {
            // Offset49 ->132 carries the File into a checkcast fY, bypassing
            // exists/open/parse. ClassCastException escapes the narrow catch.
            DraggableComponentData data=captured!=null?(DraggableComponentData)(Object)file:
                gson.fromJson(new FileReader(file),DraggableComponentData.class);
            x=data.getX();y=data.getY(); // null/syntax/runtime errors propagate
        } catch(FileNotFoundException exception) {host.error("Failed to load component data of "+id,exception);}
        if(host.getMarker()!=null)DraggableComponentProperties.setSentinel(new Object[1]);
    }
    public String getId(){return id;}
    public HudRenderPipeline getHud(){return hud;}
    public float getX(){return x;}public float getY(){return y;}
    public float getWidth(){return width;}public float getHeight(){return height;}
    public float getContentX(){return contentX;}public float getContentY(){return contentY;}
    public DraggableHudComponent setX(float value){x=value;return this;}
    public DraggableHudComponent setY(float value){y=value;return this;}
    public DraggableHudComponent setWidth(float value){width=value;return this;}
    public DraggableHudComponent setHeight(float value){height=value;return this;}
    public DraggableHudComponent setContentX(float value){contentX=value;return this;}
    public DraggableHudComponent setContentY(float value){contentY=value;return this;}
}
