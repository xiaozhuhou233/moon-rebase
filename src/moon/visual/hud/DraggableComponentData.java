package moon.visual.hud;

import com.google.gson.annotations.SerializedName;
import java.util.function.Consumer;

/** fY: mutable x/y persistence data, including virtual getter/canEqual order,
 * signed-zero/NaN equality and the independent fZ sentinel branches.
 * The hash multiplier and text are confirmed by supplied runtime seed evidence. */
public class DraggableComponentData {
    @SerializedName("x") private float x;
    @SerializedName("y") private float y;
    private static Consumer<String> externalMarkerWriter;

    public DraggableComponentData(){}
    public DraggableComponentData(float x,float y){this.x=x;this.y=y;}
    public float getX(){return x;}public float getY(){return y;}
    public DraggableComponentData setX(float value){x=value;return this;}
    public DraggableComponentData setY(float value){y=value;return this;}
    protected boolean canEqual(Object other){return other instanceof DraggableComponentData;}
    @Override public boolean equals(Object value){
        Object[] captured=DraggableComponentProperties.getSentinel();
        if(captured==null){
            if(value==this)return true;
            if(!(value instanceof DraggableComponentData))return false;
        }
        DraggableComponentData other=(DraggableComponentData)value;
        boolean compatible=other.canEqual(this);
        if(captured!=null)return compatible;
        return compatible&&Float.compare(getX(),other.getX())==0&&Float.compare(getY(),other.getY())==0;
    }
    @Override public int hashCode(){
        // Original also decodes an unused 59 before capturing fZ. Both multiplier
        // calls decode 59; no guessed Lombok constant or raw-NaN-bit replacement.
        Object[] captured=DraggableComponentProperties.getSentinel();
        int hash=59+Float.floatToIntBits(getX());
        hash=hash*59+Float.floatToIntBits(getY());
        if(captured!=null){
            Consumer<String> writer=externalMarkerWriter;
            if(writer==null)throw new IllegalStateException("Configure the fY r2 marker writer before using the nonnull fZ branch");
            writer.accept("Xxx1nc");
        }
        return hash;
    }
    @Override public String toString(){return "DraggableComponentData(x="+getX()+", y="+getY()+")";}
    /** Bind to the destination's shared r2 marker; null explicitly unconfigures
     * this host boundary. No marker writes are silently discarded. */
    public static void setExternalMarkerWriter(Consumer<String> writer){externalMarkerWriter=writer;}
    public static Consumer<String> getExternalMarkerWriter(){return externalMarkerWriter;}
}
