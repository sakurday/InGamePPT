import dev.ingameppt.deck.SlideDeck;
import dev.ingameppt.image.ImagePipeline;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Turns a folder of numbered slide images into a resource pack and a data pack.
 *
 * <pre>
 * javac -encoding UTF-8 -cp out/plugin -d out/probe tools/PackBuilder.java
 * java -cp "out/plugin;out/probe" PackBuilder &lt;slides&gt; &lt;out&gt; &lt;textureWidth&gt; &lt;screenW&gt; &lt;screenH&gt;
 * </pre>
 *
 * Unlike the map renderer, this path keeps full colour: the image becomes a plain texture
 * that a custom item model draws on a quad, so there is no palette and no dithering.
 */
public final class PackBuilder {

    /** Taken from version.json inside the 26.1.2 server jar. */
    private static final int RESOURCE_PACK_FORMAT = 84;
    private static final int DATA_PACK_FORMAT = 101;

    private static final String NAMESPACE = "ppt";
    private static final String PACK_NAME = "InGamePPT";

    public static void main(String[] args) throws Exception {
        Path slidesFolder = Path.of(args.length > 0 ? args[0] : "paper_server/plugins/InGamePPT/ppt");
        Path outRoot = Path.of(args.length > 1 ? args[1] : "dist");
        int textureWidth = args.length > 2 ? Integer.parseInt(args[2]) : 640;
        double screenWidth = args.length > 3 ? Double.parseDouble(args[3]) : 7;
        double screenHeight = args.length > 4 ? Double.parseDouble(args[4]) : 4;

        SlideDeck deck = SlideDeck.scan(slidesFolder);
        if (deck.size() == 0) {
            throw new IllegalStateException("在 " + slidesFolder + " 里没有找到幻灯片(文件名要是 1.png、2.png ...)");
        }

        // The texture keeps the screen's aspect ratio and the slide is letterboxed inside it,
        // exactly like the map renderer did, so scaling the quad cannot stretch the image.
        int textureHeight = (int) Math.round(textureWidth * screenHeight / screenWidth);
        textureHeight += textureHeight % 2;

        Path resourcePack = outRoot.resolve(PACK_NAME + "-ResourcePack");
        Path dataPack = outRoot.resolve(PACK_NAME + "-Datapack");
        deleteRecursively(resourcePack);
        deleteRecursively(dataPack);

        System.out.printf("幻灯片   : %d 页 (%s)%n", deck.size(), slidesFolder);
        System.out.printf("贴图尺寸 : %dx%d  (屏幕 %.0fx%.0f 格)%n", textureWidth, textureHeight, screenWidth, screenHeight);

        writeResourcePack(resourcePack, deck, slidesFolder, textureWidth, textureHeight);
        writeDataPack(dataPack, deck, screenWidth, screenHeight);

        Path resourceZip = outRoot.resolve(PACK_NAME + "-ResourcePack.zip");
        Path dataZip = outRoot.resolve(PACK_NAME + "-Datapack.zip");
        zip(resourcePack, resourceZip);
        zip(dataPack, dataZip);

        report(deck, resourcePack, dataPack, resourceZip, dataZip, textureWidth, textureHeight);
    }

    // ---------------------------------------------------------------- resource pack

