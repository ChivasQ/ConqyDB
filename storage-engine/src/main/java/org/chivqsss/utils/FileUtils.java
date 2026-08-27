package org.chivqsss.utils;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.OptionalLong;

public class FileUtils {
    private static final String SSTABLE_PREFIX = "sstable_";
    private static final String SSTABLE_SUFFIX = ".sst";

    public static File[] findFilesSorted(Path dir, String extension) {
        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(extension));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName).reversed());
            return files;
        }
        return new File[0];
    }

    public static OptionalLong parseSeqFromName(String name) {
        if (!name.startsWith(SSTABLE_PREFIX) || !name.endsWith(SSTABLE_SUFFIX)) {
            return OptionalLong.empty();
        }
        String numberStr = name.substring(SSTABLE_PREFIX.length(), name.length() - SSTABLE_SUFFIX.length());
        try {
            return OptionalLong.of(Long.parseLong(numberStr));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
