package com.cystrix.hashgraph.net;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.Event;
import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.hashview.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class RequestHandler {

    private HashgraphMember hashgraphMember;
    public RequestHandler(HashgraphMember hashgraphMember) {
        this.hashgraphMember = hashgraphMember;
    }

    public Response process(Request request) {
        Response response = new Response();
        // 约定通信的内容为json字符串对象
        if (request.getMapping() == null) {
            request.setMapping("/default");
        }
        switch (request.getMapping()) {
            case "/sendTransaction":
                sendTransactionMapping(request, response);
                break;
            case "/shutdown":
                shutdownMapping(request, response);
                break;
            case "/pullEvent":
                pullEventMapping(request, response);
                break;
            case "/pullEventByOtherShard":
                pullEventByOtherShardMapping(request, response);
                break;
            case "/pullEventBySelfShard":
                pullEventBySelfShardMapping(request, response);
                break;
            case "/default":
                defaultMapping(request, response);
            default:
                errorMapping(request, response);
                break;
        }
        return response;
    }

    private void pullEventBySelfShardMapping(Request request, Response response) {
        String json = request.getData();
        if(json == null) {
            response.setCode(400);
            response.setMessage("请求参数错误");
            return;
        }
        //log.info("接收到的高度数据:{}", json);
        HashMap<Integer, Integer> hashMap = JSONObject.parseObject(json, HashMap.class);  //请求者的哈希图高度
        //log.info("解析后的 高度数据:{}", hashMap);
        //  接收者的邻居节点列表
        List<Integer> neighborIdList = new ArrayList<>(hashgraphMember.getIntraShardNeighborAddrs());
        // 邻居节点列表添加自己的id
        neighborIdList.add(hashgraphMember.getId());
        // 自己的哈希图高度
        HashMap<Integer, Integer> myHashgraphHeightMap = new HashMap<>(neighborIdList.size());
        this.hashgraphMember.getHashgraph().forEach((id, chain)->{
            if (chain.size() != 0) {
                myHashgraphHeightMap.put(id, chain.get(chain.size() - 1).getEventId());
            }else {
                myHashgraphHeightMap.put(id, 0);
            }

        });

        // 要发送的事件集合
        HashMap<Integer, List<Event>> subEventListMap = new HashMap<>();
        for (int i = 0; i < hashgraphMember.getNumNodes(); i++) {
            int nodeId = i;
            // 获得本地hashgraph副本高度
            int my_size = myHashgraphHeightMap.get(nodeId);
            // 获得请求者的hashgraph副本高度
            int guest_size = hashMap.get(nodeId);
            List<Event> chain = hashgraphMember.getHashgraph().get(nodeId);
            if (my_size > guest_size) {
                List<Event> subEventList = new ArrayList<>();
                for (int j = chain.size() - 1; j >= 0; j--) {
                    Event e = chain.get(j);
                    if (e.getEventId() > guest_size) {
                        subEventList.add(e);
                    }
                }
                subEventListMap.put(nodeId, subEventList);
            }
        }

        //转成json字符串格式
        String data = JSON.toJSONString(subEventListMap);
        response.setCode(200);
        response.setMessage("SUCCESS");
        response.setData(data);
    }

    private void pullEventByOtherShardMapping(Request request, Response response) {
        //String json = request.getData();
        //HashMap<Integer, Integer> hashMap = JSONObject.parseObject(json, HashMap.class);  //请求者的哈希图高度
        //  接收者的邻居节点列表
        List<Integer> neighborIdList = new ArrayList<>(hashgraphMember.getIntraShardNeighborAddrs());
        // 邻居节点列表添加自己的id
        neighborIdList.add(hashgraphMember.getId());
        // 如果自己不是某分片的leader，则拒绝处理该请求
        if (!hashgraphMember.getLeaderId().equals(hashgraphMember.getId())) {
            response.setCode(400);
            response.setMessage("请求节点不是Leader");
        }else {
            // 自己的哈希图高度
            // HashMap<Integer, Integer> myHashgraphHeightMap = new HashMap<>(neighborIdList.size());
            // 分片内 平行链上 最新的事件（代表未达成共识的事件）
            Map<Integer, Event> intraShardingLastEventMap = new HashMap<>();

            // 只发送自己分片内的平行链上的未达成共识的事件
            for (Integer nodeId : neighborIdList) {
                List<Event> chain = this.hashgraphMember.getHashgraph().get(nodeId);
                if (chain.size() != 0) {
                    intraShardingLastEventMap.put(nodeId, chain.get(chain.size()-1));
                }
            }
            String jsonData = JSON.toJSONString(intraShardingLastEventMap);
            response.setCode(200);
            response.setMessage("SUCCESS");
            response.setData(jsonData);
        }
    }

    private void sendTransactionMapping(Request request, Response response) {
        String json = request.getData();
        Transaction transaction = JSONObject.parseObject(json, Transaction.class);
        List<Transaction> waitForPackEventList = this.hashgraphMember.getWaitForPackEventList();
        synchronized (this.hashgraphMember.getLock()) {
            waitForPackEventList.add(transaction);
        }
        response.setCode(200);
        response.setMessage("SUCCESS");
    }

    // shared variables：  this.hashgraphMember.getHashgraph():ConcurrentHashMap(Integer, List<Event>)
    private void pullEventMapping(Request request, Response response) {
        String requestDataJSonString = request.getData();
        // Map<id:Integer, Chain.size():Integer>
        HashMap<Integer, Integer> hashMap;
        try {
            hashMap = JSONObject.parseObject(requestDataJSonString, HashMap.class);
            //System.out.println("接收到的Hashgraph平行链高度：" + hashMap);
        }catch (Exception e) {
            e.printStackTrace();
            response.setCode(400);
            response.setData("request parameter error: "+ request.getData());
            return;
        }
        generatePullEvents(hashMap, response);
        response.setCode(200);
        response.setMessage("SUCCESS");
    }


    private static String getRequestContent(BufferedReader reader) {
       /* StringBuilder  sb = new StringBuilder();
        String buff = null;
        try {
            while ((buff = reader.readLine()) != null) {
                sb.append(buff);
            }
        }catch (Exception e) {
            throw new BusinessException(e);
        }*/
        String content ;
        try {
            // 将所有的内容以一行输出
            content = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return content;
    }

    public static Request getRequestObject(BufferedReader reader) {
        String jsonContent = getRequestContent(reader);
        Request request = JSONObject.parseObject(jsonContent, Request.class);
        return request;
    }

    public static String  responseObject2JsonString(Response response) {
        return JSON.toJSONString(response);
    }

    public static String requestObject2JsonString(Request request) {
        return JSON.toJSONString(request);
    }

    public static Response getResponseObject(BufferedReader reader) {
        String content = getRequestContent(reader);
        return JSONObject.parseObject(content, Response.class);
    }

    private void  defaultMapping(Request request, Response response) {
        response.setCode(200);
        response.setMessage("SUCCESS");
        response.setData("server received this message: " + request.getData());
    }

    private void errorMapping(Request request, Response response) {
        response.setCode(400);
        response.setMessage("未找到路径");
    }

    private void shutdownMapping(Request request, Response response) {
        this.hashgraphMember.setShutdown(true);
        response.setCode(200);
        response.setMessage("SUCCESS");
    }

    private /*synchronized*/ void generatePullEvents(HashMap<Integer, Integer> hashMap, Response response) {
        ConcurrentHashMap<Integer, List<Event>> subEventList = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, List<Event>> entry : this.hashgraphMember.getHashgraph().entrySet()) {
            Integer id = entry.getKey();
            List<Event> chain = entry.getValue();
            //int n = this.hashgraphMember.getSnapshotHeightMap().get(id);
            // 历史长度
            //int my_size = chain.size() + n;

            int my_size ;
            if (chain.size() != 0) {
                my_size = chain.get(chain.size()-1).getEventId();
            }else {
                my_size = 0;
            }
            int guest_size;
            try {
                guest_size = hashMap.get(id);
            }catch (Exception e) {
                throw new BusinessException("取nodeId:"+ id + "hashheightMap:" + hashMap);
            }

            if (my_size > guest_size) {
//                subEventList.put(id, chain.subList(guest_size, my_size));  !!!!! 巨坑： watch out ! 会引发并发问题T_T T_T
                int gap = my_size - guest_size;
                List<Event> c = new ArrayList<>(gap);
                for (int i = chain.size()-gap; i < chain.size(); i++) {
                    c.add(chain.get(i));
                }
                subEventList.put(id, c);
            }
        }
        String res;
        res = JSON.toJSONString(subEventList);
        response.setData(res);
    }

}
