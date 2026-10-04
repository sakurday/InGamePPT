package dev.ingameppt.deck;

import dev.ingameppt.screen.FrameGrid;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's playback state: which screen, which deck, which page, and the map items
 * already built for the pages they have visited.
 *
 * <p>Maps are built lazily and then cached, so going back to a page that has already been
 * shown is instant and does not consume any new map ids.
 */
public final class PlayerSession {

    private final FrameGrid grid;
    private final SlideDeck deck;
    private final Map<Integer, List<ItemStack>> pageItems = new HashMap<>();
    private final Map<Integer, List<MapView>> pageViews = new HashMap<>();

    private int index;
    private boolean busy;

    public PlayerSession(FrameGrid grid, SlideDeck deck) {
        this.grid = grid;
        this.deck = deck;
    }

    public FrameGrid grid() {
        return grid;
    }

    public SlideDeck deck() {
        return deck;
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean busy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public boolean isCached(int index) {
        return pageItems.containsKey(index);
    }

    public void cache(int index, List<ItemStack> items, List<MapView> views) {
        pageItems.put(index, items);
        pageViews.put(index, views);
    }

    public List<ItemStack> items(int index) {
        return pageItems.get(index);
    }

    public List<MapView> views(int index) {
        return pageViews.get(index);
    }
}
