package moon.module.visual.clickgui;
import moon.module.visual.ClickGuiModule;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import moon.module.visual.ClickGuiModule.*;

/** jK v..B, seven settings in original constructor order. This same object is
 * the l9/kz live configuration; category hosts use roundedStyle() for kA/kc.
 * Original a1 labels are unresolved and must be supplied explicitly by the host.
 */
public final class ClickGuiSettings implements NativeClickGuiScreen.Config,CategoryPanel.StyleConfig {
    public final EnumSetting<Mode> mode;
    public final BooleanSetting openingAnimation,closingAnimation,center,postProcessing;
    public final FloatSetting scale;
    public final EnumSetting<OpeningType> openingType;
    public final EnumSetting<ClosingType> closingType;
    public final EnumSetting<ModuleDesign> moduleDesign;
    public final ColorSetting color;
    public final moon.visual.render.ShadowStyle shadowColor;
    private final Map<String,SettingValue<?>> fields=new LinkedHashMap<>();
    private final ClickGuiModule module;

    public ClickGuiSettings(MarkerHooks markers,ClickGuiModule.Backend backend,
                            BooleanSupplier originalGuardIsNull,Function<Mode,String> modeLabels) {
        // Original u=com.v.b(-1) precedes v..B; B's RED default has no callback.
        module=new ClickGuiModule(backend,this::selectedMode,originalGuardIsNull);
        shadowColor=new moon.visual.render.ShadowStyle("shadow-color","Shadow Color",markers);
        mode=record("v",EnumSetting.builder("mode",Mode.class,markers).configure("Mode",Mode.MODERN)
            .labels(Objects.requireNonNull(modeLabels,"unresolved a1 labels")).build());
        openingAnimation=record("w",BooleanSetting.builder("open-animation",markers).configure("Opening Animation",true).visibility(()->mode.get()==Mode.DEFAULT).build());
        openingType=record("x",EnumSetting.builder("open-animation-type",OpeningType.class,markers).configure("Opening Type",OpeningType.LINEAR)
            .visibility(()->mode.get()==Mode.DEFAULT&&openingAnimation.get()).build());
        closingAnimation=record("y",BooleanSetting.builder("close-animation",markers).configure("Closing Animation",true).visibility(()->mode.get()==Mode.DEFAULT).build());
        closingType=record("z",EnumSetting.builder("close-animation-type",ClosingType.class,markers).configure("Closing Type",ClosingType.IN_BACK)
            .visibility(()->mode.get()==Mode.DEFAULT&&closingAnimation.get()).build());
        moduleDesign=record("A",EnumSetting.builder("module-design-type",ModuleDesign.class,markers).configure("Module Design",ModuleDesign.STACKED).visibility(()->mode.get()==Mode.DEFAULT).build());
        center=record("nightly.B",BooleanSetting.builder("center",markers).configure("Center",false).visibility(()->mode.get()==Mode.MODERN).build());
        postProcessing=record("nightly.C",BooleanSetting.builder("post-processing",markers).configure("Post Processing",true).visibility(()->mode.get()==Mode.MODERN).build());
        scale=record("nightly.D",FloatSetting.builder("scale",markers).configure("Scale",1F,.5F,2F,.1F).visibility(()->mode.get()==Mode.MODERN).build());
        color=record("B",ColorSetting.builder("color",markers).configure("Color",ClickGuiModule.DEFAULT_COLOR_SETTING)
            .callback(module::onColorChanged).build());
    }
    private Mode selectedMode(){return mode.get();}
    private <T extends SettingValue<?>> T record(String field,T value){fields.put(field,value);return value;}
    public ClickGuiModule module(){return module;}
    public Map<String,SettingValue<?>> originalFields(){return Collections.unmodifiableMap(fields);}
    public List<SettingValue<?>> roots(){List<SettingValue<?>> roots=new ArrayList<>(fields.values());roots.add(shadowColor.group);return Collections.unmodifiableList(roots);}
    @Override public boolean modernCenter(){return center.get();}
    @Override public boolean modernPostProcessing(){return postProcessing.get();}
    @Override public float modernScale(){return scale.get();}
    @Override public int colorArgb(){return color.get();}
    @Override public boolean openingAnimation(){return openingAnimation.get();}
    @Override public boolean closingAnimation(){return closingAnimation.get();}
    @Override public NativeClickGuiScreen.Easing openingEasing(){
        switch(openingType.get()) {
            case LINEAR:return NativeClickGuiScreen.Easing.LINEAR;
            case OUT_BACK:return NativeClickGuiScreen.Easing.EASE_OUT_BACK;
            default:throw new AssertionError(openingType.get());
        }
    }
    @Override public NativeClickGuiScreen.Easing closingEasing(){
        switch(closingType.get()) {
            case LINEAR:return NativeClickGuiScreen.Easing.LINEAR;
            case IN_BACK:return NativeClickGuiScreen.Easing.EASE_IN_BACK;
            default:throw new AssertionError(closingType.get());
        }
    }
    @Override public boolean roundedStyle(){return moduleDesign.get()==ModuleDesign.ROUNDED;}
}
