package moon.client;
import moon.module.visual.glow.GlowSettings;

import java.awt.Color;
import java.io.IOException;
import java.util.*;
import moon.visual.render.*;
import moon.visual.render.GlowEntityFilter.Type;
import moon.visual.render.PostProcessBackend.Framebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.src.Config;
import net.optifine.shaders.Shaders;
import org.lwjgl.opengl.*;
import org.lwjglx.opengl.Display;

/** Actual world POST model capture -> fK -> optional fJ -> HUD PRE fG.
 * Constants not proved by the original capture are explicit profile choices.
 * Current cadence is one update per captured world frame on the render thread;
 * original jN worker/throttling and independent fH/fI are not claimed here. */
public final class McpGlowServices implements AutoCloseable {
    final McpVisualServices visual;
    final Minecraft mc;
    public final GlowSettings settings;
    final Set<LwjglSeedFramebuffer> masks=Collections.newSetFromMap(new IdentityHashMap<>());
    final Object normalPostMarker=new Object();
    LwjglGlowBackend backend;
    GlowEntityMaskPass<Entity> mask;
    GlowOutlinePass outline;
    GlowBlurPass blur;
    GlowCompositePass composite;
    EntityOutlinePass entityOutline;
    McpEspServices esp;
    private final GlowMaskColorEnvironment colors;
    private final Frustum frustum=new Frustum();
    public boolean enabled;
    private boolean captured,closed;
    private float partialTicks;
    public int captureCount,compositeCount,modelDraws;
    private final GlowEntityMaskPass.Renderer<Entity> renderer=new GlowEntityMaskPass.Renderer<>(){
        public void flagA(boolean v){McpGlowRenderPolicy.captureColor=v;}
        public void flagB(boolean v){McpGlowRenderPolicy.glint=v;}
        public void flagC(boolean v){McpGlowRenderPolicy.shadowFire=v;}
        public void flagD(boolean v){McpGlowRenderPolicy.names=v;}
        public void flagE(boolean v){McpGlowRenderPolicy.cameraBypass=v;}
        public boolean render(Entity entity,float ticks){modelDraws++;return mc.getRenderManager().renderEntityStatic(entity,ticks,true);}
    };
    public McpGlowServices(McpVisualServices visual)throws IOException{
        this.visual=visual;mc=visual.mc;settings=new GlowSettings(visual.hooks,visual.profile);
        settings.synchronizeShadow(visual.effectsSettings.shadowIterations::get,()->visual.effectsSettings.shadowOffset.get().floatValue(),()->visual.effectsEnabled&&visual.effectsSettings.shadowEnabled.get());
        try{
        colors=new GlowMaskColorEnvironment(new LwjglGlowMaskColorBackend(visual.state,OpenGlHelper::setLightmapTextureCoords,
            new LwjglGlowMaskColorBackend.Shaders(){
                public boolean enabled(){return Config.isShaders();}
                public void color(float r,float g,float b,float a){Shaders.setEntityColor(r,g,b,a);}
            }),visual.profile.glowColorEnvironment);
        MaskHost host=new MaskHost();
        mask=new GlowEntityMaskPass<>(host,settings,visual.profile.glowMaskFilter,new GlowEntityFilter<>(host,visual.profile.glowExcludedCodes));
        backend=new LwjglGlowBackend(visual.draw,visual.backend,visual.state,()->mc.displayWidth,()->mc.displayHeight,()->normalPostMarker,7);
        outline=new GlowOutlinePass(backend,visual.profile.glowOutline,settings.radius::get,settings.alphaCurve::getLookup);
        blur=new GlowBlurPass(backend,visual.profile.glowBlur,settings);
        LwjglGlowCompositeBackend compositeBackend=new LwjglGlowCompositeBackend(new LwjglGlowCompositeBackend.GameAccess(){
            public Object marker(){return normalPostMarker;}public void setExternalMarker(String value){visual.hooks.setMarker(value);}
            public GlowCompositePass.Resolution scaledResolution(){return new GlowCompositePass.Resolution(){
                public double width(){return visual.scaledWidth();}public double height(){return visual.scaledHeight();}
            };}
            public Framebuffer mainFramebuffer(){return visual.main;}public int clientFps(){return Minecraft.getDebugFPS();}
            public int desktopFrequency(){return Display.getDisplayMode().getFrequency();}
        },visual.state,new LwjglMcpGlowBatchFactory(new FlowingGlow(visual.draw,settings.shadowColor::colors)));
        composite=new GlowCompositePass(compositeBackend,settings,visual.profile.glowComposite,mask::output,outline,blur);
        entityOutline=new EntityOutlinePass(new LwjglEntityOutlineBackend(backend,()->null,value->{},()->null),compositeBackend,
            settings.esp.outlineConfig(),visual.profile.entityOutline,Object::new,mask::output);
        esp=new McpEspServices(visual,this);
        }catch(IOException|RuntimeException|Error failure){
            try{close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }
    /** The mask owns the shared fD/jN filter; the producer bypasses its projection gate. */
    boolean selectedForProjection(Entity entity){return ((GlowEntityFilter<Entity>)mask.predicate()).selectedForProjection(entity);}
    /** Hook after endRender, outside the first-person-hand conditional. */
    public void worldPost(float ticks){
        captured=false;if(!enabled||mc.theWorld==null||mc.thePlayer==null){esp.clear();return;}
        esp.capture(ticks);visual.esp.render(ticks);if(!mask.enabled())return;
        partialTicks=ticks;mc.entityRenderer.enableLightmap();GL20.glUseProgram(0);
        // Establish vanilla lightmap combine operands before original kR's partial override.
        visual.state.activeTexture(OpenGlHelper.lightmapTexUnit);visual.state.enableTexture2D();
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE1_RGB,GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE2_RGB,GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_OPERAND0_RGB,GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_OPERAND1_RGB,GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_OPERAND2_RGB,GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_COMBINE_ALPHA,GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE0_ALPHA,GL13.GL_PREVIOUS);
        visual.state.activeTexture(OpenGlHelper.defaultTexUnit);
        mask.afterRender();captureCount++;captured=true;
    }
    public void overlayPre(float ticks){
        if(!enabled||mc.theWorld==null||mc.thePlayer==null){esp.clear();return;}
        boolean useMask=captured;captured=false;
        GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.disableBlend();
        if(useMask&&composite.enabled())composite.update();
        if(useMask&&entityOutline.enabled())entityOutline.update();
        esp.prepareOverlay();visual.main.bind(true);mc.entityRenderer.setupOverlayRendering();
        GlStateManager.color(1,1,1,1);GL20.glUseProgram(0);
        if(useMask&&composite.enabled()){composite.render();compositeCount++;}
        if(useMask&&entityOutline.enabled())entityOutline.render();
        esp.overlayPre();
        GlStateManager.enableDepth();
    }
    public int liveFramebuffers(){return masks.size()+(backend==null?0:backend.liveFramebuffers());}
    public Framebuffer maskFramebuffer(){return mask.output();}public Framebuffer outlineFramebuffer(){return outline.output();}
    public Framebuffer blurFramebuffer(){return blur.output();}
    public int liveAdditionalTextures(){return esp==null?0:esp.liveTextures();}
    public void resized(){captured=false;if(esp!=null)esp.clear();}
    public void close(){
        if(closed)return;closed=true;
        try{if(esp!=null)esp.close();}finally{try{if(entityOutline!=null)entityOutline.close();}finally{
        try{if(blur!=null)blur.close();}finally{try{if(outline!=null)outline.close();}finally{
            try{if(mask!=null)mask.close();}finally{try{if(backend!=null)backend.close();}finally{McpGlowRenderPolicy.reset();}}
        }}}}
    }
    private final class MaskHost implements GlowEntityMaskPass.Host<Entity>,GlowEntityFilter.Host<Entity>{
        public Object marker(){return null;}public Object externalMarker(){return null;}public void setMarker(String value){visual.hooks.setMarker(value);}
        public Framebuffer createFramebuffer(int w,int h,boolean depth){LwjglSeedFramebuffer value=new LwjglSeedFramebuffer(w,h,depth,visual.state);masks.add(value);return value;}
        public void releaseFramebuffer(Framebuffer value){if(!masks.remove(value))throw new IllegalArgumentException("Foreign mask framebuffer");((LwjglSeedFramebuffer)value).close();}
        public int displayWidth(){return mc.displayWidth;}public int displayHeight(){return mc.displayHeight;}public float partialTicks(){return partialTicks;}
        public void enableBlend(){GlStateManager.enableBlend();}public void disableDepth(){GlStateManager.disableDepth();}public void enableDepth(){GlStateManager.enableDepth();}
        public void setupCamera(float ticks,int pass){mc.entityRenderer.setupCameraTransform(ticks,pass);}
        public void lightmap(float x,float y){OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,x,y);}
        public boolean entityShadows(){return mc.gameSettings.entityShadows;}public void entityShadows(boolean value){mc.gameSettings.entityShadows=value;}
        public GlowEntityMaskPass.Renderer<Entity> renderer(){return renderer;}
        public Iterable<Entity> loadedEntities(){return mc.theWorld.loadedEntityList;}
        public boolean inFrustum(Entity entity){Entity view=mc.getRenderViewEntity();frustum.setPosition(view.posX,view.posY,view.posZ);return frustum.isBoundingBoxInFrustum(entity.getEntityBoundingBox())||entity.ignoreFrustumCheck;}
        public int hurtTime(Entity entity){return entity instanceof EntityLivingBase living?living.hurtTime:0;}
        public boolean is(Entity entity,Type type){return switch(type){
            case LIVING->entity instanceof EntityLivingBase;case MOB->entity instanceof EntityMob;
            case ANIMAL_A->entity instanceof EntityAnimal;case ANIMAL_B->entity instanceof EntityVillager;
            case ANIMAL_C->entity instanceof EntitySquid;case PLAYER->entity instanceof EntityPlayer;
        };}
        public void resetColorEnvironment(){colors.reset();}public void colorEnvironment(Color color){colors.apply(color);}
        public void disableItemLighting(){RenderHelper.disableStandardItemLighting();}public void setupOverlay(){mc.entityRenderer.disableLightmap();mc.entityRenderer.setupOverlayRendering();}
        public Framebuffer mainFramebuffer(){return visual.main;}
        public GlowEntityFilter.Targets targets(){return target->settings.targets.isSelected(GlowSettings.Target.valueOf(target.name()));}
        public boolean isLocalPlayer(Entity entity){return entity==mc.thePlayer;}public int thirdPersonView(){return mc.gameSettings.thirdPersonView;}
        public Object projection(Entity entity){return esp.projection(entity);}
        public int entityCode(Entity entity){return entity.getEntityId();}public boolean invisible(Entity entity){return entity.isInvisible();}
    }
}
