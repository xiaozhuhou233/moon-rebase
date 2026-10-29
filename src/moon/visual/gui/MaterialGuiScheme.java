package moon.visual.gui;

import moon.visual.color.MaterialScheme;

/** com.v roles consumed by native settings, kr/kw modals and kv curves.
 * Names such as success come from the consuming widget API: original kf/kw
 * actually select secondary/onSecondary, not a separate green success palette.
 * Holds the mutable Scheme by reference. Numeric profile selection is the host's.
 */
public class MaterialGuiScheme implements SettingWidgetFactory.Scheme, ModalOverlay.Scheme {
    private final MaterialScheme scheme;
    public MaterialGuiScheme(MaterialScheme scheme) { this.scheme = scheme; }
    public MaterialScheme scheme() { return scheme; }
    public int accent() { return scheme.primary(); }
    public int onPrimary() { return scheme.onPrimary(); }
    public int onAccent() { return scheme.onPrimary(); }
    public int primaryContainer() { return scheme.primaryContainer(); }
    public int onPrimaryContainer() { return scheme.onPrimaryContainer(); }
    public int secondary() { return scheme.secondary(); }
    public int onSecondary() { return scheme.onSecondary(); }
    public int secondaryContainer() { return scheme.secondaryContainer(); }
    public int onSecondaryContainer() { return scheme.onSecondaryContainer(); }
    public int track() { return scheme.surfaceVariant(); }
    public int text() { return scheme.onSurfaceVariant(); }
    public int outline() { return scheme.outline(); }
    public int success() { return scheme.secondary(); }
    public int onSuccess() { return scheme.onSecondary(); }
    public int error() { return scheme.error(); }
    public int onError() { return scheme.onError(); }
    public int panel() { return scheme.background(); }
    public int onPanel() { return scheme.onBackground(); }
    public int background() { return scheme.secondaryContainer(); }
    public int curve() { return scheme.onSecondaryContainer(); }
}
