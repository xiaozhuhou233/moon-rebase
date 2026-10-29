package dev.pier.lua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owns the LuaJ globals, the script directory, script modules and their
 * settings. There is deliberately no dependency on a click GUI or a native
 * module system yet: everything the scripts need is in this package.
 */
public final class LuaManager {

    private static final LuaManager INSTANCE = new LuaManager();

    private final Map<String, LuaModule> modules = new LinkedHashMap<String, LuaModule>();
    private final Properties options = new Properties();
    private final ThreadLocal<Boolean> suppressSendEvent = new ThreadLocal<Boolean>();
    private final ThreadLocal<Boolean> inPacketCallback = new ThreadLocal<Boolean>();

    private File scriptDir;
    private LuaConfig config;
    private boolean initialized;
    private boolean autoEnable = false;

    private LuaManager() {
    }

    public static LuaManager get() {
        return INSTANCE;
    }

    public void init() {
        if (initialized) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.mcDataDir == null) {
            return;
        }

        scriptDir = new File(minecraft.mcDataDir, "scripts");
        if (!scriptDir.isDirectory() && !scriptDir.mkdirs()) {
            log("Could not create script directory: " + scriptDir.getAbsolutePath());
            return;
        }

        loadOptions();
        config = new LuaConfig(new File(scriptDir, "lua-config.properties"));
        initialized = true;
        reloadScripts();
    }

    public void onTick() {
        try {
            init();
            if (!initialized) {
                return;
            }
            List<LuaModule> snapshot = snapshotModules();
            for (LuaModule module : snapshot) {
                if (module.isEnabled()) module.tick();
            }
            if(config!=null)config.poll(System.currentTimeMillis());
        } catch (Throwable throwable) {
            log("Tick error: " + throwable);
        }
    }

    /** Actual overlay callbacks, with destination 2D state scoped per script. */
    public void onRender(float partialTicks, boolean pre) {
        if (!initialized) {
            return;
        }
        for (LuaModule module : snapshotModules()) {
            if (module.isEnabled()) {
                moon.client.McpLuaRenderer.renderCallback(() -> module.renderOverlay(partialTicks, pre));
            }
        }
    }

    public void onEffectRender(boolean blur, float partialTicks) {
        if (!initialized) {
            return;
        }
        for (LuaModule module : snapshotModules()) {
            if (module.isEnabled()) {
                moon.client.McpLuaRenderer.renderCallback(() -> module.renderEffect(blur, partialTicks));
            }
        }
    }

    /**
     * Moon's on_update(pre, x, y, z, yaw, pitch, ground) event. The table is
     * passed by reference, so scripts can read and, on pre, edit values.
     */
    public void onUpdate(EntityPlayerSP player, boolean pre) {
        if (!initialized || player == null) {
            return;
        }
        LuaTable event = new LuaTable();
        event.set("pre", LuaValue.valueOf(pre));
        event.set("x", LuaValue.valueOf(player.posX));
        event.set("y", LuaValue.valueOf(player.posY));
        event.set("z", LuaValue.valueOf(player.posZ));
        event.set("yaw", LuaValue.valueOf((double) player.rotationYaw));
        event.set("pitch", LuaValue.valueOf((double) player.rotationPitch));
        event.set("ground", LuaValue.valueOf(player.onGround));

        boolean handled = false;
        for (LuaModule module : snapshotModules()) {
            if (module.isEnabled() && module.hasCallback("on_update")) {
                handled = true;
                module.update(event);
            }
        }
        if (!handled || !pre) {
            return;
        }
        try {
            double x = event.get("x").todouble();
            double y = event.get("y").todouble();
            double z = event.get("z").todouble();
            if (x != player.posX || y != player.posY || z != player.posZ) {
                player.setPosition(x, y, z);
            }
            player.rotationYaw = (float) event.get("yaw").todouble();
            player.rotationPitch = (float) event.get("pitch").todouble();
            player.onGround = event.get("ground").toboolean();
        } catch (Throwable throwable) {
            log("on_update apply error: " + throwable);
        }
    }

    /**
     * Called from NetworkManager.channelRead0 before processPacket. Returning
     * true cancels the packet. The callback runs on the network thread, so
     * LuaModule serializes all Lua entry points per script.
     */
    public boolean onPacketReceive(Packet packet) {
        if (!initialized || packet == null) {
            return false;
        }
        Integer packetId = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, packet);
        if (packetId == null || !hasHandler("on_packet_receive")) {
            return false;
        }

        LuaTable event = LuaPackets.read(packet, packetId.intValue());
        for (LuaModule module : snapshotModules()) {
            if (module.isEnabled() && module.hasCallback("on_packet_receive")) {
                module.packetReceive(event);
            }
        }
        if (event.get("cancel").toboolean()) {
            return true;
        }
        LuaPackets.applyInbound(event, packet);
        return false;
    }

    /**
     * Called before an outbound play packet is dispatched. Returns the packet
     * to send (possibly rebuilt from Lua edits), or null when canceled.
     */
    public Packet onPacketSend(Packet packet) {
        if (!initialized || packet == null
                || Boolean.TRUE.equals(suppressSendEvent.get())
                || Boolean.TRUE.equals(inPacketCallback.get())) {
            return packet;
        }
        Integer packetId = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.SERVERBOUND, packet);
        if (packetId == null || !hasHandler("on_packet_send")) {
            return packet;
        }

        LuaTable event = LuaPackets.read(packet, packetId.intValue());
        inPacketCallback.set(Boolean.TRUE);
        try {
            for (LuaModule module : snapshotModules()) {
                if (module.isEnabled() && module.hasCallback("on_packet_send")) {
                    module.packetSend(event);
                }
            }
        } finally {
            inPacketCallback.remove();
        }
        if (event.get("cancel").toboolean()) {
            return null;
        }
        return LuaPackets.rebuild(packet, event);
    }

    /**
     * Sends a packet without firing on_packet_send. Used by
     * player.send_packet_no_event.
     */
    public boolean sendPacketNoEvent(Packet packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null || packet == null) {
            return false;
        }
        Boolean previous = suppressSendEvent.get();
        suppressSendEvent.set(Boolean.TRUE);
        try {
            minecraft.thePlayer.sendQueue.addToSendQueue(packet);
            return true;
        } finally {
            if (previous == null) {
                suppressSendEvent.remove();
            } else {
                suppressSendEvent.set(previous);
            }
        }
    }

    private boolean hasHandler(String callback) {
        for (LuaModule module : snapshotModules()) {
            if (module.isEnabled() && module.hasCallback(callback)) {
                return true;
            }
        }
        return false;
    }

    public void reloadScripts() {
        if (scriptDir == null) {
            return;
        }
        disableAll();
        synchronized (modules) {
            modules.clear();
        }

        File[] files = scriptDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".lua"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        int loaded = 0;
        for (File file : files) {
            if (loadScript(file)) {
                loaded++;
            }
        }
        disableAll();
        log("Loaded " + loaded + " script(s) from " + scriptDir.getAbsolutePath());
    }

    private boolean loadScript(File file) {
        Globals globals = null;
        try {
            globals = LuaApi.createGlobals(this);
            try (InputStream in = new FileInputStream(file);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                globals.load(reader, "@" + file.getName()).call();
            }

            if (autoEnable) {
                for (LuaModule module : snapshotModules()) {
                    if (module.getGlobals() == globals && !module.isEnabled()) {
                        module.setEnabled(true);
                    }
                }
            }
            return true;
        } catch (Throwable throwable) {
            if (globals != null) {
                synchronized (modules) {
                    for (LuaModule module : new ArrayList<LuaModule>(modules.values())) {
                        if (module.getGlobals() == globals) {
                            modules.remove(module.getName().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
            log("Failed to load " + file.getName() + ": " + throwable);
            chat(EnumChatFormatting.RED + "Failed to load " + file.getName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private void disableAll() {
        for (LuaModule module : snapshotModules()) {
            try {
                module.setEnabled(false);
            } catch (Throwable throwable) {
                log("Disable error in " + module.getName() + ": " + throwable);
            }
        }
    }

    /** Detached module list for render-thread GUI adapters; registry ownership stays here. */
    public List<LuaModule> getModulesSnapshot() {
        return snapshotModules();
    }

    private List<LuaModule> snapshotModules() {
        synchronized (modules) {
            return new ArrayList<LuaModule>(modules.values());
        }
    }

    public LuaModule registerModule(Globals globals, String name, String displayName,
                                    String description, LuaTable table) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("module name is empty");
        }
        String key = name.toLowerCase(Locale.ROOT);
        LuaModule previous;
        synchronized (modules) {
            previous = modules.remove(key);
        }
        if (previous != null) {
            previous.setEnabled(false);
        }
        LuaModule module = new LuaModule(this, globals, name, displayName == null ? name : displayName,
                description, table);
        synchronized (modules) {
            modules.put(key, module);
        }
        log("Registered Lua module " + name + " (" + module.getDisplayName() + ")");
        return module;
    }

    public LuaModule findModule(String name) {
        if (name == null) {
            return null;
        }
        synchronized (modules) {
            LuaModule module = modules.get(name.toLowerCase(Locale.ROOT));
            if (module != null) {
                return module;
            }
            for (LuaModule candidate : modules.values()) {
                if (candidate.getName().equalsIgnoreCase(name)
                        || candidate.getDisplayName().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public boolean isEnabled(String name) {
        LuaModule module = findModule(name);
        return module != null && module.isEnabled();
    }

    /**
     * Native modules are not present in this base yet. Unknown names are a
     * deliberate no-op: telly.lua calls this for ScaffoldWalk, and a missing
     * feature must not throw a Lua error or break the script's state machine.
     */
    public boolean setState(String name, boolean state) {
        LuaModule module = findModule(name);
        if (module == null) {
            return false;
        }
        module.setEnabled(state);
        return true;
    }

    public boolean toggle(String name) {
        LuaModule module = findModule(name);
        if (module == null) {
            return false;
        }
        module.setEnabled(!module.isEnabled());
        return true;
    }

    public boolean handleCommand(String rawMessage) {
        if (rawMessage == null || !rawMessage.startsWith(".lua")) {
            return false;
        }
        init();
        String[] parts = rawMessage.trim().split("\\s+");
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "help";

        if ("help".equals(sub)) {
            chat("Commands: .lua list | .lua on <name> | .lua off <name> | .lua toggle <name> | .lua settings <name> | .lua reload");
            return true;
        }
        if ("list".equals(sub)) {
            List<LuaModule> snapshot = snapshotModules();
            if (snapshot.isEmpty()) {
                chat("No Lua modules loaded.");
            } else {
                StringBuilder builder = new StringBuilder("Lua modules:");
                for (LuaModule module : snapshot) {
                    builder.append(' ').append(module.getName()).append('=').append(module.isEnabled() ? "on" : "off");
                }
                chat(builder.toString());
            }
            return true;
        }
        if ("reload".equals(sub)) {
            if (scriptDir == null) {
                chat("Script directory is not available.");
            } else {
                reloadScripts();
                chat("Reloaded Lua scripts (" + snapshotModules().size() + " module(s)).");
            }
            return true;
        }
        if ("on".equals(sub) || "off".equals(sub) || "toggle".equals(sub)) {
            if (parts.length < 3) {
                chat("Usage: .lua " + sub + " <name>");
                return true;
            }
            String name = parts[2];
            boolean result;
            if ("on".equals(sub)) {
                result = setState(name, true);
            } else if ("off".equals(sub)) {
                result = setState(name, false);
            } else {
                result = toggle(name);
            }
            if (!result) {
                chat("Module not found: " + name);
            }
            return true;
        }
        if ("settings".equals(sub)) {
            if (parts.length < 3) {
                chat("Usage: .lua settings <name>");
                return true;
            }
            LuaModule module = findModule(parts[2]);
            if (module == null) {
                chat("Module not found: " + parts[2]);
                return true;
            }
            if (module.getSettings().isEmpty()) {
                chat(module.getName() + " has no settings.");
            } else {
                StringBuilder builder = new StringBuilder(module.getName() + " settings:");
                for (LuaSetting setting : module.getSettings()) {
                    builder.append(' ').append(setting.getId()).append('=').append(setting.toConfigString());
                }
                chat(builder.toString());
            }
            return true;
        }
        chat("Unknown .lua command: " + sub);
        return true;
    }

    public void chat(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GRAY + "[Lua] " + EnumChatFormatting.RESET + message));
        } else {
            log(message);
        }
    }

    public void shutdown(){disableAll();if(config!=null)config.flush();}

    public void onScriptError(LuaModule module, Throwable throwable) {
        log("Lua error in " + module.getName() + ": " + throwable);
        chat(EnumChatFormatting.RED + "Lua error in " + module.getName()
                + ": " + String.valueOf(throwable.getMessage()));
        module.setEnabled(false);
    }

    public LuaConfig getConfig() {
        if (config == null) {
            config = new LuaConfig(scriptDir == null ? null : new File(scriptDir, "lua-config.properties"));
        }
        return config;
    }

    public File getScriptDir() {
        return scriptDir;
    }

    public boolean isAutoEnable() {
        return autoEnable;
    }

    private void loadOptions() {
        File file = new File(scriptDir, "lua.properties");
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                options.load(in);
            } catch (Exception ignored) {
            }
        } else {
            options.setProperty("auto_enable", "false");
            try (OutputStream out = new FileOutputStream(file)) {
                options.store(out, "Moon Lua loader options");
            } catch (Exception ignored) {
            }
        }
        // Loading scripts never opts every module in, including legacy auto_enable=true files.
        // Users enable individual modules in the GUI or through their own explicit Lua calls.
        autoEnable = false;
    }

    private static void log(String message) {
        System.out.println("[Lua] " + message);
    }
}
