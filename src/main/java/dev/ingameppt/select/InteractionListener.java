package dev.ingameppt.select;

import dev.ingameppt.InGamePptPlugin;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * All the "hit something with a wand" interactions live here.
 *
 * <ul>
 *   <li>golden axe + {@code /ppt select}: left click a frame to mark a corner
 *   <li>diamond axe while a deck is playing: left click = previous page, right click = next
 * </ul>
 *
 * <p>Which event the server fires for a punched item frame has changed between versions, so
 * both the hanging-break and the damage event are handled; whichever arrives first wins.
 */
public final class InteractionListener implements Listener {

    private final InGamePptPlugin plugin;

    public InteractionListener(InGamePptPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !(event.getRemover() instanceof Player player)) {
            return;
        }
        if (handleAttack(player, frame)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        if (handleAttack(player, frame)) {
            event.setCancelled(true);
        }
    }

    /** Right clicking a frame. Also fires for the positional subclass used by frames. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (handleUse(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Right clicking into the air while presenting.
     *
     * <p>Deliberately limited to air: intercepting right clicks on blocks would stop the
     * presenter from opening doors, pulling levers or stripping logs while holding the axe.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (handleUse(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** @return true when the hit was ours and the vanilla behaviour should be suppressed */
    private boolean handleAttack(Player player, ItemFrame frame) {
        if (plugin.isHolding(player, plugin.wand()) && plugin.selections().isSelecting(player)) {
            plugin.onCornerMarked(player, frame);
            return true;
        }
        if (plugin.isHolding(player, plugin.pagingWand()) && plugin.hasSession(player)) {
            plugin.turnPage(player, -1);
            return true;
        }
        return false;
    }

    /** @return true when the click was ours and the vanilla behaviour should be suppressed */
    private boolean handleUse(Player player) {
        if (plugin.isHolding(player, plugin.pagingWand()) && plugin.hasSession(player)) {
            plugin.turnPage(player, 1);
            return true;
        }
        return false;
    }
}
