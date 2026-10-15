package dev.chunkfinder.core;

/** Camera-relative movement math, independent of Minecraft input/camera ownership. */
public final class FreecamMovement {
    public record Vector(double x, double y, double z) { }
    private static final Vector ZERO = new Vector(0, 0, 0);

    private FreecamMovement() { }

    /** Strafe is positive for Right/D; forward follows the supplied native camera look vector. */
    public static Vector offset(double yawDegrees, Vector look, int forward, int strafe, int vertical, double distance) {
        double yaw = Math.toRadians(yawDegrees);
        return offset(look, new Vector(Math.cos(yaw), 0, Math.sin(yaw)), forward, strafe, vertical, distance);
    }

    /** Positive strafe means screen-right, the opposite of the native camera's left vector. */
    public static Vector offset(Vector look, Vector left, int forward, int strafe, int vertical, double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return ZERO;
        double dx = look.x * forward - left.x * strafe;
        double dy = look.y * forward - left.y * strafe + vertical;
        double dz = look.z * forward - left.z * strafe;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        // Native look vectors have floating-point residue at vertical pitch.
        // Do not magnify almost-cancelled input into a full-speed horizontal step.
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0e-12) return ZERO;
        double scale = distance / Math.sqrt(lengthSquared);
        return new Vector(dx * scale, dy * scale, dz * scale);
    }
}
