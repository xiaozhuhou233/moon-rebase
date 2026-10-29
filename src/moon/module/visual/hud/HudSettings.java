package moon.module.visual.hud;
import moon.visual.hud.*;
import moon.visual.render.ShadowStyle;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import moon.visual.gui.*;
import moon.visual.hud.EventTrackerHudComponent;
import moon.visual.hud.HudColors;
import moon.visual.hud.HudRenderPipeline;
import moon.visual.hud.InformationHudRenderer;
import moon.visual.hud.PotionHudRenderer;
import moon.visual.hud.WatermarkHudRenderer;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** jP constructor: 45 leaf settings and six groups, in original construction order.
 * Normal null-ba visibility path; no invented module startup/renderer order.
 * The destination owns the dirty sink (the same pipeline used by every config view).
 * See HUD_SETTINGS_EVIDENCE.json for bytecode, runtime numbers and label provenance.
 */
public final class HudSettings {
    public final ShadowStyle watermarkShadow,potionShadow,listShadow,componentShadow;
    public interface Hooks extends MarkerHooks {
        Object[] originalHudGuard();
        void setOriginalHudGuard(Object[] value);
    }
    /** bc/bb label decryption is unresolved; these strings must be supplied explicitly.
     * A host may choose translated labels without changing persisted enum ordinals.
     */
    public interface Labels {
        String colorMode(ColorMode value);
        String textCase(TextCase value);
    }
    public enum WatermarkStyle implements DisplayNamed {
        TEXT("Text"),LOGO("Logo"),INFORMATIVE("Informative");
        private final String label;WatermarkStyle(String label){this.label=label;}
        public String displayName(){return label;}
    }
    public enum ColorMode implements DisplayNamed {
        SYNC,STATIC,GRADIENT,PULSING;
        public String displayName(){throw new UnsupportedOperationException("bc original labels require Labels.colorMode");}
    }
    public enum TextMode implements DisplayNamed {
        STATIC("Static"),GRADIENT("Gradient"),PULSING("Pulsing"),INDIVIDUAL("Individual");
        private final String label;TextMode(String label){this.label=label;}public String displayName(){return label;}
    }
    public enum SidebarMode implements DisplayNamed {
        SYNC("Sync"),STATIC("Static"),GRADIENT("Gradient"),PULSING("Pulsing");
        private final String label;SidebarMode(String label){this.label=label;}public String displayName(){return label;}
    }
    /** a_ order differs from HudModuleListRenderer.SuffixStyle: persist THIS order. */
    public enum Suffix implements DisplayNamed {
        SPACE("Space"),DASH("Dash (-)"),BRACES("Braces ()"),NONE("None");
        private final String label;Suffix(String label){this.label=label;}public String displayName(){return label;}
    }
    public enum TextCase implements DisplayNamed {
        NORMAL,LOWER;
        public String displayName(){throw new UnsupportedOperationException("bb original labels require Labels.textCase");}
    }
    public enum Font implements DisplayNamed {
        ROBOTO("Roboto","ROBOTO_MEDIUM"),PRODUCT_SANS("Product Sans","PRODUCT_SANS"),
        SF_UI("San Francisco","SF_UI"),VOLTE("Volte","VOLTE_SEMI_BOLD");
        private final String label,resource;
        Font(String label,String resource){this.label=label;this.resource=resource;}
        public String displayName(){return label;}public String originalResourceName(){return resource;}
    }

