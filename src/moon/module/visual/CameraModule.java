package moon.module.visual;

import java.util.List;
import java.util.Arrays;
import java.util.BitSet;
import moon.client.McpGlowRenderPolicy;
import moon.client.McpLuaRenderer;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.*;

/** jJ. Render-thread HUD cache and vanilla render policies; Java 24 / LWJGL3.
 * fx/gg/gh: original bounding-voxel sampling, DDA and two-bit cache. Its worker
 * is scheduled on the render thread at the original 10 ms minimum cadence.
 * r3 reads particle lighting independently of the module enabled flag; j1
 * applies its outer particle AABB frustum gate even when culling is unchecked.
 */
public final class CameraModule implements ModuleRowWidget.Module,AutoCloseable {
    public final BooleanSetting limitHudFps,entityBatching,entityCulling,particleCulling,
        disableArmorStands,disableParticleLighting,noHurtCam,hideBossBar,hideTitle,
        hideAchievements,hideScoreIndex,removeSpeedEffect;
    public final IntegerSetting maxEntityDistance,maxTileEntityDistance;
    
    private final Minecraft mc;
    private final List<SettingValue<?>> settings;
    private boolean enabled,dirty=true,capturing;
    private Framebuffer cache;
    private Object world;
    private int guiWidth,guiHeight;
    private long captures,reuses;
    private final Frustum particleFrustum=new Frustum();
    private static boolean compilingBatch;
    private VoxelVisibility visibility;
    private World occlusionWorld;
    private long nextOcclusionNanos,occlusionUpdates;

