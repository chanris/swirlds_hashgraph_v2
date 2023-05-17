package com.cystrix.hashgraph.hashview;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class HashgraphMember {
    private Integer id;
    private  String name;
    private ConcurrentHashMap<Integer, List<Event>> hashgraph; // 以map的方式保留hashgraph 副本
    //private Graph graph; // 以邻接矩阵的方式保留事件之间的 引用关系
    private int numNodes; // 成员节点总数

    private boolean shutdown = false;

    public HashgraphMember(Integer id, String name, int numNodes) {
        this.id = id;
        this.name = name;
        this.numNodes = numNodes;
        this.hashgraph = new ConcurrentHashMap<>();
    }
}
