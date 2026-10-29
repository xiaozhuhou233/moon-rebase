package moon.visual.gui;

/** kh: pre-super inverse capture; its wrapping loop never measures font height. */
public final class EnumChoiceWidget<T extends Enum<T> & DisplayNamed> extends ChoiceSettingWidget<T> {
    private final EnumSetting<T> setting;
    public EnumChoiceWidget(Host host,EnumSetting<T> setting,float x,float y,float width,float height) {
        this(host,setting,x,y,width,height,inverseComponentSentinel());
    }
    private EnumChoiceWidget(Host host,EnumSetting<T> setting,float x,float y,float width,float height,int captured) {
        super(host,x,y,width,height,Kind.ENUM,captured);this.setting=setting;initializeOptions();
    }
    protected T[] values() { return setting.values(); }
    protected boolean selected(T option) { return option==setting.get(); }
    protected void select(T option) { setting.set(option); }
    protected String title() { return setting.getDisplayName(); }
    @Override protected String optionLabel(T option) { return setting.displayName(option); }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public EnumSetting<T> getSetting() { return setting; }
}
