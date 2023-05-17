package com.cystrix.hashgraph.net;

import lombok.Data;

@Data
public class Request {
    private Integer code;
    private String mapping;
    private String data;
}
