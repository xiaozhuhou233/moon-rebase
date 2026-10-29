package moon.module.visual;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import moon.module.visual.glow.GlowSettings;
import moon.visual.gui.*;
import moon.visual.render.DrawServices;
import moon.visual.render.WorldDispatcher;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** jN: one ESP module owns 2D, model outline/glow and the fE 3D box.
 * The recovered hF topology and camera capture stay local to this owner. */
public final class EspModule implements ModuleRowWidget.Module,AutoCloseable {
    private static final float[][] SHAPE={
        {0,2,4},{0,3,4},{1,2,4},{1,3,4},{1,2,5},{1,3,5},{0,2,5},{0,3,5},
        {1,3,4},{1,2,4},{0,3,4},{0,2,4},{0,3,5},{0,2,5},{1,3,5},{1,2,5},
        {0,3,4},{1,3,4},{1,3,5},{0,3,5},{0,3,4},{0,3,5},{1,3,5},{1,3,4},
        {0,2,4},{1,2,4},{1,2,5},{0,2,5},{0,2,4},{0,2,5},{1,2,5},{1,2,4},
        {0,2,4},{0,3,4},{0,2,5},{0,3,5},{1,2,5},{1,3,5},{1,2,4},{1,3,4},
        {0,3,5},{0,2,5},{0,3,4},{0,2,4},{1,3,4},{1,2,4},{1,3,5},{1,2,5}
    };
    public final BooleanSetting box,outline,markTarget;
    
