package dev.ingameppt.select;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks who is currently marking corners, and which corner they marked first. */
public final class SelectionManager {

    private final Set<UUID> selecting = new HashSet<>();
    private final Map<UUID, Selection> firstCorner = new HashMap<>();

    public void begin(Player player) {
        selecting.add(player.getUniqueId());
        firstCorner.remove(player.getUniqueId());
    }

    public void stop(Player player) {
        selecting.remove(player.getUniqueId());
        firstCorner.remove(player.getUniqueId());
    }

    public boolean isSelecting(Player player) {
        return selecting.contains(player.getUniqueId());
    }

    public Selection firstCorner(Player player) {
        return firstCorner.get(player.getUniqueId());
    }

    public void setFirstCorner(Player player, Selection selection) {
        firstCorner.put(player.getUniqueId(), selection);
    }

    public void clearFirstCorner(Player player) {
        firstCorner.remove(player.getUniqueId());
    }
}

