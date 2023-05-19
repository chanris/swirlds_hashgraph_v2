package com.cystrix.hashgraph;

import com.cystrix.hashgraph.hashview.Event;
import com.cystrix.hashgraph.util.SHA256;
import org.junit.Test;

import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class DSATests {

    @Test
    public void skGenerateTest() {
        Map<String, String> stringStringMap = SHA256.generateKeyPairBase64();
        System.out.println(stringStringMap);

        KeyPair keyPair = SHA256.getKeyPair(stringStringMap);
        assert Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()).equals(stringStringMap.get("PK"));
        assert Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()).equals(stringStringMap.get("SK"));
        System.out.println("test success!");
    }

    @Test
    public void signatureAndVerifyTest() {
        Map<String, String> stringStringMap = SHA256.generateKeyPairBase64();
        Event event = new Event();
        event.setPacker(stringStringMap.get("PK"));
        event.setTimestamp(System.currentTimeMillis());
        event.setSelfParentHash(UUID.randomUUID().toString().replace("-", ""));
        event.setOtherParentHash(UUID.randomUUID().toString().replace("-", ""));
        String signature = SHA256.signEvent(event, stringStringMap.get("SK"));
        event.setSignature(signature);
        assert SHA256.verifyEvent(event);
    }
}
