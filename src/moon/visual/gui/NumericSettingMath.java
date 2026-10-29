package moon.visual.gui;

/** Only Dq's three clamp overloads and kU's decimal rounding, not their unrelated utilities.
 * Host may mirror DR.b() nullness through setDefaultClampBranch. Normal DR is non-null.
 */
public final class NumericSettingMath {
    private static boolean defaultClampBranch=true;
    private NumericSettingMath() { }
    public static float clamp(float value,float minimum,float maximum) {
        if(!defaultClampBranch) return Math.min(value,minimum);
        return value<minimum?minimum:Math.min(value,maximum);
    }
    public static double clamp(double value,double minimum,double maximum) {
        if(!defaultClampBranch) return Math.min(value,minimum);
        return value<minimum?minimum:Math.min(value,maximum);
    }
    public static int clamp(int value,int minimum,int maximum) {
        if(!defaultClampBranch) return Math.min(value,minimum);
        return value<minimum?minimum:Math.min(value,maximum);
    }
    public static double round(double value,int places) {
        double scale=Math.pow(10.0,places);
        return Math.round(value*scale)/scale;
    }
    public static boolean isDefaultClampBranch() { return defaultClampBranch; }
    public static void setDefaultClampBranch(boolean value) { defaultClampBranch=value; }
}
