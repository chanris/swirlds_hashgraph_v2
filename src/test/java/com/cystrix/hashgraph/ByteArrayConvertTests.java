package com.cystrix.hashgraph;

import org.junit.Test;

import java.util.Base64;

public class ByteArrayConvertTests {
    @Test
    public void byteArray2Base64() {
        byte[] bytes = "Hello, World!".getBytes();
        int be_len  = bytes.length;
        String base64 = Base64.getEncoder().encodeToString(bytes);
        byte[] af_bytes = Base64.getDecoder().decode(base64);
        assert  be_len == af_bytes.length;
        for (int i = 0; i < be_len; i++) {
            assert bytes[i] == af_bytes[i];
        }
        System.out.println("test success!");
    }
}
