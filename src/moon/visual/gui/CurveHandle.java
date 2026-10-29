package moon.visual.gui;

import java.util.function.Consumer;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** ky. Point is the x/y callback boundary of le; le's unrelated vector API is not restored here. */
public final class CurveHandle {
    public static final class Point {
        public float x,y;
        public Point(float x,float y) { this.x=x;this.y=y; }
    }
    private final Consumer<Point> callback;
    private final boolean movableX;
    private final MarkerHooks markers;
    private float x,y;
    private static String[] sentinel=new String[1];
    public CurveHandle(Consumer<Point> callback,boolean movableX,MarkerHooks markers) {
        this.callback=callback;this.movableX=movableX;this.markers=markers;
    }
    public void apply() {
        getSentinel();callback.accept(new Point(x,y));
        if(markers.getMarker()!=null) setSentinel(new String[1]);
    }
    public boolean hit(int mouseX,int mouseY) {
        String[] captured=getSentinel();boolean result;
        float left=x-10F,top=y-10F;
        if(getSentinel()==null) {
            int comparison=Float.isNaN(left)?-1:mouseX>left?1:mouseX==left?0:-1;
            result=(comparison&1)!=0;
        } else result=mouseX>=left&&mouseX<=left+20F&&mouseY>=top&&mouseY<=top+20F;
        if(captured==null) markers.setMarker("HXjlxc");
        return result;
    }
    public Consumer<Point> getCallback() { return callback; }
    public boolean isMovableX() { return movableX; }
    public float getX() { return x; }
    public float getY() { return y; }
    public CurveHandle setX(float value) { x=value;return this; }
    public CurveHandle setY(float value) { y=value;return this; }
    public static String[] getSentinel() { return sentinel; }
    public static void setSentinel(String[] value) { sentinel=value; }
}
