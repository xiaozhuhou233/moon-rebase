package moon.visual.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

/**
 * com.moon.fy: abstract framebuffer pass, complete small-class reconstruction.
 * moom.fL -> Minecraft; moom.pz -> Framebuffer, checked by field/constructor
 * structure against the supplied Moon-rebase. No ownership or resize behavior
 * is invented: the original stores both references without copying them.
 * Subclasses fz/fA remain partially recovered and are not implemented here.
 */
public abstract class FramebufferRenderPass {
    protected static final Minecraft client = Minecraft.getMinecraft();
    protected final Framebuffer input;
    protected final Framebuffer[] pyramid;

    public FramebufferRenderPass(Framebuffer input, Framebuffer[] pyramid) {
        this.input = input;
        this.pyramid = pyramid;
    }

    public abstract void render();
}
