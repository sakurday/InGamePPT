package dev.ingameppt.screen;

import dev.ingameppt.image.MapColors;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/**
 * Draws one 128x128 tile of the slide onto a map.
 *
 * <p>The image is written once and then kept in the map's stored pixel data, so it survives
 * reconnects and is visible to every player who looks at the frame - including players who
 * were not online when the slide was painted.
 */
public final class MapTileRenderer extends MapRenderer {

    private final int[][] ids;
    private boolean painted;

    public MapTileRenderer(int[][] ids) {
        this.ids = ids;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (painted) {
            return;
        }
        for (int y = 0; y < ids.length; y++) {
            int[] row = ids[y];
            for (int x = 0; x < row.length; x++) {
                canvas.setPixelColor(x, y, new Color(MapColors.RGB[row[x]]));
            }
        }
        painted = true;
    }
}

