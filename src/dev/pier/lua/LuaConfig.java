package dev.pier.lua;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Script settings are live in memory. Changed values are coalesced off the render path. */
public final class LuaConfig {
    private final File file;
    private final Properties properties=new Properties();
    private boolean dirty,writable=true;
    private long nextSave;
    public LuaConfig(File file){
        this.file=file;
        if(file!=null&&file.isFile())try(Reader input=Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)){properties.load(input);}
        catch(IOException|IllegalArgumentException failure){writable=false;System.err.println("[Lua] Settings not loaded; original file preserved: "+failure);}
    }
    public synchronized String get(String module,String setting){return properties.getProperty(module+"."+setting);}
    public synchronized void set(String module,String setting,String value){
        String key=module+"."+setting;
        if(Objects.equals(properties.getProperty(key),value))return;
        if(value==null)properties.remove(key);else properties.setProperty(key,value);
        dirty=true;
    }
    public File getFile(){return file;}
    public synchronized void poll(long now){if(now>=nextSave){nextSave=now+1000;flush();}}
    public synchronized boolean flush(){
        if(!dirty||!writable||file==null)return false;
        Path target=file.toPath().toAbsolutePath(),temporary=null;
        try{
            Files.createDirectories(target.getParent());temporary=Files.createTempFile(target.getParent(),"lua-settings-",".tmp");
            try(Writer output=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){properties.store(output,"Moon Lua settings");}
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;return true;
        }catch(IOException failure){System.err.println("[Lua] Could not save settings; will retry: "+failure);return false;}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
}
