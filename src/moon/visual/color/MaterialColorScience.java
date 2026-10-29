package moon.visual.color;

/** Executable bridge for the recovered HCT state/palette classes. Numeric and
 * packed dependencies stay explicit; production does not instantiate this yet. */
public final class MaterialColorScience implements HctColor.ColorScience {
    private final MaterialColorUtils colors;
    private final HctSolver solver;
    public MaterialColorScience(MaterialColorUtils colors, HctSolver.Parameters parameters, double[] criticalPlanes) {
        this.colors = colors;
        solver = new HctSolver(parameters, criticalPlanes, colors);
    }
    public Cam16Color cam16FromArgb(int argb) { return Cam16Color.fromArgb(argb, colors); }
    public double lstarFromArgb(int argb) { return colors.lstarFromArgb(argb); }
    public int solveToArgb(double hue, double chroma, double tone) { return solver.solveToInt(hue, chroma, tone); }
}
