package moon.visual.imgui;

import java.util.*;
import moon.visual.gui.ModuleRowWidget;

/** l_ normal nonnull-DQ path. Category type/registry and original kY/backend
* calls are explicit host boundaries. The original static initialized flag is
* shared even across screen instances; no lifecycle repair is hidden here. */
public final class ImGuiConfigurationScreen<C> {
    public interface Host<C> {
        C combatCategory();
        List<C> categories();
        String categoryName(C category);
        String categoryLabel(C category);
        List<? extends ModuleRowWidget.Module> modules(C category);
        boolean originalDqIsNonnull();
        void initializeContext(String fontResource,float size,String glsl,boolean installCallbacks);
        void originalGlU();
        void originalGlL();
        void originalGlC();
        void originalGlD();
        void glfwNewFrame();
        void renderDrawData();
    }
    // Runtime-seed DES decode of original l_.F low32, not a guessed ImGui flag.
    public static final int TAB_FLAGS=32;
    private static boolean initialized;
    private final Host<C> host;
    private final ImGuiApi api;
    private final ImGuiSettingComponents factory;
    private C selected;
    private final Map<C,List<ImGuiComponent>> components=new HashMap<>();
    public ImGuiConfigurationScreen(Host<C> host,ImGuiApi api,ImGuiSettingComponents factory) {
        this.host=host;
        this.api=api;
        this.factory=factory;
        selected=host.combatCategory();
    }
    private void normal() {
        if(!host.originalDqIsNonnull())throw new UnsupportedOperationException("l_ null-DQ path not restored");
    }
    public void initialize() {
        normal();
        if(!initialized) {
            host.initializeContext("/Tahoma.ttf",15F,"#version 130",true);
            initialized=true;
        }
        api.style();
        components.clear();
        for(C category:host.categories())for(ModuleRowWidget.Module module:host.modules(category)) {
            ImGuiComponent component=new ImGuiModuleComponent(module,factory);
            component.initialize();
            components.computeIfAbsent(category,key->new ArrayList<>()).add(component);
        }
    }
    public void render(int mouseX,int mouseY,float partialTicks) {
        normal();
        host.originalGlU();
        host.originalGlL();
        host.originalGlC();
        host.glfwNewFrame();
        api.newFrame();
        api.begin("Moon");
        api.beginTabBar("categories");
        // Both return values are discarded in original bytecode.
        for(C category:host.categories()) {
            api.pushId(host.categoryName(category));
            if(api.beginTabItem(host.categoryLabel(category),TAB_FLAGS)) {
                if(api.itemActive())selected=category;
                api.endTabItem();
                // Not isItemSelected / beginTabItem alone.
            }
            api.popId();
        }
        api.endTabBar();
        List<ImGuiComponent> selectedComponents=components.get(selected);
        if(selectedComponents!=null)for(ImGuiComponent component:selectedComponents)component.render();
        api.end();
        api.render();
        host.renderDrawData();
        host.originalGlD();
        // Original has no finally.
    }
    public C selected() {
        return selected;
    }
    public Map<C,List<ImGuiComponent>> components() {
        return components;
    }
}
