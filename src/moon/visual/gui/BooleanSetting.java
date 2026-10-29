package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** iW/jX. The nullable boxed value and iU callback-before-commit are preserved. */
public final class BooleanSetting extends SettingValue<Boolean> {
    private BooleanSetting(String key,MarkerHooks markers) { super(key,Type.BOOLEAN,markers); }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get());return json;
    }
    public void fromJson(JsonObject json) { if(json.has("value")) set(json.get("value").getAsBoolean()); }
    /** The iW field is shared with the previously restored numeric/choice sentinel boundary. */
    public static void setObfuscationSentinel(int[] value) { setBooleanSettingSentinel(value); }
    public static int[] getObfuscationSentinel() { return (int[])getBooleanSettingSentinel(); }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final BooleanSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new BooleanSetting(key,markers); }
        public Builder configure(String title,boolean initial) { setting.setDisplayName(title);setting.set(initial);return this; }
        public Builder callback(BiConsumer<Boolean,Boolean> callback) { setting.setCallback(callback);return this; }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String description) { setting.setDescription(description);return this; }
        public BooleanSetting build() { return setting; }
    }
}
