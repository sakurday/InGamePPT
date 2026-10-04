package dev.ingameppt.screen;

import dev.ingameppt.image.ImagePipeline;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;

/** Turns a quantised poster into map items, and pushes those items into a grid of frames. */
public final class ScreenPainter {

    /**
     * One page worth of maps. {@code items} and {@code views} are both in row-major order
     * (row 0 left to right, then row 1, ...), which is the order the frames are filled in.
     */
    public record PaintedPage(List<ItemStack> items, List<MapView> views) {
    }

    private ScreenPainter() {
    }

    /**
     * Creates one map per tile. Must run on the main thread, but it does not touch any
     * frames, so a page can be prepared ahead of the moment it is shown.
     */
    public static PaintedPage build(FrameGrid grid, int[][] ids) {
        List<ItemStack> items = new ArrayList<>();
        List<MapView> views = new ArrayList<>();
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.cols(); col++) {
                MapView view = Bukkit.createMap(grid.world());
                view.getRenderers().forEach(view::removeRenderer);
                view.addRenderer(new MapTileRenderer(ImagePipeline.tile(ids, col, row)));

                ItemStack stack = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) stack.getItemMeta();
                meta.setMapView(view);
                stack.setItemMeta(meta);

                items.add(stack);
                views.add(view);
            }
        }
        return new PaintedPage(items, views);
    }

    /** Swaps the given page into the frames. Must run on the main thread. */
    public static void apply(FrameGrid grid, List<ItemStack> items) {
        int index = 0;
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.cols(); col++) {
                grid.frame(col, row).setItem(items.get(index++), false);
            }
        }
    }

    /** Empties the frames again, which is also how the old map ids get released. */
    public static void clear(FrameGrid grid) {
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.cols(); col++) {
                grid.frame(col, row).setItem(new ItemStack(Material.AIR), false);
            }
        }
    }
}
