package moon.visual.gui;

import java.util.function.LongSupplier;

/** Shared migration constructor table for kn and kc module rows.
 * No fallback widget or implicit substitute for unknown original text/toggle constants.
 */
public final class SettingWidgetFactory implements GroupSettingWidget.Factory {
    public interface Scheme extends ColorSettingWidget.Scheme,BooleanSettingWidget.Scheme,
            ActionSettingWidget.Scheme,BezierSettingWidget.Scheme,ModularListSettingWidget.Scheme { }
    public interface Drawing extends ColorSettingWidget.Drawing,BooleanSettingWidget.Drawing,ChoiceSettingWidget.Drawing { }
    public interface Host extends GroupSettingWidget.Host,ColorSettingWidget.Host,BooleanSettingWidget.Host,
            ActionSettingWidget.Host,BezierSettingWidget.Host,ModularListSettingWidget.Host {
        @Override Scheme uiColor(int argb);
        @Override Drawing drawing();
    }
    private final Host host;
    private final BooleanSettingWidget.Parameters booleanParameters;
    private final StringSettingWidget.Parameters textParameters;
    private final LongSupplier clock;
    public SettingWidgetFactory(Host host,BooleanSettingWidget.Parameters booleanParameters,
                                StringSettingWidget.Parameters textParameters,LongSupplier clock) {
        this.host=host;this.booleanParameters=booleanParameters;this.textParameters=textParameters;this.clock=clock;
    }
    public SettingWidget create(SettingValue<?> setting,float x,float y,float width) {
        return create(setting,x,y,width,GroupSettingWidget.originalInitialHeight(setting.getType()));
    }
    @Override @SuppressWarnings({"rawtypes","unchecked"})
    public SettingWidget create(SettingValue<?> setting,float x,float y,float width,float height) {
        switch(setting.getType()) {
            case BOOLEAN:return new BooleanSettingWidget(host,(BooleanSetting)setting,x,y,width,height,booleanParameters,clock);
            case COLOR:return new ColorSettingWidget(host,(ColorSetting)setting,x,y,width,height);
            case ENUM:return new EnumChoiceWidget(host,(EnumSetting)setting,x,y,width,height);
            case FLOAT:return new FloatSliderWidget(host,(FloatSetting)setting,x,y,width,height);
            case INTEGER:return new IntegerSliderWidget(host,(IntegerSetting)setting,x,y,width,height);
            case MULTIPLE_CHOICE:return new MultiChoiceWidget(host,(MultiChoiceSetting)setting,x,y,width,height);
            case MODE:return new ModeChoiceWidget(host,(ModeSetting)setting,x,y,width,height);
            case STRING:return new StringSettingWidget(host,(StringSetting)setting,x,y,width,height,textParameters,clock);
            case GROUP:return new GroupSettingWidget(host,(SettingGroup)setting,x,y,width,height,this);
            case BUTTON:return new ActionSettingWidget(host,(ActionSetting)setting,x,y,width,height);
            case MODULAR_LIST:return new ModularListSettingWidget(host,(ModularListSetting)setting,x,y,width,height,clock);
            case BEZIER:return new BezierSettingWidget(host,(BezierSetting)setting,x,y,width,height);
            default:throw new IncompatibleClassChangeError();
        }
    }
}
