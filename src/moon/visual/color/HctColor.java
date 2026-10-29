package moon.visual.color;

/** Original com.d state/ordering. ColorScience still requires the packed RGB,
 * L* and HCT solver integration. Cam16Color now supplies recovered appearance
 * mathematics, but this state wrapper supplies no RGB shortcut. */
public final class HctColor {
    public interface ColorScience {
        Appearance cam16FromArgb(int argb);
        double lstarFromArgb(int argb);
        int solveToArgb(double hue, double chroma, double tone);
    }

    public interface Appearance {
        double hue();
        double chroma();
    }

    private double hue;
    private double chroma;
    private double tone;
    private int argb;
    private final ColorScience science;

    private HctColor(int argb, ColorScience science) {
        this.science = science;
        setInternalState(argb);
    }

    public static HctColor fromArgb(int argb, ColorScience science) {
        return new HctColor(argb, science);
    }

    public static HctColor from(double hue, double chroma, double tone, ColorScience science) {
        return new HctColor(science.solveToArgb(hue, chroma, tone), science);
    }

    public double hue() { return hue; }
    public double chroma() { return chroma; }
    public double tone() { return tone; }
    public int toArgb() { return argb; }

    public void setHue(double hue) {
        setInternalState(science.solveToArgb(hue, chroma, tone));
    }

    public void setChroma(double chroma) {
        setInternalState(science.solveToArgb(hue, chroma, tone));
    }

    public void setTone(double tone) {
        setInternalState(science.solveToArgb(hue, chroma, tone));
    }

    private void setInternalState(int argb) {
        // Original com.d commits ARGB before CAM16, then hue/chroma before L*.
        // Exceptions deliberately leave those partial assignments in place.
        this.argb = argb;
        Appearance appearance = science.cam16FromArgb(argb);
        hue = appearance.hue();
        chroma = appearance.chroma();
        tone = science.lstarFromArgb(argb);
    }
}
