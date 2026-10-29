package moon.visual.render;

import java.io.*;
import java.util.*;
import moon.visual.shader.ShaderProgram;

/** gC resource/layout/link lifecycle. The original gF/md parser is an injected boundary. */
public final class ShaderAttributeSet {
    public interface Parser { List<ShaderAttribute> parse(InputStream input) throws IOException; }
    private final DrawServices services;
    private final List<ShaderAttribute> attributes=new ArrayList<>();
    private final String data,path;
    private final ShaderProgram shader;
    public ShaderAttributeSet(String data,String path,ShaderProgram shader,DrawServices services) {
        this.data=data;this.path=path;this.shader=shader;this.services=services;
    }
    public void link() throws IOException {
        InputStream input=services.resources.open("/engine/attributes/"+data+".attr");
        if(input==null) { services.diagnostics.error("Unable to find attribute file {} in the engine package","/engine/attributes/"+data+".attr");return; }
        List<ShaderAttribute> parsed=services.attributeParser.parse(input);
        if(parsed==null) { services.diagnostics.error("Unable to parse attribute file {}","/engine/attributes/"+data+".attr");return; }
        attributes.addAll(parsed); // Repeated original initialization appends, never clears.
        int stride=0,offset=0;
        for(ShaderAttribute a:attributes)stride+=a.getSize()*a.getByteWidth();
        for(ShaderAttribute a:attributes) {
            a.setGroup(this).setShader(shader).setStride(stride).setPointer(offset);
            a.initializeLocation(services);offset+=a.getSize()*a.getByteWidth();
        }
    }
    public void specifyPointers() { for(ShaderAttribute a:attributes)a.specifyPointer(services); }
    public List<ShaderAttribute> getAttributes(){return attributes;}
    public String getData(){return data;} public String getPath(){return path;}
    public ShaderProgram getShader(){return shader;}
}
