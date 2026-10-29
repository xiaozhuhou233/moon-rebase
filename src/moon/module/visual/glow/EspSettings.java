package moon.module.visual.glow;
import moon.client.McpVisualProfile;

import java.util.*;
import moon.visual.gui.*;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** jN u..Y/am..ar normal constructor and visibility. Enum labels and encrypted
 * outline radius limits are explicit destination policy, not recovered strings. */
public final class EspSettings implements EntityEspRenderer.Settings,EntityEspBoxComponent.Settings,
        EntityEspBarComponents.Settings,EntityEspTextComponents.Settings {
    public enum HealthMode implements DisplayNamed {NORMAL,GRADIENT,STATIC;public String displayName(){return name();}}
    public enum ArmorMode implements DisplayNamed {STATIC,GRADIENT;public String displayName(){return name();}}
    public enum Indicator implements DisplayNamed {BLOCK,SNEAK,LOW_HP;public String displayName(){return name();}}
    public final BooleanSetting twoD,box,cornered,relativeCorners,infill,names,customNameColor,health,percentage,armor,items,icons,itemNames,status,outline;
    public final FloatSetting cornerFactor,brightness,contrast,saturation;
    public final IntegerSetting outlineRadius;
    public final ColorSetting boxColor,nameColor,healthFirst,healthSecond,armorFirst,armorSecond,itemColor;
    public final EnumSetting<HealthMode> healthColorMode;
    public final EnumSetting<ArmorMode> armorColorMode;
    public final MultiChoiceSetting<Indicator> indicators;
    public final SettingGroup boxGroup,nameGroup,healthGroup,armorGroup,itemGroup,statusGroup,outlineGroup;
    public EspSettings(MarkerHooks h,McpVisualProfile profile){
        twoD=BooleanSetting.builder("two-d",h).configure("2D",true).build();
        box=BooleanSetting.builder("two-d-box-enabled",h).configure("Enabled",true).build();
        cornered=BooleanSetting.builder("two-d-box-cornered",h).configure("Cornered",true).visibility(box::get).build();
        relativeCorners=BooleanSetting.builder("two-d-box-cornered-relative",h).configure("Relative Corners",true).visibility(()->box.get()&&cornered.get()).build();
        cornerFactor=FloatSetting.builder("two-d-box-cornered-corner-factor",h).configure("Corner Factor",2,2,4,.1F).visibility(()->box.get()&&cornered.get()).build();
        infill=BooleanSetting.builder("two-d-box-infill",h).configure("Infill",false).visibility(box::get).build();
        boxColor=ColorSetting.builder("two-d-box-color",h).configure("Color",-1).visibility(box::get).build();
        boxGroup=SettingGroup.builder(h).configure("Box Settings",box,cornered,relativeCorners,cornerFactor,infill,boxColor).visibility(twoD::get).build();
        names=BooleanSetting.builder("two-d-name-enabled",h).configure("Enabled",false).build();
        customNameColor=BooleanSetting.builder("two-d-name-custom-color",h).configure("Custom Color",false).visibility(names::get).build();
        nameColor=ColorSetting.builder("two-d-name-color",h).configure("Color",-1).visibility(()->names.get()&&customNameColor.get()).build();
        nameGroup=SettingGroup.builder(h).configure("Name Settings",names,customNameColor,nameColor).visibility(twoD::get).build();
        health=BooleanSetting.builder("two-d-health-enabled",h).configure("Enabled",true).build();
        percentage=BooleanSetting.builder("two-d-show-health",h).configure("Show health",true).visibility(health::get).build();
        healthColorMode=EnumSetting.builder("two-d-health-color-type",HealthMode.class,h).configure("Color Type",HealthMode.NORMAL).visibility(health::get).build();
        healthFirst=ColorSetting.builder("two-d-health-color-one",h).configure("Color",-1).visibility(()->health.get()&&healthColorMode.get()!=HealthMode.NORMAL).build();
        healthSecond=ColorSetting.builder("two-d-health-color-two",h).configure("Second Color",0xffff0000).visibility(()->health.get()&&healthColorMode.get()==HealthMode.GRADIENT).build();
        healthGroup=SettingGroup.builder(h).configure("Health Settings",health,percentage,healthColorMode,healthFirst,healthSecond).visibility(twoD::get).build();
        armor=BooleanSetting.builder("two-d-armor-enabled",h).configure("Enabled",true).build();
        armorColorMode=EnumSetting.builder("two-d-armor-color-type",ArmorMode.class,h).configure("Color Type",ArmorMode.STATIC).visibility(armor::get).build();
        armorFirst=ColorSetting.builder("two-d-armor-color-one",h).configure("Color",-1).visibility(armor::get).build();
        armorSecond=ColorSetting.builder("two-d-armor-color-two",h).configure("Second Color",0xffff0000).visibility(()->armor.get()&&armorColorMode.get()==ArmorMode.GRADIENT).build();
        armorGroup=SettingGroup.builder(h).configure("Armor Settings",armor,armorColorMode,armorFirst,armorSecond).visibility(twoD::get).build();
        items=BooleanSetting.builder("two-d-item-enabled",h).configure("Enabled",true).build();
        icons=BooleanSetting.builder("two-d-item-tex",h).configure("Texture",true).visibility(items::get).build();
        itemNames=BooleanSetting.builder("two-d-item-name",h).configure("Name",true).visibility(items::get).build();
        itemColor=ColorSetting.builder("two-d-item-color",h).configure("Color",-1).visibility(()->items.get()&&icons.get()).build();
        itemGroup=SettingGroup.builder(h).configure("Item Settings",items,icons,itemNames,itemColor).visibility(twoD::get).build();
        status=BooleanSetting.builder("two-d-indicator-enabled",h).configure("Enabled",false).build();
        indicators=MultiChoiceSetting.builder("two-d-indicator-types",Indicator.class,h).configure("Types",Indicator.BLOCK).visibility(status::get).build();
        statusGroup=SettingGroup.builder(h).configure("Indicator Settings",status,indicators).visibility(twoD::get).build();
        outline=BooleanSetting.builder("outline",h).configure("Outline",true).build();
        outlineRadius=IntegerSetting.builder("outline-outline-intensity",h).configure("Outline Radius",profile.entityOutlineRadius,0,profile.entityOutlineRadiusMaximum,1).build();
        brightness=FloatSetting.builder("outline-brightness",h).configure("Outline Brightness",0,0,1,.05F).build();
        contrast=FloatSetting.builder("outline-contrast",h).configure("Outline Contrast",1,0,5,.05F).build();
        saturation=FloatSetting.builder("outline-saturation",h).configure("Outline Saturation",1,0,5,.05F).build();
        outlineGroup=SettingGroup.builder(h).configure("Outline Settings",outlineRadius,brightness,contrast,saturation).visibility(outline::get).build();
    }
    public List<SettingValue<?>> twoDRoots(){return List.of(twoD,boxGroup,nameGroup,healthGroup,armorGroup,itemGroup,statusGroup);}
    public EntityOutlinePass.Config outlineConfig(){return new EntityOutlinePass.Config(){
        public Integer radius(){return outlineRadius.get();}public Float brightness(){return EspSettings.this.brightness.get();}
        public Float contrast(){return EspSettings.this.contrast.get();}public Float saturation(){return EspSettings.this.saturation.get();}public Boolean enabled(){return outline.get();}
    };}
    public boolean enabled(){return twoD.get();}public boolean boxEnabled(){return box.get();}public int boxColor(){return boxColor.get();}
    public boolean innerFill(){return infill.get();}public boolean corners(){return cornered.get();}public float cornerDivisor(){return cornerFactor.get();}
    public boolean independentVerticalCorners(){return relativeCorners.get();}
    public boolean healthEnabled(){return health.get();}public boolean healthPercentage(){return percentage.get();}
    public EntityEspBarComponents.Mode healthMode(){return switch(healthColorMode.get()){case NORMAL->EntityEspBarComponents.Mode.AUTOMATIC;case STATIC->EntityEspBarComponents.Mode.STATIC;case GRADIENT->EntityEspBarComponents.Mode.GRADIENT;};}
    public int healthFirstColor(){return healthFirst.get();}public int healthSecondColor(){return healthSecond.get();}
    public boolean armorEnabled(){return armor.get();}public EntityEspBarComponents.Mode armorMode(){return EntityEspBarComponents.Mode.valueOf(armorColorMode.get().name());}
    public int armorFirstColor(){return armorFirst.get();}public int armorSecondColor(){return armorSecond.get();}
    public boolean itemsEnabled(){return items.get();}public boolean itemIconsEnabled(){return icons.get();}public boolean itemNamesEnabled(){return itemNames.get();}
    public int itemColor(){return itemColor.get();}public boolean itemEnabled(){return items.get();}public boolean itemNameEnabled(){return itemNames.get();}
    public boolean statusEnabled(){return status.get();}public boolean flag(EntityEspTextComponents.Flag flag){return indicators.isSelected(Indicator.valueOf(flag.name()));}
    public boolean nameEnabled(){return names.get();}public boolean unformattedName(){return customNameColor.get();}public int nameColor(){return nameColor.get();}
}
