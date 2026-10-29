package moon.visual.hud;

import java.util.*;
import moon.visual.render.*;

/** f7 normal null-f4 path. Potion data refreshes on dH/client-tick POST,
 * independently of HUD PRE. Its retained icon task is separate from jP's six.
 */
public final class PotionHudRenderer implements HudRenderer {
    public static final String INVENTORY_TEXTURE="textures/gui/container/inventory.png";
    public interface Config {
        boolean enabled();boolean customFont();boolean icons();boolean showBuild();float scale();
    }
    public interface PotionType {String translationKey();int iconIndex();int liquidColor();}
    public interface Effect {int amplifier();}
    public interface Host {
        boolean originalGuardIsNull();Config findHudConfig();MsdfFontBase selectedFont();
        InformationHudRenderer.Resolution resolution();boolean isOriginalChatScreen();
        boolean hasPlayer();PotionType[] potionRegistry();boolean hasEffect(PotionType type);Effect effect(PotionType type);
        String translate(String key);String duration(Effect effect);
        int vanillaWidth(String text);int vanillaHeight();void drawVanilla(String text,float x,float y,int color);
        DrawServices drawing();ShaderDispatcher coloredTextureDispatcher();DrawTask componentTextTask();
        void bindResource(String resource);void bindTexture(int texture);
    }
    private final Host host;
    private final Map<PotionType,Effect> effects=new HashMap<>();
    private final List<PotionType> order=new ArrayList<>();
    private Config config;
    private DrawTask icons;
    public PotionHudRenderer(Host host){this.host=host;}
    private void checkGuard(){if(!host.originalGuardIsNull())throw new UnsupportedOperationException("f7 nonnull f4 sentinel not restored");}
    private void ensureConfig(){checkGuard();if(config==null)config=host.findHudConfig();}
    /** Bind to original dH POST, not world rendering. Does not initialize config. */
    public void onClientTick(boolean post) {
        checkGuard();
        if(!host.hasPlayer())return;
        if(!post||config==null)return;
        MsdfFontBase font=host.selectedFont();
        effects.clear();order.clear();
        for(PotionType type:host.potionRegistry()) {
            if(type!=null&&host.hasEffect(type))effects.put(type,host.effect(type));
        }
        order.addAll(effects.keySet());
        // HashMap encounter order and stable comparator ties are intentional.
        // Unary minus is applied as FLOAT before promotion to double.
        order.sort(Comparator.comparingDouble(type->-font.width(label(type,effects.get(type)),9F)));
    }
    private String label(PotionType type,Effect effect) {
        return host.translate(type.translationKey())+" \u00a77"+(effect.amplifier()+1)+" ("+host.duration(effect)+")";
    }
    private float width(String text) {
        if(!config.customFont())return host.vanillaWidth(text);
        return host.selectedFont().width(text,9F);
    }
    private float height() {
        if(!config.customFont())return host.vanillaHeight();
        return host.selectedFont().height(9F);
    }
    private void rebuildIcons() {
        if(icons!=null)icons.getState().release();
        icons=DrawTask.builder(host.drawing()).dispatcher(host.coloredTextureDispatcher()).ownedBuffers().retained().build();
    }
    @Override public void prepareRectangles(DrawTask task) { }
    @Override public void prepareBackground(DrawTask task) { }
    @Override public void prepareMask(DrawTask task) { }
    @Override public void prepareText(DrawTask ignored) {
        ensureConfig();
        if(!config.customFont()||!config.enabled())return;
        MsdfFontBase font=host.selectedFont();
        InformationHudRenderer.Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
        rebuildIcons();
        float offset=host.isOriginalChatScreen()?12F/config.scale():0F;
        if(config.showBuild())offset+=height();
        for(PotionType type:order) {
            String text=label(type,effects.get(type));
            float x=resolution.width()-font.width(text,9F)-2F;
            float height=font.height(9F),y=resolution.height()-height-2F-offset;
            int index=type.iconIndex();
            font.draw(host.componentTextTask(),text,9F,x,y,type.liquidColor()|0xff000000);
            // Original computes both UV values even when icons are disabled.
            float u=index%8*18,v=198+index/8*18;
            if(config.icons())icons.add(new UiGeometry.ColoredTextureQuad(x-11F-2F,y,x-2F,y+11F,
                u*.00390625F,v*.00390625F,(u+18F)*.00390625F,(v+18F)*.00390625F,-1,host.drawing().markers));
            offset+=height;
        }
        resolution.reset();
    }
    @Override public void renderAfterTasks() {
        ensureConfig();
        if(!config.customFont()&&config.enabled()) {
            InformationHudRenderer.Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
            rebuildIcons();
            float offset=host.isOriginalChatScreen()?12F/config.scale():0F;
            if(config.showBuild())offset+=height()+2F;
            for(PotionType type:order) {
                String text=label(type,effects.get(type));
                float x=resolution.width()-width(text)-2F;
                float height=height(),y=resolution.height()-height-2F-offset;
                int index=type.iconIndex();
                host.drawVanilla(text,x,y,type.liquidColor()|0xff000000);
                float u=index%8*18,v=198+index/8*18;
                if(config.icons())icons.add(new UiGeometry.ColoredTextureQuad(x-11F-2F,y-1F,x-2F,y-1F+11F,
                    u*.00390625F,v*.00390625F,(u+18F)*.00390625F,(v+18F)*.00390625F,-1,host.drawing().markers));
                offset+=height+2F;
            }
            resolution.reset();
        }
        if(config.enabled()&&config.icons()) {
            host.bindResource(INVENTORY_TEXTURE);
            RenderTransformState.setScale(config.scale(),config.scale(),1F);
            icons.draw(); // original null-task failure is retained
            RenderTransformState.resetScale();host.bindTexture(0);
        }
    }
    /** Migration owner may release this on render-thread teardown. No invented
     * original close event is implied; the original only releases on rebuild. */
    public DrawTask getIconTask(){return icons;}
}
