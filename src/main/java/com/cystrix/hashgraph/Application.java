package com.cystrix.hashgraph;

import com.cystrix.chart.ChartUtils;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.net.NodeServer;
import com.cystrix.hashgraph.shard.ShardUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class Application {
    public static void main(String[] args) throws InterruptedException {
//        List<NodeServer> bootstrap = Application.bootstrap(8080, 16, 4, true);

//        ChartUtils.showTPSOne(bootstrap.get(0).getHashgraphMember());
//        Application.bootstrap2Hashgraph(4, 1);
//        Application.bootstrap3Hashgraph(128, 8);

        List<NodeServer> bootstrap = bootstrap(16, 4, 1);
        ChartUtils.showTPSOne(bootstrap.get(0).getHashgraphMember());
//        bootstrap(8080, 16, 0, 0);
//        bootstrap(8080, 32, 0, 0);
//        bootstrap(8080, 64, 0, 0);
//        bootstrap(8080, 128, 0, 0);
//        bootstrap(8080, 256, 0, 0);

    }

    // shardType 0 不分片
    // shardType 1 Ref Shard
    // shardType 2 Ours Shard
    public static List<NodeServer> bootstrap( int nodeNum, int shardSize ,int  shardType) {
        Map<Integer, Integer> portMap = new HashMap<>(nodeNum);
        List<NodeServer> serverList = new ArrayList<>();
        for (int i = 0; i < nodeNum; i++ ) {
            serverList.add(new NodeServer(20, new HashgraphMember(i, "node-"+i, nodeNum), shardType));
        }
        for (NodeServer server : serverList) {
            server.startup();
        }
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (shardType>=1) {
            ShardUtils.shard(serverList, shardSize);
        }else {
            for (NodeServer server : serverList) {
                portMap.put(server.getHashgraphMember().getId(), server.getPort());
            }
            for (NodeServer server: serverList) {
                HashMap<Integer, Integer> m = new HashMap<>(portMap);
                m.remove(server.getHashgraphMember().getId());
                server.getHashgraphMember().setIntraShardNeighborAddrs(m);
            }
        }
        for (NodeServer server : serverList) {
            server.startGossipSync();
        }
        return serverList;
    }

    public static void bootstrap2Hashgraph(int nodeNum, int shardSize) {
        // no shard hashgraph
        List<NodeServer> bootstrap = bootstrap(nodeNum, shardSize, 0);
        List<NodeServer> bootstrap1 = bootstrap(nodeNum, shardSize, 1);
        ChartUtils.showTPS(bootstrap.get(0).getHashgraphMember(), bootstrap1.get(0).getHashgraphMember());
    }

    public static void bootstrap3Hashgraph(int nodeNum, int shardSize) {
        List<NodeServer> bootstrap = bootstrap(nodeNum, shardSize, 0);
        List<NodeServer> bootstrap1 = bootstrap(nodeNum, shardSize, 1);
        List<NodeServer> bootstrap2 = bootstrap(nodeNum, shardSize, 2);
        ChartUtils.showTPS(bootstrap.get(0).getHashgraphMember(), bootstrap1.get(0).getHashgraphMember(), bootstrap2.get(0).getHashgraphMember());
    }

}
