package com.cystrix.hashgraph.net;

import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.HashgraphMember;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
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
                request.setMapping("/requestPullData");
                writer.println(RequestHandler.requestObject2JsonString(request));
                Response response = RequestHandler.getResponseObject(reader);
                System.out.println(response);
            }catch (Exception e) {
                throw new BusinessException(e);
            }
            //log.info("node_id:{} send message num :{} , received message num: {}",this.hashgraphMember.getId(),  sendMessageNum.get(), receivedMessageNum.get());
        }
    }

}
