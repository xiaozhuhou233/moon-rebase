package moon.visual.hud;

import java.awt.Color;
import moon.visual.render.*;

/** Nightly hu watermark, with f8's verified text/logo dispatch and colors.
 * hu's literal geometry and Material icons are recovered from the classfile.
 * hu's encrypted strings and numeric calls are now verified by full-table DES decoding.
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
        default MsdfFontBase iconFont(){return selectedFont();}
        default ShaderDispatcher iconDispatcher(){throw new UnsupportedOperationException("Missing Material icon dispatcher");}
        default boolean blurEnabled(){return false;}
        default String buildIdentifier(){return "256ae016d1ab5f21124f38e269e06bb26cfdc63d".substring(0,7);}
        default void setLogoLinearSampling(){}
    }
    private final Host host;
    private final HudColors colors;
    private Config config;
    public WatermarkHudRenderer(Host host,HudColors colors){this.host=host;this.colors=colors;}
    private void ensureConfig() {
        if(config==null)config=host.findHudConfig();
    }
    private int color() {return colors.color(0F,config,config.colorMode(),config.colorOne(),config.colorTwo());}
    /** f8.a(): build identifier, session identity, server and live FPS. */
    public String[] informativeParts() {
        String server="Singleplayer";
        if(host.serverData()!=null)server=host.serverData().address(); // two original lookups
        return new String[]{"moon",host.buildIdentifier(),host.buildSuffix(),server,host.framesPerSecond()+" fps"};
    }
    public static final float INFO_SIZE=5.85F;
    public static final String[] INFO_ICONS={"\ue86f","\ue7fd","\ue157","\ue8e5"};
    // hu seed 7931392571438. Background measurement intentionally uses its
    // own delimiter; the draw loop uses a bold dot and two spaces before icons' text.
    public static final String SECTION_GAP="  \u00a77\u00a7l\u00b7\u00a7r  ",ICON_GAP="  ";
    public static final String MEASURE_GAP="    \u00a77\u00b7  \u00a7r";
    public static final int INFO_ICON_COLOR=0xffcccccc;
    public record InfoPart(String text,String icon,float x,float iconWidth){}
    public record InfoLayout(java.util.List<InfoPart> parts,float width,float height){}
    private InfoLayout frameInfo;
    public InfoLayout informativeLayout(){
        MsdfFontBase font=host.selectedFont(),icons=host.iconFont();
        String[] values=informativeParts();var parts=new java.util.ArrayList<InfoPart>();float x=0;
        for(int i=0;i<values.length;i++){
            String icon=i==0?"":INFO_ICONS[i-1],content=(i==0?"":ICON_GAP)+values[i];
            String text=content+(i+1<values.length?SECTION_GAP:"");
            float iconWidth=icons.width(icon,INFO_SIZE);parts.add(new InfoPart(text,icon,x,iconWidth));
            x+=font.width(content,INFO_SIZE)+(i+1<values.length?font.width(SECTION_GAP,INFO_SIZE):0F)+iconWidth;
        }
        float measured=font.width(String.join(MEASURE_GAP,values),INFO_SIZE)
            +icons.width(String.join("",INFO_ICONS),INFO_SIZE)+11F;
        return new InfoLayout(java.util.List.copyOf(parts),measured,font.height(INFO_SIZE)+11F);
    }
    private InfoLayout info(){if(frameInfo==null)frameInfo=informativeLayout();return frameInfo;}
    @Override public void prepareRectangles(DrawTask task) {frameInfo=null;}
    @Override public void prepareBackground(DrawTask task) {
        ensureConfig();MsdfFontBase font=host.selectedFont(); // even when disabled
        if(!config.enabled()||config.style()!=Style.INFORMATIVE)return;
        InfoLayout info=info();
        task.add(new UiGeometry.RoundedRectangle(7.25F,8.25F,info.width(),info.height(),6.5F,
            new ArgbColor(0xff000000).withAlpha(host.blurEnabled()?.275F:.4F).getRGB(),host.drawing().markers));
    }
    @Override public void prepareMask(DrawTask task) {
        ensureConfig();MsdfFontBase font=host.selectedFont();
        if(!config.enabled()||config.style()!=Style.INFORMATIVE)return;
        InfoLayout info=info();
        task.add(new UiGeometry.RoundedRectangle(7F,8F,info.width()+.5F,info.height()+.5F,6.5F,Color.BLACK.getRGB(),host.drawing().markers));
    }
    @Override public void prepareText(DrawTask ignored) {
        ensureConfig();MsdfFontBase font=host.selectedFont();
        if(host.componentTextTask()==null||!config.enabled())return;
        switch(config.style()) {
            case TEXT:
                if(config.customTextFont())font.draw(host.componentTextTask(),config.text().replace("&","\u00a7"),9F,2F,2F,color());
                break;
            case INFORMATIVE:
                InfoLayout info=info();
                for(InfoPart part:info.parts())font.draw(host.componentTextTask(),part.text(),INFO_SIZE,
                    12.75F+part.x()+part.iconWidth(),8.25F+info.height()/2F-font.height(INFO_SIZE)/2F+.5F,
                    part.icon().isEmpty()?color():-1,true,false,0F,-.02F);
                break;
            default:break;
        }
    }
    @Override public void renderAfterTasks() {
        ensureConfig();
        if(!config.enabled())return;
        switch(config.style()) {
            case INFORMATIVE:renderIcons();break;
            case LOGO:renderLogo();break;
            case TEXT:
                if(!config.customTextFont())host.drawVanilla(config.text().replace("&","\u00a7"),2F,2F,color());
                break;
            default:break;
        }
    }
    private void renderIcons(){
        MsdfFontBase font=host.iconFont();InfoLayout info=info();
        DrawTask icons=DrawTask.builder(host.drawing()).dispatcher(host.iconDispatcher()).texture(font::bind).build();
        for(InfoPart part:info.parts())if(!part.icon().isEmpty())font.draw(icons,part.icon(),INFO_SIZE,12.75F+part.x(),
            8.25F+info.height()/2F-font.secondMetric(part.icon(),INFO_SIZE)/4F,INFO_ICON_COLOR,true,false,0F);
        RenderTransformState.setScale(config.scale(),config.scale(),1F);
        try{icons.draw();}finally{RenderTransformState.resetScale();}
    }
    private void renderLogo() {
        host.enableTexture2D();host.bindResource(LOGO_TEXTURE);
        host.setLogoLinearSampling();
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
        RenderTransformState.setScale(config.scale(),config.scale(),1F);
        try{logo.draw();}finally{RenderTransformState.resetScale();host.bindTexture(0);}
    }
}
