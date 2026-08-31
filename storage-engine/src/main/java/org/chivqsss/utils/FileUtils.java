package org.chivqsss.utils;

import org.chivqsss.Engine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.OptionalLong;

import static org.chivqsss.Engine.DATE_TIME_FORMATTER;

public class FileUtils {
    private static final String SSTABLE_PREFIX = "sstable_";
    private static final String SSTABLE_SUFFIX = ".sst";
    private static final String WAL_PREFIX = "conqydb";
    private static final String WAL_SUFFIX = ".log";

    public static File[] findSSTableSorted(Path dir, String extension) {
        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(extension));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName).reversed());
            return files;
        }
        return new File[0];
    }

    public static File findNewestWAL(Path dir) {
        LocalDateTime date = LocalDateTime.now();
        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith("log"));
        if (files != null && files.length > 0) {
            Arrays.sort(files, (file, t1) -> {
                String str1 = file.getName().substring(WAL_PREFIX.length(), file.getName().length() - WAL_SUFFIX.length());
                String str2 = t1.getName().substring(WAL_PREFIX.length(), t1.getName().length() - WAL_SUFFIX.length());
                LocalDateTime date1 = LocalDateTime.parse(str1, DATE_TIME_FORMATTER);
                LocalDateTime date2 = LocalDateTime.parse(str2, DATE_TIME_FORMATTER);
                return date2.compareTo(date1);
            });
            return files[0];
        }
        return dir.resolve("conqydb-" + date.format(DATE_TIME_FORMATTER) + ".log").toFile();
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

    public static Comparator<String> utf8Comparator = (s1, s2) -> {
        byte[] b1 = s1.getBytes(StandardCharsets.UTF_8);
        byte[] b2 = s2.getBytes(StandardCharsets.UTF_8);
        return Arrays.compareUnsigned(b1, b2);
    };
}
