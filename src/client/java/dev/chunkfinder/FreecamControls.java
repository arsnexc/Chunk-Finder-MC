package dev.chunkfinder;

import dev.chunkfinder.core.FreecamMovement;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Input;

/** Native camera basis and actual remappable bindings; does not create or require a world. */
final class FreecamControls extends Camera {
    private CameraType previousView;

    Input captureAndMask(Options options) {
        Input input = new Input(options.keyUp.isDown(), options.keyDown.isDown(), options.keyLeft.isDown(),
                options.keyRight.isDown(), options.keyJump.isDown(), options.keyShift.isDown(), options.keySprint.isDown());
        restore(options, Input.EMPTY);
        return input;
    }
    void restore(Options options, Input input) {
        setDown(options.keyUp, input.forward()); setDown(options.keyDown, input.backward());
        setDown(options.keyLeft, input.left()); setDown(options.keyRight, input.right());
        setDown(options.keyJump, input.jump()); setDown(options.keyShift, input.shift()); setDown(options.keySprint, input.sprint());
    }
    private static void setDown(KeyMapping key, boolean down) {
        if (key.isDown() == down) return;
        key.setDown(down);
        // ToggleKeyMapping ignores setDown(false) in toggle mode. A single press
        // turns it off; never press an already-correct toggle and invert its state.
        if (key.isDown() != down) key.setDown(true);
    }
    void beginView(Options options) {
        previousView = options.getCameraType();
        enforceView(options);
    }
    void enforceView(Options options) { options.setCameraType(CameraType.FIRST_PERSON); }
    void endView(Options options, boolean mayRestore) {
        if (mayRestore && options != null && previousView != null && options.getCameraType() == CameraType.FIRST_PERSON)
            options.setCameraType(previousView);
        previousView = null;
    }
    FreecamMovement.Vector movement(float yaw, float pitch, Input input, int speed, boolean swapStrafe) {
        return movement(yaw, pitch, input, speed, swapStrafe, false);
    }
    FreecamMovement.Vector movement(float yaw, float pitch, Input input, int speed, boolean swapStrafe, boolean horizontal) {
        setRotation(yaw, horizontal ? 0 : pitch); // Native basis; only movement ignores pitch in horizontal mode.
        var look = forwardVector(); var left = leftVector();
        int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        int strafe = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
        int vertical = (input.jump() ? 1 : 0) - (input.shift() ? 1 : 0);
        return FreecamMovement.offset(new FreecamMovement.Vector(look.x(), look.y(), look.z()),
                new FreecamMovement.Vector(left.x(), left.y(), left.z()), forward, swapStrafe ? -strafe : strafe,
                vertical, speed * (input.sprint() ? 0.2 : 0.1));
    }
}
