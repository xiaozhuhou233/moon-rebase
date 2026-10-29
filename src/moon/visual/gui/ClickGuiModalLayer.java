package moon.visual.gui;

import java.util.function.Supplier;

/** Normal l9.A subset: store before initialize, modal event preemption and legacy Escape=1.
 * The category/module screen tree and nondefault mT flow are not implemented by this layer.
 * Destination GLFW keys must be translated to the client's legacy input codes by its input host.
 */
public final class ClickGuiModalLayer implements ModalConfiguration.Screen {
    private final Supplier<ModalOverlay.Scheme> scheme;
    private ModalOverlay active;
    public ClickGuiModalLayer(Supplier<ModalOverlay.Scheme> scheme) { this.scheme=scheme; }
    public ModalOverlay.Scheme modalScheme() { return scheme.get(); }
    public void setModal(ModalOverlay value) { active=value;if(value!=null) value.initialize(); }
    public ModalOverlay getActive() { return active; }
    public boolean update(int mouseX,int mouseY) {
        if(active==null) return false;active.update(mouseX,mouseY);return true;
    }
    public boolean mouseClicked(int mouseX,int mouseY,int button) {
        if(active==null) return false;active.mouseClicked(mouseX,mouseY,button);return true;
    }
    public boolean mouseReleased(int mouseX,int mouseY,int button) {
        if(active==null) return false;active.mouseReleased(mouseX,mouseY,button);return true;
    }
    public boolean keyTyped(char character,int legacyKey) {
        if(active==null) return false;
        if(legacyKey==1) { setModal(null);return true; }
        active.keyTyped(character,legacyKey);return true;
    }
}
