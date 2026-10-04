package dev.ingameppt.image;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline preview of what a slide looks like on a wall of maps.
 *
 * <pre>
 * java dev.ingameppt.image.PreviewMain test 7 4 dither preview-out
 * java dev.ingameppt.image.PreviewMain deck.png 7 4 dither preview-out
 * </pre>
 *
 * It runs the exact same fitting, quantising and tiling code the plugin uses in game, so
 * the result is not a mock-up.
 */
public final class PreviewMain {

    public static void main(String[] args) throws Exception {
        String source = args.length > 0 ? args[0] : "test";
        int cols = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        int rows = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        boolean dither = args.length > 3 && args[3].equalsIgnoreCase("dither");
        Path outDir = Path.of(args.length > 4 ? args[4] : "preview-out");
        int scale = args.length > 5 ? Integer.parseInt(args[5]) : 4;

        Files.createDirectories(outDir);

        BufferedImage sourceImage = source.equalsIgnoreCase("test")
                ? TestSlide.render(cols * ImagePipeline.TILE * 2, rows * ImagePipeline.TILE * 2)
                : ImagePipeline.load(source, Path.of("."));

        int width = cols * ImagePipeline.TILE;
        int height = rows * ImagePipeline.TILE;
        System.out.printf("source  : %dx%d%n", sourceImage.getWidth(), sourceImage.getHeight());
        System.out.printf("screen  : %dx%d (%d x %d maps)%n", width, height, cols, rows);
        System.out.printf("dither  : %s%n", dither);

        long start = System.nanoTime();
        BufferedImage fitted = ImagePipeline.containFit(sourceImage, width, height);
        int[][] ids = ImagePipeline.toMapIds(fitted, dither);
        long millis = (System.nanoTime() - start) / 1_000_000;

        BufferedImage flat = ImagePipeline.toImage(ids);
        BufferedImage flatPreview = ImagePipeline.upscale(ids, scale, false);
        BufferedImage gridPreview = ImagePipeline.upscale(ids, scale, true);

        Path flatPath = outDir.resolve("preview-1x.png");
        Path previewPath = outDir.resolve("preview-" + scale + "x.png");
        Path gridPath = outDir.resolve("preview-" + scale + "x-grid.png");
        javax.imageio.ImageIO.write(flat, "png", flatPath.toFile());
        javax.imageio.ImageIO.write(flatPreview, "png", previewPath.toFile());
        javax.imageio.ImageIO.write(gridPreview, "png", gridPath.toFile());

        System.out.printf("quantise: %d ms%n", millis);
        System.out.println("wrote   : " + flatPath.toAbsolutePath());
        System.out.println("wrote   : " + previewPath.toAbsolutePath());
        System.out.println("wrote   : " + gridPath.toAbsolutePath());
    }
}
