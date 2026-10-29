package moon.visual.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import moon.visual.shader.ShaderSourceLoader.ResourceAccess;

/** hO normal null-hN path. ResourceAccess replaces Class.getResourceAsStream.
 * Migration change: close the acquired stream; the original IOUtils call leaked it.
 */
public final class BitmapAtlasLoader {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final ResourceAccess resources;
    public BitmapAtlasLoader(ResourceAccess resources) { this.resources=resources; }
    public BitmapAtlasData load(String name) {
        if(BitmapAtlasData.getSentinel()!=null)
            throw new UnsupportedOperationException("NEEDS_MANUAL_REVIEW: hO non-null hN path");
        try(InputStream input=resources.open("/assets/minecraft/bit/"+name+".json")) {
            if(input==null) throw new RuntimeException("Failed to load font file "+name);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[4096];
            for(int count;(count=input.read(buffer))!=-1;) bytes.write(buffer,0,count);
            return GSON.fromJson(new String(bytes.toByteArray(),StandardCharsets.UTF_8),BitmapAtlasData.class);
        } catch(IOException failure) { throw new RuntimeException(failure); }
    }
}
