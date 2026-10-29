package moon.visual.render;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/** LWJGL3 bridge; native memory remains owned by the NativeData provider. */
public final class LwjglVertexBackend implements VertexArray.Backend, VertexBuffer.Backend {
    @Override public int generateVertexArray() { return GL30.glGenVertexArrays(); }
    @Override public void bindVertexArray(int array) { GL30.glBindVertexArray(array); }
    @Override public void deleteVertexArray(int array) { GL30.glDeleteVertexArrays(array); }
    @Override public int generateBuffer() { return GL15.glGenBuffers(); }
    @Override public void bindBuffer(int target, int buffer) { GL15.glBindBuffer(target, buffer); }
    @Override public void allocateBuffer(int target, long bytes, int usage) { GL15.glBufferData(target, bytes, usage); }
    @Override public void uploadBuffer(int target, long offset, long bytes, long address) { GL15.nglBufferSubData(target, offset, bytes, address); }
    @Override public void deleteBuffer(int buffer) { GL15.glDeleteBuffers(buffer); }
}
