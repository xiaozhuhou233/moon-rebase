package moon.visual.hud;

import java.awt.Color;
import java.util.function.LongSupplier;
import moon.visual.render.ColorInterpolation;

/** f1 color helper. Normal fZ-null path plus original nonnull bad-cast failure.
 * Parameters are explicit because f1's numeric seed was not captured at runtime.
 * HUD_CONTENT_EVIDENCE.json records the offline candidates separately.
 */
public final class HudColors {
    public enum Mode { SYNC,STATIC,GRADIENT,PULSING } // bc declaration order
    public interface Config {
        int textColorOne();int textColorTwo();HudRenderPipeline.TextColorMode textMode();
    }
    public static final class Parameters {
        public final int pulsingLength,pulsingFinalIndex,gradientLength,gradientFinalIndex;
        public final long pulsingPhase,gradientPhase;
        public Parameters(int pulsingLength,int pulsingFinalIndex,long pulsingPhase,
                          int gradientLength,int gradientFinalIndex,long gradientPhase) {
            this.pulsingLength=pulsingLength;this.pulsingFinalIndex=pulsingFinalIndex;this.pulsingPhase=pulsingPhase;
            this.gradientLength=gradientLength;this.gradientFinalIndex=gradientFinalIndex;this.gradientPhase=gradientPhase;
        }
    }
    private final Parameters parameters;
    private final LongSupplier clock;
    public HudColors(Parameters parameters){this(parameters,System::currentTimeMillis);}
    public HudColors(Parameters parameters,LongSupplier clock){this.parameters=parameters;this.clock=clock;}
    public int color(float offset,Config config,Mode mode,int first,int second) {
        return color(offset,4500D,config,mode,first,second);
    }
    public int color(float offset,double period,Config config,Mode mode,int first,int second) {
        Object[] guard=DraggableComponentProperties.getSentinel();
        int index=mode.ordinal();
        if(guard!=null) {
            // Original bc is checkcast to unrelated ba before reading the clock.
            index=((HudRenderPipeline.TextColorMode)(Object)mode).ordinal()+1;
        } else if(mode==Mode.SYNC) {
            first=config.textColorOne();second=config.textColorTwo();index=config.textMode().ordinal()+1;
        }
        long now=clock.getAsLong(); // read even STATIC / INDIVIDUAL
        if(index!=2&&index!=3)return first;
        long cycle=(long)(1.5D*period);
        boolean pulsing=index==3;
        if(pulsing)second=new Color(first).darker().darker().getRGB();
        int[] palette=new int[pulsing?parameters.pulsingLength:parameters.gradientLength];
        palette[0]=first;palette[1]=second;palette[2]=first;
        palette[3]=second;palette[4]=first;palette[5]=second;
        palette[pulsing?parameters.pulsingFinalIndex:parameters.gradientFinalIndex]=first;
        long phase=pulsing?parameters.pulsingPhase:parameters.gradientPhase;
        float factor=(float)((now+(long)(int)offset*phase)%cycle)/((float)cycle/2F);
        return ColorInterpolation.sample(palette,(double)factor);
    }
}
