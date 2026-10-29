package moon.visual.render;

import net.minecraft.client.renderer.GlStateManager;

/** Actual Moon-rebase MCP state-cache adapter; compiled against the supplied base's existing classes. */
public final class LwjglMcpStateAccess implements LwjglPostProcessBackend.StateAccess {
    public void enableDepth(){GlStateManager.enableDepth();}public void enableBlend(){GlStateManager.enableBlend();}
    public void enableAlpha(){GlStateManager.enableAlpha();}public void enableTexture2D(){GlStateManager.enableTexture2D();}
    public void disableTexture2D(){GlStateManager.disableTexture2D();}
    public void alphaFunc(int f,float r){GlStateManager.alphaFunc(f,r);}
    public void blendFunc(int s,int d){GlStateManager.blendFunc(s,d);}
    public void blendFuncSeparate(int sr,int dr,int sa,int da){GlStateManager.tryBlendFuncSeparate(sr,dr,sa,da);}
    public void activeTexture(int t){GlStateManager.setActiveTexture(t);}public void bindTexture(int t){GlStateManager.bindTexture(t);}
    public void clearColor(float r,float g,float b,float a){GlStateManager.clearColor(r,g,b,a);}
    public void viewport(int w,int h){GlStateManager.viewport(0,0,w,h);}
    public void clearDepth(double d){GlStateManager.clearDepth(d);}public void clear(int mask){GlStateManager.clear(mask);}
    public void deleteTexture(int t){GlStateManager.deleteTexture(t);}
    public void colorMask(boolean r,boolean g,boolean b,boolean a){GlStateManager.colorMask(r,g,b,a);}
    public void color(float r,float g,float b,float a){GlStateManager.color(r,g,b,a);}
}
