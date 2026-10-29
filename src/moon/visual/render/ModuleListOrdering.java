package moon.visual.render;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Partial extraction: com.moon.f4.b() + lambda$sortArraylist$0.
 * The original font measurement's encrypted integer remains in the adapter;
 * this helper does not invent a font size or render a replacement HUD.
 */
public final class ModuleListOrdering {
    private ModuleListOrdering() {}

    public static <M> void sortByRenderedWidth(List<M> modules,
            Function<M, String> formattedName, Function<M, String> suffix,
            ToDoubleFunction<String> measureWithOriginalParameters,
            Consumer<Boolean> setHudDirty) {
        modules.sort(Comparator.comparingDouble(module ->
                -measureWithOriginalParameters.applyAsDouble(
                        formattedName.apply(module) + suffix.apply(module))));
        setHudDirty.accept(true);
    }
}
