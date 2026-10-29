package moon.visual.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i5/j6 (misnamed IntegerSetting/MultiSelectSetting in the handoff).
 * Individual edits mutate the original HashMap and never dispatch iU's map replacement callback.
 */
public final class MultiChoiceSetting<T extends Enum<T> & DisplayNamed> extends SettingValue<Map<T,Boolean>> {
    private final Class<T> enumClass;
    private MultiChoiceSetting(String key,Class<T> type,MarkerHooks markers) {
        super(key,Type.MULTIPLE_CHOICE,markers);enumClass=type;
        Object captured=getBooleanSettingSentinel();Map<T,Boolean> selected=new HashMap<>();
        for(T value:type.getEnumConstants()) {
            selected.put(value,false);
            // Non-null iW jumps directly to return, without committing the partially built map.
            if(captured!=null) return;
        }
        set(selected);
    }
    public T[] values() { return enumClass.getEnumConstants(); }
    public void toggle(T value) {
        Object captured=getBooleanSettingSentinel();Map<T,Boolean> selected=get();boolean old=selected.get(value);
        selected.put(value,captured!=null?old:!old);
    }
    public void setSelected(T value,boolean selected) { get().put(value,selected); }
    public boolean isSelected(T value) { return get().get(value); }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();JsonArray selected=new JsonArray();json.addProperty("name",getKey());
        get().forEach((key,value)->{if(value) selected.add(new JsonPrimitive(key.name()));});
        json.add("value",selected);return json;
    }
    public void fromJson(JsonObject json) {
        Object captured=getBooleanSettingSentinel();
        if(captured==null) {
            if(!json.has("value")) return;
            get().forEach((key,value)->setSelected(key,false));
        }
        JsonArray array=json.get("value").getAsJsonArray();
        for(JsonElement element:array) {
            String name=element.getAsString();
            get().forEach((key,value)->{if(key.name().equals(name)) setSelected(key,true);});
            if(captured!=null) break;
        }
    }
    public static <T extends Enum<T> & DisplayNamed> Builder<T> builder(String key,Class<T> type,MarkerHooks markers) {
        return new Builder<>(key,type,markers);
    }
    public static final class Builder<T extends Enum<T> & DisplayNamed> {
        private final MultiChoiceSetting<T> setting;
        public Builder(String key,Class<T> type,MarkerHooks markers) { setting=new MultiChoiceSetting<>(key,type,markers); }
        @SafeVarargs public final Builder<T> configure(String title,T... initial) {
            Object captured=getBooleanSettingSentinel();setting.setDisplayName(title);
            // Iterate to preserve the original null-varargs failure even in the nondefault branch.
            for(T value:initial) { if(captured!=null) return this;setting.setSelected(value,true); }
            return this;
        }
        public Builder<T> callback(BiConsumer<Map<T,Boolean>,Map<T,Boolean>> callback) { setting.setCallback(callback);return this; }
        public Builder<T> visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder<T> description(String description) { setting.setDescription(description);return this; }
        public MultiChoiceSetting<T> build() { return setting; }
    }
}
