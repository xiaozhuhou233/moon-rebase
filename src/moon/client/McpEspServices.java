package moon.client;
import moon.module.visual.glow.EspSettings;

import java.util.*;
import moon.visual.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.*;
import org.lwjglx.util.vector.Vector4f;

/** Live fI/fO/fP/fN/fQ/fS/fR hosts with the restored gs/k1 projection producer.
 * Original friend registries remain an explicit destination boundary. */
public final class McpEspServices implements AutoCloseable,EntityEspRenderer.Host<Entity,EntityEspTextComponents.Item>,
        EntityEspBarComponents.Host<Entity>,EntityEspTextComponents.Host<Entity>,BitmapFontFamily.FontHooks {
    private final McpVisualServices visual;private final McpGlowServices glow;private final Minecraft mc;
    private final McpEntityProjection projector=new McpEntityProjection();private Frustum frustum=new Frustum();
    private Object world;
    private final Map<Entity,Vector4f> projected=new IdentityHashMap<>();
    private List<Entity> frame=List.of();private boolean pending,prepared,closed;
    private final LwjglBitmapFontHost textures;
    private final BitmapFontFamily bitmap;
    final EntityEspRenderer<Entity,EntityEspTextComponents.Item> renderer;
    public int updateCount,renderCount,itemDraws,nameSuppressions;
    public McpEspServices(McpVisualServices visual,McpGlowServices glow){
        this.visual=visual;this.glow=glow;mc=visual.mc;
        textures=new LwjglBitmapFontHost(visual.resources,visual.state);
        try{
            bitmap=new BitmapFontFamily("skeet",8,BitmapFontBuilder.factory(new BitmapAtlasLoader(visual.resources),textures,visual.hooks),
                ()->Math.min(5,new ScaledResolution(mc).getScaleFactor()),this);
            EspSettings s=glow.settings.esp;
            renderer=new EntityEspRenderer<>(this,s,new EntityEspTaskFactory(visual.draw,visual.rectangle,visual.gradient,visual.msdf,visual.coloredTexture),
                new LwjglMcpEspState(()->0,visual.profile.espItemRectangle),visual.profile.esp,glow.mask.predicate(),
                new EntityEspBoxComponent<>(s,this,visual.profile.espBox),new EntityEspBarComponents.Health<>(s,this,visual.profile.espBars),
                new EntityEspBarComponents.Armor<>(s,this,visual.profile.espBars),new EntityEspTextComponents.Status<>(s,this,visual.profile.espLabels),
                new EntityEspTextComponents.Name<>(s,this),new EntityEspTextComponents.HeldItem<>(s,this));
        }catch(RuntimeException|Error failure){textures.close();throw failure;}
    }
    /** Same fixed camera buffers as original kH/ActiveRenderInfo. Snapshot the
     * frustum before hand rendering changes GL matrices. */
    public void captureCamera(FloatBuffer model,FloatBuffer projection,IntBuffer viewport){
        projector.capture(model,projection,viewport);
        ClippingHelper source=ClippingHelperImpl.getInstance(),copy=new ClippingHelper();
        for(int i=0;i<6;i++)System.arraycopy(source.frustum[i],0,copy.frustum[i],0,4);
        copy.disabled=source.disabled;frustum=new Frustum(copy);
    }
    /** gr invokes gs at world PRE and POST. Selection deliberately precedes
     * projection availability; fD consumers require the stored projection. */
    public void capture(float ticks){
        pending=false;prepared=false;
        if(!glow.enabled||mc.theWorld==null||mc.thePlayer==null){clear();return;}
        if(world!=mc.theWorld){clear();world=mc.theWorld;}
        Entity view=mc.getRenderViewEntity();if(view==null){clear();return;}
        frustum.setPosition(view.posX,view.posY,view.posZ);
        frame=List.copyOf(mc.theWorld.loadedEntityList);
        Set<Entity> live=Collections.newSetFromMap(new IdentityHashMap<>());live.addAll(frame);
        projected.keySet().removeIf(entity->!live.contains(entity));
        for(Entity entity:frame){
            if(!glow.selectedForProjection(entity))continue;
            // gs checks its expanded box directly; ignoreFrustumCheck applies
            // later in fD consumers, and does not bypass this producer gate.
            if(!frustum.isBoundingBoxInFrustum(McpEntityProjection.entityBounds(entity,ticks)))continue;
            Vector4f bounds=projector.project(entity,ticks,mc);
            if(bounds==null)projected.remove(entity);else projected.put(entity,bounds);
        }
        pending=true;
    }
    public void prepareOverlay(){
        prepared=false;if(closed||!pending||!glow.enabled||!renderer.enabled()||mc.theWorld==null)return;pending=false;
        renderer.update();updateCount++;prepared=true;
    }
    public void overlayPre(){
        if(!prepared)return;prepared=false;
        visual.main.bind(true);mc.entityRenderer.setupOverlayRendering();GL20.glUseProgram(0);
        GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,1);
        GlStateManager.pushMatrix();
        try{renderer.render();renderCount++;}
        finally{
            GlStateManager.popMatrix();
            // Original fI has no failure cleanup. The destination boundary restores
            // vanilla overlay state without changing its internal call sequence.
            GL20.glUseProgram(0);visual.state.activeTexture(OpenGlHelper.defaultTexUnit);GlStateManager.colorMask(true,true,true,true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableRescaleNormal();RenderHelper.disableStandardItemLighting();GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,1);GlStateManager.enableDepth();
        }
    }
    public void clear(){projected.clear();frame=List.of();world=null;pending=false;prepared=false;}
    /** jN.a(dg): only replace vanilla tags for a selected projected entity while
     * both 2D and its name component are enabled. Capture suppression is separate. */
    public boolean hidesVanillaName(Entity entity){
        boolean hide=glow.enabled&&glow.settings.esp.twoD.get()&&glow.settings.esp.names.get()&&projection(entity)!=null
            &&glow.mask.predicate().test(entity)&&inFrustum(entity);
        if(hide)nameSuppressions++;return hide;
    }
    public int liveTextures(){return textures.liveTextures();}
    public int updateMarker(){return 1;}public int renderMarker(){return 0;}public void setRenderMarker(int value){}
    public int componentMarker(){return 1;}public int inverseComponentMarker(){return 0;}public void setInverseComponentMarker(int value){}
    public Object getMarker(){return null;}public void setMarker(String value){}
    public String[] getFontSentinel(){return null;}public void setFontSentinel(String[] value){}
    public Object clampMarker(){return this;}
    public MsdfFontBase verdana(){return visual.font("Verdana");}public BitmapFontFamily bitmapFont(){return bitmap;}
    public Iterable<Entity> entities(){return frame;}public Vector4f projection(Entity entity){return projected.get(entity);}
    public boolean inFrustum(Entity entity){return entity.ignoreFrustumCheck||frustum.isBoundingBoxInFrustum(entity.getEntityBoundingBox());}
    public boolean isLiving(Entity entity){return entity instanceof EntityLivingBase;}public boolean isPlayer(Entity entity){return entity instanceof EntityPlayer;}
    public Entity requireLiving(Entity entity){return (EntityLivingBase)entity;}public Entity requirePlayer(Entity entity){return (EntityPlayer)entity;}
    public float health(Entity entity){return ((EntityLivingBase)entity).getHealth();}public float maxHealth(Entity entity){return ((EntityLivingBase)entity).getMaxHealth();}
    public int armor(Entity entity){return ((EntityLivingBase)entity).getTotalArmorValue();}
    public boolean blocking(Entity entity){return ((EntityPlayer)entity).isBlocking();}public boolean sneaking(Entity entity){return entity.isSneaking();}
    public String rawName(Entity entity){return entity.getName();}public String formattedName(Entity entity){return entity.getDisplayName().getFormattedText();}
    // No friend or combat target registry exists in this base. Keep these explicit
    // for a future base integration; do not manufacture friends or target identities.
    public EntityEspTextComponents.Friend friend(Entity entity){return null;}public Object target(Entity entity){return null;}
    public EntityEspTextComponents.Item heldItem(Entity entity){ItemStack stack=((EntityPlayer)entity).getHeldItem();return stack==null?null:new StackItem(stack);}
    private record StackItem(ItemStack stack) implements EntityEspTextComponents.Item {public String displayName(){return stack.getDisplayName();}}
    public void renderItem(EntityEspTextComponents.Item item,float x,float y){
        // fI borrows the main framebuffer alpha as its item mask. Vanilla in this
        // destination leaves opaque world alpha outside the sprite. Initialize
        // only the caller's 50x50 alpha scratch area; RGB (including the shadow
        // from the preceding itemMask call) is preserved by fI's colorMask.
        GL20.glUseProgram(0);GlStateManager.disableAlpha();GlStateManager.disableBlend();GlStateManager.disableTexture2D();
        GlStateManager.color(0,0,0,0);GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x-25,y+25);GL11.glVertex2f(x+25,y+25);GL11.glVertex2f(x+25,y-25);GL11.glVertex2f(x-25,y-25);GL11.glEnd();
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,1);
        // Vanilla glint assumes a populated depth mask. fI deliberately disables
        // depth testing, so its flat glint quad would overwrite the sprite alpha.
        // Preserve the actual enchanted stack/model but capture only its base alpha.
        boolean glint=McpGlowRenderPolicy.glint;McpGlowRenderPolicy.glint=false;
        itemDraws++;GlStateManager.pushMatrix();try{GlStateManager.translate(x,y,0);mc.getRenderItem().renderItemAndEffectIntoGUI(((StackItem)item).stack(),0,0);}
        finally{GlStateManager.popMatrix();McpGlowRenderPolicy.glint=glint;}
    }
    public void bindMainFramebuffer(boolean viewport){visual.main.bind(viewport);}
    public void bindBlockAtlas(){mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);}
    public void blockAtlasBlurMipmap(boolean blur,boolean mipmap){mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(blur,mipmap);}
    public void restoreBlockAtlasBlurMipmap(){mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture).restoreLastBlurMipmap();}
    public void setupOverlay(){mc.entityRenderer.setupOverlayRendering();}public void enableGuiItemLighting(){RenderHelper.enableGUIStandardItemLighting();}
    public void disableItemLighting(){RenderHelper.disableStandardItemLighting();}
    public void close(){if(closed)return;closed=true;try{renderer.close();}finally{textures.close();clear();}}
}
