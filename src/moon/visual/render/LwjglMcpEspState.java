package moon.visual.render;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;

/** Destination cache-aware state bridge. Raw k3 color and fI depth calls
 * intentionally bypass GlStateManager; resetColor invalidates its cache only.
 * Gui.drawRect is the destination implementation, not decrypted ga constants. */
public final class LwjglMcpEspState implements EntityEspRenderer.State,LegacyColorRectangle.Host {
    private final IntSupplier marker;private final LegacyColorRectangle rectangle;
    public LwjglMcpEspState(IntSupplier rectangleMarker,LegacyColorRectangle.Constants constants){
        marker=rectangleMarker;rectangle=new LegacyColorRectangle(this,constants);
    }
    public int rectangleMarker(){return marker.getAsInt();}
    public void zeroAreaGuiRectangle(){Gui.drawRect(0,0,0,0,0);}
    public void disableDepth(){GlStateManager.disableDepth();}
    public void enableRescaleNormal(){GlStateManager.enableRescaleNormal();}
    public void disableRescaleNormal(){GlStateManager.disableRescaleNormal();}
    public void disableAlpha(){GlStateManager.disableAlpha();}public void enableAlpha(){GlStateManager.enableAlpha();}
    public void alphaFunc(int f,float r){GlStateManager.alphaFunc(f,r);}
    public void enableBlend(){GlStateManager.enableBlend();}public void disableBlend(){GlStateManager.disableBlend();}
    public void blendFuncSeparate(int sr,int dr,int sa,int da){GlStateManager.tryBlendFuncSeparate(sr,dr,sa,da);}
    public void blendFunc(int s,int d){GlStateManager.blendFunc(s,d);}
    public void color(float r,float g,float b,float a){GlStateManager.color(r,g,b,a);}
    public void scale(float x,float y,float z){GlStateManager.scale(x,y,z);}
    public void bindTexture(int texture){GlStateManager.bindTexture(texture);}
    public void colorMask(boolean r,boolean g,boolean b,boolean a){GlStateManager.colorMask(r,g,b,a);}
    public void rawDepthFunc(int f){GL11C.glDepthFunc(f);}
    public void itemColorRectangle(float x,float y,float w,float h,int color){rectangle.draw(x,y,w,h,color);}
    public void enableTexture2D(){GlStateManager.enableTexture2D();}public void disableTexture2D(){GlStateManager.disableTexture2D();}
    public void rawColor(float r,float g,float b,float a){GL11.glColor4f(r,g,b,a);}
    public void begin(int primitive){GL11.glBegin(primitive);}public void vertex(float x,float y){GL11.glVertex2f(x,y);}
    public void end(){GL11.glEnd();}public void resetColorCache(){GlStateManager.resetColor();}
}
