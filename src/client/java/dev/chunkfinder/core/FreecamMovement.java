package dev.chunkfinder.core;

/** Camera-relative movement math, independent of Minecraft input/camera ownership. */
public final class FreecamMovement {
    public record Vector(double x, double y, double z) { }
    private static final Vector ZERO = new Vector(0, 0, 0);

    private FreecamMovement() { }

    /** Strafe is positive for Right/D; forward follows the supplied native camera look vector. */
    public static Vector offset(double yawDegrees, Vector look, int forward, int strafe, int vertical, double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return ZERO;
        double yaw = Math.toRadians(yawDegrees);
        // Minecraft yaw 0 looks South (+Z), so screen-right is West (-X).
        double dx = look.x * forward - Math.cos(yaw) * strafe;
        double dy = look.y * forward + vertical;
        double dz = look.z * forward - Math.sin(yaw) * strafe;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        // Native look vectors have floating-point residue at vertical pitch.
        // Do not magnify almost-cancelled input into a full-speed horizontal step.
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0e-12) return ZERO;
        double scale = distance / Math.sqrt(lengthSquared);
        return new Vector(dx * scale, dy * scale, dz * scale);
    }
}
