package dev.pier.lua;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseIoLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.luaj.vm2.lib.jse.JseOsLib;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

/**
 * Builds the sandboxed LuaJ environment and installs the globals used by Moon
 * scripts. Rendering and font metrics delegate to the target visual service;
 * script loading and CPU batch preparation do not allocate GL resources.
 */
public final class LuaApi {

    private interface LuaCall {
        Varargs invoke(Varargs args) throws Exception;
    }

    private LuaApi() {
    }

    public static Globals createGlobals(final LuaManager manager) {
        Globals globals = new Globals();
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        globals.load(new JseIoLib());
        globals.load(new JseOsLib());
        LoadState.install(globals);
        LuaC.install(globals);

        // Remove everything that can reach the filesystem, the process or Java.
        globals.set("package", LuaValue.NIL);
        globals.set("io", LuaValue.NIL);
        globals.set("os", LuaValue.NIL);
        globals.set("debug", LuaValue.NIL);
        globals.set("luajava", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("module", LuaValue.NIL);
        globals.set("load", LuaValue.NIL);

        globals.set("print", fn(args -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i <= args.narg(); i++) {
                if (i > 1) {
                    builder.append('\t');
                }
                builder.append(args.arg(i).tojstring());
            }
            manager.chat(builder.toString());
            return LuaValue.NONE;
        }));

