package moon.visual.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** hX captured hY guards, live caches and UTF-16 measurement. h6 stays a host boundary.
 * Measures UTF-16 chars exactly as the original: no code-point conversion or newline layout.
 */
public final class CachedMsdfMetrics implements MsdfMetrics {
    public interface Glyph {
        float advance();       // hY.g(): mutable j
        float planeHeight();   // hY.f(): final i = planeBounds.top - bottom
    }
    public interface Font {
        Glyph glyph(char character);
        float kerning(char previous,char current);
        float descender();
        float lineHeight();
    }
    private final Map<String,Float> widths=new ConcurrentHashMap<>();
    private final Map<String,Float> heights=new ConcurrentHashMap<>();
    private final Font font;
    private final BooleanSupplier glyphSentinel;
    public CachedMsdfMetrics(Font font,BooleanSupplier glyphSentinel) {
        this.font=font; this.glyphSentinel=glyphSentinel;
    }
    public float relativeWidth(String text) {
        widths.computeIfAbsent(text,this::computeWidth);
        return widths.get(text);
    }
    public float relativeHeight(String text) {
        heights.computeIfAbsent(text,this::computeHeight);
        return heights.get(text)+font.descender();
    }
    public float lineHeight() { return font.lineHeight(); }
    public Map<String,Float> getWidths() { return widths; }
    public Map<String,Float> getHeights() { return heights; }
    public Font getFont() { return font; }
    private Float computeWidth(String text) {
        char[] chars=text.toCharArray();
        boolean captured=glyphSentinel.getAsBoolean();
        // hX width 67 -> 345 -> 350: first char is read, no glyph callback occurs.
        if(!captured) return 0F;
        float width=0;
        char previous=0;
        for(int i=0;i<chars.length;i++) {
            boolean hasPrevious=previous!=0;
            char ch=chars[i];
            if(ch==10||ch==13||ch==9) continue;
            if(ch==167) {
                if(i>=chars.length-2) break; // Original drops a trailing format pair.
                i++;
                continue;
            }
            Glyph glyph=font.glyph(ch);
            if(glyph==null) continue;
            float kerning=hasPrevious?font.kerning(previous,ch):0;
            width+=glyph.advance()+kerning;
            previous=ch;
        }
        return width;
    }
    private Float computeHeight(String text) {
        boolean captured=glyphSentinel.getAsBoolean();
        char[] chars=text.toCharArray();
        // hX height 40 -> 324 -> 329: zero cached height, descender added by caller.
        if(!captured) return 0F;
        float height=0;
        for(int i=0;i<chars.length;i++) {
            char ch=chars[i];
            if(ch==10||ch==13||ch==9) continue;
            if(ch==167) {
                if(i>=chars.length-2) break;
                i++;
                continue;
            }
            Glyph glyph=font.glyph(ch);
            if(glyph==null) continue;
            if(glyph.planeHeight()>height) height=glyph.planeHeight();
        }
        return height;
    }
}
