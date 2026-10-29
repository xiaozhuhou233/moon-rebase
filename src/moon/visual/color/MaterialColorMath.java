package moon.visual.color;

/** Recovered unencrypted normal-path portions of com.y/com.z.
 * Packed-color bit constants remain an explicit host boundary. */
public final class MaterialColorMath {
    private static final double[] WHITE_POINT_D65 = {95.047, 100.0, 108.883};

    // Original com.c.a matrix. final protects the reference, not its entries.
    static final double[][] XYZ_TO_CAM16RGB = {
        {0.401288, 0.650173, -0.051461},
        {-0.250268, 1.204414, 0.045854},
        {-0.002079, 0.048952, 0.953127}
    };

    // Original com.c.b inverse matrix, including its original precision.
    static final double[][] CAM16RGB_TO_XYZ = {
        {1.8620678, -1.0112547, 0.14918678},
        {0.38752654, 0.62144744, -0.00897398},
        {-0.0158415, -0.03412294, 1.0499644}
    };

    private MaterialColorMath() { }

    /** Original com.y.a(): returns the shared mutable array without copying. */
    public static double[] whitePointD65() { return WHITE_POINT_D65; }

    public static double yFromLstar(double lstar) {
        return 100.0 * labInverseF((lstar + 16.0) / 116.0);
    }

    /** Original com.y.b(int); no range clamp is added. */
    public static double linearized(int channel) {
        double normalized = channel / 255.0;
        if (normalized <= 0.040449936) return normalized / 12.92 * 100.0;
        return Math.pow((normalized + 0.055) / 1.055, 2.4) * 100.0;
    }

    public static double labF(double value) {
        if (value > 0.008856451679035631) {
            return Math.pow(value, 0.3333333333333333);
        }
        return (903.2962962962963 * value + 16.0) / 116.0;
    }

    public static double labInverseF(double value) {
        double cube = value * value * value;
        if (cube > 0.008856451679035631) return cube;
        return (116.0 * value - 16.0) / 903.2962962962963;
    }

    public static double lerp(double start, double stop, double amount) {
        return (1.0 - amount) * start + amount * stop;
    }

    public static double clamp(double minimum, double maximum, double value) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }

    /** Original com.z.a(double), including +1 for NaN and int zero for -0.0. */
    public static int signum(double value) {
        if (value < 0.0) return -1;
        if (value == 0.0) return 0;
        return 1;
    }

    /** Original double degree sanitizer; the int overload is still unresolved. */
    public static double sanitizeDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees < 0.0) degrees += 360.0;
        return degrees;
    }

    /** Original com.z row/matrix helper: each output is a matrix-row dot input. */
    static double[] matrixMultiply(double[] row, double[][] matrix) {
        return new double[]{
            row[0] * matrix[0][0] + row[1] * matrix[0][1] + row[2] * matrix[0][2],
            row[0] * matrix[1][0] + row[1] * matrix[1][1] + row[2] * matrix[1][2],
            row[0] * matrix[2][0] + row[1] * matrix[2][1] + row[2] * matrix[2][2]};
    }
}
