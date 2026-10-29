package moon.visual.hud;

import java.awt.Color;
import java.util.*;
import java.util.function.Function;
import moon.visual.render.*;

/** f4's normal null-sentinel HUD module list, not the l9 ClickGUI screen.
 * Sizes/phases are decoded with the supplied f4 runtime seed; see MODULE_LIST_EVIDENCE.
 * Module animation/dimensions remain on the original module, and mutate during mask PRE.
 */
public final class HudModuleListRenderer implements HudRenderer {
    public enum SuffixStyle { DASH,SPACE,BRACES,NONE }
    public enum TextCase {
        NORMAL,LOWER;
        public Function<String,String> function(){return this==NORMAL?s->s:String::toLowerCase;}
    }
    public interface Animation {
        void resetTo(float value);boolean isFinished();void update(float lower,float upper,boolean enabled);float get();
    }
    public interface Module {
        boolean isClickGuiModule();boolean isHidden();boolean isEnabled();
        String displayName();String suffix();Animation animation();
        float animatedWidth();float animatedHeight();void setAnimatedWidth(float value);void setAnimatedHeight(float value);
    }
    public interface ToggleEvent {Module module();boolean enabled();}
    /** dp is published before i8.l changes: hidden=new hidden flag, enabled=old i8.k. */
    public interface HiddenEvent {Module module();boolean hidden();boolean enabled();}
    public interface Config {
        boolean enabled();boolean customFont();boolean alternateTextPass();boolean background();
        int backgroundAlpha();boolean rounded();int radius();int padding();int lineSpacing();float outerPadding();
        SuffixStyle suffixStyle();TextCase textCase();boolean sidebar();
        HudRenderPipeline.SidebarColorMode sidebarMode();int sidebarColorOne();
        HudRenderPipeline.TextColorMode textMode();int textColorOne();int textColorTwo();float scale();
        void setDirty(boolean value);
    }
    public interface Effects {void requestShadowUpdate();}
    public interface Host {
        boolean originalGuardIsNull();Effects findEffects();Config findHudConfig();
        MsdfFontBase selectedFont();InformationHudRenderer.Resolution resolution();DrawServices drawing();
        DrawTask componentTextTask();DrawTask sidebarTask();
        int vanillaWidth(String text);int vanillaHeight();
        /** Exact f_.b(String,FFI) (false branch) vs f_.a(String,FFI) (true branch). */
        void drawVanilla(String text,float x,float y,int color,boolean alternatePass);
        long currentTimeMillis();
    }
    private final Host host;
    private final List<Module> modules=new ArrayList<>(),removals=new ArrayList<>();
    private long lastAnimation;
    private Effects effects;
    private Config config;
    public HudModuleListRenderer(Host host){this.host=host;lastAnimation=host.currentTimeMillis();}
    private void checkGuard(){if(!host.originalGuardIsNull())throw new UnsupportedOperationException("f4 nonnull rendering sentinel not restored");}
    private void ensureConfig(){boolean normal=host.originalGuardIsNull();if(normal&&effects==null)effects=host.findEffects();if(!normal||config==null)config=host.findHudConfig();}
    public List<Module> getModules(){return modules;}public List<Module> getRemovals(){return removals;}
    public long getLastAnimation(){return lastAnimation;}
    public Effects getEffects(){return effects;}public Config getConfig(){return config;}

