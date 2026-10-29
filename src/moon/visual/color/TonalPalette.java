package moon.visual.color;

import java.util.HashMap;
import java.util.Map;

/** Original com.h normal null-sentinel path. Each integer tone is solved once;
 * tone keys are neither clamped nor rounded and no failure is cached. */
public final class TonalPalette {
    Map<Integer, Integer> cache = new HashMap<>();
    double hue;
    double chroma;
    private final HctColor.ColorScience science;

    private TonalPalette(double hue, double chroma, HctColor.ColorScience science) {
        this.hue = hue;
        this.chroma = chroma;
        this.science = science;
    }

    public static TonalPalette fromArgb(int argb, HctColor.ColorScience science) {
        HctColor color = HctColor.fromArgb(argb, science);
        return fromHueAndChroma(color.hue(), color.chroma(), science);
    }

    public static TonalPalette fromHueAndChroma(double hue, double chroma, HctColor.ColorScience science) {
        return new TonalPalette(hue, chroma, science);
    }

    public int tone(int tone) {
        Integer color = cache.get(tone);
        if (color == null) {
            // Original com.h uses com.d.from(hue,chroma,tone).toArgb(), including
            // CAM16/L* refresh after solving; do not shortcut to solveToArgb().
            color = HctColor.from(hue, chroma, tone, science).toArgb();
            cache.put(tone, color);
        }
        return color;
    }

    public double hue() { return hue; }
    public double chroma() { return chroma; }
    public int cachedToneCount() { return cache.size(); }
}
