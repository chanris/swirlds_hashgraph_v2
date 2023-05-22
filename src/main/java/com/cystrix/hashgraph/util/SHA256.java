package com.cystrix.hashgraph.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.Event;
import com.cystrix.hashgraph.hashview.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class SHA256 {

    public static String signEvent(Event event, String skBase64Encoded) throws BusinessException {
        _Event e = new _Event(event);
        String s = JSONObject.toJSONString(e);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);

        try {
            PrivateKey privateKey = parsePrivateKey(skBase64Encoded);
            // 创建签名对象
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");

            ecdsaSign.initSign(privateKey);
            ecdsaSign.update(bytes);

            byte[] signature = ecdsaSign.sign();
            return Base64.getEncoder().encodeToString(signature);
        }catch (Exception ex) {
            throw new BusinessException(ex);
        }
    }


    public static String signTransaction(Transaction tx, String skBase64Encoded) throws BusinessException {
        _Transaction transaction = new _Transaction(tx);
        byte[] bytes =  JSONObject.toJSONString(transaction).getBytes(StandardCharsets.UTF_8);
        try {
            PrivateKey privateKey = parsePrivateKey(skBase64Encoded);
            // 创建签名对象
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");

            ecdsaSign.initSign(privateKey);
            ecdsaSign.update(bytes);

            byte[] signature = ecdsaSign.sign();
            return Base64.getEncoder().encodeToString(signature);
        }catch (Exception ex) {
            throw new BusinessException();
        }
    }

    public static boolean verifyEvent(Event event) {
        _Event e = new _Event(event);
        String s = JSONObject.toJSONString(e);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        try {
            PublicKey publicKey = parsePublicKey(e.getPacker());
            // 创建签名对象
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
            byte[] signature = Base64.getDecoder().decode(event.getSignature());
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(bytes);
            return ecdsaVerify.verify(signature);
        }catch (Exception ex) {
            ex.printStackTrace();
            throw new BusinessException(ex);
        }
    }

    private static class _Transaction {
        private String sender; // pkBase64Encoded
        private String receiver;
        private Long balance;
        private Long timestamp;
        private String extra;

        private String signature;

        public _Transaction(Transaction tx) {
            this.sender = tx.getSender();
            this.receiver = tx.getReceiver();
            this.balance = tx.getBalance();
            this.timestamp = tx.getTimestamp();
            this.extra = tx.getExtra();
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getReceiver() {
            return receiver;
        }

        public void setReceiver(String receiver) {
            this.receiver = receiver;
        }

        public Long getBalance() {
            return balance;
        }

        public void setBalance(Long balance) {
            this.balance = balance;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public String getExtra() {
            return extra;
        }

        public void setExtra(String extra) {
            this.extra = extra;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }
    }
    private static class _Event {
        private String packer; // pkBase64Encoded
        private String otherParentHash;
        private String selfParentHash;
        private List<Transaction> transactionList;
        private Long timestamp;

        public _Event(Event e) {
            this.packer = e.getPacker();
            this.otherParentHash = e.getOtherParentHash();
            this.selfParentHash = e.getSelfParentHash();
            this.transactionList = e.getTransactionList();
            this.timestamp = e.getTimestamp();
        }

        public String getPacker() {
            return packer;
        }

        public void setPacker(String packer) {
            this.packer = packer;
        }

        public String getOtherParentHash() {
            return otherParentHash;
        }

        public void setOtherParentHash(String otherParentHash) {
            this.otherParentHash = otherParentHash;
        }

        public String getSelfParentHash() {
            return selfParentHash;
        }

        public void setSelfParentHash(String selfParentHash) {
            this.selfParentHash = selfParentHash;
        }

        public List<Transaction> getTransactionList() {
            return transactionList;
        }

        public void setTransactionList(List<Transaction> transactionList) {
            this.transactionList = transactionList;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }

    // pk.getEncoded() ==> base64 字符串
    // sk.getEncoded() ==> base64 字符串
    //生成公私钥，并以base64编码的字符串表示
    public static Map<String, String> generateKeyPairBase64() {
        Map<String, String> keyPairMap = new HashMap<>(2);
        try {
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();
            assert publicKey != null;
            assert privateKey != null;
            keyPairMap.put("PK", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
            keyPairMap.put("SK", Base64.getEncoder().encodeToString(privateKey.getEncoded()));
            return keyPairMap;
        }catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    public static KeyPair getKeyPair(Map<String, String> keyPairBase64) {
        byte[] pkEncoded = Base64.getDecoder().decode(keyPairBase64.get("PK"));
        byte[] skEncoded = Base64.getDecoder().decode(keyPairBase64.get("SK"));
        try {
            // 使用 X509EncodedKeySpec 构造公钥规范
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pkEncoded);
            // 使用 PKCS8EncodedKeySpec 构造私钥规范
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(skEncoded);

            // 使用 KeyFactory 根据公钥和私钥规范生成 KeyPair 对象
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            return keyPair;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    // pk.getEncoded() ==> base64 字符串 ==> PublicKey
    // sk.getEncoded() ==> base64 字符串 ==> PrivateKey
    public static PrivateKey parsePrivateKey(String skBase64Encoded) {
        byte[] skEncoded = Base64.getDecoder().decode(skBase64Encoded);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(skEncoded);

        KeyFactory keyFactory;
        PrivateKey privateKey;
        try {
            keyFactory = KeyFactory.getInstance("EC");
            privateKey = keyFactory.generatePrivate(privateKeySpec);
            return privateKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static PublicKey parsePublicKey(String pkBase64Encoded) {
        byte[] pkEncoded = Base64.getDecoder().decode(pkBase64Encoded);
        // 使用 X509EncodedKeySpec 构造公钥规范
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pkEncoded);
        KeyFactory keyFactory;
        PublicKey publicKey;
        try {
            keyFactory = KeyFactory.getInstance("EC");
            publicKey = keyFactory.generatePublic(publicKeySpec);
            return publicKey;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] sha256(String message) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(message.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256HexString(String message) throws NoSuchAlgorithmException {
        byte[] bytes = sha256(message);
        return byteArrayToHexString(bytes);
    }


    public static String byteArrayToHexString(byte[] byteArray) {
        StringBuilder sb = new StringBuilder(byteArray.length * 2);
        for (byte b : byteArray) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public synchronized  static String toJSONString(Object o) {
        return JSON.toJSONString(o);
    }
}
