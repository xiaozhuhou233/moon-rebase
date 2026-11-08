package moon.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.awt.geom.Rectangle2D;
import moon.compose.Material3GameScene;
import moon.compose.MaterialClickGuiModel;
import moon.compose.MaterialClickGuiState;
import moon.compose.MaterialModuleBoundsSink;
import moon.compose.MaterialSettingModel;
import moon.module.visual.ClickGuiModule;
import moon.visual.gui.CategoryPanel;
import moon.visual.gui.ModuleRowWidget;
import moon.visual.gui.SettingValue;
import moon.visual.gui.FloatSetting;
import moon.visual.gui.IntegerSetting;
import moon.visual.gui.BooleanSetting;
import moon.visual.gui.StringSetting;
import moon.visual.gui.ColorSetting;
import moon.visual.gui.SettingGroup;
import moon.visual.gui.EnumSetting;
import moon.visual.gui.MultiChoiceSetting;
import moon.visual.gui.ModeSetting;
import moon.visual.gui.DisplayNamed;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

/** Compose Material 3 ClickGUI rendered into the game's LWJGL window. */
final class McpMaterialClickGuiScreen extends GuiScreen implements ClickGuiModule.NativeScreen {
    private static final long FRAME_INTERVAL_NS = 16_666_667L;
    private static final long IDLE_REFRESH_NS = 500_000_000L;
    private final McpVisualServices services;
    private final MaterialClickGuiModel model = new Model();
    private int texture;
    private int textureWidth, textureHeight;
    private int renderWidth, renderHeight;
    private int regionX, regionY, regionW = 1, regionH = 1;
    private long lastComposeFrame;
    private MaterialSettingModel focusedTextSetting;
    private int selectedCategory;
    private int requestedModule = -1;
    private final Map<Long, Rectangle2D.Float> moduleBounds = new HashMap<>();

    McpMaterialClickGuiScreen(McpVisualServices services) {
        this.services = services;
    }

    @Override public void initialize() { }

