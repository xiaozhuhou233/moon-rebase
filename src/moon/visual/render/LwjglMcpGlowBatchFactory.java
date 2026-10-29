package moon.visual.render;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/** Destination Moon-rebase's actual POSITION_TEX batch API. Constructing the
 * batch writes only CPU vertices; GlowCompositePass binds its texture before
 * draw() submits the tessellator. No texture bind inside glBegin/glEnd. */
public final class LwjglMcpGlowBatchFactory implements LwjglGlowCompositeBackend.BatchFactory {
    private final FlowingGlow flowing;
    public LwjglMcpGlowBatchFactory(){flowing=null;}
    public LwjglMcpGlowBatchFactory(FlowingGlow flowing){this.flowing=flowing;}
    public GlowCompositePass.Batch begin(int primitive) {
        final Tessellator tessellator=Tessellator.getInstance();
        final WorldRenderer vertices=tessellator.getWorldRenderer();
        vertices.begin(primitive,DefaultVertexFormats.POSITION_TEX);
        return new GlowCompositePass.Batch(){
            public void vertex(double x,double y,double z,double u,double v){vertices.pos(x,y,z).tex(u,v).endVertex();}
            public void draw(){if(flowing==null)tessellator.draw();else flowing.draw(tessellator::draw);}
        };
    }
}
