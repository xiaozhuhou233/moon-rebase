package moon.visual.hud;

import moon.visual.gui.NumericSettingMath;
import moon.visual.render.DrawTask;
import moon.visual.render.MsdfFontBase;

/** f6, normal null-f4-sentinel path. FPS/BPS/build at the lower left/right,
 * preserving live getter order, two font paths and asymmetric vanilla spacing.
 * Custom text targets jP.aw (componentText), not the supplied av callback task.
 * Host ports game services; this class does not claim destination-game wiring.
 */
public final class InformationHudRenderer implements HudRenderer {
    public interface Config extends HudColors.Config {
        boolean showFps();boolean showBps();boolean showBuild();boolean customFont();
        float scale();HudColors.Mode prefixMode();int prefixColorOne();int prefixColorTwo();
    }
    public interface Resolution {
        void scale(float factor);int width();int height();void reset();
    }
    public interface Host {
        boolean originalGuardIsNull();
        Config findHudConfig(); // lazy registry lookup; null is retried next invocation
        boolean isOriginalExcludedScreen(); // original instanceof moom.gE
        MsdfFontBase selectedFont(); // live supplier, also used anew by metrics
        Resolution resolution();
        DrawTask componentTextTask(); // jP.e():gv -> aw
        int framesPerSecond();float movementSpeed();String buildSuffix(); // bw.B string, not renamed to username
        int vanillaWidth(String text);int vanillaHeight();
        void drawVanilla(String text,float x,float y,int color); // original (String,FFI) overload
    }
    private static final int METRIC_SIZE=9,BUILD_PREFIX_LENGTH=7;
    private final Host host;
    private final HudColors colors;
    private Config config;
    public InformationHudRenderer(Host host,HudColors colors){this.host=host;this.colors=colors;}
    private void ensureConfig() {
        if(!host.originalGuardIsNull())throw new UnsupportedOperationException("f6 nonnull f4 sentinel not restored");
        if(config==null)config=host.findHudConfig();
    }
    private boolean shouldRender() {
        return (config.showFps()||config.showBps()||config.showBuild())&&!host.isOriginalExcludedScreen();
    }
    private String[] leftLines() {
        String value="";
        if(config.showFps())value=value+"FPS\u00a77: \u00a7f"+host.framesPerSecond()+"\n";
        if(config.showBps())value=value+"BPS\u00a77: \u00a7f"+NumericSettingMath.round((double)host.movementSpeed(),2);
        config.showBps(); // second original setting read, even though its value has no effect
        return value.split("\n"); // retain the single empty element for ""
    }
    private String[] rightLines() {
        String value="";
        if(config.showBuild())value=value+"\u00a774.0 - \u00a7f\u00a7l"+
            "b8e61134ee6d4968344274b8619c9a6fd3438c7c".substring(0,BUILD_PREFIX_LENGTH)+" \u00a77- "+host.buildSuffix();
        return value.split("\n");
    }
    private float width(String text,int size) {
        if(!config.customFont())return host.vanillaWidth(text);
        return host.selectedFont().width(text,size);
    }
    private float height(int size) {
        if(!config.customFont())return host.vanillaHeight();
        return host.selectedFont().height(size);
    }
    private int textColor() {
        return colors.color(0F,config,config.prefixMode(),config.prefixColorOne(),config.prefixColorTwo());
    }
    @Override public void prepareRectangles(DrawTask task) { }
    @Override public void prepareBackground(DrawTask task) { }
    @Override public void prepareMask(DrawTask task) { }
    @Override public void prepareText(DrawTask ignored) {
        ensureConfig();
        if(!shouldRender()||!config.customFont())return;
        MsdfFontBase font=host.selectedFont();
        Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
        String[] left=leftLines(),right=rightLines();
        float offset=0F,baseline=resolution.height()-height(METRIC_SIZE);
        for(String text:left) {
            font.draw(host.componentTextTask(),text,9F,2F,baseline-1F-offset,textColor());
            offset+=height(METRIC_SIZE);
        }
        offset=0F;
        for(String text:right) {
            // The destination task is evaluated before width/metrics/color getters.
            DrawTask task=host.componentTextTask();
            float x=resolution.width()-width(text,METRIC_SIZE)-2F;
            font.draw(task,text,9F,x,baseline-1F-offset,textColor());
            offset+=height(METRIC_SIZE);
        }
        resolution.reset(); // no finally: preserve original exception residue
    }
    @Override public void renderAfterTasks() {
        ensureConfig();
        if(!shouldRender()||config.customFont())return;
        Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
        String[] left=leftLines(),right=rightLines();
        float offset=0F,baseline=resolution.height()-height(METRIC_SIZE);
        for(String text:left) {
            host.drawVanilla(text,2F,baseline-1F-offset,textColor());
            offset+=height(METRIC_SIZE)+1F;
        }
        offset=0F;
        for(String text:right) {
            float x=resolution.width()-width(text,METRIC_SIZE)-2F;
            host.drawVanilla(text,x,baseline-1F-offset,textColor());
            offset+=height(METRIC_SIZE); // original right side has no +1
        }
        resolution.reset();
    }
}