    public final SettingGroup boxGroup,boxColors;
    public final GlowSettings glow;
    private final Consumer<Boolean> enableEffects;
    public final FloatSetting widthExpansion,heightExpansion,opacity;
    public final ColorSetting normalColor,targetColor;
    private final Minecraft mc;
    private final Predicate<Entity> filter;
    private final List<SettingValue<?>> settings;
    private final Frustum frustum=new Frustum();
    private final FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16);
    private final FloatBuffer vertexData=BufferUtils.createFloatBuffer(48*7);
    private final WorldDispatcher worldDispatcher;
    private boolean enabled,closed,cameraCaptured;
    private Object capturedWorld;
    private double originX,originY,originZ;
    private int lastVertices,drawCount;

    public EspModule(Minecraft mc,DrawServices drawing,GlowSettings targets,MarkerHooks hooks,Consumer<Boolean> enableEffects,Predicate<Entity> filter)throws IOException {
        this.mc=mc;this.glow=targets;this.enableEffects=Objects.requireNonNull(enableEffects);this.filter=Objects.requireNonNull(filter);
        box=BooleanSetting.builder("box",hooks).configure("Box",false).build();
        outline=BooleanSetting.builder("box-outline",hooks).configure("Outline",true).build();
        opacity=FloatSetting.builder("box-opacity",hooks).configure("Opacity",.3F,.01F,1F,.01F).build();
        widthExpansion=FloatSetting.builder("box-width-expansion",hooks).configure("Width expansion",0F,0F,1F,.01F).build();
        heightExpansion=FloatSetting.builder("box-height-expansion",hooks).configure("Height expansion",0F,0F,1F,.01F).build();
        markTarget=BooleanSetting.builder("box-mark-target",hooks).configure("Mark Target",true).build();
        normalColor=ColorSetting.builder("box-normal-color",hooks).configure("Normal color",0xff0000ff).build();
        targetColor=ColorSetting.builder("box-target-color",hooks).configure("Target color",0xffff0000).visibility(markTarget::get).build();
        boxGroup=SettingGroup.builder(hooks).configure("Box Settings",markTarget,outline,opacity,widthExpansion,heightExpansion).visibility(box::get).build();
        boxColors=SettingGroup.builder(hooks).configure("Color Settings",normalColor,targetColor).visibility(box::get).build();
        List<SettingValue<?>> roots=new ArrayList<>(glow.roots());
        roots.add(roots.indexOf(glow.colors),box);roots.add(boxGroup);roots.add(boxColors);
        settings=List.copyOf(roots);
        worldDispatcher=new WorldDispatcher(drawing);worldDispatcher.initialize();
    }
    public String displayName(){return "ESP";}public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}public void setEnabled(boolean value){enabled=value&&!closed;enableEffects.accept(enabled);if(!enabled){cameraCaptured=false;capturedWorld=null;lastVertices=0;}}
    public List<? extends SettingValue<?>> settings(){return settings;}
    public int lastVertices(){return lastVertices;}public int drawCount(){return drawCount;}

    /** RenderManager calls this immediately after camera matrices and world origin are set. */
    public void captureCamera(double x,double y,double z){
        if(!enabled||!box.get()||closed)return;
        originX=x;originY=y;originZ=z;capturedWorld=mc.theWorld;cameraCaptured=capturedWorld!=null;
        if(!cameraCaptured)return;
        model.clear();projection.clear();GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,projection);
    }
    /** Draw retained world-space quads after terrain/entities and before HUD projection. */
    public void render(float partialTicks){
        lastVertices=0;
        if(!enabled||closed||!cameraCaptured||capturedWorld!=mc.theWorld||mc.thePlayer==null)return;
        cameraCaptured=false;
        if(!box.get())return;
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float oldLine=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        var renderer=worldDispatcher;
        var array=renderer.getArray();var buffer=renderer.getBuffer();var program=renderer.getProgram();
        try {
            buffer.bind(GL15.GL_ARRAY_BUFFER);array.bind();program.use();
            FloatBuffer m=copy(model),p=copy(projection);
            renderer.getModelviewUniform().setMatrix4(false,m);renderer.getProjectionUniform().setMatrix4(false,p);
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.disableDepth();GlStateManager.depthMask(false);GlStateManager.disableCull();GlStateManager.disableAlpha();
            frustum.setPosition(originX,originY,originZ);lastVertices=0;
            for(Entity entity:mc.theWorld.loadedEntityList){
                if(!accepts(entity)||!(frustum.isBoundingBoxInFrustum(entity.getEntityBoundingBox())||entity.ignoreFrustumCheck))continue;
                { // fE always fills when the 3D Box feature is enabled.
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices(entity,partialTicks,false),GL15.GL_DYNAMIC_DRAW);
                    GL11.glDrawArrays(GL11.GL_QUADS,0,48);drawCount++;
                }
                if(outline.get()){
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices(entity,partialTicks,true),GL15.GL_DYNAMIC_DRAW);
                    GL11.glLineWidth(.5F);GL11.glDrawArrays(GL11.GL_LINES,0,48);drawCount++;
                }
            }
        } finally {
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            GlStateManager.depthMask(depthWrite);
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();GL11.glLineWidth(oldLine);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);GL30.glBindVertexArray(oldArray);GL20.glUseProgram(oldProgram);
        }
    }
    private FloatBuffer vertices(Entity entity,float partial,boolean edge){
        double x=entity.prevPosX+(entity.posX-entity.prevPosX)*partial-originX;
        double y=entity.prevPosY+(entity.posY-entity.prevPosY)*partial-originY;
        double z=entity.prevPosZ+(entity.posZ-entity.prevPosZ)*partial-originZ;
        double w=entity.width,h=entity.height+.1F,wx=widthExpansion.get(),hy=heightExpansion.get();
        // hF uses pairs xMin/xMax, yMin/yMax, zMin/zMax, indexed directly by SHAPE.
        double[] corner={x-w+.25-wx,x+w-.25+wx,y-hy,y+h+hy,z-w+.25-wx,z+w-.25+wx};
        // fE 0x243..0x279 tests the enabled KillAura target directly; box-mark-target only controls color-setting visibility.
        boolean target=mc.thePlayer!=null&&McpTarget.isCurrent(mc,entity);
        int color=(target?targetColor.get():normalColor.get());
        int alpha=edge?255:(int)(opacity.get()*255F);color=color&0x00ffffff|alpha<<24;
        FloatBuffer result=vertexData.clear();
        for(float[] point:SHAPE){result.put((float)corner[(int)point[0]]).put((float)corner[(int)point[1]]).put((float)corner[(int)point[2]]);
            result.put(((color>>16)&255)/255F).put(((color>>8)&255)/255F).put((color&255)/255F).put(((color>>>24)&255)/255F);}
        result.flip();lastVertices+=48;return result;
    }
    /** fE inherits fD: use the very same target predicate as fF and fI.
     * Its local-player/third-person branch deliberately precedes target, ID and invisibility checks. */
    public boolean accepts(Entity entity){return filter.test(entity);}
    // glGetFloatv writes by address and does not advance the Java buffer position.
    private static FloatBuffer copy(FloatBuffer source){return source.asReadOnlyBuffer();}
    public void close(){if(!closed){setEnabled(false);closed=true;cameraCaptured=false;capturedWorld=null;}}

    private static final class McpTarget {
        static boolean isCurrent(Minecraft mc,Entity entity){
            var services=moon.client.McpModuleServices.current();if(services==null||!services.killAura.enabled())return false;
            var target=services.killAura.target();return target!=null&&mc.theWorld.getEntityByID(target.id())==entity;
        }
    }
}
