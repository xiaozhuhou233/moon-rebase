package moon.module.visual;
import moon.module.visual.clickgui.ClickGuiSettings;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import moon.visual.gui.DisplayNamed;

/**
 * jK controller with both captured ba.b() guard values. l9/l_ are host screens.
 * ClickGuiSettings owns the original seven settings and live screen configuration.
 * dl/dm keep their original names until their publisher semantics are proved.
 */
public final class ClickGuiModule {
    public enum Mode implements DisplayNamed {
        DEFAULT, IM_GUI;
        public String displayName() { throw new UnsupportedOperationException("a1 original display labels unresolved; supply ClickGuiSettings modeLabels"); }
    }
    /** a3/a0/a2 labels decoded from their original tables using an offline seed model;
     * these are not runtime string captures. Enum order is confirmed by classfiles. */
    public enum OpeningType implements DisplayNamed {
        LINEAR("Linear"), OUT_BACK("Out Back");
        private final String label;OpeningType(String label){this.label=label;}public String displayName(){return label;}
    }
    public enum ClosingType implements DisplayNamed {
        LINEAR("Linear"), IN_BACK("In Back");
        private final String label;ClosingType(String label){this.label=label;}public String displayName(){return label;}
    }
    public enum ModuleDesign implements DisplayNamed {
        ROUNDED("Rounded"), STACKED("Stacked");
        private final String label;ModuleDesign(String label){this.label=label;}public String displayName(){return label;}
    }
    public static final Mode DEFAULT_MODE = Mode.DEFAULT;
    public static final boolean DEFAULT_OPEN_ANIMATION = true;
    public static final boolean DEFAULT_CLOSE_ANIMATION = true;
    public static final OpeningType DEFAULT_OPENING_TYPE = OpeningType.LINEAR;
    public static final ClosingType DEFAULT_CLOSING_TYPE = ClosingType.IN_BACK;
    public static final ModuleDesign DEFAULT_MODULE_DESIGN = ModuleDesign.ROUNDED;
    public static final int DEFAULT_COLOR_SETTING = 0xFFFF0000;
    public static final int INITIAL_UI_COLOR = -1;

    public interface Screen {
        /** Original screen a(), invoked only when constructed, before display. */
        void initialize();
    }
    public interface NativeScreen extends Screen {
        void onOriginalDl(); // original l9.j()
        void onOriginalDm(Object event); // original l9.a(dm)
        void onColorSettingChanged(); // original l9.b(), exact body still external
    }
    public interface Backend {
        NativeScreen createNativeScreen(ClickGuiModule module);
        Screen createImGuiScreen();
        Object currentScreen();
        void displayScreen(Screen screen);
        Object createUiColor(int argb); // original com.v.b(int)
        void applyGlobalColor(int argb); // original bw.a().a():bz then bz.a(int)
        /** Original inherited e(); do not guess toggle/disable semantics. */
        void invokeOriginalModuleE();
    }

    private final Backend backend;
    private final Supplier<Mode> mode;
    private final BooleanSupplier originalGuardIsNull;
    private NativeScreen nativeScreen;
    private Screen imGuiScreen;
    private Object uiColor;

    public ClickGuiModule(Backend backend, Supplier<Mode> mode, BooleanSupplier originalGuardIsNull) {
        this.backend=backend;
        this.mode=mode;
        this.originalGuardIsNull=originalGuardIsNull;
        this.uiColor=backend.createUiColor(INITIAL_UI_COLOR);
    }

    /** Original c(). A failed initialization leaves the allocated screen cached. */
    public void openSelectedScreen() {
        // Nonnull guard jumps straight to return193 before reading mode.
        if(!originalGuardIsNull.getAsBoolean()) return;
        switch(mode.get()) {
            case IM_GUI:
                if(imGuiScreen==null) {
                    imGuiScreen=backend.createImGuiScreen();
                    imGuiScreen.initialize();
                }
                backend.displayScreen(imGuiScreen);
                break;
            case DEFAULT:
                if(nativeScreen==null) {
                    nativeScreen=backend.createNativeScreen(this);
                    nativeScreen.initialize();
                }
                backend.displayScreen(nativeScreen);
                break;
            default: break;
        }
        backend.invokeOriginalModuleE();
    }

    public void onOriginalDl() {
        boolean normal=originalGuardIsNull.getAsBoolean();
        Object current=backend.currentScreen();
        if(normal) {
            if(!(current instanceof NativeScreen)) return;
            current=backend.currentScreen();
        }
        // Nonnull skips instanceof and the second read; null==null deliberately
        // reaches the original null receiver invocation and its exception.
        if(current==nativeScreen) nativeScreen.onOriginalDl();
    }

    public void onOriginalDm(Object event) {
        boolean normal=originalGuardIsNull.getAsBoolean();
        Object current=backend.currentScreen();
        if(normal) {
            if(!(current instanceof NativeScreen)) return;
            current=backend.currentScreen();
        }
        // Nonnull skips instanceof and the second read; null==null deliberately
        // reaches the original null receiver invocation and its exception.
        if(current==nativeScreen) nativeScreen.onOriginalDm(event);
    }

    public void onColorChanged(Integer previousValue,Integer newValue) {
        boolean normal=originalGuardIsNull.getAsBoolean();
        if(normal && nativeScreen!=null) nativeScreen.onColorSettingChanged();
        uiColor=backend.createUiColor(newValue.intValue());
        backend.applyGlobalColor(newValue.intValue());
    }

    public NativeScreen getNativeScreen(){return nativeScreen;}
    public Screen getImGuiScreen(){return imGuiScreen;}
    public Object getUiColor(){return uiColor;}

}
