package moon.visual.color;

/** Original com.g normal null-com.h-sentinel path. Palette slots stay publicly
 * mutable, as in the original. Hue+60 is passed through without normalization. */
public final class CorePalette {
    public TonalPalette primary;
    public TonalPalette secondary;
    public TonalPalette tertiary;
    public TonalPalette neutral;
    public TonalPalette neutralVariant;
    public TonalPalette error;

    private CorePalette(int argb, boolean content, HctColor.ColorScience science) {
        HctColor color = HctColor.fromArgb(argb, science);
        double hue = color.hue();
        double chroma = color.chroma();
        if (content) {
            primary = TonalPalette.fromHueAndChroma(hue, chroma, science);
            secondary = TonalPalette.fromHueAndChroma(hue, chroma / 3.0, science);
            tertiary = TonalPalette.fromHueAndChroma(hue + 60.0, chroma / 2.0, science);
            neutral = TonalPalette.fromHueAndChroma(hue, Math.min(chroma / 12.0, 4.0), science);
            neutralVariant = TonalPalette.fromHueAndChroma(hue, Math.min(chroma / 6.0, 8.0), science);
        } else {
            primary = TonalPalette.fromHueAndChroma(hue, Math.max(48.0, chroma), science);
            secondary = TonalPalette.fromHueAndChroma(hue, 16.0, science);
            tertiary = TonalPalette.fromHueAndChroma(hue + 60.0, 24.0, science);
            neutral = TonalPalette.fromHueAndChroma(hue, 4.0, science);
            neutralVariant = TonalPalette.fromHueAndChroma(hue, 8.0, science);
        }
        error = TonalPalette.fromHueAndChroma(25.0, 84.0, science);
    }

    public static CorePalette fromArgb(int argb, HctColor.ColorScience science) {
        return new CorePalette(argb, false, science);
    }

    public static CorePalette contentFromArgb(int argb, HctColor.ColorScience science) {
        return new CorePalette(argb, true, science);
    }
}
