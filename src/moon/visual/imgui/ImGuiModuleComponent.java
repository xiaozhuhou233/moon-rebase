package moon.visual.imgui;

import java.util.*;
import moon.visual.gui.*;

/** kJ. No module pushID, no child-list clear on repeated initialization, and no
* invented controls for unsupported original setting types. */
public final class ImGuiModuleComponent implements ImGuiComponent {
    private final ModuleRowWidget.Module module;
    private final ImGuiSettingComponents factory;
    private final List<ImGuiComponent> children=new ArrayList<>();
    public ImGuiModuleComponent(ModuleRowWidget.Module module,ImGuiSettingComponents factory) {
        this.module=module;
        this.factory=factory;
    }
    public void initialize() {
        ImGuiSettingComponents.normal();
        for(SettingValue<?> setting:module.settings()) {
            if(setting.isGrouped())continue;
            ImGuiComponent component=factory.create(setting);
            if(component!=null)children.add(component);
        }
        for(ImGuiComponent child:children)child.initialize();
    }
    public void render() {
        ImGuiSettingComponents.normal();
        if(factory.api.collapsingHeader(module.displayName())) {
            factory.api.checkbox(factory.parameters.enabledLabel,module.enabled());
            if(factory.api.itemClicked())module.toggle();
            for(ImGuiComponent child:children)child.render();
        }
    }
    public List<ImGuiComponent> children() {
        return children;
    }
}
