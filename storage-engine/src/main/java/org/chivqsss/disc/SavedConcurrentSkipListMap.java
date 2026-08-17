package org.chivqsss.disc;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentSkipListMap;

public class SavedConcurrentSkipListMap<K,V> extends ConcurrentSkipListMap<K, V> {
    public void serialize(Path writeToFile) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(writeToFile))) {
            oos.writeObject(this);
        }
    }

    public void deserialize(Path readFromFile) {

    }
}
