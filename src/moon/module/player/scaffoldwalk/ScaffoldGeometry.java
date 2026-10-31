package moon.module.player.scaffoldwalk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure geometry for ScaffoldWalk, ported from Scaffold.lua.
 *
 * <p>The Lua controller searched five placement faces (UP, NORTH, SOUTH, WEST,
 * EAST), verified the native hit vector against the clicked face and walked a
 * fixed offset grid around the player. Everything here is world-free so the
 * module can stay off the Minecraft boundary.</p>
 */
public final class ScaffoldGeometry {
    public static final double REACH = 4.5D;
    public static final double FACE_MARGIN = 0.02D;
    public static final double RAY_EPSILON = 0.00002D;

    /** One placement face. Ids follow {@code EnumFacing.getIndex()}. */
    public record Face(int id, String name, int dx, int dy, int dz) {
        public int delta(int axis) {
            return axis == 0 ? dx : axis == 1 ? dy : dz;
        }
    }

    public static final Face UP = new Face(1, "UP", 0, 1, 0);
    public static final Face NORTH = new Face(2, "NORTH", 0, 0, -1);
    public static final Face SOUTH = new Face(3, "SOUTH", 0, 0, 1);
    public static final Face WEST = new Face(4, "WEST", -1, 0, 0);
    public static final Face EAST = new Face(5, "EAST", 1, 0, 0);

    private static final Face[] FACES = {null, UP, NORTH, SOUTH, WEST, EAST};
    private static final Face[] NEIGHBOUR_ORDER = {EAST, WEST, NORTH, SOUTH, UP};

    public static Face byId(int id) {
        return id >= 0 && id < FACES.length ? FACES[id] : null;
    }

    public static Face[] neighbourFaces() {
        return NEIGHBOUR_ORDER.clone();
    }

    public record Offset(int x, int y, int z, int distance) { }

    /** Lua SEARCH_OFFSETS: X -4..5, Y -4..-1, Z -4..5 sorted by distance, then Y desc, X, Z. */
    public static final List<Offset> SEARCH_OFFSETS;

