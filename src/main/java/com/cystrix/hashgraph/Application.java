package com.cystrix.hashgraph;

import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.net.NodeServer;
import com.cystrix.hashgraph.shard.ShardUtils;

import java.util.ArrayList;
import java.util.List;


public class Application {
    public static void main(String[] args) {
        Application.bootstrap(16);
    }

    public static void bootstrap(int nodeNum) {
        int port = 8080;
        List<NodeServer> serverList = new ArrayList<>();
        for (int i = 0; i < nodeNum; i++ ) {
            serverList.add(new NodeServer(port, 20, new HashgraphMember(i, "node-"+i, nodeNum)));
            port++;
        }
        // 分片
        ShardUtils.shard(serverList, 4);

        for (NodeServer server : serverList) {
            server.startup();
        }
    }
}
