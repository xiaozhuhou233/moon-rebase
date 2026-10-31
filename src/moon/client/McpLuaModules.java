package moon.client;

import java.util.*;
import dev.pier.lua.*;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import org.luaj.vm2.LuaValue;

/** Live GUI adapters for the destination's existing Lua modules. Values/config still belong to Lua.
 * Lua's numeric declaration API has no bounds/step. Edit these as validated text instead of
 * inventing slider ranges. All twelve restored native setting widgets remain available.
 */
final class McpLuaModules {
    private final MarkerHooks hooks;
    private final Map<LuaModule,Module> cached=new IdentityHashMap<>();
    McpLuaModules(MarkerHooks hooks){this.hooks=hooks;}
    boolean refresh(List<ModuleRowWidget.Module> output){
        List<LuaModule> current=LuaManager.get().getModulesSnapshot();List<ModuleRowWidget.Module> next=new ArrayList<>();
        boolean changed=false;
        for(LuaModule source:current){
            if(source.getName().equalsIgnoreCase("scaffoldlua"))continue;
            Module module=cached.computeIfAbsent(source,Module::new);changed|=module.refresh();next.add(module);
        }
        changed|=!next.equals(output);output.clear();output.addAll(next);cached.keySet().retainAll(current);return changed;
    }
    private final class Module implements ModuleRowWidget.Module {
        private final LuaModule source;
        private final Map<LuaSetting,Value> values=new IdentityHashMap<>();
        private final List<SettingValue<?>> settings=new ArrayList<>();
        Module(LuaModule source){this.source=source;}
        public String bindId(){return "lua:"+source.getName().toLowerCase(Locale.ROOT);}
        public String hudSuffix(){LuaValue value=source.getTable().get("suffix");return value.isstring()||value.isnumber()?value.tojstring():"";}
        public String displayName(){return source.getDisplayName();}public boolean enabled(){return source.isEnabled();}
        public void toggle(){source.setEnabled(!source.isEnabled());}
        public List<? extends SettingValue<?>> settings(){return settings;}
        boolean refresh(){
            List<LuaSetting> current=new ArrayList<>(source.getSettings());List<SettingValue<?>> next=new ArrayList<>();
            for(LuaSetting setting:current){Value value=values.computeIfAbsent(setting,s->new Value(source,s));value.refresh();next.add(value.setting);}
            boolean changed=!settings.equals(next);settings.clear();settings.addAll(next);values.keySet().retainAll(current);return changed;
        }
    }
    private final class Value {
        private final LuaModule module;
        private final LuaSetting source;
        final SettingValue<?> setting;
        private Object observed;
        Value(LuaModule module,LuaSetting source){
            this.module=module;this.source=source;observed=source.getValue();String key=source.getId(),name=source.getName();
            setting=switch(source.getType()){
                case BOOLEAN->BooleanSetting.builder(key,hooks).configure(name,(Boolean)observed).callback((old,value)->write(LuaValue.valueOf(value))).build();
                case COLOR->ColorSetting.builder(key,hooks).configure(name,(Integer)observed).callback((old,value)->write(LuaValue.valueOf(value))).build();
                case TEXT->StringSetting.builder(key,hooks).configure(name,String.valueOf(observed)).callback((old,value)->write(LuaValue.valueOf(value))).build();
                case INTEGER,FLOAT->StringSetting.builder(key,hooks).configure(name,String.valueOf(observed)).callback((old,value)->{
                    try{
                        if(source.getType()==LuaSetting.Type.INTEGER)write(LuaValue.valueOf(Integer.parseInt(value)));
                        else{float number=Float.parseFloat(value);if(Float.isFinite(number))write(LuaValue.valueOf(number));}
                    }catch(NumberFormatException incomplete){/* Keep editing incomplete input; retain the last valid Lua value. */}
                }).build();
            };
        }
        private void write(LuaValue value){synchronized(module){module.setSetting(source.getId(),value);observed=source.getValue();}}
        @SuppressWarnings({"unchecked","rawtypes"}) void refresh(){
            Object live=source.getValue();if(Objects.equals(live,observed))return;observed=live;
            SettingValue raw=setting;var callback=raw.getCallback();raw.setCallback(null);
            try{raw.set(source.getType()==LuaSetting.Type.INTEGER||source.getType()==LuaSetting.Type.FLOAT?String.valueOf(live):live);}
            finally{raw.setCallback(callback);}
        }
    }
}
