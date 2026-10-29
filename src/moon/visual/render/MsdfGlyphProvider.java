package moon.visual.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/** h6 captured provider guards and fresh per-candidate kerning guards. Preserves bucket allocation, truncating lookup,
 * the UTF-16 random lookup bug, first-match kerning and the original out-of-range behavior.
 */
public final class MsdfGlyphProvider implements CachedMsdfMetrics.Font {
    private static String[] sentinel=new String[4];
    private static final Random RANDOM=new Random();
    private final Map<Integer,MsdfGlyphShape> glyphs=new HashMap<>();
    private final Supplier<MsdfFontData> data;
    private final String name;
    private int[][] buckets;
    public MsdfGlyphProvider(Supplier<MsdfFontData> data,String name) { this.data=data;this.name=name; }
    public void initialize() {
        String[] captured=getSentinel();
        MsdfFontData font=data.get();float maximum=0;
        for(MsdfFontData.Glyph glyph:font.getGlyphs()) {
            MsdfGlyphShape shape=new MsdfGlyphShape(glyph,font.getAtlas(),font.getMetrics());
            float advance=shape.advance();
            if(captured==null) return; // h6 87 -> 187, after constructing the first shape.
            if(advance>maximum) maximum=advance;
            glyphs.put(glyph.getUnicode(),shape);
        }
        buildBuckets(maximum*100);
    }
    private void buildBuckets(float maximum) {
        // Existing helper policy: moom.Dq.b(F)I is still an encrypted external class.
        int truncated=(int)maximum,length=maximum>truncated?truncated+1:truncated;
        String[] captured=getSentinel();
        buckets=new int[length][];
        int i=0;
        outer: while(i<length) {
            List<Integer> values=new ArrayList<>();
            for(Map.Entry<Integer,MsdfGlyphShape> entry:glyphs.entrySet()) {
                MsdfGlyphShape glyph=entry.getValue();
                // Original 99 -> 31: no index increment or bucket write. Stable nonempty
                // maps do not terminate here; callers must not invoke that path on the UI thread.
                if(captured==null) continue outer;
                boolean noPlane=glyph.getGlyph().getPlaneBounds()==null;
                float distance=Math.abs(glyph.advance()*100-i);
                if(!(distance<=.5F)) continue;
                if(noPlane) continue;
                int unicode=entry.getKey();
                if(unicode!=167) values.add(unicode); // Existing decoded h6 low integer.
            }
            int[] array=new int[values.size()];
            for(int j=0;j<values.size();j++) array[j]=values.get(j);
            buckets[i]=array;
            i++;
            if(captured==null) return;
        }
    }
    public MsdfGlyphShape randomGlyph(MsdfGlyphShape original) {
        int[] bucket=buckets[(int)(original.advance()*100)];
        return bucket.length==0?original:glyph((char)bucket[RANDOM.nextInt(bucket.length)]);
    }
    public float kerning(char previous,char current) {
        String[] captured=getSentinel();
        MsdfFontData font=data.get();MsdfGlyphShape glyph=glyphs.get((int)current);
        Map<Character,Float> cache=glyph.getKerningCache();
        Object selected;
        if(captured==null) selected=cache; // Original 47 -> 117: Map is cast to h3.
        else {
            if(cache.containsKey(previous)) return glyph.cachedKerning(previous);
            selected=font.getKerning().stream().filter(k->matchesKerning(previous,current,k))
                .findFirst().orElse(new MsdfFontData.Kerning());
        }
        float value=((MsdfFontData.Kerning)selected).getAdvance();
        glyph.cacheKerning(previous,value);return value;
    }
    private static boolean matchesKerning(char previous,char current,MsdfFontData.Kerning pair) {
        String[] captured=getSentinel();
        int first=pair.getUnicode1();
        return first==previous&&(captured==null||pair.getUnicode2()==current);
    }
    public MsdfGlyphShape glyph(char ch) { return glyphs.get((int)ch); }
    public Map<Integer,MsdfGlyphShape> getGlyphs() { return glyphs; }
    public Supplier<MsdfFontData> getDataSource() { return data; }
    public String getName() { return name; }
    public int[][] getBuckets() { return buckets; }
    public float descender() { return data.get().getMetrics().getDescender(); }
    public float lineHeight() { return data.get().getMetrics().getLineHeight(); }
    public static String[] getSentinel() { return sentinel; }
    public static void setSentinel(String[] value) { sentinel=value; }
}
