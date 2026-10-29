package moon.visual.hud;

import java.awt.Color;
import moon.visual.render.*;

/** f8/f9 normal null-f4 path, including original informative mask and logo
 * gradient command order. f9 mappings are verified from its standard classfile.
 */
public final class WatermarkHudRenderer implements HudRenderer {
    public static final String LOGO_TEXTURE="logo.png";
    public enum Style { TEXT,LOGO,INFORMATIVE } // be declaration order
    public interface Config extends HudColors.Config {
        boolean enabled();Style style();boolean customTextFont();String text();
        HudColors.Mode colorMode();int colorOne();int colorTwo();boolean tintBackground();boolean translucentText();float scale();
    }
    public interface ServerData {String address();}
    public interface Host {
        boolean originalGuardIsNull();Config findHudConfig();MsdfFontBase selectedFont();DrawTask componentTextTask();
        DrawServices drawing();ShaderDispatcher textureDispatcher();void enableTexture2D();void bindResource(String resource);void bindTexture(int texture);
        ServerData serverData();String buildSuffix();int framesPerSecond();void drawVanilla(String text,float x,float y,int color);
    }
    private final Host host;
    private final HudColors colors;
    private Config config;
    public WatermarkHudRenderer(Host host,HudColors colors){this.host=host;this.colors=colors;}
    private void ensureConfig() {
        if(!host.originalGuardIsNull())throw new UnsupportedOperationException("f8 nonnull f4 sentinel not restored");
        if(config==null)config=host.findHudConfig();
    }
    private int color() {return colors.color(0F,config,config.colorMode(),config.colorOne(),config.colorTwo());}
    private String[] informativeParts() {
        String server="Singleplayer";
        if(host.serverData()!=null)server=host.serverData().address(); // two original lookups
        return new String[]{"Moon","b8e61134ee6d4968344274b8619c9a6fd3438c7c".substring(0,7),host.buildSuffix(),server,host.framesPerSecond()+" fps"};
    }
    @Override public void prepareRectangles(DrawTask task) { }
    @Override public void prepareBackground(DrawTask task) {
        ensureConfig();MsdfFontBase font=host.selectedFont(); // even when disabled
        if(!config.enabled()||config.style()!=Style.INFORMATIVE)return;
        String text=String.join("  \u00a77|  \u00a7r",informativeParts());
        float width=font.width(text,7.5F)+12F,height=font.height(7.5F)+12F;
        int tint=color();
        task.add(new UiGeometry.RoundedRectangle(7F,8F,width,height,6F,new ArgbColor(0xff000000).withAlpha(.4F).getRGB(),host.drawing().markers));
        task.add(new UiGeometry.RoundedRectangle(7F,8F,width,height,6F,
            new ArgbColor(config.tintBackground()?tint:Color.BLACK.getRGB()).withAlpha(.2F).getRGB(),host.drawing().markers));
    }
    @Override public void prepareMask(DrawTask task) {
        ensureConfig();MsdfFontBase font=host.selectedFont();
        if(!config.enabled()||config.style()!=Style.INFORMATIVE)return;
        String text=String.join("  \u00a77|  \u00a7r",informativeParts());
        float width=font.width(text,7.5F)+12F,height=font.height(7.5F)+12F;
        int tint=color(); // original computes helper even with tint disabled
        task.add(new UiGeometry.RoundedRectangle(7F,8F,width,height,6F,config.tintBackground()?tint:Color.BLACK.getRGB(),host.drawing().markers));
    }
    @Override public void prepareText(DrawTask ignored) {
        ensureConfig();MsdfFontBase font=host.selectedFont();
        if(host.componentTextTask()==null||!config.enabled())return;
        switch(config.style()) {
            case TEXT:
                if(config.customTextFont())font.draw(host.componentTextTask(),config.text().replace("&","\u00a7"),9F,2F,2F,color());
                break;
            case INFORMATIVE:
                String text=String.join("  \u00a77|\u00a7r  ",informativeParts());
                font.drawSinglePass(host.componentTextTask(),text,7.5F,13F,14.5F,config.translucentText()?0xbbffffff:-1);
                break;
            default:break;
        }
    }
    @Override public void renderAfterTasks() {
        ensureConfig();
        if(!config.enabled())return;
        switch(config.style()) {
            case LOGO:renderLogo();break;
            case TEXT:
                if(!config.customTextFont())host.drawVanilla(config.text().replace("&","\u00a7"),2F,2F,color());
                break;
            default:break;
        }
    }
    private void renderLogo() {
        host.enableTexture2D();host.bindResource(LOGO_TEXTURE);
        DrawTask logo=DrawTask.builder(host.drawing()).dispatcher(host.textureDispatcher()).build();
        logo.add(new UiGeometry.ColoredTextureQuad(4F,4F,68F,68F,0F,0F,1F,1F,config.colorOne(),host.drawing().markers));
        int first=-1,second=-1;
        if(config.colorMode()!=HudColors.Mode.STATIC) {
            switch(config.colorMode()) {
                case GRADIENT:first=config.colorOne();second=config.colorTwo();break;
                case PULSING:first=config.colorOne();second=new Color(config.colorOne()).darker().darker().getRGB();break;
                default:break;
            }
            logo.add(new AnimatedGradientCommand(first,second,1F,0F,false,host.drawing()));
        }
        if(config.colorMode()==HudColors.Mode.SYNC) {
            switch(config.textMode()) {
                case GRADIENT:first=config.textColorOne();second=config.textColorTwo();break;
                case PULSING:first=config.textColorOne();second=new Color(config.textColorOne()).darker().darker().getRGB();break;
                default:break;
            }
            // SYNC deliberately adds a SECOND command, even when text is STATIC.
            logo.add(new AnimatedGradientCommand(first,second,1F,0F,false,host.drawing()));
        }
        RenderTransformState.setScale(config.scale(),config.scale(),1F);logo.draw();RenderTransformState.resetScale();host.bindTexture(0);
    }
}
