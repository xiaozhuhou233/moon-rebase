package moon.module.movement;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import moon.module.NativeModule;
import moon.visual.gui.*;
import moon.visual.render.DrawServices;
import moon.visual.render.WorldDispatcher;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import org.lwjgl.opengl.*;

/** Alpha ju + fc: original ring selection, validity checks and movement-event steering. */
public final class TargetStrafeModule extends NativeModule {
    public final FloatSetting radius;
    public final IntegerSetting pointCount;
    public final BooleanSetting onlySpace,drawPoints;
    private final Minecraft mc;
    private final Supplier<EntityLivingBase> targetSource;
    private final List<SettingValue<?>> settings;
    private final List<Point> points=new ArrayList<>();
    private EntityLivingBase target;
    private Point selected;
    private int direction=1;
    private final WorldDispatcher renderer;
    private final FloatBuffer model=DrawServices.floatBuffer(16),projection=DrawServices.floatBuffer(16);
    private final FloatBuffer vertices=DrawServices.floatBuffer(45*24*7);
    private boolean cameraCaptured;
    private double originX,originY,originZ;
    private record Point(Vec3 offset,Vec3 position,boolean valid) { }

    public TargetStrafeModule(Minecraft mc,DrawServices drawing,MarkerHooks hooks,Supplier<EntityLivingBase> targetSource)throws IOException {
        super("targetstrafe","TargetStrafe",Category.PLAYER);
        this.mc=mc;this.targetSource=targetSource;
        radius=FloatSetting.builder("radius",hooks).configure("Radius",1.5F,.5F,4F,.1F).build();
        pointCount=IntegerSetting.builder("points",hooks).configure("Points",15,5,45,1).build();
        onlySpace=BooleanSetting.builder("only-space",hooks).configure("Space required",true).build();
        drawPoints=BooleanSetting.builder("draw-points",hooks).configure("Draw points",true).build();
        settings=List.of(radius,pointCount,onlySpace,drawPoints);
        renderer=new WorldDispatcher(drawing);renderer.initialize();
    }
    public List<? extends SettingValue<?>> settings(){return settings;}
    protected void onEnable(){worldChanged();}
    protected void onDisable(){worldChanged();}
    public void worldChanged(){points.clear();target=null;selected=null;direction=1;cameraCaptured=false;}
    public int generatedPoints(){return points.size();}
    public int validPoints(){return (int)points.stream().filter(Point::valid).count();}
    public int direction(){return direction;}
    public Vec3 selectedPoint(){return selected==null?null:selected.position;}

