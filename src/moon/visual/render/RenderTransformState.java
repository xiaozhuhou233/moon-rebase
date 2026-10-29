package moon.visual.render;

/** com.moon.gt global transform/alpha values, including the gv sentinel branches. */
public final class RenderTransformState {
    private static float scaleX=1,scaleY=1,scaleZ=1;
    private static float translateX,translateY,translateZ,angle,axisX,axisY,axisZ;
    private static float alpha=1;
    public RenderTransformState() { }
    public static void setScale(float x,float y,float z) { scaleX=x;scaleY=y;scaleZ=z; }
    public static void setTranslation(float x,float y,float z) { translateX=x;translateY=y;translateZ=z; }
    public static void setRotation(float value,float x,float y,float z) { angle=value;axisX=x;axisY=y;axisZ=z; }
    public static void resetScale() { scaleZ=1;scaleY=1;scaleX=1; }
    public static void resetTranslation() { translateZ=0;translateY=0;translateX=0; }
    public static void resetRotation() { angle=0;axisZ=0;axisY=0;axisX=0; }
    public static void resetAlpha() { alpha=1; }
    public static boolean hasScale() { return DrawTask.getSentinel()!=null?scaleX!=1:scaleX!=1||scaleY!=1||scaleZ!=1; }
    public static boolean hasTranslation() { return DrawTask.getSentinel()!=null?translateX!=0:translateX!=0||translateY!=0||translateZ!=0; }
    public static boolean hasRotation() { return DrawTask.getSentinel()!=null?angle!=0:angle!=0||axisX!=0||axisY!=0||axisZ!=0; }
    public static float getScaleX(){return scaleX;} public static void setScaleX(float v){scaleX=v;}
    public static float getScaleY(){return scaleY;} public static void setScaleY(float v){scaleY=v;}
    public static float getScaleZ(){return scaleZ;} public static void setScaleZ(float v){scaleZ=v;}
    public static float getTranslateX(){return translateX;} public static void setTranslateX(float v){translateX=v;}
    public static float getTranslateY(){return translateY;} public static void setTranslateY(float v){translateY=v;}
    public static float getTranslateZ(){return translateZ;} public static void setTranslateZ(float v){translateZ=v;}
    public static float getAngle(){return angle;} public static void setAngle(float v){angle=v;}
    public static float getAxisX(){return axisX;} public static void setAxisX(float v){axisX=v;}
    public static float getAxisY(){return axisY;} public static void setAxisY(float v){axisY=v;}
    public static float getAxisZ(){return axisZ;} public static void setAxisZ(float v){axisZ=v;}
    public static float getAlpha(){return alpha;} public static void setAlpha(float v){alpha=v;}
}