    @Override public void initGui() {
        renderWidth = mc.displayWidth;
        renderHeight = mc.displayHeight;
        Material3GameScene.openClickGui(renderWidth, renderHeight, model);
        int[] initialRegion = Material3GameScene.panelRegion();
        regionX = initialRegion[0]; regionY = initialRegion[1]; regionW = initialRegion[2]; regionH = initialRegion[3];
        if (texture == 0) texture = GL11.glGenTextures();
        Keyboard.enableRepeatEvents(true);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Material3GameScene.pointerMove(px(mouseX), py(mouseY));
        long now = System.nanoTime();
        long idle = now - lastComposeFrame;
        if (lastComposeFrame == 0 || idle >= IDLE_REFRESH_NS
                || (idle >= FRAME_INTERVAL_NS && Material3GameScene.hasInvalidations())) {
            uploadFrame();
            lastComposeFrame = now;
        }
        if (Material3GameScene.isCloseFinished()) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        float sf = new ScaledResolution(mc).getScaleFactor();
        float qx0 = regionX / sf, qy0 = regionY / sf, qx1 = (regionX + regionW) / sf, qy1 = (regionY + regionH) / sf;
        try (McpLuaRenderer.DrawState ignored = new McpLuaRenderer.DrawState()) {
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.bindTexture(texture);
            GlStateManager.color(1F, 1F, 1F, 1F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(qx0, qy0);
            GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(qx0, qy1);
            GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(qx1, qy1);
            GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(qx1, qy0);
            GL11.glEnd();
        }
    }

    private void uploadFrame() {
        int[] region = Material3GameScene.panelRegion();
        regionX = region[0]; regionY = region[1]; regionW = region[2]; regionH = region[3];
        ByteBuffer data = Material3GameScene.renderRegionBuffer(regionW, regionH);
        uploadPixels(texture, regionW, regionH, data, regionW != textureWidth || regionH != textureHeight);
        textureWidth = regionW;
        textureHeight = regionH;
    }

    /** Upload rasterized Compose pixels without borrowing Skia's GPU context or game unpack state. */
    private static void uploadPixels(int texture, int width, int height, ByteBuffer data, boolean allocate) {
        int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try (McpLuaRenderer.DrawState ignored = new McpLuaRenderer.DrawState()) {
            GlStateManager.bindTexture(texture);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (allocate) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, data);
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, data);
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) openModuleAt(mouseX, mouseY);
        if (button == 2) bindModuleAt(mouseX, mouseY);
        Material3GameScene.pointerPress(px(mouseX), py(mouseY), button);
    }

    private float px(int mouseX) { return mouseX * (float) renderWidth / width - regionX; }
    private float py(int mouseY) { return mouseY * (float) renderHeight / height - regionY; }

    private void bindModuleAt(int mouseX, int mouseY) {
        float x = px(mouseX), y = py(mouseY);
        for (Map.Entry<Long, Rectangle2D.Float> entry : moduleBounds.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                MaterialClickGuiState.INSTANCE.setBindCategory((int) (entry.getKey() >> 32));
                MaterialClickGuiState.INSTANCE.setBindModule((int) (long) entry.getKey());
                Material3GameScene.invalidateTextInput();
                return;
            }
        }
    }

    private void openModuleAt(int mouseX, int mouseY) {
        float physicalX = px(mouseX);
        float physicalY = py(mouseY);
        for (Map.Entry<Long, Rectangle2D.Float> entry : moduleBounds.entrySet()) {
            if (entry.getValue().contains(physicalX, physicalY)) {
                int category = (int) (entry.getKey() >> 32);
                int module = (int) (long) entry.getKey();
                selectedCategory = category;
                MaterialClickGuiState.INSTANCE.setSelectedCategory(category);
                MaterialClickGuiState.INSTANCE.setSelectedModuleCategory(category);
                MaterialClickGuiState.INSTANCE.setSelectedModuleIndex(module);
                requestedModule = module;
                focusedTextSetting = null;
                moduleBounds.clear();
                Material3GameScene.invalidateTextInput();
                return;
            }
        }
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        Material3GameScene.pointerRelease(px(mouseX), py(mouseY), button);
    }

    @Override protected void keyTyped(char character, int keyCode) throws IOException {
        boolean controlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (controlDown && keyCode == Keyboard.KEY_F) {
            focusedTextSetting = null;
            MaterialClickGuiState.INSTANCE.setSearchActive(true);
            MaterialClickGuiState.INSTANCE.setSelectedModuleCategory(-1);
            MaterialClickGuiState.INSTANCE.setSelectedModuleIndex(-1);
            Material3GameScene.resetModuleListScroll();
            Material3GameScene.invalidateTextInput();
            return;
        }
        MaterialClickGuiState bindState = MaterialClickGuiState.INSTANCE;
        if (bindState.getBindModule() >= 0) {
            if (keyCode != Keyboard.KEY_ESCAPE) model.bindModule(bindState.getBindCategory(), bindState.getBindModule(), keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE ? 0 : keyCode);
            bindState.setBindModule(-1);
            bindState.setBindCategory(-1);
            Material3GameScene.invalidateTextInput();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            focusedTextSetting = null;
            Material3GameScene.requestClose();
            return;
        }
        if (focusedTextSetting != null && MaterialClickGuiState.INSTANCE.getSelectedModuleIndex() >= 0) {
            if (keyCode == Keyboard.KEY_BACK) {
                String text = focusedTextSetting.valueText();
                if (!text.isEmpty()) focusedTextSetting.setText(text.substring(0, text.length() - 1));
            } else if (character >= 32 && character != '\u007f' && character != '\u00a7') {
                focusedTextSetting.setText(focusedTextSetting.valueText() + character);
            }
            Material3GameScene.invalidateTextInput();
            return;
        }
        focusedTextSetting = null;
        MaterialClickGuiState state = MaterialClickGuiState.INSTANCE;
        if (state.getSearchActive() && state.getSelectedModuleIndex() < 0) {
            String query = state.getSearchQuery();
            if (keyCode == Keyboard.KEY_BACK) {
                if (!query.isEmpty()) state.setSearchQuery(query.substring(0, query.length() - 1));
            } else if (character >= 32 && character != 127 && character != 167) {
                state.setSearchQuery(query + character);
            } else {
                Material3GameScene.keyEvent(character, keyCode, true);
                return;
            }
            Material3GameScene.resetModuleListScroll();
            Material3GameScene.invalidateTextInput();
            return;
        }
        Material3GameScene.keyEvent(character, keyCode, true);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        Material3GameScene.scroll(delta);
    }

    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Material3GameScene.close();
        if (texture != 0) GlStateManager.deleteTexture(texture);
        texture = 0;
        textureWidth = textureHeight = 0;
        focusedTextSetting = null;
        requestedModule = -1;
        moduleBounds.clear();
        lastComposeFrame = 0;
        super.onGuiClosed();
    }

    @Override public void onOriginalDl() { }
    @Override public void onOriginalDm(Object event) { }
    @Override public void onColorSettingChanged() { }
    @Override public boolean doesGuiPauseGame() { return false; }

    private final class Model implements MaterialClickGuiModel, MaterialModuleBoundsSink {
        @Override public int categoryCount() { return services.categories().size(); }
        @Override public String categoryName(int index) { return services.categories().get(index).displayName(); }
        @Override public int moduleCount(int category) { return modules(category).size(); }
        @Override public String moduleName(int category, int module) { return modules(category).get(module).displayName(); }
        @Override public String moduleSuffix(int category, int module) { return modules(category).get(module).hudSuffix(); }
        @Override public boolean moduleEnabled(int category, int module) { return modules(category).get(module).enabled(); }
        @Override public void toggleModule(int category, int module) { modules(category).get(module).toggle(); }
        @Override public String moduleBindLabel(int category, int module) { McpBindings b = services.bindings; return b == null ? "" : b.keyLabel(modules(category).get(module)); }
        @Override public void bindModule(int category, int module, int key) { McpBindings b = services.bindings; if (b != null) b.bind(modules(category).get(module), key); }
        @Override public float guiScale() { return services.clickSettings.scale.get(); }
        @Override public int palette() { return services.clickSettings.palette.get().ordinal(); }
        @Override public void setSelectedCategory(int category) {
            selectedCategory = category;
            moduleBounds.clear();
            Material3GameScene.resetModuleListScroll();
        }
        @Override public int requestedModuleSettings() { int value = requestedModule; requestedModule = -1; return value; }
        @Override public void clearModuleBounds() { moduleBounds.clear(); }
        @Override public void setModuleBounds(int category, int module, float left, float top, float right, float bottom) {
            long address = ((long) category << 32) | (module & 0xFFFFFFFFL);
            moduleBounds.put(address, new Rectangle2D.Float(left, top, right - left, bottom - top));
        }
        @Override public int settingCount(int category, int module) { return flattenedSettings(category, module).size(); }
        @Override public MaterialSettingModel setting(int category, int module, int setting) {
            return new SettingAdapter(flattenedSettings(category, module).get(setting));
        }
        @Override public void focusTextSetting(MaterialSettingModel setting) { focusedTextSetting = setting; }
        @Override public void closeGui() { McpMaterialClickGuiScreen.this.mc.displayGuiScreen(null); }
        private List<? extends ModuleRowWidget.Module> modules(int category) {
            return services.modules(services.categories().get(category));
        }
        private List<SettingValue<?>> flattenedSettings(int category, int module) {
            List<SettingValue<?>> result = new ArrayList<>();
            for (SettingValue<?> setting : modules(category).get(module).settings()) appendSetting(setting, result);
            return result;
        }
        private void appendSetting(SettingValue<?> setting, List<SettingValue<?>> result) {
            if (!setting.isVisible()) return;
            if (setting instanceof SettingGroup) {
                List<SettingValue<?>> children = new ArrayList<>();
                for (SettingValue<?> child : ((SettingGroup) setting).get()) appendSetting(child, children);
                if (children.isEmpty()) return;
                result.add(setting);
                result.addAll(children);
                return;
            }
            result.add(setting);
        }
    }

    private static final class SettingAdapter implements MaterialSettingModel {
        private final SettingValue<?> setting;
        SettingAdapter(SettingValue<?> setting) { this.setting = setting; }
        @Override public String kind() { return setting.getType().name(); }
        @Override public String name() { return setting.getDisplayName() == null ? setting.getKey() : setting.getDisplayName(); }
        @Override public String description() { return setting.getDescription(); }
        @Override public String valueText() {
            Object value = setting.get();
            if (setting instanceof ColorSetting) return String.format(Locale.ROOT, "#%08X", ((Number) value).intValue());
            if (setting instanceof EnumSetting) return ((EnumSetting) setting).displayName((Enum) value);
            if (value instanceof DisplayNamed) return ((DisplayNamed) value).displayName();
            return value == null ? "" : value.toString();
        }
        @Override public float number() { return ((Number) setting.get()).floatValue(); }
        @Override public float minimum() {
            if (setting instanceof FloatSetting) return ((FloatSetting) setting).getMinimum();
            if (setting instanceof IntegerSetting) return ((IntegerSetting) setting).getMinimum();
            return 0;
        }
        @Override public float maximum() {
            if (setting instanceof FloatSetting) return ((FloatSetting) setting).getMaximum();
            if (setting instanceof IntegerSetting) return ((IntegerSetting) setting).getMaximum();
            return 1;
        }
        @Override public boolean checked() { return Boolean.TRUE.equals(setting.get()); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public int choiceCount() { return setting instanceof EnumSetting ? ((EnumSetting) setting).values().length : 0; }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public String choiceName(int index) { EnumSetting e = (EnumSetting) setting; return e.displayName((Enum) e.values()[index]); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public boolean choiceSelected(int index) { EnumSetting e = (EnumSetting) setting; return e.values()[index] == e.get(); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public void selectChoice(int index) { EnumSetting e = (EnumSetting) setting; e.set((Enum) e.values()[index]); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public int optionCount() { return setting instanceof MultiChoiceSetting ? ((MultiChoiceSetting) setting).values().length : 0; }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public String optionName(int index) { return ((DisplayNamed) ((MultiChoiceSetting) setting).values()[index]).displayName(); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public boolean optionSelected(int index) { return ((MultiChoiceSetting) setting).isSelected(((MultiChoiceSetting) setting).values()[index]); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public void toggleOption(int index) { ((MultiChoiceSetting) setting).toggle(((MultiChoiceSetting) setting).values()[index]); }
        @Override public int colorArgb() { return ((ColorSetting) setting).get(); }
        @Override public void setNumber(float value) {
            if (setting instanceof FloatSetting) ((FloatSetting) setting).set(value);
            else if (setting instanceof IntegerSetting) ((IntegerSetting) setting).setFromDrag(value);
        }
        @Override public void setChecked(boolean value) { ((BooleanSetting) setting).set(value); }
        @Override public void setText(String value) {
            if (setting instanceof StringSetting) ((StringSetting) setting).set(value);
            else if (setting instanceof ColorSetting && value.matches("#?[0-9a-fA-F]{8}"))
                ((ColorSetting) setting).set((int) Long.parseLong(value.replace("#", ""), 16));
        }
        @Override public void setColorArgb(int value) { ((ColorSetting) setting).set(value); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public void advanceChoice() {
            Object value = setting.get();
            if (value instanceof Enum) {
                Object[] values = value.getClass().getEnumConstants();
                ((SettingValue) setting).set((Enum) values[(((Enum) value).ordinal() + 1) % values.length]);
            }
        }
    }
}
