package moon.visual.render;

import java.awt.Color;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.lwjglx.util.vector.Vector4f;

/** fP/fN: actual rectangle/gradient/font submissions, independent write-expiring
 * caches, upper-only ratio clamping and the original double .1f smoothing. */
public final class EntityEspBarComponents {
    private EntityEspBarComponents() {}
    public enum Mode { STATIC,GRADIENT,AUTOMATIC }
    public interface Settings {
        boolean healthEnabled();boolean healthPercentage();Mode healthMode();
        int healthFirstColor();int healthSecondColor();
        boolean armorEnabled();Mode armorMode();int armorFirstColor();int armorSecondColor();
    }
    public interface Host<E> extends EntityEspComponent.Host<E> {
        BitmapFontFamily bitmapFont();boolean isLiving(E entity);E requireLiving(E entity);
        float health(E living);float maxHealth(E living);int armor(E living);
        /** moom.DR.b() for the Dq float clamp used by the percentage label. */
        Object clampMarker();
    }
    public static final class Constants {
        public final long cacheMinutes;
        public final int leadingColor,otherBackgroundColor,healthColorOr,gradientEstimate;
        /** Raw duration and encrypted colors are required; offline candidates aren't defaults. */
        public Constants(long cacheMinutes,int leadingColor,int otherBackgroundColor,int healthColorOr,int gradientEstimate) {
            this.cacheMinutes=cacheMinutes;this.leadingColor=leadingColor;this.otherBackgroundColor=otherBackgroundColor;
            this.healthColorOr=healthColorOr;this.gradientEstimate=gradientEstimate;
        }
    }
    private static <E> Cache<E,Double> cache(long minutes,Ticker ticker) {
        Duration duration=Duration.ofMinutes(minutes);
        long nanos;
        try { nanos=duration.toNanos(); }
        catch(ArithmeticException overflow) { nanos=duration.isNegative()?Long.MIN_VALUE:Long.MAX_VALUE; }
        // The destination includes Guava17, without the Duration overload. Match
        // newer Guava's saturated Duration->nanos conversion and use its same cache.
        return CacheBuilder.newBuilder().ticker(ticker).expireAfterWrite(nanos,TimeUnit.NANOSECONDS).build();
    }
    public static final class Health<E> extends EntityEspComponent<E,Settings> {
        private final EntityEspBarComponents.Host<E> barHost;private final Constants constants;private final Cache<E,Double> cache;
        public Health(Settings settings,EntityEspBarComponents.Host<E> host,Constants constants) { this(settings,host,constants,Ticker.systemTicker()); }
        public Health(Settings settings,EntityEspBarComponents.Host<E> host,Constants constants,Ticker ticker) {
            super(settings,host);barHost=host;this.constants=constants;cache=cache(constants.cacheMinutes,ticker);
        }
        public boolean accepts(E entity) {
            int marker=host.componentMarker();boolean enabled=getSettings().healthEnabled();
            return marker==0?enabled:enabled&&barHost.isLiving(entity);
        }
        private int healthColor(E living) {
            float hue=Math.max(0,Math.min(barHost.health(living),barHost.maxHealth(living))/barHost.maxHealth(living))/3F;
            return Color.HSBtoRGB(hue,1,1)|constants.healthColorOr;
        }
        private float ratio(E living,boolean update) {
            int marker=host.componentMarker();
            Double previous=cache.getIfPresent(living);
            double target=(double)Math.min(barHost.health(living)/barHost.maxHealth(living),1F);
            if(marker!=0&&previous==null)previous=target;
            double value=update?previous.doubleValue()+(target-previous.doubleValue())*(double).1F:previous.doubleValue();
            cache.put(living,value);
            return (float)value;
        }
        public void draw(E entity) {
            BitmapFontFamily font=barHost.bitmapFont();
            Vector4f bounds=host.projection(entity);
            float l=bounds.x;
            int marker=host.componentMarker();
            float t=bounds.y,r=bounds.z,b=bounds.w,width=r-l,height=b-t;
            if(marker!=0&&!barHost.isLiving(entity))return;
            E living=barHost.requireLiving(entity);
            float thickness=2,x=l-spacing-thickness,length=height;
            int color=healthColor(living);
            rect(x,t,.5F,length,constants.leadingColor);
            rect(x,t,thickness,.5F,constants.otherBackgroundColor);
            rect(x+thickness-spacing,t,.5F,length,constants.otherBackgroundColor);
            rect(x,t+length-spacing,thickness,.5F,constants.otherBackgroundColor);
            rect(x,t,thickness,length,constants.otherBackgroundColor);
            float value=ratio(living,true),filled=length*value;
            if(marker==0||getSettings().healthMode()==Mode.GRADIENT) {
                gradients.add(new UiGeometry.GradientRectangle(x+spacing,t+spacing+(length-filled),
                        thickness-spacing*2,filled-spacing*2,getSettings().healthFirstColor(),
                        getSettings().healthSecondColor(),true,constants.gradientEstimate,host));
            } else {
                // Mode is read a second time: callbacks may change it between these calls.
                rect(x+spacing,t+spacing+(length-filled),thickness-spacing*2,filled-spacing*2,
                        getSettings().healthMode()==Mode.STATIC?getSettings().healthFirstColor():color);
            }
            float current=barHost.health(living),maximum=barHost.maxHealth(living);
            int comparison=current<maximum?-1:current==maximum?0:1; // fcmpg, NaN -> +1.
            int percentage;
            if(marker==0)percentage=comparison;
            else {
                if(comparison>=0||!getSettings().healthPercentage())return;
                float v=barHost.health(living)/barHost.maxHealth(living);
                float clamped=barHost.clampMarker()==null?Math.min(v,0F):v<0?0:Math.min(v,1F);
                percentage=Math.round(clamped*100F);
            }
            String text=String.valueOf(percentage); // Original concat recipe is only U+0001, no '%' suffix.
            font.drawOutline(bitmap,text,x+thickness-font.width(text),t+spacing+(length-filled)-font.height(),1,-1);
        }
        private void rect(float x,float y,float w,float h,int color) { rectangles.add(new UiGeometry.Rectangle(x,y,w,h,color)); }
    }
    public static final class Armor<E> extends EntityEspComponent<E,Settings> {
        private final EntityEspBarComponents.Host<E> barHost;private final Constants constants;private final Cache<E,Double> cache;
        public Armor(Settings settings,EntityEspBarComponents.Host<E> host,Constants constants) { this(settings,host,constants,Ticker.systemTicker()); }
        public Armor(Settings settings,EntityEspBarComponents.Host<E> host,Constants constants,Ticker ticker) {
            super(settings,host);barHost=host;this.constants=constants;cache=cache(constants.cacheMinutes,ticker);
        }
        public boolean accepts(E entity) {
            int marker=host.inverseComponentMarker();boolean enabled=getSettings().armorEnabled();
            return marker!=0?enabled:enabled&&barHost.isLiving(entity);
        }
        private float ratio(E living,boolean update) {
            Double previous=cache.getIfPresent(living);
            int marker=host.componentMarker();
            double target=(double)Math.min((float)barHost.armor(living)/20F,1F);
            if(marker!=0&&previous==null)previous=target;
            double value=update?previous.doubleValue()+(target-previous.doubleValue())*(double).1F:previous.doubleValue();
            cache.put(living,value);
            return (float)value;
        }
        public void draw(E entity) {
            Vector4f bounds=host.projection(entity);
            float l=bounds.x,t=bounds.y;
            int marker=host.inverseComponentMarker();
            float r=bounds.z,b=bounds.w,width=r-l,height=b-t;
            if(marker==0&&!barHost.isLiving(entity))return;
            E living=barHost.requireLiving(entity);
            float thickness=2,y=t+height+spacing,length=width;
            rect(l,y,length,.5F,constants.leadingColor);
            rect(l,y,.5F,thickness,constants.otherBackgroundColor);
            rect(l+length-spacing,y,.5F,thickness,constants.otherBackgroundColor);
            rect(l,y+thickness-spacing,length,.5F,constants.otherBackgroundColor);
            rect(l,y,length,thickness,constants.otherBackgroundColor);
            float value=ratio(living,true),filled=length*value;
            if(marker!=0||getSettings().armorMode()==Mode.GRADIENT) {
                gradients.add(new UiGeometry.GradientRectangle(l+spacing,y+spacing,filled-spacing*2,
                        thickness-spacing*2,getSettings().armorFirstColor(),getSettings().armorSecondColor(),
                        false,constants.gradientEstimate,host));
            } else rect(l+spacing,y+spacing,filled-spacing*2,thickness-spacing*2,getSettings().armorFirstColor());
        }
        private void rect(float x,float y,float w,float h,int color) { rectangles.add(new UiGeometry.Rectangle(x,y,w,h,color)); }
    }
}
