import dev.ingameppt.deck.SlideDeck;
import dev.ingameppt.image.ImagePipeline;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures what one page turn actually costs, using the real deck on disk.
 *
 * <pre>
 * javac -encoding UTF-8 -cp out/plugin -d out/probe tools/PipelineBench.java
 * java -cp "out/plugin;out/probe" PipelineBench paper_server/plugins/InGamePPT/ppt 7 4
 * </pre>
 *
 * This covers the part that runs off the server thread: decoding, fitting and quantising.
 * The part that runs on the main thread (creating maps, writing pixels, sending packets)
 * is measured analytically from these numbers plus the known packet sizes.
 */
public final class PipelineBench {

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args.length > 0 ? args[0] : "paper_server/plugins/InGamePPT/ppt");
        int cols = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        int rows = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int width = cols * ImagePipeline.TILE;
        int height = rows * ImagePipeline.TILE;

        SlideDeck deck = SlideDeck.scan(folder);
        System.out.println("幻灯片: " + deck.size() + " 页, 屏幕 " + cols + "x" + rows
                + " = " + (cols * rows) + " 张地图/页, " + width + "x" + height + " px");

        List<Long> decode = new ArrayList<>();
        List<Long> quantise = new ArrayList<>();
        List<Long> ratios = new ArrayList<>();
        long totalStart = System.nanoTime();
        int maxPages = Math.min(deck.size(), 30);

        for (int i = 0; i < maxPages; i++) {
            Path file = deck.slide(i).file();
            long t0 = System.nanoTime();
            BufferedImage image = ImagePipeline.load(file.toString(), folder);
            long t1 = System.nanoTime();
            BufferedImage fitted = ImagePipeline.containFit(image, width, height);
            int[][] ids = ImagePipeline.toMapIds(fitted, true);
            long t2 = System.nanoTime();

            decode.add((t1 - t0) / 1_000_000);
            quantise.add((t2 - t1) / 1_000_000);

            if (ids.length != height || ids[0].length != width) {
                throw new IllegalStateException("尺寸不对");
            }

            byte[] probeTile = new byte[ImagePipeline.TILE * ImagePipeline.TILE];
            for (int y = 0; y < ImagePipeline.TILE; y++) {
                for (int x = 0; x < ImagePipeline.TILE; x++) {
                    probeTile[y * ImagePipeline.TILE + x] = (byte) ids[y][x];
                }
            }
            ratios.add(100L * deflate(probeTile) / probeTile.length);
        }
        long totalMillis = (System.nanoTime() - totalStart) / 1_000_000;

        System.out.printf("已测 %d 页, 总计 %d ms%n", maxPages, totalMillis);
        System.out.printf("  解码     平均 %d ms, 最大 %d ms%n", avg(decode), max(decode));
        System.out.printf("  适配+量化 平均 %d ms, 最大 %d ms%n", avg(quantise), max(quantise));
        System.out.printf("  单页合计 平均 %d ms%n", avg(decode) + avg(quantise));
        System.out.printf("  按全部 %d 页推算: 约 %.1f 秒 CPU 时间(一次性预渲染)%n",
                deck.size(), deck.size() * (avg(decode) + avg(quantise)) / 1000.0);

        // Retained memory of a quantised page, if a deck were cached in full.
        int[][] one = ImagePipeline.toMapIds(
                ImagePipeline.containFit(ImagePipeline.load(deck.slide(0).file().toString(), folder), width, height), true);
        long pageBytes = 0;
        for (int[] row : one) {
            pageBytes += 16 + (long) row.length * 4;
        }
        System.out.printf("%n一页的量化结果: %.1f MB heap (%s)%n", pageBytes / 1048576.0,
                fn(pageBytes));
        System.out.printf("  若把全部 %d 页都缓存在内存: %.0f MB  <- 所以不能缓存 int[][], 只缓存地图物品%n",
                deck.size(), pageBytes * deck.size() / 1048576.0);

        // Map data the server has to keep: 16384 bytes of colours per map.
        long mapsShown = (long) deck.size() * cols * rows;
        System.out.printf("%n地图数据(服务端常驻): %d 页 x %d = %d 张地图%n", deck.size(), cols * rows, mapsShown);
        System.out.printf("  颜色数组本身就约 %.0f MB, 加上对象与存储开销大致 %.0f-%.0f MB%n",
                mapsShown * 16384 / 1048576.0, mapsShown * 16384 / 1048576.0 * 1.5,
                mapsShown * 16384 / 1048576.0 * 2.5);

        // Network per page turn per viewer: one map packet each, 16384 bytes raw.
        // A map packet carries 16384 palette bytes. The server deflates anything above
        // network-compression-threshold, so measure what one tile really costs on the wire.
        byte[] tile = new byte[ImagePipeline.TILE * ImagePipeline.TILE];
        for (int y = 0; y < ImagePipeline.TILE; y++) {
            for (int x = 0; x < ImagePipeline.TILE; x++) {
                tile[y * ImagePipeline.TILE + x] = (byte) one[y][x];
            }
        }
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(6);
        deflater.setInput(tile);
        deflater.finish();
        byte[] buffer = new byte[1 << 18];
        int compressed = deflater.deflate(buffer);
        deflater.end();

        double rawTile = tile.length / 1024.0;
        double deflatedTile = compressed / 1024.0;
        double rawPerPage = rawTile * cols * rows / 1024.0;
        double deflatedPerPage = deflatedTile * cols * rows / 1024.0;
        System.out.printf("%n网络: 单张地图 %d 字节, 压缩后 %d 字节 (%.0f%%)%n",
                tile.length, compressed, 100.0 * compressed / tile.length);
        System.out.printf("  全部 %d 页的压缩率: 最小 %d%%, 平均 %d%%, 最大 %d%%%n",
                ratios.size(), min(ratios), avg(ratios), max(ratios));
        System.out.printf("  每次翻页每位观众: %.2f MB 原始 -> %.2f MB 实际传输%n", rawPerPage, deflatedPerPage);
        System.out.printf("  20 位观众(你的 max-players): %.1f MB / 次翻页%n", deflatedPerPage * 20);
        System.out.printf("  30 位观众: %.1f MB / 次翻页%n", deflatedPerPage * 30);
        System.out.printf("  按每 15 秒翻一页, 30 人: %.0f KB/s 上行%n", deflatedPerPage * 30 * 1024 / 15);

        System.gc();
        Runtime runtime = Runtime.getRuntime();
        System.out.printf("%nJVM 当前堆: 已用 %.0f MB / 上限 %.0f MB%n",
                (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0,
                runtime.maxMemory() / 1048576.0);
    }

    private static long avg(List<Long> values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }

    private static long max(List<Long> values) {
        long best = 0;
        for (long v : values) {
            best = Math.max(best, v);
        }
        return best;
    }

    private static long min(List<Long> values) {
        long best = Long.MAX_VALUE;
        for (long v : values) {
            best = Math.min(best, v);
        }
        return values.isEmpty() ? 0 : best;
    }

    private static int deflate(byte[] data) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(6);
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[1 << 18];
        int written = deflater.deflate(buffer);
        deflater.end();
        return written;
    }

    private static String fn(long bytes) {
        return bytes / 1024 + " KB";
    }
}
