package moon.visual.color;

/** Original com.e normal-path HCT mathematics and control flow.
 * No default parameters/backend are invented: encrypted numeric calls and
 * critical-plane indices still require original evidence before installation.
 * Helpers are package-visible for independent numeric checks, as in com.e. */
public final class HctSolver {
    public interface ColorBackend extends Cam16Color.PackedColor {
        int argbFromLinearRgb(double[] linearRgb);
        int argbFromLstar(double lstar);
    }

    /** Four unresolved dynamic-call sites in original helpers; the middleLimit
     * and iterations calls share an operand but have distinct long keys.
     * initialRightPlane is overwritten normally; retained as an explicit edge. */
    public record Parameters(int middleVertexLimit, int vertexCount,
                             int initialRightPlane, int bisectionIterations) { }

    static final double[][] SCALED_DISCOUNT_FROM_LINRGB = {
        {0.001200833568784504, 0.002389694492170889, 0.0002795742885861124},
        {0.0005891086651375999, 0.0029785502573438758, 0.0003270666104008398},
        {0.00010146692491640572, 0.0005364214359186694, 0.0032979401770712076}
    };
    static final double[][] LINRGB_FROM_SCALED_DISCOUNT = {
        {1373.2198709594231, -1100.4251190754821, -7.278681089101213},
        {-271.815969077903, 559.6580465940733, -32.46047482791194},
        {1.9622899599665666, -57.173814538844006, 308.7233197812385}
    };
    static final double[] Y_FROM_LINRGB = {0.2126, 0.7152, 0.0722};

    private final Parameters parameters;
    private final double[] criticalPlanes;
    private final ColorBackend backend;

    /** Dependencies are borrowed without validation or copying. The original
     * critical-plane array is mutable; this preserves observable array reads. */
    public HctSolver(Parameters parameters, double[] criticalPlanes, ColorBackend backend) {
        this.parameters = parameters;
        this.criticalPlanes = criticalPlanes;
        this.backend = backend;
    }

    static double sanitizeRadians(double radians) {
        return (radians + Math.PI * 8) % (Math.PI * 2);
    }

    static double trueDelinearized(double channel) {
        double normalized = channel / 100.0;
        double delinearized;
        if (normalized <= 0.0031308) delinearized = normalized * 12.92;
        else delinearized = 1.055 * Math.pow(normalized, 0.4166666666666667) - 0.055;
        return delinearized * 255.0;
    }

    static double chromaticAdaptation(double component) {
        double factor = Math.pow(Math.abs(component), 0.42);
        return MaterialColorMath.signum(component) * 400.0 * factor / (factor + 27.13);
    }

    static double hueOf(double[] linearRgb) {
        double[] scaled = MaterialColorMath.matrixMultiply(linearRgb, SCALED_DISCOUNT_FROM_LINRGB);
        double rA = chromaticAdaptation(scaled[0]);
        double gA = chromaticAdaptation(scaled[1]);
        double bA = chromaticAdaptation(scaled[2]);
        double a = (11.0 * rA + -12.0 * gA + bA) / 11.0;
        double b = (rA + gA - 2.0 * bA) / 9.0;
        return Math.atan2(b, a);
    }

    static boolean areInCyclicOrder(double a, double b, double c) {
        double deltaAB = sanitizeRadians(b - a);
        double deltaAC = sanitizeRadians(c - a);
        return deltaAB < deltaAC;
    }

    static double intercept(double source, double mid, double target) {
        return (mid - source) / (target - source);
    }

    static double[] lerpPoint(double[] source, double amount, double[] target) {
        return new double[]{source[0] + (target[0] - source[0]) * amount,
            source[1] + (target[1] - source[1]) * amount,
            source[2] + (target[2] - source[2]) * amount};
    }

    static double[] setCoordinate(double[] source, double coordinate, double[] target, int axis) {
        double amount = intercept(source[axis], coordinate, target[axis]);
        return lerpPoint(source, amount, target);
    }

    static boolean isBounded(double value) { return 0.0 <= value && value <= 100.0; }

    double[] nthVertex(double y, int n) {
        double kR = Y_FROM_LINRGB[0];
        double kG = Y_FROM_LINRGB[1];
        double kB = Y_FROM_LINRGB[2];
        double coordA = n % 4 <= 1 ? 0.0 : 100.0;
        double coordB = n % 2 == 0 ? 0.0 : 100.0;
        if (n < 4) {
            double g = coordA;
            double b = coordB;
            double r = (y - g * kG - b * kB) / kR;
            return isBounded(r) ? new double[]{r, g, b} : new double[]{-1.0, -1.0, -1.0};
        }
        if (n < parameters.middleVertexLimit()) {
            double b = coordA;
            double r = coordB;
            double g = (y - r * kR - b * kB) / kG;
            return isBounded(g) ? new double[]{r, g, b} : new double[]{-1.0, -1.0, -1.0};
        }
        double r = coordA;
        double g = coordB;
        double b = (y - r * kR - g * kG) / kB;
        return isBounded(b) ? new double[]{r, g, b} : new double[]{-1.0, -1.0, -1.0};
    }

    double[][] bisectToSegment(double y, double targetHue) {
        double[] left = {-1.0, -1.0, -1.0};
        double[] right = left;
        double leftHue = 0.0;
        double rightHue = 0.0;
        boolean initialized = false;
        boolean uncut = true;
        for (int n = 0; n < parameters.vertexCount(); n++) {
            double[] mid = nthVertex(y, n);
            if (mid[0] < 0.0) continue;
            double midHue = hueOf(mid);
            if (!initialized) {
                left = mid;
                right = mid;
                leftHue = midHue;
                rightHue = midHue;
                initialized = true;
                continue;
            }
            if (uncut || areInCyclicOrder(leftHue, midHue, rightHue)) {
                uncut = false;
                if (areInCyclicOrder(leftHue, targetHue, midHue)) {
                    right = mid;
                    rightHue = midHue;
                } else {
                    left = mid;
                    leftHue = midHue;
                }
            }
        }
        return new double[][]{left, right};
    }