    public final BooleanSetting watermarkEnabled,watermarkCustomFont,watermarkColorful,watermarkLowAlpha;
    public final EnumSetting<WatermarkStyle> watermarkStyle;
    public final StringSetting watermarkText;
    public final EnumSetting<ColorMode> watermarkColorMode;
    public final ColorSetting watermarkColorOne,watermarkColorTwo;
    public final SettingGroup watermarkGroup;
    public final BooleanSetting potionEnabled,potionCustomFont,potionIcons;
    public final SettingGroup potionGroup;
    public final BooleanSetting showFps,showBps,showBuild,informationCustomFont;
    public final EnumSetting<ColorMode> prefixMode;
    public final ColorSetting prefixColorOne,prefixColorTwo;
    public final SettingGroup informationGroup;
    public final BooleanSetting trackerEnabled,trackerHideTitle,trackerFixedWidth,trackerMajor;
    public final IntegerSetting trackerAmount;
    public final SettingGroup trackerGroup,draggableGroup;
    public final BooleanSetting listEnabled,listCustomFont,listTextShadow,listBackground,listRounded,listSidebar;
    public final IntegerSetting listBackgroundOpacity,listRadius,listOffset,listSpacing;
    public final FloatSetting listHorizontalSpacing;
    public final EnumSetting<Suffix> listSuffix;
    public final EnumSetting<TextCase> listTextCase;
    public final EnumSetting<SidebarMode> sidebarMode;
    public final ColorSetting sidebarColorOne,sidebarColorTwo;
    public final EnumSetting<TextMode> textMode;
    public final ColorSetting textColorOne,textColorTwo;
    public final SettingGroup listGroup;
    public final EnumSetting<Font> font;
    public final FloatSetting scale;

    private final Hooks hooks;
    private final Consumer<Boolean> dirty;
    private final Map<String,SettingValue<?>> fields=new LinkedHashMap<>();

