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

            // 选择邻居节点
            int receiverId = r.nextInt(Integer.MAX_VALUE);
            receiverId %= this.hashgraphMember.getNumNodes();
            if (receiverId == this.hashgraphMember.getId()) {
                receiverId ++;
                receiverId %= this.hashgraphMember.getNumNodes();
            }

            try (Socket socket = new Socket("127.0.0.1", receiverId + 8080);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);){
                Request request = new Request();
                request.setCode(200);
                request.setMapping("/pullEvent");
                Map<Integer, Integer> hashgraphHeightMap = new HashMap<>();
                this.hashgraphMember.getHashgraph().forEach((id, chain)->{
                    //int n = this.hashgraphMember.getSnapshotHeightMap().get(id);
                    //hashgraphHeightMap.put(id, n + chain.size());
                    if (chain.size() != 0) {
                        hashgraphHeightMap.put(id, chain.get(chain.size()-1).getEventId());
                    }else {
                        hashgraphHeightMap.put(id, 0);
                    }

                });
                request.setData(JSONObject.toJSONString(hashgraphHeightMap));
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
//                     packTransactionList(event);
                    packTransactionListMock(event);
                    // for search parent hash
                    this.hashgraphMember.getHashEventMap().put(SHA256.sha256HexString(JSON.toJSONString(event)), event);
//                    this.hashgraphMember.divideRounds();
//                    this.hashgraphMember.decideFame();
//                    this.hashgraphMember.findOrder();

                    this.hashgraphMember.snapshot();
                    if (nodeId == 0) {
                        List<Integer> height = new ArrayList<>();
                        for (int i = 0; i < this.hashgraphMember.getNumNodes(); i++) {
                            height.add(this.hashgraphMember.getHashgraph().get(i).size());
                        }
                        System.out.println("node_id: 0" + height);
                    }
                }else {
                    log.warn("node_id:{} request node_id:{} gossip communication failed!", this.hashgraphMember.getId(), receiverId);
                }
            }catch (Exception e) {
                throw new BusinessException(e);
            }
        }
    }

    private void handleHashgraph() {
        while (!shutdown) {
            try {
                int idleTime = 2000; //ms
                TimeUnit.MILLISECONDS.sleep(idleTime);

                this.hashgraphMember.divideRounds();
                this.hashgraphMember.decideFame();
                this.hashgraphMember.findOrder();
            }catch (Exception ex) {
                throw  new BusinessException(ex);
            }
        }
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
