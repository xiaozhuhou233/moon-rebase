package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i3/j4 (misnamed DoubleSetting in the handoff). Event and accessor objects are host boundaries.
 * Builder callback is secondary; replacing the inherited callback still replaces the internal switch.
 */
public final class ModeSetting<T extends Enum<T> & ModeSetting.Value> extends SettingValue<T> {
    public interface Value extends DisplayNamed { Object moduleAccessor(); }
    public interface EventBus { void unsubscribe(Object accessor);void subscribe(Object accessor); }
    public interface Host { boolean moduleEnabled();EventBus eventBus(); }
    private BiConsumer<T,T> secondaryCallback;
    private final Class<T> enumClass;
    private ModeSetting(String key,Class<T> enumClass,Host host,MarkerHooks markers) {
        super(key,Type.MODE,markers);this.enumClass=enumClass;
        setCallback((oldValue,newValue)->{
            Object captured=getBooleanSettingSentinel();
            if(host.moduleEnabled()) {
                EventBus bus=host.eventBus();
                bus.unsubscribe(oldValue.moduleAccessor());
                bus.subscribe(newValue.moduleAccessor());
            }
            if(captured!=null||secondaryCallback!=null) secondaryCallback.accept(oldValue,newValue);
        });
    }
    public T[] values() { return enumClass.getEnumConstants(); }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());json.addProperty("value",get().ordinal());return json;
    }
    public void fromJson(JsonObject json) { if(json.has("value")) set(values()[json.get("value").getAsInt()]); }
    public static <T extends Enum<T> & Value> Builder<T> builder(String key,Class<T> type,Host host,MarkerHooks markers) {
        return new Builder<>(key,type,host,markers);
    }
    public static final class Builder<T extends Enum<T> & Value> {
        private final ModeSetting<T> setting;
        public Builder(String key,Class<T> type,Host host,MarkerHooks markers) { setting=new ModeSetting<>(key,type,host,markers); }
        public Builder<T> configure(String title,T initial) { setting.setDisplayName(title);setting.set(initial);return this; }
        public Builder<T> callback(BiConsumer<T,T> callback) { setting.secondaryCallback=callback;return this; }
        public Builder<T> visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder<T> description(String description) { setting.setDescription(description);return this; }
        public ModeSetting<T> build() { return setting; }
    }
}
