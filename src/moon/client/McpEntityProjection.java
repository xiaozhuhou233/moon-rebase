package moon.client;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjglx.util.glu.GLU;
import org.lwjglx.util.vector.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.src.Config;
import net.minecraft.util.AxisAlignedBB;
import net.optifine.shaders.Shaders;

/** gs/k1: the original eight-corner projection producer. Camera buffers come
 * from ActiveRenderInfo, so hand/overlay matrix changes cannot replace them. */
public final class McpEntityProjection {
    private final FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),point=BufferUtils.createFloatBuffer(3);
    private final IntBuffer viewport=BufferUtils.createIntBuffer(4);
    private boolean captured;

    public void capture(FloatBuffer modelview,FloatBuffer perspective,IntBuffer area){
        for(int i=0;i<16;i++){model.put(i,modelview.get(i));projection.put(i,perspective.get(i));}
        for(int i=0;i<4;i++)viewport.put(i,area.get(i));
        captured=true;
    }
    /** gs uses width/1.5 and height minus .2 while sneaking, independently of
     * the entity's collision AABB. The subtraction by .2 is a double operation. */
    public static AxisAlignedBB entityBounds(Entity entity,float partial){
        double x=entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial;
        double y=entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial;
        double z=entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial;
        float radius=entity.width/1.5F;
        double height=(double)entity.height-(entity.isSneaking()?.2D:0D);
        return new AxisAlignedBB(x-radius,y,z-radius,x+radius,y+height,z+radius);
    }
    public Vector4f project(Entity entity,float partial,Minecraft mc){
        if(!captured)return null;
        var render=mc.getRenderManager();var resolution=new ScaledResolution(mc);
        float renderScale=Config.isShaders()?(float)Shaders.renderWidth/mc.displayWidth:1F;
        return bounds(entityBounds(entity,partial),render.viewerPosX,render.viewerPosY,render.viewerPosZ,
            model,projection,viewport,resolution.getScaleFactor(),resolution.getScaledHeight(),renderScale,point);
    }
    /** Pure original gs/k1 arithmetic endpoint. No edge or side-plane clipping.
     * Cz enumerates z, then x, then y. Keep the original depth-initialized z
     * component and inclusive [0,1] depth test, including degenerate bounds. */
    public static Vector4f bounds(AxisAlignedBB box,double cameraX,double cameraY,double cameraZ,
            FloatBuffer model,FloatBuffer projection,IntBuffer viewport,int scale,int height,float renderScale,FloatBuffer point){
        Vector4f result=null;
        for(int i=0;i<8;i++){
            double x=(i&2)==0?box.minX:box.maxX,y=(i&4)==0?box.minY:box.maxY,z=(i&1)==0?box.minZ:box.maxZ;
            if(!GLU.gluProject((float)(x-cameraX),(float)(y-cameraY),(float)(z-cameraZ),model,projection,viewport,point))continue;
            double screenX=point.get(0)/scale/renderScale,screenY=height-point.get(1)/scale/renderScale,depth=point.get(2);
            if(!(depth>=0D&&depth<=1D))continue;
            if(result==null)result=new Vector4f((float)screenX,(float)screenY,(float)depth,(float)screenY);
            result.x=(float)Math.min(screenX,result.x);result.y=(float)Math.min(screenY,result.y);
            result.z=(float)Math.max(screenX,result.z);result.w=(float)Math.max(screenY,result.w);
        }
        return result;
    }
}
