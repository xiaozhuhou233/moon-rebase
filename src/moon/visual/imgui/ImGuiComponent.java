package moon.visual.imgui;

/** Original kB. Initialization is separate from rendering, including for children. */
public interface ImGuiComponent {
    void initialize();
    void render();
}