    public void onModuleToggle(ToggleEvent event) {
        ensureConfig();boolean originalGuard=host.originalGuardIsNull();Module module=event.module();
        boolean clickGui=module.isClickGuiModule(); // instanceof is evaluated before the sentinel branch
        if(!originalGuard){module.animation().resetTo(0F);module.animation().resetTo(module.animation().get());sort();return;}
        if(clickGui||module.isHidden())return;
        if(event.enabled()&&!modules.contains(module)){modules.add(module);module.animation().resetTo(0F);}
        else module.animation().resetTo(module.animation().get());
        sort();
    }
    public void onHiddenChanged(HiddenEvent event) {
        boolean originalGuard=host.originalGuardIsNull();ensureConfig();Module module=event.module();
        boolean clickGui=module.isClickGuiModule();
        if(!originalGuard){sort();return;}
        if(clickGui)return;
        if(!event.hidden()&&event.enabled()&&!modules.contains(module)){modules.add(module);module.animation().resetTo(0F);}
        else if(event.hidden())removals.add(module); // original allows duplicates
        sort();
    }
    public void onSuffixChanged(){ensureConfig();sort();} // dr's fields are not read
    private void sort(){modules.sort(Comparator.comparingDouble(m->-width(name(m)+suffix(m),9)));config.setDirty(true);}
    public String name(Module module){return config.textCase().function().apply(module.displayName());}
    public String suffix(Module module) {
        boolean normal=host.originalGuardIsNull();String raw=module.suffix();
        if(!normal)return raw;
        if(raw.isEmpty())return "";
        String value;
        switch(config.suffixStyle()) {
            case DASH:value=" \u00a77- "+module.suffix();break;
            case SPACE:value=" \u00a77"+module.suffix();break;
            case BRACES:value=" \u00a77("+module.suffix()+")";break;
            case NONE:value="";break;
            default:throw new IncompatibleClassChangeError();
        }
        return config.textCase().function().apply(value);
    }
    private float width(String text,int size){boolean normal=host.originalGuardIsNull(),custom=config.customFont();if(!normal)return custom?1F:0F;if(!custom)return host.vanillaWidth(text);return host.selectedFont().width(text,size);}
    private float height(int size){boolean normal=host.originalGuardIsNull(),custom=config.customFont();if(!normal)return custom?1F:0F;if(!custom)return host.vanillaHeight();return host.selectedFont().height(size);}
    private int individual(Module module) {
        return config.textColorOne();
    }
    public int color(float offset){return color(offset,4500D);}
    public int color(float offset,double period) {
        boolean normal=host.originalGuardIsNull();
        long now=host.currentTimeMillis();int first=config.textColorOne(),second=config.textColorTwo();
        HudRenderPipeline.TextColorMode mode=config.textMode();
        if(!normal)return mode==HudRenderPipeline.TextColorMode.PULSING?1:mode==HudRenderPipeline.TextColorMode.GRADIENT?2:0;
        if(mode!=HudRenderPipeline.TextColorMode.PULSING&&mode!=HudRenderPipeline.TextColorMode.GRADIENT)return config.textColorOne();
        long cycle=(long)(1.5D*period);
        if(mode==HudRenderPipeline.TextColorMode.PULSING)second=new Color(first).darker().darker().getRGB();
        int[] palette={first,second,first,second,first,second,first};
        return ColorInterpolation.sample(palette,(double)((float)((now+(int)offset*-100L)%cycle)/((float)cycle/2F)));
    }
    @Override public void prepareRectangles(DrawTask task){ensureConfig();}
    @Override public void prepareBackground(DrawTask task) {
        checkGuard();ensureConfig();if(!config.enabled())return;
        if(host.currentTimeMillis()-lastAnimation-250L<0){effects.requestShadowUpdate();config.setDirty(true);}
        preparePass(task,false);
    }
    @Override public void prepareMask(DrawTask task){checkGuard();ensureConfig();if(!config.enabled())return;preparePass(task,true);}
    private void preparePass(DrawTask task,boolean mask) {
        if(!config.enabled())return;
        InformationHudRenderer.Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
        float y=2.5F;boolean sidebar=config.sidebar();float sidebarWidth=sidebar?1F:0F;Module previous=null;
        for(int i=0;i<modules.size();i++) {
            Module module=modules.get(i),next=i<modules.size()-1?modules.get(i+1):null;
            if(!module.animation().isFinished())lastAnimation=host.currentTimeMillis();
            if(!module.isEnabled()&&module.animation().isFinished()&&!removals.contains(module))removals.add(module);
            if(mask)module.animation().update(0F,1F,module.isEnabled());
            float progress=(float)(Math.round((double)module.animation().get()*100D)/100D);
            String suffix=suffix(module),label=name(module)+suffix;
            float width=width(label,9),height=height(9);
            module.setAnimatedWidth(width*progress);
            module.setAnimatedHeight((height+config.lineSpacing())*Math.max(Math.min(progress,1F),0F));
            int padding=config.padding();float offset=-config.outerPadding()-sidebarWidth-padding;
            float right=resolution.width(),x=right-module.animatedWidth()+offset;
            float nextX=next==null?0F:right-next.animatedWidth()+offset;
            float rowY=y+padding;
            if(config.background()) {
                int radius=config.rounded()?config.radius():0;
                UiGeometry.RoundedRectangle rect=new UiGeometry.RoundedRectangle(x-config.outerPadding(),rowY-3.5F,
                    width+config.outerPadding()*2F,height+config.lineSpacing()+1F,radius,
                    mask?Color.BLACK.getRGB():new Color(0,0,0,config.backgroundAlpha()).getRGB(),host.drawing().markers);
                if(i!=modules.size()-1||sidebar)rect.cornerJ(false);
                if(previous!=null){rect.cornerH(false);rect.cornerG(false);}
                if(sidebar)rect.cornerH(false);
                rect.smooth(false);task.add(rect);
            }
            if(sidebar&&!mask) {
                int individual=individual(module);
                int textColor=config.textMode()==HudRenderPipeline.TextColorMode.INDIVIDUAL?individual:config.textColorOne();
                int color;
                if(config.sidebarMode()==HudRenderPipeline.SidebarColorMode.SYNC&&
                    (config.textMode()==HudRenderPipeline.TextColorMode.STATIC||config.textMode()==HudRenderPipeline.TextColorMode.INDIVIDUAL))color=textColor;
                else color=config.sidebarColorOne();
                UiGeometry.RoundedRectangle rect=new UiGeometry.RoundedRectangle(x+width+config.outerPadding()-1F,rowY-3.5F,2F,
                    height+config.lineSpacing()+1F,config.rounded()?1F:0F,color,host.drawing().markers);
                rect.cornerG(false);rect.cornerH(previous==null);rect.cornerI(false);rect.cornerJ(i==modules.size()-1);rect.smooth(false);
                host.sidebarTask().add(rect);
            }
            y+=module.animatedHeight();previous=module;
        }
        resolution.reset(); // original has no finally
    }
    private void removeFinished(){for(Module module:removals)modules.remove(module);removals.clear();}
    @Override public void prepareText(DrawTask task) {
        checkGuard();ensureConfig();if(!config.enabled()||!config.customFont())return;
        MsdfFontBase font=host.selectedFont();InformationHudRenderer.Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
        float y=0F,sidebar=config.sidebar()?1F:0F;
        for(Module module:modules) {
            int padding=config.padding();float x=resolution.width()-module.animatedWidth()-config.outerPadding()-sidebar-padding;
            float rowY=y+padding+config.lineSpacing()/2F-.5F;
            String suffix=suffix(module);int individual=individual(module);
            int color=config.textMode()==HudRenderPipeline.TextColorMode.INDIVIDUAL?individual:config.textColorOne();String name=name(module);
            if(!config.alternateTextPass()) {
                font.draw(task,name,9F,x,rowY+.5F,color,true);
                if(!suffix.isEmpty())font.draw(host.componentTextTask(),suffix,9F,x+font.width(name,9F),rowY+.5F,-1,true);
            }else {
                font.draw(host.componentTextTask(),name,9F,x+.5F,rowY+1F,0x80000000,false);
                font.draw(task,name,9F,x,rowY+.5F,color,false);
                if(!suffix.isEmpty())font.draw(host.componentTextTask(),suffix,9F,x+font.width(name,9F),rowY+.5F,-1);
            }
            y+=module.animatedHeight();
        }
        resolution.reset();removeFinished();
    }
    @Override public void renderAfterTasks() {
        checkGuard();ensureConfig();if(!config.enabled()||config.customFont())return;
        InformationHudRenderer.Resolution resolution=host.resolution();resolution.scale(1F/config.scale());
        float y=0F;int i=0;float sidebar=config.sidebar()?1F:0F;
        for(Module module:modules) {
            int padding=config.padding();float x=resolution.width()-module.animatedWidth()-config.outerPadding()-sidebar-padding;
            float rowY=y+padding+config.lineSpacing()/2F;
            String suffix=suffix(module);int individual=individual(module);
            int color=config.textMode()==HudRenderPipeline.TextColorMode.INDIVIDUAL?individual:color(i*.9F);
            String name=name(module);
            if(!config.alternateTextPass())host.drawVanilla(name+suffix,x,rowY+.5F,color,false);
            else host.drawVanilla(name+suffix,x,rowY+.5F,color,true);
            y+=module.animatedHeight();i++;
        }
        resolution.reset();removeFinished();
    }
}
