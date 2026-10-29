package moon.visual.render;

import java.nio.FloatBuffer;
import org.lwjglx.util.vector.Matrix4f;

/** com.moon.gu, using the target MCP's renamed LWJGL vector math implementation. */
public final class MatrixTransforms {
    public MatrixTransforms() { }
    public static Matrix4f orthographic(float left,float right,float bottom,float top,float near,float far,
                                        DrawServices.MarkerHooks markers) {
        Matrix4f result=new Matrix4f();
        float x=-(right+left)/(right-left),y=-(top+bottom)/(top-bottom);
        Object[] captured=DrawTask.getSentinel();
        float z=-(far+near)/(far-near);
        result.m00=2.0F/(right-left); result.m11=2.0F/(top-bottom); result.m22=-2.0F/(far-near);
        result.m03=x; result.m13=y; result.m23=z;
        if(captured!=null)markers.setMarker("FUsTpc");
        return result;
    }
    public static Matrix4f scale(float x,float y,float z) {
        Matrix4f result=new Matrix4f(); result.m00=x;result.m11=y;result.m22=z;return result;
    }
    /** Original writes rows of LWJGL's field layout; deliberately not Matrix4f.store(). */
    public static void write(Matrix4f m,FloatBuffer output,DrawServices.MarkerHooks markers) {
        DrawTask.getSentinel();
        output.put(m.m00).put(m.m10).put(m.m20).put(m.m30);
        output.put(m.m01).put(m.m11).put(m.m21).put(m.m31);
        output.put(m.m02).put(m.m12).put(m.m22).put(m.m32);
        output.put(m.m03).put(m.m13).put(m.m23).put(m.m33);
        output.flip();
        if(markers.getMarker()!=null)DrawTask.setSentinel(new Object[3]);
    }
    public static Matrix4f read(FloatBuffer input) { Matrix4f result=new Matrix4f();result.load(input.duplicate());return result; }
}
