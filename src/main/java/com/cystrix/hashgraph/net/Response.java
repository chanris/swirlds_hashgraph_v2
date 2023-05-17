package com.cystrix.hashgraph.net;

import lombok.Data;

@Data
public class Response {
    private Integer code;
    private String message;
    private String data;
}
