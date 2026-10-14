package dev.chunkfinder.ui;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Produces a clearly labelled preview from the production renderer, with illustrative fixture values. */
public final class UiPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "build/ui-preview.png");
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 700;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 400;
        int scale = 2;
        try (AwtUiCanvas canvas = new AwtUiCanvas(width, height, scale)) {
            ClickGui gui = UiTestFixtures.preview();
            gui.resize(width, height);
            gui.render(canvas, -1, -1);
            BufferedImage labelled = new BufferedImage(width * scale, height * scale + 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = labelled.createGraphics();
            graphics.setColor(new Color(0x111111));
            graphics.fillRect(0, 0, labelled.getWidth(), labelled.getHeight());
            graphics.drawImage(canvas.image(), 0, 0, null);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            graphics.setColor(new Color(0x777777));
            graphics.drawString("HEADLESS UI PREVIEW  /  illustrative toggle states  /  not an in-game screenshot", 28, height * scale + 24);
            graphics.dispose();
            Files.createDirectories(output.toAbsolutePath().getParent());
            ImageIO.write(labelled, "png", output.toFile());
        }
        System.out.println("Preview written to " + output.toAbsolutePath());
    }
}
