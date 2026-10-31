package moon.client;

import java.util.*;
import java.io.IOException;
import java.util.function.Function;
import moon.visual.gui.*;
import moon.visual.hud.*;
import moon.visual.render.*;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/** Live Minecraft data hosts for restored jP/f4/f6/f7/f8. No synthetic player/potion data. */
final class McpHudServices implements AutoCloseable {
    private final McpVisualServices visual;
    final HudRenderPipeline pipeline;
    final PotionHudRenderer potions;
    final HudModuleListRenderer moduleList;
    final McpHudLayoutStore layout;
    final McpHudDragController drag;
    private boolean closed;
    private final IdentityHashMap<ModuleRowWidget.Module,Module> modules=new IdentityHashMap<>();
    private final Map<Potion,PotionType> potionTypes=new IdentityHashMap<>();
    McpHudServices(McpVisualServices visual){
        this.visual=visual;
        pipeline=new HudRenderPipeline(new PipelineHost(),visual.hudSettings.pipelineConfig(),visual.profile.hud);
        HudColors colors=new HudColors(visual.profile.hudColors);
        moduleList=new HudModuleListRenderer(new ListHost());
        potions=new PotionHudRenderer(new PotionHost());
        // Destination registration order. Original startup registration remains unresolved.
        pipeline.getRenderers().add(moduleList);
        pipeline.getRenderers().add(new InformationHudRenderer(new InformationHost(),colors));
        pipeline.getRenderers().add(potions);
        pipeline.getRenderers().add(new WatermarkHudRenderer(new WatermarkHost(),colors));
        layout=new McpHudLayoutStore(visual.mc.mcDataDir.toPath().resolve("moon/hud-layout.json"));
        try{layout.load();}catch(IOException failure){visual.diagnostics.error("HUD layout not loaded; original file kept and automatic saving disabled",failure);}
        drag=new McpHudDragController(()->List.copyOf(pipeline.getComponents()),this::editing,
            ()->visual.hudSettings.scale.get(),visual::scaledWidth,visual::scaledHeight,this::layoutChanged,this::saveLayout);
    }
    private boolean editing(){return !closed&&visual.hudEnabled&&visual.mc.theWorld!=null&&visual.mc.currentScreen instanceof GuiChat;}
    private void layoutChanged(){pipeline.setDirty(true);visual.post.requestShadowUpdate();visual.post.requestBlurUpdate();}
    private boolean saveLayout(){try{layout.flush();return true;}catch(IOException|RuntimeException failure){visual.diagnostics.error("Could not save HUD layout; last file retained",failure);return false;}}
    <T extends DraggableHudComponent>T register(Function<HudRenderPipeline,T> factory){
        visual.backend.requireOpen();if(closed)throw new IllegalStateException("HUD services closed");
        T component=Objects.requireNonNull(factory.apply(pipeline));
        if(component.getHud()!=pipeline||pipeline.getComponents().contains(component))throw new IllegalArgumentException("Component must belong to this HUD and be registered once");
        boolean bound=false,listed=false;
        try{
            layout.register(component);bound=true;pipeline.getComponents().add(component);listed=true;
            if(editing())drag.constrain(component);layoutChanged();return component;
        }catch(RuntimeException|Error failure){
            if(listed)pipeline.getComponents().remove(component);if(bound)layout.unregister(component);
            try{closeComponent(component);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }
    void unregister(DraggableHudComponent component){
        visual.backend.requireOpen();if(!pipeline.getComponents().contains(component))return;
        if(drag.captured()==component)drag.finish();
        if(!saveLayout())throw new IllegalStateException("HUD component remains registered until its position can be saved");
        layout.unregister(component);pipeline.getComponents().remove(component);layoutChanged();closeComponent(component);
    }
    void editorOpened(){if(!closed)drag.opened();}
    void editorClosed(){if(!closed){drag.finish();saveLayout();}}
    boolean mousePressed(int x,int y,int button){return !closed&&drag.press(x,y,button);}
    boolean mouseMoved(int x,int y,int button){return !closed&&drag.move(x,y,button);}
    boolean mouseReleased(int button){return !closed&&drag.release(button);}
    void drawEditor(int mouseX,int mouseY){
        if(!editing())return;drag.validate();
        // A release outside the window must not leave a component captured.
        if(drag.captured()!=null&&!org.lwjglx.input.Mouse.isButtonDown(0))drag.finish();
        boolean any=false;float scale=visual.hudSettings.scale.get();
        for(var component:pipeline.getComponents()){
            if(!component.isEnabled())continue;
            drag.constrain(component);
            float left=component.getX()*scale,top=component.getY()*scale,right=left+component.getWidth()*scale,bottom=top+component.getHeight()*scale;
            if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)||right<=left||bottom<=top)continue;
            any=true;
            int color=component==drag.captured()?0xff66ddff:mouseX>=left&&mouseX<=right&&mouseY>=top&&mouseY<=bottom?0xcc66ddff:0x66ffffff;
            Gui.drawRect((int)left,(int)top,(int)Math.ceil(right),(int)top+1,color);Gui.drawRect((int)left,(int)bottom-1,(int)Math.ceil(right),(int)Math.ceil(bottom),color);
            Gui.drawRect((int)left,(int)top,(int)left+1,(int)Math.ceil(bottom),color);Gui.drawRect((int)right-1,(int)top,(int)Math.ceil(right),(int)Math.ceil(bottom),color);
        }
        if(any)visual.mc.fontRendererObj.drawStringWithShadow("Drag HUD elements to move them",4,4,0xffdddddd);
    }
    void tickPost(){
        if(closed)return;drag.validate();
        try{layout.poll(System.currentTimeMillis());}catch(IOException|RuntimeException failure){visual.diagnostics.error("Could not save HUD layout",failure);}
        potions.onClientTick(true);
        Set<ModuleRowWidget.Module> current=Collections.newSetFromMap(new IdentityHashMap<>());
        for(ModuleRowWidget.Module source:visual.registeredModules()){
            current.add(source);Module module=modules.computeIfAbsent(source,Module::new);
            String suffix=source.hudSuffix();
            if(!Objects.equals(module.lastSuffix,suffix)){module.lastSuffix=suffix;moduleList.onSuffixChanged();pipeline.setDirty(true);}
            boolean enabled=source.enabled();
            if(module.lastEnabled!=enabled){
                // Snapshot observer for destination modules. It is not the original i8 event bus.
                moduleList.onModuleToggle(new HudModuleListRenderer.ToggleEvent(){
                    public HudModuleListRenderer.Module module(){return module;}public boolean enabled(){return enabled;}
                });
                module.lastEnabled=enabled;pipeline.setDirty(true);
            }
        }
        Iterator<Map.Entry<ModuleRowWidget.Module,Module>> iterator=modules.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<ModuleRowWidget.Module,Module> entry=iterator.next();if(current.contains(entry.getKey()))continue;
            Module module=entry.getValue();module.removed=true;
            moduleList.onHiddenChanged(new HudModuleListRenderer.HiddenEvent(){
                public HudModuleListRenderer.Module module(){return module;}public boolean hidden(){return true;}public boolean enabled(){return module.lastEnabled;}
            });iterator.remove();
        }
    }
    private class Common {
        public boolean originalGuardIsNull(){return true;}
        public MsdfFontBase selectedFont(){return visual.selectedFont();}
        public DrawServices drawing(){return visual.draw;}
        public DrawTask componentTextTask(){return pipeline.getComponentText();}
        public int framesPerSecond(){return visual.framesPerSecond();}
        public int vanillaWidth(String value){return visual.mc.fontRendererObj.getStringWidth(value);}
        public int vanillaHeight(){return visual.mc.fontRendererObj.FONT_HEIGHT;}
        public void drawVanilla(String value,float x,float y,int color){visual.mc.fontRendererObj.drawString(value,x,y,color,false);}
        public InformationHudRenderer.Resolution resolution(){return new Resolution();}
        // This destination's session username is an explicit replacement for unavailable bw.B.
        public String buildSuffix(){return visual.mc.getSession().getUsername();}
        public void bindResource(String resource){visual.mc.getTextureManager().bindTexture(new ResourceLocation(resource));}
        public void bindTexture(int texture){visual.state.bindTexture(texture);}
    }
    private final class Resolution implements InformationHudRenderer.Resolution {
        private int width=visual.scaledWidth(),height=visual.scaledHeight();
        public void scale(float value){width=(int)(width*value);height=(int)(height*value);}
        public int width(){return width;}public int height(){return height;}
        public void reset(){width=visual.scaledWidth();height=visual.scaledHeight();}
    }
    private final class PipelineHost extends Common implements HudRenderPipeline.Host {
        public ShadowStyle shadowStyle(Object source){
            return source instanceof WatermarkHudRenderer?visual.hudSettings.watermarkShadow:
                source instanceof PotionHudRenderer?visual.hudSettings.potionShadow:
                source instanceof HudModuleListRenderer?visual.hudSettings.listShadow:visual.hudSettings.componentShadow;
        }
        public ShaderDispatcher rectangleDispatcher(){return visual.rectangle;}
        public ShaderDispatcher roundedDispatcher(){return visual.rounded;}
        public ShaderDispatcher msdfDispatcher(){return visual.msdf;}
        public void bindMainFramebuffer(boolean viewport){visual.main.bind(viewport);}
        public void disableDepth(){GlStateManager.disableDepth();}public void enableDepth(){GlStateManager.enableDepth();}
        public void enableBlend(){GlStateManager.enableBlend();}
        public void blendFuncSeparate(int sr,int dr,int sa,int da){GlStateManager.tryBlendFuncSeparate(sr,dr,sa,da);}
        public void fixedFunctionScale(float x,float y,float z){GlStateManager.scale(x,y,z);}
    }
    private final class InformationHost extends Common implements InformationHudRenderer.Host {
        public InformationHudRenderer.Config findHudConfig(){return visual.hudSettings.informationConfig();}
        public boolean isOriginalExcludedScreen(){return visual.mc.currentScreen instanceof GuiChat;} // moom.gE is the original chat screen.
        public float movementSpeed(){
            var player=visual.mc.thePlayer;
            return player==null?0F:(float)(Math.hypot(player.posX-player.prevPosX,player.posZ-player.prevPosZ)*20D);
        }
    }
    private final class PotionHost extends Common implements PotionHudRenderer.Host {
        public PotionHudRenderer.Config findHudConfig(){return visual.hudSettings.potionConfig();}
        public boolean isOriginalChatScreen(){return visual.mc.currentScreen instanceof GuiChat;}
        public boolean hasPlayer(){return visual.mc.thePlayer!=null;}
        public PotionHudRenderer.PotionType[] potionRegistry(){
            PotionHudRenderer.PotionType[] result=new PotionHudRenderer.PotionType[Potion.potionTypes.length];
            for(int i=0;i<result.length;i++){
                Potion potion=Potion.potionTypes[i];if(potion!=null)result[i]=potionTypes.computeIfAbsent(potion,PotionType::new);
            }return result;
        }
        public boolean hasEffect(PotionHudRenderer.PotionType type){return visual.mc.thePlayer.isPotionActive(((PotionType)type).potion);}
        public PotionHudRenderer.Effect effect(PotionHudRenderer.PotionType type){return new Effect(visual.mc.thePlayer.getActivePotionEffect(((PotionType)type).potion));}
        public String translate(String key){return I18n.format(key);}
        public String duration(PotionHudRenderer.Effect effect){return Potion.getDurationString(((Effect)effect).effect);}
        public ShaderDispatcher coloredTextureDispatcher(){return visual.coloredTexture;}
    }
    private record PotionType(Potion potion) implements PotionHudRenderer.PotionType {
        public String translationKey(){return potion.getName();}public int iconIndex(){return potion.getStatusIconIndex();}public int liquidColor(){return potion.getLiquidColor();}
    }
    private record Effect(PotionEffect effect) implements PotionHudRenderer.Effect {public int amplifier(){return effect.getAmplifier();}}
    private final class WatermarkHost extends Common implements WatermarkHudRenderer.Host {
        public WatermarkHudRenderer.Config findHudConfig(){return visual.hudSettings.watermarkConfig();}
        public ShaderDispatcher textureDispatcher(){return visual.coloredTexture;}
        public void enableTexture2D(){GlStateManager.enableTexture2D();}
        public String informativeTitle(){
            var server=visual.mc.getCurrentServerData();
            if(server==null)return "Singleplayer";
            if(server.serverName!=null&&!server.serverName.isEmpty())return server.serverName;
            return server.serverIP==null||server.serverIP.isEmpty()?"Singleplayer":server.serverIP;
        }
        /** Original hj card: AC, CPS L/R, BPS, PPS I/O, TB, player state and host. */
        public List<WatermarkHudRenderer.Row> informativeRows(){
            var player=visual.mc.thePlayer;
            if(player==null)return List.of();
            McpModuleServices services=McpModuleServices.current();
            int leftCps=0,rightCps=0,inPps=0,outPps=0;
            if(services!=null){
                leftCps=services.leftCps();rightCps=services.rightCps();
                int[] rates=services.packetRates();inPps=rates[0];outPps=rates[1];
            }
            double bps=Math.hypot(player.posX-player.prevPosX,player.posZ-player.prevPosZ)*20D;
            var server=visual.mc.getCurrentServerData();
            List<WatermarkHudRenderer.Row> rows=new ArrayList<>();
            rows.add(new WatermarkHudRenderer.Row("AC",""));
            rows.add(new WatermarkHudRenderer.Row("CPS L/R",leftCps+"/"+rightCps));
            rows.add(new WatermarkHudRenderer.Row("BPS",String.format(Locale.ROOT,"%.2f",bps)));
            rows.add(new WatermarkHudRenderer.Row("PPS I/O",inPps+"/"+outPps));
            rows.add(new WatermarkHudRenderer.Row("TB","0ms"));
            rows.add(new WatermarkHudRenderer.Row("Blocking",String.valueOf(player.isBlocking())));
            rows.add(new WatermarkHudRenderer.Row("Sprinting",String.valueOf(player.isSprinting())));
            rows.add(new WatermarkHudRenderer.Row("Sneaking",String.valueOf(player.isSneaking())));
            rows.add(new WatermarkHudRenderer.Row("On ground",String.valueOf(player.onGround)));
            rows.add(new WatermarkHudRenderer.Row("Host",server==null?"Singleplayer":String.valueOf(server.serverIP)));
            return rows;
        }
        public WatermarkHudRenderer.ServerData serverData(){
            var server=visual.mc.getCurrentServerData();return server==null?null:()->server.serverIP;
        }
    }
    private final class ListHost extends Common implements HudModuleListRenderer.Host {
        public HudModuleListRenderer.Config findHudConfig(){return visual.hudSettings.moduleListConfig();}
        public HudModuleListRenderer.Effects findEffects(){return visual.effects::requestShadowUpdate;}
        public DrawTask sidebarTask(){return pipeline.getSidebar();}
        public void drawVanilla(String text,float x,float y,int color,boolean alternate){visual.mc.fontRendererObj.drawString(text,x,y,color,alternate);}
        public long currentTimeMillis(){return System.currentTimeMillis();}
    }
    private final class Module implements HudModuleListRenderer.Module {
        private final ModuleRowWidget.Module source;
        private final TimedAnimation animation=new TimedAnimation(250,0,System::currentTimeMillis,visual.hooks);
        private boolean lastEnabled,removed;private String lastSuffix="";private float width,height;
        Module(ModuleRowWidget.Module source){this.source=source;}
        public boolean isClickGuiModule(){return source.displayName().equals("ClickGUI");}
        public boolean isHidden(){return removed;}public boolean isEnabled(){return !removed&&source.enabled();}
        public String displayName(){return source.displayName();}public String suffix(){return source.hudSuffix();}
        public HudModuleListRenderer.Animation animation(){return new HudModuleListRenderer.Animation(){
            public void resetTo(float value){animation.resetTo(value);}public boolean isFinished(){return animation.isFinished();}
            public void update(float lower,float upper,boolean enabled){animation.update(lower,upper,enabled);}public float get(){return animation.get();}
        };}
        public float animatedWidth(){return width;}public float animatedHeight(){return height;}
        public void setAnimatedWidth(float value){width=value;}public void setAnimatedHeight(float value){height=value;}

    }
    @Override public void close(){
        if(closed)return;drag.finish();saveLayout();closed=true;pipeline.releaseShadowMasks();
        RuntimeException failure=null;
        for(var component:List.copyOf(pipeline.getComponents()))try{closeComponent(component);}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        pipeline.getComponents().clear();
        for(DrawTask task:new DrawTask[]{pipeline.getRectangles(),pipeline.getMask(),pipeline.getBackground(),pipeline.getRendererText(),pipeline.getComponentText(),pipeline.getSidebar(),potions.getIconTask()})
            if(task!=null)try{task.getState().release();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure!=null)throw failure;
    }
    private static void closeComponent(DraggableHudComponent component){if(component instanceof AutoCloseable owner)try{owner.close();}catch(Exception failure){throw new IllegalStateException("Could not close HUD component "+component.getId(),failure);}}
}
