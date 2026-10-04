package dev.ingameppt.command;

import dev.ingameppt.InGamePptPlugin;
import dev.ingameppt.screen.FrameGrid;
import dev.ingameppt.screen.ScreenPainter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PptCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "\u00a7b[PPT] \u00a7r";
    private static final List<String> SUBS =
            List.of("play", "+", "-", "select", "test", "clear", "info", "cancel");

    private final InGamePptPlugin plugin;

    public PptCommand(InGamePptPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "play" -> plugin.play(player);
            case "+", "next" -> plugin.turnPage(player, 1);
            case "-", "prev" -> plugin.turnPage(player, -1);
            case "select" -> {
                plugin.selections().begin(player);
                player.sendMessage(PREFIX + "\u00a7a选区模式已开启\u00a7r。手持 " + plugin.wand()
                        + " 左键点击区域\u00a7e左上角\u00a7r的展示框,再点\u00a7e右下角\u00a7r那个。");
            }
            case "cancel" -> {
                plugin.selections().stop(player);
                player.sendMessage(PREFIX + "已退出选区模式。");
            }
            case "test" -> {
                String source = args.length > 1 ? args[1] : null;
                boolean dither = plugin.defaultDither();
                for (int i = 1; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("nodither")) {
                        dither = false;
                    } else if (args[i].equalsIgnoreCase("dither")) {
                        dither = true;
                    } else {
                        source = args[i];
                    }
                }
                plugin.renderTest(player, source, dither);
            }
            case "clear" -> {
                FrameGrid grid = plugin.region(player);
                plugin.stopSession(player);
                if (grid == null) {
                    player.sendMessage(PREFIX + "\u00a7c还没有选区。");
                    return true;
                }
                ScreenPainter.clear(grid);
                player.sendMessage(PREFIX + "已清空 " + grid.describe() + "。");
            }
            case "info" -> {
                FrameGrid grid = plugin.region(player);
                if (grid == null) {
                    player.sendMessage(PREFIX + "还没有选区。用 \u00a7e/ppt select\u00a7r 开始。");
                    return true;
                }
                player.sendMessage(PREFIX + "选区: \u00a7f" + grid.describe());
                player.sendMessage(PREFIX + "左上角单元格: \u00a7f"
                        + grid.frame(0, 0).getLocation().getBlockX() + ", "
                        + grid.frame(0, 0).getLocation().getBlockY() + ", "
                        + grid.frame(0, 0).getLocation().getBlockZ()
                        + "  世界 \u00a7f" + grid.world().getName());
                player.sendMessage(PREFIX + "幻灯片目录: \u00a7f" + plugin.slideFolder());
                player.sendMessage(PREFIX + "播放中: \u00a7f" + (plugin.hasSession(player) ? "是" : "否"));
            }
            default -> usage(player);
        }
        return true;
    }

    private void usage(Player player) {
        player.sendMessage(PREFIX + "\u00a7e/ppt select\u00a7r - 选区模式,用 " + plugin.wand() + " 点两个对角展示框");
        player.sendMessage(PREFIX + "\u00a7e/ppt play\u00a7r - 从 1.png 开始播放");
        player.sendMessage(PREFIX + "\u00a7e/ppt +\u00a7r / \u00a7e/ppt -\u00a7r - 下一页 / 上一页"
                + "(也可手持 " + plugin.pagingWand() + " 左键上一页、右键下一页)");
        player.sendMessage(PREFIX + "\u00a7e/ppt test [图片] [nodither]\u00a7r - 单独投一张图");
        player.sendMessage(PREFIX + "\u00a7e/ppt clear\u00a7r - 清空展示框   \u00a7e/ppt info\u00a7r - 查看选区"
                + "   \u00a7e/ppt cancel\u00a7r - 退出选区模式");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (String sub : SUBS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            return List.of("dither", "nodither");
        }
        return List.of();
    }
}
