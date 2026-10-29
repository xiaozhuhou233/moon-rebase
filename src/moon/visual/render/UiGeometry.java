package moon.visual.render;
import moon.visual.shader.ShaderUniform.MarkerHooks;
/** Readable ht implementations. Coordinates are GUI units, not framebuffer pixels.
 * Quad order is right/top, left/top, left/bottom, right/bottom.
 * hw/hx/hE require an explicit estimate: their original encrypted estimates remain unresolved.
 * hA also requires its original returned vertex count; layout alone does not decode it.
 * Supplying their actual layout sizes (272/224/72) is a migration choice, not a recovered constant.
 */
public final class UiGeometry {
    private UiGeometry() {
    }
    private static int roundedSentinel;
    private static Object[] glyphSentinel;
    private static String textureSentinel="FEURPb",triangleSentinel,borderSentinel="VGmipc";
    public static String getBorderSentinel() { return borderSentinel; }
    public static void setBorderSentinel(String value) { borderSentinel=value; }
    public static int getRoundedSentinel() {
        return roundedSentinel;
    }
    public static void setRoundedSentinel(int value) {
        roundedSentinel=value;
    }
    public static int roundedInverse() {
        return roundedSentinel==0?48:0;
    }
    public static Object[] getGlyphSentinel() {
        return glyphSentinel;
    }
    public static void setGlyphSentinel(Object[] value) {
        glyphSentinel=value;
    }
    public static String getTextureSentinel() {
        return textureSentinel;
    }
    public static void setTextureSentinel(String value) {
        textureSentinel=value;
    }
    public static String getTriangleSentinel() {
        return triangleSentinel;
    }
    public static void setTriangleSentinel(String value) {
        triangleSentinel=value;
    }
    /** hy: width/height extents, unlike the absolute right/bottom glyph/texture bounds. */
    public static final class Rectangle implements DrawTaskState.Geometry {
        private final float x,y,width,height;
        private final int color;
        public Rectangle(float x,float y,float width,float height,int color) {
            this.x=x;
            this.y=y;
            this.width=width;
            this.height=height;
            this.color=color;
        }
        public int estimatedBytes() {
            return 96;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            float l=x/sw,r=(x+width)/sw,t=y/sh,b=(y+height)/sh;
            w.append(r,t).appendColor(color);
            w.append(l,t).appendColor(color);
            w.append(l,b).appendColor(color);
            w.append(r,b).appendColor(color);
            return 4;
        }
        public float getX() {
            return x;
        }
        public float getY() {
            return y;
        }
        public float getWidth() {
            return width;
        }
        public float getHeight() {
            return height;
        }
        public int getColor() {
            return color;
        }
    }
    /** hA: absolute right/bottom bounds, four 1.5-unit backing strips and four
     * half-unit foreground strips. The 32 vertices occupy 768 bytes; the two
     * encrypted returned integers are independent, required inputs, not defaults. */
    public static final class DoubleBorder implements DrawTaskState.Geometry {
        private final float left,top,right,bottom;
        private final int backing,foreground,estimate,returnedCount;
        private final MarkerHooks markers;
        private DoubleBorder(float l,float t,float r,float b,int backing,int foreground,
                             int originalEstimate,int originalReturnedCount,MarkerHooks markers) {
            left=l;top=t;right=r;bottom=b;this.backing=backing;this.foreground=foreground;
            estimate=originalEstimate;returnedCount=originalReturnedCount;this.markers=markers;
        }
        public static DoubleBorder create(float l,float t,float r,float b,int backing,int foreground,
                                         int originalEstimate,int originalReturnedCount,MarkerHooks markers) {
            getBorderSentinel();
            DoubleBorder value=new DoubleBorder(l,t,r,b,backing,foreground,originalEstimate,originalReturnedCount,markers);
            if(markers.getMarker()!=null)setBorderSentinel("aZfmAb");
            return value;
        }
        public int estimatedBytes() { return estimate; }
        public int write(NativeVertexWriter w,float sw,float sh) {
            float l=left/sw,r=right/sw,t=top/sh,b=bottom/sh;
            vertex(w,l,t,backing);
            String captured=getBorderSentinel(); // After the complete first vertex, before the second.
            vertex(w,l,b,backing);
            vertex(w,l+1.5F/sw,b,backing);
            vertex(w,l+1.5F/sw,t,backing);
            vertex(w,r-1.5F/sw,t,backing);
            vertex(w,r-1.5F/sw,b,backing);
            vertex(w,r,b,backing);
            vertex(w,r,t,backing);
            vertex(w,l+1.5F/sw,t,backing);
            vertex(w,l+1.5F/sw,t+1.5F/sh,backing);
            vertex(w,r-1.5F/sw,t+1.5F/sh,backing);
            vertex(w,r-1.5F/sw,t,backing);
            vertex(w,l+1.5F/sw,b-1.5F/sh,backing);
            vertex(w,l+1.5F/sw,b,backing);
            vertex(w,r-1.5F/sw,b,backing);
            vertex(w,r-1.5F/sw,b-1.5F/sh,backing);
            vertex(w,l+.5F/sw,t+.5F/sh,foreground);
            vertex(w,l+.5F/sw,b-.5F/sh,foreground);
            vertex(w,l+1F/sw,b-.5F/sh,foreground);
            vertex(w,l+1F/sw,t+.5F/sh,foreground);
            vertex(w,r-1F/sw,t+.5F/sh,foreground);
            vertex(w,r-1F/sw,b-.5F/sh,foreground);
            vertex(w,r-.5F/sw,b-.5F/sh,foreground);
            vertex(w,r-.5F/sw,t+.5F/sh,foreground);
            vertex(w,l+.5F/sw,t+.5F/sh,foreground);
            vertex(w,l+.5F/sw,t+1F/sh,foreground);
            vertex(w,r-.5F/sw,t+1F/sh,foreground);
            vertex(w,r-.5F/sw,t+.5F/sh,foreground);
            vertex(w,l+.5F/sw,b-1F/sh,foreground);
            vertex(w,l+.5F/sw,b-.5F/sh,foreground);
            vertex(w,r-.5F/sw,b-.5F/sh,foreground);
            vertex(w,r-.5F/sw,b-1F/sh,foreground);
            if(captured==null)markers.setMarker("CQStr");
            return returnedCount;
        }
        private void vertex(NativeVertexWriter w,float x,float y,int color) { w.append(x,y).appendColor(color); }
    }
    /** hu: fallback glyph, including the -1 UV sentinel and two scalar parameters. */
    public static final class PlaceholderGlyph implements DrawTaskState.Geometry {
        private final float left,top,right,bottom;
        private final int color;
        private final MarkerHooks markers;
        public PlaceholderGlyph(float l,float t,float r,float b,int color,MarkerHooks markers) {
            left=l;
            top=t;
            right=r;
            bottom=b;
            this.color=color;
            this.markers=markers;
        }
        public int estimatedBytes() {
            return 160;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            getGlyphSentinel();
            vertex(w,right/sw,top/sh);
            vertex(w,left/sw,top/sh);
            vertex(w,left/sw,bottom/sh);
            vertex(w,right/sw,bottom/sh);
            if(markers.getMarker()!=null)setGlyphSentinel(new Object[3]);
            return 4;
        }
        private void vertex(NativeVertexWriter w,float x,float y) {
            w.append(x,y).append(-1,-1).append(1F).append(1F).appendColor(color);
        }
    }
    /** hv: top and bottom skew in opposite directions, in normalized GUI X units. */
    public static final class MsdfGlyph implements DrawTaskState.Geometry {
        private final float left,top,right,bottom,u0,v0,u1,v1,parameterI,parameterJ,skew;
        private final int color;
        private final MarkerHooks markers;
        public MsdfGlyph(float l,float t,float r,float b,float u0,float v0,float u1,float v1,float i,float j,float skew,int color,MarkerHooks markers) {
            left=l;
            top=t;
            right=r;
            bottom=b;
            this.u0=u0;
            this.v0=v0;
            this.u1=u1;
            this.v1=v1;
            parameterI=i;
            parameterJ=j;
            this.skew=skew;
            this.color=color;
            this.markers=markers;
        }
        public int estimatedBytes() {
            return 160;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            Object[] captured=getGlyphSentinel();
            float s=skew/sw;
            vertex(w,right/sw+s,top/sh,u1,v0);
            vertex(w,left/sw+s,top/sh,u0,v0);
            vertex(w,left/sw-s,bottom/sh,u0,v1);
            vertex(w,right/sw-s,bottom/sh,u1,v1);
            if(captured!=null)markers.setMarker("fLAMKb");
            return 4;
        }
        private void vertex(NativeVertexWriter w,float x,float y,float u,float v) {
            w.append(x,y).append(u,v).append(parameterI).append(parameterJ).appendColor(color);
        }
    }
    /** hw: layout restored; original estimate is an explicit unresolved input. */
    public static final class GradientRectangle implements DrawTaskState.Geometry {
        private final float x,y,width,height;
        private final int from,to,estimate;
        private final boolean vertical;
        private final MarkerHooks markers;
        public GradientRectangle(float x,float y,float width,float height,int from,int to,boolean vertical,int originalEstimate,MarkerHooks markers) {
            this.x=x;
            this.y=y;
            this.width=width;
            this.height=height;
            this.from=from;
            this.to=to;
            this.vertical=vertical;
            estimate=originalEstimate;
            this.markers=markers;
        }
        public int estimatedBytes() {
            return estimate;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            int captured=roundedInverse();
            float l=x/sw,r=(x+width)/sw,t=y/sh,b=(y+height)/sh;
            vertex(w,r,t,sw,sh);
            vertex(w,l,t,sw,sh);
            vertex(w,l,b,sw,sh);
            vertex(w,r,b,sw,sh);
            if(markers.getMarker()!=null)setRoundedSentinel(captured+1);
            return 4;
        }
        private void vertex(NativeVertexWriter w,float vx,float vy,float sw,float sh) {
            w.append(vx,vy).append(width,height).append(x,y).append(sw,sh).append(vertical?1F:0F).appendColor(from).appendColor(to);
        }
    }
    /** hx: layout restored; original estimate is an explicit unresolved input. */
    public static final class OutlineRectangle implements DrawTaskState.Geometry {
        private final float x,y,width,height,radius,lineWidth;
        private final int color,estimate;
        public OutlineRectangle(float x,float y,float width,float height,float radius,float lineWidth,int color,int originalEstimate) {
            this.x=x;
            this.y=y;
            this.width=width;
            this.height=height;
            this.radius=radius;
            this.lineWidth=lineWidth;
            this.color=color;
            estimate=originalEstimate;
        }
        public int estimatedBytes() {
            return estimate;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            float l=x/sw,r=(x+width)/sw,t=y/sh,b=(y+height)/sh;
            vertex(w,r,t,sw,sh);
            vertex(w,l,t,sw,sh);
            vertex(w,l,b,sw,sh);
            vertex(w,r,b,sw,sh);
            return 4;
        }
        private void vertex(NativeVertexWriter w,float vx,float vy,float sw,float sh) {
            w.append(vx,vy).append(width,height).append(sw,sh).append(x,y).append(radius).append(lineWidth).appendColor(color);
        }
    }
    /** hz: preserves the nonzero-sentinel shortened layout as well as the usual 60-byte vertex. */
    public static final class RoundedRectangle implements DrawTaskState.Geometry {
        private final float x,y,width,height,radius;
        private final int color;
        private final MarkerHooks markers;
        private boolean cornerG=true,cornerH=true,cornerI=true,cornerJ=true,smooth=true;
        public RoundedRectangle(float x,float y,float width,float height,float radius,int color,MarkerHooks markers) {
            this.x=x;
            this.y=y;
            this.width=width;
            this.height=height;
            this.radius=radius;
            this.color=color;
            this.markers=markers;
        }
        public RoundedRectangle cornerG(boolean v) {
            cornerG=v;
            return this;
        }
        public RoundedRectangle cornerH(boolean v) {
            cornerH=v;
            return this;
        }
        public RoundedRectangle cornerI(boolean v) {
            cornerI=v;
            return this;
        }
        public RoundedRectangle cornerJ(boolean v) {
            cornerJ=v;
            return this;
        }
        public RoundedRectangle smooth(boolean v) {
            smooth=v;
            return this;
        }
        public int estimatedBytes() {
            return 240;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            int captured=getRoundedSentinel();
            float ox=x+(smooth?0:.5F),oy=y+(smooth?0:.5F),rw=width-(smooth?0:1),rh=height-(smooth?0:1);
            int mask=captured!=0?(cornerG?1:0):((cornerG?0:1)|(cornerH?0:2)|(cornerI?0:4)|(cornerJ?0:8));
            float l=ox/sw,r=(ox+rw)/sw,t=oy/sh,b=(oy+rh)/sh;
            vertex(w,r,t,sw,sh,ox,oy,rw,rh,mask,captured);
            vertex(w,l,t,sw,sh,ox,oy,rw,rh,mask,captured);
            vertex(w,l,b,sw,sh,ox,oy,rw,rh,mask,captured);
            vertex(w,r,b,sw,sh,ox,oy,rw,rh,mask,captured);
            if(captured!=0)markers.setMarker("eAgNbb");
            return 4;
        }
        private void vertex(NativeVertexWriter w,float vx,float vy,float sw,float sh,float ox,float oy,float rw,float rh,int mask,int captured) {
            w.append(vx,vy).append(rw,rh).append(sw,sh).append(ox,oy).append(radius);
            if(captured==0)w.append((float)mask).append(smooth?1F:0F);
            else w.append((float)mask);
            w.appendColor(color);
        }
    }
    /** hB: no color attribute. */
    public static final class TextureQuad implements DrawTaskState.Geometry {
        private final float left,top,right,bottom,u0,v0,u1,v1;
        public TextureQuad(float l,float t,float r,float b,float u0,float v0,float u1,float v1) {
            left=l;
            top=t;
            right=r;
            bottom=b;
            this.u0=u0;
            this.v0=v0;
            this.u1=u1;
            this.v1=v1;
        }
        public int estimatedBytes() {
            return 64;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            w.append(right/sw,top/sh).append(u1,v0);
            w.append(left/sw,top/sh).append(u0,v0);
            w.append(left/sw,bottom/sh).append(u0,v1);
            w.append(right/sw,bottom/sh).append(u1,v1);
            return 4;
        }
    }
    /** hD. Its static sentinel starts as FEURPb, not null. */
    public static final class ColoredTextureQuad implements DrawTaskState.Geometry {
        private final float left,top,right,bottom,u0,v0,u1,v1;
        private final int color;
        private final MarkerHooks markers;
        public ColoredTextureQuad(float l,float t,float r,float b,float u0,float v0,float u1,float v1,int color,MarkerHooks markers) {
            left=l;
            top=t;
            right=r;
            bottom=b;
            this.u0=u0;
            this.v0=v0;
            this.u1=u1;
            this.v1=v1;
            this.color=color;
            this.markers=markers;
        }
        public int estimatedBytes() {
            return 128;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            getTextureSentinel();
            w.append(right/sw,top/sh).append(u1,v0).appendColor(color);
            w.append(left/sw,top/sh).append(u0,v0).appendColor(color);
            w.append(left/sw,bottom/sh).append(u0,v1).appendColor(color);
            w.append(right/sw,bottom/sh).append(u1,v1).appendColor(color);
            if(markers.getMarker()!=null)setTextureSentinel("KwCwn");
            return 4;
        }
    }
    /** hC. Captures hD sentinel before any vertex writes. */
    public static final class RoundedTextureQuad implements DrawTaskState.Geometry {
        private final float left,top,right,bottom,u0,v0,u1,v1,radius;
        private final int color;
        private final MarkerHooks markers;
        public RoundedTextureQuad(float l,float t,float r,float b,float u0,float v0,float u1,float v1,float radius,int color,MarkerHooks markers) {
            left=l;
            top=t;
            right=r;
            bottom=b;
            this.u0=u0;
            this.v0=v0;
            this.u1=u1;
            this.v1=v1;
            this.radius=radius;
            this.color=color;
            this.markers=markers;
        }
        public int estimatedBytes() {
            return 240;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            String captured=getTextureSentinel();
            vertex(w,right/sw,top/sh,u1,v0,sw,sh);
            vertex(w,left/sw,top/sh,u0,v0,sw,sh);
            vertex(w,left/sw,bottom/sh,u0,v1,sw,sh);
            vertex(w,right/sw,bottom/sh,u1,v1,sw,sh);
            if(captured==null)markers.setMarker("CvSq2");
            return 4;
        }
        private void vertex(NativeVertexWriter w,float x,float y,float u,float v,float sw,float sh) {
            w.append(x,y).append(u,v).append(right-left,bottom-top).append(sw,sh).append(left,top).append(radius).appendColor(color);
        }
    }
    /** hE: no triangle shader is invented here. Original estimate remains an input. */
    public static final class Triangle implements DrawTaskState.Geometry {
        private final float x0,y0,x1,y1,x2,y2;
        private final int color,estimate;
        private final MarkerHooks markers;
        private Triangle(float x0,float y0,float x1,float y1,float x2,float y2,int color,int estimate,MarkerHooks markers) {
            this.x0=x0;
            this.y0=y0;
            this.x1=x1;
            this.y1=y1;
            this.x2=x2;
            this.y2=y2;
            this.color=color;
            this.estimate=estimate;
            this.markers=markers;
        }
        public static Triangle create(float x0,float y0,float x1,float y1,float x2,float y2,int color,int originalEstimate,MarkerHooks markers) {
            getTriangleSentinel();
            Triangle t=new Triangle(x0,y0,x1,y1,x2,y2,color,originalEstimate,markers);
            if(markers.getMarker()!=null)setTriangleSentinel("fghICb");
            return t;
        }
        public int estimatedBytes() {
            return estimate;
        }
        public int write(NativeVertexWriter w,float sw,float sh) {
            w.append(x0/sw,y0/sh).appendColor(color);
            String captured=getTriangleSentinel();
            w.append(x1/sw,y1/sh).appendColor(color);
            w.append(x2/sw,y2/sh).appendColor(color);
            if(captured!=null)markers.setMarker("HFkr7b");
            return 3;
        }
    }
}
