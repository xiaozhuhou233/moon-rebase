package moon.visual.render;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import org.lwjglx.util.vector.Matrix4f;
import org.lwjglx.util.vector.Vector3f;

/** hH bitmap drawing, measurement and transform, on the original default null-hN path.
 * Width deliberately remains in atlas pixels; the hI family performs its own live division.
 * Nondefault draw/palette paths stay explicit rather than silently behaving as the default.
 */
public final class BitmapAtlasFont extends BitmapFontFace {
    public interface Host {
        void enableTexture2D();
        void enableBlend();
        /** ResourceLocation-style relative path: bit/<name>.png, default namespace minecraft. */
        void bindResource(String path);
        void bindTexture(int texture);
    }
    private final Map<String,Float> widthCache=new HashMap<>();
    private final int[] palette=new int[32];
    private final float scaledSize;
    private final float scale;
    private final String texture;
    private final BitmapGlyph[] glyphs=new BitmapGlyph[256];
    private final Host host;
    private final MarkerHooks markers;

    public BitmapAtlasFont(String name,float size,float scale,BitmapAtlasLoader loader,
                           Host host,MarkerHooks markers) {
        this.host=host;
        this.markers=markers;
        int[] captured=BitmapAtlasData.getSentinel();
        texture="bit/"+name+".png";
        scaledSize=size*scale;
        this.scale=scale;
        BitmapAtlasData data=loader.load(name);
        for(BitmapGlyph glyph:data.getGlyphs()) {
            glyphs[glyph.getId()]=glyph;
            if(captured!=null) return; // bytecode 180 -> 204 skips palette initialization
        }
        initializePalette();
    }

    private void initializePalette() {
        requireDefaultPath();
        for(int i=0;i<32;i++) {
            int base=(i>>3&1)*85;
            int red=(i>>2&1)*170+base;
            int green=(i>>1&1)*170+base;
            int blue=(i&1)*170+base;
            if(i==6) red+=85;
            if(i>=16) { red/=4; green/=4; blue/=4; }
            palette[i]=(red&255)<<16|(green&255)<<8|blue&255;
        }
    }

    @Override public void draw(DrawTask task,String text,float x,float y,int color,boolean shadowPass) {
        x*=scale;
        int[] captured=BitmapAtlasData.getSentinel();
        y*=scale;
        requireDefaultPath(captured);
        float penX=x;
        float[] original={(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>24&255)/255F};
        char[] chars=text.toCharArray();
        for(int i=0;i<chars.length;i++) {
            char ch=text.charAt(i);
            if(ch>=256) continue;
            if(ch==32) { penX+=3; continue; }
            if(ch==167&&i+1<text.length()) {
                if(!shadowPass) {
                    int format="0123456789abcdefklmnor".indexOf(text.charAt(i+1));
                    if(format<16) {
                        if(format<0) format=15;
                        int rgb=palette[format];
                        color=new Color(rgb>>16&255,rgb>>8&255,rgb&255,(int)(original[3]*255F)).getRGB();
                    } else if(format==21) {
                        color=new Color((int)(original[0]*255F),(int)(original[1]*255F),
                                (int)(original[2]*255F),(int)(original[3]*255F)).getRGB();
                    }
                }
                i++;
                continue;
            }
            BitmapGlyph glyph=glyphs[ch];
            if(glyph==null) continue;
            task.add(new UiGeometry.ColoredTextureQuad(penX,y,penX+glyph.getWidth(),y+glyph.getHeight(),
                    glyph.getX()/128F,glyph.getY()/128F,(glyph.getX()+glyph.getWidth())/128F,
                    (glyph.getY()+glyph.getHeight())/128F,color,markers));
            penX+=glyph.getWidth()+1;
        }
        if(markers.getMarker()!=null) BitmapAtlasData.setSentinel(new int[2]);
    }

    @Override public void apply(Matrix4f matrix) {
        host.enableTexture2D();
        host.enableBlend();
        host.bindResource(texture);
        matrix.scale(new Vector3f(1F/scale,1F/scale,1));
    }
    @Override public void unapply(Matrix4f matrix) {
        host.bindTexture(0);
        matrix.scale(new Vector3f(scale,scale,1));
    }
    @Override public float width(String text) {
        // The non-null branch is already fully determined here: immediate map.get/unbox.
        if(BitmapAtlasData.getSentinel()!=null) return widthCache.get(text);
        if(widthCache.containsKey(text)) return widthCache.get(text);
        float width=0;
        int length=text.toCharArray().length;
        for(int i=0;i<length;i++) {
            char ch=text.charAt(i);
            if(ch>=256) continue;
            if(ch==32) { width+=3; continue; }
            if(ch==167&&i+1<text.length()) { i++; continue; }
            BitmapGlyph glyph=glyphs[ch];
            if(glyph!=null) width+=glyph.getWidth()+1;
        }
        widthCache.put(text,width-1);
        return width-1; // Includes empty string -> -1, trailing space -> +2.
    }
    @Override public float height() { return scaledSize/scale; }

    private static void requireDefaultPath() { requireDefaultPath(BitmapAtlasData.getSentinel()); }
    private static void requireDefaultPath(int[] captured) {
        if(captured!=null) throw new UnsupportedOperationException("NEEDS_MANUAL_REVIEW: hH non-null hN draw/palette path");
    }
}
