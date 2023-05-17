package com.cystrix.hashgraph.hashview;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Event implements Cloneable {

    private Event selfParent; //
    private Event otherParent; //
    private List<Transaction> transactionList;

    private Long timestamp;
    private Long consensusTimestamp;
    private Boolean isWitness;
    private Boolean isFamous;
    private Boolean isCommit;
    private Integer createdRound;
    private Integer receivedRound;
    private String signature;  // 事件签名




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
