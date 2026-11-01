package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i0 and j1. rawValue is deliberately separate from the ceil-quantized boxed value. */
public final class FloatSetting extends SettingValue<Float> {
    private float rawValue,minimum,maximum,step;
    public FloatSetting(String key,MarkerHooks markers) { super(key,Type.FLOAT,markers); }
    public float getRawValue() { return rawValue; }
    public void setRawValue(float value) { rawValue=value; }
    public float getMinimum() { return minimum; }
    public void setMinimum(float value) { minimum=value; }
    public float getMaximum() { return maximum; }
    public void setMaximum(float value) { maximum=value; }
    public float getStep() { return step; }
    public void setStep(float value) { step=value; }
    @Override public void set(Float value) {
        value=NumericSettingMath.clamp(value,minimum,maximum);
        double quantized=NumericSettingMath.round(NumericSettingMath.clamp(step*Math.ceil(value/step),(double)minimum,(double)maximum),3);
        setRawValue(value.floatValue());super.set((float)quantized);
        if(Float.compare(get(),(float)quantized)!=0)setRawValue(get());
    }
    @Override public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get());return json;
    }
    @Override public void fromJson(JsonObject json) {
        Object captured=getBooleanSettingSentinel();
        if(captured==null) {
            if(!json.has("value")) return;
            setRawValue(json.get("value").getAsFloat());
        }
        set(Float.valueOf(json.get("value").getAsFloat()));
    }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final FloatSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new FloatSetting(key,markers); }
        public Builder configure(String title,float initial,float minimum,float maximum,float step) {
            setting.setDisplayName(title);setting.setMinimum(minimum);setting.setMaximum(maximum);setting.setStep(step);
            setting.set(Float.valueOf(initial));setting.setRawValue(initial);return this;
        }
        public Builder callback(BiConsumer<Float,Float> value) { setting.setCallback(value);return this; }
        public Builder visibility(Supplier<Boolean> value) { setting.setVisibility(value);return this; }
        public Builder description(String value) { setting.setDescription(value);return this; }
        public FloatSetting build() { return setting; }
    }
}
