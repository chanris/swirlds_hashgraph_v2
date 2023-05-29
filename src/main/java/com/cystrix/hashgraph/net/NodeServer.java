package com.cystrix.hashgraph.net;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.cystrix.chart.ChartUtils;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.Event;
import com.cystrix.hashgraph.hashview.HashgraphMember;
import com.cystrix.hashgraph.hashview.Transaction;
import com.cystrix.hashgraph.util.SHA256;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Data
@ToString(of = {"hashgraphMember"})
public class NodeServer {

    private boolean shutdown = false;
    private  int port;
    private ExecutorService executor;
    private int threadNum; // 线程池数量
    private HashgraphMember hashgraphMember;

    private AtomicInteger trigger = new AtomicInteger(0);

    public NodeServer(int port, int threadNum, HashgraphMember hashgraphMember) {
        this.port = port;
        this.executor = Executors.newFixedThreadPool(threadNum);
        this.threadNum = threadNum;
        this.hashgraphMember = hashgraphMember;
    }

    public void startup() {
        new Thread(()->{
            try (ServerSocket serverSocket = new ServerSocket(port)){
                log.info("node_id:{} node_name:{} port: {}  startup success!", this.hashgraphMember.getId(),
                        this.hashgraphMember.getName(), port);
                while (!shutdown) {
                    Socket clientSocket = serverSocket.accept();
                    executor.execute(new Task(clientSocket, hashgraphMember));
                    if (this.hashgraphMember.isShutdown()) {
                        this.shutdown = true;
                    }
                }
                executor.shutdown();
            }catch (Exception e) {
                throw new BusinessException(e);
            }
        },"node_" + port + "_await_request_thread").start();

        Thread t1 = new Thread(()->{
            // hashgraph 使用的 八卦协议 拉的方式：即 节点向随机邻居节点发送请求，邻居节点发送哈希图给原节点，
            // 源节点在本地创建新事件，并指向此次通信的邻居节点
            log.info("node_{}_gossip_sync_thread  startup success!", this.hashgraphMember.getId());
            try {
                gossipSync();
            } catch (Exception e) {
                throw new BusinessException(e);
            }
        }, "node_" + port + "_gossip_sync_thread");
        t1.start();

        if (this.hashgraphMember.getId() == 0) {
            ChartUtils.showTPS(this.hashgraphMember);
        }
    }

