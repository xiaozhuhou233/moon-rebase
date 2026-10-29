package moon.module.visual.glow;
import moon.client.McpVisualProfile;

import java.util.*;
import moon.visual.gui.*;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** jN's fG/fH/fI settings, shared by the single EspModule with its 3D box.
 * Plain constructor literals and live visibility are restored; encrypted radius
 * bounds are supplied by the explicit destination profile. */
public final class GlowSettings implements GlowEntityMaskPass.Config,GlowBlurPass.Config,GlowCompositePass.Config {
    public enum Target implements DisplayNamed {
        PLAYER,ANIMAL,MOB,INVISIBLE;
        public String displayName(){return name();}
    }
    public final MultiChoiceSetting<Target> targets;
    public final BooleanSetting glow,blur,cutInside;
    public final IntegerSetting radius,iterations;
    public final FloatSetting offset,exposure;
    public final BezierSetting alphaCurve;
    public final ColorSetting player,animal,mob,hurt;
    public final SettingGroup intensity,colors;
    public final ShadowStyle shadowColor;
    private java.util.function.Supplier<Integer> effectIterations;
    private java.util.function.Supplier<Float> effectOffset;
    private java.util.function.BooleanSupplier effectEnabled=()->true;
    public void synchronizeShadow(java.util.function.Supplier<Integer> iterations,java.util.function.Supplier<Float> offset,java.util.function.BooleanSupplier enabled){effectIterations=iterations;effectOffset=offset;effectEnabled=enabled;}
    public final EspSettings esp;
    private final List<SettingValue<?>> roots;
    public GlowSettings(MarkerHooks hooks,McpVisualProfile profile){
        shadowColor=new ShadowStyle("shadow-color","Glow Color",hooks);
        targets=MultiChoiceSetting.builder("targets",Target.class,hooks).configure("Targets",Target.PLAYER).build();
        glow=BooleanSetting.builder("glow",hooks).configure("Glow",true).build();
        radius=IntegerSetting.builder("glow-outline-intensity",hooks).configure("Outline Radius",profile.glowRadius,0,profile.glowRadiusMaximum,1).build();
        alphaCurve=BezierSetting.builder("glow-alpha-bezier",hooks).configure("Alpha Curve",.5F,.1F)
            .points(new float[]{.25F,.5F},new float[]{.6F,.1F}).build();
        blur=BooleanSetting.builder("glow-blur",hooks).configure("Blur outline",true).build();
        iterations=IntegerSetting.builder("glow-iterations",hooks).configure("Iterations",2,1,5,1).visibility(blur::get).build();
        offset=FloatSetting.builder("glow-radius",hooks).configure("Offset",2,1,10,.1F).visibility(blur::get).build();
        exposure=FloatSetting.builder("glow-alpha",hooks).configure("Exposure",1,1,3,.1F).visibility(blur::get).build();
        cutInside=BooleanSetting.builder("glow-cut-inside",hooks).configure("Cut Entity",false).visibility(blur::get).build();
        intensity=SettingGroup.builder(hooks).configure("Intensity Settings",radius,alphaCurve,blur,exposure,cutInside).visibility(glow::get).build();
        player=ColorSetting.builder("glow-player-color",hooks).configure("Player Color",-1).visibility(()->targets.isSelected(Target.PLAYER)).build();
        animal=ColorSetting.builder("glow-animal-color",hooks).configure("Animal Color",-1).visibility(()->targets.isSelected(Target.ANIMAL)).build();
        mob=ColorSetting.builder("glow-mobs-color",hooks).configure("Mobs Color",-1).visibility(()->targets.isSelected(Target.MOB)).build();
        hurt=ColorSetting.builder("glow-hurttime-color",hooks).configure("Hurt Time Color",-1).build();
        colors=SettingGroup.builder(hooks).configure("Color Settings",player,animal,mob,hurt).visibility(glow::get).build();
        esp=new EspSettings(hooks,profile);
        colors.setVisibility(()->glow.get()||esp.outline.get());
        List<SettingValue<?>> settings=new ArrayList<>();settings.add(targets);settings.addAll(esp.twoDRoots());
        settings.addAll(List.of(glow,intensity,shadowColor.group,esp.outline,esp.outlineGroup,colors));roots=Collections.unmodifiableList(settings);
    }
    public List<SettingValue<?>> roots(){return roots;}
    public Boolean primaryEnabled(){return glow.get();}public Boolean secondaryEnabled(){return esp.outline.get();}
    public Integer hurtColor(){return hurt.get();}public Integer mobColor(){return mob.get();}
    public Integer animalColor(){return animal.get();}public Integer playerColor(){return player.get();}
    public Integer iterations(){return effectIterations==null?iterations.get():effectIterations.get();}public Float offset(){return effectOffset==null?offset.get():effectOffset.get();}
    public Float alpha(){return exposure.get();}public Boolean stencil(){return cutInside.get();}
    public Boolean blur(){return blur.get();}public Boolean enabled(){return glow.get()&&effectEnabled.getAsBoolean();}
}
