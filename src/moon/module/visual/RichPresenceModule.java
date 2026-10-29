package moon.module.visual;

import com.google.gson.*;
import java.io.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** jR / lh / li / lj / lk / lg, kept together. Original classes are DATA only.
 * Java24 IPC replaces arikia's native binding. The original application's bootstrap
 * configuration is not recovered: no guessed application ID or assets are sent.
 */
public final class RichPresenceModule implements ModuleRowWidget.Module,AutoCloseable {
    public record Image(String key,Supplier<String> text) {
        public Image { Objects.requireNonNull(key);Objects.requireNonNull(text); }
    }
    public record Config(long applicationId,Supplier<String> state,Supplier<String> description,
                         Image bigImage,Image smallImage,boolean autoRefresh,int refreshInterval) {
        public Config {
            if(applicationId<=0)throw new IllegalArgumentException("Invalid Discord application ID");
            Objects.requireNonNull(state);Objects.requireNonNull(description);
            if(autoRefresh&&refreshInterval<=0)throw new IllegalArgumentException("Refresh interval must be positive");
        }
    }
    public record Activity(String state,String description,long startMillis,String bigImage,String bigText,String smallImage,String smallText) {}
    public record Frame(int opcode,JsonObject body) {}
    @FunctionalInterface public interface Configuration { Config load()throws IOException; }
    @FunctionalInterface public interface Connector { Transport open()throws IOException; }
    public interface Transport extends AutoCloseable {
        Frame read()throws IOException;
        void write(Frame frame)throws IOException;
        void close()throws IOException;
    }
    private static final Gson GSON=new GsonBuilder().serializeNulls().create();
    private static final long SHUTDOWN_GRACE_MILLIS=250; // Destination lifecycle bound, not an original numeric constant.
    private final Configuration configuration;
    private final Connector connector;
    private final LongSupplier clock;
    private final Set<Session> sessions=ConcurrentHashMap.newKeySet();
    
    private final List<SettingValue<?>> settings;
    private volatile Session session;
    private boolean enabled,closed;
    private long startMillis,nextConnect;
    private volatile String lastError;

    public RichPresenceModule(MarkerHooks hooks,Configuration configuration) {
        this(hooks,configuration,RichPresenceModule::openDiscordPipe,System::currentTimeMillis);
    }
    public RichPresenceModule(MarkerHooks hooks,Configuration configuration,Connector connector,LongSupplier clock) {
        this.configuration=Objects.requireNonNull(configuration);this.connector=Objects.requireNonNull(connector);this.clock=Objects.requireNonNull(clock);
        settings=List.of();
    }
    public String displayName(){return "RichPresence";}
    public boolean enabled(){return enabled;}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public void toggle(){setEnabled(!enabled);}
    public boolean connected(){Session s=session;return s!=null&&s.ready&&!s.stopped;}
    public String lastError(){return lastError;}
    public int liveSessions(){return sessions.size();}
    public void setEnabled(boolean value) {
        if(closed&&value)throw new IllegalStateException("RichPresence is closed");
        if(enabled==value)return;enabled=value;
        if(value){startMillis=clock.getAsLong();nextConnect=0;tick();}
        else {Session old=session;session=null;if(old!=null)old.stop();}
    }
    /** Suppliers are evaluated on the client thread, never on the pipe reader. */
    public void tick() {
        if(!enabled||closed)return;
        long now=clock.getAsLong();Session current=session;
        if(current==null||current.stopped) {
            if(now<nextConnect)return;nextConnect=now+2000;
            try {
                Config config=configuration.load();
                if(config==null){lastError="Original Discord application configuration is not available";return;}
                current=new Session(config);session=current;lastError=null;current.start();
            }catch(IOException|RuntimeException failure){lastError=failure.toString();return;}
        }
        if(current.ready&&current.config.autoRefresh&&now>=current.nextRefresh) {
            current.nextRefresh=now+current.config.refreshInterval;
            try {current.send(activity(current.config,startMillis));}
            catch(RuntimeException failure){lastError=failure.toString();}
        }
    }
    public void refresh() {
        Session s=session;if(enabled&&s!=null&&s.ready&&!s.stopped)s.send(activity(s.config,startMillis));
    }
    private static Activity activity(Config config,long started) {
        return new Activity(config.state.get(),config.description.get(),started,
            config.bigImage==null?null:config.bigImage.key,config.bigImage==null?null:config.bigImage.text.get(),
            config.smallImage==null?null:config.smallImage.key,config.smallImage==null?null:config.smallImage.text.get());
    }
    public void close(){if(closed)return;setEnabled(false);closed=true;for(Session s:sessions)s.stop();}

