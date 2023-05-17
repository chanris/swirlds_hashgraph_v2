package com.cystrix.hashgraph;

import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.net.NodeServer;


public class Application {
    public static void main(String[] args) throws InterruptedException {
        NodeServer node = new NodeServer(8080, 20, new HashgraphMember(0, "node-1", 4));
        NodeServer node2 = new NodeServer(8081, 20, new HashgraphMember(1, "node-2", 4));
        NodeServer node3 = new NodeServer(8082, 20, new HashgraphMember(2, "node-3", 4));
        NodeServer node4 = new NodeServer(8083, 20, new HashgraphMember(3, "node-4", 4));

        node.startup();
        node2.startup();
        node3.startup();
        node4.startup();
    }
}
