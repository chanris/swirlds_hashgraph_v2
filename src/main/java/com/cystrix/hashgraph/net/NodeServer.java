package com.cystrix.hashgraph.net;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


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

//        new Thread(()->{
//            try {
//                snapshot();
//            } catch (InterruptedException e) {
//                throw new BusinessException(e);
//            }
//        }, "node_"+port+"_gossip_sync_thread").start();


        new Thread(()->{

            HashMap<Integer, Integer> height1Map =  getHashgraphHeight();
            try {
                TimeUnit.MILLISECONDS.sleep(10 * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            HashMap<Integer, Integer> height2Map = getHashgraphHeight();
            AtomicInteger gapCount = new AtomicInteger(0);
            height1Map.forEach((id, size)->{
                gapCount.addAndGet(height2Map.get(id) - size);
            });
            System.out.println("************************************************");
            System.out.println("system throughput is: "+ ((gapCount.get() * 10) / 5));
            System.out.println("************************************************");
        }, "node_"+port+"_test_throughput_thread").start();
    }

    private void gossipSync() throws Exception{
        while (!shutdown) {
            // 间隔 100 ~ 150 ms 发起一次通信
            int time = new Random(System.currentTimeMillis() / (this.hashgraphMember.getId() + 1)
                    + this.hashgraphMember.getId() * 100).nextInt(5000) + 100;
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
                    hashgraphHeightMap.put(id, chain.size());
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
                    // System.out.println("event"+ event);
                    // 打包目前接收到的交易
                    // packTransactionList(event);
                    // for search parent hash
                    this.hashgraphMember.getEventHashMap().put(SHA256.sha256HexString(JSON.toJSONString(event)), event);

//                    System.out.println("******************************************************************************************");
//                    System.out.println("node_id：" + nodeId + " 的hashgraph副本");
//                    List<Event> events = this.hashgraphMember.getHashgraph().get(0);
//                    System.out.println(events);
//
//                    System.out.println("******************************************************************************************");

                     this.hashgraphMember.divideRounds();
                     //this.hashgraphMember.decideFame2();
                    //this.hashgraphMember.findOrder();

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

    /// 存储冗余
    // 削减Hashgraph的大小
    // 如果一个轮次之前的所有事件的consensusTimestamp全部被确定，那么就可以从Hashgraph上剪掉
    private void snapshot() throws InterruptedException {
        while (!shutdown) {
            int idleTime = 2 * 1000;
            TimeUnit.MILLISECONDS.sleep(idleTime);
            int size1 = this.hashgraphMember.getWitnessMap().keySet().size();
            if (size1 < 4) {
                continue;
            }

            ArrayList<Event> removeSubEventList = new ArrayList<>();
            List<Integer> rounds = new ArrayList<>(this.hashgraphMember.getWitnessMap().keySet());
            Collections.sort(rounds);
            int i;
            if (rounds.size() > 2) {
                i = rounds.get(2);
            }else {
                return;
            }
            List<Event> witnessList = this.hashgraphMember.getWitnessMap().get(i);
            for (int n = 0; n < this.hashgraphMember.getNumNodes(); n++) {
                Event event = witnessList.get(n);
                if (!isAllEventFindOrder(event)) {
                    return;
                }
            }

            int size = witnessList.size();
            if (size == this.hashgraphMember.getNumNodes()) {
                List<List<Event>> values = this.hashgraphMember.getHashgraph().values().stream().collect(Collectors.toList());;
                for (int j = 0; j < size; j++) {
                    List<Event> events = values.get(j);
                    synchronized (events) {
                        for (Event e : events) {
                            if (e == witnessList.get(j)) {
                                break;
                            }else {
                                removeSubEventList.add(e);
                            }
                        }
                        events.removeAll(removeSubEventList);
                    }
                }
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
