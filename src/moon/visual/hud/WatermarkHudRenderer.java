package moon.visual.hud;

import java.awt.Color;
import java.util.List;
import moon.visual.render.*;

/** f8/f9 normal null-f4 path, including original informative mask and logo
 * gradient command order. f9 mappings are verified from its standard classfile.
 */
public final class WatermarkHudRenderer implements HudRenderer {
    public static final String LOGO_TEXTURE="logo.png";
    public enum Style { TEXT,LOGO,INFORMATIVE } // be declaration order
    public record Row(String label,String value){}
    public interface Config extends HudColors.Config {
        boolean enabled();Style style();boolean customTextFont();String text();
        HudColors.Mode colorMode();int colorOne();int colorTwo();boolean tintBackground();boolean translucentText();float scale();
    }
    public interface ServerData {String address();}
    public interface Host {
        boolean originalGuardIsNull();Config findHudConfig();MsdfFontBase selectedFont();DrawTask componentTextTask();
        DrawServices drawing();ShaderDispatcher textureDispatcher();void enableTexture2D();void bindResource(String resource);void bindTexture(int texture);
        List<Row> informativeRows();String informativeTitle();
        ServerData serverData();String buildSuffix();int framesPerSecond();void drawVanilla(String text,float x,float y,int color);
    }
    private final Host host;
    private final HudColors colors;
    private Config config;
    private List<Row> cachedRows;
    private long cachedRowsAt;
    public WatermarkHudRenderer(Host host,HudColors colors){this.host=host;this.colors=colors;}
    private List<Row> rows(){
        long now=System.currentTimeMillis();
        if(cachedRows==null||now-cachedRowsAt>50L){cachedRows=host.informativeRows();cachedRowsAt=now;}
        return cachedRows==null?List.of():cachedRows;
    }
    private void ensureConfig() {
        if(!host.originalGuardIsNull())throw new UnsupportedOperationException("f8 nonnull f4 sentinel not restored");
        if(config==null)config=host.findHudConfig();
    }
    private int color() {return colors.color(0F,config,config.colorMode(),config.colorOne(),config.colorTwo());}
    private static final float CARD_X=7F,CARD_PADDING=8F,LABEL_VALUE_GAP=10F;
    private float informativeWidth(MsdfFontBase font){
        float width=font.width(host.informativeTitle(),12F);
        for(Row row:rows())width=Math.max(width,font.width(row.label(),8F)+LABEL_VALUE_GAP+font.width(row.value(),8F));
        return width+CARD_PADDING*2F;
    }
    private float informativeHeight(MsdfFontBase font){return font.height(8F)*(rows().size()+1)+18F;}
    @Override public void prepareRectangles(DrawTask task) { }
    @Override public void prepareBackground(DrawTask task) {
        ensureConfig();MsdfFontBase font=host.selectedFont(); // even when disabled
        if(!config.enabled()||config.style()!=Style.INFORMATIVE)return;
        float width=informativeWidth(font),height=informativeHeight(font);
        int tint=color();
        task.add(new UiGeometry.RoundedRectangle(7F,8F,width,height,6F,new ArgbColor(0xff000000).withAlpha(.4F).getRGB(),host.drawing().markers));
        task.add(new UiGeometry.RoundedRectangle(7F,8F,width,height,6F,
            new ArgbColor(config.tintBackground()?tint:Color.BLACK.getRGB()).withAlpha(.2F).getRGB(),host.drawing().markers));
    }
    @Override public void prepareMask(DrawTask task) {
        ensureConfig();MsdfFontBase font=host.selectedFont();
        if(!config.enabled()||config.style()!=Style.INFORMATIVE)return;
        float width=informativeWidth(font),height=informativeHeight(font);
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
                int textColor=config.translucentText()?0xbbffffff:-1;
                int labelColor=(textColor&0x00FFFFFF)|0x99000000;
                float width=informativeWidth(font);
                float rowY=13F;
                font.draw(host.componentTextTask(),host.informativeTitle(),12F,15F,rowY,color());
                rowY+=font.height(8F)+4F;
                for(Row row:rows()){
                    font.draw(host.componentTextTask(),row.label(),8F,12F,rowY,labelColor);
                    float valueWidth=font.width(row.value(),8F);
                    font.draw(host.componentTextTask(),row.value(),8F,
                            CARD_X+width-CARD_PADDING-valueWidth,rowY,textColor);
                    rowY+=font.height(8F);
                }
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
