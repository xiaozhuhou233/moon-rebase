package moon.client;

import java.io.IOException;
import java.util.*;
import imgui.ImGui;
import moon.visual.gui.*;
import moon.visual.imgui.*;
import moon.module.visual.ClickGuiModule;
import net.minecraft.client.gui.GuiScreen;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

/** MCP GuiScreen boundary for l_; native Combat is the initial module catalog.
 * Destination text capacity/Enabled label and enum label resolution are explicit policies. */
final class McpImGuiScreen extends GuiScreen implements ClickGuiModule.Screen {
    private final McpVisualServices services;
    private final McpImGuiBackend backend;
    private final ImGuiConfigurationScreen<CategoryPanel.Category> controller;
    private boolean initialized,opened,repeatBefore;
    McpImGuiScreen(McpVisualServices services,McpImGuiBackend backend){
        this(services,backend,new ImGuiApi.Native());
    }
    /** Package-local API injection supports scoped native-call instrumentation. */
    McpImGuiScreen(McpVisualServices services,McpImGuiBackend backend,ImGuiApi api){
        this.services=services;this.backend=backend;
        ImGuiSettingComponents factory=new ImGuiSettingComponents(api,new ImGuiSettingComponents.Parameters(1024,"Enabled"),
            Math::random,McpImGuiScreen::enumLabels);
        controller=new ImGuiConfigurationScreen<>(new Host(),api,factory);
    }
    private static <E extends Enum<E>&DisplayNamed>String[] enumLabelsTyped(EnumSetting<E> setting){return Arrays.stream(setting.values()).map(setting::displayName).toArray(String[]::new);}
    @SuppressWarnings({"unchecked","rawtypes"})private static String[] enumLabels(EnumSetting<?> setting){return enumLabelsTyped((EnumSetting)setting);}
    @Override public void initialize(){backend.ensureReady();try(var scope=backend.activate()){controller.initialize();initialized=true;}}
    @Override public void initGui(){
        // Rebuild fresh components for live Lua categories; no duplicate child accumulation.
        if(!initialized||opened)initialize();opened=true;
        // In this MCP, this public method returns its current repeat-enabled flag.
        if(!inputOpen){repeatBefore=Keyboard.isRepeatEvent();inputOpen=true;}
        Keyboard.enableRepeatEvents(true);backend.clearInput();
    }
    private boolean inputOpen;
    @Override public void drawScreen(int x,int y,float partialTicks){
        try(var scope=backend.activate();var state=new McpImGuiBackend.GlState()){
            state.prepare();
            try{controller.render(x,y,partialTicks);}
            catch(RuntimeException|Error failure){try{ImGui.endFrame();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;}
        }
    }
    @Override public void handleKeyboardInput()throws IOException {
        int key=Keyboard.getEventKey();boolean down=Keyboard.getEventKeyState();
        if(down&&key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(null);if(mc.currentScreen==null)mc.setIngameFocus();return;}
        backend.keyEvent(key,down,Keyboard.getEventCharacter());
    }
    @Override public void handleMouseInput(){backend.mouseEvent(Mouse.getEventButton(),Mouse.getEventButtonState(),Mouse.getEventDWheel());}
    @Override public void onGuiClosed(){backend.clearInput();if(inputOpen){Keyboard.enableRepeatEvents(repeatBefore);inputOpen=false;}super.onGuiClosed();}
    @Override public boolean doesGuiPauseGame(){return false;}
    ImGuiConfigurationScreen<CategoryPanel.Category> controller(){return controller;}
    private final class Host implements ImGuiConfigurationScreen.Host<CategoryPanel.Category> {
        public CategoryPanel.Category combatCategory(){return services.categories().get(0);}
        public List<CategoryPanel.Category> categories(){return new ArrayList<>(services.categories());}
        public String categoryName(CategoryPanel.Category c){return c.displayName();}
        public String categoryLabel(CategoryPanel.Category c){return c.displayName();}// Tahoma has no Material icon glyphs.
        public List<? extends ModuleRowWidget.Module> modules(CategoryPanel.Category c){return services.modules(c);}
        public boolean originalDqIsNonnull(){return true;}
        public void initializeContext(String font,float size,String glsl,boolean callbacks){backend.ensureReady();}
        public void originalGlU(){}public void originalGlL(){}public void originalGlC(){}public void originalGlD(){}
        public void glfwNewFrame(){backend.newFrame();}
        public void renderDrawData(){backend.renderDrawData();}
    }
}
