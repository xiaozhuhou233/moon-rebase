package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i6/j7. Runtime-supplied keys name/value; callback-before-commit comes from iU. */
public final class StringSetting extends SettingValue<String> {
    private StringSetting(String key,MarkerHooks markers) { super(key,Type.STRING,markers); }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get());return json;
    }
    public void fromJson(JsonObject json) { if(json.has("value")) set(json.get("value").getAsString()); }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final StringSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new StringSetting(key,markers); }
        public Builder configure(String title,String initial) { setting.setDisplayName(title);setting.set(initial);return this; }
        public Builder callback(BiConsumer<String,String> callback) { setting.setCallback(callback);return this; }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String description) { setting.setDescription(description);return this; }
        public StringSetting build() { return setting; }
    }
}
