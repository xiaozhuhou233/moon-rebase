package moon.module.combat;
import moon.module.combat.killaura.KillAuraSettings;
import moon.module.combat.killaura.KillAuraClickScheduler;
import moon.module.combat.killaura.KillAuraRotations;
import moon.module.combat.killaura.KillAuraTargetSelector;

import java.util.*;
import java.util.random.RandomGenerator;
import moon.module.NativeModule;
import moon.visual.gui.SettingValue;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import moon.module.combat.killaura.KillAuraTargetSelector.*;
import moon.module.combat.killaura.KillAuraRotations.Angles;

/** jc selection/rotation and eP..eZ normal block/click paths.
 * Numeric constants use independently cross-checked finite original-DATA candidates.
 * Original guards/event bus and destination lifecycle boundaries remain documented.
 */
public final class MoonKillAuraModule extends NativeModule {
    public interface Host {
        Player player();
        Collection<Target> targets();
        boolean available();
        long currentTimeMillis();
        int hurtResistantTime();
        int targetHurtResistantTime(int entityId);
        double heldItemAttackSpeed();
        int selectedSlot();
        void sendHeldItemChange(int slot);
        void sendUseItemPacket();
        void sendWatchdogPlacement();
        void releaseUseItemPacket();
        boolean cancelledByOtherModule();
        boolean rayHits(int entityId,Angles angles,float range);
        boolean swordHeld();
        void block();
        void unblock();
        /** eP.e/f: change the actual use-item key without synthesizing a click pulse. */
        void useKey(boolean pressed);
        /** Publish an attack cancellation event, then use vanilla swing/attack ordering. */
        boolean attack(int entityId,boolean realClick,boolean ignoreBlocking);
        /** Original eZ swings when only aim-reach contains the target. */
        default void swingMiss() {}
        default void stopSprintOnAttack() {}
        default int ticksExisted(){return 0;}
        default void sendInvalidUsePacket() {}
    }
    public final KillAuraSettings config;
    private final Host host;
    private final RandomGenerator random;
    private final KillAuraTargetSelector selector=new KillAuraTargetSelector();
    private int switchIndex;
    private long switchedAt;
    private KillAuraSettings.Selection lastSelection;
    private final KillAuraRotations rotations=new KillAuraRotations();
    private final KillAuraClickScheduler clicks;
    private Target target;
    private boolean blocked,useKeyOwned,packetBlocked;
    private boolean sprintAllowed;
    private Object activeBlockMode;
    private String pendingReason;
    private long attempts,attacks;
    private int invalidUsedTick=-1;
    public MoonKillAuraModule(Host host,MarkerHooks hooks,RandomGenerator random) {
        super("killaura","KillAura",Category.COMBAT);
        this.host=Objects.requireNonNull(host);this.random=Objects.requireNonNull(random);
        config=new KillAuraSettings(hooks);clicks=new KillAuraClickScheduler(KillAuraClickScheduler.Policy.destination());
    }
    @Override public String hudSuffix(){return config.selection.get().displayName();}
    public List<? extends SettingValue<?>> settings() { return config.roots(); }
    public Target target() { return target; }
    public Angles angles() { return rotations.current(); }
    public String pendingReason() { return pendingReason; }
    public long attackAttempts() { return attempts; }
    public long attacks() { return attacks; }
    /** jc.dM: every auto-block mode publishes a visual pose while a sword/target is active. */
    public boolean rendersBlockPose() {
        return enabled()&&host.available()&&!host.cancelledByOtherModule()&&config.autoBlock.get()
                &&host.swordHeld()&&target!=null;
    }
    protected void onEnable() { worldChanged();rotations.reset(host.player(),random); }
    protected void onDisable() { worldChanged(); }
    public void worldChanged() {
        releaseBlocking();switchIndex=0;switchedAt=0;lastSelection=null;activeBlockMode=null;target=null;pendingReason=null;clicks.reset();rotations.reset(null,random);
    }
    /** jc.ds: one immediate eZ.b attempt, reset g, then skip the next normal cycle.
     * The original ea/jm time-bank publisher is a separate module, not synthesized here.
     */
    public void forceClickOnTickbase() {
        if(!enabled()||!host.available())return;
        if(target!=null||config.realClick.get())attackOnce();
        clicks.afterTickbaseAttack();
    }
    /** Count actual input-phase dispatches; queuing the two key pulses is not an attack. */
    public void realClickAttackAccepted() { if(enabled()&&config.realClick.get())++attacks; }
    public void updateTarget() {
        if(!enabled())return;
        if(!host.available()||host.cancelledByOtherModule()) { worldChanged();return; }
        Object selected=selectedBlockMode();
        if(selected!=activeBlockMode) { releaseBlocking();activeBlockMode=selected; }
        pendingReason=null;
        // jc writes these settings before invoking e2 and e1, in this order.
        config.aimReach.set(Math.max(config.aimReach.get(),config.reach.get()));
        config.reach.set(Math.min(config.reach.get(),config.aimReach.get()));
        Player player=host.player();target=select(player);
        Angles current=rotations.current();
        rotations.update(player,target,config,random,target!=null&&current!=null&&host.rayHits(target.id(),current,6));
        if(target==null)sprintAllowed=true;
        if(target==null||!host.swordHeld())releaseBlocking();
    }
    /** Called once immediately before the actual C03 walking-player packet dispatch. */
    public Angles motionPre() {
        if(!enabled()||!host.available()||pendingReason!=null||host.cancelledByOtherModule())return null;
        blockMotionPre();
        if(target!=null) {
            clicks.cycle(config,host.currentTimeMillis(),random,host.hurtResistantTime(),host.targetHurtResistantTime(target.id()),host.heldItemAttackSpeed(),
                    this::beforeAttackBatch,this::attackOnce,this::afterAttackBatch);
        }
        return config.rotate.get()?rotations.current():null;
    }
    private void beforeAttackBatch() {
        if(activeBlockMode==KillAuraSettings.BlockMode.INVALID)packetBlocked=false;
        else if(activeBlockMode==KillAuraSettings.BlockMode.SPOOF)restoreSpoofSlot();
        else if(normalPacketBlocking()&&config.unblockOnAttack.get())releaseBlock();
    }
    private void attackOnce() {
        ++attempts;
        // eZ.b checks Real click before target/distance and queues input even at aim-only reach.
        if(config.realClick.get()) {
            if(host.attack(target==null?-1:target.id(),true,config.ignoreBlocking.get()))++attacks;
            return;
        }
        boolean inReach=KillAuraTargetSelector.distance(host.player(),target)<=config.reach.get();
        if(inReach) {
            if(config.stopSprintOnAttack.get())host.stopSprintOnAttack();
            if(host.attack(target.id(),config.realClick.get(),config.ignoreBlocking.get()))++attacks;
        } else if(!config.realClick.get())host.swingMiss();
    }
    private void afterAttackBatch() {
        if(activeBlockMode==KillAuraSettings.BlockMode.INVALID&&!packetBlocked&&host.ticksExisted()!=invalidUsedTick)host.sendInvalidUsePacket();
        // eP.d runs once after all same-motion attempts, even if cancelled or aim-only.
        if(normalPacketBlocking()&&config.unblockOnAttack.get()) { if(!blocked)block(); }
        else if(clickKeyBlocking()&&host.swordHeld())setUseKey(true);
    }
    public void motionPost() {
        if(enabled()&&host.available()&&pendingReason==null&&!host.cancelledByOtherModule()
                &&target!=null) {
            if(activeBlockMode==KillAuraSettings.BlockMode.POST)block();

        }
    }
    public boolean hasRotation() {
        return enabled()&&host.available()&&!host.cancelledByOtherModule()&&pendingReason==null&&config.rotate.get()&&angles()!=null;
    }
    public boolean correctingMovement() { return hasRotation()&&config.correctMovement.get(); }
    public boolean allowsSprintKey() { return !enabled()||sprintAllowed; }
    public record Direction(float forward,float strafe) {}
    /** kQ.a(FF): preserve the two separate round operations at negative half sectors. */
    public static Direction correctedDirection(float yaw,float movementYaw) {
        float delta=KillAuraRotations.wrap(yaw-movementYaw);
        int absolute=Math.round(Math.abs(delta)/45F),signed=Math.round(delta/45F);
        int strafeEnabled=absolute!=0&&absolute!=4?1:0;
        int forwardEnabled=absolute!=2?1:0;
        return new Direction((absolute<2?1:-1)*forwardEnabled,(signed>0&&signed<4?1:-1)*strafeEnabled);
    }
    /** jc.dH PRE uses unconditional item/sneak multipliers for sprint eligibility. */
    public void updateSprintEligibility(float forward,boolean sprinting,boolean collided,boolean canSprint,
                                        boolean usingItem,boolean blind,Runnable stopSprint) {
        if(!hasRotation()||!config.correctMovement.get()||!config.silentlyCorrect.get())return;
        if(sprinting&&(forward<.8F||collided||!canSprint)) {
            stopSprint.run();sprinting=false;sprintAllowed=false;
        }
        // Original reallow path intentionally does not test collision after stopping.
        if(!sprinting&&forward>=.8F&&canSprint&&!usingItem&&!blind)sprintAllowed=true;
    }
    public void afterInput() { }
    public boolean stopForScaffold() { boolean had=blocked||packetBlocked||useKeyOwned;releaseBlocking();return had; }
    public void correction() { worldChanged(); }
    public boolean outgoing(Object packet) { return false; }
    private Target select(Player player) {
        var candidates=new ArrayList<Target>();
        for(Target t:host.targets())if(selector.select(player,List.of(t),config)!=null)candidates.add(t);
        if(candidates.stream().anyMatch(Target::preferred))candidates.removeIf(t->!t.preferred());
        if(lastSelection!=config.selection.get()){switchIndex=0;switchedAt=host.currentTimeMillis();lastSelection=config.selection.get();}
        if(candidates.isEmpty())return null;
        if(config.selection.get()==KillAuraSettings.Selection.SWITCH){
            if(host.currentTimeMillis()-switchedAt>=config.switchDelay.get()){switchIndex++;switchedAt=host.currentTimeMillis();}
            if(switchIndex>=candidates.size())switchIndex=0;
            return candidates.get(switchIndex);
        }
        if(config.selection.get()==KillAuraSettings.Selection.DYNAMIC){
            if(target!=null&&host.targetHurtResistantTime(target.id())<=16)
                for(Target t:candidates)if(t.id()==target.id())return t;
            candidates.removeIf(t->host.targetHurtResistantTime(t.id())>16);
            if(candidates.isEmpty())return target!=null&&KillAuraTargetSelector.distance(player,target)<=config.aimReach.get()?target:null;
            return candidates.stream().min(java.util.Comparator.comparingDouble(t->{double a=Math.abs(player.yaw()-(Math.toDegrees(Math.atan2(t.z()-player.z(),t.x()-player.x()))-90))%360;return a>180?360-a:a;})).orElse(null);
        }
        return selector.select(player,candidates,config);
    }
    private Object selectedBlockMode() {
        return !config.autoBlock.get()?null:config.clickAutoBlock.get()?config.clickBlockMode.get():config.blockMode.get();
    }
    private boolean normalPacketBlocking() {
        return activeBlockMode==KillAuraSettings.BlockMode.PRE||activeBlockMode==KillAuraSettings.BlockMode.POST;
    }
    private boolean clickKeyBlocking() {
        return activeBlockMode==KillAuraSettings.ClickBlockMode.HOLD||activeBlockMode==KillAuraSettings.ClickBlockMode.ATTACK_SYNC;
    }
    private void blockMotionPre() {
        // eX first undoes its previous slot spoof before testing the current sword/target.
        if(activeBlockMode==KillAuraSettings.BlockMode.SPOOF)restoreSpoofSlot();
        if(target==null||!host.swordHeld())return;
        if(activeBlockMode==KillAuraSettings.BlockMode.INVALID&&!packetBlocked){host.sendInvalidUsePacket();packetBlocked=true;invalidUsedTick=host.ticksExisted();}
        // Nightly f_: ordinary Watchdog requires the 1.12.2 protocol bridge hT.
        // This 1.8.9 base has no hT; only its independent aggressive path applies.
        if(activeBlockMode==KillAuraSettings.BlockMode.WATCHDOG&&config.aggressive.get()){host.sendInvalidUsePacket();packetBlocked=true;}
        if(activeBlockMode==KillAuraSettings.BlockMode.SPOOF) { host.sendUseItemPacket();packetBlocked=true; }
        else if(activeBlockMode==KillAuraSettings.BlockMode.PRE)block();
        else if(activeBlockMode==KillAuraSettings.ClickBlockMode.HOLD)setUseKey(true);
        else if(activeBlockMode==KillAuraSettings.ClickBlockMode.ATTACK_SYNC)
            setUseKey(clicks.ticks()<clicks.delay()-1);
        else if(activeBlockMode==KillAuraSettings.ClickBlockMode.HURT_TIME)
            setUseKey(host.targetHurtResistantTime(target.id())>13);
        else if(activeBlockMode==KillAuraSettings.ClickBlockMode.OPTIMIZED)
            setUseKey(clicks.ticks()<clicks.delay()-1||host.targetHurtResistantTime(target.id())>13);
    }
    private void block() {
        // eV/eW send use-item on each matching motion phase, even while already blocking.
        if(host.swordHeld()) { host.block();blocked=true; }
    }
    private void setUseKey(boolean pressed) {
        host.useKey(pressed);useKeyOwned=pressed;
    }
    private void releaseBlock() { if(blocked) { blocked=false;host.unblock(); } }
    private void sendSlotPair() {
        int slot=host.selectedSlot();host.sendHeldItemChange(slot%8+1);host.sendHeldItemChange(slot);
    }
    private void restoreSpoofSlot() { if(packetBlocked) { sendSlotPair();packetBlocked=false; } }
    private void releaseBlocking() {
        releaseBlock();
        if(packetBlocked) {
            if(activeBlockMode==KillAuraSettings.BlockMode.SPOOF)restoreSpoofSlot();
            else { packetBlocked=false;host.releaseUseItemPacket(); }
        }
        if(useKeyOwned) { useKeyOwned=false;host.useKey(false); }
    }
}
