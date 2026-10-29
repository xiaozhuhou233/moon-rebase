package dev.pier.lua;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C11PacketEnchantItem;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.network.play.client.C18PacketSpectate;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Packet-to-Lua table fields and Lua-table-to-packet reconstruction.
 *
 * This is a compatibility layer, not a mirror of Moon's internal packet
 * schemas. Canonical field names follow the 1.8.9 protocol; common aliases
 * (ground/on_ground, entity_id/id, action/status) are also exposed.
 */
final class LuaPackets {

    private LuaPackets() {
    }

    static LuaTable read(Packet packet, int packetId) {
        LuaTable event = new LuaTable();
        event.set("packet_id", LuaValue.valueOf(packetId));
        event.set("cancel", LuaValue.FALSE);

        if (packet instanceof C03PacketPlayer) {
            readPlayer(event, (C03PacketPlayer) packet);
        } else if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging p = (C07PacketPlayerDigging) packet;
            event.set("action", LuaValue.valueOf(p.getStatus().ordinal()));
            event.set("status", LuaValue.valueOf(p.getStatus().ordinal()));
            event.set("x", LuaValue.valueOf(p.getPosition().getX()));
            event.set("y", LuaValue.valueOf(p.getPosition().getY()));
            event.set("z", LuaValue.valueOf(p.getPosition().getZ()));
            event.set("facing", LuaValue.valueOf(p.getFacing().ordinal()));
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
            event.set("x", LuaValue.valueOf(p.getPosition().getX()));
            event.set("y", LuaValue.valueOf(p.getPosition().getY()));
            event.set("z", LuaValue.valueOf(p.getPosition().getZ()));
            event.set("facing", LuaValue.valueOf(p.getPlacedBlockDirection()));
            event.set("offset_x", LuaValue.valueOf((double) p.getPlacedBlockOffsetX()));
            event.set("offset_y", LuaValue.valueOf((double) p.getPlacedBlockOffsetY()));
            event.set("offset_z", LuaValue.valueOf((double) p.getPlacedBlockOffsetZ()));
        } else if (packet instanceof C00PacketKeepAlive) {
            event.set("id", LuaValue.valueOf(((C00PacketKeepAlive) packet).getKey()));
        } else if (packet instanceof S00PacketKeepAlive) {
            event.set("id", LuaValue.valueOf(((S00PacketKeepAlive) packet).func_149134_c()));
        } else if (packet instanceof C01PacketChatMessage) {
            event.set("message", LuaValue.valueOf(((C01PacketChatMessage) packet).getMessage()));
        } else if (packet instanceof S02PacketChat) {
            S02PacketChat p = (S02PacketChat) packet;
            event.set("message", LuaValue.valueOf(p.getChatComponent().getUnformattedText()));
            event.set("type", LuaValue.valueOf((int) p.getType()));
        } else if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) packet;
            event.set("target", LuaValue.valueOf(intField(p, "entityId", -1)));
            event.set("action", LuaValue.valueOf(p.getAction().ordinal()));
            Vec3 hit = p.getHitVec();
            if (hit != null) {
                event.set("hit_x", LuaValue.valueOf(hit.xCoord));
                event.set("hit_y", LuaValue.valueOf(hit.yCoord));
                event.set("hit_z", LuaValue.valueOf(hit.zCoord));
            }
        } else if (packet instanceof C09PacketHeldItemChange) {
            event.set("slot", LuaValue.valueOf(((C09PacketHeldItemChange) packet).getSlotId()));
        } else if (packet instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction p = (C0BPacketEntityAction) packet;
            event.set("action", LuaValue.valueOf(p.getAction().ordinal()));
            event.set("aux_data", LuaValue.valueOf(p.getAuxData()));
        } else if (packet instanceof C0CPacketInput) {
            C0CPacketInput p = (C0CPacketInput) packet;
            event.set("strafe", LuaValue.valueOf((double) p.getStrafeSpeed()));
            event.set("forward", LuaValue.valueOf((double) p.getForwardSpeed()));
            event.set("jumping", LuaValue.valueOf(p.isJumping()));
            event.set("sneaking", LuaValue.valueOf(p.isSneaking()));
        } else if (packet instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow p = (C0EPacketClickWindow) packet;
            event.set("window_id", LuaValue.valueOf(p.getWindowId()));
            event.set("slot_id", LuaValue.valueOf(p.getSlotId()));
            event.set("mouse_button", LuaValue.valueOf(p.getUsedButton()));
            event.set("mode", LuaValue.valueOf(p.getMode()));
            event.set("action_number", LuaValue.valueOf((int) p.getActionNumber()));
        } else if (packet instanceof C0FPacketConfirmTransaction) {
            C0FPacketConfirmTransaction p = (C0FPacketConfirmTransaction) packet;
            event.set("window_id", LuaValue.valueOf(p.getWindowId()));
            event.set("action_number", LuaValue.valueOf((int) p.getUid()));
            event.set("uid", LuaValue.valueOf((int) p.getUid()));
            event.set("accepted", LuaValue.valueOf(booleanField(p, "accepted", false)));
        } else if (packet instanceof C11PacketEnchantItem) {
            C11PacketEnchantItem p = (C11PacketEnchantItem) packet;
            event.set("window_id", LuaValue.valueOf(p.getWindowId()));
            event.set("enchantment", LuaValue.valueOf(p.getButton()));
        } else if (packet instanceof C13PacketPlayerAbilities) {
            C13PacketPlayerAbilities p = (C13PacketPlayerAbilities) packet;
            event.set("invulnerable", LuaValue.valueOf(p.isInvulnerable()));
            event.set("flying", LuaValue.valueOf(p.isFlying()));
            event.set("allow_flying", LuaValue.valueOf(p.isAllowFlying()));
            event.set("creative_mode", LuaValue.valueOf(p.isCreativeMode()));
            event.set("flying_speed", LuaValue.valueOf((double) floatField(p, "flySpeed", 0.0F)));
            event.set("walking_speed", LuaValue.valueOf((double) floatField(p, "walkSpeed", 0.0F)));
        } else if (packet instanceof C14PacketTabComplete) {
            C14PacketTabComplete p = (C14PacketTabComplete) packet;
            event.set("text", LuaValue.valueOf(p.getMessage()));
            BlockPos pos = p.getTargetBlock();
            if (pos != null) {
                event.set("x", LuaValue.valueOf(pos.getX()));
                event.set("y", LuaValue.valueOf(pos.getY()));
                event.set("z", LuaValue.valueOf(pos.getZ()));
            }
        } else if (packet instanceof C18PacketSpectate) {
            event.set("target", LuaValue.valueOf(String.valueOf(field((C18PacketSpectate) packet, "id"))));
        } else if (packet instanceof C19PacketResourcePackStatus) {
            C19PacketResourcePackStatus p = (C19PacketResourcePackStatus) packet;
            event.set("hash", LuaValue.valueOf(String.valueOf(field(p, "hash"))));
            event.set("status", LuaValue.valueOf(((C19PacketResourcePackStatus.Action) field(p, "status")).ordinal()));
        } else if (packet instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook p = (S08PacketPlayerPosLook) packet;
            event.set("x", LuaValue.valueOf(p.getX()));
            event.set("y", LuaValue.valueOf(p.getY()));
            event.set("z", LuaValue.valueOf(p.getZ()));
            event.set("yaw", LuaValue.valueOf((double) p.getYaw()));
            event.set("pitch", LuaValue.valueOf((double) p.getPitch()));
            event.set("flags", LuaValue.valueOf(S08PacketPlayerPosLook.EnumFlags.func_180056_a(p.func_179834_f())));
        } else if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity p = (S12PacketEntityVelocity) packet;
            event.set("entity_id", LuaValue.valueOf(p.getEntityID()));
            event.set("motion_x", LuaValue.valueOf(p.getMotionX() / 8000.0D));
            event.set("motion_y", LuaValue.valueOf(p.getMotionY() / 8000.0D));
            event.set("motion_z", LuaValue.valueOf(p.getMotionZ() / 8000.0D));
        } else if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            event.set("entity_id", LuaValue.valueOf(intField(p, "entityId", -1)));
            event.set("x", LuaValue.valueOf(p.func_149062_c()));
            event.set("y", LuaValue.valueOf(p.func_149061_d()));
            event.set("z", LuaValue.valueOf(p.func_149064_e()));
            event.set("yaw", LuaValue.valueOf(p.func_149066_f()));
            event.set("pitch", LuaValue.valueOf(p.func_149063_g()));
            event.set("ground", LuaValue.valueOf(p.getOnGround()));
            event.set("on_ground", LuaValue.valueOf(p.getOnGround()));
            event.set("rotating", LuaValue.valueOf(p.func_149060_h()));
        } else if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion p = (S27PacketExplosion) packet;
            event.set("x", LuaValue.valueOf(p.getX()));
            event.set("y", LuaValue.valueOf(p.getY()));
            event.set("z", LuaValue.valueOf(p.getZ()));
            event.set("strength", LuaValue.valueOf((double) p.getStrength()));
            event.set("motion_x", LuaValue.valueOf((double) p.func_149149_c()));
            event.set("motion_y", LuaValue.valueOf((double) p.func_149144_d()));
            event.set("motion_z", LuaValue.valueOf((double) p.func_149147_e()));
            LuaTable blocks = new LuaTable();
            List<BlockPos> positions = p.getAffectedBlockPositions();
            if (positions != null) {
                int index = 1;
                for (BlockPos pos : positions) {
                    LuaTable block = new LuaTable();
                    block.set("x", LuaValue.valueOf(pos.getX()));
                    block.set("y", LuaValue.valueOf(pos.getY()));
                    block.set("z", LuaValue.valueOf(pos.getZ()));
                    blocks.set(index++, block);
                }
            }
            event.set("affected_blocks", blocks);
        } else if (packet instanceof S32PacketConfirmTransaction) {
            S32PacketConfirmTransaction p = (S32PacketConfirmTransaction) packet;
            event.set("window_id", LuaValue.valueOf(p.getWindowId()));
            event.set("action_number", LuaValue.valueOf((int) p.getActionNumber()));
            event.set("accepted", LuaValue.valueOf(p.func_148888_e()));
        } else if (packet instanceof S48PacketResourcePackSend) {
            S48PacketResourcePackSend p = (S48PacketResourcePackSend) packet;
            event.set("url", LuaValue.valueOf(p.getURL()));
            event.set("hash", LuaValue.valueOf(p.getHash()));
        }

        return event;
    }

    private static void readPlayer(LuaTable event, C03PacketPlayer p) {
        event.set("x", LuaValue.valueOf(p.getPositionX()));
        event.set("y", LuaValue.valueOf(p.getPositionY()));
        event.set("z", LuaValue.valueOf(p.getPositionZ()));
        event.set("yaw", LuaValue.valueOf((double) p.getYaw()));
        event.set("pitch", LuaValue.valueOf((double) p.getPitch()));
        event.set("ground", LuaValue.valueOf(p.isOnGround()));
        event.set("on_ground", LuaValue.valueOf(p.isOnGround()));
    }

    /**
     * Applies edits to an inbound packet before it is processed.
     */
    static void applyInbound(LuaTable event, Packet packet) {
        if (packet instanceof S00PacketKeepAlive) {
            setInt(packet, "id", intValue(event, "id", ((S00PacketKeepAlive) packet).func_149134_c()));
        } else if (packet instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook p = (S08PacketPlayerPosLook) packet;
            setDouble(packet, "x", doubleValue(event, "x", p.getX()));
            setDouble(packet, "y", doubleValue(event, "y", p.getY()));
            setDouble(packet, "z", doubleValue(event, "z", p.getZ()));
            setFloat(packet, "yaw", (float) doubleValue(event, "yaw", p.getYaw()));
            setFloat(packet, "pitch", (float) doubleValue(event, "pitch", p.getPitch()));
        } else if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            setByte(packet, "posX", (byte) intValue(event, "x", p.func_149062_c()));
            setByte(packet, "posY", (byte) intValue(event, "y", p.func_149061_d()));
            setByte(packet, "posZ", (byte) intValue(event, "z", p.func_149064_e()));
            setByte(packet, "yaw", (byte) intValue(event, "yaw", p.func_149066_f()));
            setByte(packet, "pitch", (byte) intValue(event, "pitch", p.func_149063_g()));
            setBoolean(packet, "onGround", booleanValue(event, "ground", p.getOnGround()));
        } else if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion p = (S27PacketExplosion) packet;
            setDouble(packet, "posX", doubleValue(event, "x", p.getX()));
            setDouble(packet, "posY", doubleValue(event, "y", p.getY()));
            setDouble(packet, "posZ", doubleValue(event, "z", p.getZ()));
            setFloat(packet, "strength", (float) doubleValue(event, "strength", p.getStrength()));
        } else if (packet instanceof S32PacketConfirmTransaction) {
            S32PacketConfirmTransaction p = (S32PacketConfirmTransaction) packet;
            setInt(packet, "windowId", intValue(event, "window_id", p.getWindowId()));
            setShort(packet, "actionNumber", (short) intValue(event, "action_number", p.getActionNumber()));
            setBoolean(packet, "field_148893_c", booleanValue(event, "accepted", p.func_148888_e()));
        } else if (packet instanceof S48PacketResourcePackSend) {
            setString(packet, "url", stringValue(event, "url", ((S48PacketResourcePackSend) packet).getURL()));
            setString(packet, "hash", stringValue(event, "hash", ((S48PacketResourcePackSend) packet).getHash()));
        } else if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity p = (S12PacketEntityVelocity) packet;
            setInt(packet, "entityID", intValue(event, "entity_id", p.getEntityID()));
            setInt(packet, "motionX", (int) (doubleValue(event, "motion_x", p.getMotionX() / 8000.0D) * 8000.0D));
            setInt(packet, "motionY", (int) (doubleValue(event, "motion_y", p.getMotionY() / 8000.0D) * 8000.0D));
            setInt(packet, "motionZ", (int) (doubleValue(event, "motion_z", p.getMotionZ() / 8000.0D) * 8000.0D));
        }
    }

    /**
     * Rebuilds an outbound packet when a script edited its fields. Returns the
     * original packet when nothing changed or the packet is not editable.
     */
    static Packet rebuild(Packet original, LuaTable event) {
        if (original instanceof C03PacketPlayer) {
            C03PacketPlayer p = (C03PacketPlayer) original;
            double x = doubleValue(event, "x", p.getPositionX());
            double y = doubleValue(event, "y", p.getPositionY());
            double z = doubleValue(event, "z", p.getPositionZ());
            float yaw = (float) doubleValue(event, "yaw", p.getYaw());
            float pitch = (float) doubleValue(event, "pitch", p.getPitch());
            boolean ground = booleanValue(event, "ground", p.isOnGround());
            if (x == p.getPositionX() && y == p.getPositionY() && z == p.getPositionZ()
                    && yaw == p.getYaw() && pitch == p.getPitch() && ground == p.isOnGround()) {
                return original;
            }
            if (p instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                return new C03PacketPlayer.C06PacketPlayerPosLook(x, y, z, yaw, pitch, ground);
            }
            if (p instanceof C03PacketPlayer.C05PacketPlayerLook) {
                return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, ground);
            }
            if (p instanceof C03PacketPlayer.C04PacketPlayerPosition) {
                return new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, ground);
            }
            return new C03PacketPlayer(ground);
        }
        if (original instanceof C00PacketKeepAlive) {
            int id = intValue(event, "id", ((C00PacketKeepAlive) original).getKey());
            return id == ((C00PacketKeepAlive) original).getKey() ? original : new C00PacketKeepAlive(id);
        }
        if (original instanceof C01PacketChatMessage) {
            String message = stringValue(event, "message", ((C01PacketChatMessage) original).getMessage());
            return message.equals(((C01PacketChatMessage) original).getMessage())
                    ? original : new C01PacketChatMessage(message);
        }
        if (original instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging p = (C07PacketPlayerDigging) original;
            int action = intValue(event, "action", p.getStatus().ordinal());
            int facing = intValue(event, "facing", p.getFacing().ordinal());
            int x = intValue(event, "x", p.getPosition().getX());
            int y = intValue(event, "y", p.getPosition().getY());
            int z = intValue(event, "z", p.getPosition().getZ());
            if (action == p.getStatus().ordinal() && facing == p.getFacing().ordinal()
                    && x == p.getPosition().getX() && y == p.getPosition().getY() && z == p.getPosition().getZ()) {
                return original;
            }
            return new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.values()[action],
                    new BlockPos(x, y, z), EnumFacing.values()[facing]);
        }
        if (original instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) original;
            int facing = intValue(event, "facing", p.getPlacedBlockDirection());
            float ox = (float) doubleValue(event, "offset_x", p.getPlacedBlockOffsetX());
            float oy = (float) doubleValue(event, "offset_y", p.getPlacedBlockOffsetY());
            float oz = (float) doubleValue(event, "offset_z", p.getPlacedBlockOffsetZ());
            if (facing == p.getPlacedBlockDirection() && ox == p.getPlacedBlockOffsetX()
                    && oy == p.getPlacedBlockOffsetY() && oz == p.getPlacedBlockOffsetZ()) {
                return original;
            }
            return new C08PacketPlayerBlockPlacement(p.getPosition(), facing, p.getStack(), ox, oy, oz);
        }
        if (original instanceof C09PacketHeldItemChange) {
            int slot = intValue(event, "slot", ((C09PacketHeldItemChange) original).getSlotId());
            return slot == ((C09PacketHeldItemChange) original).getSlotId()
                    ? original : new C09PacketHeldItemChange(slot);
        }
        if (original instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction p = (C0BPacketEntityAction) original;
            int action = intValue(event, "action", p.getAction().ordinal());
            int aux = intValue(event, "aux_data", p.getAuxData());
            if (action == p.getAction().ordinal() && aux == p.getAuxData()) {
                return original;
            }
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            return player == null ? original
                    : new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.values()[action], aux);
        }
        if (original instanceof C0CPacketInput) {
            C0CPacketInput p = (C0CPacketInput) original;
            float strafe = (float) doubleValue(event, "strafe", p.getStrafeSpeed());
            float forward = (float) doubleValue(event, "forward", p.getForwardSpeed());
            boolean jumping = booleanValue(event, "jumping", p.isJumping());
            boolean sneaking = booleanValue(event, "sneaking", p.isSneaking());
            if (strafe == p.getStrafeSpeed() && forward == p.getForwardSpeed()
                    && jumping == p.isJumping() && sneaking == p.isSneaking()) {
                return original;
            }
            return new C0CPacketInput(strafe, forward, jumping, sneaking);
        }
        if (original instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow p = (C0EPacketClickWindow) original;
            int window = intValue(event, "window_id", p.getWindowId());
            int slot = intValue(event, "slot_id", p.getSlotId());
            int button = intValue(event, "mouse_button", p.getUsedButton());
            int mode = intValue(event, "mode", p.getMode());
            int action = intValue(event, "action_number", p.getActionNumber());
            if (window == p.getWindowId() && slot == p.getSlotId() && button == p.getUsedButton()
                    && mode == p.getMode() && action == p.getActionNumber()) {
                return original;
            }
            return new C0EPacketClickWindow(window, slot, button, mode, p.getClickedItem(), (short) action);
        }
        if (original instanceof C0FPacketConfirmTransaction) {
            C0FPacketConfirmTransaction p = (C0FPacketConfirmTransaction) original;
            int window = intValue(event, "window_id", p.getWindowId());
            int action = intValue(event, "action_number", p.getUid());
            boolean accepted = booleanValue(event, "accepted", booleanField(p, "accepted", false));
            if (window == p.getWindowId() && action == p.getUid()
                    && accepted == booleanField(p, "accepted", false)) {
                return original;
            }
            return new C0FPacketConfirmTransaction(window, (short) action, accepted);
        }
        if (original instanceof C11PacketEnchantItem) {
            C11PacketEnchantItem p = (C11PacketEnchantItem) original;
            int window = intValue(event, "window_id", p.getWindowId());
            int button = intValue(event, "enchantment", p.getButton());
            if (window == p.getWindowId() && button == p.getButton()) {
                return original;
            }
            return new C11PacketEnchantItem(window, button);
        }
        if (original instanceof C14PacketTabComplete) {
            C14PacketTabComplete p = (C14PacketTabComplete) original;
            String text = stringValue(event, "text", p.getMessage());
            BlockPos target = p.getTargetBlock();
            if (text.equals(p.getMessage())) {
                return original;
            }
            return new C14PacketTabComplete(text, target);
        }
        if (original instanceof C18PacketSpectate) {
            String target = stringValue(event, "target", String.valueOf(field(original, "id")));
            return target.equals(String.valueOf(field(original, "id")))
                    ? original : new C18PacketSpectate(UUID.fromString(target));
        }
        if (original instanceof C19PacketResourcePackStatus) {
            C19PacketResourcePackStatus p = (C19PacketResourcePackStatus) original;
            String hash = stringValue(event, "hash", String.valueOf(field(p, "hash")));
            int status = intValue(event, "status",
                    ((C19PacketResourcePackStatus.Action) field(p, "status")).ordinal());
            if (hash.equals(String.valueOf(field(p, "hash")))
                    && status == ((C19PacketResourcePackStatus.Action) field(p, "status")).ordinal()) {
                return original;
            }
            return new C19PacketResourcePackStatus(hash, C19PacketResourcePackStatus.Action.values()[status]);
        }
        return original;
    }

    static Packet createOutbound(int id, LuaTable args, EntityPlayerSP player) {
        switch (id) {
            case 0x00:
                return new C00PacketKeepAlive(intArg(args, 1, 0));
            case 0x01:
                return new C01PacketChatMessage(stringArg(args, 1, ""));
            case 0x02:
                return createUseEntity(args, player);
            case 0x03:
                return new C03PacketPlayer(boolArg(args, 1, player != null && player.onGround));
            case 0x04:
                return new C03PacketPlayer.C04PacketPlayerPosition(
                        doubleArg(args, 1, 0.0D), doubleArg(args, 2, 0.0D), doubleArg(args, 3, 0.0D),
                        boolArg(args, 4, player != null && player.onGround));
            case 0x05:
                return new C03PacketPlayer.C05PacketPlayerLook(
                        (float) doubleArg(args, 1, 0.0D), (float) doubleArg(args, 2, 0.0D),
                        boolArg(args, 3, player != null && player.onGround));
            case 0x06:
                return new C03PacketPlayer.C06PacketPlayerPosLook(
                        doubleArg(args, 1, 0.0D), doubleArg(args, 2, 0.0D), doubleArg(args, 3, 0.0D),
                        (float) doubleArg(args, 4, 0.0D), (float) doubleArg(args, 5, 0.0D),
                        boolArg(args, 6, player != null && player.onGround));
            case 0x07:
                if (args.get(2).istable()) {
                    LuaTable pos = args.get(2).checktable();
                    return new C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.values()[intArg(args, 1, 0)],
                            new BlockPos(tableInt(pos, "x", 0), tableInt(pos, "y", 0), tableInt(pos, "z", 0)),
                            EnumFacing.values()[intArg(args, 3, 0)]);
                }
                return new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.values()[intArg(args, 1, 0)],
                        new BlockPos(intArg(args, 2, 0), intArg(args, 3, 0), intArg(args, 4, 0)),
                        EnumFacing.values()[intArg(args, 5, 0)]);
            case 0x08:
                if (args.get(2).istable()) {
                    LuaTable pos = args.get(2).checktable();
                    return new C08PacketPlayerBlockPlacement(
                            new BlockPos(tableInt(pos, "x", 0), tableInt(pos, "y", 0), tableInt(pos, "z", 0)),
                            intArg(args, 3, 0), player == null ? null : player.getHeldItem(),
                            (float) doubleArg(args, 4, 0.5D), (float) doubleArg(args, 5, 0.5D),
                            (float) doubleArg(args, 6, 0.5D));
                }
                return new C08PacketPlayerBlockPlacement(
                        new BlockPos(intArg(args, 1, 0), intArg(args, 2, 0), intArg(args, 3, 0)),
                        intArg(args, 4, 0), player == null ? null : player.getHeldItem(),
                        (float) doubleArg(args, 5, 0.5D), (float) doubleArg(args, 6, 0.5D),
                        (float) doubleArg(args, 7, 0.5D));
            case 0x09:
                return new C09PacketHeldItemChange(intArg(args, 1, 0));
            case 0x0A:
                return new C0APacketAnimation();
            case 0x0B:
                return new C0BPacketEntityAction(player,
                        C0BPacketEntityAction.Action.values()[intArg(args, 1, 0)], intArg(args, 2, 0));
            case 0x0C:
                return new C0CPacketInput((float) doubleArg(args, 1, 0.0D),
                        (float) doubleArg(args, 2, 0.0D), boolArg(args, 3, false), boolArg(args, 4, false));
            case 0x0E:
                return new C0EPacketClickWindow(intArg(args, 1, 0), intArg(args, 2, 0),
                        intArg(args, 3, 0), intArg(args, 4, 0), null, (short) intArg(args, 5, 0));
            case 0x0F:
                return new C0FPacketConfirmTransaction(intArg(args, 1, 0),
                        (short) intArg(args, 2, 0), boolArg(args, 3, true));
            case 0x11:
                return new C11PacketEnchantItem(intArg(args, 1, 0), intArg(args, 2, 0));
            case 0x13:
                C13PacketPlayerAbilities abilities = new C13PacketPlayerAbilities();
                abilities.setInvulnerable(boolArg(args, 1, false));
                abilities.setFlying(boolArg(args, 2, false));
                abilities.setAllowFlying(boolArg(args, 3, false));
                abilities.setCreativeMode(boolArg(args, 4, false));
                abilities.setFlySpeed((float) doubleArg(args, 5, 0.05D));
                abilities.setWalkSpeed((float) doubleArg(args, 6, 0.1D));
                return abilities;
            case 0x14:
                BlockPos target = args.get(2).isnil() ? null
                        : new BlockPos(intArg(args, 2, 0), intArg(args, 3, 0), intArg(args, 4, 0));
                return new C14PacketTabComplete(stringArg(args, 1, ""), target);
            case 0x18:
                return new C18PacketSpectate(UUID.fromString(stringArg(args, 1, "")));
            case 0x19:
                return new C19PacketResourcePackStatus(stringArg(args, 1, ""),
                        C19PacketResourcePackStatus.Action.values()[intArg(args, 2, 0)]);
            default:
                throw new LuaError("Unsupported outbound packet id 0x" + Integer.toHexString(id));
        }
    }

    private static Packet createUseEntity(LuaTable args, EntityPlayerSP player) {
        int targetId = intArg(args, 1, -1);
        int action = intArg(args, 2, 0);
        Entity entity = player == null || player.worldObj == null ? null : player.worldObj.getEntityByID(targetId);
        if (entity == null) {
            throw new LuaError("Unknown entity id " + targetId);
        }
        if (action == C02PacketUseEntity.Action.INTERACT_AT.ordinal()) {
            return new C02PacketUseEntity(entity,
                    new Vec3(doubleArg(args, 3, 0.0D), doubleArg(args, 4, 0.0D), doubleArg(args, 5, 0.0D)));
        }
        return new C02PacketUseEntity(entity, C02PacketUseEntity.Action.values()[action]);
    }

    private static double doubleArg(LuaTable table, int index, double fallback) {
        LuaValue value = table.get(index);
        return value.isnil() ? fallback : value.todouble();
    }

    private static int intArg(LuaTable table, int index, int fallback) {
        LuaValue value = table.get(index);
        return value.isnil() ? fallback : value.toint();
    }

    private static int tableInt(LuaTable table, String key, int fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.toint();
    }

    private static boolean boolArg(LuaTable table, int index, boolean fallback) {
        LuaValue value = table.get(index);
        return value.isnil() ? fallback : value.toboolean();
    }

    private static String stringArg(LuaTable table, int index, String fallback) {
        LuaValue value = table.get(index);
        return value.isnil() ? fallback : value.tojstring();
    }

    private static double doubleValue(LuaTable table, String key, double fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.todouble();
    }

    private static int intValue(LuaTable table, String key, int fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.toint();
    }

    private static boolean booleanValue(LuaTable table, String key, boolean fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.toboolean();
    }

    private static String stringValue(LuaTable table, String key, String fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.tojstring();
    }

    private static Object field(Object target, String name) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int intField(Object target, String name, int fallback) {
        Object value = field(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static float floatField(Object target, String name, float fallback) {
        Object value = field(target, name);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static boolean booleanField(Object target, String name, boolean fallback) {
        Object value = field(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static void setInt(Object target, String name, int value) {
        setField(target, name, Integer.valueOf(value));
    }

    private static void setShort(Object target, String name, short value) {
        setField(target, name, Short.valueOf(value));
    }

    private static void setByte(Object target, String name, byte value) {
        setField(target, name, Byte.valueOf(value));
    }

    private static void setDouble(Object target, String name, double value) {
        setField(target, name, Double.valueOf(value));
    }

    private static void setFloat(Object target, String name, float value) {
        setField(target, name, Float.valueOf(value));
    }

    private static void setBoolean(Object target, String name, boolean value) {
        setField(target, name, Boolean.valueOf(value));
    }

    private static void setString(Object target, String name, String value) {
        setField(target, name, value);
    }

    private static void setField(Object target, String name, Object value) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
