package moon.module.visual;

import java.awt.Color;
import java.util.*;
import moon.visual.gui.*;
import moon.visual.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjglx.opengl.Display;

/** jQ: offscreen PRE overlay. Original DATA: seed 45410192382669, cache 2m,
 * excluded IDs -84/-42, cadence cap 240. CPU snapshots replace its endless worker;
 * every temporary DrawTask is released on the render thread. */
public final class PointersModule implements ModuleRowWidget.Module,AutoCloseable {
    public enum Target implements DisplayNamed {
        PLAYER,ANIMAL,MOB,INVISIBLE;
        public String displayName(){return switch(this){case PLAYER->"Player";case ANIMAL->"Animal";case MOB->"Mob";case INVISIBLE->"Invisible";};}
    }
    public interface Rendering {
        DrawServices drawing();ShaderDispatcher rectangle();ShaderDispatcher text();MsdfFontBase font();
        void isolated(Runnable action);
    }
    public record Point(float x,float y){}
    public record Pointer(int entityId,Point tip,Point left,Point right,float boxX,float boxY,float boxSize,int color,float health,int healthColor,String name,String distance){}
    private record Health(double value,long written){}
    private final Minecraft mc;private final Rendering rendering;
    public final MultiChoiceSetting<Target> targets;
    public final FloatSetting size,radius,opacity;
    public final BooleanSetting stretch,distanceColored,box,name,distance,health;
    public final ColorSetting color;
    private final List<SettingValue<?>> settings;
    private List<EntityLivingBase> offscreen=List.of();
    private final Map<EntityLivingBase,Health> healthCache=new WeakHashMap<>();
    private List<Pointer> pointers=List.of();private Object world;private long nextUpdate;private boolean enabled;
    public PointersModule(Minecraft mc,DrawServices draw,Rendering rendering){
        this.mc=mc;this.rendering=rendering;var hooks=draw.markers;
        targets=MultiChoiceSetting.builder("targets",Target.class,hooks).configure("Targets",Target.PLAYER).build();
        size=FloatSetting.builder("size",hooks).configure("Size",10,5,25,.1F).build();
        radius=FloatSetting.builder("radius",hooks).configure("Radius",45,10,200,1).build();
        stretch=BooleanSetting.builder("stretch",hooks).configure("Stretch",false).build();
        distanceColored=BooleanSetting.builder("distance-colored",hooks).configure("Distance colored",true).build();
        color=ColorSetting.builder("color",hooks).configure("Color",-1).visibility(()->!distanceColored.get()).build();
        opacity=FloatSetting.builder("opacity",hooks).configure("Opacity",.5F,0,1,.05F).build();
        box=BooleanSetting.builder("box",hooks).configure("Box",false).build();
        name=BooleanSetting.builder("name",hooks).configure("Name",true).build();
        distance=BooleanSetting.builder("distance",hooks).configure("Distance",true).build();
        health=BooleanSetting.builder("health",hooks).configure("Health",true).build();
        var arrows=SettingGroup.builder(hooks).configure("Arrow Settings",size,radius,stretch,distanceColored,color,opacity).build();
        var info=SettingGroup.builder(hooks).configure("Info Settings",box,name,health,distance).build();
        settings=List.of(targets,arrows,info);
    }
    public String displayName(){return "Pointers";}public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}public void setEnabled(boolean value){enabled=value;if(!value)clear();else nextUpdate=0;}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public List<Pointer> pointers(){return pointers;}
    private void clear(){pointers=List.of();offscreen=List.of();healthCache.clear();world=null;nextUpdate=0;}
    public boolean accepts(Entity e){
        if(e==mc.thePlayer||e.getEntityId()==-84||e.getEntityId()==-42)return false;
        if(e.isInvisible()&&!targets.isSelected(Target.INVISIBLE))return false;
        return e instanceof EntityMob&&targets.isSelected(Target.MOB)
            ||(e instanceof EntityAnimal||e instanceof EntityVillager||e instanceof EntitySquid)&&targets.isSelected(Target.ANIMAL)
            ||e instanceof EntityPlayer&&targets.isSelected(Target.PLAYER);
    }
    private static double lerp(double current,double previous,float partial){return previous+(current-previous)*partial;}
    /** Capture while the world camera matrices are still installed, before GUI ortho. */
    public void worldPost(){
        if(!enabled||mc.theWorld==null||mc.thePlayer==null){clear();return;}
        if(world!=mc.theWorld){clear();world=mc.theWorld;}
        var view=mc.getRenderViewEntity();if(view==null){offscreen=List.of();return;}
        var frustum=new Frustum();frustum.setPosition(view.posX,view.posY,view.posZ);List<EntityLivingBase> result=new ArrayList<>();
        for(Entity e:mc.theWorld.loadedEntityList)if(e instanceof EntityLivingBase living&&accepts(e)
            &&!e.ignoreFrustumCheck&&!frustum.isBoundingBoxInFrustum(e.getEntityBoundingBox()))result.add(living);
        offscreen=List.copyOf(result);
    }
    private static float[] normalized(float x,float y,float z){
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        return new float[]{x/length,y/length,z/length};
    }
    private static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static Point rotate(Point pivot,Point point,float degrees){
        double radians=Math.toRadians(degrees);float cos=(float)Math.cos(radians),sin=(float)Math.sin(radians);
        return new Point(pivot.x+(point.x-pivot.x)*cos-(point.y-pivot.y)*sin,
            pivot.y+(point.x-pivot.x)*sin+(point.y-pivot.y)*cos);
    }
    /** jQ's float operations include 3D normalization and absolute-point rotation.
     * nowNanos uses System.nanoTime, like the original Guava expiry ticker. */
    public Pointer project(EntityLivingBase e,float partial,int width,int height,long nowNanos){
        var p=mc.thePlayer;
        float dx=(float)(lerp(e.posX,e.lastTickPosX,partial)-lerp(p.posX,p.lastTickPosX,partial));
        float dy=(float)(lerp(e.posY,e.lastTickPosY,partial)-lerp(p.posY,p.lastTickPosY,partial));
        float dz=(float)(lerp(e.posZ,e.lastTickPosZ,partial)-lerp(p.posZ,p.lastTickPosZ,partial));
        float[] direction=normalized(dz,dx,dy);
        float yaw=(float)Math.toRadians(360F-net.minecraft.util.MathHelper.wrapAngleTo180_float(p.rotationYaw));
        float[] forward=normalized((float)Math.cos(yaw),(float)Math.sin(yaw),0F);
        float[] side={0F*forward[2]-1F*forward[1],1F*forward[0]-0F*forward[2],0F*forward[1]-0F*forward[0]};
        float r=radius.get()*(height/480F),along=dot(direction,forward),across=dot(direction,side);
        float degrees=(float)Math.toDegrees(Math.atan2(r*-across,r*-along)+Math.PI);
        float angle=-degrees,radians=(float)(-Math.toRadians(degrees));
        float sin=(float)Math.sin(radians)*(stretch.get()?2:1),cos=(float)Math.cos(radians);
        float x=width/2F+r*sin,y=height/2F-r*cos,s=size.get();
        Point pivot=new Point(x,y),unrotatedRight=new Point(x+s/2F,y+s/2F),
            unrotatedLeft=new Point(x-s/2F,y+s/2F),unrotatedTip=new Point(x,y-s/2F);
        float edgeX=unrotatedTip.x-unrotatedLeft.x,edgeY=unrotatedTip.y-unrotatedLeft.y;
        float edgeLength=(float)Math.sqrt(edgeX*edgeX+edgeY*edgeY);
        double arc=Math.asin(s/edgeLength);
        float circumRadius=(float)(edgeLength/(2D*Math.sin(arc)));
        Point tip=rotate(pivot,unrotatedTip,angle),left=rotate(pivot,unrotatedLeft,angle),right=rotate(pivot,unrotatedRight,angle);
        double midX1=(left.x+right.x)/2F,midY1=(left.y+right.y)/2F,
            midX2=(right.x+tip.x)/2F,midY2=(right.y+tip.y)/2F;
        double slope1=-(right.x-left.x)/(right.y-left.y),slope2=-(tip.x-right.x)/(tip.y-right.y);
        float centerX=(float)((midY2-midY1-slope2*midX2+slope1*midX1)/(slope1-slope2));
        float centerY=(float)(midY1+slope1*(centerX-midX1));
        float extent=circumRadius*2F+4F,boxX=centerX-circumRadius-2F,boxY=centerY-circumRadius-2F;
        float ratio=0F;
        if(health.get()){
            float current=Math.min(e.getHealth()/e.getMaxHealth(),1F);Health old=healthCache.get(e);
            double previous=old==null||nowNanos-old.written>=120_000_000_000L?current:old.value;
            double smoothed=previous+(current-previous)*.10000000149011612;
            healthCache.put(e,new Health(smoothed,nowNanos));ratio=(float)smoothed;
        }
        int rgb=distanceColored.get()?Color.HSBtoRGB(Math.max(0,Math.min(p.getDistanceToEntity(e),40)/40F)/3,1,1):color.get();
        int argb=(Math.max(0,Math.min(255,(int)(opacity.get()*255)))<<24)|(rgb&0xffffff);
        int healthRgb=Color.HSBtoRGB(Math.max(0,Math.min(e.getHealth(),e.getMaxHealth())/e.getMaxHealth())/3,1,1);
        return new Pointer(e.getEntityId(),tip,left,right,boxX,boxY,extent,argb,ratio,healthRgb,e.getName(),Math.round(p.getDistanceToEntity(e))+"m");
    }
    public void prepare(float partial){
        if(!enabled||mc.theWorld==null||mc.thePlayer==null){clear();return;}
        if(world!=mc.theWorld){clear();world=mc.theWorld;}
        long now=System.nanoTime();int fps=Minecraft.getDebugFPS();
        int budget=Math.min(Math.max(fps/3,Display.getDesktopDisplayMode().getFrequency()),240);
        if(now<nextUpdate&&mc.gameSettings.limitFramerate==260&&!mc.gameSettings.enableVsync&&fps>=budget)return;
        nextUpdate=now+Math.max(1,1000/Math.max(1,budget))*1_000_000L;
        var sr=new ScaledResolution(mc);List<Pointer> result=new ArrayList<>();
        for(EntityLivingBase e:offscreen)if(e.worldObj==mc.theWorld&&accepts(e))result.add(project(e,partial,sr.getScaledWidth(),sr.getScaledHeight(),now));
        pointers=List.copyOf(result);
    }
    private void rectangle(DrawTask task,float x,float y,float w,float h,int color){task.add(new UiGeometry.Rectangle(x,y,w,h,color));}
    private void box(DrawTask task,float x,float y,float s){
        float right=x+s,bottom=y+s;
        rectangle(task,x,y,s,1.5F,0xff000000);rectangle(task,x,y,1.5F,s,0xff000000);
        rectangle(task,right-s,y,s,1.5F,0xff000000);rectangle(task,right-1.5F,y,1.5F,s,0xff000000);
        rectangle(task,x,bottom-1.5F,s,1.5F,0xff000000);rectangle(task,x,bottom-s,1.5F,s,0xff000000);
        rectangle(task,right-s,bottom-1.5F,s,1.5F,0xff000000);rectangle(task,right-1.5F,bottom-s,1.5F,s,0xff000000);
        rectangle(task,x+.5F,y+.5F,s-1F,.5F,-1);rectangle(task,x+.5F,y+.5F,.5F,s-1F,-1);
        rectangle(task,right-s+.5F,y+.5F,s-1F,.5F,-1);rectangle(task,right-1F,y+.5F,.5F,s-1F,-1);
        rectangle(task,x+.5F,bottom-1F,s-1F,.5F,-1);rectangle(task,x+.5F,bottom-s+.5F,.5F,s-1F,-1);
        rectangle(task,right-s+.5F,bottom-1F,s-1F,.5F,-1);rectangle(task,right-1F,bottom-s+.5F,.5F,s-1F,-1);
    }
    public void draw(){
        if(!enabled||pointers.isEmpty())return;
        rendering.isolated(()->{
            var draw=rendering.drawing();var font=rendering.font();
            DrawTask triangles=DrawTask.builder(draw).dispatcher(rendering.rectangle()).retained().build();triangles.getState().setPrimitive(GL11.GL_TRIANGLES);
            DrawTask rectangles=DrawTask.builder(draw).dispatcher(rendering.rectangle()).retained().build();
            DrawTask texts=DrawTask.builder(draw).dispatcher(rendering.text()).texture(font::bind).retained().build();
            try{
                for(Pointer p:pointers){
                    triangles.add(UiGeometry.Triangle.create(p.tip.x,p.tip.y,p.left.x,p.left.y,p.right.x,p.right.y,p.color,72,draw.markers));
                    float x=p.boxX,y=p.boxY,s=p.boxSize;
                    if(box.get())box(rectangles,x,y,s);
                    if(health.get()){
                        float hx=x-2F-.5F,filled=p.health*s;
                        rectangle(rectangles,hx,y,.5F,s,0x80000000);rectangle(rectangles,hx,y,2,.5F,0x80000000);
                        rectangle(rectangles,hx+2F-.5F,y,.5F,s,0x80000000);rectangle(rectangles,hx,y+s-.5F,2,.5F,0x80000000);
                        rectangle(rectangles,hx,y,2,s,0x80000000);
                        rectangle(rectangles,hx+.5F,y+.5F+(s-filled),1,filled-1,p.healthColor);
                    }
                    if(name.get())font.draw(texts,p.name,6,x+s/2-font.width(p.name,6)/2,y-font.height(6),-1,true,false,0);
                    if(distance.get())font.draw(texts,p.distance,6,x+s/2-font.width(p.distance,6)/2,y+s,-1,true,false,0);
                }
                boolean smooth=GL11.glIsEnabled(GL11.GL_POLYGON_SMOOTH);
                GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
                try{triangles.draw();}finally{if(!smooth)GL11.glDisable(GL11.GL_POLYGON_SMOOTH);}
                rectangles.draw();texts.draw();
            }finally{triangles.getState().release();rectangles.getState().release();texts.getState().release();}
        });
    }
    @Override public void close(){enabled=false;clear();}
}
