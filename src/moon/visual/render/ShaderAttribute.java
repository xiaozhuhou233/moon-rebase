package moon.visual.render;

import moon.visual.shader.ShaderProgram;

/** gy binding methods. GL enum/width are injected instead of claiming bj enum restoration. */
public final class ShaderAttribute {
    private final int size,type,byteWidth;
    private final String typeName;
    private String path;
    private ShaderProgram shader;
    private ShaderAttributeSet group;
    private int cachedLocation,stride,pointer,location;
    public ShaderAttribute(int size,int type,int byteWidth,String typeName,String path) {
        this.size=size;this.type=type;this.byteWidth=byteWidth;this.typeName=typeName;this.path=path;
    }
    public void initializeLocation(DrawServices s) {
        if(shader==null) { s.diagnostics.error("Failed to link attribute location, Shader is null! (Did the shader compile successfully?)");return; }
        location=s.backend.getAttributeLocation(shader.getProgram(),path);
        s.backend.enableAttribute(location);
        if(s.backend.getError()!=0) {
            s.diagnostics.error("Failed to link attribute location as the associated shader program does not contain this attribute or the program did not compile successfully!");return;
        }
        s.diagnostics.info("Successfully linked attribute location with path: {}",path);
    }
    public void specifyPointer(DrawServices s) {
        s.backend.attributePointer(location,size,type,false,stride,(long)pointer);
        int error=s.backend.getError();
        if(error==1280)s.diagnostics.error("Failed to specify attribute pointer for the attribute with path: {} as type is not an accepted value [type: {}]",path,typeName);
        if(error==1281)s.diagnostics.error("Failed to specify attribute pointer for the attribute with path: {} as the generated index, size or stride was wrongly defined (index higher than GL_MAX_VERTEX_ATTRIBS, type not an accepted value, stride is negative) [type: {}, stride: {}]",path,typeName,stride);
        if(error==1282)s.diagnostics.error("Failed to specify attribute pointer for the attribute with path: {} as the size or type is invalid [size: {}, type: {}, stride: {}, pointer: {}]",path,size,typeName,stride,pointer);
    }
    public int getSize(){return size;} public int getType(){return type;} public int getByteWidth(){return byteWidth;}
    public String getPath(){return path;} public ShaderAttribute setPath(String v){path=v;return this;}
    public ShaderProgram getShader(){return shader;} public ShaderAttribute setShader(ShaderProgram v){shader=v;return this;}
    public ShaderAttributeSet getGroup(){return group;} public ShaderAttribute setGroup(ShaderAttributeSet v){group=v;return this;}
    public int getStride(){return stride;} public ShaderAttribute setStride(int v){stride=v;return this;}
    public int getPointer(){return pointer;} public ShaderAttribute setPointer(int v){pointer=v;return this;}
    public int getLocation(){return location;} public ShaderAttribute setLocation(int v){location=v;return this;}
    public int getCachedLocation(){return cachedLocation;} public ShaderAttribute setCachedLocation(int v){cachedLocation=v;return this;}
}