        globals.set("modules", modulesTable(manager, globals));
        globals.set("player", playerTable(manager));
        globals.set("client", clientTable(manager));
        globals.set("inventory", inventoryTable());
        globals.set("world", worldTable());
        globals.set("enums", enumsTable());
        globals.set("types", typesTable());
        globals.set("render", renderTable(manager));
        globals.set("font", fontTable());
        return globals;
    }

    private static LuaTable modulesTable(final LuaManager manager, final Globals globals) {
        final LuaTable modules = new LuaTable();

        modules.set("register", fn(args -> {
            Varargs a = strip(args, modules);
            String name = a.checkjstring(1);
            String display = a.checkjstring(2);
            String description = "";
            LuaTable table;
            if (a.narg() >= 4 && a.arg(4).istable()) {
                description = a.arg(3).tojstring();
                table = a.checktable(4);
            } else {
                table = a.checktable(3);
            }
            manager.registerModule(globals, name, display, description, table);
            return LuaValue.NONE;
        }));

        modules.set("is_enabled", fn(args -> {
            Varargs a = strip(args, modules);
            return LuaValue.valueOf(manager.isEnabled(a.checkjstring(1)));
        }));

        modules.set("set_state", fn(args -> {
            Varargs a = strip(args, modules);
            manager.setState(a.checkjstring(1), a.checkboolean(2));
            return LuaValue.NONE;
        }));

        modules.set("toggle", fn(args -> {
            Varargs a = strip(args, modules);
            manager.toggle(a.checkjstring(1));
            return LuaValue.NONE;
        }));

        modules.set("create_boolean_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module != null) {
                module.addSetting(new LuaSetting(a.checkjstring(2), a.checkjstring(3),
                        LuaSetting.Type.BOOLEAN, a.checkboolean(4)));
            }
            return LuaValue.NONE;
        }));

        modules.set("create_integer_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module != null) {
                module.addSetting(new LuaSetting(a.checkjstring(2), a.checkjstring(3),
                        LuaSetting.Type.INTEGER, a.checkint(4)));
            }
            return LuaValue.NONE;
        }));

        modules.set("create_float_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module != null) {
                module.addSetting(new LuaSetting(a.checkjstring(2), a.checkjstring(3),
                        LuaSetting.Type.FLOAT, a.checkdouble(4)));
            }
            return LuaValue.NONE;
        }));

        modules.set("create_color_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module != null) {
                module.addSetting(new LuaSetting(a.checkjstring(2), a.checkjstring(3),
                        LuaSetting.Type.COLOR, colorBits(a.arg(4))));
            }
            return LuaValue.NONE;
        }));

        modules.set("create_text_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module != null) {
                module.addSetting(new LuaSetting(a.checkjstring(2), a.checkjstring(3),
                        LuaSetting.Type.TEXT, a.checkjstring(4)));
            }
            return LuaValue.NONE;
        }));

        modules.set("get_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module == null) {
                return LuaValue.NIL;
            }
            return module.getSettingValue(a.checkjstring(2));
        }));

        modules.set("set_setting", fn(args -> {
            Varargs a = strip(args, modules);
            LuaModule module = manager.findModule(a.checkjstring(1));
            if (module != null) {
                module.setSetting(a.checkjstring(2), a.arg(3));
            }
            return LuaValue.NONE;
        }));

        return modules;
    }

    private static LuaTable playerTable(final LuaManager manager) {
        LuaTable player = new LuaTable();

        player.set("is_moving", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && (p.moveForward != 0.0F || p.moveStrafing != 0.0F));
        }));
        player.set("is_on_ground", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.onGround);
        }));
        player.set("jump", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null && p.onGround) {
                p.motionY = 0.42D;
                p.isAirBorne = true;
            }
            return LuaValue.NONE;
        }));
        player.set("set_motion_speed", fn(args -> {
            EntityPlayerSP p = player();
            double speed = args.checkdouble(1);
            if (p != null) {
                double current = Math.hypot(p.motionX, p.motionZ);
                if (current > 1.0E-4D) {
                    p.motionX = p.motionX / current * speed;
                    p.motionZ = p.motionZ / current * speed;
                }
            }
            return LuaValue.NONE;
        }));
        player.set("get_hurt_time", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0 : p.hurtTime);
        }));
        player.set("get_name", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? "" : p.getName());
        }));
        player.set("get_ticks_existed", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0 : p.ticksExisted);
        }));
        player.set("get_id", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? -1 : p.getEntityId());
        }));
        player.set("get_health", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : (double) p.getHealth());
        }));
        player.set("get_max_health", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : (double) p.getMaxHealth());
        }));
        player.set("get_absorption", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : (double) p.getAbsorptionAmount());
        }));
        player.set("get_food_amount", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0 : p.getFoodStats().getFoodLevel());
        }));
        player.set("is_sprinting", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isSprinting());
        }));
        player.set("is_sneaking", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isSneaking());
        }));
        player.set("is_in_water", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isInWater());
        }));
        player.set("is_burning", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isBurning());
        }));
        player.set("is_riding", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isRiding());
        }));
        player.set("is_dead", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isDead);
        }));
        player.set("is_on_ladder", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isOnLadder());
        }));
        player.set("get_position_x", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : p.posX);
        }));
        player.set("get_position_y", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : p.posY);
        }));
        player.set("get_position_z", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : p.posZ);
        }));
        player.set("get_motion_x", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : p.motionX);
        }));
        player.set("get_motion_y", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : p.motionY);
        }));
        player.set("get_motion_z", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : p.motionZ);
        }));
        player.set("set_motion_x", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.motionX = args.checkdouble(1);
            }
            return LuaValue.NONE;
        }));
        player.set("set_motion_y", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.motionY = args.checkdouble(1);
            }
            return LuaValue.NONE;
        }));
        player.set("set_motion_z", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.motionZ = args.checkdouble(1);
            }
            return LuaValue.NONE;
        }));
        player.set("get_yaw", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : (double) p.rotationYaw);
        }));
        player.set("get_pitch", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : (double) p.rotationPitch);
        }));
        player.set("set_yaw", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.rotationYaw = (float) args.checkdouble(1);
            }
            return LuaValue.NONE;
        }));
        player.set("set_pitch", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.rotationPitch = (float) args.checkdouble(1);
            }
            return LuaValue.NONE;
        }));
        player.set("set_sprinting", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.setSprinting(args.checkboolean(1));
            }
            return LuaValue.NONE;
        }));
        player.set("set_sneaking", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.setSneaking(args.checkboolean(1));
            }
            return LuaValue.NONE;
        }));
        player.set("get_held_item_slot", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? -1 : p.inventory.currentItem);
        }));
        player.set("set_held_item", fn(args -> {
            EntityPlayerSP p = player();
            if (p != null) {
                p.inventory.currentItem = args.checkint(1);
            }
            return LuaValue.NONE;
        }));
        player.set("get_input", fn(args -> {
            EntityPlayerSP p = player();
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(p == null ? 0.0D : (double) p.moveStrafing));
            out.set("y", LuaValue.valueOf(p == null ? 0.0D : (double) p.moveForward));
            return out;
        }));
        player.set("get_speed", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : Math.hypot(p.motionX, p.motionZ));
        }));
        player.set("get_fall_distance", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p == null ? 0.0D : (double) p.fallDistance);
        }));
        player.set("collided_vertically", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isCollidedVertically);
        }));
        player.set("collided_horizontally", fn(args -> {
            EntityPlayerSP p = player();
            return LuaValue.valueOf(p != null && p.isCollidedHorizontally);
        }));
        player.set("distance_to", fn(args -> {
            EntityPlayerSP p = player();
            if (p == null) {
                return LuaValue.valueOf(0.0D);
            }
            return LuaValue.valueOf(p.getDistance(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3)));
        }));

        player.set("send_packet_no_event", fn(args -> {
            Varargs a = strip(args, player);
            int id = a.checkint(1);
            LuaTable data = a.checktable(2);
            EntityPlayerSP p = player();
            if (p == null) {
                return LuaValue.NONE;
            }
            manager.sendPacketNoEvent(LuaPackets.createOutbound(id, data, p));
            return LuaValue.NONE;
        }));
        player.set("send_packet", fn(args -> {
            Varargs a = strip(args, player);
            int id = a.checkint(1);
            LuaTable data = a.checktable(2);
            EntityPlayerSP p = player();
            if (p == null) {
                return LuaValue.NONE;
            }
            p.sendQueue.addToSendQueue(LuaPackets.createOutbound(id, data, p));
            return LuaValue.NONE;
        }));

        return player;
    }

    private static LuaTable clientTable(final LuaManager manager) {
        LuaTable client = new LuaTable();

        client.set("print", fn(args -> {
            Varargs a = strip(args, client);
            manager.chat(a.checkjstring(1));
            return LuaValue.NONE;
        }));
        client.set("message", fn(args -> {
            Varargs a = strip(args, client);
            manager.chat(a.checkjstring(1));
            return LuaValue.NONE;
        }));
        client.set("fps", fn(args -> LuaValue.valueOf(Minecraft.getDebugFPS())));
        client.set("time", fn(args -> LuaValue.valueOf((double) System.currentTimeMillis())));
        client.set("get_scaled_width", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            return LuaValue.valueOf(mc == null ? 0 : new ScaledResolution(mc).getScaledWidth());
        }));
        client.set("get_scaled_height", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            return LuaValue.valueOf(mc == null ? 0 : new ScaledResolution(mc).getScaledHeight());
        }));
        client.set("get_width", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            return LuaValue.valueOf(mc == null ? 0 : mc.displayWidth);
        }));
        client.set("get_height", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            return LuaValue.valueOf(mc == null ? 0 : mc.displayHeight);
        }));
        client.set("is_key_down", fn(args -> {
            Varargs a = strip(args, client);
            return LuaValue.valueOf(Keyboard.isKeyDown(a.checkint(1)));
        }));
        client.set("is_mouse_down", fn(args -> {
            Varargs a = strip(args, client);
            return LuaValue.valueOf(Mouse.isButtonDown(a.checkint(1)));
        }));
        client.set("set_timer_speed", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.timer.timerSpeed = (float) args.checkdouble(1);
            }
            return LuaValue.NONE;
        }));
        client.set("current_kill_aura_target", fn(args -> LuaValue.valueOf(-1)));
        client.set("force_effects_redraw", fn(args -> LuaValue.NONE));

        return client;
    }

    private static LuaTable inventoryTable() {
        LuaTable inventory = new LuaTable();

        inventory.set("get_item", fn(args -> {
            Varargs a = strip(args, inventory);
            EntityPlayerSP p = player();
            if (p == null) {
                return LuaValue.NIL;
            }
            ItemStack stack = p.inventory.getStackInSlot(a.checkint(1));
            return stack == null ? LuaValue.NIL : itemTable(stack);
        }));
        inventory.set("get_item_in_window", fn(args -> {
            Varargs a = strip(args, inventory);
            EntityPlayerSP p = player();
            if (p == null) {
                return LuaValue.NIL;
            }
            ItemStack stack = p.inventory.getStackInSlot(a.checkint(2));
            return stack == null ? LuaValue.NIL : itemTable(stack);
        }));
        inventory.set("drop", fn(args -> LuaValue.NONE));
        inventory.set("shift", fn(args -> LuaValue.NONE));
        inventory.set("swap", fn(args -> LuaValue.NONE));
        inventory.set("window_click", fn(args -> LuaValue.NONE));
        inventory.set("upper_container_id", fn(args -> LuaValue.valueOf(0)));
        inventory.set("lower_container_id", fn(args -> LuaValue.valueOf(0)));
        inventory.set("player_inventory_id", fn(args -> LuaValue.valueOf(0)));
        inventory.set("current_inventory", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.currentScreen == null) {
                return LuaValue.valueOf("NIL");
            }
            return LuaValue.valueOf(mc.currentScreen.getClass().getSimpleName());
        }));

        return inventory;
    }

    private static LuaTable worldTable() {
        LuaTable world = new LuaTable();

        world.set("get_entities", fn(args -> {
            LuaTable out = new LuaTable();
            WorldClient level = level();
            if (level == null) {
                return out;
            }
            int index = 1;
            for (Entity entity : level.loadedEntityList) {
                out.set(index++, LuaValue.valueOf(entity.getEntityId()));
            }
            return out;
        }));
        world.set("get_players", fn(args -> {
            LuaTable out = new LuaTable();
            WorldClient level = level();
            if (level == null) {
                return out;
            }
            int index = 1;
            for (EntityPlayer entity : level.playerEntities) {
                out.set(index++, LuaValue.valueOf(entity.getEntityId()));
            }
            return out;
        }));
        world.set("get_name", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity == null ? "" : entity.getName());
        }));
        world.set("get_position_x", fn(args -> position(args.checkint(1), 0)));
        world.set("get_position_y", fn(args -> position(args.checkint(1), 1)));
        world.set("get_position_z", fn(args -> position(args.checkint(1), 2)));
        world.set("get_yaw", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity == null ? 0.0D : (double) entity.rotationYaw);
        }));
        world.set("get_pitch", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity == null ? 0.0D : (double) entity.rotationPitch);
        }));
        world.set("get_ticks_existed", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity == null ? 0 : entity.ticksExisted);
        }));
        world.set("get_health", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity instanceof EntityLivingBase
                    ? (double) ((EntityLivingBase) entity).getHealth() : 0.0D);
        }));
        world.set("get_max_health", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity instanceof EntityLivingBase
                    ? (double) ((EntityLivingBase) entity).getMaxHealth() : 0.0D);
        }));
        world.set("is_player", fn(args -> LuaValue.valueOf(entity(args.checkint(1)) instanceof EntityPlayer)));
        world.set("is_sprinting", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity != null && entity.isSprinting());
        }));
        world.set("is_sneaking", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity != null && entity.isSneaking());
        }));
        world.set("is_invisible", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity != null && entity.isInvisible());
        }));
        world.set("is_burning", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity != null && entity.isBurning());
        }));
        world.set("is_riding", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity != null && entity.isRiding());
        }));
        world.set("is_bot", fn(args -> LuaValue.FALSE));
        world.set("is_target", fn(args -> LuaValue.FALSE));
        world.set("is_friend", fn(args -> LuaValue.FALSE));
        world.set("get_width", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity == null ? 0.0D : (double) entity.width);
        }));
        world.set("get_height", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity == null ? 0.0D : (double) entity.height);
        }));
        world.set("get_hurt_time", fn(args -> {
            Entity entity = entity(args.checkint(1));
            return LuaValue.valueOf(entity instanceof EntityLivingBase
                    ? ((EntityLivingBase) entity).hurtTime : 0);
        }));
        world.set("get_block_at", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) {
                return LuaValue.NIL;
            }
            BlockPos pos = new BlockPos(MathHelper.floor_double(args.checkdouble(1)),
                    MathHelper.floor_double(args.checkdouble(2)),
                    MathHelper.floor_double(args.checkdouble(3)));
            LuaTable out = new LuaTable();
            out.set("name", LuaValue.valueOf(mc.theWorld.getBlockState(pos).getBlock().getUnlocalizedName()));
            return out;
        }));
        world.set("remove", fn(args -> {
            WorldClient level = level();
            Entity entity = entity(args.checkint(1));
            if (level != null && entity != null) {
                level.removeEntity(entity);
            }
            return LuaValue.NONE;
        }));
        world.set("get_active_effects", fn(args -> new LuaTable()));
        world.set("has_effect", fn(args -> LuaValue.FALSE));
        world.set("get_effect", fn(args -> LuaValue.NIL));
        world.set("get_biome", fn(args -> {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP p = player();
            if (mc == null || mc.theWorld == null || p == null) {
                return LuaValue.valueOf("");
            }
            return LuaValue.valueOf(mc.theWorld.getBiomeGenForCoords(
                    new BlockPos(MathHelper.floor_double(p.posX), 64,
                            MathHelper.floor_double(p.posZ))).biomeName);
        }));

        return world;
    }

    private static LuaTable enumsTable() {
        LuaTable enums = new LuaTable();
        enums.set("template_type", enumTable("RECT", "GRADIENT", "ROUNDED_RECT",
                "OUTLINED_ROUNDED_RECT", "TEXT", "TRIANGLE"));
        enums.set("custom_font_type", enumTable("ROBOTO_MEDIUM", "INTER_MEDIUM", "TAHOMA",
                "VERDANA", "SF", "PRODUCT_SANS", "MATERIAL_ICONS",
                "MATERIAL_SYMBOLS_OUTLINED_REGULAR", "VOLTE_SEMI_BOLD"));
        enums.set("facing", enumTable("DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"));
        enums.set("entity_action", enumTable("START_SNEAKING", "STOP_SNEAKING", "STOP_SLEEPING",
                "START_SPRINTING", "STOP_SPRINTING", "RIDING_JUMP", "OPEN_INVENTORY"));
        enums.set("digging_action", enumTable("START_DESTROY_BLOCK", "ABORT_DESTROY_BLOCK",
                "STOP_DESTROY_BLOCK", "DROP_ALL_ITEMS", "DROP_ITEM", "RELEASE_USE_ITEM"));
        enums.set("hittype", enumTable("ENTITY", "BLOCK", "MISS"));
        enums.set("client_status_actions", enumTable("PERFORM_RESPAWN", "REQUEST_STATS",
                "OPEN_INVENTORY_ACHIEVEMENT"));
        return enums;
    }

    private static LuaTable typesTable() {
        LuaTable types = new LuaTable();

        LuaTable template = new LuaTable();
        template.set("rect", rectTemplate());
        template.set("rounded_rect", roundedRectTemplate());
        template.set("outlined_rounded_rect", outlinedRoundedRectTemplate());
        template.set("gradient", gradientTemplate());
        template.set("triangle", triangleTemplate());
        template.set("text", textTemplate());
        types.set("template", template);

        LuaTable vec3d = new LuaTable();
        vec3d.set("new", fn(args -> {
            Varargs a = strip(args, vec3d);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("y", LuaValue.valueOf(a.checkdouble(2)));
            out.set("z", LuaValue.valueOf(a.checkdouble(3)));
            return out;
        }));
        types.set("vec3d", vec3d);

        LuaTable vec2f = new LuaTable();
        vec2f.set("new", fn(args -> {
            Varargs a = strip(args, vec2f);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("y", LuaValue.valueOf(a.checkdouble(2)));
            return out;
        }));
        types.set("vec2f", vec2f);

        LuaTable vec3i = new LuaTable();
        vec3i.set("new", fn(args -> {
            Varargs a = strip(args, vec3i);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkint(1)));
            out.set("y", LuaValue.valueOf(a.checkint(2)));
            out.set("z", LuaValue.valueOf(a.checkint(3)));
            return out;
        }));
        types.set("vec3i", vec3i);

        return types;
    }

    private static LuaTable rectTemplate() {
        final LuaTable constructor = new LuaTable();
        constructor.set("new", fn(args -> {
            Varargs a = strip(args, constructor);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("y", LuaValue.valueOf(a.checkdouble(2)));
            out.set("width", LuaValue.valueOf(a.checkdouble(3)));
            out.set("height", LuaValue.valueOf(a.checkdouble(4)));
            out.set("color", LuaValue.valueOf(colorBits(a.arg(5))));
            return out;
        }));
        return constructor;
    }

    private static LuaTable roundedRectTemplate() {
        final LuaTable constructor = new LuaTable();
        constructor.set("new", fn(args -> {
            Varargs a = strip(args, constructor);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("y", LuaValue.valueOf(a.checkdouble(2)));
            out.set("width", LuaValue.valueOf(a.checkdouble(3)));
            out.set("height", LuaValue.valueOf(a.checkdouble(4)));
            out.set("radius", LuaValue.valueOf(a.checkdouble(5)));
            out.set("color", LuaValue.valueOf(colorBits(a.arg(6))));
            return out;
        }));
        return constructor;
    }

    private static LuaTable outlinedRoundedRectTemplate() {
        final LuaTable constructor = new LuaTable();
        constructor.set("new", fn(args -> {
            Varargs a = strip(args, constructor);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("y", LuaValue.valueOf(a.checkdouble(2)));
            out.set("width", LuaValue.valueOf(a.checkdouble(3)));
            out.set("height", LuaValue.valueOf(a.checkdouble(4)));
            out.set("radius", LuaValue.valueOf(a.checkdouble(5)));
            out.set("thickness", LuaValue.valueOf(a.checkdouble(6)));
            out.set("color", LuaValue.valueOf(colorBits(a.arg(7))));
            return out;
        }));
        return constructor;
    }

    private static LuaTable gradientTemplate() {
        final LuaTable constructor = new LuaTable();
        constructor.set("new", fn(args -> {
            Varargs a = strip(args, constructor);
            LuaTable out = new LuaTable();
            out.set("x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("y", LuaValue.valueOf(a.checkdouble(2)));
            out.set("width", LuaValue.valueOf(a.checkdouble(3)));
            out.set("height", LuaValue.valueOf(a.checkdouble(4)));
            out.set("from", LuaValue.valueOf(colorBits(a.arg(5))));
            out.set("to", LuaValue.valueOf(colorBits(a.arg(6))));
            out.set("vertical", LuaValue.valueOf(a.checkboolean(7)));
            return out;
        }));
        return constructor;
    }

    private static LuaTable triangleTemplate() {
        final LuaTable constructor = new LuaTable();
        constructor.set("new", fn(args -> {
            Varargs a = strip(args, constructor);
            LuaTable out = new LuaTable();
            out.set("top_x", LuaValue.valueOf(a.checkdouble(1)));
            out.set("top_y", LuaValue.valueOf(a.checkdouble(2)));
            out.set("left_x", LuaValue.valueOf(a.checkdouble(3)));
            out.set("left_y", LuaValue.valueOf(a.checkdouble(4)));
            out.set("right_x", LuaValue.valueOf(a.checkdouble(5)));
            out.set("right_y", LuaValue.valueOf(a.checkdouble(6)));
            out.set("color", LuaValue.valueOf(colorBits(a.arg(7))));
            return out;
        }));
        return constructor;
    }

    private static LuaTable textTemplate() {
        final LuaTable constructor = new LuaTable();
        constructor.set("new", fn(args -> {
            Varargs a = strip(args, constructor);
            LuaTable out = new LuaTable();
            out.set("font_type", LuaValue.valueOf(a.checkint(1)));
            out.set("text", LuaValue.valueOf(a.checkjstring(2)));
            out.set("size", LuaValue.valueOf(a.checkdouble(3)));
            out.set("x", LuaValue.valueOf(a.checkdouble(4)));
            out.set("y", LuaValue.valueOf(a.checkdouble(5)));
            out.set("color", LuaValue.valueOf(colorBits(a.arg(6))));
            out.set("shadow", LuaValue.valueOf(a.checkboolean(7)));
            return out;
        }));
        return constructor;
    }

    private static LuaTable renderTable(final LuaManager manager) {
        return moon.client.McpLuaRenderer.renderTable();
    }

    private static LuaTable fontTable() {
        return moon.client.McpLuaRenderer.fontTable();
    }

    private static LuaTable itemTable(ItemStack stack) {
        LuaTable item = new LuaTable();
        item.set("name", LuaValue.valueOf(stack.getItem().getUnlocalizedName()));
        item.set("display_name", LuaValue.valueOf(stack.getDisplayName()));
        item.set("stack_size", LuaValue.valueOf(stack.stackSize));
        item.set("max_stack_size", LuaValue.valueOf(stack.getMaxStackSize()));
        item.set("damage", LuaValue.valueOf(stack.getItemDamage()));
        item.set("max_damage", LuaValue.valueOf(stack.getMaxDamage()));
        item.set("solid", LuaValue.FALSE);
        item.set("enchantments", new LuaTable());
        item.set("potions", new LuaTable());
        return item;
    }

    private static LuaTable enumTable(String... names) {
        LuaTable out = new LuaTable();
        for (int i = 0; i < names.length; i++) {
            out.set(names[i], LuaValue.valueOf(i));
        }
        return out;
    }

    private static LuaValue position(int entityId, int axis) {
        Entity entity = entity(entityId);
        if (entity == null) {
            return LuaValue.valueOf(0.0D);
        }
        return LuaValue.valueOf(axis == 0 ? entity.posX : axis == 1 ? entity.posY : entity.posZ);
    }

    private static Entity entity(int id) {
        WorldClient level = level();
        return level == null ? null : level.getEntityByID(id);
    }

    private static WorldClient level() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.theWorld;
    }

    private static EntityPlayerSP player() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.thePlayer;
    }

    private static int colorBits(LuaValue value) {
        return (int) ((long) value.todouble());
    }

    private static Varargs strip(Varargs args, LuaValue self) {
        if (args.narg() > 0 && args.arg(1) == self) {
            return args.subargs(2);
        }
        return args;
    }

    private static LuaValue fn(final LuaCall call) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return call.invoke(args);
                } catch (LuaError error) {
                    throw error;
                } catch (Throwable throwable) {
                    throw new LuaError(throwable);
                }
            }
        };
    }
}
