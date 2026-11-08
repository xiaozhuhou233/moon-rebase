package moon.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import moon.compose.Material3GameScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjglx.input.Keyboard;

/** Compose Material 3 scene rendered into the game's existing LWJGL window. */
final class McpMaterial3Screen extends GuiScreen {
    private static final long FRAME_INTERVAL_NS = 33_333_333L;
    private int texture;
    private int textureWidth, textureHeight;
    private long lastComposeFrame;

    static void open(Minecraft mc) { mc.displayGuiScreen(new McpMaterial3Screen()); }

    @Override public void initGui() {
        Material3GameScene.resize(width, height);
        if (texture == 0) texture = GL11.glGenTextures();
        Keyboard.enableRepeatEvents(true);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Material3GameScene.pointerMove(mouseX, mouseY);
        long now = System.nanoTime();
        if (lastComposeFrame == 0 || now - lastComposeFrame >= FRAME_INTERVAL_NS
                || Material3GameScene.hasInvalidations()) {
            uploadFrame();
            lastComposeFrame = now;
        }
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
            GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(0F, 0F);
            GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(0F, height);
            GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(width, height);
            GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(width, 0F);
            GL11.glEnd();
        }
    }

    private void uploadFrame() {
        byte[] pixels = Material3GameScene.renderBgra();
        ByteBuffer data = ByteBuffer.allocateDirect(pixels.length);
        data.put(pixels).flip();
        GlStateManager.bindTexture(texture);
        int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        try {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (width != textureWidth || height != textureHeight) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, data);
                textureWidth = width;
                textureHeight = height;
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, data);
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) {
        Material3GameScene.pointerPress(mouseX, mouseY, button);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        Material3GameScene.pointerRelease(mouseX, mouseY, button);
    }

    @Override protected void keyTyped(char character, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        Material3GameScene.keyEvent(character, keyCode, true);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        Material3GameScene.scroll(org.lwjglx.input.Mouse.getEventDWheel());
    }

    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Material3GameScene.close();
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
        textureWidth = textureHeight = 0;
        super.onGuiClosed();
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