    public HudSettings(Hooks hooks,Labels labels,Consumer<Boolean> dirty) {
        this.hooks=hooks;this.dirty=dirty;
        watermarkShadow=new ShadowStyle("wm-shadow","Shadow Color",hooks);
        potionShadow=new ShadowStyle("pot-shadow","Shadow Color",hooks);
        listShadow=new ShadowStyle("al-shadow","Shadow Color",hooks);
        componentShadow=new ShadowStyle("component-shadow","Shadow Color",hooks);
        watermarkEnabled=bool("t","wm-enabled","Enabled",true,null);
        watermarkStyle=choice("u","wm-mode","Mode",WatermarkStyle.class,WatermarkStyle.TEXT,watermarkEnabled::get,null);
        hooks.originalHudGuard(); // original discarded ba.b() after constructing u
        watermarkText=record("v",StringSetting.builder("wm-clientname",hooks).configure("Client Name","Moon")
            .visibility(()->normal()&&watermarkEnabled.get()&&watermarkStyle.get()==WatermarkStyle.TEXT).build());
        watermarkCustomFont=bool("w","wm-custom-font","Custom Font",true,
            ()->normal()&&watermarkEnabled.get()&&watermarkStyle.get()==WatermarkStyle.TEXT);
        watermarkColorful=bool("x","wm-colorful","Colorful background",true,
            ()->normal()&&watermarkEnabled.get()&&watermarkStyle.get()==WatermarkStyle.INFORMATIVE);
        watermarkLowAlpha=bool("y","wm-low-alpha-text","Low alpha text",true,
            ()->normal()&&watermarkEnabled.get()&&watermarkStyle.get()==WatermarkStyle.INFORMATIVE);
        watermarkColorMode=choice("z","wm-color","Color Mode",ColorMode.class,ColorMode.STATIC,watermarkEnabled::get,labels::colorMode);
        watermarkColorOne=color("A","wm-color-one","Color",()->normal()&&watermarkEnabled.get()&&watermarkColorMode.get()!=ColorMode.SYNC);
        watermarkColorTwo=color("B","wm-color-two","Second Color",()->normal()&&watermarkEnabled.get()&&watermarkColorMode.get()==ColorMode.GRADIENT);
        watermarkGroup=group("C","Watermark Settings",watermarkEnabled,watermarkStyle,watermarkText,watermarkCustomFont,
            watermarkColorful,watermarkLowAlpha,watermarkColorMode,watermarkColorOne,watermarkColorTwo,watermarkShadow.group);
        potionEnabled=bool("D","pot-enabled","Enabled",true,null);
        potionCustomFont=bool("E","pot-custom-font","Custom Font",true,potionEnabled::get);
        potionIcons=bool("F","pot-show-icons","Show Icons",true,potionEnabled::get);
        potionGroup=group("G","Potion Settings",potionEnabled,potionCustomFont,potionIcons,potionShadow.group);
        showFps=bool("H","info-fps-enabled","Show FPS",true,null);
        showBps=bool("I","info-bps-enabled","Show BPS",true,null);
        showBuild=bool("J","info-build-enabled","Show Build",true,null);
        informationCustomFont=bool("K","info-custom-font","Custom Font",true,null);
        prefixMode=choice("L","info-prefix-color","Prefix Color",ColorMode.class,ColorMode.SYNC,null,labels::colorMode);
        prefixColorOne=color("M","info-prefix-col-one","Color",()->normal()&&prefixMode.get()!=ColorMode.SYNC);
        prefixColorTwo=color("N","info-prefix-col-two","Second Color",()->normal()&&prefixMode.get()==ColorMode.GRADIENT);
        informationGroup=group("O","Information Settings",showFps,showBps,showBuild,informationCustomFont,prefixMode,prefixColorOne,prefixColorTwo);
        trackerEnabled=bool("P","pit-event-tracker","Enabled",true,null);
        trackerHideTitle=bool("Q","pit-event-tracker-hide-title","Hide title",false,null);
        trackerFixedWidth=bool("R","pit-event-tracker-padding","Padding between time",true,null);
        trackerMajor=bool("S","pit-event-tracker-major","Only Major",false,trackerEnabled::get);
        trackerAmount=integer("T","pit-event-tracker-amount","Event Amount",10,5,20,1,trackerEnabled::get);
        trackerGroup=group("U","Pit Event Tracker",trackerEnabled,trackerHideTitle,trackerFixedWidth,trackerMajor,trackerAmount);
        draggableGroup=group("V","Draggable Settings",trackerGroup,componentShadow.group);
        listEnabled=bool("W","al-enabled","Enabled",true,null);
        listCustomFont=bool("X","al-custom-font","Custom Font",true,listEnabled::get);
        listTextShadow=bool("Y","al-text-shadow","Text Shadow",true,listEnabled::get);
        listBackground=bool("Z","al-background","Background",true,listEnabled::get);
        listBackgroundOpacity=integer("aa","al-background-opacity","Background Opacity",80,0,255,1,
            ()->normal()&&listBackground.get()&&listEnabled.get());
        listRounded=bool("ab","al-rounded","Rounded",false,listEnabled::get);
        listRadius=integer("ac","al-rounding-factor","Rounding Factor",3,1,6,1,
            ()->normal()&&listRounded.get()&&listEnabled.get());
        listOffset=integer("ad","al-offset","Offset",0,0,10,1,listEnabled::get);
        listSpacing=integer("ae","al-spacing","Vertical Spacing",8,0,10,1,listEnabled::get);
        listHorizontalSpacing=record("af",FloatSetting.builder("al-hor-spacing",hooks)
            .configure("Horizontal Spacing",2F,0F,5F,.5F).visibility(listEnabled::get).build());
        listSuffix=choice("ag","al-suffix-type","Suffix Type",Suffix.class,Suffix.SPACE,listEnabled::get,null);
        listTextCase=choice("ah","al-module-name-formatting","Name Formatting",TextCase.class,TextCase.NORMAL,listEnabled::get,labels::textCase);
        listSidebar=bool("ai","al-sidebar","Sidebar",true,listEnabled::get);
        sidebarMode=choice("aj","al-sidebar-color","Sidebar Color",SidebarMode.class,SidebarMode.SYNC,
            ()->normal()&&listSidebar.get()&&listEnabled.get(),null);
        sidebarColorOne=color("ak","al-color-one-sb","Color",
            ()->normal()&&listSidebar.get()&&sidebarMode.get()!=SidebarMode.SYNC&&listEnabled.get());
        sidebarColorTwo=color("al","al-color-two-sb","Second Color",
            ()->normal()&&listSidebar.get()&&sidebarMode.get()==SidebarMode.GRADIENT&&listEnabled.get());
        textMode=choice("am","al-text-color","Text Color",TextMode.class,TextMode.STATIC,listEnabled::get,null);
        textColorOne=color("an","al-color-one","Color",listEnabled::get);
        textColorTwo=color("ao","al-color-two","Second Color",()->normal()&&textMode.get()==TextMode.GRADIENT&&listEnabled.get());
        listGroup=group("ap","Module List",listEnabled,listCustomFont,listTextShadow,listBackground,listBackgroundOpacity,listRounded,
            listRadius,listOffset,listSpacing,listHorizontalSpacing,listSuffix,listTextCase,listSidebar,sidebarMode,sidebarColorOne,sidebarColorTwo,textMode,textColorOne,textColorTwo,listShadow.group);
        font=choice("aq","custom-font-type","Custom Font",Font.class,Font.ROBOTO,null,null);
        scale=record("ar",FloatSetting.builder("scale",hooks).configure("Scale",1F,.5F,4F,.1F)
            .callback((oldValue,newValue)->dirty.accept(true)).build());
        if(hooks.getMarker()!=null)hooks.setOriginalHudGuard(new Object[5]);
    }
    private boolean normal() {
        if(hooks.originalHudGuard()!=null)throw new UnsupportedOperationException("jP nonnull ba visibility path not restored");
        return true;
    }
    private <T extends SettingValue<?>> T record(String field,T setting){fields.put(field,setting);return setting;}
    private BooleanSetting bool(String field,String key,String title,boolean initial,Supplier<Boolean> visible) {
        BooleanSetting.Builder b=BooleanSetting.builder(key,hooks).configure(title,initial);
        if(visible!=null)b.visibility(visible);return record(field,b.build());
    }
    private ColorSetting color(String field,String key,String title,Supplier<Boolean> visible) {
        return record(field,ColorSetting.builder(key,hooks).configure(title,-1).visibility(visible).build());
    }
    private IntegerSetting integer(String field,String key,String title,int initial,int min,int max,int step,Supplier<Boolean> visible) {
        return record(field,IntegerSetting.builder(key,hooks).configure(title,initial,min,max,step).visibility(visible).build());
    }
    private <T extends Enum<T> & DisplayNamed> EnumSetting<T> choice(String field,String key,String title,Class<T> type,T initial,
            Supplier<Boolean> visible,Function<T,String> labels) {
        EnumSetting.Builder<T> b=EnumSetting.builder(key,type,hooks).configure(title,initial);
        if(visible!=null)b.visibility(visible);if(labels!=null)b.labels(labels);return record(field,b.build());
    }
    private SettingGroup group(String field,String title,SettingValue<?>... children) {
        return record(field,SettingGroup.builder(hooks).configure(title,children).build());
    }
    /** Declaration/construction order only; destination module discovery is a separate boundary. */
    public Map<String,SettingValue<?>> originalFields(){return Collections.unmodifiableMap(fields);}
    public List<SettingValue<?>> roots(){return Collections.unmodifiableList(Arrays.asList(watermarkGroup,potionGroup,informationGroup,draggableGroup,listGroup,font,scale));}
    public SettingValue<?> byKey(String key){for(SettingValue<?> s:fields.values())if(s.getKey().equals(key))return s;return null;}

