package moon.module.player;

import java.util.*;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;

/** jx: PRE chest scan, shared kT filter, delayed shift-click and close on completion.
 * Silent mode detaches only the screen; server container transactions stay active.
 * Restores original raw-slot scan, inclusive miss, click-only timer and deferred close.
 * Silent screen ownership and world/disable cleanup are explicit MCP adapters. */
public final class ChestStealerModule extends NativeModule {
    public final BooleanSetting ignoreTrash,shuffle,miss,silent,renderGui,gameGui,customInventory;
    public final IntegerSetting delay,randomization,missChance;
    private final List<SettingValue<?>> settings;private final Minecraft mc;private final InventoryManagerModule inventory;
    private static final String[] MENU_WORDS={"menu","selector","game","gui","server","inventory","play","teleporter","shop","melee","armor","block","castle","mini","warp","teleport","user","team","tool","sure","trade","cancel","accept","soul","book","recipe","profile","tele","port","map","kit","select","lobby","vault","lock","quick","travel","cake","war","pvp","choose","collectibles","modos","jogo","clique"};
    private static final String MENU_EXCEPTION="    gui";
    private final Random random=new Random(),shuffleRandom=new Random();
    final ChestActions actions=new ChestActions(this);private ContainerChest chest;private long transfers;
    public ChestStealerModule(Minecraft mc,InventoryManagerModule inventory,MarkerHooks h){
        super("cheststealer","ChestStealer",Category.PLAYER);this.mc=mc;this.inventory=inventory;
        ignoreTrash=flag(h,"ignore-trash",true);shuffle=flag(h,"shuffle",false);miss=flag(h,"miss",false);
        silent=flag(h,"silent",true);renderGui=flag(h,"render-gui",true);gameGui=flag(h,"game-gui",false);customInventory=flag(h,"custom-inventory",false);
        delay=number(h,"delay",150,0,300,50);randomization=number(h,"delay-randomization",50,0,500,50);missChance=number(h,"miss-chance",20,0,100,1);
        renderGui.setVisibility(silent::get);missChance.setVisibility(miss::get);
        settings=List.of(ignoreTrash,shuffle,miss,silent,renderGui,gameGui,customInventory,delay,randomization,missChance);
    }
    @Override public String hudSuffix(){return delay.get()+" ms";}
    public List<? extends SettingValue<?>> settings(){return settings;}
    private GuiChest hiddenScreen;
    private Object hiddenWorld;
    public long transfers(){return transfers;}
    public boolean silentActive(){return hiddenScreen!=null&&mc.theWorld==hiddenWorld&&mc.thePlayer!=null&&mc.thePlayer.openContainer==hiddenScreen.inventorySlots;}
    public String pendingReason(){return null;}
    public void worldChanged(){hiddenScreen=null;hiddenWorld=null;chest=null;actions.reset(clock());}
    protected void onDisable(){restoreScreen();worldChanged();}
    private void restoreScreen(){
        GuiChest screen=hiddenScreen;boolean live=silentActive();hiddenScreen=null;hiddenWorld=null;
        if(!live)return;
        if(mc.currentScreen==null&&mc.thePlayer.isEntityAlive())mc.displayGuiScreen(screen);
        else closeHiddenContainer(screen);
    }
    private void closeHiddenContainer(GuiChest screen){
        if(mc.thePlayer!=null&&mc.thePlayer.openContainer==screen.inventorySlots){
            mc.thePlayer.sendQueue.addToSendQueue(new C0DPacketCloseWindow(screen.inventorySlots.windowId));
            screen.inventorySlots.onContainerClosed(mc.thePlayer);
            mc.thePlayer.openContainer=mc.thePlayer.inventoryContainer;
        }
    }
    /** Preview never owns focus or slot input; live container identity is unchanged. */
    public void renderSilent(float partialTicks){
        if(!enabled()||!silentActive()||!renderGui.get()||mc.currentScreen!=null)return;
        ScaledResolution resolution=new ScaledResolution(mc);
        if(hiddenScreen.width!=resolution.getScaledWidth()||hiddenScreen.height!=resolution.getScaledHeight())
            hiddenScreen.setWorldAndResolution(mc,resolution.getScaledWidth(),resolution.getScaledHeight());
        hiddenScreen.drawScreen(-10000,-10000,partialTicks);
    }
    public void motionPre(){
        if(!enabled())return;
        if(mc.thePlayer==null||!mc.thePlayer.isEntityAlive()){worldChanged();return;}
        if(!(mc.thePlayer.openContainer instanceof ContainerChest c)){hiddenScreen=null;hiddenWorld=null;chest=null;actions.noScreen(clock());return;}
        if(hiddenScreen!=null&&!silentActive()){hiddenScreen=null;hiddenWorld=null;}
        if(silentActive()&&mc.currentScreen!=null){closeHiddenContainer(hiddenScreen);worldChanged();return;}
        if(!silent.get())restoreScreen();
        if(!(mc.currentScreen instanceof GuiChest screen&&screen.inventorySlots==c)&&!silentActive()){actions.noScreen(clock());return;}
        chest=c;
        // hm.aa is the upper player inventory; hm.ab is the lower chest inventory.
        int upperStart=c.inventorySlots.size()-36;
        if(!acceptsInventory(c.getSlot(upperStart).inventory.hasCustomName(),
            c.getLowerChestInventory().getDisplayName().getUnformattedText()))return;
        if(silent.get()&&hiddenScreen==null&&mc.currentScreen instanceof GuiChest screen&&screen.inventorySlots==c){
            if(mc.moonHideChestScreen(screen)){hiddenScreen=screen;hiddenWorld=mc.theWorld;}
        }
        actions.pre(access,clock());
    }

