package moon.module;

import java.util.List;
import moon.visual.gui.ModuleRowWidget;
import moon.visual.gui.SettingValue;

/** Destination module lifecycle, independent of the old native bootstrap. */
public abstract class NativeModule implements ModuleRowWidget.Module, AutoCloseable {
    public enum Category { COMBAT, PLAYER, EXPLOIT }
    private final String key, name;
    private final Category category;
    private boolean enabled;

    protected NativeModule(String key, String name, Category category) {
        this.key = key; this.name = name; this.category = category;
    }
    public final String key() { return key; }
    @Override public final String bindId(){return key;}
    public final String displayName() { return name; }
    public final Category category() { return category; }
    public final boolean enabled() { return enabled; }
    public final void toggle() { setEnabled(!enabled); }
    public final void setEnabled(boolean value) {
        if (enabled == value) return;
        if (value) {
            onEnable();
            enabled = true;
        } else {
            enabled = false;
            onDisable();
        }
    }
    public abstract List<? extends SettingValue<?>> settings();
    protected void onEnable() { }
    protected void onDisable() { }
    /** Clear world-owned state without changing the user's enabled preference. */
    public abstract void worldChanged();
    public final void close() { setEnabled(false); worldChanged(); }
}
