package moon.visual.state;

import java.util.function.IntConsumer;

/**
 * Readable reconstruction of moom.k2, checked against its bytecode.
 * Original members: a:int -> capability, b:boolean -> enabled.
 * Original a() -> disable(), b() -> enable(), a(boolean) -> setEnabled(boolean).
 * Identity exception trampoline is omitted. No original client code is executed.
 *
 * For a port, use ONE cache owner: Minecraft GlStateManager or this object.
 * Raw GL calls outside the owner can invalidate the cached value.
 */
public final class GlCapabilityState {
    private final int capability;
    private final IntConsumer enableCapability;
    private final IntConsumer disableCapability;
    private boolean enabled = false;

    public GlCapabilityState(int capability, IntConsumer enableCapability,
                             IntConsumer disableCapability) {
        this.capability = capability;
        this.enableCapability = enableCapability;
        this.disableCapability = disableCapability;
    }

    public void disable() { setEnabled(false); }
    public void enable() { setEnabled(true); }

    public void setEnabled(boolean requested) {
        if (requested == enabled) return;
        // Bytecode writes the cache BEFORE invoking GL. Preserve that order,
        // including its observable behavior if a backend call throws.
        enabled = requested;
        if (requested) enableCapability.accept(capability);
        else disableCapability.accept(capability);
    }
}
