package com.cystrix.hashgraph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cystrix.hashgraph.hashview.Event;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class JsonConvertTests {

    @Test
    public void json2Map() {
        Map<String, String> m = new HashMap<>();
        m.put("Name", "Chen Yue");
        m.put("Age", "23");
        String s = JSON.toJSONString(m);
        HashMap hashMap = JSONObject.parseObject(s, HashMap.class);
        System.out.println(hashMap);

        Map<Integer, Integer> m2 = new HashMap<>();
        m2.put(1, 1);
        String json = JSON.toJSONString(m2);
        HashMap<Integer, Integer> hashMap1 = JSONObject.parseObject(json, HashMap.class);
        System.out.println(hashMap1.get(1));
    }

    @Test
    public void jsonSerialize() {
        Event e = new Event();
        e.setTimestamp(System.currentTimeMillis());
        e.setIsCommit(true);

        String json = JSON.toJSONString(e);
        System.out.println(json);
    }
}
