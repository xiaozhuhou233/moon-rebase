package moon.visual.color;

/** com.y normal color conversions with explicit numeric layout dependencies.
 * CamInput keeps com.c's mask-before-shift path distinct from com.y's channel
 * shift-before-mask. No implicit original/runtime-verified layout is supplied. */
public final class MaterialColorUtils implements HctSolver.ColorBackend {
    public record Layout(int opaqueBits, int channelMask, int redShift, int greenShift,
                         int alphaShift, int delinearizedMaximum) { }
    public record CamInput(int redMask, int redShift, int greenMask, int greenShift, int blueMask) { }
    static final double[][] SRGB_TO_XYZ = {
        {.41233895, .35762064, .18051042}, {.2126, .7152, .0722}, {.01932141, .11916382, .95034478}
    };
    static final double[][] XYZ_TO_SRGB = {
        {3.2413774792388685, -1.5376652402851851, -.49885366846268053},
        {-.9691452513005321, 1.8758853451067872, .04156585616912061},
        {.05562093689691305, -.20395524564742123, 1.0571799111220335}
    };
    private final Layout layout;
    private final CamInput cam;
    public MaterialColorUtils(Layout layout, CamInput cam) { this.layout = layout; this.cam = cam; }
    public int argbFromRgb(int red, int green, int blue) {
        return layout.opaqueBits() | (red & layout.channelMask()) << layout.redShift()
            | (green & layout.channelMask()) << layout.greenShift() | blue & layout.channelMask();
    }
    public int alphaFromArgb(int argb) { return argb >> layout.alphaShift() & layout.channelMask(); }
    public int redFromArgb(int argb) { return argb >> layout.redShift() & layout.channelMask(); }
    public int greenFromArgb(int argb) { return argb >> layout.greenShift() & layout.channelMask(); }
    public int blueFromArgb(int argb) { return argb & layout.channelMask(); }
    public boolean isOpaque(int argb) { return alphaFromArgb(argb) >= layout.channelMask(); }
    // com.c's separate input expressions, not com.y's accessors.
    public int red(int argb) { return (argb & cam.redMask()) >> cam.redShift(); }
    public int green(int argb) { return (argb & cam.greenMask()) >> cam.greenShift(); }
    public int blue(int argb) { return argb & cam.blueMask(); }
    public int delinearized(double channel) {
        double normalized = channel / 100.0;
        double value;
        if (normalized <= .0031308) value = normalized * 12.92;
        else value = 1.055 * Math.pow(normalized, .4166666666666667) - .055;
        // Keep the original long-to-int narrowing BEFORE clamp, including infinities.
        int rounded = (int)Math.round(value * 255.0);
        if (rounded < 0) return 0;
        if (rounded > layout.delinearizedMaximum()) return layout.delinearizedMaximum();
        return rounded;
    }
    public int argbFromLinearRgb(double[] rgb) {
        int r = delinearized(rgb[0]);
        int g = delinearized(rgb[1]);
        int b = delinearized(rgb[2]);
        return argbFromRgb(r, g, b);
    }
    public int argbFromXyz(double x, double y, double z) {
        double[][] matrix = XYZ_TO_SRGB;
        double r = matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z;
        double g = matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z;
        double b = matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z;
        int ri = delinearized(r);
        int gi = delinearized(g);
        int bi = delinearized(b);
        return argbFromRgb(ri, gi, bi);
    }
    public double[] xyzFromArgb(int argb) {
        double r = MaterialColorMath.linearized(redFromArgb(argb));
        double g = MaterialColorMath.linearized(greenFromArgb(argb));
        double b = MaterialColorMath.linearized(blueFromArgb(argb));
        return MaterialColorMath.matrixMultiply(new double[]{r,g,b}, SRGB_TO_XYZ);
    }
    public int argbFromLab(double lstar, double a, double b) {
        double[] white = MaterialColorMath.whitePointD65();
        double fy = (lstar + 16.0) / 116.0;
        double fx = a / 500.0 + fy;
        double fz = fy - b / 200.0;
        double x = MaterialColorMath.labInverseF(fx);
        double y = MaterialColorMath.labInverseF(fy);
        double z = MaterialColorMath.labInverseF(fz);
        return argbFromXyz(x * white[0], y * white[1], z * white[2]);
    }
    public double[] labFromArgb(int argb) {
        double r = MaterialColorMath.linearized(redFromArgb(argb));
        double g = MaterialColorMath.linearized(greenFromArgb(argb));
        double b = MaterialColorMath.linearized(blueFromArgb(argb));
        double[][] matrix = SRGB_TO_XYZ;
        double x = matrix[0][0] * r + matrix[0][1] * g + matrix[0][2] * b;
        double y = matrix[1][0] * r + matrix[1][1] * g + matrix[1][2] * b;
        double z = matrix[2][0] * r + matrix[2][1] * g + matrix[2][2] * b;
        double[] white = MaterialColorMath.whitePointD65();
        double fx = MaterialColorMath.labF(x / white[0]);
        double fy = MaterialColorMath.labF(y / white[1]);
        double fz = MaterialColorMath.labF(z / white[2]);
        return new double[]{116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }
    public int argbFromLstar(double lstar) {
        double y = MaterialColorMath.yFromLstar(lstar);
        int component = delinearized(y);
        return argbFromRgb(component, component, component);
    }
    public double lstarFromArgb(int argb) {
        double y = xyzFromArgb(argb)[1];
        return 116.0 * MaterialColorMath.labF(y / 100.0) - 16.0;
    }
}
