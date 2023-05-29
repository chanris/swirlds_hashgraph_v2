package com.cystrix.hashgraph.hashview;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.search.DFS;
import com.cystrix.hashgraph.util.SHA256;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@Data
@ToString(of = {"leaderId", "neighborNodeAddrs"})
public class HashgraphMember {
    {
        Map<String, String> keyPair = SHA256.generateKeyPairBase64();
        PK = keyPair.get("PK");
        SK = keyPair.get("SK");
    }
    private Integer id;
    private  String name;
    private Integer leaderId;
    private ConcurrentHashMap<Integer, List<Event>> hashgraph; // 以map的方式保留hashgraph 副本
    private int numNodes; // 成员节点总数
    private boolean shutdown = false;
    private  final String PK;
    private  final String SK; // sk base64 encode
    private List<Transaction> waitForPackEventList;
    private ConcurrentHashMap<Integer, List<Event>> witnessMap; // 存放每轮的见证者
    private ConcurrentHashMap<String, Event> hashEventMap;   // 将event的hash值作为key， event事件作为key，方便构造事件之间的引用关系

    private Integer maxRound = 0;
    private int coinRound = 10;

    private final Object lock = new Object();
    private Integer consensusEventNum = 0;

    private Integer transactionNum = 10;
    private List<Integer> intraShardNeighborAddrs;
    private List<Integer> leaderNeighborAddrs;  // 当前epoch内 节点的邻居节点地址，地址格式：ip:port。由于都是本地模拟，存储端口号即可


    private BigDecimal nodeStatusComprehensiveEvaluationValue; //存储当前节点在当前epoch的的权值

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
        this.hashEventMap = new ConcurrentHashMap<>();
        this.witnessMap = new ConcurrentHashMap<>();
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
        e.setEventId(1);

