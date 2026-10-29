package moon.visual.render;

/** kP: reverse palette sampling and truncating ARGB interpolation. The decoded
 * channel table matches ARGB semantics; its seed is an offline model candidate.
 * No clamping, palette validation or NaN sanitization is added.
 */
public class ColorInterpolation {
    public ColorInterpolation() { }
    public static int sample(int[] colors,double factor) {
        int sentinel=ArgbColor.getSentinel();
        int length=colors.length;
        if(sentinel==0) {
            if(compareToOne(factor)==0)return colors[length-1];
        } else {
            if(compareToOne(factor)>0)factor=1D-factor%1D;
            if(factor==1D)return colors[0];
            if(factor==0D)return colors[length-1];
        }
        double position=Math.max(0D,(1D-factor)*(length-1));
        int index=(int)position;
        int first=colors[index],second=colors[index+1];
        return interpolate(first,second,position-index);
    }
    public static int interpolate(int first,int second,double factor) {
        int comparison=compareToOne(factor);
        if(ArgbColor.getSentinel()==0)return comparison;
        if(comparison>0)factor=1D-factor%1D;
        return interpolateRaw(first,second,factor);
    }
    private static int compareToOne(double value) {
        return value>1D||Double.isNaN(value)?1:value==1D?0:-1; // JVM dcmpl
    }
    public static int interpolateRaw(int first,int second,double factor) {
        double inverse=1D-factor;
        int red=(int)((first>>16&255)*inverse+(second>>16&255)*factor);
        int green=(int)((first>>8&255)*inverse+(second>>8&255)*factor);
        int blue=(int)((first&255)*inverse+(second&255)*factor);
        int alpha=(int)((first>>24&255)*inverse+(second>>24&255)*factor);
        return (alpha&255)<<24|(red&255)<<16|(green&255)<<8|blue&255;
    }
    public static float[] rgba(int argb) {
        return new float[]{(argb>>16&255)/255F,(argb>>8&255)/255F,(argb&255)/255F,(argb>>24&255)/255F};
    }
}
