package dev.pier.lua;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * A module registered by a Lua script. This is intentionally independent of
 * any native/Java module system: the loader can host script modules even when
 * the base client has no click GUI or module framework yet.
 */
public final class LuaModule {

    private final LuaManager manager;
    private final Globals globals;
    private final LuaTable table;
    private final String name;
    private final String displayName;
    private final String description;
    private final Map<String, LuaSetting> settings = new LinkedHashMap<String, LuaSetting>();

    private boolean enabled;
    private boolean failed;
    private Throwable lastFailure;
    public Throwable lastFailure(){return lastFailure;}

    public LuaModule(LuaManager manager, Globals globals, String name, String displayName,
                     String description, LuaTable table) {
        this.manager = manager;
        this.globals = globals;
        this.name = name;
        this.displayName = displayName;
        this.description = description == null ? "" : description;
        this.table = table;
    }

    public Globals getGlobals() {
        return globals;
    }

    public LuaTable getTable() {
        return table;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFailed() {
        return failed;
    }

    public synchronized void setEnabled(boolean state) {
        if (failed && state) {
            return;
        }
        if (enabled == state) {
            return;
        }
        enabled = state;
        try {
            invoke(state ? "on_enable" : "on_disable");
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public synchronized void tick() {
        if (!enabled || failed) {
            return;
        }
        try {
            invoke("on_tick", LuaValue.valueOf(true));
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public synchronized void update(LuaTable event) {
        if (!enabled || failed) {
            return;
        }
        try {
            invoke("on_update", event);
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public synchronized void move(LuaTable event) {
        if (!enabled || failed) {
            return;
        }
        try {
            invoke("on_move", event);
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public synchronized void packetReceive(LuaTable event) {
        if (!enabled || failed) {
            return;
        }
        try {
            invoke("on_packet_receive", event);
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public synchronized void packetSend(LuaTable event) {
        if (!enabled || failed) {
            return;
        }
        try {
            invoke("on_packet_send", event);
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public boolean hasCallback(String name) {
        LuaValue function = table.get(name);
        return !function.isnil() && function.isfunction();
    }

    /**
     * Called by the future render hook. Kept here so scripts can already
     * register the callback; the loader does not invoke it until a renderer
     * is wired up.
     */
    public synchronized void renderOverlay(float partialTicks, boolean pre) {
        if (!enabled || failed) {
            return;
        }
        try {
            LuaTable event=new LuaTable(); event.set("partial_ticks",LuaValue.valueOf(partialTicks));
            event.set("pre",LuaValue.valueOf(pre)); invoke("on_overlay_render",event);
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public synchronized void renderEffect(boolean blur, float partialTicks) {
        if (!enabled || failed) {
            return;
        }
        try {
            LuaTable event=new LuaTable(); event.set("partial_ticks",LuaValue.valueOf(partialTicks));
            event.set("blur",LuaValue.valueOf(blur)); invoke("on_effect_render",event);
        } catch (LuaError error) {
            lastFailure=error;
            failed = true;
            enabled = false;
            manager.onScriptError(this, error);
        } catch (Throwable throwable) {
            lastFailure=throwable;
            failed = true;
            enabled = false;
            manager.onScriptError(this, throwable);
        }
    }

    public void addSetting(LuaSetting setting) {
        String saved = manager.getConfig().get(name, setting.getId());
        if (saved != null) {
            setting.setFromConfigString(saved);
        }
        settings.put(setting.getId(), setting);
    }

    public LuaValue getSettingValue(String id) {
        LuaSetting setting = settings.get(id);
        return setting == null ? LuaValue.NIL : setting.toLua();
    }

    public boolean setSetting(String id, LuaValue value) {
        LuaSetting setting = settings.get(id);
        if (setting == null) {
            return false;
        }
        String before=setting.toConfigString();setting.setFromLua(value);
        if(!Objects.equals(before,setting.toConfigString()))manager.getConfig().set(name,id,setting.toConfigString());
        return true;
    }

    public LuaSetting getSetting(String id) {
        return settings.get(id);
    }

    public Collection<LuaSetting> getSettings() {
        return settings.values();
    }

    private void invoke(String key, LuaValue... args) {
        LuaValue function = table.get(key);
        if (function.isnil() || !function.isfunction()) {
            return;
        }
        if (args.length == 0) {
            function.call();
        } else {
            function.invoke(LuaValue.varargsOf(args));
        }
    }
}
