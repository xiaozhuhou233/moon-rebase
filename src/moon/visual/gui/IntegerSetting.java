package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i2 and j3; this is not the erroneous handoff file named IntegerSetting (actual i5). */
public final class IntegerSetting extends SettingValue<Integer> {
    private float rawValue;
    private int minimum,maximum,step;
    private IntegerSetting(String key,MarkerHooks markers) { super(key,Type.INTEGER,markers); }
    public float getRawValue() { return rawValue; }
    public void setRawValue(float value) { rawValue=value; }
    public int getMinimum() { return minimum; }
    public void setMinimum(int value) { minimum=value; }
    public int getMaximum() { return maximum; }
    public void setMaximum(int value) { maximum=value; }
    public int getStep() { return step; }
    public void setStep(int value) { step=value; }
    @Override public void set(Integer value) {
        value=NumericSettingMath.clamp(value,minimum,maximum);
        int quantized=(int)NumericSettingMath.round(NumericSettingMath.clamp(step*Math.ceil(value.floatValue()/step),(double)minimum,(double)maximum),2);
        setRawValue((float)value.intValue());super.set(quantized);
    }
    public void setFromDrag(float value) {
        value=NumericSettingMath.clamp(value,(float)minimum,(float)maximum);
        int quantized=(int)NumericSettingMath.round(NumericSettingMath.clamp(step*Math.ceil(value/step),(double)minimum,(double)maximum),2);
        setRawValue(value);super.set(quantized);
    }
    @Override public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get());return json;
    }
    @Override public void fromJson(JsonObject json) {
        Object captured=getBooleanSettingSentinel();
        if(captured==null) {
            if(!json.has("value")) return;
            setFromDrag((float)json.get("value").getAsInt());
        }
        setRawValue((float)json.get("value").getAsInt());
    }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final IntegerSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new IntegerSetting(key,markers); }
        public Builder configure(String title,int initial,int minimum,int maximum,int step) {
            setting.setDisplayName(title);setting.setMinimum(minimum);setting.setMaximum(maximum);setting.setStep(step);
            setting.setFromDrag((float)initial);setting.setRawValue((float)initial);return this;
        }
        public Builder callback(BiConsumer<Integer,Integer> value) { setting.setCallback(value);return this; }
        public Builder visibility(Supplier<Boolean> value) { setting.setVisibility(value);return this; }
        public Builder description(String value) { setting.setDescription(value);return this; }
        public IntegerSetting build() { return setting; }
    }
}
