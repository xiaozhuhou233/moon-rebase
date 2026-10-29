package moon.visual.gui;

import java.util.function.IntSupplier;

/** k8. The original encrypted FPS floor is explicit: candidate 60 is documented,
 * but has no supplied runtime-seed confirmation. This class remains PARTIAL for that constant.
 */
public final class FrameAnimation {
    private float value;
    private final IntSupplier framesPerSecond;
    private final int fpsFloor;
    private static boolean sentinel;
    public FrameAnimation(IntSupplier framesPerSecond,int fpsFloor) {
        this.framesPerSecond=framesPerSecond;this.fpsFloor=fpsFloor;
    }
    private double interpolate(float target,float current,double factor) {
        factor=NumericSettingMath.clamp(factor,0.0,1.0);
        // The subtraction occurs in float precision before promotion to double.
        return current+(target-current)*factor;
    }
    public void update(float target,double speed) { value=(float)interpolate(target,value,speed*frameScale()); }
    public void set(float value) { this.value=value; }
    public float frameScale() { return 60.0F/Math.max(framesPerSecond.getAsInt(),fpsFloor); }
    public float get() { return value; }
    public int getFpsFloor() { return fpsFloor; }
    public static void setSentinel(boolean value) { sentinel=value; }
    public static boolean getSentinel() { return sentinel; }
    public static boolean inverseSentinel() { return !getSentinel(); }
}
