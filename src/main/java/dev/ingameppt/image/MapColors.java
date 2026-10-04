package dev.ingameppt.image;

import java.util.HashMap;
import java.util.Map;

/**
 * The vanilla Minecraft map colour palette.
 *
 * <p>There are 62 base colours and every base colour exists in 4 shades, which gives
 * 248 map colour ids. Ids {@code 0..3} belong to the "no colour" base entry and render
 * as transparent pixels, so they are never used for slide content.
 *
 * <p>The numbers below are the same values the vanilla client ships with (and that
 * Bukkit's {@code MapPalette} mirrors), so a pixel written here looks identical in game.
 */
public final class MapColors {

    /** Index 0 is the transparent placeholder, the remaining 61 are real base colours. */
    private static final int[] BASE = {
            0x000000,
            0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7, 0x007C00, 0xFFFFFF,
            0xA4A8B8, 0x976D4D, 0x707070, 0x4040FF, 0x8F7748, 0xFFFCF5, 0xD87F33, 0xB24CD8,
            0x6699D8, 0xE5E533, 0x7FCC19, 0xF27FA5, 0x4C4C4C, 0x999999, 0x4C7F99, 0x7F3FB2,
            0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919, 0xFAEE4D, 0x5CDBD5, 0x4A80FF,
            0x00D93A, 0x815631, 0x700200, 0xD1B1A1, 0x9F5224, 0x95576C, 0x706C8A, 0xBA8524,
            0x677535, 0xA04D4E, 0x392923, 0x876B62, 0x575C5C, 0x7A4958, 0x4C3E5C, 0x4C3223,
            0x4C522A, 0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D, 0x167E86, 0x3A8E8C,
            0x562C3E, 0x14B485, 0x646464, 0xD8AF93, 0x7FA796
    };

    /** Shade multipliers applied to every base colour, in palette order. */
    private static final int[] SHADE = {180, 220, 255, 135};

    /** 248 entries, indexed by the map colour id used in game. */
    public static final int[] RGB = new int[BASE.length * SHADE.length];

    /** {@code true} for the four transparent ids. */
    public static final boolean[] TRANSPARENT = new boolean[RGB.length];

    private static final Map<Integer, Integer> CACHE = new HashMap<>(1 << 14);

    static {
        for (int base = 0; base < BASE.length; base++) {
            for (int shade = 0; shade < SHADE.length; shade++) {
                int id = base * SHADE.length + shade;
                TRANSPARENT[id] = base == 0;
                RGB[id] = multiply(BASE[base], SHADE[shade]);
            }
        }
    }

    private MapColors() {
    }

    private static int multiply(int rgb, int factor) {
        int r = (rgb >> 16 & 0xFF) * factor / 255;
        int g = (rgb >> 8 & 0xFF) * factor / 255;
        int b = (rgb & 0xFF) * factor / 255;
        return r << 16 | g << 8 | b;
    }

    /**
     * Finds the closest palette id for an RGB triple.
     *
     * <p>Uses the same weighted distance Bukkit uses, which matches human perception of
     * the palette far better than a plain Euclidean distance.
     */
    public static int nearest(int r, int g, int b) {
        int key = r << 16 | g << 8 | b;
        Integer cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        double bestDistance = Double.MAX_VALUE;
        int best = 4;
        for (int id = 4; id < RGB.length; id++) {
            int candidate = RGB[id];
            int cr = candidate >> 16 & 0xFF;
            int cg = candidate >> 8 & 0xFF;
            int cb = candidate & 0xFF;

            double rMean = (r + cr) / 2.0;
            double dr = r - cr;
            double dg = g - cg;
            double db = b - cb;
            double distance = (2 + rMean / 256) * dr * dr
                    + 4.0 * dg * dg
                    + (2 + (255 - rMean) / 256) * db * db;

            if (distance < bestDistance) {
                bestDistance = distance;
                best = id;
            }
        }

        CACHE.put(key, best);
        return best;
    }
}
