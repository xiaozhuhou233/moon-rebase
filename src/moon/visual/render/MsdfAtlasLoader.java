package moon.visual.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import moon.visual.shader.ShaderDiagnostics;
import moon.visual.shader.ShaderSourceLoader.ResourceAccess;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** h5 captured loading guard and original exception/state order. JSON is /engine/font/msdf/name.json; ResourceLocation(name.png)
 * resolves to /assets/minecraft/name.png. UTF-8 and stream closing are migration fixes.
 * ResourceAccess open/close IOExceptions become UncheckedIOException outside the parse catch.
 * Closing completes before provider construction; primary failures retain suppressed close failures.
 * The original did not close streams and used the default charset; these policies are explicit.
 * Stateful per-font object: each MsdfFont needs its own loader. The texture Host may be shared.
 */
public final class MsdfAtlasLoader {
    public interface Host {
        void bindResource(String resource);
        void bindTexture(int texture);
        void textureParameter(int target,int parameter,float value);
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final ResourceAccess resources;
    private final Host host;
    private final ShaderDiagnostics diagnostics;
    private final MarkerHooks markers;
    private MsdfGlyphProvider provider;
    private String textureResource;
    private MsdfFontData data;
    public MsdfAtlasLoader(ResourceAccess resources,Host host,ShaderDiagnostics diagnostics,MarkerHooks markers) {
        this.resources=resources;this.host=host;this.diagnostics=diagnostics;this.markers=markers;
    }
    public void load(String name) {
        String[] captured=MsdfGlyphProvider.getSentinel();
        boolean initialize;
        // Original open(57) is outside its parse catch; only the checked host IO boundary
        // is adapted here. Runtime exceptions and Errors retain identity and state.
        try(InputStream input=resources.open(String.format("/engine/font/msdf/%s",name+".json"))) {
            initialize=loadData(name,input,captured);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
        if(!initialize) return;
        provider=new MsdfGlyphProvider(this::getData,name);
        provider.initialize();
        if(markers.getMarker()!=null) MsdfGlyphProvider.setSentinel(new String[5]);
    }
    private boolean loadData(String name,InputStream input,String[] captured) {
        // The missing-resource diagnostic is also outside the original parse catch.
        if(captured!=null&&input==null) {
            diagnostics.error("Could not read msdf font file "+name+".json");
            return false;
        }
        try {
            if(captured!=null) {
                textureResource=name+".png";
                host.bindResource(textureResource);
                host.textureParameter(3553,10241,9729F);
                host.textureParameter(3553,10240,9729F);
                host.bindTexture(0);
                data=GSON.fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),MsdfFontData.class);
            }
            // Original 301 -> 307 -> 310 -> 314: diagnostic failure reaches this catch too.
            if(data==null) diagnostics.error("Could not parse font info "+name+".json");
        } catch(Exception failure) { diagnostics.error("Could not parse font info "+name+".json",failure); }
        return true;
    }
    public MsdfGlyphProvider getProvider() { return provider; }
    public String getTextureResource() { return textureResource; }
    public MsdfFontData getData() { return data; }
    public Host getHost() { return host; }
}
