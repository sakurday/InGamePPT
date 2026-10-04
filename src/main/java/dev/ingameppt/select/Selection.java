package dev.ingameppt.select;

import org.bukkit.entity.ItemFrame;

/** One corner the player has marked with the wand. */
public record Selection(ItemFrame frame, long tick) {
}

