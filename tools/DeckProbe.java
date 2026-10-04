import dev.ingameppt.deck.SlideDeck;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks how slide file names are recognised, without needing a Minecraft server.
 *
 * <pre>
 * javac -encoding UTF-8 -cp out/plugin -d out/probe tools/DeckProbe.java
 * java -cp "out/plugin;out/probe" DeckProbe
 * </pre>
 */
public final class DeckProbe {

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("ingameppt-deck");
        List<String> names = List.of(
                "1.png", "2.png", "10.png", "3.PNG", "4.jpg", "5.jpeg",
                "cover.png", "notes.txt", "0.png", "-1.png", "007.png", "007.jpg", "12");
        for (String name : names) {
            Files.createFile(folder.resolve(name));
        }
        Files.createDirectory(folder.resolve("sub"));
        Files.createFile(folder.resolve("sub").resolve("6.png")); // must be ignored

        SlideDeck deck = SlideDeck.scan(folder);
        System.out.println("输入文件: " + names);
        System.out.println("识别到页数: " + deck.size() + " (期望 7)");
        for (int i = 0; i < deck.size(); i++) {
            System.out.println("  index " + i + " -> 页码 " + deck.slide(i).number()
                    + "  文件 " + deck.slide(i).fileName());
        }
        System.out.println("重复页码数: " + deck.duplicates() + " (期望 1)");
        System.out.println("indexOf(1) = " + deck.indexOf(1) + " (期望 0)");
        System.out.println("indexOf(9) = " + deck.indexOf(9) + " (期望 -1)");
        System.out.println("numbers() = " + deck.numbers());

        Path empty = Files.createTempDirectory("ingameppt-empty");
        System.out.println("空目录页数: " + SlideDeck.scan(empty).size() + " (期望 0)");
        System.out.println("不存在的目录页数: " + SlideDeck.scan(empty.resolve("nope")).size() + " (期望 0)");
    }
}
