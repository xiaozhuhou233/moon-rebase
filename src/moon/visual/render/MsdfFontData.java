package moon.visual.render;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** h1 root and h0/h2/h3/h4/hZ JSON models. Nullable bounds and absent lists stay nullable. */
public final class MsdfFontData {
    @SerializedName("atlas") private Atlas atlas;
    @SerializedName("metrics") private Metrics metrics;
    @SerializedName("glyphs") private List<Glyph> glyphs;
    @SerializedName("kerning") private List<Kerning> kerning;
    private static Object[] sentinel;
    public Atlas getAtlas() { return atlas; }
    public Metrics getMetrics() { return metrics; }
    public List<Glyph> getGlyphs() { return glyphs; }
    public List<Kerning> getKerning() { return kerning; }
    public MsdfFontData setAtlas(Atlas value) { atlas=value; return this; }
    public MsdfFontData setMetrics(Metrics value) { metrics=value; return this; }
    public MsdfFontData setGlyphs(List<Glyph> value) { glyphs=value; return this; }
    public MsdfFontData setKerning(List<Kerning> value) { kerning=value; return this; }
    public static Object[] getSentinel() { return sentinel; }
    public static void setSentinel(Object[] value) { sentinel=value; }
    static { if(getSentinel()!=null) setSentinel(new Object[5]); }

    public static final class Bounds {
        @SerializedName("left") private float left;
        @SerializedName("top") private float top;
        @SerializedName("right") private float right;
        @SerializedName("bottom") private float bottom;
        public float getLeft() { return left; }
        public float getTop() { return top; }
        public float getRight() { return right; }
        public float getBottom() { return bottom; }
        public Bounds setLeft(float value) { left=value; return this; }
        public Bounds setTop(float value) { top=value; return this; }
        public Bounds setRight(float value) { right=value; return this; }
        public Bounds setBottom(float value) { bottom=value; return this; }
    }
    public static final class Glyph {
        @SerializedName("unicode") private int unicode;
        @SerializedName("advance") private float advance;
        @SerializedName("planeBounds") private Bounds planeBounds;
        @SerializedName("atlasBounds") private Bounds atlasBounds;
        public int getUnicode() { return unicode; }
        public float getAdvance() { return advance; }
        public Bounds getPlaneBounds() { return planeBounds; }
        public Bounds getAtlasBounds() { return atlasBounds; }
        public Glyph setUnicode(int value) { unicode=value; return this; }
        public Glyph setAdvance(float value) { advance=value; return this; }
        public Glyph setPlaneBounds(Bounds value) { planeBounds=value; return this; }
        public Glyph setAtlasBounds(Bounds value) { atlasBounds=value; return this; }
    }
    public static final class Kerning {
        @SerializedName("unicode1") private int unicode1;
        @SerializedName("unicode2") private int unicode2;
        @SerializedName("advance") private float advance;
        public int getUnicode1() { return unicode1; }
        public int getUnicode2() { return unicode2; }
        public float getAdvance() { return advance; }
        public Kerning setUnicode1(int value) { unicode1=value; return this; }
        public Kerning setUnicode2(int value) { unicode2=value; return this; }
        public Kerning setAdvance(float value) { advance=value; return this; }
    }
    public static final class Atlas {
        @SerializedName("type") private String type;
        @SerializedName("distanceRange") private float distanceRange;
        @SerializedName("size") private float size;
        @SerializedName("width") private float width;
        @SerializedName("height") private float height;
        @SerializedName("yOrigin") private String yOrigin;
        public String getType() { return type; }
        public float getDistanceRange() { return distanceRange; }
        public float getSize() { return size; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        public String getYOrigin() { return yOrigin; }
        public Atlas setType(String value) { type=value; return this; }
        public Atlas setDistanceRange(float value) { distanceRange=value; return this; }
        public Atlas setSize(float value) { size=value; return this; }
        public Atlas setWidth(float value) { width=value; return this; }
        public Atlas setHeight(float value) { height=value; return this; }
        public Atlas setYOrigin(String value) { yOrigin=value; return this; }
    }
    public static final class Metrics {
        @SerializedName("emSize") private float emSize;
        @SerializedName("lineHeight") private float lineHeight;
        @SerializedName("ascender") private float ascender;
        @SerializedName("descender") private float descender;
        @SerializedName("underlineY") private float underlineY;
        @SerializedName("underlineThickness") private float underlineThickness;
        /** h4.a(): original baseline expression and marker side effect. */
        public float baseline(MarkerHooks markers) {
            Object[] captured=MsdfFontData.getSentinel();
            float result=lineHeight+descender+underlineY/2F;
            if(captured!=null) markers.setMarker("HvGRjc");
            return result;
        }
        public float getEmSize() { return emSize; }
        public float getLineHeight() { return lineHeight; }
        public float getAscender() { return ascender; }
        public float getDescender() { return descender; }
        public float getUnderlineY() { return underlineY; }
        public float getUnderlineThickness() { return underlineThickness; }
        public Metrics setEmSize(float value) { emSize=value; return this; }
        public Metrics setLineHeight(float value) { lineHeight=value; return this; }
        public Metrics setAscender(float value) { ascender=value; return this; }
        public Metrics setDescender(float value) { descender=value; return this; }
        public Metrics setUnderlineY(float value) { underlineY=value; return this; }
        public Metrics setUnderlineThickness(float value) { underlineThickness=value; return this; }
    }
}
