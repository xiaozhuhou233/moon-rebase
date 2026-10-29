package moon.visual.gui;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** iU. A callback observes old/new BEFORE the boxed field is committed, even for equal values.
 * Group is an injected i1.isVisible boundary, allowing a destination setting system to adapt it.
 */
public abstract class SettingValue<T> {
    public enum Type { BOOLEAN,COLOR,ENUM,FLOAT,INTEGER,GROUP,MULTIPLE_CHOICE,MODE,STRING,BUTTON,MODULAR_LIST,BEZIER }
    private final String key;
    private T value;
    private String displayName;
    private final Type type;
    private BiConsumer<T,T> callback;
    private Supplier<Boolean> visibility=()->true;
    private String description;
    private boolean grouped;
    private Supplier<Boolean> groupVisibility;
    private static Object[] sentinel=new Object[3];
    // External iW (BooleanSetting) sentinel shared by numeric JSON and choice settings/builders.
    private static Object booleanSettingSentinel;
    protected SettingValue(String key,Type type,MarkerHooks markers) {
        Object[] captured=getSentinel();this.key=key;this.type=type;
        if(captured==null) markers.setMarker("GDEK4b");
    }
    public void set(T value) {
        Object[] captured=getSentinel();
        if(captured!=null&&callback!=null) callback.accept(this.value,value);
        this.value=value;
    }
    public boolean isVisible() {
        Object[] captured=getSentinel();
        if(captured==null) return grouped;
        if(grouped&&!groupVisibility.get()) return false;
        return visibility.get();
    }
    public abstract JsonObject toJson();
    public abstract void fromJson(JsonObject json);
    public String getKey() { return key; }
    public T get() { return value; }
    public String getDisplayName() { return displayName; }
    public Type getType() { return type; }
    public BiConsumer<T,T> getCallback() { return callback; }
    public Supplier<Boolean> getVisibility() { return visibility; }
    public String getDescription() { return description; }
    public boolean isGrouped() { return grouped; }
    public Supplier<Boolean> getGroupVisibility() { return groupVisibility; }
    public SettingValue<T> setDisplayName(String value) { displayName=value;return this; }
    public SettingValue<T> setCallback(BiConsumer<T,T> value) { callback=value;return this; }
    public SettingValue<T> setVisibility(Supplier<Boolean> value) { visibility=value;return this; }
    public SettingValue<T> setDescription(String value) { description=value;return this; }
    public SettingValue<T> setGrouped(boolean value) { grouped=value;return this; }
    public SettingValue<T> setGroupVisibility(Supplier<Boolean> value) { groupVisibility=value;return this; }
    public static Object[] getSentinel() { return sentinel; }
    public static void setSentinel(Object[] value) { sentinel=value; }
    public static Object getBooleanSettingSentinel() { return booleanSettingSentinel; }
    public static void setBooleanSettingSentinel(Object value) { booleanSettingSentinel=value; }
}
