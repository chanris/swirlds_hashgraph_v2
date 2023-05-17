package com.cystrix.hashgraph.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(){
        super();
    }
    public BusinessException(String message) {
        super(message);
    }
    public BusinessException(Exception e) {
        super(e);
    }
}
