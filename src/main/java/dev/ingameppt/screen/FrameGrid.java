package dev.ingameppt.screen;

import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;

/**
 * The rectangle of item frames a slide is projected onto.
 *
 * <p>The player marks two opposite corners; everything in between must be an item frame
 * that faces the same way. From the facing we derive the two in-plane axes (right and
 * down as seen by a viewer standing in front of the wall), which gives every frame a
 * (column, row) address.
 */
public final class FrameGrid {

    /** Raised for anything the player can fix themselves; the message is shown as-is. */
    public static final class InvalidScreenException extends RuntimeException {
        public InvalidScreenException(String message) {
            super(message);
        }
    }

    private final World world;
    private final BlockFace facing;
    private final BlockVector origin;
    private final BlockVector right;
    private final BlockVector down;
    private final int cols;
    private final int rows;
    private final ItemFrame[][] frames;

    private FrameGrid(World world, BlockFace facing, BlockVector origin, BlockVector right, BlockVector down,
                      int cols, int rows, ItemFrame[][] frames) {
        this.world = world;
        this.facing = facing;
        this.origin = origin;
        this.right = right;
        this.down = down;
        this.cols = cols;
        this.rows = rows;
        this.frames = frames;
    }

    public World world() {
        return world;
    }

    public BlockFace facing() {
        return facing;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public ItemFrame frame(int col, int row) {
        return frames[row][col];
    }

    public String describe() {
        return cols + " x " + rows + " maps (" + (cols * 128) + " x " + (rows * 128) + " px), facing " + facing;
    }

    /** Resolves the rectangle spanned by two corner frames. */
    public static FrameGrid resolve(ItemFrame a, ItemFrame b) {
        if (!a.getWorld().equals(b.getWorld())) {
            throw new InvalidScreenException("两个角不在同一个世界里。");
        }

        BlockFace facing = facingOf(a);
        BlockFace other = facingOf(b);
        if (facing != other) {
            throw new InvalidScreenException("两个展示框朝向不同 (" + facing + " / " + other
                    + ")。所有展示框必须贴在同一个平面上、朝向一致。");
        }

        // Item frames are laid out on a regular lattice; using the entity's own block is
        // enough to address a cell, because two frames that share a cell always face
        // opposite ways and we reject mixed facings above.
        BlockVector cellA = cellOf(a);
        BlockVector cellB = cellOf(b);

        BlockVector right = rightAxis(facing);
        BlockVector down = downAxis(facing);

        int dx = cellB.getBlockX() - cellA.getBlockX();
        int dy = cellB.getBlockY() - cellA.getBlockY();
        int dz = cellB.getBlockZ() - cellA.getBlockZ();
        int u = dx * right.getBlockX() + dy * right.getBlockY() + dz * right.getBlockZ();
        int v = dx * down.getBlockX() + dy * down.getBlockY() + dz * down.getBlockZ();

        if (!step(step(cellA, right, u), down, v).equals(cellB)) {
            throw new InvalidScreenException("两个角没有构成一个和展示框平面平行的矩形。");
        }

        int cols = Math.abs(u) + 1;
        int rows = Math.abs(v) + 1;
        BlockVector origin = step(step(cellA, right, Math.min(0, u)), down, Math.min(0, v));

        ItemFrame[][] frames = collect(a.getWorld(), facing, origin, right, down, cols, rows);
        return new FrameGrid(a.getWorld(), facing, origin, right, down, cols, rows, frames);
    }

    /** Walks {@code times} steps along {@code axis}. Kept in integer maths on purpose. */
    private static BlockVector step(BlockVector from, BlockVector axis, int times) {
        return new BlockVector(
                from.getBlockX() + axis.getBlockX() * times,
                from.getBlockY() + axis.getBlockY() * times,
                from.getBlockZ() + axis.getBlockZ() * times);
    }

    private static ItemFrame[][] collect(World world, BlockFace facing, BlockVector origin,
                                         BlockVector right, BlockVector down, int cols, int rows) {
        BlockVector far = step(step(origin, right, cols - 1), down, rows - 1);

        BoundingBox box = BoundingBox.of(origin, far).expand(2, 2, 2);

        Map<String, ItemFrame> byCell = new HashMap<>();
        for (Entity entity : world.getNearbyEntities(box, e -> e instanceof ItemFrame)) {
            ItemFrame frame = (ItemFrame) entity;
            if (facingOf(frame) != facing) {
                continue;
            }
            BlockVector cell = cellOf(frame);
            byCell.putIfAbsent(key(cell), frame);
        }

        ItemFrame[][] frames = new ItemFrame[rows][cols];
        int missing = 0;
        StringBuilder firstMissing = null;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                BlockVector cell = step(step(origin, right, col), down, row);
                ItemFrame frame = byCell.get(key(cell));
                if (frame == null) {
                    missing++;
                    if (firstMissing == null) {
                        firstMissing = new StringBuilder(cell.getBlockX() + ", " + cell.getBlockY() + ", " + cell.getBlockZ());
                    }
                    continue;
                }
                frames[row][col] = frame;
            }
        }

        if (missing > 0) {
            throw new InvalidScreenException("选区内有 " + missing + " 个格子不是展示框(例如 "
                    + firstMissing + ")。先把整块矩形用展示框铺满。");
        }
        return frames;
    }

    private static String key(BlockVector vector) {
        return vector.getBlockX() + ":" + vector.getBlockY() + ":" + vector.getBlockZ();
    }

    public static BlockVector cellOf(ItemFrame frame) {
        return new BlockVector(frame.getLocation().getBlockX(),
                frame.getLocation().getBlockY(),
                frame.getLocation().getBlockZ());
    }

    /**
     * Reads the direction a frame is facing.
     *
     * <p>{@code getFacing()} comes from the legacy {@code Attachable} interface, so it is
     * wrapped defensively and falls back to the attached face (or, in the worst case, to
     * a north-facing wall) rather than blowing up the event handler.
     */
    public static BlockFace facingOf(ItemFrame frame) {
        try {
            BlockFace face = frame.getFacing();
            if (face != null && face.isCartesian() && face != BlockFace.SELF) {
                return face;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            BlockFace attached = frame.getAttachedFace();
            if (attached != null && attached.isCartesian() && attached != BlockFace.SELF) {
                return attached.getOppositeFace();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return BlockFace.SOUTH;
    }

    /** The axis that moves right on screen for a viewer standing in front of the frames. */
    private static BlockVector rightAxis(BlockFace facing) {
        switch (facing) {
            case NORTH:
                return new BlockVector(-1, 0, 0);
            case SOUTH:
                return new BlockVector(1, 0, 0);
            case EAST:
                return new BlockVector(0, 0, -1);
            case WEST:
                return new BlockVector(0, 0, 1);
            case UP:
                return new BlockVector(1, 0, 0);
            case DOWN:
                return new BlockVector(1, 0, 0);
            default:
                return new BlockVector(1, 0, 0);
        }
    }

    /** The axis that moves down on screen for a viewer standing in front of the frames. */
    private static BlockVector downAxis(BlockFace facing) {
        switch (facing) {
            case UP:
                return new BlockVector(0, 0, 1);
            case DOWN:
                return new BlockVector(0, 0, -1);
            default:
                return new BlockVector(0, -1, 0);
        }
    }

}