    /** Destination-owned file format; unknown fields are left untouched. No file is created. */
    public static Configuration configurationFile(Path path) {
        return ()->{
            if(!Files.exists(path))return null;
            JsonObject object=new JsonParser().parse(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            long id=object.get("applicationId").getAsLong();
            String state=object.get("state").getAsString(),details=object.get("description").getAsString();
            return new Config(id,()->state,()->details,image(object,"bigImage"),image(object,"smallImage"),
                    object.get("autoRefresh").getAsBoolean(),object.get("refreshInterval").getAsInt());
        };
    }
    private static Image image(JsonObject object,String key) {
        if(!object.has(key)||object.get(key).isJsonNull())return null;
        JsonObject image=object.getAsJsonObject(key);String text=image.get("text").getAsString();
        return new Image(image.get("key").getAsString(),()->text);
    }
    private static Frame handshake(long applicationId) {
        JsonObject body=new JsonObject();body.addProperty("v",1);body.addProperty("client_id",Long.toString(applicationId));return new Frame(0,body);
    }
    private static Frame presence(Activity activity,long nonce) {
        JsonObject body=new JsonObject();body.addProperty("cmd","SET_ACTIVITY");body.addProperty("nonce",Long.toString(nonce));
        JsonObject args=new JsonObject();args.addProperty("pid",ProcessHandle.current().pid());body.add("args",args);
        if(activity==null)args.add("activity",JsonNull.INSTANCE);
        else {
            JsonObject value=new JsonObject();value.addProperty("state",activity.state);value.addProperty("details",activity.description);
            JsonObject timestamps=new JsonObject();timestamps.addProperty("start",activity.startMillis/1000);value.add("timestamps",timestamps);
            JsonObject assets=new JsonObject();
            if(activity.bigImage!=null){assets.addProperty("large_image",activity.bigImage);assets.addProperty("large_text",activity.bigText);}
            if(activity.smallImage!=null){assets.addProperty("small_image",activity.smallImage);assets.addProperty("small_text",activity.smallText);}
            if(assets.entrySet().size()>0)value.add("assets",assets);args.add("activity",value);
        }
        return new Frame(1,body);
    }
    private final class Session {
        final Config config;
        final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("Moon-RichPresence-write").factory());
        volatile Transport transport;
        volatile boolean ready,stopped,stopping;
        volatile long nextRefresh;
        long nonce;
        Session(Config config){this.config=config;}
        void start(){sessions.add(this);Thread.ofVirtual().name("Moon-RichPresence-read").start(this::readLoop);}
        void readLoop() {
            try {
                Transport opened=connector.open();transport=opened;
                if(stopping){opened.close();return;}
                opened.write(handshake(config.applicationId));
                while(!stopped) {
                    Frame frame=opened.read();
                    if(frame.opcode==2)throw new IOException("Discord closed IPC: "+frame.body);
                    if(frame.opcode==3){submit(()->opened.write(new Frame(4,frame.body)));continue;}
                    if(frame.opcode!=1)continue;
                    String event=frame.body.has("evt")&&!frame.body.get("evt").isJsonNull()?frame.body.get("evt").getAsString():"";
                    if("READY".equals(event)&&!ready){nextRefresh=clock.getAsLong()+config.refreshInterval;ready=true;}
                    if("ERROR".equals(event))lastError="Discord RPC error: "+frame.body.get("data");
                }
            }catch(IOException|RuntimeException failure){if(!stopping)lastError=failure.toString();}
            finally {finish();}
        }
        @FunctionalInterface interface Write {void run()throws IOException;}
        void submit(Write action) {
            if(stopped)return;
            try{writer.execute(()->{if(stopped)return;try{action.run();}catch(IOException|RuntimeException failure){if(!stopping)lastError=failure.toString();finish();}});}
            catch(RejectedExecutionException ignored){}
        }
        void send(Activity activity){submit(()->{if(ready&&!stopping)transport.write(presence(activity,++nonce));});}
        void stop() {
            if(stopping||stopped)return;stopping=true;
            submit(()->{try{if(ready)transport.write(presence(null,++nonce));}finally{finish();}});
            // Closing a blocked local pipe must not stall Minecraft's render thread.
            Thread.ofVirtual().name("Moon-RichPresence-close").start(()->{
                try{Thread.sleep(SHUTDOWN_GRACE_MILLIS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
                finish();
            });
        }
        void finish() {
            stopped=true;ready=false;
            Transport opened=transport;if(opened!=null)try{opened.close();}catch(IOException ignored){}
            writer.shutdownNow();sessions.remove(this);
        }
    }
    private static Transport openDiscordPipe()throws IOException {
        if(!System.getProperty("os.name","").startsWith("Windows"))throw new IOException("This host requires a Discord IPC connector for its OS");
        IOException last=null;
        for(int i=0;i<10;i++)try{return windowsPipe("discord-ipc-"+i);}catch(IOException failure){last=failure;}
        throw new IOException("Discord desktop IPC is unavailable",last);
    }
    public static Transport windowsPipe(String name)throws IOException {
        if(!name.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid local pipe name");
        return channelTransport(new WindowsPipe(name));
    }
    /** Java24 FFM, FILE_FLAG_OVERLAPPED: a waiting read must not serialize the writer.
     * This is ordinary local kernel32 IPC, with no old native client/DLL dependency.
     */
    private static final class WindowsPipe implements ByteChannel {
        private static final Linker LINKER=Linker.nativeLinker();
        private static final Arena LIBRARY=Arena.ofShared();
        private static final SymbolLookup KERNEL=SymbolLookup.libraryLookup("kernel32",LIBRARY);
        private static final MemoryLayout ERROR_LAYOUT=Linker.Option.captureStateLayout();
        private static final long ERROR_OFFSET=ERROR_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("GetLastError"));
        private static final ValueLayout.OfInt DWORD=ValueLayout.JAVA_INT;
        private static final AddressLayout PTR=ValueLayout.ADDRESS;
        private static MethodHandle function(String name,MemoryLayout result,MemoryLayout...args) {
            return LINKER.downcallHandle(KERNEL.find(name).orElseThrow(),FunctionDescriptor.of(result,args),Linker.Option.captureCallState("GetLastError"));
        }
        private static final MethodHandle OPEN=function("CreateFileW",PTR,PTR,DWORD,DWORD,PTR,DWORD,DWORD,PTR);
        private static final MethodHandle EVENT=function("CreateEventW",PTR,PTR,DWORD,DWORD,PTR);
        private static final MethodHandle READ=function("ReadFile",DWORD,PTR,PTR,DWORD,PTR,PTR);
        private static final MethodHandle WRITE=function("WriteFile",DWORD,PTR,PTR,DWORD,PTR,PTR);
        private static final MethodHandle RESULT=function("GetOverlappedResult",DWORD,PTR,PTR,PTR,DWORD);
        private static final MethodHandle CANCEL=function("CancelIoEx",DWORD,PTR,PTR);
        private static final MethodHandle CLOSE=function("CloseHandle",DWORD,PTR);
        private final MemorySegment handle;
        private volatile boolean open=true;
        private int transfers;
        WindowsPipe(String name)throws IOException {
            if(PTR.byteSize()!=8)throw new IOException("Windows IPC requires a 64-bit JVM");
            try(Arena arena=Arena.ofConfined()) {
                MemorySegment error=arena.allocate(ERROR_LAYOUT),path=arena.allocateFrom("\\\\.\\pipe\\"+name,StandardCharsets.UTF_16LE);
                handle=(MemorySegment)OPEN.invoke(error,path,0xC0000000,0,MemorySegment.NULL,3,0x40000000,MemorySegment.NULL);
                if(handle.address()==-1L)throw failure("CreateFileW",error);
            }catch(IOException failure){throw failure;}catch(Throwable failure){throw new IOException("Could not open local Discord pipe",failure);}
        }
        private static IOException failure(String operation,MemorySegment error) {
            return new IOException(operation+" failed (Windows error "+error.get(DWORD,ERROR_OFFSET)+")");
        }
        private synchronized void beginTransfer()throws ClosedChannelException {
            if(!open)throw new ClosedChannelException();transfers++;
        }
        private synchronized void endTransfer(){transfers--;notifyAll();}
        private int transfer(ByteBuffer buffer,boolean writing)throws IOException {
            int length=buffer.remaining();if(length==0)return 0;
            beginTransfer();
            try(Arena arena=Arena.ofConfined()) {
                MemorySegment error=arena.allocate(ERROR_LAYOUT),event=(MemorySegment)EVENT.invoke(error,MemorySegment.NULL,1,0,MemorySegment.NULL);
                if(event.address()==0)throw failure("CreateEventW",error);
                try {
                    MemorySegment bytes=arena.allocate(length),count=arena.allocate(DWORD),overlapped=arena.allocate(32,8);
                    overlapped.set(PTR,24,event);
                    if(writing)bytes.asByteBuffer().put(buffer.duplicate());
                    int complete=(int)(writing?WRITE:READ).invoke(error,handle,bytes,length,count,overlapped);
                    if(complete==0) {
                        int code=error.get(DWORD,ERROR_OFFSET);
                        if(code!=997){if(!writing&&(code==109||code==232))return -1;throw failure(writing?"WriteFile":"ReadFile",error);}
                        if((int)RESULT.invoke(error,handle,overlapped,count,1)==0)throw failure("GetOverlappedResult",error);
                    }
                    int transferred=count.get(DWORD,0);
                    if(writing)buffer.position(buffer.position()+transferred);
                    else buffer.put(bytes.asByteBuffer().limit(transferred));
                    return transferred;
                }finally{CLOSE.invoke(error,event);}
            }catch(IOException failure){throw failure;}catch(Throwable failure){throw new IOException("Local pipe I/O failed",failure);}
            finally{endTransfer();}
        }
        public int read(ByteBuffer buffer)throws IOException{return transfer(buffer,false);}
        public int write(ByteBuffer buffer)throws IOException{return transfer(buffer,true);}
        public boolean isOpen(){return open;}
        public synchronized void close()throws IOException {
            if(!open)return;open=false;
            boolean interrupted=false;
            try(Arena arena=Arena.ofConfined()) {
                MemorySegment error=arena.allocate(ERROR_LAYOUT);CANCEL.invoke(error,handle,MemorySegment.NULL);
                // Keep the handle and OVERLAPPED buffers alive until canceled I/O completes.
                while(transfers>0)try{wait();}catch(InterruptedException ignored){interrupted=true;}
                CLOSE.invoke(error,handle);
            }catch(Throwable failure){throw new IOException("Could not close local Discord pipe",failure);}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    /** Framing is independent of Minecraft/LWJGL and can be reused by another MCP host. */
    public static Transport channelTransport(ByteChannel channel) {
        return new Transport(){
            public Frame read()throws IOException {
                ByteBuffer header=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);readFully(header);header.flip();
                int opcode=header.getInt(),length=header.getInt();
                if(length<0||length>1048576)throw new IOException("Invalid Discord IPC frame length: "+length);
                ByteBuffer payload=ByteBuffer.allocate(length);readFully(payload);payload.flip();
                try{return new Frame(opcode,new JsonParser().parse(StandardCharsets.UTF_8.decode(payload).toString()).getAsJsonObject());}
                catch(RuntimeException failure){throw new IOException("Invalid Discord IPC JSON",failure);}
            }
            private void readFully(ByteBuffer buffer)throws IOException {while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new EOFException("Discord IPC closed");}
            public synchronized void write(Frame frame)throws IOException {
                byte[] bytes=GSON.toJson(frame.body).getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer=ByteBuffer.allocate(8+bytes.length).order(ByteOrder.LITTLE_ENDIAN);buffer.putInt(frame.opcode).putInt(bytes.length).put(bytes).flip();
                while(buffer.hasRemaining())channel.write(buffer);
            }
            public void close()throws IOException{channel.close();}
        };
    }
}
