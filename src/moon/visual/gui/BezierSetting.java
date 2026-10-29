package moon.visual.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** iV/jW. Float Bernstein arithmetic, live point arrays and the original sampled X bins.
 * Numeric table: 200000 samples, 101 count/result bins, confirmed by the supplied iV seed.
 * Field setters rebuild independently of iU's nullable boxed value/callback.
 */
public final class BezierSetting extends SettingValue<Float> {
    private float parameterK,parameterL,begin,end;
    private float[][] points;
    private float[] lookup;
    private final MarkerHooks markers;
    private BezierSetting(String key,MarkerHooks markers) { super(key,Type.BEZIER,markers);this.markers=markers; }
    public void setParameterK(float value) { parameterK=value;rebuild(); }
    public void setParameterL(float value) { parameterL=value;rebuild(); }
    public void setBegin(float value) { begin=value;rebuild(); }
    public void setEnd(float value) { end=value;rebuild(); }
    public void setPoints(float[]... value) { points=value;rebuild(); }
    public float getParameterK() { return parameterK; }
    public float getParameterL() { return parameterL; }
    public float getBegin() { return begin; }
    public float getEnd() { return end; }
    public float[][] getPoints() { return points; }
    public float[] getLookup() { return lookup; }

    public float[] sample(float t) {
        Object captured=getBooleanSettingSentinel();
        float u=1F-t,tt=t*t,uu=u*u,uuu=uu*u,ttt=tt*t;
        double x=3F*uu*t*points[0][0]+3F*u*tt*points[1][0]+ttt;
        double y=uuu*begin+3F*uu*t*points[0][1]+3F*u*tt*points[1][1]+ttt*end;
        float[] result={(float)x,(float)y};
        if(captured!=null) markers.setMarker("tSzWyb");
        return result;
    }
    private void rebuild() {
        Object captured=getBooleanSettingSentinel();
        float[][] samples=points;
        if(captured==null) {
            if(points==null) return;
            samples=new float[200000][2];
        }
        for(int i=0;i<samples.length;i++) {
            samples[i]=sample(i/(float)samples.length);
            if(captured!=null) break;
        }
        int[] counts=new int[101];float[] bins=new float[101];
        for(float[] point:samples) {
            if(captured!=null) return;
            // dcmpl > 0 / dcmpg < 0 allow NaN through; f2i puts it in bin zero.
            if(point[0]>1D||point[0]<0D) continue;
            float y=point[1];int bin=(int)(point[0]*100F);
            counts[bin]++;
            bins[bin]+=(y-bins[bin])/counts[bin];
        }
        lookup=bins;
    }
    public JsonObject toJson() {
        Object captured=getBooleanSettingSentinel();
        JsonObject json=new JsonObject();json.addProperty("name",getKey());
        json.addProperty("begin",begin);json.addProperty("end",end);
        JsonArray array=new JsonArray();boolean append=true;
        for(float[] point:points) {
            JsonObject entry=new JsonObject();entry.addProperty("x",point[0]);entry.addProperty("y",point[1]);array.add(entry);
            if(captured!=null) { append=false;break; }
        }
        if(append) json.add("points",array);
        if(markers.getMarker()!=null) setBooleanSettingSentinel(new int[5]);
        return json;
    }
    public void fromJson(JsonObject json) {
        Object captured=getBooleanSettingSentinel();
        if(!json.has("begin")) return;
        if(captured==null&&(!json.has("end")||!json.has("points"))) return;
        setBegin(json.get("begin").getAsFloat());
        setEnd(json.get("end").getAsFloat());
        JsonArray array=json.get("points").getAsJsonArray();float[][] parsed=new float[2][2];
        for(int i=0;i<array.size();i++) {
            JsonObject entry=array.get(i).getAsJsonObject();
            parsed[i]=new float[]{entry.get("x").getAsFloat(),entry.get("y").getAsFloat()};
            if(captured!=null) return;
        }
        setPoints(parsed);
    }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final BezierSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new BezierSetting(key,markers); }
        public Builder configure(String title,float begin,float end) {
            setting.setDisplayName(title);setting.setBegin(begin);setting.setEnd(end);return this;
        }
        public Builder points(float[]... points) { setting.setPoints(points);return this; }
        public Builder callback(BiConsumer<Float,Float> callback) { setting.setCallback(callback);return this; }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String description) { setting.setDescription(description);return this; }
        public BezierSetting build() { return setting; }
    }
}
