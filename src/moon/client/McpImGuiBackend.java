package moon.client;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import imgui.*;
import imgui.callback.*;
import imgui.flag.ImGuiKey;
import imgui.gl3.ImGuiImplGl3;
import imgui.internal.ImGuiContext;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;

/** Destination owner, not reconstructed kY/GLFW code. Uses the MCP event queue;
 * never installs, replaces or frees host GLFW callbacks. All methods are render-thread only.
 * Context/font/GL3 resources survive GUI close and are released with visual services. */
final class McpImGuiBackend implements AutoCloseable {
    private final Thread thread=Thread.currentThread();
    private final Path gameDir;
    private ImGuiContext context;
    private ImGuiImplGl3 renderer;
    private boolean closed,focused;
    private double lastTime;
    private float wheel;
    private final Map<Integer,ArrayDeque<Boolean>> keys=new HashMap<>(),buttons=new HashMap<>();
    private final boolean[] lastKeys=new boolean[512],lastButtons=new boolean[5];
    private final StringBuilder characters=new StringBuilder();
    McpImGuiBackend(Path gameDir){this.gameDir=Objects.requireNonNull(gameDir);}
    private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("ImGui requires render thread");if(closed)throw new IllegalStateException("ImGui owner closed");}
    // The binding reuses one mutable wrapper in getCurrentContext/createContext.
    // Retain pointer VALUES in distinct wrappers, never the shared return object.
    private static ImGuiContext current(){return new ImGuiContext(ImGui.getCurrentContext().ptr);}
    Scope activate(){check();if(context==null)throw new IllegalStateException("ImGui not initialized");return new Scope();}
    final class Scope implements AutoCloseable {
        private final ImGuiContext previous=current();
        private Scope(){ImGui.setCurrentContext(context);}
        public void close(){ImGui.setCurrentContext(previous);}
    }
    void ensureReady(){
        check();if(context!=null)return;
        ImGuiContext previous=current();
        try(GlState state=new GlState()){
          try{
            context=new ImGuiContext(ImGui.createContext().ptr);
            // CreateContext preserves an existing current context in this library.
            ImGui.setCurrentContext(context);
            ImGuiIO io=ImGui.getIO();io.setIniFilename(null);io.setLogFilename(null);
            io.setBackendPlatformName("moon_mcp_events");
            io.setGetClipboardTextFn(new ImStrSupplier(){public String get(){return GuiScreen.getClipboardString();}});
            io.setSetClipboardTextFn(new ImStrConsumer(){public void accept(String text){GuiScreen.setClipboardString(text);}});
            mapKeys(io);
            // 1.86.11's byte[] memory-font overload crashes at native teardown.
            // Cache the same resource bytes and use the verified file overload.
            Path font=fontFile();
            ImFont loaded=io.getFonts().addFontFromFileTTF(font.toString(),15F);
            if(loaded==null||!loaded.isValidPtr()||!io.getFonts().build())throw new IllegalStateException("Tahoma font build failed");
            renderer=new ImGuiImplGl3();renderer.init("#version 130");
            if(!GL11.glIsTexture(io.getFonts().getTexID()))throw new IllegalStateException("ImGui font texture missing");
          }catch(IOException failure){cleanupFailed(failure);throw new UncheckedIOException(failure);
          }catch(RuntimeException|Error failure){cleanupFailed(failure);throw failure;}
        }finally{ImGui.setCurrentContext(previous);}
    }
    private Path fontFile()throws IOException {
        byte[] bytes;
        try(InputStream in=McpImGuiBackend.class.getResourceAsStream("/moon/fonts/Tahoma.ttf")){
            if(in==null)throw new FileNotFoundException("/moon/fonts/Tahoma.ttf");bytes=in.readAllBytes();
        }
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        Path folder=gameDir.resolve("moon/cache/imgui");Files.createDirectories(folder);
        Path file=folder.resolve("Tahoma-"+hash+".ttf");
        if(!Files.isRegularFile(file)||!Arrays.equals(bytes,Files.readAllBytes(file)))Files.write(file,bytes);
        return file;
    }
    private void cleanupFailed(Throwable failure){
        try{if(renderer!=null)renderer.dispose();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}finally{renderer=null;}
        try{if(context!=null)ImGui.destroyContext(context);}catch(Throwable cleanup){failure.addSuppressed(cleanup);}finally{context=null;}
    }
    private static void mapKeys(ImGuiIO io){
        int[] map={Keyboard.KEY_TAB,Keyboard.KEY_LEFT,Keyboard.KEY_RIGHT,Keyboard.KEY_UP,Keyboard.KEY_DOWN,
            Keyboard.KEY_PRIOR,Keyboard.KEY_NEXT,Keyboard.KEY_HOME,Keyboard.KEY_END,Keyboard.KEY_INSERT,Keyboard.KEY_DELETE,
            Keyboard.KEY_BACK,Keyboard.KEY_SPACE,Keyboard.KEY_RETURN,Keyboard.KEY_ESCAPE,Keyboard.KEY_NUMPADENTER,
            Keyboard.KEY_A,Keyboard.KEY_C,Keyboard.KEY_V,Keyboard.KEY_X,Keyboard.KEY_Y,Keyboard.KEY_Z};
        if(map.length!=ImGuiKey.COUNT)throw new IllegalStateException("Unsupported ImGui key map");
        for(int i=0;i<map.length;i++)io.setKeyMap(i,map[i]);
    }
    private static void enqueue(Map<Integer,ArrayDeque<Boolean>> queue,boolean[] last,int index,boolean down){
        if(index<0||index>=last.length||last[index]==down)return;
        last[index]=down;queue.computeIfAbsent(index,k->new ArrayDeque<>()).addLast(down);
    }
    void keyEvent(int legacyKey,boolean down,char character){
        check();enqueue(keys,lastKeys,legacyKey,down);
        if(down&&character>=32&&character!=127)characters.append(character);
    }
    /** This MCP Mouse.getEventDWheel returns raw GLFW notch units (NOT legacy 120). */
    void mouseEvent(int button,boolean down,int rawWheel){check();enqueue(buttons,lastButtons,button,down);wheel+=rawWheel;}
    void clearInput(){
        check();keys.clear();buttons.clear();Arrays.fill(lastKeys,false);Arrays.fill(lastButtons,false);characters.setLength(0);wheel=0;lastTime=0;
        if(context!=null)try(Scope scope=activate()){
            ImGuiIO io=ImGui.getIO();io.clearInputKeys();io.clearInputCharacters();
            for(int i=0;i<5;i++)io.setMouseDown(i,false);
            io.setMouseWheel(0);io.setMouseWheelH(0);io.setKeyCtrl(false);io.setKeyShift(false);io.setKeyAlt(false);io.setKeySuper(false);
        }
    }
    private static void drain(Map<Integer,ArrayDeque<Boolean>> queue,java.util.function.BiConsumer<Integer,Boolean> setter){
        for(var it=queue.entrySet().iterator();it.hasNext();){var entry=it.next();setter.accept(entry.getKey(),entry.getValue().removeFirst());if(entry.getValue().isEmpty())it.remove();}
    }
    void newFrame(){
        check();ImGuiIO io=ImGui.getIO();long window=Display.getWindow();
        int[] w={0},h={0},fw={0},fh={0};GLFW.glfwGetWindowSize(window,w,h);GLFW.glfwGetFramebufferSize(window,fw,fh);
        io.setDisplaySize(w[0],h[0]);io.setDisplayFramebufferScale(w[0]>0?(float)fw[0]/w[0]:1,h[0]>0?(float)fh[0]/h[0]:1);
        double now=GLFW.glfwGetTime();io.setDeltaTime(lastTime>0&&now>lastTime?(float)(now-lastTime):1F/60F);lastTime=now;
        boolean active=GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)!=0;
        if(active!=focused){io.addFocusEvent(active);focused=active;if(!active)clearInput();}
        if(active){
            // Consume the same GLFW-to-legacy coordinates as the MCP event bridge.
            // Mouse's callback stores y = Display.height - nativeWindowY (no -1).
            io.setMousePos(Mouse.getX(),Display.getHeight()-Mouse.getY());
            drain(keys,io::setKeysDown);drain(buttons,io::setMouseDown);
            io.setKeyCtrl(io.getKeysDown(Keyboard.KEY_LCONTROL)||io.getKeysDown(Keyboard.KEY_RCONTROL));
            io.setKeyShift(io.getKeysDown(Keyboard.KEY_LSHIFT)||io.getKeysDown(Keyboard.KEY_RSHIFT));
            io.setKeyAlt(io.getKeysDown(Keyboard.KEY_LMENU)||io.getKeysDown(Keyboard.KEY_RMENU));
            io.setKeySuper(io.getKeysDown(Keyboard.KEY_LMETA)||io.getKeysDown(Keyboard.KEY_RMETA));
            if(!characters.isEmpty())io.addInputCharactersUTF8(characters.toString());characters.setLength(0);
            io.setMouseWheel(wheel);wheel=0;
        }else io.setMousePos(-Float.MAX_VALUE,-Float.MAX_VALUE);
    }
    void renderDrawData(){check();renderer.renderDrawData(ImGui.getDrawData());}
    int liveResources(){return context==null?0:2;}// One context + one aggregate GL3 owner; native objects verified separately.
    long contextPointer(){return context==null?0:context.ptr;}
    @Override public void close(){
        if(Thread.currentThread()!=thread)throw new IllegalStateException("ImGui requires render thread");if(closed)return;
        ImGuiContext previous=current();
        try(GlState state=new GlState()){
            if(context!=null){ImGui.setCurrentContext(context);try{if(renderer!=null)renderer.dispose();}finally{renderer=null;ImGui.destroyContext(context);}}
        }finally{if(context!=null&&previous.ptr==context.ptr)previous=new ImGuiContext(0);context=null;closed=true;keys.clear();buttons.clear();characters.setLength(0);ImGui.setCurrentContext(previous);}
    }
    /** kY remains unresolved. This explicit destination guard restores caller/MCP state. */
    static final class GlState implements AutoCloseable {
        final McpLuaRenderer.DrawState base=new McpLuaRenderer.DrawState();
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int[] viewport=new int[4],scissor=new int[4];
        final boolean stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST),clip=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlState(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);}
        void prepare(){GlStateManager.disableAlpha();GlStateManager.disableDepth();GlStateManager.disableCull();GL11.glDisable(GL11.GL_STENCIL_TEST);}
        public void close(){
            base.close();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            if(stencil)GL11.glEnable(GL11.GL_STENCIL_TEST);else GL11.glDisable(GL11.GL_STENCIL_TEST);
            if(clip)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        }
    }
}
