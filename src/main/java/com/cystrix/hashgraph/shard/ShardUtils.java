package com.cystrix.hashgraph.shard;


import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.net.NodeServer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
public class ShardUtils {

    // 根据权值分片，每一个分片内的权值总和要求尽量持平
    // 16个节点
    public static void shard(List<NodeServer> nodeList, int shardSize) {
        if (nodeList.size() % shardSize != 0) {
            log.warn("分片失败，节点总数：{} 无法均匀分片 shardSize: {}", nodeList.size(), shardSize);
            throw new BusinessException("分片失败，无法均匀分片");
        }

        List<BigDecimal> weightList = generateWeightValue(nodeList);
        Map<BigDecimal, Integer> nodeWeightMap = new HashMap<>(nodeList.size());
        Map<Integer, BigDecimal> nodeWeightMap2 = new HashMap<>(nodeList.size());
        List<Integer> leaderIdList = new ArrayList<>();
        for (int i = 0; i < weightList.size(); i++) {
            nodeWeightMap.put(weightList.get(i), i);
            nodeWeightMap2.put(i, weightList.get(i));
        }
        weightList.sort(Collections.reverseOrder());

        // shard-id, nodeIdList
        System.out.println("x");        HashMap<Integer, List<Integer>> shardNodeListMap = new HashMap<>(shardSize);
        for (int i = 0; i < shardSize; i++) {
            shardNodeListMap.put(i, new ArrayList<>(nodeList.size()/shardSize));
        }
        for (int i = 0; i < weightList.size(); i += shardSize) {
            // 分组 添加 节点 id
            for (int j = 0; j < shardSize; j++) {
                //shardNodeListMap.get(j).add(nodeWeightMap.get(weightList.get(j*(i+1))));
                List<Integer> shardNodeIdList = shardNodeListMap.get(j);
                int nodeId =nodeWeightMap.get(weightList.get(i + j));
                shardNodeIdList.add(nodeId);
            }


        }
        // the  nodes in each shard only contribute to the consensus of events in their onw nodes
        // and only store the cross-shard events without consensus.
        // shard 0 => {node_id,...}
        // shard 1 => {node_id,...}
        // shard 2 => {node_id,...}
        // shard 3 => {node_id,...}

        // 决定每个分片的leader
        // 为每个分片随机选择一个领导人
//        System.out.println(shardNodeListMap);
        shardNodeListMap.forEach((shardId, shardNodeList)->{
            Random r = new Random(System.currentTimeMillis() / 10000);
            // 领导人权值选择值，如果x落入某个节点的权值区间，则该节点为leader.
            double x = r.nextDouble();
            BigDecimal sum = new BigDecimal("0");
            for (int i = 0; i < shardSize; i++) {
                sum = sum.add(nodeWeightMap2.get(shardNodeList.get(i)));
            }

            BigDecimal z = new BigDecimal(String.format("%.12f", x));
            z = z.multiply(sum);
            int leaderId = shardNodeList.get(0);
            for (int i = 0; i < shardSize; i++) {
                BigDecimal left,right;
                int nodeId = shardNodeList.get(i);
                if (i == 0) {
                    left = new BigDecimal("0");
                    right = nodeWeightMap2.get(nodeId);

                }else {
                    left = nodeWeightMap2.get(shardNodeList.get(i-1));
                    right = nodeWeightMap2.get(shardNodeList.get(i));
                }
                if (left.compareTo(z) >= 0 && right.compareTo(z) < 0) {
                    leaderId = nodeId;
                }
            }
            for (int i = 0; i < shardSize; i++) {
                int nodeId = shardNodeList.get(i);
                nodeList.get(nodeId).getHashgraphMember().setLeaderId(leaderId);
            }
            leaderIdList.add(leaderId);
        });


        // shard id, shard node id list
        shardNodeListMap.forEach((shardId, shardNodeList)->{
            for (int i = 0; i < shardSize; i++) {
                int nodeId = shardNodeList.get(i); // 一个分片内的一个nodeId
                HashgraphMember hashgraphMember = nodeList.get(nodeId).getHashgraphMember();
                hashgraphMember.setNeighborNodeAddrs(new ArrayList<>(shardNodeList));
                // 如果是leader节点
                if (leaderIdList.contains(nodeId)) {
                    hashgraphMember.getNeighborNodeAddrs().addAll(leaderIdList);
                }
                List<Integer> neighborIdList =  hashgraphMember.getNeighborNodeAddrs();
                hashgraphMember.setNeighborNodeAddrs(new ArrayList<>(new HashSet<>(neighborIdList)));
                hashgraphMember.setNodeStatusComprehensiveEvaluationValue(nodeWeightMap2.get(nodeId));
                //log.info("shard_id:{} node_id:{}, neighborIdList:{}, leader_id:{} quanzhi:{} ",shardId, nodeId,    hashgraphMember.getNeighborNodeAddrs(),
                //        hashgraphMember.getLeaderId(), hashgraphMember.getNodeStatusComprehensiveEvaluationValue());

            }
        });
    }

    //
    // 随机生成，模拟权值分布
    public static List<BigDecimal> generateWeightValue(List<NodeServer> nodeList) {
        int nodeNum = nodeList.size();
        //int nodeNum = 10;
        List<Integer> weightList = new ArrayList<>(nodeNum);
        List<BigDecimal> weightList2 = new ArrayList<>(nodeNum);
        int sum = 0;
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < nodeNum; i++) {
            int w = r.nextInt(Integer.MAX_VALUE/10000);
            weightList.add(w);
            sum += w;
        }
        for (int i = 0; i < nodeNum; i++) {
            String n = String.format("%.12f", weightList.get(i) / ((1.0) * sum));
            BigDecimal w = new BigDecimal(n);
            weightList2.add(w);
            nodeList.get(i).getHashgraphMember().setNodeStatusComprehensiveEvaluationValue(w);
        }
        return weightList2;
    }

}