    private boolean acceptsInventory(boolean upperCustom,String title){
        if(!customInventory.get()&&upperCustom)return false;
        if(gameGui.get())return true;
        String lower=title.toLowerCase(); // Original uses the JVM default locale.
        for(String word:MENU_WORDS)if(lower.contains(word)&&!lower.contains(MENU_EXCEPTION))return false;
        return true;
    }
    private long sampleDelay(){
        int jitter=random.nextInt(randomization.get()+1)+25;
        jitter-=jitter%50;
        return delay.get()+jitter;
    }
    interface ChestAccess {
        int slotCount(); int slotNumber(int index); boolean hasStack(int index);
        boolean discard(int index); void shift(int slotNumber); void close(); long now();
    }
    /** jx E/G/H. Only actual clicks reset the timer and sample the next delay. */
    static final class ChestActions {
        final ChestStealerModule owner;
        long lastClick, sampledDelay;
        boolean closePending;
        ChestActions(ChestStealerModule owner){this.owner=owner;reset(clock());}
        void reset(long now){lastClick=now;sampledDelay=0;closePending=false;}
        void noScreen(long now){lastClick=now;}
        void pre(ChestAccess access,long now){
            if(now-lastClick<sampledDelay-5)return;
            if(closePending){access.close();closePending=false;return;}
            int count=access.slotCount();
            List<Integer> slots=new LinkedList<>();
            for(int i=0;i<count;i++)slots.add(i);
            // Original uses Collections' shared RNG, independently of kU's delay/miss RNG.
            if(owner.shuffle.get())Collections.shuffle(slots,owner.shuffleRandom);
            boolean missing=owner.miss.get()&&owner.missChance.get()>=owner.random.nextInt(101);
            int missedSlot=owner.random.nextInt(count);
            boolean acted=false;
            for(int index:slots){
                int slotNumber=access.slotNumber(index);
                if(missing){
                    if(missedSlot!=slotNumber)continue;
                    if(access.hasStack(index)){
                        acted=true;
                        // Original indexes the shuffled list by raw slot number, not iterator position.
                        if(slots.size()-1>index)missedSlot=slots.get(index+1);
                        continue;
                    }
                }else if(!access.hasStack(index))continue;
                if(owner.ignoreTrash.get()&&access.discard(index))continue;
                access.shift(slotNumber);
                lastClick=access.now();sampledDelay=owner.sampleDelay();acted=true;
                if(owner.delay.get()!=0||owner.randomization.get()!=0)break;
            }
            if(!acted)closePending=true;
        }
    }
    private final ChestAccess access=new ChestAccess(){
        public int slotCount(){return chest.getLowerChestInventory().getSizeInventory()/9*9;}
        public int slotNumber(int index){return chest.getSlot(index).slotNumber;}
        public boolean hasStack(int index){return chest.getSlot(index).getHasStack();}
        public boolean discard(int index){return !inventory.useful(chest.getSlot(index));}
        public void shift(int slot){
            ItemStack before=chest.getSlot(slot).getStack();int size=before==null?0:before.stackSize;
            mc.playerController.windowClick(chest.windowId,slot,0,1,mc.thePlayer);
            ItemStack after=chest.getSlot(slot).getStack();
            if(before!=null&&(after==null||after.stackSize<size))transfers++;
        }
        public void close(){mc.thePlayer.closeScreen();}
        public long now(){return clock();}
    };
    private static long clock(){return System.nanoTime()/1_000_000;}

    private static IntegerSetting number(MarkerHooks h,String key,int v,int min,int max,int step){return IntegerSetting.builder(key,h).configure(key.replace('-',' '),v,min,max,step).build();}
    private static BooleanSetting flag(MarkerHooks h,String key,boolean v){return BooleanSetting.builder(key,h).configure(key.replace('-',' '),v).build();}
}
