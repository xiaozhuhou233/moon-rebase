package moon.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import dev.pier.lua.LuaManager;
import moon.visual.event.HudOverlayPhaseEvent;
import moon.visual.hud.HudRenderPipeline;
import moon.module.visual.ClickGuiModule;
import moon.module.visual.EffectsModule;

/** Destination event transport. This is migration glue, not original event-bus
 * reconstruction. A fully constructed visual service must explicitly install
 * a Binding; source installation alone does not initialize/render the visuals.
 */
public final class McpVisualEvents {
    public interface Scripts {
        void overlay(float partialTicks,boolean pre);
        /** The destination Lua API calls this argument blur, unlike dm's shadow flag. */
        void effect(boolean blur,float partialTicks);
    }
    public static final class Binding {
        public final EffectsModule effects;
        public final HudRenderPipeline hud;
        public final ClickGuiModule clickGui;
        public final BooleanSupplier effectsEnabled,hudEnabled;
        private final Runnable tick,tickPost,resize;
        private final AutoCloseable owner;
        private final Consumer<Float> worldPost,overlayPre;
        private final Runnable overlayPostPrepare,overlayPostDraw;
        private final Consumer<Boolean> overlayMask;
        public Binding(EffectsModule effects,HudRenderPipeline hud,ClickGuiModule clickGui,
                       BooleanSupplier effectsEnabled,BooleanSupplier hudEnabled,
                       Runnable tick,Runnable resize,AutoCloseable owner) {
            this(effects,hud,clickGui,effectsEnabled,hudEnabled,tick,()->{},resize,owner);
        }
        public Binding(EffectsModule effects,HudRenderPipeline hud,ClickGuiModule clickGui,
                       BooleanSupplier effectsEnabled,BooleanSupplier hudEnabled,
                       Runnable tick,Runnable tickPost,Runnable resize,AutoCloseable owner) {
            this(effects,hud,clickGui,effectsEnabled,hudEnabled,tick,tickPost,resize,owner,value->{},value->{});
        }
        public Binding(EffectsModule effects,HudRenderPipeline hud,ClickGuiModule clickGui,
                       BooleanSupplier effectsEnabled,BooleanSupplier hudEnabled,
                       Runnable tick,Runnable tickPost,Runnable resize,AutoCloseable owner,
                       Consumer<Float> worldPost,Consumer<Float> overlayPre) {
            this(effects,hud,clickGui,effectsEnabled,hudEnabled,tick,tickPost,resize,owner,worldPost,overlayPre,()->{},()->{},shadow->{});
        }
        public Binding(EffectsModule effects,HudRenderPipeline hud,ClickGuiModule clickGui,
                       BooleanSupplier effectsEnabled,BooleanSupplier hudEnabled,
                       Runnable tick,Runnable tickPost,Runnable resize,AutoCloseable owner,
                       Consumer<Float> worldPost,Consumer<Float> overlayPre,
                       Runnable overlayPostPrepare,Runnable overlayPostDraw,Consumer<Boolean> overlayMask) {
            this.effects=Objects.requireNonNull(effects);this.hud=Objects.requireNonNull(hud);
            this.clickGui=Objects.requireNonNull(clickGui);this.effectsEnabled=Objects.requireNonNull(effectsEnabled);
            this.hudEnabled=Objects.requireNonNull(hudEnabled);this.tick=Objects.requireNonNull(tick);
            this.tickPost=Objects.requireNonNull(tickPost);
            this.resize=Objects.requireNonNull(resize);this.owner=Objects.requireNonNull(owner);
            this.worldPost=Objects.requireNonNull(worldPost);this.overlayPre=Objects.requireNonNull(overlayPre);
            this.overlayPostPrepare=Objects.requireNonNull(overlayPostPrepare);this.overlayPostDraw=Objects.requireNonNull(overlayPostDraw);
            this.overlayMask=Objects.requireNonNull(overlayMask);
        }
    }
    /** Testable dispatcher and explicit render-thread ownership; no worker touches GL. */
    public static final class Dispatcher implements AutoCloseable {
        private final Scripts scripts;
        private final Thread thread=Thread.currentThread();
        private Binding binding;
        private float partialTicks;
        private boolean closed;
        public Dispatcher(Scripts scripts){this.scripts=Objects.requireNonNull(scripts);}
        private void check(){
            if(Thread.currentThread()!=thread)throw new IllegalStateException("Visual events require the client/render thread");
            if(closed)throw new IllegalStateException("Visual event dispatcher closed");
        }
        public void install(Binding value){check();if(binding!=null)throw new IllegalStateException("Visual services already installed");binding=Objects.requireNonNull(value);}
        public boolean installed(){check();return binding!=null;}
        public void overlay(float partialTicks,boolean pre){
            check();this.partialTicks=partialTicks;Binding current=binding;
            HudOverlayPhaseEvent event=new HudOverlayPhaseEvent(pre?HudOverlayPhaseEvent.Phase.PRE:HudOverlayPhaseEvent.Phase.POST);
            if(pre&&current!=null)current.overlayPre.accept(partialTicks);
            if(!pre&&current!=null)current.overlayPostPrepare.run();
            // jM HIGH POST precedes jP LOWEST; scripts use the ordinary-priority slot.
            if(!pre&&current!=null&&current.effectsEnabled.getAsBoolean())current.effects.onHudOverlay(event);
            scripts.overlay(partialTicks,pre);
            if(current!=null&&current.hudEnabled.getAsBoolean())current.hud.onHudOverlay(event);
            if(!pre&&current!=null)current.overlayPostDraw.run();
        }
        /** Pass this method to LwjglPostProcessBackend.Host.publishMaskEvent. */
        public void mask(boolean shadow){
            check();Binding current=binding;
            if(current!=null){
                McpVisualServices visual=McpVisualServices.current();
                if(shadow&&visual!=null)visual.clickSettings.shadowColor.render(()->current.clickGui.onOriginalDm(Boolean.TRUE));
                else current.clickGui.onOriginalDm(Boolean.valueOf(shadow));
            }
            scripts.effect(!shadow,partialTicks);
            if(current!=null&&current.hudEnabled.getAsBoolean())current.hud.onOriginalDm(Boolean.valueOf(shadow));
            if(current!=null)current.overlayMask.accept(shadow);
        }
        /** Pass this method to EffectsModule.RenderHooks.publishScreenEvent. */
        public void screen(){check();if(binding!=null)binding.clickGui.onOriginalDl();}
        public void tick(){
            check();Binding current=binding;if(current==null)return;
            // Minecraft already invokes LuaManager.onTick; never invoke it here.
            if(current.effectsEnabled.getAsBoolean())current.effects.requestShadowUpdate();
            if(current.hudEnabled.getAsBoolean())current.hud.onClientTick();
            current.tick.run();
        }
        public void resize(){
            check();Binding current=binding;if(current==null)return;
            current.hud.setDirty(true);
            if(current.effectsEnabled.getAsBoolean())current.effects.requestShadowUpdate();
            current.resize.run();
        }
        /** Invoked at the actual end of Minecraft.runTick, after player/world updates. */
        public void tickPost(){check();if(binding!=null)binding.tickPost.run();}
        public void worldPost(float ticks){check();if(binding!=null)binding.worldPost.accept(ticks);}
        /** Detach before close so a failing owner is never called twice on shutdown. */
        @Override public void close()throws Exception {
            if(Thread.currentThread()!=thread)throw new IllegalStateException("Visual shutdown requires the client/render thread");
            if(closed)return;closed=true;Binding current=binding;binding=null;
            if(current!=null)current.owner.close();
        }
    }
    private static final Dispatcher CLIENT=new Dispatcher(new Scripts(){
        public void overlay(float ticks,boolean pre){LuaManager.get().onRender(ticks,pre);}
        public void effect(boolean blur,float ticks){LuaManager.get().onEffectRender(blur,ticks);}
    });
    private McpVisualEvents(){}
    public static void install(Binding value){CLIENT.install(value);}
    public static boolean installed(){return CLIENT.installed();}
    public static void overlay(float ticks,boolean pre){CLIENT.overlay(ticks,pre);}
    public static void mask(boolean shadow){CLIENT.mask(shadow);}
    public static void screen(){CLIENT.screen();}
    public static void tick(){CLIENT.tick();}
    public static void packetFramePost(){McpModuleServices.backtrackFramePost();}
    public static void tickPost(){CLIENT.tickPost();}
    public static void worldPost(float ticks){CLIENT.worldPost(ticks);}
    public static void resize(){CLIENT.resize();}
    public static void shutdown()throws Exception {CLIENT.close();}
}
