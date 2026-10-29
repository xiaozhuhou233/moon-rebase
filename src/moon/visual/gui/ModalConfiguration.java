package moon.visual.gui;

import java.util.List;
import java.util.function.Supplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** ks/kt: immutable field references, live suppliers/list, a fresh descriptor on every build. */
public final class ModalConfiguration {
    public interface Screen {
        ModalOverlay.Scheme modalScheme();
        void setModal(ModalOverlay value);
    }
    private final String title,description;
    private final Supplier<Float> x,y;
    private final ModalContent content;
    private final Screen screen;
    private final List<ModalButton> buttons;
    private ModalConfiguration(Builder builder) {
        title=builder.title;description=builder.description;x=builder.x;y=builder.y;
        ModalContent.getSentinel();content=builder.content;screen=builder.screen;buttons=builder.buttons;
        if(builder.markers.getMarker()!=null) ModalContent.setSentinel(new int[5]);
    }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Supplier<Float> getX() { return x; }
    public Supplier<Float> getY() { return y; }
    public ModalContent getContent() { return content; }
    public Screen getScreen() { return screen; }
    public List<ModalButton> getButtons() { return buttons; }
    public static Builder builder(MarkerHooks markers) { return new Builder(markers); }
    public static final class Builder {
        private String title,description;
        private Supplier<Float> x,y;
        private ModalContent content;
        private Screen screen;
        private List<ModalButton> buttons;
        private final MarkerHooks markers;
        private Builder(MarkerHooks markers) { this.markers=markers; }
        public Builder title(String value) { title=value;return this; }
        public Builder description(String value) { description=value;return this; }
        public Builder x(Supplier<Float> value) { x=value;return this; }
        public Builder y(Supplier<Float> value) { y=value;return this; }
        public Builder content(ModalContent value) { content=value;return this; }
        public Builder screen(Screen value) { screen=value;return this; }
        public Builder buttons(List<ModalButton> value) { buttons=value;return this; }
        public ModalConfiguration build() { return new ModalConfiguration(this); }
        @Override public String toString() {
            return "OverlayConfiguration.OverlayConfigurationBuilder(title="+title+", description="+description+
                ", x="+x+", y="+y+", content="+content+", clickGuiScreen="+screen+", buttons="+buttons+")";
        }
    }
}
