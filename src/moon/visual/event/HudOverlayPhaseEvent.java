package moon.visual.event;

/**
 * com.moon.c9 (handoff alias AttackEvent) is published around HUD overlay drawing
 * in moom.gy.a(float), offsets 0x2d..0x41 (PRE), 0x89b..0x8ab (POST).
 * This is a rendering event, not an entity-attack event.
 * Cancellation is inherited from com.moon.cP in the original.
 * Transport-only port: original static obfuscator sentinels are not included.
 */
public final class HudOverlayPhaseEvent {
    public enum Phase { PRE, POST }

    private final Phase phase;
    private boolean cancelled;

    public HudOverlayPhaseEvent(Phase phase) { this.phase = phase; }
    public Phase getPhase() { return phase; }
    public boolean isCancelled() { return cancelled; }
    public HudOverlayPhaseEvent setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        return this;
    }
}
