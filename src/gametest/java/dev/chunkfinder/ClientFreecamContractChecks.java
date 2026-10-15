package dev.chunkfinder;

import dev.chunkfinder.core.FreecamMovement.Vector;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.joml.Vector3fc;

/** Native camera/key-mapping checks, without creating a player/world or changing physical bindings. */
final class ClientFreecamContractChecks {
    private static int assertions;
    private static final class ReferenceCamera extends Camera {
        void aim(float yaw, float pitch) { setRotation(yaw, pitch); }
    }
    static int run(Minecraft client) {
        assertions = 0;
        var options = client.options;
        FreecamControls controls = new FreecamControls();
        Input before = controls.captureAndMask(options);
        CameraType oldView = options.getCameraType();
        boolean oldCrouch = options.toggleCrouch().get(), oldSprint = options.toggleSprint().get();
        KeyboardInput vanilla = new KeyboardInput(options);
        ReferenceCamera reference = new ReferenceCamera();
        try {
            for (float yaw : new float[]{0, 45, 90, 180, 270, -90}) for (float pitch : new float[]{0, -75, 75}) {
                reference.aim(yaw, pitch);
                for (boolean left : new boolean[]{true, false}) {
                    Input requested = new Input(false, false, left, !left, false, false, false);
                    controls.restore(options, requested);
                    vanilla.tick();
                    check(vanilla.keyPresses.equals(requested), "real remappable A/D bindings reach vanilla KeyboardInput");
                    float vanillaLeft = vanilla.getMoveVector().x;
                    Input captured = controls.captureAndMask(options);
                    check(captured.equals(vanilla.keyPresses), "freecam captures the same left/right state as vanilla");
                    vanilla.tick(); check(vanilla.keyPresses.equals(Input.EMPTY), "masked bindings cannot move the player");
                    Vector movement = controls.movement(yaw, pitch, captured, 5, false);
                    check(Math.abs(dot(movement, reference.leftVector()) - vanillaLeft * 0.5) < 1.0e-5,
                            "A/D agree with the native camera's screen-left basis at yaw " + yaw + " pitch " + pitch);
                    check(Math.abs(dot(movement, reference.forwardVector())) < 1.0e-5 && Math.abs(movement.y()) < 1.0e-5,
                            "strafing stays lateral/horizontal instead of following camera pitch");
                    Vector swapped = controls.movement(yaw, pitch, captured, 5, true);
                    check(Math.abs(dot(swapped, reference.leftVector()) + vanillaLeft * 0.5) < 1.0e-5, "Swap A/D reverses only lateral motion");
                    controls.restore(options, captured); vanilla.tick();
                    check(vanilla.keyPresses.equals(requested), "held A/D state is restored after the player tick");
                }
            }
            for (boolean toggle : new boolean[]{false, true}) {
                options.toggleCrouch().set(toggle); options.toggleSprint().set(toggle);
                Input held = new Input(true, false, false, true, true, true, true);
                controls.restore(options, held); vanilla.tick();
                check(vanilla.keyPresses.equals(held), "native hold/toggle modes can be set without accidental inversion");
                Input captured = controls.captureAndMask(options); vanilla.tick();
                check(vanilla.keyPresses.equals(Input.EMPTY), "sneak/sprint are masked even when their mappings are toggles");
                controls.restore(options, captured); controls.restore(options, captured); vanilla.tick();
                check(vanilla.keyPresses.equals(held), "restoring a toggle twice is idempotent");
                controls.restore(options, Input.EMPTY);
            }
            Input both = new Input(false, false, true, true, false, false, false);
            Vector stopped = controls.movement(30, 20, both, 5, false);
            check(stopped.x() == 0 && stopped.y() == 0 && stopped.z() == 0, "opposing A/D cancel");
            for (CameraType type : CameraType.values()) {
                options.setCameraType(type); controls.beginView(options);
                check(options.getCameraType() == CameraType.FIRST_PERSON, "freecam normalizes every starting perspective");
                options.setCameraType(CameraType.THIRD_PERSON_FRONT); controls.enforceView(options);
                check(options.getCameraType() == CameraType.FIRST_PERSON, "F5 front-view cannot mirror active freecam controls");
                controls.endView(options, true);
                check(options.getCameraType() == type, "previous perspective is restored on exit");
            }
            options.setCameraType(CameraType.THIRD_PERSON_FRONT); controls.beginView(options);
            controls.endView(options, false);
            check(options.getCameraType() == CameraType.FIRST_PERSON, "camera takeover does not force another owner's perspective");
            FreecamModule module = new FreecamModule();
            boolean oldSwap = module.swapStrafe();
            module.setSwapStrafe(!oldSwap);
            check(new FreecamModule().swapStrafe() == !oldSwap, "actual Freecam adapter persists its Swap A/D control");
            module.setSwapStrafe(oldSwap);
        } finally {
            controls.endView(options, false);
            options.toggleCrouch().set(oldCrouch); options.toggleSprint().set(oldSprint);
            controls.restore(options, before); options.setCameraType(oldView);
        }
        return assertions;
    }
    private static double dot(Vector v, Vector3fc axis) { return v.x() * axis.x() + v.y() * axis.y() + v.z() * axis.z(); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
