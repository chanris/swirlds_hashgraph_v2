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
public class NodeServer {

    private boolean shutdown = false;
    private  int port;
    private ExecutorService executor;
    private int threadNum; // 线程池数量
    private HashgraphMember hashgraphMember;

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

        new Thread(()->{
            // hashgraph 使用的 八卦协议 拉的方式：即 节点向随机邻居节点发送请求，邻居节点发送哈希图给原节点，
            // 源节点在本地创建新事件，并指向此次通信的邻居节点
            log.info("node_{}_gossip_sync_thread  startup success!", this.hashgraphMember.getId());
            try {
                gossipSync();
            } catch (Exception e) {
                throw new BusinessException(e);
            }
        }, "node_" + port + "_gossip_sync_thread").start();

        if (this.hashgraphMember.getId() == 0) {
            ChartUtils.showTPS(this.hashgraphMember.getHashgraph(), this.hashgraphMember.getSnapshotHeightMap());
        }
    }

    private void gossipSync() throws Exception{
        while (!shutdown) {
            // 间隔 1 ~ 1.5s 发起一次通信
            int time = new Random(System.currentTimeMillis() / (this.hashgraphMember.getId() + 1)
                    + this.hashgraphMember.getId() * 1000).nextInt(700) + 300;
            TimeUnit.MILLISECONDS.sleep(time);

            // 选择邻居节点
            int receiverId = (int)(Math.random() * this.hashgraphMember.getNumNodes());
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
                    int n = this.hashgraphMember.getSnapshotHeightMap().get(id);
                    hashgraphHeightMap.put(id, n + chain.size());
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
                    this.hashgraphMember.getEventHashMap().put(SHA256.sha256HexString(JSON.toJSONString(event)), event);

                     this.hashgraphMember.divideRounds();
                     this.hashgraphMember.decideFame();
                    //this.hashgraphMember.findOrder();
                    this.snapshot();

                }else {
                    log.warn("node_id:{} request node_id:{} gossip communication failed!", this.hashgraphMember.getId(), receiverId);
                }
            }catch (Exception e) {
                throw new BusinessException(e);
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

    /// 存储冗余
    // 削减Hashgraph的大小
    // 如果一个轮次之前的所有事件的consensusTimestamp全部被确定，那么就可以从Hashgraph上剪掉
    private void snapshot() {
        // 如果轮次次数大于等于6，那么开始削减Hashgraph的大小
        int size1 = this.hashgraphMember.getWitnessMap().keySet().size();
        if (size1 < 6) {
            return;
        }

        // 获得次最小的round编号
        List<Integer> rounds = new ArrayList<>(this.hashgraphMember.getWitnessMap().keySet());
        Collections.sort(rounds);
        int r = rounds.get(1);
        //todo 判断次最小轮次的全部祖先事件是否最终定序，如果为true, 那么从内存中删除全部祖先事件。持久化磁盘中。
        List<Event> witnessList = this.hashgraphMember.getWitnessMap().get(r);
        for (int n = 0; n < this.hashgraphMember.getNumNodes(); n++) {
            Event event = witnessList.get(n);
            if (!isAllEventFindOrder(event)) {
                return;
            }
        }

        int size = witnessList.size();
        if (size == this.hashgraphMember.getNumNodes()) {
            ArrayList<Event> removeSubEventList = new ArrayList<>();
            this.hashgraphMember.getHashgraph().forEach((id, chain)->{
                witnessList.sort(Comparator.comparingInt(Event::getNodeId));
                Iterator<Map.Entry<String, Event>> iterator = this.hashgraphMember.getEventHashMap().entrySet().iterator();
                for (Event e : chain) {
                    while (iterator.hasNext()) {
                        Map.Entry<String, Event> entry = iterator.next();
                        if (entry.getValue().equals(e)) {
                            iterator.remove();
                        }
                    }
                    if (e == witnessList.get(id)) {
                        break;
                    } else {
                        removeSubEventList.add(e);
                    }
                }
                // hashgraph：HashMap<Integer,List<Event>> 中删除事件
                chain.removeAll(removeSubEventList);
                int n = this.hashgraphMember.getSnapshotHeightMap().get(id) + removeSubEventList.size();
                this.hashgraphMember.getSnapshotHeightMap().put(id, n);
                removeSubEventList.clear();
            });

            // witnessMap<Integer,List<Event>> 中删除最小轮次的见证人列表
            this.hashgraphMember.getWitnessMap().remove(r-1);
            // 并将次最小轮次的见证人 的selfParent和otherParent引用设为null
            for (Event witness : witnessList) {
                witness.setSelfParent(null);
                witness.setOtherParent(null);
                witness.setNeighbors(null);
            }
        }
    }

    /**
     * 判断一个 witness event 的自祖先事件是否全部定序
     * @param witness
     * @return
     */
    private boolean isAllEventFindOrder(Event witness) {
        /*if (witness.getSelfParent() != null) {
            if (witness.getSelfParent().getConsensusTimestamp() != null) {
                boolean b = isAllEventFindOrder(witness.getSelfParent());
                return b;
            }
        }*/
        return true;
    }

    private HashMap<Integer,Integer> getHashgraphHeight() {
        HashMap<Integer, Integer> hash = new HashMap<>(this.hashgraphMember.getNumNodes());
        this.hashgraphMember.getHashgraph().forEach((id, chain)->{
            hash.put(id, chain.size());
        });
        return hash;
    }

}
