package com.cystrix.hashgraph.hashview;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
 @ToString(of = { "receivedRound"})
public class Event implements Cloneable {

    private String packer;  // 公钥信息
    private String otherParentHash;
    private String selfParentHash;
    private List<Transaction> transactionList;
    private Long timestamp;
    private String signature;  // 事件签名

    // for build DAG-structure
    private Integer nodeId; //成员id
    private Integer otherId; //otherParent node id 冗余信息

    // local fields
    @JSONField(serialize = false)
    private Long consensusTimestamp;
    @JSONField(serialize = false)
    private Boolean isWitness;
    @JSONField(serialize = false)
    private Boolean isFamous;
    @JSONField(serialize = false)
    private Boolean isCommit;
    @JSONField(serialize = false)
    private Integer createdRound;
    @JSONField(serialize = false)
    private Integer receivedRound;
    @JSONField(serialize = false)
    private boolean voteRes;

    //fields for DFS
    @JSONField(serialize = false)
    private List<Event> neighbors; // 邻接链表
    @JSONField(serialize = false)
    private boolean visited;
    @JSONField(serialize = false)
    private Event selfParent; //
    @JSONField(serialize = false)
    private Event otherParent; //

    public Event clone() {
        Event clone;
        try {
            clone = (Event) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        List<Transaction> transactionList1 = new ArrayList<>();
        this.transactionList.forEach(tx-> transactionList1.add(tx.clone()));
        clone.setTransactionList(transactionList1);
        return clone;
    }

}
