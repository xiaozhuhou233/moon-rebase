package moon.module.visual.effects;
import moon.module.visual.EffectsModule;

import java.util.*;
import java.util.function.BooleanSupplier;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** jM normal constructor setting declarations v..D. Six leaves/two groups;
 * defaults are configured before callbacks, in original order. Allocation and
 * event registration remain the destination's explicit lifecycle boundaries.
 */
public final class EffectsSettings {
    public final moon.visual.render.ShadowStyle shadowColor;
    public final BooleanSetting blurEnabled,shadowEnabled;
    public final IntegerSetting blurIterations,blurOffset,shadowIterations,shadowOffset;
    public final FloatSetting shadowAlpha;
    public final SettingGroup blurGroup,shadowGroup;
    private final Map<String,SettingValue<?>> fields=new LinkedHashMap<>();
    private final EffectsModule.Pipeline blur;
    private final EffectsModule.ShadowPipeline shadow;

    public EffectsSettings(MarkerHooks hooks,EffectsModule.Pipeline blur,EffectsModule.ShadowPipeline shadow){
        this.blur=blur;this.shadow=shadow;
        shadowColor=new moon.visual.render.ShadowStyle("shadow-color","Default Shadow Color",hooks);
        blurEnabled=record("v",BooleanSetting.builder("blur",hooks).configure("Blur",true).build());
        blurIterations=record("w",IntegerSetting.builder("blur-iterations",hooks).configure("Iterations",3,1,5,1)
            .visibility(blurEnabled::get).callback((oldValue,newValue)->blur.setIterations(newValue)).build());
        blurOffset=record("x",IntegerSetting.builder("blur-offset",hooks).configure("Offset",3,0,10,1)
            .visibility(blurEnabled::get).callback((oldValue,newValue)->blur.setOffset(newValue)).build());
        blurGroup=record("y",SettingGroup.builder(hooks).configure("Blur Settings",blurIterations,blurOffset)
            .visibility(blurEnabled::get).build());
        shadowEnabled=record("A",BooleanSetting.builder("shadow",hooks).configure("Shadow",true).build());
        shadowIterations=record("B",IntegerSetting.builder("shadow-iterations",hooks).configure("Iterations",3,1,5,1)
            .visibility(shadowEnabled::get).callback((oldValue,newValue)->{shadow.setIterations(newValue);shadow.requestUpdate();}).build());
        shadowOffset=record("C",IntegerSetting.builder("shadow-offset",hooks).configure("Offset",3,0,10,1)
            .visibility(shadowEnabled::get).callback((oldValue,newValue)->{shadow.setOffset(newValue);shadow.requestUpdate();}).build());
        shadowAlpha=record("E",FloatSetting.builder("shadow-alpha",hooks).configure("Shadow Alpha",1F,0F,1F,0.05F)
            .visibility(shadowEnabled::get).callback((oldValue,newValue)->{shadow.setAlpha(newValue);shadow.requestUpdate();}).build());
        shadowGroup=record("D",SettingGroup.builder(hooks).configure("Shadow Settings",shadowIterations,shadowOffset,shadowAlpha,shadowColor.group)
            .visibility(shadowEnabled::get).build());
    }
    private <T extends SettingValue<?>> T record(String field,T value){fields.put(field,value);return value;}
    public Map<String,SettingValue<?>> originalFields(){return Collections.unmodifiableMap(fields);}
    /** Destination module surface: grouped children remain in their own group. */
    public List<SettingValue<?>> roots(){return Collections.unmodifiableList(Arrays.asList(blurEnabled,blurGroup,shadowEnabled,shadowGroup));}
    public EffectsModule createModule(BooleanSupplier originalGuardIsNull,EffectsModule.RenderHooks hooks){
        return new EffectsModule(blur,shadow,blurEnabled::get,shadowEnabled::get,originalGuardIsNull,hooks);
    }
}
