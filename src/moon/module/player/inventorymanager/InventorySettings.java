package moon.module.player.inventorymanager;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

public final class InventorySettings {
    public enum Mode implements DisplayNamed {NONE,OPEN,SPOOF;public String displayName(){return name();}}
    public enum Feature implements DisplayNamed {AUTO_CLEAN,AUTO_ARMOR,AUTO_SWORD,AUTO_SOUP,AUTO_POTION,MOVE_BLOCKS;public String displayName(){return name();}}
    public enum PotionType implements DisplayNamed {INSTANT_HEAL,RESISTANCE,REGENERATION,SPEED;public String displayName(){return name();}}
    public final EnumSetting<Mode> mode;public final MultiChoiceSetting<Feature> features;public final MultiChoiceSetting<PotionType> potions;
    public final IntegerSetting delay,randomization,blockLimit,toolsSlot,potionDelay,health;
    public final BooleanSetting named,bows,tools,hotbarTools;
    private final List<SettingValue<?>> roots;
    public InventorySettings(MarkerHooks h){
        mode=EnumSetting.builder("inventory-mode",Mode.class,h).configure("Required inventory state",Mode.NONE).build();
        features=MultiChoiceSetting.builder("features",Feature.class,h).configure("Features",Feature.values()).build();
        delay=n(h,"delay",150,1,300,50);randomization=n(h,"delay-randomization",50,0,500,50);blockLimit=n(h,"block-limit",256,64,1024,64);
        named=b(h,"ignore-custom-display-names",true);bows=b(h,"keep-bows",true);tools=b(h,"keep-tools",true);hotbarTools=b(h,"hotbar-tools",true);
        toolsSlot=n(h,"tools-slot",1,1,6,1);potionDelay=n(h,"potion-throw-delay",500,50,2500,50);health=n(h,"activation-health",15,1,20,1);
        potions=MultiChoiceSetting.builder("allowed-potions",PotionType.class,h).configure("Allowed potions",PotionType.values()).build();
        blockLimit.setVisibility(()->features.isSelected(Feature.AUTO_CLEAN));
        named.setVisibility(()->features.isSelected(Feature.AUTO_CLEAN));
        bows.setVisibility(()->features.isSelected(Feature.AUTO_CLEAN));
        tools.setVisibility(()->features.isSelected(Feature.AUTO_CLEAN));
        hotbarTools.setVisibility(()->features.isSelected(Feature.AUTO_CLEAN)&&tools.get());
        toolsSlot.setVisibility(()->features.isSelected(Feature.AUTO_CLEAN)&&tools.get()&&hotbarTools.get());
        potions.setVisibility(()->features.isSelected(Feature.AUTO_POTION));
        potionDelay.setVisibility(()->features.isSelected(Feature.AUTO_POTION));
        health.setVisibility(()->features.isSelected(Feature.AUTO_POTION)&&potions.isSelected(PotionType.INSTANT_HEAL));
        roots=List.of(mode,features,delay,randomization,blockLimit,named,bows,tools,hotbarTools,toolsSlot,potionDelay,potions,health);
    }
    public List<SettingValue<?>> roots(){return roots;}
    static IntegerSetting n(MarkerHooks h,String key,int v,int min,int max,int step){return IntegerSetting.builder(key,h).configure(key.replace('-',' '),v,min,max,step).build();}
    static BooleanSetting b(MarkerHooks h,String key,boolean v){return BooleanSetting.builder(key,h).configure(key.replace('-',' '),v).build();}
}
