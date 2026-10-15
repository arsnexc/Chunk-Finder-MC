package dev.chunkfinder.core;

import dev.chunkfinder.core.FreecamMovement.Vector;

/** Optional, tick-based acceleration/braking. Zero settings preserve immediate legacy controls. */
public final class FlightSmoothing {
    private static final Vector ZERO = new Vector(0, 0, 0);
    private Vector velocity = ZERO;
    private boolean braking;
    private double brakeStep;
    public void reset() { velocity = ZERO; braking = false; brakeStep = 0; }
    public Vector step(Vector desired, int acceleration, int deceleration) {
        double desiredLength = length(desired);
        if (!Double.isFinite(desiredLength)) { reset(); return ZERO; }
        if (desiredLength > 1.0e-9) {
            braking = false;
            velocity = acceleration <= 0 ? desired : towards(velocity, desired, desiredLength / Math.min(30, acceleration));
            double actual = length(velocity);
            if (actual > desiredLength) velocity = new Vector(velocity.x() * desiredLength / actual, velocity.y() * desiredLength / actual, velocity.z() * desiredLength / actual);
        } else if (deceleration <= 0) reset();
        else {
            if (!braking) { braking = true; brakeStep = length(velocity) / Math.min(30, deceleration); }
            velocity = towards(velocity, ZERO, brakeStep);
        }
        return velocity;
    }
    private static Vector towards(Vector from, Vector to, double amount) {
        double x = to.x() - from.x(), y = to.y() - from.y(), z = to.z() - from.z();
        double distance = Math.sqrt(x*x + y*y + z*z);
        if (distance <= Math.max(1.0e-9, amount)) return to;
        if (amount <= 0) return from;
        double scale = amount / distance;
        return new Vector(from.x() + x * scale, from.y() + y * scale, from.z() + z * scale);
    }
    private static double length(Vector v) { return v == null ? Double.NaN : Math.sqrt(v.x()*v.x() + v.y()*v.y() + v.z()*v.z()); }
}
