package com.cystrix.hashgraph.hashview;

import com.cystrix.hashgraph.exception.BusinessException;
import lombok.Data;

@Data
public class Transaction implements Cloneable{
    private String sender;
    private String receiver;
    private Long balance;  // 使用最小单位 1 CY  = 10^18 yue
    private String extra;
    private String signature;

    public Transaction clone() {
        Transaction transaction;
        try {
            transaction = (Transaction) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new BusinessException(e);
        }
        return transaction;
    }
}
