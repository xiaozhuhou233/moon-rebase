package moon.module.player;

import com.google.gson.*;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import moon.visual.render.ShadowStyle;

/** jF/fp/bG: ordered detection profiles, one-second pending counters and server history.
 * Original classes are static evidence only. History uses the destination's own file;
 * the recovered sessions/kills/deaths/playTime fields retain their original meaning.
 */
public final class SessionInformationModule extends NativeModule {
    public interface Host {
        /** Null means singleplayer, as in the original ServerData boundary. */
        String server(); String playerName(); boolean inWorld();
        default long currentTimeMillis(){return System.currentTimeMillis();}
        default void openDirectory(Path path)throws IOException{Desktop.getDesktop().open(path.toFile());}
    }
    private record Rules(List<String> kills,List<String> deaths){}
    private static final List<String> DEFAULT_KILLS=List.of("by %p.","by %p. FINAL KILL!");
    private static final List<String> DEFAULT_DEATHS=List.of("%p was","%p fell into","%p lost a","%p drank",
        "%p be shot","%p be snowballed","%p be sent to","%p be killed","%p be shot");
    public final ModularListSetting servers;
    public final BooleanSetting autoDetect,colorful,lowAlpha,syncColors;
    public final FloatSetting scale,x,y;
    public final ColorSetting color;
    public final ShadowStyle shadowColor;
    public final ActionSetting openServers,reloadDetections;
    private final List<SettingValue<?>> settings;
    private final Host host;
    private final Path directory,historyFile;
    private final Map<String,Rules> profiles=new LinkedHashMap<>();
    private JsonObject history=new JsonObject();
    private boolean historyWritable=true;
    private String observedServer;
    private long lastUpdate,playTime,pendingPlayTime;
    private int kills,deaths,pendingKills,pendingDeaths;
    private volatile String error;
    private final Object writeLock=new Object();
    private Thread writer;
    private String pendingWrite;

