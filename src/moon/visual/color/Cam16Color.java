package moon.visual.color;

/** Recovered com.c normal CAM16/JCH/UCS mathematics. Packed integer constants
 * are not decoded: PackedColor supplies the original missing input/output edge.
 * fromLinearRgb/toXyz expose that split for numeric verification and migration.
 * No sentinel/marker side effects or input validation are synthesized. */
public final class Cam16Color implements HctColor.Appearance {
    public interface PackedColor {
        int red(int argb);
        int green(int argb);
        int blue(int argb);
        int argbFromXyz(double x, double y, double z);
    }

    private final double hue, chroma, j, q, m, s, jstar, astar, bstar;

    private Cam16Color(double hue, double chroma, double j, double q, double m,
                       double s, double jstar, double astar, double bstar) {
        this.hue = hue;
        this.chroma = chroma;
        this.j = j;
        this.q = q;
        this.m = m;
        this.s = s;
        this.jstar = jstar;
        this.astar = astar;
        this.bstar = bstar;
    }

    public double hue() { return hue; }
    public double chroma() { return chroma; }
    public double j() { return j; }
    public double q() { return q; }
    public double m() { return m; }
    public double s() { return s; }
    public double jstar() { return jstar; }
    public double astar() { return astar; }
    public double bstar() { return bstar; }

    public static Cam16Color fromArgb(int argb, PackedColor packed) {
        return fromArgb(argb, ViewingConditions.DEFAULT, packed);
    }

    public static Cam16Color fromArgb(int argb, ViewingConditions viewing, PackedColor packed) {
        int r = packed.red(argb);
        int g = packed.green(argb);
        int b = packed.blue(argb);
        return fromLinearRgb(MaterialColorMath.linearized(r), MaterialColorMath.linearized(g),
                             MaterialColorMath.linearized(b), viewing);
    }

    /** Linear RGB on the original 0..100 scale; unvalidated, including negatives. */
    public static Cam16Color fromLinearRgb(double r, double g, double b, ViewingConditions viewing) {
        double x = 0.41233895 * r + 0.35762064 * g + 0.18051042 * b;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = 0.01932141 * r + 0.11916382 * g + 0.95034478 * b;
        double[][] matrix = MaterialColorMath.XYZ_TO_CAM16RGB;
        double rT = x * matrix[0][0] + y * matrix[0][1] + z * matrix[0][2];
        double gT = x * matrix[1][0] + y * matrix[1][1] + z * matrix[1][2];
        double bT = x * matrix[2][0] + y * matrix[2][1] + z * matrix[2][2];
        double rD = viewing.rgbD()[0] * rT;
        double gD = viewing.rgbD()[1] * gT;
        double bD = viewing.rgbD()[2] * bT;
        double rAF = Math.pow(viewing.fl() * Math.abs(rD) / 100.0, 0.42);
        double gAF = Math.pow(viewing.fl() * Math.abs(gD) / 100.0, 0.42);
        double bAF = Math.pow(viewing.fl() * Math.abs(bD) / 100.0, 0.42);
        double rA = Math.signum(rD) * 400.0 * rAF / (rAF + 27.13);
        double gA = Math.signum(gD) * 400.0 * gAF / (gAF + 27.13);
        double bA = Math.signum(bD) * 400.0 * bAF / (bAF + 27.13);
        double a = (11.0 * rA + -12.0 * gA + bA) / 11.0;
        double opponentB = (rA + gA - 2.0 * bA) / 9.0;
        double u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0;
        double p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0;
        double degrees = Math.toDegrees(Math.atan2(opponentB, a));
        double hue = degrees < 0.0 ? degrees + 360.0 : degrees >= 360.0 ? degrees - 360.0 : degrees;
        double radians = Math.toRadians(hue);
        double ac = p2 * viewing.nbb();
        double j = 100.0 * Math.pow(ac / viewing.aw(), viewing.c() * viewing.z());
        double q = 4.0 / viewing.c() * Math.sqrt(j / 100.0) * (viewing.aw() + 4.0) * viewing.flRoot();
        double huePrime = hue < 20.14 ? hue + 360.0 : hue;
        double eHue = 0.25 * (Math.cos(Math.toRadians(huePrime) + 2.0) + 3.8);
        double p1 = 3846.153846153846 * eHue * viewing.nc() * viewing.ncb();
        double t = p1 * Math.hypot(a, opponentB) / (u + 0.305);
        double alpha = Math.pow(1.64 - Math.pow(0.29, viewing.n()), 0.73) * Math.pow(t, 0.9);
        double chroma = alpha * Math.sqrt(j / 100.0);
        double m = chroma * viewing.flRoot();
        double s = 50.0 * Math.sqrt(alpha * viewing.c() / (viewing.aw() + 4.0));
        double jstar = 1.7000000000000002 * j / (1.0 + 0.007 * j);
        double mstar = 43.859649122807014 * Math.log1p(0.0228 * m);
        return new Cam16Color(hue, chroma, j, q, m, s, jstar,
                              mstar * Math.cos(radians), mstar * Math.sin(radians));
    }

