package dev.ingameppt.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A deliberately harsh test slide: white background, a blue title bar, small black body
 * text and a saturated chart. If a slide like this survives the map pipeline then a real
 * deck will.
 */
public final class TestSlide {

    private TestSlide() {
    }

    public static BufferedImage render(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(0x1F4E79));
        g.fillRect(0, 0, width, height / 6);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, height / 12));
        g.drawString("InGamePPT 可读性测试", width / 30, height / 11);

        g.setColor(new Color(0x222222));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, height / 26));
        int line = height / 4;
        int step = height / 18;
        String[] bullets = {
                "• 正文最小字号约等于屏高的 4%（接近真实 PPT 的 18pt 正文）",
                "• 细横线、渐变与照片是地图色板最容易崩掉的三类内容",
                "• 抖动开启后照片还能看，纯色块与文字几乎没有损失",
                "• 结论：标题和要点可以照常做，正文与图表必须放大重画"
        };
        for (String bullet : bullets) {
            g.drawString(bullet, width / 20, line);
            line += step;
        }

        // A saturated bar chart in the lower third: the classic worst case for map colours.
        int chartTop = height * 11 / 18;
        int chartBottom = height * 9 / 10;
        int barWidth = width / 26;
        int gap = width / 90;
        Color[] colors = {
                new Color(0xE64A19), new Color(0x8BC34A), new Color(0x2196F3),
                new Color(0xFFC107), new Color(0x9C27B0), new Color(0x00897B)
        };
        int[] heights = {85, 55, 100, 40, 70, 62};
        for (int i = 0; i < colors.length; i++) {
            int barHeight = (chartBottom - chartTop) * heights[i] / 100;
            g.setColor(colors[i]);
            g.fillRect(width / 16 + i * (barWidth + gap), chartBottom - barHeight, barWidth, barHeight);
        }
        g.setColor(new Color(0x555555));
        g.setStroke(new BasicStroke(Math.max(1f, height / 400f)));
        g.drawLine(width / 16 - gap, chartBottom, width / 16 + colors.length * (barWidth + gap), chartBottom);

        // A gradient strip: shows how badly the palette bands without dithering.
        for (int x = 0; x < width / 3; x++) {
            int shade = x * 255 / (width / 3);
            g.setColor(new Color(shade, shade, shade));
            g.fillRect(width * 2 / 3 + x * (width / 3) / (width / 3), chartTop, 1, chartBottom - chartTop);
        }

        g.dispose();
        return image;
    }
}

