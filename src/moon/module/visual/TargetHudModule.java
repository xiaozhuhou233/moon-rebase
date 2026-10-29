package moon.module.visual;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import moon.module.visual.hud.HudSettings;
import moon.visual.gui.*;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjglx.util.vector.Matrix4f;
import org.lwjglx.util.vector.Vector3f;

/** jS/ga/gb: player target, Moon layout, rounded skin, health easing and masks.
 * gb constants recovered from static DATA seed 118689157411510; health uses
 * kU's Math.round(double * 10) / 10 and original double-to-string conversion.
 * Settings, lifecycle, geometry and drag handling intentionally share one file.
 */
public final class TargetHudModule implements ModuleRowWidget.Module {
    public enum Design implements DisplayNamed {
        MOON, BASIC;
        public String displayName() { return this == MOON ? "Moon" : "Basic"; }
    }
    public interface Rendering {
        DrawServices drawing();
        ShaderDispatcher rounded();
        ShaderDispatcher texture();
        ShaderDispatcher text();
        MsdfFontBase font();
        int fpsFloor();
        void invalidate();
        void isolated(Runnable drawing);
    }
    // Original gb constructor and draw constants; refresh scheduling is shared with the host.
    public static final long TRANSITION_MILLIS = 450;
    private static final int BASE_COLOR = 0xff000000, TRACK_COLOR = 0x80000000;
    private static final String HEALTH_SUFFIX = " HP";
    public static final float MOON_WIDTH = 125, MOON_HEIGHT = 50, BASIC_HEIGHT = 40, RADIUS = 8;

    private final Minecraft mc;
    private final Supplier<EntityLivingBase> target;
    private final HudSettings hud;
    private final Rendering rendering;
    public final ShadowStyle shadowColor;
    private final TimedAnimation visibility;
    private final FrameAnimation health;
    private final List<SettingValue<?>> settings;
    public final EnumSetting<Design> design;
    public final BooleanSetting colorfulBackground, syncWithModuleList;
    public final ColorSetting moonColor;
    public final FloatSetting x, y;
    private boolean enabled, showing, dragging, drawable;
    private EntityPlayer displayed;
    private Object world;
    private float left, top, width = MOON_WIDTH, height = MOON_HEIGHT, scale, dragX, dragY;
    private String name = "", healthText = "";
    private int accent;
    private ResourceLocation skin;

