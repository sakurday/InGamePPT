import dev.ingameppt.deck.SlideDeck;
import dev.ingameppt.image.ImagePipeline;

import java.nio.file.Path;

/**
 * Reproduces the "path got joined twice" bug against a real server layout, without
 * starting a server.
 *
 * <pre>
 * javac -encoding UTF-8 -cp out/plugin -d out/probe tools/PathProbe.java
 * java -cp "out/plugin;out/probe" PathProbe paper_server/plugins/InGamePPT
 * </pre>
 */
public final class PathProbe {

    public static void main(String[] args) throws Exception {
        Path data = Path.of(args.length > 0 ? args[0] : "paper_server/plugins/InGamePPT");
        Path slides = data.resolve("ppt");
        Path images = data.resolve("images");

        System.out.println("data   = " + data);
        System.out.println("slides = " + slides + "  exists=" + java.nio.file.Files.isDirectory(slides));

        SlideDeck deck = SlideDeck.scan(slides);
        System.out.println("识别到 " + deck.size() + " 页, 第一页文件 = " + deck.slide(0).file());

        // Exactly what the plugin does when it shows a slide.
        String first = deck.slide(0).file().toString();
        var image = ImagePipeline.load(first, slides);
        System.out.println("加载第一页成功: " + image.getWidth() + "x" + image.getHeight());

        // A plain file name still resolves against the images folder.
        Path named = images.resolve("slide1.png");
        if (java.nio.file.Files.isRegularFile(named)) {
            var fromImages = ImagePipeline.load("slide1.png", images);
            System.out.println("按文件名从 images 加载成功: " + fromImages.getWidth() + "x" + fromImages.getHeight());
        } else {
            System.out.println("images/slide1.png 不存在, 跳过按文件名加载的检查");
        }

        // And an explicit absolute path keeps working.
        var absolute = ImagePipeline.load(deck.slide(0).file().toAbsolutePath().toString(), images);
        System.out.println("按绝对路径加载成功: " + absolute.getWidth() + "x" + absolute.getHeight());
    }
}
