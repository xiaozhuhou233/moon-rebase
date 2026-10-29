package moon.module.visual;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import pisi.unitedmeows.minecraft.Settings;

/** jI and ll's seven first-person sword poses, with sF's local-player swing timing. */
public final class BlockAnimationsModule implements ModuleRowWidget.Module {
    public enum Mode implements DisplayNamed {
        V1_8, V1_7, SLIDE, XIV, EXHIBITION, OLD_EXHIBITION, WINGY;
        public String displayName() { return switch(this) {
            case V1_8 -> "1.8";
            case V1_7 -> "1.7";
            case OLD_EXHIBITION -> "Old Exhibition";
            default -> name().replace('_',' ');
        }; }
    }
    private static BlockAnimationsModule active;
    public final EnumSetting<Mode> mode;
    public final BooleanSetting slowAnimationDown;
    public final IntegerSetting slowDownStrength;
    public final FloatSetting x,y,scale;
    
    private final List<SettingValue<?>> settings;
    private boolean enabled,rememberedOldValue;

    public BlockAnimationsModule(MarkerHooks hooks) {
        mode=EnumSetting.builder("mode",Mode.class,hooks).configure("Mode",Mode.V1_7).build();
        slowAnimationDown=BooleanSetting.builder("slow-animation-down",hooks).configure("Slow animation down",true).build();
        // jI.C cast to int, decoded with the supplied runtime seed: 15.
        slowDownStrength=IntegerSetting.builder("slow-down-strength",hooks).configure("Slow down strength",5,1,15,1)
            .visibility(slowAnimationDown::get).build();
        x=FloatSetting.builder("x-pos",hooks).configure("X Position",0F,-5F,5F,.1F).build();
        y=FloatSetting.builder("y-pos",hooks).configure("Y Position",0F,-5F,5F,.1F).build();
        scale=FloatSetting.builder("scale",hooks).configure("Scale",1F,.1F,3F,.1F).build();
        settings=List.of(mode,slowAnimationDown,slowDownStrength,x,y,scale);
        if(active!=null)throw new IllegalStateException("Block animations already installed");
        active=this;
    }
    public static BlockAnimationsModule current(){return active;}
    public static Mode activeMode(){BlockAnimationsModule value=active;return value==null||!value.enabled?null:value.mode.get();}
    public static boolean usesLegacyBlockPose(){
        BlockAnimationsModule value=active;
        return value==null?Settings.OLD_ANIMATIONS:value.enabled&&value.mode.get()==Mode.V1_7;
    }
    /** Slowdown changes the animation duration, preserving its complete 0..1 arc. */
    public static float blockSwingProgress(float progress){return progress;}
    /** Zero delegates to vanilla haste/fatigue timing for every other entity. */
    public static int swingDuration(EntityLivingBase entity){
        BlockAnimationsModule value=active;
        if(value==null||!value.enabled||!value.slowAnimationDown.get()||entity!=Minecraft.getMinecraft().thePlayer)return 0;
        // sF.k's base 10 is the unique finite offline numeric DATA candidate, not a runtime seed claim.
        return 10+value.slowDownStrength.get();
    }
    /** Called within ItemRenderer's pushed matrix. False selects the base's disabled pose. */
    public static boolean renderBlockPose(float equip,float swing){
        BlockAnimationsModule value=active;if(value==null||!value.enabled)return false;
        float size=value.scale.get();
        GlStateManager.scale(size,size,1F);
        GlStateManager.translate(value.x.get()/size,-value.y.get()/size,0F);
        float sinFloat=MathHelper.sin(MathHelper.sqrt_float(swing)*(float)Math.PI);
        switch(value.mode.get()){
            case V1_8 -> {firstPerson(equip,0);block();}
            case V1_7 -> {firstPerson(equip-.3F,swing);block();}
            case SLIDE -> {
                firstPerson(0,0);block();
                float sin=MathHelper.sin((float)(MathHelper.sqrt_float(swing)*Math.PI));
                GlStateManager.translate(-.05F,-0F,.35F);
                GlStateManager.rotate(-sin*60F/2F,-15F,-0F,9F);
                GlStateManager.rotate(-sin*70F,1F,-.4F,-0F);
            }
            case XIV -> {
                firstPerson(equip,0);block();
                float squared=MathHelper.sin((float)((swing*swing)*Math.PI));
                float sin=MathHelper.sin((float)(MathHelper.sqrt_float(swing)*Math.PI));
                GlStateManager.rotate(-squared*20F,0,1,0);
                GlStateManager.rotate(-sin*20F,0,0,1);
                GlStateManager.rotate(-sin*80F,1,0,0);
            }
            case EXHIBITION -> {
                GlStateManager.translate(-.1D,.15D,0D);firstPerson(equip/2F,0);
                GlStateManager.rotate(-sinFloat*45F/1.5F,sinFloat/1.5F,-0F,9F);
                GlStateManager.rotate(-sinFloat*55F,1F,sinFloat/2F,-0F);block();
            }
            case OLD_EXHIBITION -> {
                GlStateManager.translate(-.1D,.1D,0D);firstPerson(equip/2F,0);
                GlStateManager.rotate(-sinFloat*40F/2F,sinFloat/2F,-0F,9F);
                GlStateManager.rotate(-sinFloat*30F,1F,sinFloat/2F,-0F);block();
            }
            case WINGY -> {
                GlStateManager.translate(.1F,.1F,.1F);
                GlStateManager.rotate(-sinFloat*30F,-8F,-.2F,9F);
                firstPerson(equip,swing);block();
            }
        }
        return true;
    }
    private static void firstPerson(float equip,float swing){
        GlStateManager.translate(.56F,-.52F,-.71999997F);
        GlStateManager.translate(0F,equip*-.6F,0F);GlStateManager.rotate(45F,0F,1F,0F);
        float squared=MathHelper.sin(swing*swing*(float)Math.PI),sin=MathHelper.sin(MathHelper.sqrt_float(swing)*(float)Math.PI);
        GlStateManager.rotate(squared*-20F,0,1,0);GlStateManager.rotate(sin*-20F,0,0,1);GlStateManager.rotate(sin*-80F,1,0,0);
        GlStateManager.scale(.4F,.4F,.4F);
    }
    private static void block(){
        GlStateManager.translate(-.5F,.2F,0F);GlStateManager.rotate(30F,0,1,0);
        GlStateManager.rotate(-80F,1,0,0);GlStateManager.rotate(60F,0,1,0);
    }
    public String migrationStatus(){return null;}
    public void tick(){if(enabled)Settings.OLD_ANIMATIONS=mode.get()!=Mode.V1_8;}
    public String hudSuffix(){return mode.get().displayName();}
    public String displayName(){return "BlockAnimations";}
    public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}
    public void setEnabled(boolean value){
        if(enabled==value)return;
        enabled=value;
        if(value){rememberedOldValue=Settings.OLD_ANIMATIONS;tick();}
        else Settings.OLD_ANIMATIONS=rememberedOldValue;
    }
    public List<? extends SettingValue<?>> settings(){return settings;}
    public void close(){if(active==this){if(enabled)Settings.OLD_ANIMATIONS=rememberedOldValue;enabled=false;active=null;}}
    public void synchronizeVanillaPose(){tick();}
}
