package com.github.raftimpl.raft.example.client;

import com.baidu.brpc.client.BrpcProxy;
import com.baidu.brpc.client.RpcClient;
import com.github.raftimpl.raft.example.server.service.ExampleProto;
import com.github.raftimpl.raft.example.server.service.ExampleService;
import com.googlecode.protobuf.format.JsonFormat;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentClientMain {
    private static JsonFormat jsonFormat = new JsonFormat();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.printf("Usage: ./run_concurrent_client.sh THREAD_NUM\n");
            System.exit(-1);
        }

        // parse args
        String serverEndpoints = args[0];
        RpcClient rpcClient = new RpcClient(serverEndpoints);
        ExampleService exampleService = BrpcProxy.getProxy(rpcClient, ExampleService.class);

        ExecutorService readerPool = Executors.newFixedThreadPool(3);
        ExecutorService writerPool = Executors.newFixedThreadPool(3);
        Future<?>[] tasks = new Future[3];
        for (int taskId = 0; taskId < 3; taskId++) {
            tasks[taskId] = writerPool.submit(new SetTask(exampleService, readerPool));
        }
    }

    public static class SetTask implements Runnable {
        private ExampleService exampleService;
        ExecutorService readerPool;

        public SetTask(ExampleService exampleService, ExecutorService readerPool) {
            this.exampleService = exampleService;
            this.readerPool = readerPool;
        }

        @Override
        public void run() {
            while (true) {
                String recordKey = UUID.randomUUID().toString();
                String recordValue = UUID.randomUUID().toString();
                ExampleProto.SetRequest writeRequest = ExampleProto.SetRequest.newBuilder()
                        .setKey(recordKey).setValue(recordValue).build();

                long beginTime = System.currentTimeMillis();
                ExampleProto.SetResponse writeResponse = exampleService.set(writeRequest);
                try {
                    if (writeResponse != null) {
                        System.out.printf("set request, key=%s, value=%s, response=%s, elapseMS=%d\n",
                                recordKey, recordValue, jsonFormat.printToString(writeResponse), System.currentTimeMillis() - beginTime);
                        readerPool.submit(new GetTask(exampleService, recordKey));
                    } else {
                        System.out.printf("set request failed, key=%s value=%s\n", recordKey, recordValue);
                    }
                } catch (Exception err) {
                    err.printStackTrace();
                }
            }
        }
    }

    public static class GetTask implements Runnable {
        private ExampleService exampleService;
        private String recordKey;

        public GetTask(ExampleService exampleService, String recordKey) {
            this.exampleService = exampleService;
            this.recordKey = recordKey;
        }

        @Override
        public void run() {
            ExampleProto.GetRequest readRequest = ExampleProto.GetRequest.newBuilder()
                    .setKey(recordKey).build();
            long beginTime = System.currentTimeMillis();
            ExampleProto.GetResponse readResponse = exampleService.get(readRequest);
            try {
                if (readResponse != null) {
                    System.out.printf("get request, key=%s, response=%s, elapseMS=%d\n",
                            recordKey, jsonFormat.printToString(readResponse), System.currentTimeMillis() - beginTime);
                } else {
                    System.out.printf("get request failed, key=%s\n", recordKey);
                }
            } catch (Exception err) {
                err.printStackTrace();
            }
        }
    }
}