    private static void writeResourcePack(Path root, SlideDeck deck, Path slidesFolder,
                                          int textureWidth, int textureHeight) throws IOException {
        write(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s 幻灯片"
                  }
                }
                """.formatted(RESOURCE_PACK_FORMAT, PACK_NAME));

        for (int index = 0; index < deck.size(); index++) {
            SlideDeck.Slide slide = deck.slide(index);
            int page = index + 1;

            BufferedImage source = ImagePipeline.load(slide.file().toString(), slidesFolder);
            BufferedImage fitted = ImagePipeline.containFit(source, textureWidth, textureHeight);
            Path texture = root.resolve("assets/" + NAMESPACE + "/textures/page/slide_" + page + ".png");
            Files.createDirectories(texture.getParent());
            ImageIO.write(fitted, "png", texture.toFile());

            write(root.resolve("assets/" + NAMESPACE + "/items/page_" + page + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "%s:page_%d"
                      }
                    }
                    """.formatted(NAMESPACE, page));

            // Centred on the model origin so the display entity's position is the middle of
            // the screen, which makes placement predictable. Both faces carry the texture so
            // the screen is visible from either side; rotate the entity 180 degrees if the
            // side you look at shows mirrored text.
            write(root.resolve("assets/" + NAMESPACE + "/models/page_" + page + ".json"), """
                    {
                      "textures": {
                        "0": "%s:page/slide_%d"
                      },
                      "elements": [
                        {
                          "from": [ -8, -8, 7.5 ],
                          "to": [ 8, 8, 8.5 ],
                          "faces": {
                            "north": { "uv": [ 0, 0, 16, 16 ], "texture": "#0" },
                            "south": { "uv": [ 16, 16, 0, 0 ], "texture": "#0" }
                          }
                        }
                      ]
                    }
                    """.formatted(NAMESPACE, page));
        }
    }

    // ---------------------------------------------------------------- data pack

    private static void writeDataPack(Path root, SlideDeck deck,
                                      double screenWidth, double screenHeight) throws IOException {
        int pages = deck.size();
        String functionDir = "data/" + NAMESPACE + "/function/";

        write(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s 幻灯片控制"
                  }
                }
                """.formatted(DATA_PACK_FORMAT, PACK_NAME));

        write(root.resolve("data/minecraft/tags/function/load.json"), """
                {
                  "values": [ "%s:init" ]
                }
                """.formatted(NAMESPACE));

        write(root.resolve("data/minecraft/tags/function/tick.json"), """
                {
                  "values": [ "%s:tick" ]
                }
                """.formatted(NAMESPACE));

        write(root.resolve(functionDir + "init.mcfunction"), """
                # 只在第一次载入时创建记分板,重复 /reload 时这几行会报 "already exists",无害
                scoreboard objectives add ppt.page dummy
                scoreboard objectives add ppt_next trigger
                scoreboard objectives add ppt_prev trigger
                scoreboard players set #page ppt.page 1
                """);

        // Players may not be opped, so paging is also exposed through trigger objectives.
        // Trigger objectives disable themselves after use, hence the re-enable every tick.
        write(root.resolve(functionDir + "tick.mcfunction"), """
                scoreboard players enable @a ppt_next
                scoreboard players enable @a ppt_prev
                execute as @a[scores={ppt_next=1..}] run function %s:next
                scoreboard players reset @a[scores={ppt_next=1..}] ppt_next
                execute as @a[scores={ppt_prev=1..}] run function %s:prev
                scoreboard players reset @a[scores={ppt_prev=1..}] ppt_prev
                """.formatted(NAMESPACE, NAMESPACE));

        write(root.resolve(functionDir + "build.mcfunction"), """
                # 讲者站到屏幕中心的位置,朝墙面执行: function %s:build
                # 屏幕出现在讲者视线前方 4 格处,并转过来面对讲者
                execute anchored eyes positioned ^ ^ ^4 rotated ~ 180 run summon minecraft:item_display ~ ~ ~ \
                {Tags:["ppt_screen"],width:%sf,height:%sf,view_range:2.0f,brightness:{sky:15,block:15},item_display:"none",\
                item:{id:"minecraft:paper",count:1,components:{"minecraft:item_model":"%s:page_1"}},\
                transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[%sf,%sf,1f],right_rotation:[0f,0f,0f,1f]}}
                scoreboard players set #page ppt.page 1
                function %s:refresh
                """.formatted(NAMESPACE, number(screenWidth), number(screenHeight),
                NAMESPACE, number(screenWidth), number(screenHeight), NAMESPACE));

        write(root.resolve(functionDir + "clear.mcfunction"), """
                kill @e[type=minecraft:item_display, tag=ppt_screen]
                """);

        write(root.resolve(functionDir + "next.mcfunction"), """
                execute if score #page ppt.page matches %d.. if entity @s[type=minecraft:player] run tellraw @s \
                {"text":"已经是最后一页了 (%d/%d)","color":"yellow"}
                execute unless score #page ppt.page matches %d.. run scoreboard players add #page ppt.page 1
                function %s:refresh
                """.formatted(pages + 1, pages, pages, pages + 1, NAMESPACE));

        write(root.resolve(functionDir + "prev.mcfunction"), """
                execute if score #page ppt.page matches ..1 if entity @s[type=minecraft:player] run tellraw @s \
                {"text":"已经是第一页了 (1/%d)","color":"yellow"}
                execute unless score #page ppt.page matches ..1 run scoreboard players remove #page ppt.page 1
                function %s:refresh
                """.formatted(pages, NAMESPACE));

        StringBuilder refresh = new StringBuilder("# 按当前页码刷新屏幕\n");
        for (int page = 1; page <= pages; page++) {
            refresh.append("execute if score #page ppt.page matches ").append(page)
                    .append(" run function ").append(NAMESPACE).append(":show_").append(page).append('\n');
        }
        write(root.resolve(functionDir + "refresh.mcfunction"), refresh.toString());

        for (int page = 1; page <= pages; page++) {
            String fileName = deck.slide(page - 1).fileName();
            write(root.resolve(functionDir + "show_" + page + ".mcfunction"), """
                    data merge entity @e[tag=ppt_screen,limit=1] \
                    {item:{id:"minecraft:paper",count:1,components:{"minecraft:item_model":"%s:page_%d"}}}
                    execute if entity @s[type=minecraft:player] run title @s actionbar \
                    {"text":"第 %d/%d 页  %s","color":"aqua"}
                    """.formatted(NAMESPACE, page, page, pages, fileName));
        }
    }

    // ---------------------------------------------------------------- reporting

    private static void report(SlideDeck deck, Path resourcePack, Path dataPack,
                               Path resourceZip, Path dataZip,
                               int textureWidth, int textureHeight) throws Exception {
        long totalPixels = (long) deck.size() * textureWidth * textureHeight;
        System.out.printf("贴图总像素: %d (%.1f M)%n", totalPixels, totalPixels / 1_000_000.0);
        System.out.println("  图集需要有约 25% 的打包余量,加上 mipmap 再乘 1.33:");
        for (int size : new int[]{2048, 4096, 8192, 16384}) {
            long capacity = (long) size * size;
            boolean fits = capacity * 0.75 >= totalPixels * 1.33;
            System.out.printf("    %5d x %-5d = %5.1f M 像素  %s%n", size, size, capacity / 1_000_000.0,
                    fits ? "可以" : "装不下");
        }

        System.out.printf("%n资源包: %s%n", resourcePack);
        System.out.printf("  assets/%s/items/       %d 个物品模型定义%n", NAMESPACE, deck.size());
        System.out.printf("  assets/%s/models/      %d 个平面模型%n", NAMESPACE, deck.size());
        System.out.printf("  assets/%s/textures/    %d 张贴图%n", NAMESPACE, deck.size());
        System.out.printf("  版本 %d, zip %.1f MB%n", RESOURCE_PACK_FORMAT, Files.size(resourceZip) / 1048576.0);

        System.out.printf("%n数据包: %s%n", dataPack);
        System.out.printf("  版本 %d, zip %.2f MB, %d 个函数文件%n", DATA_PACK_FORMAT,
                Files.size(dataZip) / 1048576.0, countFiles(dataPack.resolve("data")));

        System.out.printf("%n=== 把下面三行填进 server.properties ===%n");
        System.out.printf("resource-pack=<把 %s 放到 HTTP 服务上后填它的 URL>%n", resourceZip.getFileName());
        System.out.printf("resource-pack-sha1=%s%n", sha1(resourceZip));
        System.out.println("require-resource-pack=true");

        System.out.printf("%n=== 数据包放到 %s/datapacks/ ===%n", "<世界目录>");
        System.out.printf("  然后进游戏执行: /reload,再执行 /function %s:build%n", NAMESPACE);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ---------------------------------------------------------------- helpers

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static int countFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return (int) walk.filter(Files::isRegularFile).count();
        }
    }

    private static void zip(Path sourceDir, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (Path file : files) {
                String name = sourceDir.relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
    }

    private static String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
