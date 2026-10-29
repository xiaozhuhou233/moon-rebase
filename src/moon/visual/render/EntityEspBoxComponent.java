package moon.visual.render;

import org.lwjglx.util.vector.Vector4f;

/** fO, including the Cz.c()==0 branch: fill becomes the corner predicate,
 * no inner fill is added, and the double border follows the corner strips. */
public final class EntityEspBoxComponent<E> extends EntityEspComponent<E,EntityEspBoxComponent.Settings> {
    public interface Settings {
        boolean boxEnabled();int boxColor();boolean innerFill();boolean corners();
        float cornerDivisor();boolean independentVerticalCorners();
    }
    public static final class Constants {
        public final int fillColor,borderEstimate,borderReturnedCount;
        /** fO.i low int, hA.a() and hA.write() return value. None runtime confirmed. */
        public Constants(int fillColor,int borderEstimate,int borderReturnedCount) {
            this.fillColor=fillColor;this.borderEstimate=borderEstimate;this.borderReturnedCount=borderReturnedCount;
        }
    }
    private final Constants constants;
    public EntityEspBoxComponent(Settings settings,Host<E> host,Constants constants) {
        super(settings,host);this.constants=constants;
    }
    public boolean accepts(E entity) { return getSettings().boxEnabled(); }
    public void draw(E entity) {
        int marker=host.componentMarker();
        Vector4f bounds=host.projection(entity);
        float l=bounds.x,t=bounds.y,r=bounds.z,b=bounds.w,width=r-l,height=b-t;
        int backing=0xff000000,color=getSettings().boxColor();
        boolean fill=getSettings().innerFill();
        if(marker!=0&&fill)rect(l+1,t+1,width-2,height-2,constants.fillColor);
        boolean corners=marker==0?fill:getSettings().corners();
        if(corners) {
            float horizontal=(r-l)/getSettings().cornerDivisor();
            float vertical=getSettings().independentVerticalCorners()?(b-t)/getSettings().cornerDivisor():horizontal;
            // Preserve the original submission order, including overlapping strips.
            rect(l,t,horizontal,1.5F,backing);
            rect(l,t,1.5F,vertical,backing);
            rect(r-horizontal,t,horizontal,1.5F,backing);
            rect(r-1.5F,t,1.5F,vertical,backing);
            rect(l,b-1.5F,horizontal,1.5F,backing);
            rect(l,b-vertical,1.5F,vertical,backing);
            rect(r-horizontal,b-1.5F,horizontal,1.5F,backing);
            rect(r-1.5F,b-vertical,1.5F,vertical,backing);
            rect(l+.5F,t+.5F,horizontal-1,.5F,color);
            rect(l+.5F,t+.5F,.5F,vertical-1,color);
            rect(r-horizontal+.5F,t+.5F,horizontal-1,.5F,color);
            rect(r-1,t+.5F,.5F,vertical-1,color);
            rect(l+.5F,b-1,horizontal-1,.5F,color);
            rect(l+.5F,b-vertical+.5F,.5F,vertical-1,color);
            rect(r-horizontal+.5F,b-1,horizontal-1,.5F,color);
            rect(r-1,b-vertical+.5F,.5F,vertical-1,color);
            if(marker!=0)return;
            host.setMarker("vDJuyc");
        }
        rectangles.add(UiGeometry.DoubleBorder.create(l,t,r,b,backing,color,
                constants.borderEstimate,constants.borderReturnedCount,host));
    }
    private void rect(float x,float y,float w,float h,int color) { rectangles.add(new UiGeometry.Rectangle(x,y,w,h,color)); }
}
