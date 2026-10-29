package moon.client;
import moon.module.visual.EffectsModule;
import moon.module.visual.effects.EffectsSettings;
import moon.module.visual.ClickGuiModule;
import moon.module.visual.clickgui.ClickGuiSettings;
import moon.module.visual.hud.HudSettings;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import moon.visual.gui.*;
import moon.visual.hud.*;
import moon.module.visual.TargetHudModule;
import moon.module.visual.BlockAnimationsModule;
import moon.module.visual.CustomMinecraftModule;
import moon.module.visual.FullBrightModule;
import moon.module.visual.TimeChangerModule;
import moon.module.visual.PointersModule;
import moon.module.visual.CameraModule;
import moon.module.visual.EspModule;
import moon.module.visual.RichPresenceModule;
import moon.module.visual.ModelModifierModule;
import moon.module.visual.NametagsModule;
import moon.visual.render.*;
import moon.visual.shader.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import moon.visual.notification.NotificationType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;

/** Render-thread destination composition. Never depends on tools/* test hosts. */
public final class McpVisualServices implements AutoCloseable,SettingWidgetFactory.Host,CategoryPanel.Host,
        NativeClickGuiScreen.Services,ModalOverlay.Host,ModalButton.Host {
    private static final Logger LOG=LogManager.getLogger("MoonVisual");
    private static McpVisualServices instance;
    final Minecraft mc;
    final McpVisualProfile profile;
    final LwjglMcpStateAccess state=new LwjglMcpStateAccess();
    final LwjglDrawBackend backend=new LwjglDrawBackend(true,state);
    final Hooks hooks=new Hooks();
    final ShaderDiagnostics diagnostics=new ShaderDiagnostics(){
        public void info(String text,Object... args){LOG.info(text,args);}
        public void error(String text,Object... args){LOG.error(text,args);}
    };
    final ShaderSourceLoader.ResourceAccess resources=path->{
        // h_ keeps its streams: detach at this host boundary and close the actual classpath stream.
        try(InputStream input=McpVisualServices.class.getResourceAsStream(path)){
            return input==null?null:new ByteArrayInputStream(input.readAllBytes());
        }
    };
    final McpMainFramebufferView main;
    final DrawServices draw;
    final LwjglMsdfFontHost textures;
    final Map<String,MsdfFont> fonts=new HashMap<>();
    final UiDispatchers.Msdf msdf;
    final UiDispatchers.RoundedRectangle rounded;
    final UiDispatchers.Rectangle rectangle;
    final UiDispatchers.Gradient gradient;
    final UiDispatchers.OutlineRectangle outline;
    final UiDispatchers.ColoredTexture coloredTexture;
    final UiDispatchers.RoundedTexture roundedTexture;
    private SettingWidgetTreeRenderer widgets;
    private BezierEditorRenderer curves;
    private SettingWidgetFactory factory;
    private McpStencilOwner stencilOwner;
    private StencilRenderUtil stencil;
    ManagedPostProcessor post;
    public EffectsSettings effectsSettings;
    public ClickGuiSettings clickSettings;
    public HudSettings hudSettings;
    EffectsModule effects;
    McpHudServices hud;
    McpGlowServices glow;
    McpNotificationServices notifications;
    McpVisualConfigStore config;
    McpBindings bindings;
    public McpModuleServices nativeModules;
    McpClientProfiles profiles;
    public TargetHudModule targetHud;
    McpSessionHud sessionHud;
    McpEventTrackerAddon eventTracker;
    public BlockAnimationsModule blockAnimations;
    public CustomMinecraftModule customMinecraft;
    public FullBrightModule fullBright;
    public TimeChangerModule timeChanger;
    public PointersModule pointers;
    public CameraModule camera;
    public EspModule esp;
    public RichPresenceModule richPresence;
    public ModelModifierModule modelModifier;
    public NametagsModule nametags;
    private ModalButton closeButton;
    private McpClickGuiScreen nativeScreen;
    private McpImGuiBackend imGui;
    private boolean closed;
    private int wheel;
    private final McpLuaModules luaModules=new McpLuaModules(hooks);
    boolean effectsEnabled=true,hudEnabled=true;
    private final List<ModuleRowWidget.Module> visualModules=new ArrayList<>();
    private final List<ModuleRowWidget.Module> scriptModules=new ArrayList<>();
    private final Category visual=new Category("Visual","\ue8b6"),scripts=new Category("Scripts","\ue86f"),combat=new Category("Combat","\ue8ff"),player=new Category("Player","\ue7fd"),exploit=new Category("Exploit","\ue86f");

    public static void start(Minecraft mc){
        if(instance!=null)throw new IllegalStateException("Visual services already started");
        McpVisualServices created=null;
        try{
            created=new McpVisualServices(mc,new McpVisualProfile());
            McpVisualServices active=created;
            McpVisualEvents.install(new McpVisualEvents.Binding(created.effects,created.hud.pipeline,created.clickSettings.module(),
                created::effectsEnabled,created::hudEnabled,created::tick,created::tickPost,created::resize,created,
                ticks->{active.glow.worldPost(ticks);active.nativeModules.backtrack.render();},created::overlayPre,created::prepareNativeOverlay,created::drawNativeOverlay,created::maskNativeOverlay));
            instance=created;LOG.info("Visual services initialized: {} (Right Shift opens ClickGUI)",created.profile.ID);
        }catch(Exception|Error failure){
            if(created!=null)try{created.close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw new IllegalStateException("Moon visual service initialization failed",failure);
        }
    }
    public static McpVisualServices current(){return instance;}
    public static void playerMotionPhase(net.minecraft.client.entity.EntityPlayerSP player){
        McpVisualServices current=instance;
        if(current!=null&&!current.closed&&player==current.mc.thePlayer)current.fullBright.motionPhase();
    }
    public static boolean cancelTimeUpdate(){return instance!=null&&!instance.closed&&instance.timeChanger.cancelsTimePacket();}
    public static void capturePointerCamera(float ticks){
        McpVisualServices v=instance;if(v!=null&&!v.closed){v.pointers.worldPost();v.glow.esp.capture(ticks);}
    }
    public static void captureEspProjectionCamera(java.nio.FloatBuffer model,java.nio.FloatBuffer projection,java.nio.IntBuffer viewport){
        McpVisualServices v=instance;if(v!=null&&!v.closed)v.glow.esp.captureCamera(model,projection,viewport);
    }
    public static CameraModule camera(){return instance==null||instance.closed?null:instance.camera;}
    public static void captureCameraOcclusion(double x,double y,double z){CameraModule c=camera();if(c!=null)c.updateOcclusion(x,y,z);}
    public static void captureEspBoxCamera(double x,double y,double z){McpVisualServices v=instance;if(v!=null&&!v.closed)v.esp.captureCamera(x,y,z);v.nativeModules.backtrack.captureCamera(x,y,z);}
    public static boolean cameraFlag(String key){
        CameraModule c=camera();if(c==null)return false;
        return switch(key){
            case "hurt"->c.active(c.noHurtCam);case "boss"->c.active(c.hideBossBar);
            case "title"->c.active(c.hideTitle);case "achievements"->c.active(c.hideAchievements);
            case "score"->c.active(c.hideScoreIndex);case "speed"->c.active(c.removeSpeedEffect);
            case "batch"->c.active(c.entityBatching);default->false;
        };
    }
    public static boolean cameraSkipEntity(net.minecraft.entity.Entity e){CameraModule c=camera();return c!=null&&c.skipEntity(e);}
    public static boolean cameraSkipTile(net.minecraft.tileentity.TileEntity t){CameraModule c=camera();return c!=null&&c.skipTile(t);}
    public static boolean cameraParticleVisible(net.minecraft.entity.Entity p){CameraModule c=camera();return c==null||c.particleVisible(p);}
    public static boolean cameraCapturing(){CameraModule c=camera();return c!=null&&c.capturing();}
    public static void vanillaHud(Runnable body,Runnable hotbar,Runnable crosshair,Runnable vignette){
        CameraModule c=camera();if(c==null)body.run();else c.renderHud(body,hotbar,crosshair,vignette);
    }
    public static boolean customScoreboardEnabled(){return instance!=null&&instance.customMinecraft.replacesScoreboard();}
    public static boolean customChatEnabled(){return instance!=null&&instance.customMinecraft.replacesChat();}
    public static MsdfFontBase customChatFont(){
        return customChatEnabled()?instance.customMinecraft.chatFont():null;
    }
    public static net.minecraft.util.IChatComponent customChatComponentAt(int x,int y){
        return customChatEnabled()?instance.customMinecraft.chatComponentAt(x,y):null;
    }
    /** Destination addon registration. The registry owns AutoCloseable component cleanup.
     * The original Event Tracker parser/data source must be supplied by its host. */
    public <T extends DraggableHudComponent>T registerHudComponent(java.util.function.Function<HudRenderPipeline,T> factory){return hud.register(factory);}
    public void unregisterHudComponent(DraggableHudComponent component){hud.unregister(component);}
    public List<DraggableHudComponent> hudComponents(){backend.requireOpen();return List.copyOf(hud.pipeline.getComponents());}
    public MsdfFontBase hudFont(){backend.requireOpen();return selectedFont();}
    public static void hudEditorOpened(){if(instance!=null)instance.hud.editorOpened();}
    public static void hudEditorClosed(){if(instance!=null){instance.sessionHud.release(0);instance.targetHud.finishDrag();instance.hud.editorClosed();}}
    public static boolean hudMousePressed(int x,int y,int button){return instance!=null&&(instance.sessionHud.press(x,y,button)||instance.targetHud.press(x,y,button)||instance.hud.mousePressed(x,y,button));}
    public static boolean hudMouseMoved(int x,int y,int button){return instance!=null&&(instance.sessionHud.move(x,y,button)||instance.targetHud.move(x,y,button)||instance.hud.mouseMoved(x,y,button));}
    public static boolean hudMouseReleased(int button){return instance!=null&&(instance.sessionHud.release(button)||instance.targetHud.release(button)||instance.hud.mouseReleased(button));}
    public static void drawHudEditor(int x,int y){if(instance!=null)instance.hud.drawEditor(x,y);}
    public static boolean hideVanillaName(net.minecraft.entity.Entity entity){
        McpVisualServices current=instance;return current!=null&&current.glow.esp.hidesVanillaName(entity);
    }
    public static boolean modelModifierFlag(net.minecraft.entity.Entity entity,ModelModifierModule.Flag flag){
        McpVisualServices current=instance;return current!=null&&!current.closed&&current.modelModifier.flag(entity,flag);
    }
    public static String nametagInformation(net.minecraft.entity.Entity entity){
        McpVisualServices current=instance;return current!=null&&!current.closed?current.nametags.information(entity):null;
    }
    public static boolean nametagSeparateInformation(){return instance!=null&&!instance.closed&&instance.nametags.separateInformation.get();}
    public static boolean roundedNametagBackground(net.minecraft.entity.Entity entity){
        McpVisualServices current=instance;return current!=null&&!current.closed&&current.nametags.applies(entity)&&current.nametags.roundedBackground.get();
    }
    List<ModuleRowWidget.Module> registeredModules(){
        List<ModuleRowWidget.Module> result=new ArrayList<>(visualModules);result.addAll(scriptModules);if(nativeModules!=null)result.addAll(nativeModules.modules());return result;
    }
    private boolean effectsEnabled(){return effectsEnabled;}
    private boolean hudEnabled(){return hudEnabled;}

    public McpVisualServices(Minecraft mc,McpVisualProfile profile)throws IOException {
        this.mc=Objects.requireNonNull(mc);this.profile=Objects.requireNonNull(profile);
        main=new McpMainFramebufferView(mc::getFramebuffer);
        draw=new DrawServices(backend,diagnostics,hooks,resources,new EngineAttributeParser(),()->scaledWidth(),()->scaledHeight());
        textures=new LwjglMsdfFontHost(resources,state);
        msdf=new UiDispatchers.Msdf(draw);rounded=new UiDispatchers.RoundedRectangle(draw,()->1);
        rectangle=new UiDispatchers.Rectangle(draw);gradient=new UiDispatchers.Gradient(draw);
        outline=new UiDispatchers.OutlineRectangle(draw,()->1);coloredTexture=new UiDispatchers.ColoredTexture(draw);
        roundedTexture=new UiDispatchers.RoundedTexture(draw,()->1);
        try{
            for(ShaderDispatcher d:new ShaderDispatcher[]{msdf,rounded,rectangle,gradient,outline,coloredTexture,roundedTexture}){
                d.initialize();if(GL20.glGetProgrami(d.getProgram().getProgram(),GL20.GL_LINK_STATUS)!=1)
                    throw new IOException("UI shader failed: "+d.getClass().getName());
            }
            font("inter_medium");font("Inter-SemiBold");font("Roboto-Medium");font("MaterialIcons-Regular");
            SettingWidgetRenderer base=new SettingWidgetRenderer(draw,()->msdf,()->rounded);
            widgets=new SettingWidgetTreeRenderer(draw,base,()->rectangle,()->gradient,()->outline,profile.gradientEstimate,profile.outlineEstimate);
            curves=new BezierEditorRenderer(draw,()->rounded,()->rectangle,org.lwjgl.opengl.GL11::glEnable,org.lwjgl.opengl.GL11::glPointSize);
            factory=new SettingWidgetFactory(this,profile.toggle,profile.text,System::currentTimeMillis);
            stencilOwner=new McpStencilOwner(mc,main,state);stencil=new StencilRenderUtil(stencilOwner,()->98);
            post=ManagedPostProcessor.originalEffects(new LwjglPostProcessBackend.Host(){
                public boolean isWindowVisible(){return org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(Display.getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)!=0;}
                public int displayWidth(){return mc.displayWidth;}public int displayHeight(){return mc.displayHeight;}
                public double scaledWidth(){return McpVisualServices.this.scaledWidth();}public double scaledHeight(){return McpVisualServices.this.scaledHeight();}
                public PostProcessBackend.Framebuffer mainFramebuffer(){return main;}
                public void publishMaskEvent(boolean shadow){if(shadow)effectsSettings.shadowColor.render(()->McpVisualEvents.mask(true));else McpVisualEvents.mask(false);}
                public void prepareComposite(){mc.entityRenderer.setupOverlayRendering();GlStateManager.color(1,1,1,1);}
                public int[] glowColors(){return hudSettings==null?FlowingGlow.palette(0xff8b6cff,0xff51d8ef):FlowingGlow.palette(hudSettings.textColorOne.get(),hudSettings.textColorTwo.get());}
            },state,resources,diagnostics,3,3,System::currentTimeMillis);
            effectsSettings=new EffectsSettings(hooks,post.blurPipeline(),post.shadowPipeline());
            effects=effectsSettings.createModule(()->true,new EffectsModule.RenderHooks(){
                public void publishScreenEvent(){McpVisualEvents.screen();}
                public void disableDepth(){GlStateManager.disableDepth();}public void enableDepth(){GlStateManager.enableDepth();}
            });
            clickSettings=new ClickGuiSettings(hooks,new ClickGuiModule.Backend(){
                public ClickGuiModule.NativeScreen createNativeScreen(ClickGuiModule module){
                    return nativeScreen=new McpClickGuiScreen(McpVisualServices.this,clickSettings,profile.screen,System::currentTimeMillis);
                }
                public ClickGuiModule.Screen createImGuiScreen(){
                    if(imGui==null)imGui=new McpImGuiBackend(mc.mcDataDir.toPath());
                    return new McpImGuiScreen(McpVisualServices.this,imGui);
                }
                public Object currentScreen(){return mc.currentScreen;}
                public void displayScreen(ClickGuiModule.Screen screen){mc.displayGuiScreen((GuiScreen)screen);}
                public Object createUiColor(int argb){return uiColor(argb);}
                public void applyGlobalColor(int argb){/* Other destinations may subscribe; config remains authoritative here. */}
                public void invokeOriginalModuleE(){/* Migration opener is an action, not a persistent enabled module. */}
            },()->true,mode->mode==ClickGuiModule.Mode.DEFAULT?"Default":"ImGui");
            hudSettings=new HudSettings(hooks,new HudSettings.Labels(){
                public String colorMode(HudSettings.ColorMode value){return value.name();}
                public String textCase(HudSettings.TextCase value){return value.name();}
            },dirty->{if(hud!=null)hud.pipeline.setDirty(dirty);});
            hud=new McpHudServices(this);
            glow=new McpGlowServices(this);
            esp=new EspModule(mc,draw,glow.settings,hooks,value->{glow.enabled=value;if(!value)glow.resized();},glow.mask.predicate());
            notifications=new McpNotificationServices(this);
            targetHud=new TargetHudModule(mc,this::targetHudTarget,hudSettings,hooks,new TargetHudModule.Rendering(){
                public DrawServices drawing(){return draw;}
                public ShaderDispatcher rounded(){return rounded;}
                public ShaderDispatcher texture(){return roundedTexture;}
                public ShaderDispatcher text(){return msdf;}
                public MsdfFontBase font(){return McpVisualServices.this.font("Roboto-Medium");}
                public int fpsFloor(){return profile.fpsFloor;}
                public void invalidate(){post.requestShadowUpdate();post.requestBlurUpdate();}
                public void isolated(Runnable drawing){McpLuaRenderer.renderHud(drawing);}
            });
            modelModifier=new ModelModifierModule(mc,hooks);
            nametags=new NametagsModule(mc,hooks);
            blockAnimations=new BlockAnimationsModule(hooks);
            sessionHud=new McpSessionHud(this);
            fullBright=new FullBrightModule(mc,hooks);
            timeChanger=new TimeChangerModule(mc,hooks);
            richPresence=new RichPresenceModule(hooks,RichPresenceModule.configurationFile(mc.mcDataDir.toPath().resolve("moon/rich-presence.json")));
            camera=new CameraModule(mc,hooks);
            pointers=new PointersModule(mc,draw,new PointersModule.Rendering(){
                public DrawServices drawing(){return draw;}
                public ShaderDispatcher rectangle(){return rectangle;}
                public ShaderDispatcher text(){return msdf;}
                public MsdfFontBase font(){return McpVisualServices.this.font("Verdana");}
                public void isolated(Runnable action){McpLuaRenderer.renderHud(action);}
            });
            customMinecraft=new CustomMinecraftModule(mc,hooks,new CustomMinecraftModule.Rendering(){
                public DrawServices drawing(){return draw;}
                public ShaderDispatcher rounded(){return rounded;}
                public ShaderDispatcher text(){return msdf;}
                public MsdfFontBase font(){return McpVisualServices.this.font("inter_medium");}
                public void invalidate(){post.requestShadowUpdate();post.requestBlurUpdate();}
                public void isolated(Runnable drawing){McpLuaRenderer.renderHud(drawing);}
            });
            visualModules.add(new VisualModule("Effects",effectsSettings.roots(),()->effectsEnabled,()->effectsEnabled=!effectsEnabled));
            visualModules.add(new VisualModule("HUD",hudSettings.roots(),()->hudEnabled,()->hudEnabled=!hudEnabled));
            visualModules.add(new VisualModule("Notifications",notifications.settings(),()->true,()->{}));
            visualModules.add(targetHud);
            visualModules.add(modelModifier);
            visualModules.add(nametags);
            visualModules.add(blockAnimations);
            visualModules.add(customMinecraft);
            visualModules.add(fullBright);
            visualModules.add(timeChanger);
            visualModules.add(pointers);
            visualModules.add(camera);
            visualModules.add(esp);
            visualModules.add(richPresence);
            visualModules.add(new VisualModule("ClickGUI",clickSettings.roots(),()->mc.currentScreen==nativeScreen,()->clickSettings.module().openSelectedScreen()));
            closeButton=ModalButton.sharedClose(this,"Close");
            config=new McpVisualConfigStore(mc.mcDataDir.toPath().resolve("moon/visual-config.json"));
            config.bind("effects",effectsSettings.roots(),()->effectsEnabled,value->effectsEnabled=value);
            config.bind("hud",hudSettings.roots(),()->hudEnabled,value->hudEnabled=value);
            config.bind("notifications",notifications.settings(),null,null);
            config.bind("targethud",targetHud.settings(),targetHud::enabled,targetHud::setEnabled);
            config.bind("modelmodifier",modelModifier.settings(),modelModifier::enabled,modelModifier::setEnabled);
            config.bind("nametags",nametags.settings(),nametags::enabled,nametags::setEnabled);
            config.bind("blockanimations",blockAnimations.settings(),blockAnimations::enabled,blockAnimations::setEnabled);
            config.bind("customminecraft",customMinecraft.settings(),customMinecraft::enabled,customMinecraft::setEnabled);
            config.bind("fullbright",fullBright.settings(),fullBright::enabled,fullBright::setEnabled);
            config.bind("timechanger",timeChanger.settings(),timeChanger::enabled,timeChanger::setEnabled);
            config.bind("pointers",pointers.settings(),pointers::enabled,pointers::setEnabled);
            config.bind("camera",camera.settings(),camera::enabled,camera::setEnabled);
            config.bind("esp",esp.settings(),esp::enabled,esp::setEnabled);
            config.bind("richpresence",richPresence.settings(),richPresence::enabled,richPresence::setEnabled);
            config.bind("clickgui",clickSettings.roots(),null,null);
            try{config.load();}catch(IOException failure){LOG.error("Visual settings not loaded; original file kept and automatic saving disabled",failure);}
            eventTracker=McpEventTrackerAddon.registerBuiltin(this);
            nativeModules=new McpModuleServices(mc,draw,hooks);
            luaModules.refresh(scriptModules);
            profiles=new McpClientProfiles(this);
            bindings=new McpBindings(mc.mcDataDir.toPath().resolve("moon/keybinds.properties"),this::registeredModules,
                text->{if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a7b[Moon] \u00a7r"+text));else LOG.info(text);});
            invalidateSettings();
        }catch(IOException|RuntimeException|Error failure){try{close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    MsdfFont font(String name){return fonts.computeIfAbsent(name,n->{
            // bo literal (smooth, boldWeight, weight), through hV -> hU.
            // Atlas IDs are the existing migration mappings; SF is still unresolved.
            float[] tuple=switch(n){
                case "Roboto-Medium" -> new float[]{0.2F,0.04F,-0.99F};
                case "inter_medium" -> new float[]{0.3F,0.05F,-0.99F};
                case "Inter-SemiBold" -> new float[]{0.325F,0.05F,-0.99F};
                case "Tahoma","Verdana","SFUI" -> new float[]{0.25F,0.05F,-0.99F};
                case "SourceSansPro-Regular","OpenSans-Medium","OpenSans-SemiBold","MaterialIcons-Regular" -> new float[]{0.25F,0.03F,-0.99F};
                case "ProductSans" -> new float[]{0.1F,0.04F,-0.97F};
                case "Volte-SemiBold" -> new float[]{0.15F,0.04F,-0.99F};
                case "MaterialSymbolsOutlined-Regular" -> new float[]{0.3F,0.03F,-0.99F};
                // Extra atlases without a recovered named bo tuple retain the migration policy.
                default -> new float[]{.5F,.05F,-1F};
            };
            return new MsdfFont(n,tuple[0],tuple[1],tuple[2],new MsdfAtlasLoader(resources,textures,diagnostics,hooks),MsdfFont.Parameters.migrationDefaults(),hooks);
        });}
    MsdfFontBase selectedFont(){
        return font(switch(hudSettings.font.get()){
            case ROBOTO->"Roboto-Medium";case PRODUCT_SANS->"ProductSans";case VOLTE->"Volte-SemiBold";
            case SF_UI->"SFUI";
        });
    }
    private void tick(){
        richPresence.tick();
        timeChanger.tick();
        if(blockAnimations!=null)blockAnimations.tick();
        if(nativeModules!=null)nativeModules.tick();
        if(mc.theWorld==null)glow.resized();
        if(config!=null)try{config.poll(System.currentTimeMillis());}catch(IOException|RuntimeException failure){LOG.error("Could not save visual settings",failure);}
        if(luaModules.refresh(scriptModules)&&nativeScreen!=null&&nativeScreen.controller().getScriptsPanel()!=null)
            nativeScreen.controller().getScriptsPanel().initialize();
        bindings.reconcileHeld(Keyboard::isKeyDown);
    }
    private void tickPost(){timeChanger.tick();camera.tick();hud.tickPost();}
    private void overlayPre(float partialTicks){
        glow.overlayPre(partialTicks);
        if(pointers.enabled()){pointers.prepare(partialTicks);pointers.draw();}
    }
    private net.minecraft.entity.EntityLivingBase targetHudTarget(){
        if(nativeModules==null||mc.theWorld==null||!nativeModules.killAura.enabled())return null;
        var target=nativeModules.killAura.target();
        if(target==null)return null;
        net.minecraft.entity.Entity entity=mc.theWorld.getEntityByID(target.id());
        return entity instanceof net.minecraft.entity.EntityLivingBase living?living:null;
    }
    private void prepareNativeOverlay(){
        if(effectsEnabled&&effectsSettings.shadowEnabled.get())post.requestShadowUpdate();
        notifications.prepare();
        targetHud.prepare();
        sessionHud.prepare();
        customMinecraft.prepare();
        if(targetHud.glowEnabled()||sessionHud.frame()!=null)post.requestBlurUpdate();
    }
    private void maskNativeOverlay(boolean shadow){
        if(shadow){notifications.shadowColor.render(()->notifications.mask(true));targetHud.mask(true);nativeModules.session.shadowColor.render(()->sessionHud.mask(true));customMinecraft.mask(true);}
        else{notifications.mask(false);targetHud.mask(false);sessionHud.mask(false);customMinecraft.mask(false);}
    }
    private void drawNativeOverlay(){notifications.draw();sessionHud.draw();nativeModules.chest.renderSilent(0);targetHud.draw();customMinecraft.draw();}
    private void invalidateSettings(){post.requestShadowUpdate();post.requestBlurUpdate();hud.pipeline.setDirty(true);glow.resized();}
    private void resize(){camera.invalidate();stencilOwner.resized();post.requestShadowUpdate();post.requestBlurUpdate();hud.pipeline.setDirty(true);glow.resized();}
    public void openClickGui(){backend.requireOpen();clickSettings.module().openSelectedScreen();}
    @Override public void renderFrame(Runnable drawing){
        try(LwjglDrawBackend.TransientMemoryScope scope=backend.transientMemoryScope()){drawing.run();}
    }
    public void mouseWheel(int delta){wheel+=delta;}
    public CategoryPanel.Host categoryHost(){return this;}
    public List<? extends CategoryPanel.Category> categories(){return Arrays.asList(combat,player,exploit,visual,scripts);}
    public boolean isScriptsCategory(CategoryPanel.Category category){return category==scripts;}
    public List<? extends ModuleRowWidget.Module> modules(CategoryPanel.Category category){return category==exploit?(nativeModules==null?List.of():nativeModules.modules(moon.module.NativeModule.Category.EXPLOIT)):category==player?(nativeModules==null?List.of():nativeModules.modules(moon.module.NativeModule.Category.PLAYER)):category==combat?(nativeModules==null?List.of():nativeModules.modules(moon.module.NativeModule.Category.COMBAT)):category==scripts?scriptModules:visualModules;}
    public GroupSettingWidget.Factory settingFactory(){return factory;}
    public CategoryPanel.Parameters categoryParameters(){return profile.category;}
    public ShaderUniform.MarkerHooks markers(){return hooks;}
    public SettingWidgetFactory.Drawing drawing(){return widgets;}
    public McpVisualProfile.Palette uiColor(int argb){return profile.palette(argb);}
    public ClickGuiWidget.Config clickGuiConfig(){return clickSettings;}
    public MsdfFontBase interMedium(){return font("inter_medium");}public MsdfFontBase interSemiBold(){return font("Inter-SemiBold");}
    public MsdfFontBase materialIcons(){return font("MaterialIcons-Regular");}public MsdfFontBase robotoMedium(){return font("Roboto-Medium");}
    public int framesPerSecond(){return Minecraft.getDebugFPS();}public int animationFpsFloor(){return profile.fpsFloor;}
    public int scaledWidth(){return new ScaledResolution(mc).getScaledWidth();}public int scaledHeight(){return new ScaledResolution(mc).getScaledHeight();}
    public float scaledScreenHeight(){return scaledHeight();}public CategoryPanel.ScaledScreen scaledScreen(){return ()->scaledHeight();}
    public boolean childAnimationsActive(){return nativeScreen!=null&&nativeScreen.controller().animation(false).isFinished();}
    public BooleanSupplier childAnimationState(){
        TimedAnimation animation=nativeScreen==null?null:nativeScreen.controller().animation(false);return ()->animation!=null&&animation.isFinished();
    }
    public int pollWheel(){int value=wheel;wheel=0;return value;}
    public int categoryBackground(Object theme){return ((McpVisualProfile.Palette)theme).panel();}
    public int categoryMask(Object theme){return ((McpVisualProfile.Palette)theme).scheme().shadow();}
    public int moduleBackground(SettingWidget.Theme theme){return ((McpVisualProfile.Palette)theme).scheme().inverseOnSurface();}
    public int enabledText(SettingWidget.Theme theme){return ((McpVisualProfile.Palette)theme).onPrimary();}
    public StencilRenderUtil stencil(){return stencil;}public EffectsModule effectsModule(){return effects;}
    public boolean effectsModuleEnabled(EffectsModule module){return effectsEnabled&&module==effects;}
    public boolean isOriginalDmShadow(Object event){return Boolean.TRUE.equals(event);}
    public void enableBlend(){GlStateManager.enableBlend();}public void blendFunc(int s,int d){GlStateManager.blendFunc(s,d);}
    public ModalOverlay.Scheme modalScheme(){return uiColor(clickSettings.colorArgb());}
    public ModalConfiguration.Screen screen(){return nativeScreen;}
    public ModalConfiguration.Screen activeScreen(){return (ModalConfiguration.Screen)mc.currentScreen;}
    public BezierEditor createEditor(BezierSetting setting){return new BezierEditor(setting,curves,profile.curve,hooks);}
    public ModalOverlay createOverlay(ModalConfiguration configuration){
        // Destination placement policy: original kr centers on the launching row,
        // which can put the curve title/close button outside a small viewport.
        ModalConfiguration bounded=ModalConfiguration.builder(hooks).title(configuration.getTitle()).description(configuration.getDescription())
            .content(configuration.getContent()).buttons(configuration.getButtons()).screen(configuration.getScreen())
            .x(()->modalCenter(configuration.getX().get(),Math.max(configuration.getContent().getWidth()+16F,200F),scaledWidth()))
            .y(()->modalCenter(configuration.getY().get(),configuration.getContent().getHeight()+52F+robotoMedium().height(7F)+robotoMedium().height(12F),scaledHeight())).build();
        return new ModalOverlay(this,bounded,profile.modal,System::currentTimeMillis);
    }
    private static float modalCenter(float requested,float extent,int available){
        float half=extent/2F;if(extent+8F>available)return available/2F;
        return Math.max(half+4F,Math.min(requested,available-half-4F));
    }
    public ModalButton sharedCloseButton(){return closeButton;}
    public ModalOverlay.Drawing modalDrawing(){return new ModalOverlay.Drawing(){
        public DrawTask roundedTask(){return widgets.roundedTask();}public DrawTask msdfTask(MsdfFontBase font){return widgets.msdfTask(font);}
        public DrawTaskState.Geometry rounded(float x,float y,float w,float h,float radius,int color){return widgets.rounded(x,y,w,h,radius,color);}
    };}
    public String readClipboard(){return GuiScreen.getClipboardString();}public void writeClipboard(String text){GuiScreen.setClipboardString(text);}
    public void enableRepeatEvents(boolean enabled){Keyboard.enableRepeatEvents(enabled);}
    public boolean allowedCharacter(char character){return ChatAllowedCharacters.isAllowedCharacter(character);}
    public boolean primaryModifier(){return GuiScreen.isCtrlKeyDown();}public boolean secondaryModifier(){return GuiScreen.isShiftKeyDown();}
    public boolean thirdModifier(){return GuiScreen.isAltKeyDown();}public boolean leftButtonDown(){return Mouse.isButtonDown(0);}
    public void errorNotification(String title,String message,long duration){
        notify(NotificationType.ERROR,title,message,duration);
    }
    /** Render-thread addon API; duration is in milliseconds. */
    public void notify(NotificationType type,String title,String message,long duration){notifications.publish(type,title,message,duration);}
    public int liveOwnedResources(){return (sessionHud==null?0:sessionHud.liveTextures())+(camera==null?0:camera.liveFramebuffers())+(imGui==null?0:imGui.liveResources())+backend.liveResources()+textures.liveTextures()+(post==null?0:post.liveDrawResources()+post.liveFramebuffers())+(stencilOwner==null?0:stencilOwner.liveAttachments())+(glow==null?0:glow.liveFramebuffers()+glow.liveAdditionalTextures());}
    @Override public void close(){
        if(closed)return;backend.requireOpen();closed=true;
        if(config!=null)try{config.flush();}catch(IOException|RuntimeException failure){LOG.error("Could not save visual settings on shutdown",failure);}
        if(nativeModules!=null)try{nativeModules.close();}catch(RuntimeException failure){LOG.error("Native module cleanup failed",failure);}
        dev.pier.lua.LuaManager.get().shutdown();
        LOG.info("Closing visual resources: {}",liveOwnedResources());
        if(richPresence!=null)richPresence.close();
        if(sessionHud!=null)sessionHud.close();
        if(blockAnimations!=null)blockAnimations.close();
        if(fullBright!=null)fullBright.close();
        if(timeChanger!=null)timeChanger.close();
        if(pointers!=null)pointers.close();
        if(camera!=null)camera.close();
        if(esp!=null)esp.close();
        try{try{if(notifications!=null)notifications.close();}finally{try{if(glow!=null)glow.close();}finally{if(hud!=null)hud.close();}}}finally{
            try{try{if(imGui!=null)imGui.close();}finally{if(post!=null)post.close();}}finally{
                try{if(stencilOwner!=null)stencilOwner.close();}finally{try{textures.close();}finally{backend.close();}}
            }
        }
        int remaining=liveOwnedResources();LOG.info("Visual resources closed: {}",remaining);
        if(remaining!=0)throw new IllegalStateException("Visual resources remain after shutdown: "+remaining);
        if(instance==this)instance=null;
    }
    static final class Hooks implements DrawServices.MarkerHooks,HudSettings.Hooks {
        private Object[] guard;
        public Object getMarker(){return null;}public void setMarker(String value){}
        public Object[] originalHudGuard(){return guard;}public void setOriginalHudGuard(Object[] value){guard=value;}
    }
    private record Category(String displayName,String icon) implements CategoryPanel.Category{}
    private record VisualModule(String displayName,List<? extends SettingValue<?>> settings,BooleanSupplier state,Runnable action) implements ModuleRowWidget.Module {
        public boolean enabled(){return state.getAsBoolean();}public void toggle(){action.run();}
    }
}
