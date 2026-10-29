package moon.module.combat.killaura;

import java.util.*;
import moon.module.combat.killaura.KillAuraSettings.*;

/** Readable e2 normal target pipeline. Host owns AntiBot/Teams/friend/priority data. */
public final class KillAuraTargetSelector {
    public record Target(int id, TargetKind kind, double x, double y, double z, float height,
            float health, boolean invisible, boolean rejected, boolean preferred) { }
    public record Player(double x, double y, double z, float eyeHeight, float yaw, float pitch) { }
    public Target select(Player player, Collection<Target> entities, KillAuraSettings settings) {
        List<Target> eligible = new ArrayList<>();
        float reach = Math.max(settings.aimReach.get(), settings.reach.get());
        for (Target entity : entities) {
            if (entity.rejected() || !settings.targets.isSelected(entity.kind())) continue;
            if (entity.invisible() && !settings.targets.isSelected(TargetKind.INVISIBLES)) continue;
            if (distance(player, entity) <= reach) eligible.add(entity);
        }
        // e2 checks for any priority target before applying the selected comparator.
        if (eligible.stream().anyMatch(Target::preferred)) eligible.removeIf(t -> !t.preferred());
        Comparator<Target> comparator = switch (settings.sorting.get()) {
            case DISTANCE -> Comparator.comparingDouble(t -> distance(player, t));
            case HEALTH -> Comparator.comparingDouble(Target::health);
            case FOV -> Comparator.comparingDouble(t -> fov(player, t));
        };
        return eligible.stream().min(comparator).orElse(null);
    }
    public static double distance(Player p, Target t) {
        double dx=t.x()-p.x(), dy=t.y()-p.y(), dz=t.z()-p.z();
        // Entity.getDistanceToEntity returns float in the original and in MCP.
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    static double fov(Player p, Target t) {
        double yaw = Math.toDegrees(Math.atan2(t.z()-p.z(), t.x()-p.x()))-90;
        double difference = Math.abs(p.yaw()-yaw)%360;
        return difference > 180 ? 360-difference : difference;
    }
}
