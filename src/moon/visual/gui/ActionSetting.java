package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** iX/jY. An action has no serialized value; its widget invokes the callback directly. */
public final class ActionSetting extends SettingValue<Void> {
    private ActionSetting(String key,MarkerHooks markers) { super(key,Type.BUTTON,markers); }
    public JsonObject toJson() { return null; }
    public void fromJson(JsonObject json) { }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final ActionSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new ActionSetting(key,markers); }
        public Builder title(String title) { setting.setDisplayName(title);return this; }
        public Builder callback(BiConsumer<Void,Void> callback) { setting.setCallback(callback);return this; }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String description) { setting.setDescription(description);return this; }
        public ActionSetting build() { return setting; }
    }
}