    static {
        List<Offset> offsets = new ArrayList<>();
        for (int x = -4; x <= 5; x++) {
            for (int y = -4; y <= -1; y++) {
                for (int z = -4; z <= 5; z++) {
                    offsets.add(new Offset(x, y, z, x * x + y * y + z * z));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(Offset::distance)
                .thenComparing(Comparator.comparingInt(Offset::y).reversed())
                .thenComparingInt(Offset::x)
                .thenComparingInt(Offset::z));
        SEARCH_OFFSETS = List.copyOf(offsets);
    }

    /**
     * Candidate points on a support face, in the same order as the Lua controller:
     * face centre, the inner 3x3 grid, the eye projection and the player's look
     * intersection with the face plane.
     */
    public static List<double[]> facePoints(double eyeX, double eyeY, double eyeZ,
                                            int supportX, int supportY, int supportZ,
                                            Face face, float lastYaw) {
        List<double[]> points = new ArrayList<>();
        double px = supportX + 0.5D + face.dx() * 0.5D;
        double py = supportY + 0.5D + face.dy() * 0.5D;
        double pz = supportZ + 0.5D + face.dz() * 0.5D;
        double inset = 0.03D;
        double[] steps = {inset, 0.5D, 1.0D - inset};
        points.add(new double[]{px, py, pz});
        for (double a : steps) {
            for (double b : steps) {
                if (face.dx() != 0) {
                    points.add(new double[]{px, supportY + a, supportZ + b});
                } else if (face.dy() != 0) {
                    points.add(new double[]{supportX + a, py, supportZ + b});
                } else {
                    points.add(new double[]{supportX + a, supportY + b, pz});
                }
            }
        }
        if (face.dy() != 0) {
            points.add(new double[]{
                    clamp(eyeX, supportX + inset, supportX + 1.0D - inset), py,
                    clamp(eyeZ, supportZ + inset, supportZ + 1.0D - inset)});
        } else {
            double yaw = Math.toRadians(lastYaw);
            double dx = -Math.sin(yaw);
            double dz = Math.cos(yaw);
            if (face.dx() != 0 && Math.abs(dx) > 0.0001D) {
                double time = (px - eyeX) / dx;
                if (time > 0) pz = clamp(eyeZ + time * dz, supportZ + inset, supportZ + 1.0D - inset);
            } else if (face.dz() != 0 && Math.abs(dz) > 0.0001D) {
                double time = (pz - eyeZ) / dz;
                if (time > 0) px = clamp(eyeX + time * dx, supportX + inset, supportX + 1.0D - inset);
            }
            for (double h : new double[]{0.1D, 0.5D, 0.9D}) {
                points.add(new double[]{px, supportY + h, pz});
            }
            points.add(new double[]{px, clamp(eyeY, supportY + inset, supportY + 1.0D - inset), pz});
        }

        double cx = supportX + 0.5D + face.dx() * 0.5D;
        double cy = supportY + 0.5D + face.dy() * 0.5D;
        double cz = supportZ + 0.5D + face.dz() * 0.5D;
        double yaw = Math.toRadians(lastYaw);
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        double denominator = face.dx() * dx + face.dz() * dz;
        double rx = eyeX + 100.0D * dx;
        double ry = eyeY;
        double rz = eyeZ + 100.0D * dz;
        if (Math.abs(denominator) > 0.000001D) {
            double time = (face.dx() * (cx - eyeX) + face.dy() * (cy - eyeY)
                    + face.dz() * (cz - eyeZ)) / denominator;
            if (time > 0) {
                double hx = eyeX + time * dx;
                double hz = eyeZ + time * dz;
                boolean inFace;
                if (face.dx() != 0) {
                    inFace = eyeY >= supportY && eyeY <= supportY + 1
                            && hz >= supportZ && hz <= supportZ + 1;
                } else {
                    inFace = hx >= supportX && hx <= supportX + 1
                            && eyeY >= supportY && eyeY <= supportY + 1;
                }
                if (inFace) {
                    rx = hx;
                    rz = hz;
                }
            }
        }
        if (face.dx() != 0) {
            points.add(new double[]{cx, clamp(ry, supportY + inset, supportY + 1.0D - inset),
                    clamp(rz, supportZ + inset, supportZ + 1.0D - inset)});
        } else if (face.dy() != 0) {
            points.add(new double[]{clamp(rx, supportX + inset, supportX + 1.0D - inset), cy,
                    clamp(rz, supportZ + inset, supportZ + 1.0D - inset)});
        } else {
            points.add(new double[]{clamp(rx, supportX + inset, supportX + 1.0D - inset),
                    clamp(ry, supportY + inset, supportY + 1.0D - inset), cz});
        }
        return points;
    }

    /**
     * Validates a native hit vector against the clicked support face. Returns the
     * smallest distance to a lateral face edge, or -1 when the hit is off the face.
     */
    public static double faceMargin(double hitX, double hitY, double hitZ,
                                    int supportX, int supportY, int supportZ, Face face) {
        double margin = 1.0D;
        for (int axis = 0; axis < 3; axis++) {
            double value = axis == 0 ? hitX - supportX : axis == 1 ? hitY - supportY : hitZ - supportZ;
            int normal = face.delta(axis);
            if (normal != 0) {
                if (Math.abs(value - (normal > 0 ? 1 : 0)) > RAY_EPSILON) return -1;
            } else {
                double edge = Math.min(value, 1.0D - value);
                if (edge < FACE_MARGIN) return -1;
                margin = Math.min(margin, edge);
            }
        }
        return margin;
    }

    /** Yaw/pitch that looks from the eye at a point. Matches the Lua atan2 pair. */
    public static float[] anglesTo(double eyeX, double eyeY, double eyeZ,
                                   double x, double y, double z) {
        double dx = x - eyeX;
        double dy = y - eyeY;
        double dz = z - eyeZ;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        return new float[]{yaw, pitch};
    }

    /** Wire hit offsets are quantized to 1/16 by the C08 packet writer. */
    public static double wire(double value) {
        int units = (int) ((float) value * 16.0F);
        return units / 16.0D;
    }

    public static float wrap(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    public static float near(float value, float previous) {
        return previous + wrap(value - previous);
    }

    /** Movement heading from camera-relative input, as in the original controller. */
    public static float direction(float yaw, float forward, float strafe) {
        if (forward < 0) yaw += 180.0F;
        if (strafe != 0) {
            yaw -= 90.0F * (forward == 0 ? 1.0F : 0.5F * Math.signum(forward)) * Math.signum(strafe);
        }
        return wrap(yaw);
    }

    /** Silent move fix: quantize the camera input onto the applied rotation. */
    public static float[] fixedInput(float cameraYaw, float yaw, float forward, float strafe) {
        if (Math.abs(forward) + Math.abs(strafe) < 0.0001F) return new float[]{0, 0};
        float angle = wrap(direction(cameraYaw, forward, strafe) - yaw);
        float magnitude = Math.max(Math.abs(forward), Math.abs(strafe));
        float f = angle >= -67.5F && angle < 67.5F ? 1.0F
                : angle >= 112.5F || angle < -112.5F ? -1.0F : 0.0F;
        float s = angle >= 22.5F && angle < 157.5F ? -1.0F
                : angle >= -157.5F && angle < -22.5F ? 1.0F : 0.0F;
        return new float[]{f * magnitude, s * magnitude};
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private ScaffoldGeometry() { }
}
