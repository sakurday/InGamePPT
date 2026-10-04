package dev.ingameppt.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns an arbitrary image into the map colour ids that a Minecraft map can actually show.
 *
 * <p>This class deliberately depends on nothing but the JDK, so it can be exercised
 * offline (see {@link PreviewMain}) with exactly the same code the plugin runs in game.
 */
public final class ImagePipeline {

    /** One Minecraft map is 128x128 pixels. */
    public static final int TILE = 128;

    private ImagePipeline() {
    }

    // ------------------------------------------------------------------ loading

    public static BufferedImage load(String source, Path imageFolder) throws IOException {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            try (InputStream in = URI.create(source).toURL().openStream()) {
                return read(in, source);
            }
        }

        // An item frame listing already walks a folder, so the path it hands us is usually
        // relative to the server's working directory. Resolving that against the images
        // folder a second time used to produce "ppt/ppt/1.png" style paths, so only fall
        // back to the images folder when the path as given does not exist.
        Path given = Path.of(source);
        Path path = given;
        if (!given.isAbsolute() && !Files.isRegularFile(given)) {
            path = imageFolder.resolve(source);
        }
        try (InputStream in = Files.newInputStream(path)) {
            return read(in, path.toString());
        }
    }

    public static BufferedImage read(InputStream in, String what) throws IOException {
        BufferedImage image = javax.imageio.ImageIO.read(in);
        if (image == null) {
            throw new IOException("Not a readable image (PNG/JPG/GIF/BMP expected): " + what);
        }
        return image;
    }

    // ------------------------------------------------------------------ fitting

    /**
     * Scales {@code source} to touch the edges of a {@code width x height} canvas while
     * keeping its aspect ratio, and fills the leftover area with opaque black.
     */
    public static BufferedImage containFit(BufferedImage source, int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        double scale = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        Image scaled = source.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(scaled, x, y, null);
        g.dispose();
        return canvas;
    }

    // ------------------------------------------------------------------ quantising

    /** Converts every pixel straight to the closest palette id, without error diffusion. */
    public static int[][] toMapIds(BufferedImage image) {
        return toMapIds(image, false);
    }

    /**
     * Converts an image into palette ids, optionally with Floyd-Steinberg error diffusion.
     *
     * <p>Dithering is what makes photographs and gradients readable on a map wall; without
     * it the 248 colours band badly.
     */
    public static int[][] toMapIds(BufferedImage image, boolean dither) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] ids = new int[height][width];

        double[][] r = new double[height][width];
        double[][] g = new double[height][width];
        double[][] b = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                r[y][x] = rgb >> 16 & 0xFF;
                g[y][x] = rgb >> 8 & 0xFF;
                b[y][x] = rgb & 0xFF;
            }
        }

        // Serpentine order: error diffusion that always runs left-to-right smears hard
        // vertical edges into a diagonal ramp, which is very visible on UI-style slides.
        for (int y = 0; y < height; y++) {
            boolean leftToRight = (y & 1) == 0;
            for (int i = 0; i < width; i++) {
                int x = leftToRight ? i : width - 1 - i;
                int direction = leftToRight ? 1 : -1;

                int pr = clamp(r[y][x]);
                int pg = clamp(g[y][x]);
                int pb = clamp(b[y][x]);

                int id = MapColors.nearest(pr, pg, pb);
                ids[y][x] = id;

                if (!dither) {
                    continue;
                }

                // The residual must come from the clamped colour that was actually
                // matched. Using the raw accumulator lets it run away on saturated
                // colours and floods neighbouring flat areas with the wrong hue.
                int chosen = MapColors.RGB[id];
                double er = pr - (chosen >> 16 & 0xFF);
                double eg = pg - (chosen >> 8 & 0xFF);
                double eb = pb - (chosen & 0xFF);

                spread(r, g, b, x + direction, y, er, eg, eb, 7 / 16.0);
                spread(r, g, b, x - direction, y + 1, er, eg, eb, 3 / 16.0);
                spread(r, g, b, x, y + 1, er, eg, eb, 5 / 16.0);
                spread(r, g, b, x + direction, y + 1, er, eg, eb, 1 / 16.0);
            }
        }
        return ids;
    }

    private static void spread(double[][] r, double[][] g, double[][] b,
                               int x, int y, double er, double eg, double eb, double factor) {
        if (y < 0 || y >= r.length || x < 0 || x >= r[0].length) {
            return;
        }
        r[y][x] = clampDouble(r[y][x] + er * factor);
        g[y][x] = clampDouble(g[y][x] + eg * factor);
        b[y][x] = clampDouble(b[y][x] + eb * factor);
    }

    private static double clampDouble(double value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    // ------------------------------------------------------------------ output

    /** Renders palette ids back to an image so the result can be eyeballed offline. */
    public static BufferedImage toImage(int[][] ids) {
        int height = ids.length;
        int width = ids[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, MapColors.RGB[ids[y][x]]);
            }
        }
        return image;
    }

    /** Cuts the poster into the single tile that belongs to map (col, row). */
    public static int[][] tile(int[][] ids, int col, int row) {
        int[][] out = new int[TILE][TILE];
        for (int y = 0; y < TILE; y++) {
            System.arraycopy(ids[row * TILE + y], col * TILE, out[y], 0, TILE);
        }
        return out;
    }

    /** Nearest-neighbour upscale of palette ids, for an offline preview. */
    public static BufferedImage upscale(int[][] ids, int scale, boolean grid) {
        int height = ids.length;
        int width = ids[0].length;
        BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                g.setColor(new Color(MapColors.RGB[ids[y][x]]));
                g.fillRect(x * scale, y * scale, scale, scale);
            }
        }
        if (grid) {
            g.setColor(new Color(255, 255, 255, 90));
            for (int x = TILE; x < width; x += TILE) {
                g.fillRect(x * scale, 0, 1, height * scale);
            }
            for (int y = TILE; y < height; y += TILE) {
                g.fillRect(0, y * scale, width * scale, 1);
            }
        }
        g.dispose();
        return image;
    }
}
