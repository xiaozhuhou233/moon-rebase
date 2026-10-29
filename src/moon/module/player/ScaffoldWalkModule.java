package moon.module.player;
import moon.module.player.scaffoldwalk.ScaffoldSettings;

import java.util.List;
import java.util.ArrayList;
import java.util.function.DoubleSupplier;
import moon.module.NativeModule;
import moon.module.combat.killaura.KillAuraRotations;
import moon.module.combat.killaura.KillAuraRotations.Angles;
import moon.visual.gui.SettingValue;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** Readable jE lifecycle: locate support, rotate PRE, place after motion, restore slot.
 * Walking cadence is the destination adapter; original dH/dw timing is not claimed. */
public final class ScaffoldWalkModule extends NativeModule {
    public record Position(int x,int y,int z) {}
    public record Placement(Position support,int face,double x,double y,double z) {}
    public interface Host {
        boolean available(); double feetY(); boolean grounded(); Angles camera();
        int selectedSlot(); void selectSlot(int slot); int blockSlot(boolean allowSwitch);
        Placement find(double level,double radius); Angles aim(Placement placement,Angles current,ScaffoldSettings.Rotation mode);
        boolean place(Placement placement,Angles angles,int slot,boolean silent,boolean realClick,boolean checkBlock,boolean swing);
        void stopSprint(); void limitSpeed(double speed);
        /** fo uses live jump and the four movement key states, not last tick input axes. */
        default boolean jumpHeld(){return false;}
        default boolean movementKeyHeld(){return false;}
        default double positionY(){return feetY();}
        default void verticalMotion(double value){}
        default boolean connected(){return available();}
        default Object worldIdentity(){return this;}
        default Object connectionIdentity(){return this;}
        default boolean sprinting(){return false;}
        default int speedAmplifier(){return -1;}
        default float forward(){return 0;}
        default float strafe(){return 0;}
        default double positionX(){return 0;}
        default double positionZ(){return 0;}
        default double previousX(){return positionX();}
        default double previousZ(){return positionZ();}
        default void horizontalMotion(double x,double z){}
        default void releaseSprintKey(){}
        default void sendSprintPacket(SprintPacket packet){}
    }
    /** fl packet view; the MCP adapter retains the actual packet instance. */
    public enum SprintPacketKind { OTHER, PLACE, ANIMATION, MOVEMENT, CORRECTION }
    public interface SprintPacket {
        SprintPacketKind kind();
        default boolean moving(){return false;}
        default boolean excluded(){return false;}
        default double x(){return 0;}
        default double z(){return 0;}
        default void horizontalPosition(double x,double z){}
    }
    public final ScaffoldSettings config;
    private final Host host;
    private final DoubleSupplier sprintRandom;
    private final List<SprintPacket> sprintPackets=new ArrayList<>();
    private int sprintStep;
    private double sprintCorrection;
    private Object sprintWorld,sprintConnection;
    private int originalSlot=-1,selectedByModule=-1,slot=-1;
    private double level;
    private Angles angles,previous;
    private Placement placement;
    private long placements;
    private float towerYaw;
    private double towerFloor;
    private boolean towerPlacement;
    private enum Phase { BEFORE, PRE, POST }
    public record TowerMovement(float yaw,double speed) {}
    public ScaffoldWalkModule(Host host,MarkerHooks hooks){this(host,hooks,new java.util.Random()::nextDouble);}
    public ScaffoldWalkModule(Host host,MarkerHooks hooks,DoubleSupplier random){
        super("scaffold-walk","ScaffoldWalk",Category.PLAYER);this.host=host;sprintRandom=random;config=new ScaffoldSettings(hooks);
    }
    @Override public String hudSuffix(){return config.rotation.get().displayName();}
    public List<? extends SettingValue<?>> settings(){return config.roots();}
    public String pendingReason(){
        if(!enabled())return null;
        return null;
    }
    public boolean active(){return enabled()&&host.available()&&pendingReason()==null;}
    public boolean blocksCombat(){return enabled()&&host.available();}
    public boolean safeWalk(){return active()&&config.safeWalk.get();}
    public boolean correctingMovement(){return active()&&config.rotate.get()&&config.correctMovement.get()&&angles!=null&&(placement!=null||config.keepRotation.get());}
    public Angles angles(){return angles;}
    public Placement placement(){return placement;}
    public long placements(){return placements;}
    protected void onEnable(){resetTower();clearSprint();begin();}
    private void begin(){if(!host.available())return;originalSlot=host.selectedSlot();level=host.feetY();angles=previous=host.camera();}
    private void restore(){
        if(originalSlot>=0&&selectedByModule>=0&&host.selectedSlot()==selectedByModule)host.selectSlot(originalSlot);
        originalSlot=selectedByModule=slot=-1;placement=null;angles=previous=null;resetTower();
    }
    protected void onDisable(){finishSprint();restore();}
    public void worldChanged(){originalSlot=selectedByModule=slot=-1;placement=null;angles=previous=null;resetTower();clearSprint();}
    private void resetTower(){towerYaw=0;towerFloor=0;towerPlacement=false;}
    /** Original fo normal null-marker path; constants are literal bytecode operands. */
    private void towerMotion(Phase phase){
        if(!active()||!config.tower.get()){resetTower();return;}
        if(!host.jumpHeld()||!host.movementKeyHeld())return;
        if(host.grounded()){towerFloor=Math.floor(host.positionY());towerYaw=host.camera().yaw();}
        if(host.grounded()&&phase==Phase.PRE){
            host.verticalMotion(0.41999998688697815);
            towerPlacement=false;
        }else if(host.positionY()-towerFloor>=0.7599999904632568&&towerPlacement&&phase==Phase.BEFORE){
            host.verticalMotion(-(host.positionY()%1.0));
            towerPlacement=false;
        }
    }
    public void motionBefore(){towerMotion(Phase.BEFORE);sprintBefore();}
    private boolean hypixelSprint(){return enabled()&&host.connected()&&config.sprint.get()&&config.sprintMode.get()==ScaffoldSettings.Sprint.HYPIXEL;}
    private boolean sameSprintWorld(){return sprintWorld==host.worldIdentity()&&sprintConnection==host.connectionIdentity();}
    private void clearSprint(){sprintPackets.clear();sprintStep=0;sprintCorrection=0;sprintWorld=host.worldIdentity();sprintConnection=host.connectionIdentity();}
    private void flushSprint(){
        List<SprintPacket> queued=List.copyOf(sprintPackets);sprintPackets.clear();
        for(SprintPacket packet:queued)host.sendSprintPacket(packet);
    }
    // Destination lifecycle: drain on disable/mode change on the same live connection;
    // discard world-owned packets on world/connection replacement.
    private void finishSprint(){if(host.connected()&&sameSprintWorld())flushSprint();clearSprint();}
    private boolean prepareSprint(){
        if(!sameSprintWorld())clearSprint();
        if(!hypixelSprint()){finishSprint();return false;}
        return true;
    }
    /** kV direction integers decoded from nine original call sites: -45, 90, 45. */
    public static float movementDirection(float yaw,float forward,float strafe){
        int side=forward<0?-45:forward==0?90:45;
        if(forward<0)yaw+=180;
        if(strafe<0)yaw+=side;
        if(strafe>0)yaw-=side;
        return (float)(yaw*0.017453292519943295);
    }
    private void sprintSpeed(double value){
        double angle=movementDirection(host.camera().yaw(),host.forward(),host.strafe());
        host.horizontalMotion(-Math.sin(angle)*value,Math.cos(angle)*value);
    }
    /** fl BEFORE normal non-null cP marker path; amplifier is literal, without +1. */
    private void sprintBefore(){
        if(!prepareSprint())return;
        boolean moving=host.movementKeyHeld();
        if(host.jumpHeld()&&moving){host.releaseSprintKey();host.stopSprint();}
        int amplifier=host.speedAmplifier();
        double base=host.sprinting()?.2873:.216;
        if(amplifier>=0)base*=1+.2*amplifier;
        if(base>.216&&moving&&host.grounded()&&amplifier>=0)sprintSpeed(config.slowSprint.get()?.106:.126);
        else if(moving&&config.slowSprint.get())sprintSpeed(.108);
    }
    /** Called once per incoming/outgoing packet, including correction countdown events.
     * Buffered packets bypass this observer when drained, as original jR.b does. */
    public boolean sprintPacket(SprintPacket packet){
        if(!prepareSprint())return false;
        if(packet.kind()==SprintPacketKind.CORRECTION)sprintCorrection=20;
        if(!(host.jumpHeld()&&host.movementKeyHeld())&&sprintCorrection==0){
            if(packet.kind()==SprintPacketKind.PLACE||packet.kind()==SprintPacketKind.ANIMATION){sprintPackets.add(packet);return true;}
            if(packet.kind()==SprintPacketKind.MOVEMENT&&!packet.excluded()){
                if(packet.moving()&&host.grounded()){
                    double angle=movementDirection(host.camera().yaw(),host.forward(),host.strafe());
                    double distance=Math.hypot(host.positionX()-host.previousX(),host.positionZ()-host.previousZ());
                    double offset=config.slowSprint.get()?Math.abs(distance-((.2116-.2)*sprintRandom.getAsDouble()+.2)):Math.max(distance-.15,0);
                    if(++sprintStep==1)packet.horizontalPosition(packet.x()-(-Math.sin(angle))*offset,packet.z()-Math.cos(angle)*offset);
                    else {flushSprint();sprintStep=0;}
                }else {flushSprint();sprintStep=0;}
            }
        }else{
            if(sprintCorrection>0)sprintCorrection--;
            host.releaseSprintKey();host.stopSprint();flushSprint();sprintStep=0;
        }
        return false;
    }
    /** MoveEvent forces forward=1, strafe=0, captured yaw and horizontal speed 0.21168. */
    public TowerMovement towerMovement(){
        return active()&&config.tower.get()&&host.jumpHeld()&&host.movementKeyHeld()
            ?new TowerMovement(towerYaw,0.21168):null;
    }
    public Angles motionPre(){
        if(!active()){restore();return null;}
        if(originalSlot<0)begin();
        if(selectedByModule>=0&&host.selectedSlot()!=selectedByModule){originalSlot=host.selectedSlot();selectedByModule=-1;}
        if(config.silent.get()&&selectedByModule>=0){host.selectSlot(originalSlot);selectedByModule=-1;}
        slot=host.blockSlot(config.switchBlock.get());
        if(!config.sprint.get())host.stopSprint();
        if(config.limitSpeed.get()&&host.grounded())host.limitSpeed(config.speedLimit.get());
        if(host.grounded()||config.tower.get()||!config.keepY.get())level=Math.floor(host.feetY());
        placement=slot<0?null:host.find(level,config.radius.get());
        if(slot>=0&&!config.silent.get()&&slot!=host.selectedSlot()){host.selectSlot(slot);selectedByModule=slot;}
        if(placement!=null&&config.rotate.get()){
            Angles target=host.aim(placement,angles,config.rotation.get());
            Angles next=new Angles(step(previous.yaw(),target.yaw(),config.rotationSpeed.get()),step(previous.pitch(),target.pitch(),config.rotationSpeed.get()));
            previous=angles;angles=next;
        }
        towerMotion(Phase.PRE);
        return config.rotate.get()&&(placement!=null||config.keepRotation.get())?angles:null;
    }
    public void motionPost(){
        towerMotion(Phase.POST);
        if(!active()||placement==null||slot<0)return;
        Angles applied=config.rotate.get()?angles:host.camera();
        if(host.place(placement,applied,slot,config.silent.get(),config.realClick.get(),config.checkBlock.get(),config.swing.get())){
            placements++;if(config.unsprint.get())host.stopSprint();
        }
        // jE.dw calls fn.a after the placement attempt, even when fd reports no success.
        if(config.tower.get())towerPlacement=true;
        placement=null;
    }
    public static float step(float previous,float target,float speed){return previous+Math.max(-speed,Math.min(speed,KillAuraRotations.wrap(target-previous)));}
}
