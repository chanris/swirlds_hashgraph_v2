package com.cystrix.hashgraph.hashview;

import com.alibaba.fastjson2.JSON;
import com.cystrix.hashgraph.hashview.search.DFS;
import com.cystrix.hashgraph.util.SHA256;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Data
public class HashgraphMember {
    {
        Map<String, String> keyPair = SHA256.generateKeyPairBase64();
        PK = keyPair.get("PK");
        SK = keyPair.get("SK");
    }
    private Integer id;
    private  String name;
    private ConcurrentHashMap<Integer, List<Event>> hashgraph; // 以map的方式保留hashgraph 副本
    private int numNodes; // 成员节点总数
    private boolean shutdown = false;
    private  final String PK;
    private  final String SK; // sk base64 encode
    private List<Transaction> waitForPackEventList;

    private ConcurrentHashMap<String, Event> eventHashMap;

    private Object lock = new Object();

    public String getPk() {
        return this.PK;
    }
    public String getSk() {
        return this.SK;
    }
    public HashgraphMember(Integer id, String name, int numNodes) {
        this.id = id;
        this.name = name;
        this.numNodes = numNodes;
        this.hashgraph = new ConcurrentHashMap<>();
        this.eventHashMap = new ConcurrentHashMap<>();
        this.waitForPackEventList = new ArrayList<>();
        for (int i = 0; i < this.numNodes; i++) {
            List<Event> chain = new ArrayList<>();
            this.hashgraph.put(i,chain);
        }
        // 初始化第一个事件
        Event e = new Event();
        e.setTimestamp(System.currentTimeMillis());
        e.setNodeId(id);
        e.setSignature(SHA256.signEvent(e, getSk()));
        this.hashgraph.get(id).add(e);
        String eventHash = null;
        try {
            eventHash = SHA256.sha256HexString(JSON.toJSONString(e));
            this.eventHashMap.put(eventHash, e);
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Deprecated
    public synchronized boolean addEvent(Event event) {
        List<Event> events = hashgraph.get(event.getNodeId());
        if (events != null) {
            if (events.size() != 0) {
                Event lastParentEvent = events.get(events.size()-1);
                if (lastParentEvent != event.getSelfParent()) {
                    return false;
                }
                Event otherParentEvent = event.getOtherParent();
                Integer nodeId = otherParentEvent.getNodeId();
                int otherChainSize = hashgraph.get(nodeId).size();
                if (hashgraph.get(nodeId).get(otherChainSize - 1) != otherParentEvent) {
                    return false;
                }
                events.add(event);
            }else {
                events.add(event);
            }
        }
        return true;
    }

    // todo 没有考虑拜占庭节点行为
    public synchronized boolean addEventBatch(HashMap<Integer, List<Event>> subHashgraph) {

        this.hashgraph.forEach((id, chain)->{
            if (subHashgraph.containsKey(id)) {
                List<Event> subChain = subHashgraph.get(id);
                chain.addAll(subChain);
                for (Event e: subChain) {
                    try {
                        this.eventHashMap.put(SHA256.sha256HexString(JSON.toJSONString(e)), e);
                    }catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        // for build DFS structure
        subHashgraph.forEach((id, chain)->{
            // 找到新加入的事件的自父亲event
            if (chain.size() != 0) {
                for (int i = chain.size()- 1; i > 0; i--) {
                    Event e = chain.get(i);
                    e.setSelfParent(chain.get(i-1));
                    Event otherParent = this.eventHashMap.get(e.getOtherParentHash());
                    e.setOtherParent(otherParent);

                    List<Event> neighbors = new ArrayList<>(2);
                    neighbors.add(e.getSelfParent());
                    neighbors.add(otherParent);
                    e.setNeighbors(neighbors);
                }

                // 若不是第一个event，则说明有父亲事件
                int index = this.hashgraph.get(id).indexOf(chain.get(0));
                if (index != 0) {
                    List<Event> neighbors = new ArrayList<>(2);
                    Event selfParent = this.hashgraph.get(id).get(index-1);
                    Event otherParent = this.eventHashMap.get(chain.get(0).getOtherParentHash());
                    chain.get(0).setSelfParent(selfParent);
                    chain.get(0).setOtherParent(otherParent);
                    neighbors.add(selfParent);
                    neighbors.add(otherParent);
                }
            }
        });
        return true;
    }




    public void divideRounds() {

    }

    public void decideFame() {

    }

    public void findOrder() {

    }


    private boolean isSee(Event src, Event dest) {
        return DFS.findPath(src, dest, new ArrayList<>());
    }

    private boolean isStronglySee(Event src, Event dest) {
        Set<Integer> set = new HashSet<>();
        List<List<Event>> allPaths = DFS.findAllPaths(src, dest);
        for (List<Event>  path: allPaths) {
            for (Event e: path) {
                set.add(e.getNodeId());
            }
            if (set.size() > (2 * numNodes / 3)) {
                return true;
            }else {
                set.clear();
            }
        }
        return false;
    }
}
