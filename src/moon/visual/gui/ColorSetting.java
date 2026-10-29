package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** iY and jZ: signed ARGB integer, with the original iU callback-before-commit semantics. */
public final class ColorSetting extends SettingValue<Integer> {
    private ColorSetting(String key,MarkerHooks markers) { super(key,Type.COLOR,markers); }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get());return json;
    }
    public void fromJson(JsonObject json) { if(json.has("value")) set(json.get("value").getAsInt()); }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final ColorSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new ColorSetting(key,markers); }
        public Builder configure(String title,int initial) { setting.setDisplayName(title);setting.set(initial);return this; }
        public Builder callback(BiConsumer<Integer,Integer> callback) { setting.setCallback(callback);return this; }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String value) { setting.setDescription(value);return this; }
        public ColorSetting build() { return setting; }
    }
}
