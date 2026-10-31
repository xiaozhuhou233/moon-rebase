package moon.module.combat.killaura;

import java.util.List;
import moon.visual.gui.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** com.moon.jc s..W, with keys/defaults from the supplied runtime settings.
 * CPS ceiling 20 is a destination policy until jc's encrypted ceiling is recovered.
 * Enum identifiers/order come from aw/av/au/at/ar/as; labels use host English.
 */
public final class KillAuraSettings {
    public enum TargetKind implements DisplayNamed {
        PLAYERS, ANIMALS, MOBS, INVISIBLES;
        public String displayName() { return name(); }
    }
    public enum Sort implements DisplayNamed {
        FOV, HEALTH, DISTANCE;
        public String displayName() { return name(); }
    }
    public enum Randomization implements DisplayNamed {
        NORMAL, ATTACK_POINT, CIRCULAR;
        public String displayName() { return name(); }
    }
    public enum ClickPattern implements DisplayNamed {
        BASIC, ADVANCED, LATEST;
        public String displayName() { return name(); }
    }
    public enum BlockMode implements DisplayNamed {
        PRE, POST, WATCHDOG, FAKE, SPOOF;
        public String displayName() { return name(); }
    }
    public enum ClickBlockMode implements DisplayNamed {
        HURT_TIME, ATTACK_SYNC, OPTIMIZED, FAKE, HOLD;
        public String displayName() { return name(); }
    }
    public final FloatSetting aimReach, reach, fov, rotationSpeed, circularSpeed, randomizationAmount;
    public final IntegerSetting cps;
    public final BooleanSetting velocityAttackSync, rotate, correctMovement, silentlyCorrect,
        onlyRotateWhenNeeded, dontRotateInHitbox, preventBackSnap, doubleClicks,
        balancedDeviation, forceClickOnTickbase, realClick, ignoreBlocking, stopSprintOnAttack, autoBlock,
        clickAutoBlock, unblockOnAttack, aggressive;
    public final MultiChoiceSetting<TargetKind> targets;
    public final MultiChoiceSetting<Randomization> randomization;
    public final EnumSetting<Sort> sorting;
    public final EnumSetting<ClickPattern> clickPattern;
    public final EnumSetting<BlockMode> blockMode;
    public final EnumSetting<ClickBlockMode> clickBlockMode;
    private final List<SettingValue<?>> roots;

    public KillAuraSettings(MarkerHooks hooks) {
        aimReach = number(hooks, "aim-reach", "Aiming Reach", 3, 3, 7, .1F);
        reach = number(hooks, "reach", "Attack Reach", 3, 3, 6, .1F);
        fov = number(hooks, "fov", "FOV", 360, 0, 360, 1);
        cps = IntegerSetting.builder("cps", hooks).configure("CPS", 8, 1, 20, 1).build();
        velocityAttackSync = flag(hooks, "velocity-attack-sync", "Attack sync with velocity", false);
        targets = MultiChoiceSetting.builder("targets", TargetKind.class, hooks).configure("Targets", TargetKind.PLAYERS).build();
        sorting = EnumSetting.builder("sorting-type", Sort.class, hooks).configure("Sorting Mode", Sort.FOV).build();
        rotate = flag(hooks, "rotate", "Rotate", true);
        correctMovement = flag(hooks, "correct-movement", "Correct movement", false);
        silentlyCorrect = BooleanSetting.builder("silently-correct", hooks).configure("Silently correct", false).visibility(correctMovement::get).build();
        rotationSpeed = number(hooks, "rotation-speed", "Rotation Speed", 90, 1, 180, 1);
        onlyRotateWhenNeeded = flag(hooks, "only-rotate-when-needed", "Only rotate if required", false);
        dontRotateInHitbox = flag(hooks, "dont-rotate-in-hitbox", "Don't rotate in hit-box", true);
        preventBackSnap = flag(hooks, "prevent-back-snap", "Prevent Back-snap", false);
        randomization = MultiChoiceSetting.builder("randomization-types", Randomization.class, hooks).configure("Randomization Types").build();
        circularSpeed = FloatSetting.builder("circular-speed", hooks).configure("Circular speed", .5F, .05F, 2, .05F)
            .visibility(() -> randomization.isSelected(Randomization.CIRCULAR)).build();
        randomizationAmount = FloatSetting.builder("randomization-amount", hooks).configure("Randomization Amount", 2.5F, .1F, 25, .1F)
            .visibility(() -> randomization.isSelected(Randomization.NORMAL)).build();
        clickPattern = EnumSetting.builder("click-pattern", ClickPattern.class, hooks).configure("Click Pattern", ClickPattern.ADVANCED).build();
        doubleClicks = BooleanSetting.builder("double", hooks).configure("Double clicks", true).visibility(() -> clickPattern.get() == ClickPattern.ADVANCED).build();
        balancedDeviation = BooleanSetting.builder("force-balanced-deviation", hooks).configure("Force balanced deviation", true).visibility(() -> clickPattern.get() == ClickPattern.ADVANCED).build();
        forceClickOnTickbase = flag(hooks, "force-click-on-tickbase", "Force click on tick-base", true);
        realClick = flag(hooks, "real-click", "Real click", false);
        ignoreBlocking = BooleanSetting.builder("ignore-blocking", hooks).configure("Ignore blocking", false).visibility(realClick::get).build();
        stopSprintOnAttack = flag(hooks, "stop-sprint-on-attack", "Stop sprint on attack", false);
        autoBlock = flag(hooks, "auto-block", "Auto block", false);
        clickAutoBlock = flag(hooks, "click-auto-block", "Click auto block", false);
        unblockOnAttack = flag(hooks, "unblock-on-attack", "Unblock on attack", true);
        blockMode = EnumSetting.builder("auto-block-mode", BlockMode.class, hooks).configure("Blocking mode", BlockMode.POST)
            .visibility(() -> autoBlock.get() && !clickAutoBlock.get()).build();
        clickBlockMode = EnumSetting.builder("click-auto-block-mode", ClickBlockMode.class, hooks).configure("Blocking mode", ClickBlockMode.HURT_TIME)
            .visibility(() -> autoBlock.get() && clickAutoBlock.get()).build();
        aggressive = BooleanSetting.builder("wd-ab-aggressive", hooks).configure("Aggressive", false)
            .visibility(() -> autoBlock.get() && !clickAutoBlock.get() && blockMode.get() == BlockMode.WATCHDOG).build();
        roots = List.of(aimReach, reach, cps,
            SettingGroup.builder(hooks).configure("Target Settings", targets, sorting, fov).build(),
            SettingGroup.builder(hooks).configure("Rotation Settings", rotate, correctMovement, silentlyCorrect, rotationSpeed,
                preventBackSnap, onlyRotateWhenNeeded, dontRotateInHitbox, randomization, randomizationAmount, circularSpeed).build(),
            SettingGroup.builder(hooks).configure("Attack Settings", clickPattern, doubleClicks, balancedDeviation, forceClickOnTickbase,
                velocityAttackSync, realClick, ignoreBlocking, stopSprintOnAttack, autoBlock, clickAutoBlock, unblockOnAttack, blockMode, clickBlockMode, aggressive).build());
    }
    public List<SettingValue<?>> roots() { return roots; }
    private static FloatSetting number(MarkerHooks h, String k, String n, float v, float min, float max, float step) {
        return FloatSetting.builder(k, h).configure(n, v, min, max, step).build();
    }
    private static BooleanSetting flag(MarkerHooks h, String k, String n, boolean v) {
        return BooleanSetting.builder(k, h).configure(n, v).build();
    }
}