    static double[] midpoint(double[] a, double[] b) {
        return new double[]{(a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0, (a[2] + b[2]) / 2.0};
    }

    static int criticalPlaneBelow(double value) { return (int)Math.floor(value - 0.5); }
    static int criticalPlaneAbove(double value) { return (int)Math.ceil(value - 0.5); }

    double[] bisectToLimit(double y, double targetHue) {
        double[][] segment = bisectToSegment(y, targetHue);
        double[] left = segment[0];
        double leftHue = hueOf(left);
        double[] right = segment[1];
        for (int axis = 0; axis < 3; axis++) {
            if (left[axis] != right[axis]) {
                int leftPlane = -1;
                int rightPlane = parameters.initialRightPlane();
                if (left[axis] < right[axis]) {
                    leftPlane = criticalPlaneBelow(trueDelinearized(left[axis]));
                    rightPlane = criticalPlaneAbove(trueDelinearized(right[axis]));
                } else {
                    leftPlane = criticalPlaneAbove(trueDelinearized(left[axis]));
                    rightPlane = criticalPlaneBelow(trueDelinearized(right[axis]));
                }
                for (int i = 0; i < parameters.bisectionIterations(); i++) {
                    if (Math.abs(rightPlane - leftPlane) <= 1) break;
                    int midPlane = (int)Math.floor((leftPlane + rightPlane) / 2.0);
                    double coordinate = criticalPlanes[midPlane];
                    double[] mid = setCoordinate(left, coordinate, right, axis);
                    double midHue = hueOf(mid);
                    if (areInCyclicOrder(leftHue, targetHue, midHue)) {
                        right = mid;
                        rightPlane = midPlane;
                    } else {
                        left = mid;
                        leftHue = midHue;
                        leftPlane = midPlane;
                    }
                }
            }
        }
        return midpoint(left, right);
    }

    static double inverseChromaticAdaptation(double adapted) {
        double magnitude = Math.abs(adapted);
        double base = Math.max(0.0, 27.13 * magnitude / (400.0 - magnitude));
        return MaterialColorMath.signum(adapted) * Math.pow(base, 2.380952380952381);
    }

    int findResultByJ(double hueRadians, double chroma, double y) {
        double j = Math.sqrt(y) * 11.0;
        ViewingConditions viewing = ViewingConditions.DEFAULT;
        double tInnerCoeff = 1.0 / Math.pow(1.64 - Math.pow(0.29, viewing.n()), 0.73);
        double eHue = 0.25 * (Math.cos(hueRadians + 2.0) + 3.8);
        double p1 = eHue * 3846.153846153846 * viewing.nc() * viewing.ncb();
        double sin = Math.sin(hueRadians);
        double cos = Math.cos(hueRadians);
        for (int iteration = 0; iteration < 5; iteration++) {
            double jNormalized = j / 100.0;
            double alpha = chroma == 0.0 || j == 0.0 ? 0.0 : chroma / Math.sqrt(jNormalized);
            double t = Math.pow(alpha * tInnerCoeff, 1.1111111111111112);
            double ac = viewing.aw() * Math.pow(jNormalized, 1.0 / viewing.c() / viewing.z());
            double p2 = ac / viewing.nbb();
            double gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * cos + 108.0 * t * sin);
            double a = gamma * cos;
            double b = gamma * sin;
            double rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0;
            double gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0;
            double bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0;
            double rC = inverseChromaticAdaptation(rA);
            double gC = inverseChromaticAdaptation(gA);
            double bC = inverseChromaticAdaptation(bA);
            double[] linearRgb = MaterialColorMath.matrixMultiply(new double[]{rC, gC, bC}, LINRGB_FROM_SCALED_DISCOUNT);
            if (linearRgb[0] < 0.0 || linearRgb[1] < 0.0 || linearRgb[2] < 0.0) return 0;
            double kR = Y_FROM_LINRGB[0];
            double kG = Y_FROM_LINRGB[1];
            double kB = Y_FROM_LINRGB[2];
            double fnj = kR * linearRgb[0] + kG * linearRgb[1] + kB * linearRgb[2];
            if (fnj <= 0.0) return 0;
            if (iteration == 4 || Math.abs(fnj - y) < 0.002) {
                if (linearRgb[0] > 100.01 || linearRgb[1] > 100.01 || linearRgb[2] > 100.01) return 0;
                return backend.argbFromLinearRgb(linearRgb);
            }
            j -= (fnj - y) * j / (2.0 * fnj);
        }
        return 0;
    }

    public int solveToInt(double hueDegrees, double chroma, double lstar) {
        if (chroma < 1.0e-4 || lstar < 1.0e-4 || lstar > 99.9999) return backend.argbFromLstar(lstar);
        hueDegrees = MaterialColorMath.sanitizeDegrees(hueDegrees);
        double hueRadians = hueDegrees / 180.0 * Math.PI;
        double y = MaterialColorMath.yFromLstar(lstar);
        int exact = findResultByJ(hueRadians, chroma, y);
        if (exact != 0) return exact;
        double[] linearRgb = bisectToLimit(y, hueRadians);
        return backend.argbFromLinearRgb(linearRgb);
    }

    public Cam16Color solveToCam(double hueDegrees, double chroma, double lstar) {
        return Cam16Color.fromArgb(solveToInt(hueDegrees, chroma, lstar), backend);
    }
}