        this.hashgraph.get(id).add(e);
        String eventHash = null;
        try {
            eventHash = SHA256.sha256HexString(JSON.toJSONString(e));
            this.hashEventMap.put(eventHash, e);
        }catch (Exception ex) {
            throw new BusinessException(ex);
        }
    }

    // todo 没有考虑拜占庭节点行为
    public boolean addEventBatch(HashMap<Integer, List<Event>> subHashgraph) {
        subHashgraph.forEach((id, subChain)->{
            this.hashgraph.get(id).addAll(subChain);
        });
        return true;
    }

    public void divideRounds() {
        AtomicInteger maxLen = new AtomicInteger(0);
        List<Integer> chainSizeList = new ArrayList<>(this.numNodes);
        // 获得当前最长链长度，并记录每条链的长度
        this.getHashgraph().forEach((id, chain)->{
            maxLen.set(Math.max(chain.size(), maxLen.get()));
            chainSizeList.add(chain.size());
        });

        // 层次遍历hashgraph，确定每个事件的轮次
        for (int i = 0; i < maxLen.get(); i++) {
            for (int j = 0; j < this.numNodes; j++) {
                // 若该坐标存在事件，则判断其轮次
                if (chainSizeList.get(j) > i) {
                    Event e =  this.hashgraph.get(j).get(i);
                    if (e.getCreatedRound() != null) {
                        continue;
                    }
                    Integer r;
                    if (i == 0) {
                        e.setIsWitness(true);
                        e.setCreatedRound(1);
                        if (!this.witnessMap.containsKey(1)) {
                            this.witnessMap.put(1, new ArrayList<>());
                        }
                        this.witnessMap.get(1).add(e);
                    }else {
                        Event selfParentEvent = e.getSelfParent();
                        r = selfParentEvent.getCreatedRound();
                        if (r == null) {
                            throw new BusinessException("自父亲事件的创建轮次不能为空");
                        }
                        AtomicInteger vote = new AtomicInteger(0);
                        this.witnessMap.get(r).forEach(witness->{
                            if (isStronglySee(e, witness)) {
                                vote.getAndIncrement();
                            }
                        });
                        if (vote.get() > (2 * numNodes / 3)) {
                            e.setIsWitness(true);
                            e.setCreatedRound(r+1);
                            maxRound = Math.max(maxRound, r + 1);
                            if (!witnessMap.containsKey(r + 1)) {
                                witnessMap.put(r+1, new ArrayList<>());
                            }
                            witnessMap.get(r+1).add(e);
                        }else {
                            e.setIsWitness(false);
                            e.setCreatedRound(r);
                        }
                    }

                }
            }
        }
    }

    public void decideFame() {
        // 遍历每轮的见证人
        List<Event> eventList = new ArrayList<>();
        for (List<Event> events: this.getWitnessMap().values()) {
           eventList.addAll(events);
        }

        for(int i = 0; i < eventList.size(); i ++) {
            for(int j = 0; j < eventList.size(); j ++) {
            Event x = eventList.get(i);
            Event y = eventList.get(j);
            // if x.witness and y.witness and y.round > x.round
            if (x.getCreatedRound() < y.getCreatedRound()) {
                // d <- y.round - x.round
                int d = y.getCreatedRound() - x.getCreatedRound();
                // s <- the set of witness events in round y.round - 1 that y can strongly see
                List<Event>  witness = filterWitnessList(y, this.witnessMap.get(y.getCreatedRound()-1));
                // v <- majority vote in s  (is TRUE for a tie)
                // t <- number of events in s with a vote of v
                AtomicInteger t = new AtomicInteger();
                boolean v = getMajorityVote(witness, t);
                if (d == 1) {
                   // y.vote <- can y see x ?
                   y.setVoteRes(isSee(y, x));
                }else {
                    if ( d % coinRound > 0) {
                        if (t.get() > (2 * this.numNodes / 3)) {
                            x.setIsFamous(v);
                            y.setVoteRes(v);
                            break;
                        }
                    }else {
                        if (t.get() > (2 * this.numNodes / 3)) {
                            y.setVoteRes(v);
                        }else {
                            y.setVoteRes(voteBySignature(y.getSignature()));
                        }
                    }
                }
            }
           }
        }
    }

    public void findOrder() throws NoSuchAlgorithmException {
        // if there is a round r such that there is no event y in or before round r that has y.witness=TRUE
        // and y.famous = UNDECIDED
        // and x is an ancestor of every round r unique famous witness
        // and this is not true of any round earlier thant r
        // then
        //      x.roundReceived <- r
        //      s <- set of each event z such that z is a self-ancestor of a round r unique famous witness,
        //           ,and x is an ancestor of z but not of the self-parent of z
        //           x.consensusTimestamp <- median of the timestamps of all the events in s
        // 解释：如果存在一个轮次 r， 如果r 和 r之前轮次的见证人都已经确定 声望
        // 且 x 是 r轮次的所以著名见证人的祖先
        // 且 没有 r之前的轮次能够满足以上两个条件
        // 那么
        //      x的接收轮次就是 r
        //      集合s <- r轮的著名见证人的自祖先z, 并且要求z的自父亲不能是x
        AtomicInteger maxLen = new AtomicInteger(0);
        List<Integer> chainSizeList = new ArrayList<>(this.numNodes);
        // 获得当前最长链长度，并记录每条链的长度
        this.getHashgraph().forEach((id, chain)->{
            maxLen.set(Math.max(chain.size(), maxLen.get()));
            chainSizeList.add(chain.size());
        });

        List<Event> consensusEventList = new ArrayList<>();
        // 层次遍历hashgraph，确定每个事件的接收轮次
        for (int i = 0; i < maxLen.get(); i++) {
            for (int j = 0; j < this.numNodes; j++) {
                if (chainSizeList.get(j) > i) {
                    Event e =  this.hashgraph.get(j).get(i);
                    List<Event> witness = findAllFamousWitnessCanStronglySeeX(e);
                    if (witness != null) {
                        //System.out.println("事件" + e+ "找到了接收轮次" + witness);
                        getConsensusTimestamp(e, witness);

                        // 是否可以在hashgraph 删除该事件 ？
                        consensusEventList.add(e);
                    }
                }
            }
        }

        // snapshot
        for (Event e: consensusEventList) {
            this.hashgraph.get(e.getNodeId()).remove(e);
            //this.snapshotHeightMap.put(e.getNodeId(), this.snapshotHeightMap.get(e.getNodeId()) + 1);
            this.hashEventMap.remove(SHA256.sha256HexString(JSONObject.toJSONString(e)));
            if (e.getIsWitness()) {
                this.witnessMap.get(e.getCreatedRound()).remove(e);
            }
            consensusEventNum += 1;
        }

       /* if (getId() == 0) {
            System.out.println("**********************************************************************************************************");
            System.out.println("node_id:" + getId() + " hashgraph " + this.consensusEventNum);
            System.out.println("**********************************************************************************************************");
        }*/
    }


    public void snapshot2() {

    }

    public void snapshot() {
        // 削减hashgraph 的大小
        // 削减hashEventMap
        // 记录打包交易的数量
        List<Integer> heightList = new ArrayList<>(this.numNodes); // 存储当前hashgraph的平行链高度
        this.hashgraph.forEach((id,chain)->{
            heightList.add(chain.size());
        });
        List<Integer> neighborIdList = new ArrayList<>();
        neighborIdList.addAll(this.intraShardNeighborAddrs);
        neighborIdList.add(this.id);
        int threshold = 30;

        // 削减hashgraph
        this.hashgraph.forEach((id,chain)->{
            if (neighborIdList.contains(id)) {
                    if (chain.size() > threshold) {
                        log.debug("SNAPSHOT!!!************************** neigbors:{}", neighborIdList);
                        List<Event> subRemoveList = new ArrayList<>(10);
                        int size = chain.size() * 2 / 3;
                        for (int i = 0; i < size; i++) {
                            subRemoveList.add(chain.get(i));
                        }
                        chain.removeAll(subRemoveList);
                    }
            }else {
                int size = chain.size();  // > 50  - 40
                if (size >= 20) {
                    Event lastEvent = chain.get(size-1);
                    chain = new ArrayList<>();
                    chain.add(lastEvent);
                }
            }
        });

    }


    // ********************************** private method area ***************************************
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
    private List<Event> findAllFamousWitnessCanStronglySeeX(Event e) {
        // 遍历所有的轮次的见证者
        AtomicInteger targetRound = new AtomicInteger(0);
        try {
            this.witnessMap.forEach((r, witnessList)->{
                boolean flag = true;
                if (witnessList.size() != this.getNumNodes()) {
                    flag = false;
                }else {
                    for (Event witness:witnessList) {
                        if (witness.getIsFamous() == null) {
                            flag = false;
                            break;
                        }
                    }
                }
                // 只遍历全部见证人都确定声望的轮次
                if (flag) {
                    // 只遍历大于事件e的创建轮次的轮次
                    if (e.getCreatedRound() < r) {

                        //  如何 判断witnessList是否为e的接收轮次， 并第一次找到接收轮次后就不在进行
                        for (Event witness : witnessList) {
                            if (witness.getIsFamous() && !isStronglySee(witness, e)) {
                                return;
                            }
                        }
                        targetRound.set(r);
                        throw new BusinessException();
                    }
                }
            });
        }catch (Exception ex) {}
        if (targetRound.get() != 0) {
            e.setReceivedRound(targetRound.get());
            return this.witnessMap.get(targetRound.get());
        }
        return null;
    }

    /**
     * @param e 待定序事件
     * @param witnessList 其接收轮次见证者
     */
    private void getConsensusTimestamp(Event e, List<Event> witnessList) {
        List<Event> s = new ArrayList<>();
        try {
            witnessList.forEach(witness->{
                // 如果是著名见证人则找到 z 事件
                if (witness.getIsFamous()) {
                    List<List<Event>> allPaths = DFS.findAllPaths(witness, e);
                    for (int i = 0; i < allPaths.size(); i++) {
                        List<Event> path = allPaths.get(i);
                        if (path.size() > 2) {
                            /*if (Objects.equals(path.get(path.size() - 2).getNodeId(), witness.getNodeId())) {
                                s.add(witness);
                            }*/
                            int a = s.size();
                            for (int j = 1; j < path.size()-1; j++) {
                                if (Objects.equals(path.get(j).getNodeId(), witness.getNodeId())) {
                                    s.add(witness);
                                }
                            }
                            int b = s.size();
                            if (a == b) {
                                s.add(witness);
                            }
                        }
                    }
                    if (s.size() == 0) {
                        int x = 0;
                        throw new RuntimeException("s集合的大小不能为0！");
                    }
                }

            });

            s.sort((o1, o2) -> (int)(o1.getTimestamp() - o2.getTimestamp()));
            Long consensusTimestamp = s.get(s.size() / 2).getTimestamp();
            e.setConsensusTimestamp(consensusTimestamp);
        }catch (Exception ex) {
            throw  new BusinessException(ex);
        }
    }


    private List<Event> filterWitnessList(Event y, List<Event> witnessList) {
        List<Event> targetWitnessList = new ArrayList<>();
        for (int i = 0; i < witnessList.size(); i ++) {
            Event witness = witnessList.get(i);
            if (isStronglySee(y, witness)) {
                targetWitnessList.add(witness);
            }
        }
        return  targetWitnessList;
    }

    private boolean voteBySignature(String signature) {
        byte[] sign = Base64.getDecoder().decode(signature);
        int len = sign.length;
        int i = sign[len / 2] & 0b00001000;
        return i != 0;
    }

    private boolean getMajorityVote(List<Event> witnessList, AtomicInteger voteNum) {
        int voteYes = 0;
        int voteNo = 0;
        for (Event e : witnessList) {
            if (e.isVoteRes()) {
                voteYes += 1;
            }else {
                voteNo += 1;
            }
        }
        if ( voteYes >= voteNo) {
            voteNum.set(voteYes);
            return true;
        }else {
            voteNum.set(voteNo);
            return false;
        }
    }

    private boolean isSee(Event src, Event dest) {
        if (src == dest) {
            return true;
        }
        cleanVisited();
        return DFS.findPath(src, dest, new ArrayList<>());
    }

    private boolean isStronglySee(Event src, Event dest) {
        if (src == dest) {
            return false;
        }
        cleanVisited();
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

    private void cleanVisited() {
        this.hashgraph.forEach((id, chain)-> chain.forEach(e-> e.setVisited(false)));
    }

}
