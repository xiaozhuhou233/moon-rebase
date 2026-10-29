package moon.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import moon.visual.gui.ModuleRowWidget;
import net.minecraft.util.ChatComponentText;
import org.lwjglx.input.Keyboard;

/** Local commands, persistent module binds and per-press input. No server completion packets. */
public final class McpBindings {
    private final Path file;
    private final Supplier<List<ModuleRowWidget.Module>> modules;
    private final Consumer<String> feedback;
    private final Map<String,Integer> keys=new TreeMap<>();
    private final BitSet held=new BitSet();
    private boolean writable=true;

    McpBindings(Path file,Supplier<List<ModuleRowWidget.Module>> modules,Consumer<String> feedback){
        this.file=file;this.modules=modules;this.feedback=feedback;
        if(!Files.exists(file))for(var module:modules.get())if(module.bindId().equals("clickgui"))keys.put(module.bindId(),Keyboard.KEY_RSHIFT);
        if(Files.exists(file))try(Reader input=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            Properties saved=new Properties();saved.load(input);
            for(String id:saved.stringPropertyNames()){
                int key=Integer.parseInt(saved.getProperty(id));
                if(key<=0||key>=256||Keyboard.getKeyName(key)==null)throw new IOException("Invalid key for "+id);
                keys.put(id,key);
            }
        }catch(IOException|IllegalArgumentException failure){keys.clear();writable=false;feedback.accept("Cannot read keybinds; file preserved: "+failure.getMessage());}
    }
    static String normalized(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]","");}
    private List<ModuleRowWidget.Module> matches(String name){
        String id=normalized(name);
        return modules.get().stream().filter(m->normalized(m.bindId()).equals(id)||normalized(m.displayName()).equals(id)).toList();
    }
    boolean hasBinding(ModuleRowWidget.Module module){return keys.containsKey(module.bindId());}
    int key(ModuleRowWidget.Module module){return keys.getOrDefault(module.bindId(),0);}
    private void save(Map<String,Integer> next)throws IOException{
        if(!writable)throw new IOException("Existing keybind file is unreadable; repair it before saving");
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"keybinds-",".tmp");
        try{
            Properties saved=new Properties();next.forEach((id,key)->saved.setProperty(id,key.toString()));
            try(Writer output=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){saved.store(output,"Moon module keys (LWJGL key codes)");}
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    boolean command(String message){
        if(!isCommand(message))return false;
        String args=message.stripLeading().substring(5).trim();
        if(args.isEmpty()){feedback.accept(".bind <module> <key | NONE>  (example: .bind KillAura R)");return true;}
        int split=args.lastIndexOf(' ');
        if(split<0){feedback.accept("Usage: .bind <module> <key | NONE>");return true;}
        String name=args.substring(0,split).trim(),keyName=args.substring(split+1).trim().toUpperCase(Locale.ROOT);
        if(name.startsWith("\"")&&name.endsWith("\"")&&name.length()>1)name=name.substring(1,name.length()-1);
        var found=matches(name);
        if(found.size()!=1){feedback.accept(found.isEmpty()?"Unknown module: "+name:"Ambiguous module: "+name);return true;}
        boolean remove=keyName.equals("NONE")||keyName.equals("UNBIND");
        int key=remove?0:Keyboard.getKeyIndex(keyName);
        if(!remove&&(key<=0||key>=256||Keyboard.getKeyName(key)==null)){feedback.accept("Unknown key: "+keyName);return true;}
        var module=found.getFirst();Map<String,Integer> next=new TreeMap<>(keys);
        if(remove)next.remove(module.bindId());else next.put(module.bindId(),key);
        try{save(next);keys.clear();keys.putAll(next);feedback.accept(module.displayName()+" bound to "+(remove?"NONE":Keyboard.getKeyName(key)));}
        catch(IOException failure){feedback.accept("Bind not saved: "+failure.getMessage());}
        return true;
    }
    static boolean isCommand(String message){
        String s=message.stripLeading();return s.length()>=5&&s.regionMatches(true,0,".bind",0,5)&&(s.length()==5||Character.isWhitespace(s.charAt(5)));
    }
    void event(int key,boolean down,boolean gameplay){
        if(key<=0||key>=256)return;
        boolean wasDown=held.get(key);held.set(key,down);
        if(!down||wasDown||!gameplay)return;
        for(var module:List.copyOf(modules.get()))if(keys.getOrDefault(module.bindId(),0)==key)module.toggle();
    }
    void reconcileHeld(IntPredicate pressed){for(int k=held.nextSetBit(0);k>=0;k=held.nextSetBit(k+1))if(!pressed.test(k))held.clear(k);}
    public static boolean handleCommand(String message){var v=McpVisualServices.current();return v!=null&&v.bindings!=null&&v.bindings.command(message);}
    public static void keyEvent(int key,boolean down){
        var v=McpVisualServices.current();if(v==null||v.bindings==null)return;
        v.bindings.event(key,down,v.mc.currentScreen==null&&v.mc.inGameHasFocus&&org.lwjglx.opengl.Display.isActive()&&v.mc.thePlayer!=null&&v.mc.theWorld!=null);
    }
    public record Completion(String text,int cursor){}
    public static final class TabCompletion {
        private String lastText;private int lastCursor,index;private List<Completion> choices=List.of();
        public void reset(){lastText=null;choices=List.of();index=0;}
        public Completion complete(String text,int cursor){
            var v=McpVisualServices.current();return complete(text,cursor,v==null?List.of():v.registeredModules());
        }
        Completion complete(String text,int cursor,List<ModuleRowWidget.Module> modules){
            if(!isCommand(text))return null;
            cursor=Math.max(0,Math.min(cursor,text.length()));
            if(!text.equals(lastText)||cursor!=lastCursor){
                index=0;choices=new ArrayList<>();
                int start=5;while(start<text.length()&&Character.isWhitespace(text.charAt(start)))start++;
                if(cursor>=start){
                    String prefix=text.substring(start,cursor).replace("\"","");
                    int end=cursor;
                    if(start<text.length()&&text.charAt(start)=='\"'){
                        int quote=text.indexOf('"',Math.max(start+1,cursor));
                        if(quote>=0)end=quote+1;
                    }else while(end<text.length()&&!Character.isWhitespace(text.charAt(end)))end++;
                    String tail=text.substring(end);
                    String head=text.substring(0,Math.min(start,text.length()));if(head.length()==5)head+=" ";
                    for(var module:modules.stream().sorted(Comparator.comparing(ModuleRowWidget.Module::displayName,String.CASE_INSENSITIVE_ORDER)).toList()){
                        if(!normalized(module.displayName()).startsWith(normalized(prefix))&&!normalized(module.bindId()).startsWith(normalized(prefix)))continue;
                        String name=module.displayName();if(name.indexOf(' ')>=0)name="\""+name+"\"";
                        String result=head+name;choices.add(new Completion(result+tail,result.length()));
                    }
                }
            }
            Completion result=choices.isEmpty()?new Completion(text,cursor):choices.get(index++%choices.size());
            lastText=result.text();lastCursor=result.cursor();return result;
        }
    }
}
