package dev.pier.lua;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.*;
import net.minecraft.potion.*;
import net.minecraft.util.*;
import net.minecraft.inventory.Container;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.block.Block;
import moon.client.*;
import moon.module.NativeModule;

/** Destination game adapter for the supplied scripts. Not a recovered donor class.
 * Contracts: official Moon API, recovered kc/j_ tables, l7 event tables and MCP 1.8.9.
 * Existing renderer batching and the original script files are preserved.
 */
final class LuaGameApis {
    private interface Call { Varargs run(Varargs args); }
    private static LuaValue fn(Call call){return new VarArgFunction(){public Varargs invoke(Varargs a){return call.run(a);}};}
    private static Minecraft mc(){return Minecraft.getMinecraft();}
    private static EntityPlayerSP player(){return mc()==null?null:mc().thePlayer;}
    private static LuaTable vec(double x,double y,double z){LuaTable t=new LuaTable();t.set("x",LuaValue.valueOf(x));t.set("y",LuaValue.valueOf(y));t.set("z",LuaValue.valueOf(z));return t;}
    private static BlockPos pos(LuaValue t){return new BlockPos(t.get("x").checkint(),t.get("y").checkint(),t.get("z").checkint());}
    private static String key(String s){return s==null?"":s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");}
    static NativeModule nativeModule(String name){
        var service=McpModuleServices.current();if(service==null)return null;
        for(var module:service.modules())if(key(name).equals(key(module.key()))||key(name).equals(key(module.displayName())))return module;
        return null;
    }
    static void install(Globals g){
        LuaTable p=g.get("player").checktable(),inv=g.get("inventory").checktable(),world=g.get("world").checktable(),client=g.get("client").checktable();
        p.set("is_in_lava",fn(a->LuaValue.valueOf(player()!=null&&player().isInLava())));
        p.set("is_in_cobweb",fn(a->LuaValue.valueOf(player()!=null&&player().luaIsInWeb())));
        p.set("is_using_item",fn(a->LuaValue.valueOf(player()!=null&&player().isUsingItem())));
        p.set("get_input",fn(a->{LuaTable t=new LuaTable();t.set("x",LuaValue.valueOf(player()==null?0:(double)player().moveForward));t.set("y",LuaValue.valueOf(player()==null?0:(double)player().moveStrafing));return t;}));
        p.set("swing_item",fn(a->{if(player()!=null)player().swingItem();return LuaValue.NONE;}));
        p.set("get_active_effects",fn(a->{LuaTable t=new LuaTable();if(player()!=null){int i=1;for(PotionEffect e:player().getActivePotionEffects())t.set(i++,potion(e));}return t;}));
        p.set("get_effect",fn(a->{int id=a.checkint(1);return player()==null||id<0||id>=Potion.potionTypes.length||Potion.potionTypes[id]==null?LuaValue.NIL:potion(player().getActivePotionEffect(Potion.potionTypes[id]));}));
        p.set("ray_cast",fn(a->{
            float yaw=(float)a.checkdouble(1),pitch=(float)a.checkdouble(2);double reach=a.checkdouble(3);
            if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Double.isFinite(reach)||reach<0)throw new LuaError("Invalid ray cast");
            EntityPlayerSP entity=player();if(entity==null||mc().theWorld==null)return LuaValue.NIL;
            float cy=MathHelper.cos(-yaw*0.017453292F-(float)Math.PI),sy=MathHelper.sin(-yaw*0.017453292F-(float)Math.PI);
            float cp=-MathHelper.cos(-pitch*0.017453292F),sp=MathHelper.sin(-pitch*0.017453292F);
            Vec3 eye=entity.getPositionEyes(1);MovingObjectPosition hit=mc().theWorld.rayTraceBlocks(eye,eye.addVector(sy*cp*reach,sp*reach,cy*cp*reach),false,false,true);
            if(hit==null)return LuaValue.NIL;
            LuaTable t=new LuaTable();t.set("hit_type",LuaValue.valueOf(hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK?1:hit.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY?2:0));
            if(hit.sideHit!=null)t.set("side_hit",LuaValue.valueOf(hit.sideHit.ordinal()));
            if(hit.getBlockPos()!=null)t.set("pos",vec(hit.getBlockPos().getX(),hit.getBlockPos().getY(),hit.getBlockPos().getZ()));
            if(hit.hitVec!=null)t.set("hit_vec",vec(hit.hitVec.xCoord,hit.hitVec.yCoord,hit.hitVec.zCoord));
            t.set("hit_entity_id",LuaValue.valueOf(hit.entityHit==null?-1:hit.entityHit.getEntityId()));return t;
        }));
        p.set("place_block",fn(a->{
            int slot=a.checkint(1),face=a.checkint(4);if(slot<0||slot>8||face<0||face>5)throw new LuaError("Invalid block placement slot/facing");
            EntityPlayerSP entity=player();if(entity==null||mc().theWorld==null||mc().playerController==null)return LuaValue.FALSE;
            BlockPos block=pos(a.arg(2));LuaValue v=a.arg(3);Vec3 hit=new Vec3(v.get("x").checkdouble(),v.get("y").checkdouble(),v.get("z").checkdouble());
            entity.inventory.currentItem=slot;ItemStack stack=entity.inventory.getStackInSlot(slot);
            return LuaValue.valueOf(stack!=null&&mc().playerController.onPlayerRightClick(entity,mc().theWorld,stack,block,EnumFacing.values()[face],hit));
        }));
        world.set("get_block_at",fn(a->{
            if(mc()==null||mc().theWorld==null)return LuaValue.NIL;
            BlockPos at=new BlockPos(a.checkdouble(1),a.checkdouble(2),a.checkdouble(3));Block b=mc().theWorld.getBlockState(at).getBlock();
            LuaTable t=new LuaTable();t.set("name",LuaValue.valueOf(b.getUnlocalizedName()));t.set("display_name",LuaValue.valueOf(b.getLocalizedName()));
            t.set("full_block",LuaValue.valueOf(b.isFullBlock()));t.set("translucent",LuaValue.valueOf(b.isTranslucent()));
            t.set("block_hardness",LuaValue.valueOf((double)b.getBlockHardness(mc().theWorld,at)));
            t.set("relative_hardness",LuaValue.valueOf(player()==null?0:(double)b.getPlayerRelativeBlockHardness(player(),mc().theWorld,at)));
            t.set("slipperiness",LuaValue.valueOf((double)b.slipperiness));return t;
        }));
        inv.set("get_item",fn(a->{int slot=a.checkint(1);return player()==null||slot<0||slot>=player().inventory.getSizeInventory()?LuaValue.NIL:item(player().inventory.getStackInSlot(slot));}));
        inv.set("get_item_in_window",fn(a->{Container c=container(a.checkint(1));int slot=a.checkint(2);return c==null||slot<0||slot>=c.inventorySlots.size()?LuaValue.NIL:item(c.getSlot(slot).getStack());}));
        inv.set("window_click",fn(a->{click(a.checkint(1),a.checkint(2),a.checkint(3),a.checkint(4));return LuaValue.NONE;}));
        inv.set("drop",fn(a->{if(player()!=null)click(player().inventoryContainer.windowId,a.checkint(1),a.checkboolean(2)?1:0,4);return LuaValue.NONE;}));
        inv.set("shift",fn(a->{if(player()!=null)click(player().inventoryContainer.windowId,a.checkint(1),0,1);return LuaValue.NONE;}));
        inv.set("swap",fn(a->{if(player()!=null)click(player().inventoryContainer.windowId,a.checkint(1),a.checkint(2),2);return LuaValue.NONE;}));
        inv.set("player_inventory_id",fn(a->LuaValue.valueOf(player()==null?0:player().inventoryContainer.windowId)));
        inv.set("upper_container_id",fn(a->LuaValue.valueOf(player()==null?0:player().openContainer.windowId)));
        inv.set("current_inventory",fn(a->mc()==null||mc().currentScreen==null?LuaValue.NIL:LuaValue.valueOf(mc().currentScreen.getClass().getSimpleName())));
        client.set("current_kill_aura_target",fn(a->{var s=McpModuleServices.current();var target=s==null?null:s.killAura.target();return LuaValue.valueOf(target==null?-1:target.id());}));
        client.set("force_effects_redraw",fn(a->{McpLuaRenderer.requestEffectsRedraw();return LuaValue.NONE;}));
        // Match the recovered enum order; ENTITY is a destination compatibility alias.
        LuaTable hits=g.get("enums").get("hittype").checktable();hits.set("MISS",LuaValue.valueOf(0));hits.set("BLOCK",LuaValue.valueOf(1));hits.set("ENITTY",LuaValue.valueOf(2));hits.set("ENTITY",LuaValue.valueOf(2));
    }
    private static Container container(int id){EntityPlayerSP p=player();return p==null?null:p.openContainer.windowId==id?p.openContainer:p.inventoryContainer.windowId==id?p.inventoryContainer:null;}
    private static void click(int window,int slot,int button,int mode){
        Container c=container(window);if(c==null||mc().playerController==null)throw new LuaError("Inventory window is not open: "+window);
        if((slot<0&&slot!=-999)||slot>=c.inventorySlots.size()||mode<0||mode>6)throw new LuaError("Invalid inventory click");
        mc().playerController.windowClick(window,slot,button,mode,player());
    }
    private static LuaValue potion(PotionEffect e){
        if(e==null)return LuaValue.NIL;Potion p=Potion.potionTypes[e.getPotionID()];LuaTable t=new LuaTable();
        t.set("id",LuaValue.valueOf(e.getPotionID()));t.set("name",LuaValue.valueOf(p.getName()));t.set("duration",LuaValue.valueOf(e.getDuration()));
        t.set("amplifier",LuaValue.valueOf(e.getAmplifier()));t.set("bad_effect",LuaValue.valueOf(p.isBadEffect()));t.set("color",LuaValue.valueOf(p.getLiquidColor()));return t;
    }
    private static LuaValue item(ItemStack s){
        if(s==null)return LuaValue.NIL;LuaTable t=new LuaTable();
        t.set("name",LuaValue.valueOf(s.getItem().getUnlocalizedName()));t.set("display_name",LuaValue.valueOf(s.getDisplayName()));
        t.set("max_stack_size",LuaValue.valueOf(s.getMaxStackSize()));t.set("stack_size",LuaValue.valueOf(s.stackSize));
        t.set("max_damage",LuaValue.valueOf(s.getMaxDamage()));t.set("damage",LuaValue.valueOf(s.getItemDamage()));
        t.set("solid",LuaValue.valueOf(s.getItem() instanceof ItemBlock&&((ItemBlock)s.getItem()).getBlock().isFullBlock()));
        LuaTable ench=new LuaTable(),pots=new LuaTable();int i=1;
        for(var e:EnchantmentHelper.getEnchantments(s).entrySet()){LuaTable row=new LuaTable();row.set("id",LuaValue.valueOf(e.getKey()));row.set("level",LuaValue.valueOf(e.getValue()));ench.set(i++,row);}
        if(s.getItem() instanceof ItemPotion){var effects=((ItemPotion)s.getItem()).getEffects(s);if(effects!=null){i=1;for(PotionEffect e:effects)pots.set(i++,potion(e));}}
        t.set("enchantments",ench);t.set("potions",pots);return t;
    }
}
