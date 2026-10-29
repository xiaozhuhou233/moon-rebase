package moon.visual.render;

/** k3.a(FFFFI) and k3.a(I) used by fI's item mask. Immediate vertices and raw
 * color are intentional; the trailing reset invalidates only the color cache. */
public final class LegacyColorRectangle {
    public interface Host {
        int rectangleMarker();void enableBlend();void disableBlend();void disableTexture2D();void enableTexture2D();
        void blendFuncSeparate(int sr,int dr,int sa,int da);void rawColor(float r,float g,float b,float a);
        void begin(int primitive);void vertex(float x,float y);void end();void resetColorCache();
    }
    public static final class Constants {
        public final int source,destination,primitive,redShift,redMask,greenShift,channelMask,alphaShift;
        public Constants(int source,int destination,int primitive,int redShift,int redMask,int greenShift,int channelMask,int alphaShift){
            this.source=source;this.destination=destination;this.primitive=primitive;this.redShift=redShift;this.redMask=redMask;
            this.greenShift=greenShift;this.channelMask=channelMask;this.alphaShift=alphaShift;
        }
    }
    private final Host host;private final Constants constants;
    public LegacyColorRectangle(Host host,Constants constants){this.host=host;this.constants=constants;}
    public void draw(float x,float y,float width,float height,int color){
        int marker=host.rectangleMarker();int primitive;
        if(marker==0){
            if(width<0){width=-width;x-=width;}if(height<0){height=-height;y-=height;}
            host.enableBlend();host.disableTexture2D();host.blendFuncSeparate(constants.source,constants.destination,1,0);
            color(color);primitive=constants.primitive;
        }else primitive=Float.isNaN(width)?1:width>0?1:width==0?0:-1; // Raw fcmpg result -> glBegin, no setup/normalization.
        host.begin(primitive);host.vertex(x,y+height);host.vertex(x+width,y+height);host.vertex(x+width,y);host.vertex(x,y);host.end();
        host.enableTexture2D();host.disableBlend();host.resetColorCache();
    }
    public void color(int value){host.rawColor((value>>constants.redShift&constants.redMask)/255F,
            (value>>constants.greenShift&constants.channelMask)/255F,(value&constants.channelMask)/255F,
            (value>>constants.alphaShift&constants.channelMask)/255F);}
    public static double interpolate(double target,double previous,double factor){return previous+(target-previous)*factor;}
}