    public CameraModule(Minecraft mc,MarkerHooks hooks){
        this.mc=mc;
        limitHudFps=bool(hooks,"limit-hud-fps","Limit Hud FPS",true);
        limitHudFps.setDescription("Improves performance but breaks vignette");
        entityBatching=bool(hooks,"batch-rendering-entity","Entity Batching",false);
        entityCulling=bool(hooks,"entity-culling","Entity Culling",false);
        particleCulling=bool(hooks,"particle-culling","Particle Culling",false);
        disableArmorStands=bool(hooks,"disable-armor-stands","Disable Armor Stands",true);
        disableParticleLighting=bool(hooks,"disable-particle-lighting","Disable Particle Lighting",true);
        maxEntityDistance=IntegerSetting.builder("max-entity-distance",hooks).configure("Max entity distance",64,8,1024,1).build();
        maxTileEntityDistance=IntegerSetting.builder("max-tile-entity-distance",hooks).configure("Max tile entity distance",64,8,1024,1).build();
        noHurtCam=bool(hooks,"no-hurt-cam","No Hurt Camera",false);
        hideBossBar=bool(hooks,"hide-boss-bar","Hide Boss Bar",false);
        hideTitle=bool(hooks,"hide-title","Hide Title",false);
        hideAchievements=bool(hooks,"hide-achievements","Hide Achievements",false);
        hideScoreIndex=bool(hooks,"hide-score-index","Hide Score Index",false);
        removeSpeedEffect=bool(hooks,"remove-speed-effect","Remove Speed FoV",false);
        settings=List.of(limitHudFps,entityBatching,
            SettingGroup.builder(hooks).configure("Optimizations",entityCulling,particleCulling,disableArmorStands,disableParticleLighting).build(),
            SettingGroup.builder(hooks).configure("Distance Limiting",maxEntityDistance,maxTileEntityDistance).build(),
            SettingGroup.builder(hooks).configure("Hide",noHurtCam,hideBossBar,hideTitle,hideAchievements,hideScoreIndex,removeSpeedEffect).build());
    }
    private static BooleanSetting bool(MarkerHooks h,String key,String name,boolean value){return BooleanSetting.builder(key,h).configure(name,value).build();}
    public String displayName(){return "Camera";}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}
    public void setEnabled(boolean value){enabled=value;invalidate();nextOcclusionNanos=0;if(!value){releaseCache();clearOcclusion();}}
    public boolean active(BooleanSetting setting){return enabled&&setting.get();}
    public boolean caching(){return enabled&&limitHudFps.get()&&OpenGlHelper.isFramebufferEnabled();}
    public boolean capturing(){return capturing;}
    public void invalidate(){dirty=true;}
    public void tick(){
        invalidate();
        if(world!=mc.theWorld){world=mc.theWorld;releaseCache();}
        if(!caching())releaseCache();
        if(occlusionWorld!=mc.theWorld||!active(entityCulling))clearOcclusion();
    }
    public int liveFramebuffers(){return cache==null?0:1;}
    public long captures(){return captures;}
    public long reuses(){return reuses;}
    public long occlusionUpdates(){return occlusionUpdates;}
    private void clearOcclusion(){
        if(occlusionWorld!=null){
            for(Entity entity:occlusionWorld.loadedEntityList)entity.moonOccluded=false;
            for(TileEntity tile:occlusionWorld.loadedTileEntityList)tile.moonOccluded=false;
        }
        occlusionWorld=null;nextOcclusionNanos=0;
    }
    /** Called after RenderManager sets the world render origin, before entity drawing. */
    public void updateOcclusion(double originX,double originY,double originZ){
        if(!active(entityCulling)||mc.thePlayer==null||mc.theWorld==null){clearOcclusion();return;}
        if(McpGlowRenderPolicy.cameraBypass())return;
        if(occlusionWorld!=mc.theWorld){clearOcclusion();occlusionWorld=mc.theWorld;}
        long now=System.nanoTime();if(now<nextOcclusionNanos)return;nextOcclusionNanos=now+10_000_000L;
        if(visibility==null)visibility=new VoxelVisibility(128,(x,y,z)->mc.theWorld.getBlockState(new BlockPos(x,y,z)).getBlock().isOpaqueCube());
        visibility.reset();occlusionUpdates++;
        var eye=new VoxelVisibility.Point(originX,originY+mc.thePlayer.getEyeHeight(),originZ);
        boolean spectator=mc.thePlayer.isSpectator();
        for(Entity entity:mc.theWorld.loadedEntityList){
            AxisAlignedBB box=entity.getEntityBoundingBox();
            if(spectator||entity instanceof EntityArmorStand||entity.getDistanceToEntity(mc.thePlayer)>128||oversized(box))entity.moonOccluded=false;
            else entity.moonOccluded=!visibility.visible(box,eye);
        }
        for(TileEntity tile:mc.theWorld.loadedTileEntityList){
            if(spectator||tile.getDistanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>128){tile.moonOccluded=false;continue;}
            var pos=tile.getPos();
            // Original Cz(x,y,z) is a point, not a unit cube or TESR render box.
            var box=new AxisAlignedBB(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
            tile.moonOccluded=!visibility.visible(box,eye);
        }
    }
    private static boolean oversized(AxisAlignedBB box){return box.maxX-box.minX>50||box.maxY-box.minY>50||box.maxZ-box.minZ>50;}
    public boolean skipEntity(Entity entity){
        if(!enabled||mc.thePlayer==null||McpGlowRenderPolicy.cameraBypass())return false;
        return entityCulling.get()&&entity.moonOccluded
            ||mc.thePlayer.getDistanceSqToEntity(entity)>(double)maxEntityDistance.get()*maxEntityDistance.get()
            ||disableArmorStands.get()&&entity instanceof EntityArmorStand;
    }
    public boolean skipTile(TileEntity tile){
        if(!enabled||mc.thePlayer==null||McpGlowRenderPolicy.cameraBypass())return false;
        if(entityCulling.get()&&tile.moonOccluded)return true;
        var p=tile.getPos();
        return p.getX()!=0&&p.getY()!=0&&p.getZ()!=0
            &&tile.getDistanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>(double)maxTileEntityDistance.get()*maxTileEntityDistance.get();
    }
    public boolean particleVisible(Entity particle){
        // j1 first calls kS(AABB), with no module/setting or ignore bypass.
        // Only a successful outer test reaches lc. Its same-box test then
        // succeeds, so the default lc path never reads particleCulling.
        if(mc.getRenderViewEntity()==null)return true;
        Entity view=mc.getRenderViewEntity();particleFrustum.setPosition(view.posX,view.posY,view.posZ);
        return particleFrustum.isBoundingBoxInFrustum(particle.getEntityBoundingBox());
    }
    /** Caller owns native overlays: they remain outside this 20 Hz vanilla cache. */
    public void renderHud(Runnable vanilla,Runnable hotbar,Runnable crosshair,Runnable vignette){
        if(!caching()){releaseCache();vanilla.run();return;}
        var sr=new ScaledResolution(mc);
        int caller=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        try{
            if(cache==null||cache.framebufferWidth!=mc.displayWidth||cache.framebufferHeight!=mc.displayHeight){
                releaseCache();cache=new Framebuffer(mc.displayWidth,mc.displayHeight,true);
                cache.setFramebufferColor(0,0,0,0);dirty=true;
            }
            if(guiWidth!=sr.getScaledWidth()||guiHeight!=sr.getScaledHeight()||world!=mc.theWorld){
                guiWidth=sr.getScaledWidth();guiHeight=sr.getScaledHeight();world=mc.theWorld;dirty=true;
            }
            if(dirty){
                cache.framebufferClear();cache.bindFramebuffer(true);capturing=true;
                try{vanilla.run();dirty=false;captures++;}finally{capturing=false;}
            }else reuses++;
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,caller);
            GlStateManager.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            mc.entityRenderer.setupOverlayRendering();hotbar.run();
            McpLuaRenderer.renderHud(()->{
                GL20.glUseProgram(0);GlStateManager.enableTexture2D();
                // The transparent HUD attachment contains premultiplied RGB.
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1,1,1,1);cache.bindFramebufferTexture();
                var tess=Tessellator.getInstance();var b=tess.getWorldRenderer();
                b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
                b.pos(0,guiHeight,0).tex(0,0).endVertex();b.pos(guiWidth,guiHeight,0).tex(1,0).endVertex();
                b.pos(guiWidth,0,0).tex(1,1).endVertex();b.pos(0,0,0).tex(0,1).endVertex();tess.draw();
            });
            crosshair.run();vignette.run();
        }finally{
            capturing=false;OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,caller);
            GlStateManager.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            GlStateManager.color(1,1,1,1);GlStateManager.enableAlpha();
        }
    }
    private void releaseCache(){
        if(cache==null)return;
        int bound=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),old=cache.framebufferObject;
        cache.deleteFramebuffer();cache=null;dirty=true;
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,bound==old?mc.getFramebuffer().framebufferObject:bound);
    }
    public static boolean compilingBatch(){return compilingBatch;}
    public static void beginModelBatch(){if(compilingBatch)throw new IllegalStateException("Nested model batch");compilingBatch=true;}
    public static void endModelBatch(){compilingBatch=false;}
    @Override public void close(){enabled=false;releaseCache();clearOcclusion();visibility=null;world=null;}

    /** gg + gh: no GL/worker dependency, suitable for a new LWJGL3 host. */
    public static final class VoxelVisibility {
        @FunctionalInterface public interface OpaqueVoxels {boolean opaque(int x,int y,int z);}
        public static final class Point {
            double x,y,z;
            public Point(double x,double y,double z){set(x,y,z);}
            void set(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        }
        private static final double[][] SAMPLE_OFFSETS={
            {.05,.05,.05},{.05,.95,.05},{.05,.95,.95},{.05,.05,.95},
            {.95,.05,.05},{.95,.95,.05},{.95,.95,.95},{.95,.05,.95},
            {.05,.5,.5},{.5,.05,.5},{.5,.5,.05},{.95,.5,.5},{.5,.95,.5},{.5,.5,.95}
        };
        private final int radius,diameter;
        private final OpaqueVoxels voxels;
        private final byte[] cache;
        private final BitSet knownVoxels=new BitSet();
        private final Point[] samples=new Point[15];
        private final boolean[] selected=new boolean[14];
        private final int[] cameraCell=new int[3],lastBlocker=new int[3];
        private boolean hasBlocker;
        private int cacheByte,cacheShift;
        public VoxelVisibility(int radius,OpaqueVoxels voxels){
            this.radius=radius;this.diameter=radius*2;this.voxels=voxels;
            cache=new byte[diameter*diameter*diameter/4];
            for(int i=0;i<samples.length;i++)samples[i]=new Point(0,0,0);
        }
        public void reset(){Arrays.fill(cache,(byte)0);}
        private static int floor(double value){return MathHelper.floor_double(value);}
        private static int side(int min,int max,int camera){return max>camera&&min>camera?1:min<camera&&max<camera?-1:0;}
        public boolean visible(AxisAlignedBB box,Point eye){
            int maxX=floor(box.maxX+.1),maxY=floor(box.maxY+.1),maxZ=floor(box.maxZ+.1);
            int minX=floor(box.minX-.1),minY=floor(box.minY-.1),minZ=floor(box.minZ-.1);
            cameraCell[0]=floor(eye.x);cameraCell[1]=floor(eye.y);cameraCell[2]=floor(eye.z);
            int sideX=side(minX,maxX,cameraCell[0]),sideY=side(minY,maxY,cameraCell[1]),sideZ=side(minZ,maxZ,cameraCell[2]);
            if(sideX==0&&sideY==0&&sideZ==0)return true;
            knownVoxels.clear();int index=0;
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
                int state=cached(x,y,z);if(state==1)return true;if(state!=0)knownVoxels.set(index);index++;
            }
            hasBlocker=false;index=0;
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
                int boundary=(x==minX?1:0)|(x==maxX?2:0)|(y==minY?4:0)|(y==maxY?8:0)|(z==minZ?16:0)|(z==maxZ?32:0);
                int facing=(x==minX&&sideX==1?1:0)|(x==maxX&&sideX==-1?2:0)
                    |(y==minY&&sideY==1?4:0)|(y==maxY&&sideY==-1?8:0)
                    |(z==minZ&&sideZ==1?16:0)|(z==maxZ&&sideZ==-1?32:0);
                if(!knownVoxels.get(index)&&facing!=0&&visibleVoxel(eye,x,y,z,boundary,facing))return true;
                index++;
            }
            return false;
        }
        private boolean visibleVoxel(Point eye,int x,int y,int z,int boundary,int facing){
            Arrays.fill(selected,false);
            if((facing&1)==1){select(0,8);if((boundary&-2)!=0)select(1,4,5);}
            if((facing&4)==4){select(0,9);if((boundary&-5)!=0)select(3,4,7);}
            if((facing&16)==16){select(0,10);if((boundary&-17)!=0)select(1,4,5);}
            if((facing&2)==2){select(4,11);if((boundary&-3)!=0)select(5,6,7);}
            if((facing&8)==8){select(1,12);if((boundary&-9)!=0)select(2,5,6);}
            if((facing&32)==32){select(2,13);if((boundary&-33)!=0)select(3,6,7);}
            int count=0;
            for(int i=0;i<selected.length;i++)if(selected[i]){
                double[] offset=SAMPLE_OFFSETS[i];samples[count++].set(x+offset[0],y+offset[1],z+offset[2]);
            }
            return visibleSamples(eye,count);
        }
        private void select(int... indices){for(int index:indices)selected[index]=true;}
        private boolean intersectsLastBlocker(Point eye,double dx,double dy,double dz){
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            double invX=1/(dx/length),invY=1/(dy/length),invZ=1/(dz/length);
            double x0=(lastBlocker[0]-eye.x)*invX,x1=(lastBlocker[0]+1-eye.x)*invX;
            double y0=(lastBlocker[1]-eye.y)*invY,y1=(lastBlocker[1]+1-eye.y)*invY;
            double z0=(lastBlocker[2]-eye.z)*invZ,z1=(lastBlocker[2]+1-eye.z)*invZ;
            double near=Math.max(Math.max(Math.min(x0,x1),Math.min(y0,y1)),Math.min(z0,z1));
            double far=Math.min(Math.min(Math.max(x0,x1),Math.max(y0,y1)),Math.max(z0,z1));
            return far>0?false:!(near>far);
        }
        private boolean visibleSamples(Point eye,int count){
            int startX=cameraCell[0],startY=cameraCell[1],startZ=cameraCell[2];
            for(int i=0;i<count;i++){
                Point target=samples[i];double dx=eye.x-target.x,dy=eye.y-target.y,dz=eye.z-target.z;
                if(hasBlocker&&intersectsLastBlocker(eye,dx,dy,dz))continue;
                double ax=Math.abs(dx),ay=Math.abs(dy),az=Math.abs(dz);
                double deltaX=1/ax,deltaY=1/ay,deltaZ=1/az;
                int steps=1,stepX,stepY,stepZ;double nextX,nextY,nextZ;
                if(ax==0){stepX=0;nextX=deltaX;}
                else if(target.x>eye.x){stepX=1;steps+=floor(target.x)-startX;nextX=(float)((startX+1-eye.x)*deltaX);}
                else{stepX=-1;steps+=startX-floor(target.x);nextX=(float)((eye.x-startX)*deltaX);}
                if(ay==0){stepY=0;nextY=deltaY;}
                else if(target.y>eye.y){stepY=1;steps+=floor(target.y)-startY;nextY=(float)((startY+1-eye.y)*deltaY);}
                else{stepY=-1;steps+=startY-floor(target.y);nextY=(float)((eye.y-startY)*deltaY);}
                if(az==0){stepZ=0;nextZ=deltaZ;}
                else if(target.z>eye.z){stepZ=1;steps+=floor(target.z)-startZ;nextZ=(float)((startZ+1-eye.z)*deltaZ);}
                else{stepZ=-1;steps+=startZ-floor(target.z);nextZ=(float)((eye.z-startZ)*deltaZ);}
                if(walk(startX,startY,startZ,deltaX,deltaY,deltaZ,steps,stepX,stepY,stepZ,nextY,nextX,nextZ)){
                    mark(samples[0],true);return true;
                }
                hasBlocker=true;
            }
            mark(samples[0],false);return false;
        }
        private boolean walk(int x,int y,int z,double deltaX,double deltaY,double deltaZ,int steps,
                             int stepX,int stepY,int stepZ,double nextY,double nextX,double nextZ){
            while(steps>1){
                int state=cached(x,y,z);
                if(state==2){blocker(x,y,z);return false;}
                if(state==0){
                    if(voxels.opaque(x,y,z)){cache[cacheByte]|=(byte)(1<<(cacheShift+1));blocker(x,y,z);return false;}
                    cache[cacheByte]|=(byte)(1<<cacheShift);
                }
                // Strict ties choose Z, including equal X/Y before a later Z crossing.
                if(nextY<nextX&&nextY<nextZ){y+=stepY;nextY+=deltaY;}
                else if(nextX<nextY&&nextX<nextZ){x+=stepX;nextX+=deltaX;}
                else{z+=stepZ;nextZ+=deltaZ;}
                steps--;
            }
            return true;
        }
        private void blocker(int x,int y,int z){lastBlocker[0]=x;lastBlocker[1]=y;lastBlocker[2]=z;}
        private int cached(int x,int y,int z){
            x-=cameraCell[0];y-=cameraCell[1];z-=cameraCell[2];
            if(Math.abs(x)>radius-2||Math.abs(y)>radius-2||Math.abs(z)>radius-2)return -1;
            int index=x+radius+(y+radius)*diameter+(z+radius)*diameter*diameter;
            cacheByte=index/4;cacheShift=index%4*2;return cache[cacheByte]>>cacheShift&3;
        }
        private void mark(Point point,boolean visible){
            int x=floor(point.x)-cameraCell[0]+radius,y=floor(point.y)-cameraCell[1]+radius,z=floor(point.z)-cameraCell[2]+radius;
            int index=x+y*diameter+z*diameter*diameter;cache[index/4]|=(byte)(1<<(index%4*2+(visible?0:1)));
        }
    }
}
