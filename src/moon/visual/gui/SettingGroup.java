package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i1/j2. List.of semantics represented as an immutable, null-rejecting snapshot for Java8
 * compatibility. Parent visibility is the existing iU injected group-visibility boundary.
 */
public final class SettingGroup extends SettingValue<List<SettingValue<?>>> {
    private SettingGroup(MarkerHooks markers) { super("",Type.GROUP,markers); }
    public JsonObject toJson() { return null; }
    public void fromJson(JsonObject json) { }
    public static Builder builder(MarkerHooks markers) { return new Builder(markers); }
    public static final class Builder {
        private final SettingGroup setting;
        public Builder(MarkerHooks markers) { setting=new SettingGroup(markers); }
        public Builder configure(String title,SettingValue<?>... children) {
            Object captured=getBooleanSettingSentinel();setting.setDisplayName(title);
            SettingValue<?>[] copy=children.clone();
            for(SettingValue<?> child:copy) Objects.requireNonNull(child);
            setting.set(Collections.unmodifiableList(Arrays.asList(copy)));
            for(SettingValue<?> child:children) {
                child.setGrouped(true);child.setGroupVisibility(setting::isVisible);
                if(captured!=null) break;
            }
            return this;
        }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String description) { setting.setDescription(description);return this; }
        public SettingGroup build() { return setting; }
    }
}