    private void gossipSync() throws Exception{
        while (!shutdown) {
            // 间隔 1 ~ 1.5s 发起一次通信
            Random r = new Random(System.currentTimeMillis() / (this.hashgraphMember.getId() + 1)
                    + this.hashgraphMember.getId() * 1000);
            int time = r.nextInt(700) + 500;
            TimeUnit.MILLISECONDS.sleep(time);

            // shard
            // 选择邻居节点
            int receiverId = r.nextInt(Integer.MAX_VALUE);
            boolean isLeader = this.hashgraphMember.getLeaderId().equals(this.hashgraphMember.getId());
            if (isLeader) {
               receiverId %= this.hashgraphMember.getLeaderNeighborAddrs().size();
               receiverId = this.hashgraphMember.getLeaderNeighborAddrs().get(receiverId);
            }else {
               receiverId %= this.hashgraphMember.getIntraShardNeighborAddrs().size();
               receiverId = this.hashgraphMember.getIntraShardNeighborAddrs().get(receiverId);
            }

            //non-shard
            // 选择邻居节点
       /*     int receiverId = r.nextInt(Integer.MAX_VALUE);
            receiverId %= this.hashgraphMember.getNumNodes();
            if (receiverId == this.hashgraphMember.getId()) {
                receiverId ++;
                receiverId %= this.hashgraphMember.getNumNodes();
            }*/

            try (Socket socket = new Socket("127.0.0.1", receiverId + 8080);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)){
                Request request = new Request();
                request.setCode(200);

                // 首先判断自己是否为leader节点， 再判断receiver 是否为领导人节点
                if (isLeader && !this.hashgraphMember.getIntraShardNeighborAddrs().contains(new Integer(receiverId))) {
                    // 接收其他分片内最新未达成共识的事件
                    request.setMapping("/pullEventByOtherShard");
                    // 写入请求
                    writer.println(RequestHandler.requestObject2JsonString(request));
                    // 获得响应信息
                    Response response = RequestHandler.getResponseObject(reader);
                    if (response.getCode() == 200) {
                        String data = response.getData();
                        HashMap<Integer, Event> otherShardLastEventWithoutConsensus = JSON.parseObject(data, new TypeReference<HashMap<Integer, Event>>() {
                            @Override
                            public HashMap<Integer, Event> parseObject(String text) {
                                return super.parseObject(text);
                            }
                        });

                        // 写入本地hashgraph中
                        otherShardLastEventWithoutConsensus.forEach((nodeId, event)->{
                            this.hashgraphMember.getHashgraph().get(nodeId).add(event);
                        });
                        // 创建新事件
                        try {
                            packNewEventFromRefOtherShardEvent(this.hashgraphMember.getId(), receiverId);
                        } catch (NoSuchAlgorithmException e) {
                            log.warn("写入新事件失败");
                            throw new BusinessException(e);
                        }
                    }
                }else {
                    // receiver 为分片内节点
                    request.setMapping("/pullEventBySelfShard");
                    // 记录当前hashgraph副本的高度
                    Map<Integer, Integer> hashgraphHeightMap = new HashMap<>();
                    this.hashgraphMember.getHashgraph().forEach((id, chain)->{
                        if (chain.size() != 0) {
                            hashgraphHeightMap.put(id, chain.get(chain.size()-1).getEventId());
                        }else {
                            hashgraphHeightMap.put(id, 0);
                        }

                    });
                    request.setData(JSONObject.toJSONString(hashgraphHeightMap));
                    //log.info("发送的高度数据:{}", request.getData());
                    writer.println(RequestHandler.requestObject2JsonString(request));
                    Response response = RequestHandler.getResponseObject(reader);

                    if (response.getCode() == 200) {
                        String data = response.getData();
                        HashMap<Integer, List<Event>> subEventListMap = JSON.parseObject(data, new TypeReference<>() {
                            @Override
                            public HashMap<Integer, List<Event>> parseObject(String text) {
                                return super.parseObject(text);
                            }
                        });
                        boolean resultSign = this.hashgraphMember.addEventBatch(subEventListMap);

                        int nodeId = this.hashgraphMember.getId();
                        // 创建新事件
                        Event event = packNewEvent(nodeId, receiverId);
                        // 打包目前接收到的交易
                        packTransactionListMock(event);

                        // for search parent hash
                        //this.hashgraphMember.getHashEventMap().put(SHA256.sha256HexString(JSON.toJSONString(event)), event);
                    }
                }

                this.hashgraphMember.snapshot();
                if (this.hashgraphMember.getId() == 0) {
                    List<Integer> height = new ArrayList<>();
                    for (int i = 0; i < this.hashgraphMember.getNumNodes(); i++) {
                        height.add(this.hashgraphMember.getHashgraph().get(i).size());
                    }
                     log.info("node_id: 0 hashgraph height: {} ,neighborsList: {}" , height, this.hashgraphMember.getIntraShardNeighborAddrs());
                }
            }catch (Exception e) {
                log.warn("node_id:{} request node_id:{} gossip communication failed!", this.hashgraphMember.getId(), receiverId);
                throw new BusinessException(e);
            }
        }
    }


    private void  packNewEventFromRefOtherShardEvent(int selfNodeId, int otherNodeId) throws NoSuchAlgorithmException {
        Event e = new Event();
        List<Event> selfChain = this.hashgraphMember.getHashgraph().get(selfNodeId);
        List<Event> otherChain = this.hashgraphMember.getHashgraph().get(otherNodeId);
        Event selfParentEvent = selfChain.get(selfChain.size() - 1);
        Event otherParentEvent = otherChain.get(otherChain.size()-1);
        e.setNeighbors(new ArrayList<>(selfChain));
        e.setSelfParent(selfParentEvent);
        e.setSelfParentHash(SHA256.sha256HexString(JSON.toJSONString(selfParentEvent)));
        e.setOtherParent(otherParentEvent);
        e.setOtherParentHash(SHA256.sha256HexString(JSON.toJSONString(otherParentEvent)));
        e.setTimestamp(System.currentTimeMillis());
        e.setEventId(selfParentEvent.getEventId() + 1);
        e.setNodeId(selfNodeId);
        e.setOtherId(otherNodeId);
        e.setPacker(this.hashgraphMember.getPk());
        String signature = SHA256.signEvent(e, this.hashgraphMember.getSk());
        e.setSignature(signature);
        selfChain.add(e);
    }



    private synchronized Event packNewEvent(int nodeId, int receiverId) {
        try {
            // 创建新事件，打包目前接收到的交易
            int otherId = receiverId;
            List<Event> chain = this.hashgraphMember.getHashgraph().get(nodeId);
            List<Event> otherChain = this.hashgraphMember.getHashgraph().get(otherId);
            List<Event> neighbors = new ArrayList<>(2);
            Event event = new Event();
            synchronized (otherChain) {
                Event chainLastEvent = chain.get(chain.size()-1);
                Event otherChainLastEvent = otherChain.get(otherChain.size()-1);
                neighbors.add(chainLastEvent);
                neighbors.add(otherChainLastEvent);
                event.setEventId(chainLastEvent.getEventId() + 1);
                event.setNodeId(nodeId);
                event.setOtherId(otherId);
                event.setTimestamp(System.currentTimeMillis());
                event.setSelfParentHash(SHA256.sha256HexString(JSON.toJSONString(chainLastEvent)));
                event.setOtherParentHash(SHA256.sha256HexString(JSON.toJSONString(otherChainLastEvent)));
                event.setSelfParent(chainLastEvent);
                event.setOtherParent(otherChainLastEvent);
                event.setPacker(this.hashgraphMember.getPk());
                event.setNeighbors(neighbors);
                String signature = SHA256.signEvent(event, this.hashgraphMember.getSk());
                event.setSignature(signature);
                chain.add(event);
            }
            return event;
        }catch (Exception e) {
            throw new BusinessException(e);
        }

    }

    private synchronized void packTransactionList(Event newEvent) {
        int maxTxNum = 10;
        ArrayList<Transaction> packTransactionList = new ArrayList<>(maxTxNum);
        List<Transaction> txList = this.hashgraphMember.getWaitForPackEventList();
        int size = txList.size();
        maxTxNum = Math.min(size, maxTxNum);
        if (maxTxNum != 0 ) {
            for (int i = 0; i < maxTxNum; i++) {
                packTransactionList.add(txList.get(i));
            }
            txList.removeAll(packTransactionList);
            newEvent.setTransactionList(packTransactionList);
        }
    }

    private void packTransactionListMock(Event newEvent) {
        ArrayList<Transaction> packTransactionList = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            Transaction transaction = new Transaction();
            transaction.setSender(hashgraphMember.getPk());
            transaction.setSignature(SHA256.signTransaction(transaction, this.hashgraphMember.getSk()));
            transaction.setBalance(1000L);
            transaction.setTimestamp(System.currentTimeMillis());
            transaction.setReceiver(hashgraphMember.getPk());
            packTransactionList.add(transaction);
        }
        newEvent.setTransactionList(packTransactionList);
    }




    private HashMap<Integer,Integer> getHashgraphHeight() {
        HashMap<Integer, Integer> hash = new HashMap<>(this.hashgraphMember.getNumNodes());
        this.hashgraphMember.getHashgraph().forEach((id, chain)->{
            hash.put(id, chain.size());
        });
        return hash;
    }

    public HashgraphMember getHashgraphMember() {
        return hashgraphMember;
    }
}
