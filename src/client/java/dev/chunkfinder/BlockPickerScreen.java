package dev.chunkfinder;

import net.minecraft.client.gui.screens.Screen;

/** Block ESP entry point retained for compatibility; all selection logic is shared with other ESPs. */
public final class BlockPickerScreen extends TargetPickerScreen {
    public BlockPickerScreen(Screen parent, ItemEspModule module) {
        super(parent, "Block ESP targets", module.targetChoices(), module.targetIds(), module::setTargetIds);
    }
}