    public void motionPre() {
        if(!enabled()||mc.thePlayer==null||mc.theWorld==null){worldChanged();return;}
        target=targetSource.get();
        if(target==null||target.isDead||!target.isEntityAlive()||target.worldObj!=mc.theWorld){target=null;selected=null;points.clear();return;}
        // py.b() consumes the press edge; holding A/D must not undo collision reversals each tick.
        if(mc.gameSettings.keyBindLeft.isPressed())direction=1;
        if(mc.gameSettings.keyBindRight.isPressed())direction=-1;
        points.clear();
        for(int i=0;i<pointCount.get();i++) {
            double angle=i*(Math.PI*2)/pointCount.get();
            Vec3 offset=new Vec3(radius.get()*Math.cos(angle),0,radius.get()*Math.sin(angle));
            Vec3 position=target.getPositionVector().add(offset);
            points.add(new Point(offset,position,valid(position)));
        }
        selected=choosePoint();
    }
    private boolean valid(Vec3 position) {
        MovingObjectPosition hit=mc.theWorld.rayTraceBlocks(mc.thePlayer.getPositionVector(),position,false,true,false);
        if(hit!=null&&hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK)return false;
        BlockPos feet=new BlockPos(position),head=feet.up();
        IBlockState lower=mc.theWorld.getBlockState(feet),upper=mc.theWorld.getBlockState(head);
        if(lower.getBlock().canCollideCheck(lower,false)&&!lower.getBlock().isPassable(mc.theWorld,feet))return false;
        // ju uses the lower state in the upper block's canCollideCheck call; retained from bytecode.
        if(upper.getBlock().canCollideCheck(lower,false)&&!upper.getBlock().isPassable(mc.theWorld,head))return false;
        for(double y=position.yCoord-.1;y>0;y-=1) {
            IBlockState ground=mc.theWorld.getBlockState(new BlockPos(position.xCoord,Math.floor(y),position.zCoord));
            if(ground.getBlock().canCollideCheck(ground,false))return position.yCoord-y<=3;
        }
        return false;
    }
    private Point choosePoint() {
        int nearest=-1;double distance=2147483647D;
        for(int i=0;i<points.size();i++) {
            Point point=points.get(i);
            double next=point.position.distanceTo(mc.thePlayer.getPositionVector());
            if(point.valid&&next<distance){distance=next;nearest=i;}
        }
        if(nearest<0)return null;
        if(points.size()==1)return points.get(nearest);
        for(int attempts=0;attempts<=points.size();attempts++) {
            Point next=points.get(Math.floorMod(nearest+direction,points.size()));
            if(next.valid)return next;
            direction=-direction;
        }
        return null;
    }
    public boolean steering() {
        return enabled()&&mc.thePlayer!=null&&target!=null&&selected!=null&&target.isEntityAlive()
            &&target.worldObj==mc.theWorld&&targetSource.get()==target&&(!onlySpace.get()||mc.gameSettings.keyBindJump.isKeyDown());
    }
    public float movementYaw() {
        if(!steering())return Float.NaN;
        float orbit=yawTo(target.getPositionVector())+135*-direction;
        float angle=(float)Math.toRadians(orbit);
        Vec3 aim=new Vec3(target.posX-MathHelper.sin(angle)*radius.get(),target.posY,target.posZ+MathHelper.cos(angle)*radius.get());
        return yawTo(aim);
    }
    private float yawTo(Vec3 point) {
        return (float)(MathHelper.atan2(point.zCoord-mc.thePlayer.posZ,point.xCoord-mc.thePlayer.posX)*(180/Math.PI))-90;
    }
    public void move(SpeedModule.Move move) {
        float yaw=movementYaw();
        if(Float.isFinite(yaw)){move.forward=1;move.strafe=0;move.yaw=yaw;}
    }
    public void captureCamera(double x,double y,double z) {
        cameraCaptured=enabled()&&drawPoints.get()&&target!=null&&selected!=null;
        if(!cameraCaptured)return;
        originX=x;originY=y;originZ=z;
        model.clear();projection.clear();
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,projection);
    }
    /** c9 POST uses interpolated target coordinates and 0.04-block, half-alpha cubes. */
    public void render(float partialTicks) {
        if(!cameraCaptured)return;cameraCaptured=false;
        if(!enabled()||!drawPoints.get()||target==null||selected==null||target.worldObj!=mc.theWorld)return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            renderer.getBuffer().bind(GL15.GL_ARRAY_BUFFER);renderer.getArray().bind();renderer.getProgram().use();
            renderer.getModelviewUniform().setMatrix4(false,model.asReadOnlyBuffer());renderer.getProjectionUniform().setMatrix4(false,projection.asReadOnlyBuffer());
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableDepth();GlStateManager.depthMask(false);GlStateManager.disableCull();GlStateManager.disableAlpha();
            double x=target.lastTickPosX+(target.posX-target.lastTickPosX)*partialTicks-originX;
            double y=target.lastTickPosY+(target.posY-target.lastTickPosY)*partialTicks-originY;
            double z=target.lastTickPosZ+(target.posZ-target.lastTickPosZ)*partialTicks-originZ;
            vertices.clear();
            for(Point point:points)cube(x+point.offset.xCoord,y,z+point.offset.zCoord,!point.valid?0xff0000:point==selected?0x00ff00:0xffffff);
            vertices.flip();GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_DYNAMIC_DRAW);GL11.glDrawArrays(GL11.GL_QUADS,0,points.size()*24);
        } finally {
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();GlStateManager.depthMask(write);
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();GlStateManager.tryBlendFuncSeparate(src,dst,sa,da);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);GL30.glBindVertexArray(array);GL20.glUseProgram(program);
        }
    }
    private void cube(double x,double y,double z,int color) {
        double half=.02F,height=.04F;
        double[][] corners={{x-half,y,z-half},{x+half,y,z-half},{x+half,y+height,z-half},{x-half,y+height,z-half},{x-half,y,z+half},{x+half,y,z+half},{x+half,y+height,z+half},{x-half,y+height,z+half}};
        int[] faces={0,1,2,3,5,4,7,6,4,0,3,7,1,5,6,2,3,2,6,7,4,5,1,0};
        for(int i:faces){double[] p=corners[i];vertices.put((float)p[0]).put((float)p[1]).put((float)p[2]).put((color>>16&255)/255F).put((color>>8&255)/255F).put((color&255)/255F).put(127/255F);}
    }
}
