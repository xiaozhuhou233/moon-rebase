package moon.visual.gui;

/** kk's independently captured component-sentinel paths. The mode title has no current-value suffix. */
public final class ModeChoiceWidget<T extends Enum<T> & ModeSetting.Value> extends ChoiceSettingWidget<T> {
    private final ModeSetting<T> setting;
    public ModeChoiceWidget(Host host,ModeSetting<T> setting,float x,float y,float width,float height) {
        super(host,x,y,width,height,Kind.MODE,0);this.setting=setting;initializeOptions();
    }
    protected T[] values() { return setting.values(); }
    protected boolean selected(T option) { return option==setting.get(); }
    protected void select(T option) { setting.set(option); }
    protected String title() { return setting.getDisplayName(); }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public ModeSetting<T> getSetting() { return setting; }
}
