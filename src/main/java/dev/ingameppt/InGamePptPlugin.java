package dev.ingameppt;

import dev.ingameppt.command.PptCommand;
import dev.ingameppt.deck.PlayerSession;
import dev.ingameppt.deck.SlideDeck;
import dev.ingameppt.image.ImagePipeline;
import dev.ingameppt.image.TestSlide;
import dev.ingameppt.screen.FrameGrid;
import dev.ingameppt.screen.ScreenPainter;
import dev.ingameppt.select.InteractionListener;
import dev.ingameppt.select.Selection;
import dev.ingameppt.select.SelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InGamePptPlugin extends JavaPlugin {

    private static final String PREFIX = "\u00a7b[PPT] \u00a7r";
    private static final double PUSH_RADIUS = 64;

    private final SelectionManager selections = new SelectionManager();
    private final Map<UUID, FrameGrid> regions = new HashMap<>();
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Map<UUID, Long> lastCorner = new HashMap<>();
    private final Map<UUID, Long> lastPageTurn = new HashMap<>();

    private Path imageFolder;
    private Path slideFolder;
    private Material wand = Material.GOLDEN_AXE;
    private Material pagingWand = Material.DIAMOND_AXE;
    private boolean defaultDither = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        wand = itemOr(getConfig().getString("wand", "GOLDEN_AXE"), Material.GOLDEN_AXE);
        pagingWand = itemOr(getConfig().getString("paging-wand", "DIAMOND_AXE"), Material.DIAMOND_AXE);
        defaultDither = getConfig().getBoolean("default-dither", true);

        // Absolute on purpose: getDataFolder() is relative to the server's working
        // directory, and mixing relative folder listings with relative lookups is how the
        // slide paths got doubled.
        Path dataFolder = getDataFolder().toPath().toAbsolutePath().normalize();
        imageFolder = dataFolder.resolve("images");
        slideFolder = dataFolder.resolve(getConfig().getString("ppt-folder", "ppt"));
        createFolder(imageFolder);
        createFolder(slideFolder);

        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);
        PptCommand command = new PptCommand(this);
        getCommand("ppt").setExecutor(command);
        getCommand("ppt").setTabCompleter(command);

        getLogger().info("Ready. Selection wand: " + wand + ", paging wand: " + pagingWand);
        getLogger().info("Slides: " + slideFolder);
    }

    private Material itemOr(String name, Material fallback) {
        Material parsed = name == null ? null : Material.matchMaterial(name);
        if (parsed == null || !parsed.isItem()) {
            getLogger().warning("Unknown material '" + name + "', falling back to " + fallback + ".");
            return fallback;
        }
        return parsed;
    }

    private void createFolder(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            getLogger().warning("Could not create " + folder + ": " + exception.getMessage());
        }
    }

    // ------------------------------------------------------------------ accessors

    public SelectionManager selections() {
        return selections;
    }

    public Material wand() {
        return wand;
    }

    public Material pagingWand() {
        return pagingWand;
    }

    public Path slideFolder() {
        return slideFolder;
    }

    public boolean defaultDither() {
        return defaultDither;
    }

    public boolean isHolding(Player player, Material material) {
        return player.getInventory().getItemInMainHand().getType() == material;
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ region

    public FrameGrid region(Player player) {
        FrameGrid grid = regions.get(player.getUniqueId());
        if (grid == null) {
            return null;
        }
        if (!isIntact(grid)) {
            regions.remove(player.getUniqueId());
            return null;
        }
        return grid;
    }

    private boolean isIntact(FrameGrid grid) {
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.cols(); col++) {
                if (!grid.frame(col, row).isValid()) {
                    return false;
                }
            }
        }
        return true;
    }

    public void setRegion(Player player, FrameGrid grid) {
        regions.put(player.getUniqueId(), grid);
        sessions.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ selection

    /** Called by the listener when the player punches a frame with the selection wand. */
    public void onCornerMarked(Player player, ItemFrame frame) {
        if (!debounce(lastCorner, player)) {
            return;
        }

        Selection first = selections.firstCorner(player);
        if (first != null && !first.frame().isValid()) {
            selections.clearFirstCorner(player);
            first = null;
        }

        if (first == null) {
            selections.setFirstCorner(player, new Selection(frame, System.currentTimeMillis()));
            player.sendMessage(PREFIX + "\u00a7a已记录第一个角\u00a7r (" + describe(frame)
                    + ")。现在用金斧左键点\u00a7e对角\u00a7r那个展示框。");
            return;
        }

        if (first.frame().getUniqueId().equals(frame.getUniqueId())) {
            player.sendMessage(PREFIX + "这就是第一个角,请点对角的展示框。");
            return;
        }

        try {
            FrameGrid grid = FrameGrid.resolve(first.frame(), frame);
            setRegion(player, grid);
            selections.stop(player);
            player.sendMessage(PREFIX + "\u00a7a选区已锁定: \u00a7f" + grid.describe());
            player.sendMessage(PREFIX + "把幻灯片(1.png、2.png ...)放进 \u00a7e"
                    + relative(slideFolder) + "\u00a7r,然后 \u00a7e/ppt play\u00a7r 开始播放。");
        } catch (FrameGrid.InvalidScreenException exception) {
            player.sendMessage(PREFIX + "\u00a7c选区无效: \u00a7r" + exception.getMessage());
        }
    }

    private String describe(ItemFrame frame) {
        return frame.getLocation().getBlockX() + ", " + frame.getLocation().getBlockY()
                + ", " + frame.getLocation().getBlockZ();
    }

    private String relative(Path path) {
        try {
            return Path.of("").toAbsolutePath().normalize().relativize(path).toString();
        } catch (IllegalArgumentException notRelative) {
            return path.toString();
        }
    }

    private boolean debounce(Map<UUID, Long> map, Player player) {
        long now = System.currentTimeMillis();
        Long previous = map.get(player.getUniqueId());
        if (previous != null && now - previous < 130) {
            return false;
        }
        map.put(player.getUniqueId(), now);
        return true;
    }

    // ------------------------------------------------------------------ playback

    public void play(Player player) {
        FrameGrid grid = region(player);
        if (grid == null) {
            player.sendMessage(PREFIX + "\u00a7c还没有选区,先 /ppt select。");
            return;
        }

        SlideDeck deck;
        try {
            deck = SlideDeck.scan(slideFolder);
        } catch (IOException exception) {
            player.sendMessage(PREFIX + "\u00a7c读取幻灯片目录失败: \u00a7r" + exception.getMessage());
            return;
        }

        if (deck.size() == 0) {
            player.sendMessage(PREFIX + "\u00a7c在 \u00a7e" + relative(slideFolder)
                    + "\u00a7c 里没有找到幻灯片。文件名必须是纯数字,例如 \u00a7e1.png\u00a7c、\u00a7e2.png\u00a7c。");
            return;
        }

        int start = deck.indexOf(1);
        if (start < 0) {
            player.sendMessage(PREFIX + "\u00a7c找不到 1.png,播放要从第 1 页开始。");
            player.sendMessage(PREFIX + "当前识别到的页码: \u00a7f" + deck.numbers());
            return;
        }

        if (deck.duplicates() > 0) {
            player.sendMessage(PREFIX + "\u00a7e注意: 有 " + deck.duplicates() + " 个重复页码被忽略。");
        }

        PlayerSession session = new PlayerSession(grid, deck);
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(PREFIX + "\u00a7a开始播放\u00a7r: 共 " + deck.size() + " 页。"
                + "翻页用 \u00a7e/ppt +\u00a7r / \u00a7e/ppt -\u00a7r,或者手持钻石斧"
                + "\u00a7e左键上一页\u00a7r、\u00a7e右键下一页\u00a7r。");
        showPage(player, session, start, true);
    }

    /** @param delta -1 for the previous page, +1 for the next one */
    public void turnPage(Player player, int delta) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(PREFIX + "\u00a7c还没有在播放幻灯片,先 /ppt play。");
            return;
        }
        if (!debounce(lastPageTurn, player)) {
            return;
        }
        if (!isIntact(session.grid())) {
            sessions.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "\u00a7c屏幕上的展示框被破坏或卸载了,请重新 /ppt select。");
            return;
        }

        int size = session.deck().size();
        int target = session.index() + delta;
        if (target < 0) {
            player.sendMessage(PREFIX + "\u00a7e已经是第一页了\u00a7r (1/" + size + ")");
            return;
        }
        if (target >= size) {
            player.sendMessage(PREFIX + "\u00a7e已经是最后一页了\u00a7r (" + size + "/" + size + ")");
            return;
        }
        showPage(player, session, target, false);
    }

    public void stopSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private void showPage(Player player, PlayerSession session, int index, boolean first) {
        if (session.busy()) {
            player.sendActionBar("\u00a7e上一页还在渲染,请稍等一下");
            return;
        }

        session.setIndex(index);
        SlideDeck.Slide slide = session.deck().slide(index);
        FrameGrid grid = session.grid();

        if (session.isCached(index)) {
            ScreenPainter.apply(grid, session.items(index));
            pushMaps(player, grid, session.views(index));
            announce(player, session, slide, first);
            return;
        }

        session.setBusy(true);
        int width = grid.cols() * ImagePipeline.TILE;
        int height = grid.rows() * ImagePipeline.TILE;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                BufferedImage image = ImagePipeline.load(slide.file().toString(), slideFolder);
                BufferedImage fitted = ImagePipeline.containFit(image, width, height);
                int[][] ids = ImagePipeline.toMapIds(fitted, defaultDither);

                Bukkit.getScheduler().runTask(this, () -> {
                    if (!isIntact(grid)) {
                        session.setBusy(false);
                        sessions.remove(player.getUniqueId());
                        player.sendMessage(PREFIX + "\u00a7c屏幕已失效,请重新 /ppt select。");
                        return;
                    }
                    ScreenPainter.PaintedPage page = ScreenPainter.build(grid, ids);
                    session.cache(index, page.items(), page.views());
                    session.setBusy(false);
                    ScreenPainter.apply(grid, page.items());
                    pushMaps(player, grid, page.views());
                    announce(player, session, slide, first);
                });
            } catch (Exception exception) {
                getLogger().warning("Could not render " + slide.fileName() + ": " + exception);
                session.setBusy(false);
                Bukkit.getScheduler().runTask(this, () -> player.sendMessage(
                        PREFIX + "\u00a7c第 " + slide.number() + " 页渲染失败: \u00a7r" + exception.getMessage()));
            }
        });
    }

    private void announce(Player player, PlayerSession session, SlideDeck.Slide slide, boolean first) {
        String label = "\u00a7b第 " + (session.index() + 1) + "/" + session.deck().size() + " 页 \u00a77"
                + slide.fileName();
        player.sendActionBar(label);
        if (first) {
            player.sendMessage(PREFIX + "正在播放 \u00a7f" + slide.fileName());
        }
    }

    /**
     * Forces the maps to be rendered and pushed right now.
     *
     * <p>The player who flipped the page gets every tile directly, which both triggers the
     * renderer immediately and guarantees they see the new page without waiting for the
     * server to notice the frames changed. Players standing near the wall get the same push
     * so the audience never lags behind.
     */
    private void pushMaps(Player actor, FrameGrid grid, List<MapView> views) {
        if (views == null) {
            return;
        }
        for (MapView view : views) {
            actor.sendMap(view);
        }

        Location centre = grid.frame(0, 0).getLocation();
        World world = grid.world();
        double radiusSquared = PUSH_RADIUS * PUSH_RADIUS;
        for (Player other : world.getPlayers()) {
            if (other.equals(actor) || !other.getWorld().equals(world)) {
                continue;
            }
            if (other.getLocation().distanceSquared(centre) > radiusSquared) {
                continue;
            }
            for (MapView view : views) {
                other.sendMap(view);
            }
        }
    }

    // ------------------------------------------------------------------ one-off test

    /** Fits, quantises and paints a single image, for checking a screen without a deck. */
    public void renderTest(Player player, String source, boolean dither) {
        FrameGrid grid = region(player);
        if (grid == null) {
            player.sendMessage(PREFIX + "\u00a7c还没有选区,先 /ppt select。");
            return;
        }

        int width = grid.cols() * ImagePipeline.TILE;
        int height = grid.rows() * ImagePipeline.TILE;
        player.sendMessage(PREFIX + "正在渲染 " + width + "x" + height + " (" + grid.describe() + ")...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                BufferedImage image = source == null || source.isBlank()
                        ? TestSlide.render(width * 2, height * 2)
                        : ImagePipeline.load(source, imageFolder);
                BufferedImage fitted = ImagePipeline.containFit(image, width, height);
                int[][] ids = ImagePipeline.toMapIds(fitted, dither);

                Bukkit.getScheduler().runTask(this, () -> {
                    ScreenPainter.PaintedPage page = ScreenPainter.build(grid, ids);
                    ScreenPainter.apply(grid, page.items());
                    pushMaps(player, grid, page.views());
                    player.sendMessage(PREFIX + "\u00a7a完成\u00a7r: " + grid.describe()
                            + (dither ? ", 已开启抖动" : ", 未抖动"));
                });
            } catch (Exception exception) {
                getLogger().warning("Render failed: " + exception);
                Bukkit.getScheduler().runTask(this, () -> player.sendMessage(
                        PREFIX + "\u00a7c渲染失败: \u00a7r" + exception.getMessage()));
            }
        });
    }
}
