package moon.visual.render;

import com.google.gson.annotations.SerializedName;

/** hN, including its original nullable static marker. */
public final class BitmapAtlasData {
    @SerializedName("chars") private BitmapGlyph[] glyphs;
    private static int[] sentinel;
    public BitmapGlyph[] getGlyphs() { return glyphs; }
    public BitmapAtlasData setGlyphs(BitmapGlyph[] value) { glyphs=value; return this; }
    public static int[] getSentinel() { return sentinel; }
    public static void setSentinel(int[] value) { sentinel=value; }
    static { if(getSentinel()!=null) setSentinel(new int[3]); }
}
