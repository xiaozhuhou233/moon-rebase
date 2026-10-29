package moon.visual.render;

import moon.visual.shader.ShaderProgram;
import moon.visual.shader.ShaderUniform;

/** Exact GL and memory operations used by g4/g5/gx/gy, with no implicit state restoration. */
public interface DrawBackend extends ShaderProgram.Backend,ShaderUniform.Backend,
        VertexArray.Backend,VertexBuffer.Backend,NativeVertexWriter.Memory {
    int getAttributeLocation(int program,String name);
    void enableAttribute(int index);
    void attributePointer(int index,int size,int type,boolean normalized,int stride,long pointer);
    void alphaFunc(int function,float reference);
    void bindTexture(int texture);
    void drawArrays(int primitive,int first,int count);
}
