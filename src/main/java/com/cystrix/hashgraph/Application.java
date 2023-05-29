package com.cystrix.hashgraph;

import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.net.NodeServer;
import com.cystrix.hashgraph.shard.ShardUtils;

import java.util.ArrayList;
import java.util.List;


public class Application {
    public static void main(String[] args) {
        // Application.bootstrap(8080,16, 4,true);
        Application.bootstrap2Hashgraph(16, 4);
    }

    public static void bootstrap(int portStart, int nodeNum, int shardSize ,boolean isShard) {
        int port = portStart;
        List<NodeServer> serverList = new ArrayList<>();
        for (int i = 0; i < nodeNum; i++ ) {
            serverList.add(new NodeServer(port, 20, new HashgraphMember(i, "node-"+i, nodeNum),isShard));
            port++;
        }
        // 分片
        if (isShard) {
            ShardUtils.shard(serverList, shardSize);
        }
        for (NodeServer server : serverList) {
            server.startup();
        }
    }

    public static void bootstrap2Hashgraph(int nodeNum, int shardSize) {
        // no shard hashgraph
        int port = 8080;
        int port2 = port + nodeNum;
        bootstrap(port, nodeNum, shardSize, true);
        bootstrap(port2, nodeNum, shardSize, false);

    }
}