    public SessionInformationModule(Host host,Path directory,MarkerHooks h){
        super("session-information","SessionInformation",Category.PLAYER);
        this.host=Objects.requireNonNull(host);this.directory=directory.toAbsolutePath().normalize();
        historyFile=this.directory.resolve("sessions.json");
        servers=ModularListSetting.builder("servers",h).configure("Detection types","hypixel.net")
            .collectionChange(ignored->reload()).build();
        autoDetect=flag(h,"auto-detect-server",true);colorful=flag(h,"colorful",true);
        lowAlpha=flag(h,"low-alpha-text",true);syncColors=flag(h,"sync-with-module-list",true);
        scale=FloatSetting.builder("render-scale",h).configure("Render scale",1,.25F,4,.1F).build();
        x=FloatSetting.builder("x",h).configure("X",0,0,100000,1).visibility(()->false).build();
        y=FloatSetting.builder("y",h).configure("Y",0,0,100000,1).visibility(()->false).build();
        color=ColorSetting.builder("color",h).configure("Color",0xffff0000).build();
        shadowColor=new ShadowStyle("shadow-color","Shadow Color",h);
        openServers=ActionSetting.builder("open-servers",h).title("Open server directory").callback((a,b)->{
            reload();try{host.openDirectory(this.directory);}catch(IOException|RuntimeException failure){error=failure.toString();}
        }).build();
        reloadDetections=ActionSetting.builder("reload-detections",h).title("Reload detections").callback((a,b)->reload()).build();
        settings=List.of(servers,autoDetect,scale,colorful,lowAlpha,syncColors,color,openServers,reloadDetections,x,y,shadowColor.group);
        readHistory();reload();
    }
    private static BooleanSetting flag(MarkerHooks h,String key,boolean value){
        return BooleanSetting.builder(key,h).configure(key.replace('-',' '),value).build();
    }
    public List<? extends SettingValue<?>> settings(){return settings;}
    public int kills(){return kills;}
    public int deaths(){return deaths;}
    public String error(){return error;}
    public long elapsedSeconds(){return playTime/1000;}
    public Path historyFile(){return historyFile;}
    public Path directory(){return directory;}
    /** Keep the original calendar calculation, including its fixed 365/30-day remainders. */
    public String elapsed(){
        LocalDateTime end=LocalDateTime.ofInstant(Instant.ofEpochMilli(playTime),ZoneId.systemDefault());
        LocalDateTime begin=LocalDateTime.ofInstant(Instant.EPOCH,ZoneId.systemDefault());
        long years=ChronoUnit.YEARS.between(begin,end),months=ChronoUnit.MONTHS.between(begin,end)-years*12;
        long days=ChronoUnit.DAYS.between(begin,end)-years*365-months*30;
        long hours=ChronoUnit.HOURS.between(begin,end)-days*24-months*720-years*8760;
        long minutes=ChronoUnit.MINUTES.between(begin,end)-hours*60-days*1440-months*43200-years*525600;
        long seconds=ChronoUnit.SECONDS.between(begin,end)-minutes*60-hours*3600-days*86400-months*2592000-years*31536000;
        StringBuilder text=new StringBuilder();
        if(years>0)text.append(years).append("y ");if(months>0)text.append(months).append("mo ");
        if(days>0)text.append(days).append("d ");if(hours>0)text.append(hours).append("h ");
        if(minutes>0)text.append(minutes).append("m ");if(seconds>0)text.append(seconds).append('s');
        return text.isEmpty()?"0s":text.toString();
    }
    @Override protected void onDisable(){awaitHistoryWrites();}
    @Override public void worldChanged(){
        // Destination lifecycle: pending chat must never leak into another connection.
        observedServer=null;pendingKills=pendingDeaths=0;pendingPlayTime=0;lastUpdate=host.currentTimeMillis();
    }
    private void observeServer(String server){
        if(Objects.equals(observedServer,server))return;
        observedServer=server;pendingKills=pendingDeaths=0;pendingPlayTime=0;
    }
    public void tick(){
        if(!enabled()||!host.inWorld())return;
        String actual=host.server();observeServer(actual);
        if(autoDetect.get()&&actual!=null)for(String profile:servers.getAvailable())
            if(profile!=null&&actual.contains(profile)){if(!Objects.equals(servers.get(),profile))servers.set(profile);break;}
        long now=host.currentTimeMillis();if(now-lastUpdate<1000)return;
        pendingPlayTime+=1000; // Decoded original period and step are both exactly 1000 ms; no catch-up loop.
        if(actual!=null){
            JsonObject sessions=history.getAsJsonObject("sessions");
            JsonObject saved=sessions.has(actual)?sessions.getAsJsonObject(actual):new JsonObject();
            kills=number(saved,"kills").intValueExact();deaths=number(saved,"deaths").intValueExact();
            playTime=number(saved,"playTime").longValueExact();
            if(servers.get()!=null&&actual.contains(servers.get())){
                kills+=pendingKills;deaths+=pendingDeaths;playTime+=pendingPlayTime;
            }
            saved.addProperty("kills",kills);saved.addProperty("deaths",deaths);saved.addProperty("playTime",playTime);
            sessions.add(actual,saved);pendingKills=pendingDeaths=0;pendingPlayTime=0;saveHistory();
        }
        // Original singleplayer branch does not apply pending values to displayed aggregates.
        lastUpdate=now;
    }
    public void chat(String message){
        if(!enabled()||!host.inWorld()||message==null)return;
        observeServer(host.server());Rules rules=profiles.get(servers.get());if(rules==null)return;
        String name=host.playerName();
        if(matches(rules.kills(),message,name))pendingKills++;
        if(matches(rules.deaths(),message,name))pendingDeaths++;
    }
    private static boolean matches(List<String> rules,String text,String name){
        return rules.stream().filter(rule->!rule.isEmpty()).anyMatch(rule->text.contains(rule.replaceAll("%p",name)));
    }
    /** Only missing files receive templates. Existing files, including empty ones, are user-owned. */
    public void reload(){
        profiles.clear();
        for(String profile:servers.getAvailable()){
            if(!validProfile(profile)){error="Invalid detection profile: "+profile;continue;}
            Path folder=directory.resolve(profile);
            try{
                Files.createDirectories(folder);boolean hypixel=profile.contains("hypixel.net");
                Path kill=folder.resolve("kills.txt"),death=folder.resolve("deaths.txt");
                createRules(kill,hypixel?DEFAULT_KILLS:List.of());createRules(death,hypixel?DEFAULT_DEATHS:List.of());
                profiles.put(profile,new Rules(readRules(kill),readRules(death)));
            }catch(IOException failure){error=failure.toString();}
        }
    }
    private static boolean validProfile(String value){
        return value!=null&&value.matches("[A-Za-z0-9._-]+")&&!value.equals(".")&&!value.equals("..");
    }
    private static void createRules(Path file,List<String> rules)throws IOException{
        try{Files.writeString(file,String.join(System.lineSeparator(),rules),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
        catch(FileAlreadyExistsException existing){}
    }
    private List<String> readRules(Path file){
        try{return Files.readAllLines(file,StandardCharsets.UTF_8);}catch(IOException failure){error=failure.toString();return List.of();}
    }
    private static java.math.BigDecimal number(JsonObject value,String key){
        if(!value.has(key))return java.math.BigDecimal.ZERO;
        JsonElement element=value.get(key);
        if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid history "+key);
        return new java.math.BigDecimal(element.getAsString());
    }
    private void readHistory(){
        history.add("sessions",new JsonObject());
        if(!Files.exists(historyFile))return;
        try{
            JsonObject parsed=new JsonParser().parse(Files.readString(historyFile,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject sessions=parsed.getAsJsonObject("sessions");if(sessions==null)throw new IllegalArgumentException("Missing sessions");
            for(var entry:sessions.entrySet()){
                JsonObject saved=entry.getValue().getAsJsonObject();number(saved,"kills").intValueExact();
                number(saved,"deaths").intValueExact();number(saved,"playTime").longValueExact();
            }
            history=parsed;
        }catch(IOException|RuntimeException failure){historyWritable=false;error="Session history preserved: "+failure;}
    }
    /** Immutable JSON snapshots; one virtual writer coalesces updates without touching client state. */
    private void saveHistory(){
        if(!historyWritable)return;
        String snapshot=new GsonBuilder().setPrettyPrinting().create().toJson(history)+"\n";
        synchronized(writeLock){
            pendingWrite=snapshot;
            if(writer!=null)return;
            writer=Thread.ofVirtual().name("Moon session history").unstarted(this::writeHistory);writer.start();
        }
    }
    private void writeHistory(){
        while(true){
            String snapshot;
            synchronized(writeLock){
                snapshot=pendingWrite;pendingWrite=null;
                if(snapshot==null){writer=null;writeLock.notifyAll();return;}
            }
            Path temporary=null;
            try{
                Files.createDirectories(directory);temporary=Files.createTempFile(directory,"sessions-",".tmp");
                Files.writeString(temporary,snapshot,StandardCharsets.UTF_8);
                try{Files.move(temporary,historyFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,historyFile,StandardCopyOption.REPLACE_EXISTING);}
            }catch(IOException|RuntimeException failure){error=failure.toString();}
            finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException failure){error=failure.toString();}}
        }
    }
    public void awaitHistoryWrites(){
        synchronized(writeLock){while(writer!=null)try{writeLock.wait();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}}
    }
}
