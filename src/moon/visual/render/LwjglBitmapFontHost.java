package moon.visual.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import moon.visual.shader.ShaderSourceLoader.ResourceAccess;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Migration owner, not an original class: lazy PNG textures shared by the five hI faces.
 * Use the same StateAccess as the game backend. Close on the render thread before the context.
 * PNGs use nearest filtering and clamp-to-edge; no claim about original resource-pack flags.
 */
public final class LwjglBitmapFontHost implements BitmapAtlasFont.Host,AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private final ResourceAccess resources;
    private final LwjglPostProcessBackend.StateAccess state;
    private final Map<String,Integer> textures=new LinkedHashMap<>();
    private boolean closed;
    public LwjglBitmapFontHost(ResourceAccess resources,LwjglPostProcessBackend.StateAccess state) {
        this.resources=resources; this.state=state;
    }
    private void requireOpen() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("Render thread required");
        if(closed) throw new IllegalStateException("Bitmap font textures closed");
    }
    public void enableTexture2D() { requireOpen(); state.enableTexture2D(); }
    public void enableBlend() { requireOpen(); state.enableBlend(); }
    public void bindTexture(int texture) { requireOpen(); state.bindTexture(texture); }
    public void bindResource(String path) {
        requireOpen();
        Integer texture=textures.get(path);
        if(texture==null) {
            texture=load("/assets/minecraft/"+path);
            textures.put(path,texture);
        }
        state.bindTexture(texture);
    }
    private int load(String resource) {
        BufferedImage image;
        try(InputStream input=resources.open(resource)) {
            if(input==null) throw new IOException("Missing bitmap font texture "+resource);
            image=ImageIO.read(input);
            if(image==null) throw new IOException("Invalid bitmap font PNG "+resource);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
        ByteBuffer pixels=MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(image.getWidth(),image.getHeight()),4));
        int texture=0;
        try {
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                int argb=image.getRGB(x,y);
                pixels.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>24));
            }
            pixels.flip();
            texture=GL11.glGenTextures();
            state.bindTexture(texture);
            GL11.glTexParameteri(3553,10241,9728);
            GL11.glTexParameteri(3553,10240,9728);
            GL11.glTexParameteri(3553,10242,33071);
            GL11.glTexParameteri(3553,10243,33071);
            GL11.glTexImage2D(3553,0,32856,image.getWidth(),image.getHeight(),0,6408,5121,pixels);
            return texture;
        } catch(RuntimeException|Error failure) {
            if(texture!=0) state.deleteTexture(texture);
            throw failure;
        } finally { MemoryUtil.memFree(pixels); }
    }
    public int liveTextures() { return textures.size(); }
    @Override public void close() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("Render thread required");
        if(closed) return;
        state.bindTexture(0);
        for(int texture:textures.values()) state.deleteTexture(texture);
        textures.clear();
        closed=true;
    }
}
