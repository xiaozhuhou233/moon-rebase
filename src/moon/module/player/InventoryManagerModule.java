package moon.module.player;
import moon.module.combat.killaura.KillAuraRotations;
import moon.module.player.inventorymanager.InventorySettings;

import net.minecraft.init.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import moon.module.NativeModule;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.visual.gui.SettingValue;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.*;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.*;
import static moon.module.player.inventorymanager.InventorySettings.Feature.*;

/** jz PRE state machine; kT vanilla shift/swap/drop and paired inventory spoof packets. */
public final class InventoryManagerModule extends NativeModule {
    public final InventorySettings config;
    private final Minecraft mc;private final BooleanSupplier busy;private final Random random=new Random();
    private final InventoryActions actions;private long operations;
    public InventoryManagerModule(Minecraft mc,BooleanSupplier busy,MarkerHooks h){super("inventory-manager","InventoryManager",Category.PLAYER);this.mc=mc;this.busy=busy;config=new InventorySettings(h);actions=new InventoryActions(config,clock());}
    @Override public String hudSuffix(){return config.mode.get().displayName();}
    public List<? extends SettingValue<?>> settings(){return config.roots();}
    public long operations(){return operations;}
    public boolean usingPotion(){return actions.pending!=-1;}
    private static long clock(){return System.nanoTime()/1_000_000L;}
    public void worldChanged(){actions.reset(clock());}
    protected void onEnable(){actions.delay=sampleDelay();}
    protected void onDisable(){worldChanged();}
    private ItemStack stack(int slot){return mc.thePlayer.inventoryContainer.getSlot(slot).getStack();}
    private void click(int slot,int button,int mode){
        boolean spoof=config.mode.get()==InventorySettings.Mode.SPOOF;
        if(spoof)mc.thePlayer.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,slot,button,mode,mc.thePlayer);operations++;
        if(spoof)mc.thePlayer.sendQueue.addToSendQueue(new C0DPacketCloseWindow(mc.thePlayer.inventoryContainer.windowId));
    }
    /** Original jz: inclusive jitter, add25, then quantize down in 50ms buckets. */
    private long sampleDelay(){
        int jitter=random.nextInt(config.randomization.get()+1)+25;
        jitter-=jitter%50;
        return config.delay.get()+jitter;
    }
    /** Original kT slot list preserves container order and Slot identity. */
    private List<Slot> policySlots(){
        List<Slot> slots=new ArrayList<>(mc.thePlayer.inventoryContainer.inventorySlots);
        if(mc.thePlayer.openContainer!=null){
            slots.removeIf(mc.thePlayer.openContainer.inventorySlots::contains);
            slots.addAll(mc.thePlayer.openContainer.inventorySlots);
        }
        return slots;
    }
    private int preferred(int kind){
        return kind==0?36:kind>=1&&kind<=3?36+config.toolsSlot.get()+kind-1:kind>=4&&kind<=7?kind+1:43;
    }
    private int best(int kind){return InventoryItemPolicy.select(policySlots(),kind,preferred(kind));}
    private int ownedBlocks(){
        int count=0;for(int i=0;i<45;i++){ItemStack s=stack(i);if(InventoryItemPolicy.block(s))count+=s.stackSize;}return count;
    }
    private final InventoryAccess access=new InventoryAccess(){
        public int screen(){return mc.currentScreen==null?0:mc.currentScreen instanceof GuiInventory?1:2;}
        public boolean targetPresent(){return busy.getAsBoolean();}
        public boolean grounded(){return mc.thePlayer.onGround;}
        public float health(){return mc.thePlayer.getHealth();}
        public int best(int kind){return InventoryManagerModule.this.best(kind);}
        public boolean equipped(int type){return stack(type+5)!=null;}
        public boolean freeInventorySlot(){return mc.thePlayer.inventory.getFirstEmptyStack()!=-1;}
        public int blockSlot(){return InventoryItemPolicy.selectBlocks(policySlots());}
        public boolean active(int effect){return mc.thePlayer.isPotionActive(effect);}
        public int potionSlot(int effect){return InventoryItemPolicy.findPotion(mc.thePlayer.inventory.mainInventory,effect);}
        public int soupSlot(){return InventoryItemPolicy.findSoup(mc.thePlayer.inventory.mainInventory);}
        public int trashSlot(){List<Slot> slots=policySlots();int blocks=ownedBlocks();for(Slot slot:slots)if(InventoryItemPolicy.discard(slot,config,slots,blocks))return slot.slotNumber;return -1;}
        public void swap(int hotbar,int source){click(source,hotbar,2);}
        public void shift(int source){click(source,0,1);}
        public void drop(int source){click(source,1,4);}
        public void use(int hotbar){InventoryManagerModule.this.use(hotbar);}
        public void dropHeld(){mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.DROP_ITEM,net.minecraft.util.BlockPos.ORIGIN,net.minecraft.util.EnumFacing.DOWN));}
        public long sampleDelay(){return InventoryManagerModule.this.sampleDelay();}
    };
    public Angles motionPre(){
        if(!enabled()||mc.thePlayer==null||mc.theWorld==null)return null;
        Float pitch=actions.pre(access,clock());return pitch==null?null:new Angles(mc.thePlayer.rotationYaw,pitch);
    }
    /** POST retains only the OPEN-without-screen timer reset; use completes on PRE. */
    public void motionPost(){if(enabled()&&mc.thePlayer!=null&&mc.theWorld!=null)actions.post(access.screen(),clock());}
    private void select(int hotbar){mc.thePlayer.inventory.currentItem=hotbar;mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(hotbar));}
    private void use(int hotbar){
        int previous=mc.thePlayer.inventory.currentItem;select(hotbar);
        mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        select(previous);operations++;
    }
    /** jx passes the actual chest Slot to the same kT predicate as AUTO_CLEAN. */
    public boolean useful(Slot slot){return !InventoryItemPolicy.discard(slot,config,policySlots(),ownedBlocks());}

    /** Pure jz PRE transition; k7 timers use monotonic milliseconds and reset on success. */
    interface InventoryAccess {
        int screen(); boolean targetPresent(); boolean grounded(); float health(); boolean active(int effect);
        int best(int kind); boolean equipped(int type); boolean freeInventorySlot();
        int blockSlot(); int potionSlot(int effect); int soupSlot(); int trashSlot();
        void swap(int hotbar,int source); void shift(int source); void drop(int source);
        void use(int hotbar); void dropHeld(); long sampleDelay();
    }
    static final class InventoryActions {
        long actionAt,useAt,potionAt,delay;
        int pending=-1;boolean ready;
        final InventorySettings config;
        InventoryActions(InventorySettings config,long now){this.config=config;reset(now);}
        void reset(long now){actionAt=useAt=potionAt=now;pending=-1;ready=false;}
        private boolean swap(InventoryAccess host,int hotbar,int source){
            if(source==-1)return false;host.swap(hotbar,source);return true;
        }
        void post(int screen,long now){if(screen==0&&config.mode.get()==InventorySettings.Mode.OPEN)actionAt=now;}
        Float pre(InventoryAccess host,long now){
            int screen=host.screen();if(screen!=0&&screen!=1)return null;
            if(screen==0&&config.mode.get()==InventorySettings.Mode.OPEN){actionAt=now;return null;}
            if(ready){host.use(pending);pending=-1;ready=false;actionAt=potionAt=now;return null;}
            if(pending!=-1){if(now-useAt>=config.potionDelay.get()){useAt=now;ready=true;}return 90F;}
            if(now-actionAt<delay-5)return null;
            actionAt=now;delay=host.sampleDelay();
            if(config.features.isSelected(AUTO_ARMOR))for(int type=0;type<4;type++){
                int selected=host.best(type+4);if(selected==-1)continue;
                if(host.equipped(type)){
                    if(!host.freeInventorySlot())break;
                    host.shift(type+5);
                }else host.shift(selected);
                return null;
            }
            if(config.features.isSelected(AUTO_SWORD)&&swap(host,0,host.best(0)))return null;
            if(config.tools.get()&&config.hotbarTools.get())for(int kind=1;kind<=3;kind++)
                if(swap(host,config.toolsSlot.get()+kind-1,host.best(kind)))return null;
            if(config.bows.get()&&swap(host,7,host.best(8)))return null;
            if(config.features.isSelected(MOVE_BLOCKS)){
                int selected=host.blockSlot();if(selected!=-1&&selected!=8){host.swap(8,selected);return null;}
            }
            if(config.features.isSelected(AUTO_POTION)&&!host.targetPresent()&&host.grounded()&&now-potionAt>=config.potionDelay.get()+2500L){
                int heal=host.potionSlot(6),regen=host.potionSlot(10),speed=host.potionSlot(1),resistance=host.potionSlot(11);
                if(config.potions.isSelected(InventorySettings.PotionType.INSTANT_HEAL)&&heal!=-1&&host.health()<=config.health.get())pending=heal;
                else if(config.potions.isSelected(InventorySettings.PotionType.REGENERATION)&&regen!=-1&&!host.active(10)&&host.health()<=config.health.get())pending=regen;
                else if(config.potions.isSelected(InventorySettings.PotionType.SPEED)&&speed!=-1&&!host.active(1))pending=speed;
                else if(config.potions.isSelected(InventorySettings.PotionType.RESISTANCE)&&resistance!=-1&&!host.active(11))pending=resistance;
                if(pending!=-1){if(pending>8){host.swap(8,pending);pending=8;}return null;}
            }
            if(config.features.isSelected(AUTO_SOUP)){
                int soup=host.soupSlot();
                if(soup!=-1&&host.health()+1<=config.health.get()&&now-useAt>=100){
                    if(soup>8){host.swap(8,soup);soup=8;}
                    host.use(soup);host.dropHeld();useAt=potionAt=now;
                }
            }
            if(config.features.isSelected(AUTO_CLEAN)){int trash=host.trashSlot();if(trash!=-1)host.drop(trash);}
            return null;
        }
    }

    /** kT ordered selection, equipment score and item discard predicate, consolidated here. */
    private static final class InventoryItemPolicy {
        private InventoryItemPolicy(){}
        public static int kind(ItemStack s){
            if(s==null)return -1;Item i=s.getItem();
            if(i instanceof ItemSword)return 0;if(i instanceof ItemPickaxe)return 1;if(i instanceof ItemAxe)return 2;if(i instanceof ItemSpade)return 3;
            if(i instanceof ItemArmor a)return 4+a.armorType;if(i instanceof ItemBow)return 8;return -1;
        }
        public static double score(ItemStack s){
            if(s==null)return -1;
            Item item=s.getItem();double base=0;
            if(item instanceof ItemArmor armor)base=armor.getArmorMaterial().getDamageReductionAmount(armor.armorType);
            else if(item instanceof ItemTool tool)base=tool.getToolMaterial().getHarvestLevel();
            else if(item instanceof ItemSword sword)base=sword.getDamageVsEntity();
            return base+enchantmentScore(s);
        }
        /** kT iterates raw NBT entries in order, including duplicates and unknown IDs. */
        private static double enchantmentScore(ItemStack stack){
            if(stack==null||!stack.isItemEnchanted())return 0;
            NBTTagList tags=stack.getEnchantmentTagList();if(tags==null)return 0;
            double score=0;
            for(int i=0;i<tags.tagCount();i++){
                var tag=tags.getCompoundTagAt(i);int level=tag.getShort("lvl");
                score+=switch(tag.getShort("id")){case 0->(double)level;case 16->1.25D*level;case 20->.5D*level;default->.2D*level;};
            }
            return score;
        }
        /** -1 means the best stack is already in the preferred slot, or no match. */
        private static int select(List<Slot> slots,int kind,int preferred){
            double best=Double.NaN;int selected=-1;
            for(Slot slot:slots){
                ItemStack s=slot.getStack();if(kind(s)!=kind)continue;
                double score=score(s);
                if(Double.isNaN(best)||score>best||(score==best&&slot.slotNumber==preferred)){
                    selected=slot.slotNumber==preferred?-1:slot.slotNumber;best=score;
                }
            }
            return selected;
        }
        static int findPotion(ItemStack[] inventory,int effect){
            for(int slot=0;slot<=35;slot++){
                ItemStack stack=inventory[slot];if(stack==null||!(stack.getItem() instanceof ItemPotion potion)||potion.getEffects(stack)==null)continue;
                for(PotionEffect found:potion.getEffects(stack))if(found.getPotionID()==effect)return slot;
            }
            return -1;
        }
        static int findSoup(ItemStack[] inventory){
            for(int slot=0;slot<=35;slot++)if(inventory[slot]!=null&&inventory[slot].getItem() instanceof ItemSoup)return slot;
            return -1;
        }
        static int selectBlocks(List<Slot> slots){
            int count=0,selected=-1;
            for(Slot slot:slots){ItemStack stack=slot.getStack();if(stack!=null&&stack.getItem() instanceof ItemBlock&&count<=stack.stackSize){selected=slot.slotNumber==44?-1:slot.slotNumber;count=stack.stackSize;}}
            return selected;
        }
        public static boolean block(ItemStack s){return s!=null&&s.getItem() instanceof ItemBlock block&&block.getBlock().isFullBlock();}
        private static boolean discard(Slot slot,InventorySettings config,List<Slot> slots,int ownedBlocks){
            ItemStack s=slot.getStack();if(s==null)return false;Item item=s.getItem();
            if(config.named.get()&&s.hasDisplayName()&&!(item instanceof ItemPotion))return false;
            int kind=kind(s),preferred=-1;
            if(kind==0)preferred=36;
            else if(kind>=1&&kind<=3&&config.tools.get())preferred=36+config.toolsSlot.get()+kind-1;
            else if(kind==8&&config.bows.get())preferred=43;
            else if(kind>=4&&kind<=7)preferred=kind+1;
            if(preferred>=0){int best=select(slots,kind,preferred);return slot.slotNumber!=best&&(best!=-1||slot.slotNumber!=preferred);}
            if(item instanceof ItemPotion potion){
                for(PotionEffect effect:potion.getEffects(s))if(effect!=null&&(Potion.potionTypes[effect.getPotionID()].isBadEffect()||effect.getPotionID()==12))return true;
                return false;
            }
            if(block(s))return ownedBlocks>config.blockLimit.get();
            if(item instanceof ItemFood||item instanceof ItemExpBottle)return false;
            return switch(Item.getIdFromItem(item)){
                case 262,263,264,265,266,280,325,326,327,368,369,370,378,388,399->false;
                default->true;
            };
        }
    }
}