    private abstract class ColorsView implements HudColors.Config {
        public int textColorOne(){return HudSettings.this.textColorOne.get();}
        public int textColorTwo(){return HudSettings.this.textColorTwo.get();}
        public HudRenderPipeline.TextColorMode textMode(){return HudRenderPipeline.TextColorMode.valueOf(HudSettings.this.textMode.get().name());}
        public float scale(){return HudSettings.this.scale.get();}
    }
    public HudRenderPipeline.Config pipelineConfig(){return new PipelineView();}
    private final class PipelineView extends ColorsView implements HudRenderPipeline.Config {
        public HudRenderPipeline.SidebarColorMode sidebarMode(){return HudRenderPipeline.SidebarColorMode.valueOf(HudSettings.this.sidebarMode.get().name());}
        public int sidebarColorOne(){return HudSettings.this.sidebarColorOne.get();}public int sidebarColorTwo(){return HudSettings.this.sidebarColorTwo.get();}
    }
    public WatermarkHudRenderer.Config watermarkConfig(){return new WatermarkView();}
    private final class WatermarkView extends ColorsView implements WatermarkHudRenderer.Config {
        public boolean enabled(){return watermarkEnabled.get();}public WatermarkHudRenderer.Style style(){return WatermarkHudRenderer.Style.valueOf(watermarkStyle.get().name());}
        public boolean customTextFont(){return watermarkCustomFont.get();}public String text(){return watermarkText.get();}
        public HudColors.Mode colorMode(){return HudColors.Mode.valueOf(watermarkColorMode.get().name());}
        public int colorOne(){return watermarkColorOne.get();}public int colorTwo(){return watermarkColorTwo.get();}
        public boolean tintBackground(){return watermarkColorful.get();}public boolean translucentText(){return watermarkLowAlpha.get();}
    }
    public InformationHudRenderer.Config informationConfig(){return new InformationView();}
    private final class InformationView extends ColorsView implements InformationHudRenderer.Config {
        public boolean showFps(){return showFps.get();}public boolean showBps(){return showBps.get();}public boolean showBuild(){return showBuild.get();}
        public boolean customFont(){return informationCustomFont.get();}public HudColors.Mode prefixMode(){return HudColors.Mode.valueOf(HudSettings.this.prefixMode.get().name());}
        public int prefixColorOne(){return HudSettings.this.prefixColorOne.get();}public int prefixColorTwo(){return HudSettings.this.prefixColorTwo.get();}
    }
    public PotionHudRenderer.Config potionConfig(){return new PotionHudRenderer.Config(){
        public boolean enabled(){return potionEnabled.get();}public boolean customFont(){return potionCustomFont.get();}public boolean icons(){return potionIcons.get();}
        public boolean showBuild(){return showBuild.get();}public float scale(){return scale.get();}
    };}
    public EventTrackerHudComponent.Config trackerConfig(){return new EventTrackerHudComponent.Config(){
        public boolean enabled(){return trackerEnabled.get();}public boolean hideTitle(){return trackerHideTitle.get();}
        public boolean fixedWidth(){return trackerFixedWidth.get();}public boolean majorOnly(){return trackerMajor.get();}public int eventLimit(){return trackerAmount.get();}
    };}
    public HudModuleListRenderer.Config moduleListConfig(){return new ModuleListView();}
    private final class ModuleListView extends ColorsView implements HudModuleListRenderer.Config {
        public boolean enabled(){return listEnabled.get();}public boolean customFont(){return listCustomFont.get();}public boolean alternateTextPass(){return listTextShadow.get();}
        public boolean background(){return listBackground.get();}public int backgroundAlpha(){return listBackgroundOpacity.get();}public boolean rounded(){return listRounded.get();}
        public int radius(){return listRadius.get();}public int padding(){return listOffset.get();}public int lineSpacing(){return listSpacing.get();}public float outerPadding(){return listHorizontalSpacing.get();}
        public HudModuleListRenderer.SuffixStyle suffixStyle(){return HudModuleListRenderer.SuffixStyle.valueOf(listSuffix.get().name());}
        public HudModuleListRenderer.TextCase textCase(){return HudModuleListRenderer.TextCase.valueOf(listTextCase.get().name());}
        public boolean sidebar(){return listSidebar.get();}public HudRenderPipeline.SidebarColorMode sidebarMode(){return HudRenderPipeline.SidebarColorMode.valueOf(HudSettings.this.sidebarMode.get().name());}
        public int sidebarColorOne(){return HudSettings.this.sidebarColorOne.get();}public void setDirty(boolean value){dirty.accept(value);}
    }
}