    public TargetHudModule(Minecraft mc, Supplier<EntityLivingBase> target,
                           HudSettings hud, MarkerHooks hooks, Rendering rendering) {
        this.mc = Objects.requireNonNull(mc);
        this.target = Objects.requireNonNull(target);
        this.hud = Objects.requireNonNull(hud);
        this.rendering = Objects.requireNonNull(rendering);
        shadowColor=new ShadowStyle("shadow-color","Shadow Color",hooks);
        visibility = new TimedAnimation(TRANSITION_MILLIS, 10, System::currentTimeMillis, hooks);
        health = new FrameAnimation(Minecraft::getDebugFPS, rendering.fpsFloor());
        design = EnumSetting.builder("design", Design.class, hooks).configure("Design", Design.MOON).build();
        colorfulBackground = BooleanSetting.builder("colorful-background", hooks).configure("Colorful background", true).build();
        syncWithModuleList = BooleanSetting.builder("sync-with-module-list", hooks).configure("Sync with module-list", true).build();
        moonColor = ColorSetting.builder("moon-color", hooks).configure("Color", 0xffff0000)
            .visibility(() -> !syncWithModuleList.get()).build();
        x = FloatSetting.builder("x", hooks).configure("X", 0F, 0F, 100000F, 1F).visibility(() -> false).build();
        y = FloatSetting.builder("y", hooks).configure("Y", 0F, 0F, 100000F, 1F).visibility(() -> false).build();
        settings = List.of(design, colorfulBackground, syncWithModuleList, moonColor, x, y, shadowColor.group);
    }
    public String hudSuffix(){return design.get().displayName();}
    public String displayName() { return "TargetHUD"; }
    public boolean enabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!value) reset();
        rendering.invalidate();
    }
    public List<? extends SettingValue<?>> settings() { return settings; }
    public EntityPlayer currentTarget() {
        if (!enabled || mc.theWorld == null || mc.thePlayer == null) return null;
        if (mc.currentScreen instanceof GuiChat) return mc.thePlayer;
        EntityLivingBase value = target.get();
        return value instanceof EntityPlayer player && player.worldObj == mc.theWorld && player.isEntityAlive() ? player : null;
    }
    private void reset() {
        displayed = null;
        showing = drawable = dragging = false;
        scale = 0;
        health.set(0);
        visibility.resetTo(0);
        visibility.block();
    }
    /** Once before POST effects: masks and color passes consume the same frame. */
    public void prepare() {
        boolean previous = drawable;
        if (world != mc.theWorld) { world = mc.theWorld; reset(); }
        if (!enabled || world == null || mc.thePlayer == null) {
            reset();
            if (previous) rendering.invalidate();
            return;
        }
        EntityPlayer selected = currentTarget();
        boolean next = selected != null;
        if (next != showing) { visibility.resetTo(visibility.get()); showing = next; }
        if (selected != null) displayed = selected;
        visibility.update(0, 1, showing);
        scale = visibility.get();
        if (!showing && visibility.isFinished()) displayed = null;
        drawable = displayed != null && scale > .001F;
        if (displayed != null) {
            name = displayed.getName();
            if (design.get() == Design.MOON) {
                width = MOON_WIDTH;
                height = MOON_HEIGHT;
            } else {
                width = Math.max(90F, rendering.font().width(name, 8F) + 28F);
                height = BASIC_HEIGHT;
            }
            health.update(Math.max(0, Math.min(displayed.getHealth(), displayed.getMaxHealth())) /
                Math.max(.001F, displayed.getMaxHealth()), 0.30000001192092896);
            healthText = formatHealth(displayed.getHealth());
            skin = displayed instanceof AbstractClientPlayer player ? player.getLocationSkin()
                : DefaultPlayerSkin.getDefaultSkin(displayed.getUniqueID());
        }
        accent = syncWithModuleList.get() ? hud.textColorOne.get() : moonColor.get();
        ScaledResolution resolution = new ScaledResolution(mc);
        left = constrain(x.get(), resolution.getScaledWidth() - width);
        top = constrain(y.get(), resolution.getScaledHeight() - height);
        if (!(mc.currentScreen instanceof GuiChat) || !org.lwjglx.input.Mouse.isButtonDown(0)) finishDrag();
        // Includes the final empty frame, target loss, world change and live colors.
        if (drawable || previous) rendering.invalidate();
    }
    private static float constrain(float value, float maximum) {
        return Float.isFinite(value) ? Math.max(0, Math.min(value, Math.max(0, maximum))) : 0;
    }
    public void draw() { if (drawable) rendering.isolated(() -> render(null)); }
    public void mask(boolean shadow) { if(drawable)rendering.isolated(()->{if(shadow)shadowColor.render(()->render(true));else render(false);}); }
    public boolean drawable() { return drawable; }
    public boolean glowEnabled() { return drawable && colorfulBackground.get(); }
    public float width() { return width; }
    public float left() { return left; }
    public float top() { return top; }
    public float visibleScale() { return scale; }
    public float healthFraction() { return health.get(); }

    private void render(Boolean shadow) {
        float previousAlpha = RenderTransformState.getAlpha();
        RenderTransformState.setAlpha(previousAlpha * Math.max(0, Math.min(1, scale)));
        try {
            drawTask(rendering.rounded(), null, task -> {
                if (shadow != null) {
                    rectangle(task, left, top, width, height, RADIUS,
                        shadow ? colorfulBackground.get() ? accent : 0xff000000 : -1);
                } else {
                    // Shadow coverage is produced exclusively by the shared Effects pass.
                    rectangle(task, left, top, width, height, RADIUS, originalOpacity(BASE_COLOR, .4F));
                    rectangle(task, left, top, width, height, RADIUS,
                        originalOpacity(colorfulBackground.get() ? accent : 0xff000000, .2F));
                    if (design.get() == Design.MOON) {
                        rectangle(task, left + 39, top + 26, width - 44, 8, 3, TRACK_COLOR);
                    } else {
                        rectangle(task, left, top, 2.5F, height, 2.5F, alpha(accent, .9F));
                        rectangle(task, left + 9, top + 27, width - 18, 4, 2, TRACK_COLOR);
                    }
                }
            });
            if (shadow != null) return;
            MsdfFontBase font = rendering.font();
            if (design.get() == Design.MOON) {
                float barWidth=(width-52)*health.get();
                drawTask(rendering.rounded(),null,task->rectangle(task,left+45,top+31,barWidth,4,2,accent));
                drawTask(rendering.texture(), () -> mc.getTextureManager().bindTexture(skin), task -> {
                    task.add(new UiGeometry.RoundedTextureQuad(left+7,top+9,left+39,top+41,
                        .125F,.125F,.25F,.25F,4,-1,rendering.drawing().markers));
                    task.add(new UiGeometry.RoundedTextureQuad(left+7,top+9,left+39,top+41,
                        .625F,.125F,.75F,.25F,4,-1,rendering.drawing().markers));
                });
                String hp = Math.round(displayed.getHealth()) + "/" + Math.round(displayed.getMaxHealth());
                String details = targetDetails();
                drawTask(rendering.text(), font::bind, task -> {
                    font.drawSinglePass(task, name, 9, left+45, top+9, -1);
                    font.drawSinglePass(task, hp, 7, left+width-8-font.width(hp,7), top+10, 0xffcccccc);
                    font.drawSinglePass(task, details, 7, left+45, top+39, 0xffc0c0c0);
                });
            } else {
                float barWidth=(width-18)*health.get();
                drawTask(rendering.rounded(),null,task->rectangle(task,left+9,top+27,barWidth,4,2,accent));
                String hp = Math.round(displayed.getHealth()) + " HP";
                drawTask(rendering.text(), font::bind, task -> {
                    font.drawSinglePass(task, name, 8, left+9, top+8, -1);
                    font.drawSinglePass(task, hp, 7, left+width-8-font.width(hp,7), top+8, 0xffcccccc);
                });
            }
        } finally { RenderTransformState.setAlpha(previousAlpha); }
    }
    private String targetDetails() {
        int ping = 0;
        if (mc.getNetHandler() != null) {
            var info = mc.getNetHandler().getPlayerInfo(displayed.getUniqueID());
            if (info != null) ping = info.getResponseTime();
        }
        return ping + "ms  " + String.format(java.util.Locale.ROOT, "%.1f", displayed.getDistanceToEntity(mc.thePlayer)) + "m";
    }
    private static String formatHealth(float value) {
        return Double.toString(Math.round((double)value * 10D) / 10D) + HEALTH_SUFFIX;
    }
    /** Y.a(float) replaces opacity; it does not multiply the stored alpha. */
    private static int originalOpacity(int color, float opacity) {
        return (color & 0xffffff) | ((int)Math.max(0D, Math.min(255D, opacity * 255F)) << 24);
    }
    private static int alpha(int color, float value) { return (color & 0xffffff) | ((int)(((color >>> 24)&255)*value) << 24); }
    private void rectangle(DrawTask task,float px,float py,float w,float h,float radius,int color) {
        if (w > 0) task.add(new UiGeometry.RoundedRectangle(px,py,w,h,radius,color,rendering.drawing().markers));
    }
    private void drawTask(ShaderDispatcher dispatcher, DrawTaskState.Texture texture,
                          java.util.function.Consumer<DrawTask> geometry) {
        DrawTask task = DrawTask.builder(rendering.drawing()).dispatcher(dispatcher).retained().build();
        try {
            task.getState().setTexture(texture);
            float cx = (left+width/2)/task.getState().getWidth();
            float cy = (top+height/2)/task.getState().getHeight();
            task.getState().setTransform(new DrawTaskState.Transform() {
                public void apply(Matrix4f matrix) {
                    matrix.translate(new Vector3f(cx,cy,0));
                    matrix.scale(new Vector3f(scale,scale,1));
                    matrix.translate(new Vector3f(-cx,-cy,0));
                }
                public void unapply(Matrix4f matrix) { matrix.setIdentity(); }
            });
            geometry.accept(task);
            task.draw();
        } finally { task.getState().release(); }
    }
    public boolean press(int mouseX,int mouseY,int button) {
        if (button != 0 || !enabled || !(mc.currentScreen instanceof GuiChat) || !drawable) return false;
        if (mouseX < left || mouseX > left+width || mouseY < top || mouseY > top+height) return false;
        dragging = true; dragX = mouseX-left; dragY = mouseY-top;
        return true;
    }
    public boolean move(int mouseX,int mouseY,int button) {
        if (!dragging) return false;
        if (!enabled || !(mc.currentScreen instanceof GuiChat)) { finishDrag(); return false; }
        ScaledResolution resolution = new ScaledResolution(mc);
        left=constrain(mouseX-dragX,resolution.getScaledWidth()-width);
        top=constrain(mouseY-dragY,resolution.getScaledHeight()-height);
        x.set(left); y.set(top); rendering.invalidate();
        return true;
    }
    public boolean release(int button) { boolean captured=dragging; if (button==0) finishDrag(); return button==0&&captured; }
    public void finishDrag() { dragging=false; }
}
