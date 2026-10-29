package moon.visual.render;

import java.util.*;
import moon.visual.shader.LwjglShaderBackend;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Migration owner for resources created through this instance. Close before destroying the context. */
public final class LwjglDrawBackend extends LwjglShaderBackend implements DrawBackend,AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private final Set<Integer> arrays=new LinkedHashSet<>(),buffers=new LinkedHashSet<>(),shaders=new LinkedHashSet<>(),programs=new LinkedHashSet<>();
    private final Set<Long> allocations=new LinkedHashSet<>();
    private final LwjglPostProcessBackend.StateAccess state;
    private boolean closed;
    private final Deque<TransientMemoryScope> memoryScopes=new ArrayDeque<>();

    /** Destination GUI calls submit synchronous, non-retained draw tasks.
     * Only allocations made inside this scope are temporary; existing HUD/FBO tasks
     * stay owned by their original lifetime. Never create retained tasks here.
     */
    public TransientMemoryScope transientMemoryScope(){
        requireOpen();TransientMemoryScope scope=new TransientMemoryScope();memoryScopes.push(scope);return scope;
    }
    public final class TransientMemoryScope implements AutoCloseable {
        private final Set<Long> pending=new LinkedHashSet<>();
        private boolean ended;
        private TransientMemoryScope(){}
        @Override public void close(){
            if(Thread.currentThread()!=owner)throw new IllegalStateException("Render thread required");
            if(ended)return;
            if(closed){ended=true;return;}
            if(memoryScopes.peek()!=this)throw new IllegalStateException("Memory scopes must close in reverse order");
            for(long address:new ArrayList<>(pending))freeMemory(address);
            memoryScopes.pop();ended=true;
        }
    }
    public LwjglDrawBackend(boolean convertLegacyTextureCalls){this(convertLegacyTextureCalls,new LwjglPostProcessBackend.StateAccess(){});}
    public LwjglDrawBackend(boolean convertLegacyTextureCalls,LwjglPostProcessBackend.StateAccess state){super(convertLegacyTextureCalls);this.state=state;}
    public void requireOpen(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Render thread required");if(closed)throw new IllegalStateException("Backend closed");}
    @Override public int createShader(int type){requireOpen();int id=super.createShader(type);shaders.add(id);return id;}
    @Override public int createProgram(){requireOpen();int id=super.createProgram();programs.add(id);return id;}
    public int generateVertexArray(){requireOpen();int id=GL30.glGenVertexArrays();arrays.add(id);return id;}
    public void bindVertexArray(int id){GL30.glBindVertexArray(id);}
    public void deleteVertexArray(int id){GL30.glDeleteVertexArrays(id);arrays.remove(id);}
    public int generateBuffer(){requireOpen();int id=GL15.glGenBuffers();buffers.add(id);return id;}
    public void bindBuffer(int target,int id){GL15.glBindBuffer(target,id);}
    public void allocateBuffer(int target,long bytes,int usage){GL15.glBufferData(target,bytes,usage);}
    public void uploadBuffer(int target,long offset,long bytes,long address){GL15.nglBufferSubData(target,offset,bytes,address);}
    public void deleteBuffer(int id){GL15.glDeleteBuffers(id);buffers.remove(id);}
    public int getAttributeLocation(int program,String name){return GL20.glGetAttribLocation(program,name);}
    public void enableAttribute(int index){GL20.glEnableVertexAttribArray(index);}
    public void attributePointer(int index,int size,int type,boolean normalized,int stride,long pointer){GL20.glVertexAttribPointer(index,size,type,normalized,stride,pointer);}
    public void alphaFunc(int function,float reference){state.alphaFunc(function,reference);}
    public void bindTexture(int texture){state.bindTexture(texture);}
    public void drawArrays(int primitive,int first,int count){GL11.glDrawArrays(primitive,first,count);}
    public long allocateMemory(long bytes){requireOpen();long p=MemoryUtil.nmemAllocChecked(bytes);allocations.add(p);if(!memoryScopes.isEmpty())memoryScopes.peek().pending.add(p);return p;}
    public long reallocateMemory(long address,long bytes){requireOpen();if(!allocations.contains(address))throw new IllegalStateException("Unknown native allocation");long p=MemoryUtil.nmemReallocChecked(address,bytes);allocations.remove(address);allocations.add(p);for(TransientMemoryScope scope:memoryScopes)if(scope.pending.remove(address))scope.pending.add(p);return p;}
    public void freeMemory(long address){requireOpen();if(!allocations.remove(address))throw new IllegalStateException("Native allocation already freed or foreign");for(TransientMemoryScope scope:memoryScopes)scope.pending.remove(address);MemoryUtil.nmemFree(address);}
    public void putFloat(long address,float value){MemoryUtil.memPutFloat(address,value);}
    public int liveResources(){return arrays.size()+buffers.size()+shaders.size()+programs.size()+allocations.size();}
    public int liveNativeAllocations(){return allocations.size();}
    public boolean ownsMemory(long address){return allocations.contains(address);}
    @Override public void close(){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Render thread required");if(closed)return;
        GL20.glUseProgram(0);GL30.glBindVertexArray(0);GL15.glBindBuffer(34962,0);
        for(int id:arrays)GL30.glDeleteVertexArrays(id);for(int id:buffers)GL15.glDeleteBuffers(id);
        for(int id:programs)GL20.glDeleteProgram(id);for(int id:shaders)GL20.glDeleteShader(id);
        for(long p:allocations)MemoryUtil.nmemFree(p);
        arrays.clear();buffers.clear();programs.clear();shaders.clear();allocations.clear();memoryScopes.clear();closed=true;
    }
}
