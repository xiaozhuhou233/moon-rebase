package moon.visual.color;

/** Original com.v normal path: all 27 roles, four factories, mutation and value methods.
 * Tone constants are runtime-seed DES decoded; HCT color science is still injected.
 * Marker-mutating/abnormal sentinel branches are outside this reconstruction. */
public class MaterialScheme {
    private int primary;
    private int onPrimary;
    private int primaryContainer;
    private int onPrimaryContainer;
    private int secondary;
    private int onSecondary;
    private int secondaryContainer;
    private int onSecondaryContainer;
    private int tertiary;
    private int onTertiary;
    private int tertiaryContainer;
    private int onTertiaryContainer;
    private int error;
    private int onError;
    private int errorContainer;
    private int onErrorContainer;
    private int background;
    private int onBackground;
    private int surface;
    private int onSurface;
    private int surfaceVariant;
    private int onSurfaceVariant;
    private int outline;
    private int shadow;
    private int inverseSurface;
    private int inverseOnSurface;
    private int inversePrimary;

    public MaterialScheme() { }

    public MaterialScheme(
        int primary, int onPrimary, int primaryContainer,
        int onPrimaryContainer, int secondary, int onSecondary,
        int secondaryContainer, int onSecondaryContainer, int tertiary,
        int onTertiary, int tertiaryContainer, int onTertiaryContainer,
        int error, int onError, int errorContainer,
        int onErrorContainer, int background, int onBackground,
        int surface, int onSurface, int surfaceVariant,
        int onSurfaceVariant, int outline, int shadow,
        int inverseSurface, int inverseOnSurface, int inversePrimary) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.secondary = secondary;
        this.onSecondary = onSecondary;
        this.secondaryContainer = secondaryContainer;
        this.onSecondaryContainer = onSecondaryContainer;
        this.tertiary = tertiary;
        this.onTertiary = onTertiary;
        this.tertiaryContainer = tertiaryContainer;
        this.onTertiaryContainer = onTertiaryContainer;
        this.error = error;
        this.onError = onError;
        this.errorContainer = errorContainer;
        this.onErrorContainer = onErrorContainer;
        this.background = background;
        this.onBackground = onBackground;
        this.surface = surface;
        this.onSurface = onSurface;
        this.surfaceVariant = surfaceVariant;
        this.onSurfaceVariant = onSurfaceVariant;
        this.outline = outline;
        this.shadow = shadow;
        this.inverseSurface = inverseSurface;
        this.inverseOnSurface = inverseOnSurface;
        this.inversePrimary = inversePrimary;
    }

    public static MaterialScheme light(int argb, HctColor.ColorScience science) {
        return light(CorePalette.fromArgb(argb, science));
    }

    public static MaterialScheme dark(int argb, HctColor.ColorScience science) {
        return dark(CorePalette.fromArgb(argb, science));
    }

    public static MaterialScheme lightContent(int argb, HctColor.ColorScience science) {
        return light(CorePalette.contentFromArgb(argb, science));
    }

    public static MaterialScheme darkContent(int argb, HctColor.ColorScience science) {
        return dark(CorePalette.contentFromArgb(argb, science));
    }

    public static MaterialScheme light(CorePalette palette) {
        return new MaterialScheme()
            .withPrimary(palette.primary.tone(40))
            .withOnPrimary(palette.primary.tone(100))
            .withPrimaryContainer(palette.primary.tone(90))
            .withOnPrimaryContainer(palette.primary.tone(10))
            .withSecondary(palette.secondary.tone(40))
            .withOnSecondary(palette.secondary.tone(100))
            .withSecondaryContainer(palette.secondary.tone(90))
            .withOnSecondaryContainer(palette.secondary.tone(10))
            .withTertiary(palette.tertiary.tone(40))
            .withOnTertiary(palette.tertiary.tone(100))
            .withTertiaryContainer(palette.tertiary.tone(90))
            .withOnTertiaryContainer(palette.tertiary.tone(10))
            .withError(palette.error.tone(40))
            .withOnError(palette.error.tone(100))
            .withErrorContainer(palette.error.tone(90))
            .withOnErrorContainer(palette.error.tone(10))
            .withBackground(palette.neutral.tone(99))
            .withOnBackground(palette.neutral.tone(10))
            .withSurface(palette.neutral.tone(99))
            .withOnSurface(palette.neutral.tone(10))
            .withSurfaceVariant(palette.neutralVariant.tone(90))
            .withOnSurfaceVariant(palette.neutralVariant.tone(30))
            .withOutline(palette.neutralVariant.tone(50))
            .withShadow(palette.neutral.tone(0))
            .withInverseSurface(palette.neutral.tone(20))
            .withInverseOnSurface(palette.neutral.tone(95))
            .withInversePrimary(palette.primary.tone(80));
    }

    public static MaterialScheme dark(CorePalette palette) {
        return new MaterialScheme()
            .withPrimary(palette.primary.tone(80))
            .withOnPrimary(palette.primary.tone(20))
            .withPrimaryContainer(palette.primary.tone(30))
            .withOnPrimaryContainer(palette.primary.tone(90))
            .withSecondary(palette.secondary.tone(80))
            .withOnSecondary(palette.secondary.tone(20))
            .withSecondaryContainer(palette.secondary.tone(30))
            .withOnSecondaryContainer(palette.secondary.tone(90))
            .withTertiary(palette.tertiary.tone(80))
            .withOnTertiary(palette.tertiary.tone(20))
            .withTertiaryContainer(palette.tertiary.tone(30))
            .withOnTertiaryContainer(palette.tertiary.tone(90))
            .withError(palette.error.tone(80))
            .withOnError(palette.error.tone(20))
            .withErrorContainer(palette.error.tone(30))
            .withOnErrorContainer(palette.error.tone(80))
            .withBackground(palette.neutral.tone(10))
            .withOnBackground(palette.neutral.tone(90))
            .withSurface(palette.neutral.tone(10))
            .withOnSurface(palette.neutral.tone(90))
            .withSurfaceVariant(palette.neutralVariant.tone(30))
            .withOnSurfaceVariant(palette.neutralVariant.tone(80))
            .withOutline(palette.neutralVariant.tone(60))
            .withShadow(palette.neutral.tone(0))
            .withInverseSurface(palette.neutral.tone(90))
            .withInverseOnSurface(palette.neutral.tone(20))
            .withInversePrimary(palette.primary.tone(40));
    }

    public int primary() { return primary; }
    public void setPrimary(int value) { primary = value; }
    public MaterialScheme withPrimary(int value) { primary = value; return this; }

    public int onPrimary() { return onPrimary; }
    public void setOnPrimary(int value) { onPrimary = value; }
    public MaterialScheme withOnPrimary(int value) { onPrimary = value; return this; }

    public int primaryContainer() { return primaryContainer; }
    public void setPrimaryContainer(int value) { primaryContainer = value; }
    public MaterialScheme withPrimaryContainer(int value) { primaryContainer = value; return this; }

    public int onPrimaryContainer() { return onPrimaryContainer; }
    public void setOnPrimaryContainer(int value) { onPrimaryContainer = value; }
    public MaterialScheme withOnPrimaryContainer(int value) { onPrimaryContainer = value; return this; }

    public int secondary() { return secondary; }
    public void setSecondary(int value) { secondary = value; }
    public MaterialScheme withSecondary(int value) { secondary = value; return this; }

    public int onSecondary() { return onSecondary; }
    public void setOnSecondary(int value) { onSecondary = value; }
    public MaterialScheme withOnSecondary(int value) { onSecondary = value; return this; }

    public int secondaryContainer() { return secondaryContainer; }
    public void setSecondaryContainer(int value) { secondaryContainer = value; }
    public MaterialScheme withSecondaryContainer(int value) { secondaryContainer = value; return this; }

    public int onSecondaryContainer() { return onSecondaryContainer; }
    public void setOnSecondaryContainer(int value) { onSecondaryContainer = value; }
    public MaterialScheme withOnSecondaryContainer(int value) { onSecondaryContainer = value; return this; }

    public int tertiary() { return tertiary; }
    public void setTertiary(int value) { tertiary = value; }
    public MaterialScheme withTertiary(int value) { tertiary = value; return this; }

    public int onTertiary() { return onTertiary; }
    public void setOnTertiary(int value) { onTertiary = value; }
    public MaterialScheme withOnTertiary(int value) { onTertiary = value; return this; }

    public int tertiaryContainer() { return tertiaryContainer; }
    public void setTertiaryContainer(int value) { tertiaryContainer = value; }
    public MaterialScheme withTertiaryContainer(int value) { tertiaryContainer = value; return this; }

    public int onTertiaryContainer() { return onTertiaryContainer; }
    public void setOnTertiaryContainer(int value) { onTertiaryContainer = value; }
    public MaterialScheme withOnTertiaryContainer(int value) { onTertiaryContainer = value; return this; }

    public int error() { return error; }
    public void setError(int value) { error = value; }
    public MaterialScheme withError(int value) { error = value; return this; }

    public int onError() { return onError; }
    public void setOnError(int value) { onError = value; }
    public MaterialScheme withOnError(int value) { onError = value; return this; }

    public int errorContainer() { return errorContainer; }
    public void setErrorContainer(int value) { errorContainer = value; }
    public MaterialScheme withErrorContainer(int value) { errorContainer = value; return this; }

    public int onErrorContainer() { return onErrorContainer; }
    public void setOnErrorContainer(int value) { onErrorContainer = value; }
    public MaterialScheme withOnErrorContainer(int value) { onErrorContainer = value; return this; }

    public int background() { return background; }
    public void setBackground(int value) { background = value; }
    public MaterialScheme withBackground(int value) { background = value; return this; }

    public int onBackground() { return onBackground; }
    public void setOnBackground(int value) { onBackground = value; }
    public MaterialScheme withOnBackground(int value) { onBackground = value; return this; }

    public int surface() { return surface; }
    public void setSurface(int value) { surface = value; }
    public MaterialScheme withSurface(int value) { surface = value; return this; }

    public int onSurface() { return onSurface; }
    public void setOnSurface(int value) { onSurface = value; }
    public MaterialScheme withOnSurface(int value) { onSurface = value; return this; }

    public int surfaceVariant() { return surfaceVariant; }
    public void setSurfaceVariant(int value) { surfaceVariant = value; }
    public MaterialScheme withSurfaceVariant(int value) { surfaceVariant = value; return this; }

    public int onSurfaceVariant() { return onSurfaceVariant; }
    public void setOnSurfaceVariant(int value) { onSurfaceVariant = value; }
    public MaterialScheme withOnSurfaceVariant(int value) { onSurfaceVariant = value; return this; }

    public int outline() { return outline; }
    public void setOutline(int value) { outline = value; }
    public MaterialScheme withOutline(int value) { outline = value; return this; }

    public int shadow() { return shadow; }
    public void setShadow(int value) { shadow = value; }
    public MaterialScheme withShadow(int value) { shadow = value; return this; }

    public int inverseSurface() { return inverseSurface; }
    public void setInverseSurface(int value) { inverseSurface = value; }
    public MaterialScheme withInverseSurface(int value) { inverseSurface = value; return this; }

    public int inverseOnSurface() { return inverseOnSurface; }
    public void setInverseOnSurface(int value) { inverseOnSurface = value; }
    public MaterialScheme withInverseOnSurface(int value) { inverseOnSurface = value; return this; }

    public int inversePrimary() { return inversePrimary; }
    public void setInversePrimary(int value) { inversePrimary = value; }
    public MaterialScheme withInversePrimary(int value) { inversePrimary = value; return this; }

    @Override public String toString() {
        int inversePrimary = this.inversePrimary;
        int inverseOnSurface = this.inverseOnSurface;
        int inverseSurface = this.inverseSurface;
        int shadow = this.shadow;
        int outline = this.outline;
        int onSurfaceVariant = this.onSurfaceVariant;
        int surfaceVariant = this.surfaceVariant;
        int onSurface = this.onSurface;
        int surface = this.surface;
        int onBackground = this.onBackground;
        int background = this.background;
        int onErrorContainer = this.onErrorContainer;
        int errorContainer = this.errorContainer;
        int onError = this.onError;
        int error = this.error;
        int onTertiaryContainer = this.onTertiaryContainer;
        int tertiaryContainer = this.tertiaryContainer;
        int onTertiary = this.onTertiary;
        int tertiary = this.tertiary;
        int onSecondaryContainer = this.onSecondaryContainer;
        int secondaryContainer = this.secondaryContainer;
        int onSecondary = this.onSecondary;
        int secondary = this.secondary;
        int onPrimaryContainer = this.onPrimaryContainer;
        int primaryContainer = this.primaryContainer;
        int onPrimary = this.onPrimary;
        int primary = this.primary;

        return "Scheme{primary=" + primary
            + ", onPrimary=" + onPrimary
            + ", primaryContainer=" + primaryContainer
            + ", onPrimaryContainer=" + onPrimaryContainer
            + ", secondary=" + secondary
            + ", onSecondary=" + onSecondary
            + ", secondaryContainer=" + secondaryContainer
            + ", onSecondaryContainer=" + onSecondaryContainer
            + ", tertiary=" + tertiary
            + ", onTertiary=" + onTertiary
            + ", tertiaryContainer=" + tertiaryContainer
            + ", onTertiaryContainer=" + onTertiaryContainer
            + ", error=" + error
            + ", onError=" + onError
            + ", errorContainer=" + errorContainer
            + ", onErrorContainer=" + onErrorContainer
            + ", background=" + background
            + ", onBackground=" + onBackground
            + ", surface=" + surface
            + ", onSurface=" + onSurface
            + ", surfaceVariant=" + surfaceVariant
            + ", onSurfaceVariant=" + onSurfaceVariant
            + ", outline=" + outline
            + ", shadow=" + shadow
            + ", inverseSurface=" + inverseSurface
            + ", inverseOnSurface=" + inverseOnSurface
            + ", inversePrimary=" + inversePrimary
            + "}";
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MaterialScheme)) return false;
        // Original invokes Object.equals before comparing fields: distinct
        // schemes stay unequal even when every role matches.
        if (!super.equals(other)) return false;
        MaterialScheme that = (MaterialScheme) other;
        return primary == that.primary &&
            onPrimary == that.onPrimary &&
            primaryContainer == that.primaryContainer &&
            onPrimaryContainer == that.onPrimaryContainer &&
            secondary == that.secondary &&
            onSecondary == that.onSecondary &&
            secondaryContainer == that.secondaryContainer &&
            onSecondaryContainer == that.onSecondaryContainer &&
            tertiary == that.tertiary &&
            onTertiary == that.onTertiary &&
            tertiaryContainer == that.tertiaryContainer &&
            onTertiaryContainer == that.onTertiaryContainer &&
            error == that.error &&
            onError == that.onError &&
            errorContainer == that.errorContainer &&
            onErrorContainer == that.onErrorContainer &&
            background == that.background &&
            onBackground == that.onBackground &&
            surface == that.surface &&
            onSurface == that.onSurface &&
            surfaceVariant == that.surfaceVariant &&
            onSurfaceVariant == that.onSurfaceVariant &&
            outline == that.outline &&
            shadow == that.shadow &&
            inverseSurface == that.inverseSurface &&
            inverseOnSurface == that.inverseOnSurface &&
            inversePrimary == that.inversePrimary;
    }

    @Override public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + primary;
        result = 31 * result + onPrimary;
        result = 31 * result + primaryContainer;
        result = 31 * result + onPrimaryContainer;
        result = 31 * result + secondary;
        result = 31 * result + onSecondary;
        result = 31 * result + secondaryContainer;
        result = 31 * result + onSecondaryContainer;
        result = 31 * result + tertiary;
        result = 31 * result + onTertiary;
        result = 31 * result + tertiaryContainer;
        result = 31 * result + onTertiaryContainer;
        result = 31 * result + error;
        result = 31 * result + onError;
        result = 31 * result + errorContainer;
        result = 31 * result + onErrorContainer;
        result = 31 * result + background;
        result = 31 * result + onBackground;
        result = 31 * result + surface;
        result = 31 * result + onSurface;
        result = 31 * result + surfaceVariant;
        result = 31 * result + onSurfaceVariant;
        result = 31 * result + outline;
        result = 31 * result + shadow;
        result = 31 * result + inverseSurface;
        result = 31 * result + inverseOnSurface;
        result = 31 * result + inversePrimary;
        return result;
    }
}
