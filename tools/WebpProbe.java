import dev.ingameppt.image.ImagePipeline;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Answers "can we read webp slides?" by decoding a real .webp file and pushing it through
 * the very same pipeline a slide goes through in game.
 *
 * <pre>
 * javac -encoding UTF-8 -cp "out/plugin;.tools/libs/*" -d out/probe tools/WebpProbe.java
 * java -cp "out/plugin;out/probe;.tools/libs/*" WebpProbe .research/sample.webp
 * </pre>
 *
 * Run it once without the TwelveMonkeys jars on the classpath to see the difference.
 */
public final class WebpProbe {

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args.length > 0 ? args[0] : ".research/sample.webp").toAbsolutePath();

        List<String> formats = Arrays.asList(ImageIO.getReaderFormatNames());
        System.out.println("ImageIO 可读格式数: " + formats.size());
        System.out.println("  含 webp: " + formats.stream().anyMatch(f -> f.equalsIgnoreCase("webp")));
        System.out.println("  全部: " + formats);

        long start = System.nanoTime();
        BufferedImage image = ImagePipeline.load(source.toString(), source.getParent());
        long decodeMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("解码成功: " + image.getWidth() + "x" + image.getHeight() + ", 用时 " + decodeMillis + " ms");

        start = System.nanoTime();
        int[][] ids = ImagePipeline.toMapIds(ImagePipeline.containFit(image, 7 * 128, 4 * 128), true);
        long mapMillis = (System.nanoTime() - start) / 1_000_000;

        Path out = Path.of("preview-out/webp-check.png").toAbsolutePath();
        Files.createDirectories(out.getParent());
        ImageIO.write(ImagePipeline.upscale(ids, 2, false), "png", out.toFile());
        System.out.println("进入地图管线: 896x512 量化用时 " + mapMillis + " ms");
        System.out.println("预览输出: " + out);

        System.out.println("能否写 webp: " + Arrays.asList(ImageIO.getWriterFormatNames())
                .stream().anyMatch(f -> f.equalsIgnoreCase("webp")));
    }
}
