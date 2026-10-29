package moon.visual.color;

/** Original com.f numerical algorithm, including reference-returning rgbD and
 * unvalidated inputs. Uses recovered literal matrices/Lab math, not HCT fixtures. */
public final class ViewingConditions {
    public static final ViewingConditions DEFAULT = make(
        new double[]{MaterialColorMath.whitePointD65()[0], MaterialColorMath.whitePointD65()[1], MaterialColorMath.whitePointD65()[2]},
        63.66197723675813 * MaterialColorMath.yFromLstar(50.0) / 100.0, 50.0, 2.0, false);

    private final double aw;
    private final double nbb;
    private final double ncb;
    private final double c;
    private final double nc;
    private final double n;
    private final double[] rgbD;
    private final double fl;
    private final double flRoot;
    private final double z;

    private ViewingConditions(double n, double aw, double nbb, double ncb, double c,
                             double nc, double[] rgbD, double fl, double flRoot, double z) {
        this.n = n;
        this.aw = aw;
        this.nbb = nbb;
        this.ncb = ncb;
        this.c = c;
        this.nc = nc;
        this.rgbD = rgbD;
        this.fl = fl;
        this.flRoot = flRoot;
        this.z = z;
    }

    public static ViewingConditions make(double[] whitePoint, double adaptingLuminance,
                                         double backgroundLstar, double surround, boolean discountingIlluminant) {
        double[][] matrix = MaterialColorMath.XYZ_TO_CAM16RGB;
        double rW = whitePoint[0] * matrix[0][0] + whitePoint[1] * matrix[0][1] + whitePoint[2] * matrix[0][2];
        double gW = whitePoint[0] * matrix[1][0] + whitePoint[1] * matrix[1][1] + whitePoint[2] * matrix[1][2];
        double bW = whitePoint[0] * matrix[2][0] + whitePoint[1] * matrix[2][1] + whitePoint[2] * matrix[2][2];
        double f = 0.8 + surround / 10.0;
        double c = f >= 0.9
            ? MaterialColorMath.lerp(0.59, 0.69, (f - 0.9) * 10.0)
            : MaterialColorMath.lerp(0.525, 0.59, (f - 0.8) * 10.0);
        double d = discountingIlluminant ? 1.0
            : f * (1.0 - 0.2777777777777778 * Math.exp((-adaptingLuminance - 42.0) / 92.0));
        d = MaterialColorMath.clamp(0.0, 1.0, d);
        double nc = f;
        double[] rgbD = {d * (100.0 / rW) + 1.0 - d,
                        d * (100.0 / gW) + 1.0 - d,
                        d * (100.0 / bW) + 1.0 - d};
        double k = 1.0 / (5.0 * adaptingLuminance + 1.0);
        double k4 = k * k * k * k;
        double k4Complement = 1.0 - k4;
        double fl = k4 * adaptingLuminance
            + 0.1 * k4Complement * k4Complement * Math.cbrt(5.0 * adaptingLuminance);
        double n = MaterialColorMath.yFromLstar(backgroundLstar) / whitePoint[1];
        double z = 1.48 + Math.sqrt(n);
        double nbb = 0.725 / Math.pow(n, 0.2);
        double ncb = nbb;
        double[] rgbAFactors = {
            Math.pow(fl * rgbD[0] * rW / 100.0, 0.42),
            Math.pow(fl * rgbD[1] * gW / 100.0, 0.42),
            Math.pow(fl * rgbD[2] * bW / 100.0, 0.42)};
        double[] rgbA = {
            400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
            400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
            400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13)};
        double aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb;
        return new ViewingConditions(n, aw, nbb, ncb, c, nc, rgbD, fl, Math.pow(fl, 0.25), z);
    }

    public double aw() { return aw; }
    public double n() { return n; }
    public double nbb() { return nbb; }
    public double ncb() { return ncb; }
    public double c() { return c; }
    public double nc() { return nc; }
    public double[] rgbD() { return rgbD; }
    public double fl() { return fl; }
    public double flRoot() { return flRoot; }
    public double z() { return z; }
}
