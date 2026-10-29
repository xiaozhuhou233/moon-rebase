package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.Function;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** iZ/j0: plain enum setting. Its callback is the inherited iU callback, before boxed commit.
 * Persisted keys name/value are confirmed by the supplied iZ runtime string capture.
 */
public final class EnumSetting<T extends Enum<T> & DisplayNamed> extends SettingValue<T> {
    Class<T> enumClass;
    private Function<T,String> labels=DisplayNamed::displayName;
    private EnumSetting(String key,Class<T> type,MarkerHooks markers) {
        super(key,Type.ENUM,markers);enumClass=type;
    }
    public T[] values() { return enumClass.getEnumConstants(); }
    /** Host boundary for enum labels whose encrypted original strings are unavailable. */
    public String displayName(T option) { return labels.apply(option); }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get().ordinal());return json;
    }
    public void fromJson(JsonObject json) { if(json.has("value")) set(values()[json.get("value").getAsInt()]); }
    public static <T extends Enum<T> & DisplayNamed> Builder<T> builder(String key,Class<T> type,MarkerHooks markers) {
        return new Builder<>(key,type,markers);
    }
    public static final class Builder<T extends Enum<T> & DisplayNamed> {
        private final EnumSetting<T> setting;
        public Builder(String key,Class<T> type,MarkerHooks markers) { setting=new EnumSetting<>(key,type,markers); }
        public Builder<T> configure(String title,T initial) { setting.setDisplayName(title);setting.set(initial);return this; }
        public Builder<T> callback(BiConsumer<T,T> callback) { setting.setCallback(callback);return this; }
        public Builder<T> visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder<T> description(String description) { setting.setDescription(description);return this; }
        public Builder<T> labels(Function<T,String> labels) { setting.labels=labels;return this; }
        public EnumSetting<T> build() { return setting; }
    }
}
