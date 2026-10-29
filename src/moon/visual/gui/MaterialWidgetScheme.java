package moon.visual.gui;

import moon.visual.color.HctColor;
import moon.visual.color.MaterialScheme;

/** Explicit Material Scheme -> kb/kg field-role bridge. This adapter does not
 * choose numeric profiles, install a theme or copy the mutable Scheme object. */
public final class MaterialWidgetScheme implements ColorSettingWidget.Scheme {
    private final MaterialScheme scheme;
    public MaterialWidgetScheme(MaterialScheme scheme) { this.scheme = scheme; }
    public static MaterialWidgetScheme dark(int argb, HctColor.ColorScience science) {
        return new MaterialWidgetScheme(MaterialScheme.dark(argb, science));
    }
    public MaterialScheme scheme() { return scheme; }
    public int accent() { return scheme.primary(); }
    public int track() { return scheme.surfaceVariant(); }
    public int text() { return scheme.onSurfaceVariant(); }
    public int onPrimary() { return scheme.onPrimary(); }
    public int primaryContainer() { return scheme.primaryContainer(); }
    public int onPrimaryContainer() { return scheme.onPrimaryContainer(); }
    public int secondary() { return scheme.secondary(); }
    public int onSecondary() { return scheme.onSecondary(); }
    public int secondaryContainer() { return scheme.secondaryContainer(); }
    public int onSecondaryContainer() { return scheme.onSecondaryContainer(); }
}
