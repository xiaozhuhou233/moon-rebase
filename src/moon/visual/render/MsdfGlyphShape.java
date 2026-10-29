package moon.visual.render;

import java.util.HashMap;
import java.util.Map;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** hY's captured constructor and quad guards. Encrypted shadow colors are explicit
 * inputs. Nondefault quads select color with the carried shadow flag; cached bounds stay cached.
 */
public final class MsdfGlyphShape implements CachedMsdfMetrics.Glyph {
    private static boolean sentinel=true;
    private final Map<Character,Float> kerningCache=new HashMap<>();
    private MsdfFontData.Glyph glyph;
    private MsdfFontData.Metrics metrics;
    private final float uvTop,uvLeft,uvBottom,uvRight,planeWidth,planeHeight;
    private float advance,fontSize,bearingTop,parameter;
    public MsdfGlyphShape(MsdfFontData.Glyph glyph,MsdfFontData.Atlas atlas,MsdfFontData.Metrics metrics) {
        boolean captured=sentinel;
        advance=glyph.getAdvance();
        MsdfFontData.Bounds bounds=glyph.getAtlasBounds();
        float left=0,top=0,right=0,bottom=0,width=0,height=0,bearing=0;
        if(!captured||bounds!=null) {
            // A nondefault null atlas-bounds value is dereferenced, as at hY 106.
            left=bounds.getLeft();top=bounds.getTop();right=bounds.getRight();bottom=bounds.getBottom();
            MsdfFontData.Bounds plane=glyph.getPlaneBounds();
            if(captured&&plane!=null) {
                width=plane.getRight()-plane.getLeft();height=plane.getTop()-plane.getBottom();bearing=plane.getTop();
            }
        }
        planeWidth=width;planeHeight=height;bearingTop=bearing;
        float atlasWidth=atlas.getWidth(),atlasHeight=atlas.getHeight();
        uvTop=1-top/atlasHeight;uvLeft=left/atlasWidth;
        uvBottom=1-bottom/atlasHeight;uvRight=right/atlasWidth;
        this.glyph=glyph;this.metrics=metrics;
    }
    public UiGeometry.MsdfGlyph quad(float x,float y,float smooth,float boldWeight,float offset,float weight,
                                     boolean shadow,FontRenderContext context,int noPlaneShadow,int planeShadow,MarkerHooks markers) {
        boolean captured=sentinel;
        y=y+metrics.baseline(markers)*fontSize-bearingTop*fontSize;
        MsdfFontData.Bounds plane=glyph.getPlaneBounds();
        float adjustment=context.getStyles().isBold()?-1+boldWeight:weight+offset;
        float skew=context.getStyles().isItalic()?1:0;
        int color;
        if(!captured) color=shadow?context.getRenderedColor():context.getInitialColor();
        else color=shadow?(plane==null?noPlaneShadow:planeShadow)
            :(context.isApplyColorCodes()?context.getRenderedColor():context.getInitialColor());
        UiGeometry.MsdfGlyph result=new UiGeometry.MsdfGlyph(
            plane==null?x:x+plane.getLeft()*fontSize,y,
            plane==null?x+planeWidth*fontSize:x+plane.getRight()*fontSize,y+planeHeight*fontSize,
            uvLeft,uvTop,uvRight,uvBottom,smooth,adjustment,skew,color,markers);
        if(plane!=null&&markers.getMarker()!=null) sentinel=!captured;
        return result;
    }
    public void cacheKerning(char previous,float value) { kerningCache.put(previous,value); }
    public float cachedKerning(char previous) { return kerningCache.getOrDefault(previous,0F); }
    public Map<Character,Float> getKerningCache() { return kerningCache; }
    public MsdfFontData.Glyph getGlyph() { return glyph; }
    public MsdfFontData.Metrics getMetrics() { return metrics; }
    public float getUvTop() { return uvTop; }
    public float getUvLeft() { return uvLeft; }
    public float getUvBottom() { return uvBottom; }
    public float getUvRight() { return uvRight; }
    public float getPlaneWidth() { return planeWidth; }
    public float planeHeight() { return planeHeight; }
    public float advance() { return advance; }
    public float getFontSize() { return fontSize; }
    public float getBearingTop() { return bearingTop; }
    public float getParameter() { return parameter; }
    public MsdfGlyphShape setGlyph(MsdfFontData.Glyph value) { glyph=value;return this; }
    public MsdfGlyphShape setMetrics(MsdfFontData.Metrics value) { metrics=value;return this; }
    public MsdfGlyphShape setAdvance(float value) { advance=value;return this; }
    public MsdfGlyphShape setFontSize(float value) { fontSize=value;return this; }
    public MsdfGlyphShape setBearingTop(float value) { bearingTop=value;return this; }
    public MsdfGlyphShape setParameter(float value) { parameter=value;return this; }
    public static boolean getSentinel() { return sentinel; }
    public static void setSentinel(boolean value) { sentinel=value; }
    public static boolean inverse() { return !sentinel; }
}
