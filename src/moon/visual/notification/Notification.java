package moon.visual.notification;

import java.util.function.LongSupplier;
import moon.visual.gui.TimedAnimation;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** gc/gd normal null-marker path. Encrypted background/duration remain explicit
 * parameters. Constructor measurements, alpha replacement, advance-based icon
 * centering and draw-before-animation-update are deliberately preserved. */
public final class Notification {
    public interface Theme {int accent();int onAccent();}
    public interface ThemeSource {Theme current();}
    public interface Host {
        MsdfFontBase textFont();MsdfFontBase iconFont();
        float iconAdvance(char icon);float iconPlaneHeight(char icon);
        int scaledWidth();int scaledHeight();
        ThemeSource resolveClickGui();
        LongSupplier clock();MarkerHooks markers();
    }
    public static final class Parameters {
        public final int backgroundArgb,linearEasingCase;
        public final long animationDuration;
        /** linearEasingCase is the unresolved original k_ mapping for bs.LINEAR. */
        public Parameters(int backgroundArgb,long animationDuration,int linearEasingCase){
            this.backgroundArgb=backgroundArgb;this.animationDuration=animationDuration;this.linearEasingCase=linearEasingCase;
        }
    }
    private static String marker;
    private final Host host;private final Parameters parameters;
    private final String title,message;private final NotificationType type;private final long duration;
    private final TimedAnimation animation;
    private ThemeSource themeSource;
    private float x,y,width,height,screenOffset;private long expirationTime;
    private boolean expired,finished;

    private Notification(Builder b){
        host=b.host;parameters=b.parameters;
        animation=new TimedAnimation(parameters.animationDuration,parameters.linearEasingCase,host.clock(),host.markers());
        title=b.title;message=b.message;type=b.type;duration=b.duration;
        measureHeight(); // Original constructor computes height, then overwrites it with builder.i.
        themeSource=b.themeSource;x=b.x;y=b.y;width=b.width;height=b.height;screenOffset=b.screenOffset;
        expirationTime=b.expirationTime;expired=b.expired;finished=b.finished;
        if(host.markers().getMarker()!=null)marker="RSLH9b";
    }
    public void initialize(){
        measureHeight();measureWidth();screenOffset=width+2F;
        expirationTime=host.clock().getAsLong()+duration;themeSource=host.resolveClickGui();
    }
    private void measureHeight(){height=Math.round(host.textFont().height(12F)+host.textFont().height(8F))+12F;}
    private void measureWidth(){
        float iconBox=Math.round(height-12F)+.5F;
        float titleWidth=host.textFont().width(title,12F),messageWidth=host.textFont().width(message,8F);
        width=Math.max(titleWidth,messageWidth)+18F+iconBox;
    }
    private UiGeometry.RoundedRectangle rounded(float x,float y,float w,float h,float radius,int color){
        return new UiGeometry.RoundedRectangle(x,y,w,h,radius,color,host.markers());
    }
    public void prepare(DrawTask background,DrawTask icons,DrawTask text,DrawTask mask,float stackOffset){
        if(marker!=null)throw new UnsupportedOperationException("gc nonnull marker path not restored");
        Theme theme=themeSource.current();
        y=host.scaledHeight()-height-4F+stackOffset;
        float progress=animation.get();x=host.scaledWidth()-(screenOffset+2F)*progress;
        background.add(rounded(x,y,width,height,6F,new ArgbColor(parameters.backgroundArgb).withAlpha(.5F*progress).getRGB()));
        int accent=new ArgbColor(theme.accent()).withAlpha(progress).getRGB();
        int onAccent=new ArgbColor(theme.onAccent()).withAlpha(progress).getRGB();
        int white=new ArgbColor(0xffffffff).withAlpha(progress).getRGB();
        float iconBox=Math.round(height-12F)+.5F;
        background.add(rounded(x+6F+.5F,y+6F,iconBox,iconBox,6F,accent));
        char icon=type.icon().charAt(0);
        // Original hY.g() is advance, NOT plane width; hY.f() is plane height.
        float advance=host.iconAdvance(icon)*16F,planeHeight=host.iconPlaneHeight(icon)*16F;
        host.iconFont().drawSinglePass(icons,type.icon(),16F,x+.5F+6F+iconBox/2F-advance/2F,
            y+6F+1.5F+iconBox/2F-2F-planeHeight/4F,onAccent);
        host.textFont().drawSinglePass(text,title,12F,x+6F+iconBox+6F,y+6F-.5F,white);
        host.textFont().drawSinglePass(text,message,8F,x+6F+iconBox+6F,y+6F-.5F+host.textFont().height(12F),white);
        mask.add(rounded(x,y,width,height,6F,new ArgbColor(0xff000000).withAlpha(progress).getRGB()));
        mask.add(rounded(x+6F+.5F,y+6F,iconBox,iconBox,0F,accent).smooth(false));
        animation.update(0F,1F,!expired);
        if(expired&&progress==0F)finished=true;
    }
    public float animatedHeight(){return height*animation.get();}
    public String title(){return title;}public String message(){return message;}public NotificationType type(){return type;}
    public long duration(){return duration;}public long expirationTime(){return expirationTime;}
    public float x(){return x;}public float y(){return y;}public float width(){return width;}public float height(){return height;}
    public float screenOffset(){return screenOffset;}public ThemeSource themeSource(){return themeSource;}
    public boolean expired(){return expired;}public boolean finished(){return finished;}
    public TimedAnimation animation(){return animation;}
    public Notification expired(boolean value){expired=value;return this;}
    public Notification finished(boolean value){finished=value;return this;}
    public static String marker(){return marker;}public static void marker(String value){marker=value;}
    public static Builder builder(Host host,Parameters parameters){return new Builder(host,parameters);}

    /** gd defaults and fluent replacement semantics; no invented validation/defaults. */
    public static final class Builder {
        private final Host host;private final Parameters parameters;
        private String title,message;private NotificationType type;private long duration;
        private ThemeSource themeSource;private float x,y,width,height,screenOffset;private long expirationTime;
        private boolean expired,finished;
        private Builder(Host host,Parameters parameters){this.host=host;this.parameters=parameters;}
        public Builder title(String v){title=v;return this;}public Builder message(String v){message=v;return this;}
        public Builder type(NotificationType v){type=v;return this;}public Builder duration(long v){duration=v;return this;}
        public Builder themeSource(ThemeSource v){themeSource=v;return this;}
        public Builder x(float v){x=v;return this;}public Builder y(float v){y=v;return this;}
        public Builder width(float v){width=v;return this;}public Builder height(float v){height=v;return this;}
        public Builder screenOffset(float v){screenOffset=v;return this;}public Builder expirationTime(long v){expirationTime=v;return this;}
        public Builder expired(boolean v){expired=v;return this;}public Builder finished(boolean v){finished=v;return this;}
        public Notification build(){return new Notification(this);}
    }
}
