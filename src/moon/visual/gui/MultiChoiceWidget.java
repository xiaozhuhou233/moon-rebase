package moon.visual.gui;

/** km's independently captured component-sentinel paths, including live-map selected/total header count. */
public final class MultiChoiceWidget<T extends Enum<T> & DisplayNamed> extends ChoiceSettingWidget<T> {
    private final MultiChoiceSetting<T> setting;
    public MultiChoiceWidget(Host host,MultiChoiceSetting<T> setting,float x,float y,float width,float height) {
        super(host,x,y,width,height,Kind.MULTI,0);this.setting=setting;initializeOptions();
    }
    protected T[] values() { return setting.values(); }
    protected boolean selected(T option) { return setting.isSelected(option); }
    protected void select(T option) { setting.toggle(option); }
    protected String title() { return setting.getDisplayName(); }
    @Override protected String headerStatus() {
        int count=(int)setting.get().keySet().stream().filter(setting::isSelected).count();return count+"/"+setting.get().size();
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public MultiChoiceSetting<T> getSetting() { return setting; }
}
