package com.github.raftimpl.raft.storage;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

public class SnapshotTest {

    @Test
    public void testReadSnapshotDataFiles() throws IOException {
        String dataStorePath = "./data";
        File baseDir = new File("./data/message");
        baseDir.mkdirs();
        File messageFile1 = new File("./data/message/queue1.txt");
        messageFile1.createNewFile();
        File messageFile2 = new File("./data/message/queue2.txt");
        messageFile2.createNewFile();

        File snapshotDir = new File("./data/snapshot");
        snapshotDir.mkdirs();
        Path symLink = FileSystems.getDefault().getPath("./data/snapshot/data");
        Path srcPath = FileSystems.getDefault().getPath("./data/message").toRealPath();
        Files.createSymbolicLink(symLink, srcPath);

        Snapshot snapshotStorage = new Snapshot(dataStorePath);
        TreeMap<String, Snapshot.SnapshotDataFile> snapshotFiles = snapshotStorage.openSnapshotFiles();
        System.out.println(snapshotFiles.keySet());
        Assert.assertTrue(snapshotFiles.size() == 2);
        Assert.assertTrue(snapshotFiles.firstKey().equals("queue1.txt"));

        Files.delete(symLink);
        FileUtils.deleteDirectory(new File(dataStorePath));
    }
}
