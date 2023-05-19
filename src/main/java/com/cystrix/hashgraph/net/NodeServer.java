package com.cystrix.hashgraph.net;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.Event;
import com.cystrix.hashgraph.hashview.HashgraphMember;
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
    }

    private void gossipSync() throws Exception{
        while (!shutdown) {
            // 间隔 100 ~ 150 ms 发起一次通信
            int time = new Random(System.currentTimeMillis() / (this.hashgraphMember.getId() + 1)
                    + this.hashgraphMember.getId() * 100).nextInt(100) + 50;
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
                    this.hashgraphMember.addEventBatch(subEventListMap);
                    int nodeId = this.hashgraphMember.getId();
                    // 创建新事件，打包目前接收到的交易
                    Event event = packNewEvent(nodeId, receiverId);

                    // for search parent hash
                    this.hashgraphMember.getEventHashMap().put(SHA256.sha256HexString(JSON.toJSONString(event)), event);
                    // log.info("node_id:{} request node_id:{} gossip communication success!", this.hashgraphMember.getId(), receiverId);
                    if (nodeId == 0) {
                         //log.info("node_Id:{} hashgraph replicas: {}", this.hashgraphMember.getId(), this.hashgraphMember.getHashgraph());

                        List<Integer> chainSizeList = new ArrayList<>();
                        this.hashgraphMember.getHashgraph().forEach((id, c)->{
                            chainSizeList.add(c.size());
                        });
                        log.info("node_Id:{} hashgraph replicas: {}", this.hashgraphMember.getId(), chainSizeList);
                    }
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
            Event chainLastEvent = chain.get(chain.size()-1);
            Event otherChainLastEvent = otherChain.get(otherChain.size()-1);
            Event event = new Event();
            event.setNodeId(nodeId);
            event.setOtherId(otherId);
            event.setTimestamp(System.currentTimeMillis());
            event.setSelfParentHash(SHA256.sha256HexString(JSON.toJSONString(chainLastEvent)));
            event.setOtherParentHash(SHA256.sha256HexString(JSON.toJSONString(otherChainLastEvent)));
            event.setSelfParent(chainLastEvent);
            event.setOtherParent(otherChainLastEvent);
            event.setPacker(this.hashgraphMember.getPk());
            String signature = SHA256.signEvent(event, this.hashgraphMember.getSk());
            event.setSignature(signature);
            chain.add(event);
            return event;
        }catch (Exception e) {
            throw new BusinessException(e);
        }

    }

}
