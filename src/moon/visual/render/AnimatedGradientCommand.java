package moon.visual.render;
import java.util.HashMap;
import java.util.function.LongSupplier;
import moon.visual.shader.*;
/** h8. Uniform caches retain the first command instance's live supplier.
 * Epoch is shared at class initialization; clock/epoch overload exists for deterministic replay.
 */
public final class AnimatedGradientCommand extends DrawCommand {
    private static final long epoch=System.currentTimeMillis();
    private static final HashMap<ShaderProgram,ShaderUniform> color1=new HashMap<>(),color2=new HashMap<>(),
    alphaUniform=new HashMap<>(),factor=new HashMap<>(),rainbowUniform=new HashMap<>(),time=new HashMap<>(),required=new HashMap<>();
    private final int from,to;
    private final float alpha,phase;
    private final boolean rainbow;
    private final DrawServices services;
    private final LongSupplier clock;
    private final long start;
    public AnimatedGradientCommand(int from,int to,float alpha,float phase,boolean rainbow,DrawServices services) {
        this(from,to,alpha,phase,rainbow,services,System::currentTimeMillis,epoch);
    }
    public AnimatedGradientCommand(int from,int to,float alpha,float phase,boolean rainbow,DrawServices services,LongSupplier clock,long start) {
        this.from=from;
        this.to=to;
        this.alpha=alpha;
        this.phase=phase;
        this.rainbow=rainbow;
        this.services=services;
        this.clock=clock;
        this.start=start;
    }
    private ShaderUniform create(String name) {
        ShaderUniform u=services.uniform(name,()->program);
        u.initializeLocation();
        return u;
    }
    public void before(float sw,float sh) {
        color1.computeIfAbsent(program,p->create("gradient.color1"));
        color2.computeIfAbsent(program,p->create("gradient.color2"));
        alphaUniform.computeIfAbsent(program,p->create("gradient.alpha"));
        factor.computeIfAbsent(program,p->create("gradient.factor"));
        rainbowUniform.computeIfAbsent(program,p->create("gradient.rainbow"));
        time.computeIfAbsent(program,p->create("gradient.time"));
        required.computeIfAbsent(program,p->create("gradient.required"));
        ShaderUniform c1=color1.get(program),c2=color2.get(program),a=alphaUniform.get(program),f=factor.get(program);
        Object[] captured=ShaderClipCommand.getSentinelArray();
        ShaderUniform r=rainbowUniform.get(program),t=time.get(program),enabled=required.get(program);
        color(c1,from);
        color(c2,to);
        a.setFloat(alpha);
        f.setInt(captured==null?(rainbow?1:0):(rainbow?4:8));
        r.setBoolean(rainbow);
        t.setFloat(((float)(clock.getAsLong()-start)+phase/5F)/1000F);
        enabled.setInt(1);
        if(captured==null)services.markers.setMarker("rBocF");
    }
    private void color(ShaderUniform u,int argb) {
        float a=RenderTransformState.getAlpha();
        u.setVector4((argb>>16&255)/255F,(argb>>8&255)/255F,(argb&255)/255F,(argb>>24&255)/255F*a);
    }
    public void after() {
        ShaderUniform enabled=required.get(program);
        program.use();
        enabled.setInt(0);
        services.backend.useProgram(0);
    }
}
