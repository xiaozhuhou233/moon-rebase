package moon.visual.gui;

import java.io.IOException;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.client.gui.GuiScreen;
import org.lwjglx.input.Keyboard;
import moon.module.visual.ClickGuiModule;
import moon.module.visual.EffectsModule;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** MCP1.8.9/LWJGL3 GuiScreen boundary for the restored l9 controller.
 * Module/settings/fonts/events and render resources are supplied by the client services.
 * The same wrapper identity is used by Minecraft, jK and modal current-screen lookups.
 */
public final class McpClickGuiScreen extends GuiScreen implements ClickGuiModule.NativeScreen,ModalConfiguration.Screen {
    private final NativeClickGuiScreen controller;
    private final NativeClickGuiScreen.Services services;

    public McpClickGuiScreen(NativeClickGuiScreen.Services services,NativeClickGuiScreen.Config config,
                            NativeClickGuiScreen.Parameters parameters,LongSupplier clock) {
        this.services=services;controller=new NativeClickGuiScreen(new Bridge(services),config,parameters,clock);
    }
    /** jK invokes this once before Minecraft.displayGuiScreen; it is not initGui. */
    @Override public void initialize(){controller.initialize();}
    /** Minecraft invokes this again on resize/reopen without reconstructing the category tree. */
    @Override public void initGui(){controller.initializeScreen();}
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){services.renderFrame(()->controller.drawScreen(mouseX,mouseY,partialTicks));}
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();services.mouseWheel(org.lwjglx.input.Mouse.getEventDWheel());
    }
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException {
        try{controller.mouseClicked(mouseX,mouseY,button);}catch(ParentInputException e){throw e.original;}
    }
    @Override protected void mouseReleased(int mouseX,int mouseY,int button){controller.mouseReleased(mouseX,mouseY,button);}
    @Override protected void keyTyped(char character,int legacyKey)throws IOException {
        try{controller.keyTyped(character,legacyKey);}catch(ParentInputException e){throw e.original;}
    }
    @Override public boolean doesGuiPauseGame(){return controller.doesGuiPauseGame();}
    // dl is published by Effects, not GuiScreen.updateScreen. Avoid a second update path.
    @Override public void onOriginalDl(){controller.onOriginalDl();}
    @Override public void onOriginalDm(Object event){services.renderFrame(()->controller.onOriginalDm(event));}
    @Override public void onColorSettingChanged(){controller.onColorSettingChanged();}
    @Override public ModalOverlay.Scheme modalScheme(){return controller.modalScheme();}
    @Override public void setModal(ModalOverlay overlay){controller.setModal(overlay);}
    public NativeClickGuiScreen controller(){return controller;}

    /** Preserve checked parent failures without rewriting exceptions from widgets/services. */
    private static final class ParentInputException extends RuntimeException {
        final IOException original;
        ParentInputException(IOException original){super(original);this.original=original;}
    }

    private void parentInitialize(){super.initGui();}
    private void parentDraw(int x,int y,float partialTicks){super.drawScreen(x,y,partialTicks);}
    private void parentMouseClicked(int x,int y,int button) {
        try{super.mouseClicked(x,y,button);}catch(IOException e){throw new ParentInputException(e);}
    }
    private void parentMouseReleased(int x,int y,int button){super.mouseReleased(x,y,button);}
    private void parentKeyTyped(char character,int key) {
        try{super.keyTyped(character,key);}catch(IOException e){throw new ParentInputException(e);}
    }
    private final class Bridge implements NativeClickGuiScreen.Host {
        private final NativeClickGuiScreen.Services services;
        Bridge(NativeClickGuiScreen.Services services){this.services=services;}
        public CategoryPanel.Host categoryHost(){return services.categoryHost();}
        public List<? extends CategoryPanel.Category> categories(){return services.categories();}
        public boolean isScriptsCategory(CategoryPanel.Category c){return services.isScriptsCategory(c);}
        public GroupSettingWidget.Factory settingFactory(){return services.settingFactory();}
        public CategoryPanel.Parameters categoryParameters(){return services.categoryParameters();}
        public MarkerHooks markers(){return services.markers();}
        public ModalOverlay.Scheme modalScheme(){return services.modalScheme();}
        public EffectsModule effectsModule(){return services.effectsModule();}
        public boolean effectsModuleEnabled(EffectsModule m){return services.effectsModuleEnabled(m);}
        public boolean isOriginalDmShadow(Object e){return services.isOriginalDmShadow(e);}
        public void enableBlend(){services.enableBlend();}public void blendFunc(int s,int d){services.blendFunc(s,d);}
        public void enableRepeatEvents(boolean enabled){Keyboard.enableRepeatEvents(enabled);}
        public void displayNoScreen(){mc.displayGuiScreen(null);}public Object currentScreen(){return mc.currentScreen;}
        public void focusGame(){mc.setIngameFocus();}
        public void parentInitialize(){McpClickGuiScreen.this.parentInitialize();}
        public void parentDraw(int x,int y,float p){McpClickGuiScreen.this.parentDraw(x,y,p);}
        public void parentMouseClicked(int x,int y,int b){McpClickGuiScreen.this.parentMouseClicked(x,y,b);}
        public void parentMouseReleased(int x,int y,int b){McpClickGuiScreen.this.parentMouseReleased(x,y,b);}
        public void parentKeyTyped(char c,int k){McpClickGuiScreen.this.parentKeyTyped(c,k);}
    }
}
