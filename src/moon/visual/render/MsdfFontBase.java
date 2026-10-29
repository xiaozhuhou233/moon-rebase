package moon.visual.render;
import moon.visual.shader.ShaderUniform.MarkerHooks;
/** hT entry points, captured-guard trim and nine-pass outline.
 * Four format characters retain the existing offline-decoded mapping; runtime seed is unconfirmed.
 */
public abstract class MsdfFontBase {
    private static int sentinel;
    private final MarkerHooks markers;
    protected MsdfFontBase(MarkerHooks markers) {
        this.markers=markers;
    }
    public abstract void draw(DrawTask task,String text,float size,float x,float y,int color,boolean flag1,boolean flag2,float offset);
    public abstract void bind();
    public abstract float width(String text,float size);
    public abstract float secondMetric(String text,float size);
    public abstract float height(float size);
    public void draw(DrawTask task,String text,float size,float x,float y,int color) {
        draw(task,text,size,x,y,color,true,true,0);
    }
    public void draw(DrawTask task,String text,float size,float x,float y,int color,boolean flag) {
        draw(task,text,size,x,y,color,flag,false,0);
    }
    public void drawSinglePass(DrawTask task,String text,float size,float x,float y,int color) {
        draw(task,text,size,x,y,color,true,false,0);
    }
    public void drawOutline(DrawTask task,String text,float size,float x,float y,int color) {
        draw(task,text,size,x-.5F,y-.5F,0xff000000,false);
        int captured=getSentinel();
        draw(task,text,size,x+.5F,y+.5F,0xff000000,false);
        draw(task,text,size,x+.5F,y-.5F,0xff000000,false);
        draw(task,text,size,x-.5F,y+.5F,0xff000000,false);
        draw(task,text,size,x,y-.5F,0xff000000,false);
        draw(task,text,size,x,y+.5F,0xff000000,false);
        draw(task,text,size,x-.5F,y,0xff000000,false);
        draw(task,text,size,x+.5F,y,0xff000000,false);
        draw(task,text,size,x,y,color,true);
        if(captured!=0)markers.setMarker("m2npjb");
    }
    /** JVM char iteration, including negative-width format markers; no Unicode codepoint rewrite. */
    public String trim(String text,int limit,float size,boolean reverse) {
        int captured=inverse();
        if(captured==0) {
            // The original carries reverse through index/step and into the char slot.
            // It still calls width once, even for null text or a nonpositive limit.
            char carried=(char)(reverse?1:0);
            width(String.valueOf(carried),size);
            return String.valueOf(carried);
        }
        StringBuilder result=new StringBuilder();
        float used=0;
        boolean formatting=false,bold=false;
        for(int i=reverse?text.length()-1:0,step=reverse?-1:1;i>=0&&i<text.length()&&used<limit;i+=step) {
            char ch=text.charAt(i);
            float advance=width(String.valueOf(ch),size);
            if(formatting) {
                formatting=false;
                if(ch=='l'||ch=='L')bold=true;
                else if(ch=='r'||ch=='R')bold=false;
            }
            else if(advance<0)formatting=true;
            else {
                used+=advance;
                if(bold)used++;
            }
            if(used>limit)break;
            if(reverse)result.insert(0,ch);
            else result.append(ch);
        }
        return result.toString();
    }
    public static int getSentinel() {
        return sentinel;
    }
    public static void setSentinel(int value) {
        sentinel=value;
    }
    public static int inverse() {
        return getSentinel()==0?109:0;
    }
}
