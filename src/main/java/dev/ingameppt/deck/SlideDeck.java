package dev.ingameppt.deck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The slides found in {@code plugins/InGamePPT/ppt}.
 *
 * <p>Only files whose name is a plain number plus an image extension count as slides
 * ({@code 1.png}, {@code 2.png}, ...). Anything else in the folder is ignored, which keeps
 * notes, exports and thumbnails from being mistaken for pages.
 *
 * <p>Order is numeric, not alphabetical, so slide 10 comes after slide 9.
 */
public final class SlideDeck {

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");

    public record Slide(int number, Path file) {
        public String fileName() {
            return file.getFileName().toString();
        }
    }

    private final List<Slide> slides;
    private final int duplicates;

    private SlideDeck(List<Slide> slides, int duplicates) {
        this.slides = slides;
        this.duplicates = duplicates;
    }

    public static SlideDeck scan(Path folder) throws IOException {
        TreeMap<Integer, Path> byNumber = new TreeMap<>();
        int duplicates = 0;

        if (Files.isDirectory(folder)) {
            try (Stream<Path> entries = Files.list(folder)) {
                for (Path file : entries.toList()) {
                    Integer number = numberOf(file);
                    if (number == null) {
                        continue;
                    }
                    if (byNumber.putIfAbsent(number, file) != null) {
                        duplicates++;
                    }
                }
            }
        }

        List<Slide> slides = new ArrayList<>(byNumber.size());
        byNumber.forEach((number, file) -> slides.add(new Slide(number, file)));
        return new SlideDeck(slides, duplicates);
    }

    /** @return the slide number encoded in the file name, or null if this is not a slide */
    private static Integer numberOf(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String stem = name.substring(0, dot);
        if (stem.isEmpty() || !stem.chars().allMatch(Character::isDigit)) {
            return null;
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            return null;
        }
        try {
            int number = Integer.parseInt(stem);
            return number >= 1 ? number : null;
        } catch (NumberFormatException tooBig) {
            return null;
        }
    }

    public int size() {
        return slides.size();
    }

    public int duplicates() {
        return duplicates;
    }

    /** 0-based lookup. */
    public Slide slide(int index) {
        return slides.get(index);
    }

    /** @return the 0-based index of the given slide number, or -1 when it is missing */
    public int indexOf(int number) {
        for (int i = 0; i < slides.size(); i++) {
            if (slides.get(i).number() == number) {
                return i;
            }
        }
        return -1;
    }

    /** Human readable list of the recognised numbers, for error messages. */
    public String numbers() {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (Slide slide : slides) {
            if (shown == 12) {
                builder.append("...");
                break;
            }
            if (shown > 0) {
                builder.append(", ");
            }
            builder.append(slide.number());
            shown++;
        }
        return builder.toString();
    }
}