    public static Cam16Color fromJch(double j, double chroma, double hue) {
        return fromJch(j, chroma, hue, ViewingConditions.DEFAULT);
    }

    public static Cam16Color fromJch(double j, double chroma, double hue, ViewingConditions viewing) {
        double q = 4.0 / viewing.c() * Math.sqrt(j / 100.0) * (viewing.aw() + 4.0) * viewing.flRoot();
        double m = chroma * viewing.flRoot();
        double alpha = chroma / Math.sqrt(j / 100.0);
        double s = 50.0 * Math.sqrt(alpha * viewing.c() / (viewing.aw() + 4.0));
        double radians = Math.toRadians(hue);
        double jstar = 1.7000000000000002 * j / (1.0 + 0.007 * j);
        double mstar = 43.859649122807014 * Math.log1p(0.0228 * m);
        return new Cam16Color(hue, chroma, j, q, m, s, jstar,
                              mstar * Math.cos(radians), mstar * Math.sin(radians));
    }

    public static Cam16Color fromUcs(double jstar, double astar, double bstar) {
        return fromUcs(jstar, astar, bstar, ViewingConditions.DEFAULT);
    }

    public static Cam16Color fromUcs(double jstar, double astar, double bstar, ViewingConditions viewing) {
        double mstar = Math.hypot(astar, bstar);
        double m = Math.expm1(mstar * 0.0228) / 0.0228;
        double chroma = m / viewing.flRoot();
        double hue = Math.atan2(bstar, astar) * 57.29577951308232;
        if (hue < 0.0) hue += 360.0;
        double j = jstar / (1.0 - (jstar - 100.0) * 0.007);
        return fromJch(j, chroma, hue, viewing);
    }

    public double distance(Cam16Color other) {
        double dj = jstar - other.jstar;
        double da = astar - other.astar;
        double db = bstar - other.bstar;
        return 1.41 * Math.pow(Math.sqrt(dj * dj + da * da + db * db), 0.63);
    }

    public int toArgb(PackedColor packed) { return viewed(ViewingConditions.DEFAULT, packed); }

    public int viewed(ViewingConditions viewing, PackedColor packed) {
        double[] xyz = toXyz(viewing);
        return packed.argbFromXyz(xyz[0], xyz[1], xyz[2]);
    }

    /** The original inverse calculation, split immediately before com.y.a(X,Y,Z). */
    public double[] toXyz(ViewingConditions viewing) {
        double alpha = chroma == 0.0 || j == 0.0 ? 0.0 : chroma / Math.sqrt(j / 100.0);
        double t = Math.pow(alpha / Math.pow(1.64 - Math.pow(0.29, viewing.n()), 0.73), 1.1111111111111112);
        double radians = Math.toRadians(hue);
        double eHue = 0.25 * (Math.cos(radians + 2.0) + 3.8);
        double ac = viewing.aw() * Math.pow(j / 100.0, 1.0 / viewing.c() / viewing.z());
        double p1 = eHue * 3846.153846153846 * viewing.nc() * viewing.ncb();
        double p2 = ac / viewing.nbb();
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * cos + 108.0 * t * sin);
        double a = gamma * cos;
        double b = gamma * sin;
        double rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0;
        double gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0;
        double bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0;
        double rBase = Math.max(0.0, 27.13 * Math.abs(rA) / (400.0 - Math.abs(rA)));
        double rC = Math.signum(rA) * (100.0 / viewing.fl()) * Math.pow(rBase, 2.380952380952381);
        double gBase = Math.max(0.0, 27.13 * Math.abs(gA) / (400.0 - Math.abs(gA)));
        double gC = Math.signum(gA) * (100.0 / viewing.fl()) * Math.pow(gBase, 2.380952380952381);
        double bBase = Math.max(0.0, 27.13 * Math.abs(bA) / (400.0 - Math.abs(bA)));
        double bC = Math.signum(bA) * (100.0 / viewing.fl()) * Math.pow(bBase, 2.380952380952381);
        double rF = rC / viewing.rgbD()[0];
        double gF = gC / viewing.rgbD()[1];
        double bF = bC / viewing.rgbD()[2];
        double[][] matrix = MaterialColorMath.CAM16RGB_TO_XYZ;
        return new double[]{
            rF * matrix[0][0] + gF * matrix[0][1] + bF * matrix[0][2],
            rF * matrix[1][0] + gF * matrix[1][1] + bF * matrix[1][2],
            rF * matrix[2][0] + gF * matrix[2][1] + bF * matrix[2][2]};
    }
}
