package com.github.raftimpl.raft.storage;

import com.github.raftimpl.raft.proto.RaftProto;
import com.google.protobuf.ByteString;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentedLogTest {

    @Test
    public void testTruncateSuffix() throws IOException {
        String dataStorePath = "./data";
        SegmentedLog logStorage = new SegmentedLog(dataStorePath, 32);
        Assert.assertTrue(logStorage.getFirstLogIndex() == 1);

        List<RaftProto.LogEntry> logEntries = new ArrayList<>();
        for (int entryNum = 1; entryNum < 10; entryNum++) {
            RaftProto.LogEntry logRecord = RaftProto.LogEntry.newBuilder()
                    .setData(ByteString.copyFrom(("testEntryData" + entryNum).getBytes()))
                    .setType(RaftProto.EntryType.ENTRY_TYPE_DATA)
                    .setIndex(entryNum)
                    .setTerm(entryNum)
                    .build();
            logEntries.add(logRecord);
        }
        long finalIndex = logStorage.append(logEntries);
        Assert.assertTrue(finalIndex == 9);

        logStorage.truncatePrefix(5);
        FileUtils.deleteDirectory(new File(dataStorePath));
    }
}
