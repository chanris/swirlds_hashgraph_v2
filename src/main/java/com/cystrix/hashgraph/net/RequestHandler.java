package com.cystrix.hashgraph.net;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.Event;
import com.cystrix.hashgraph.hashview.HashgraphMember;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class RequestHandler {

    private HashgraphMember hashgraphMember;

    public RequestHandler(HashgraphMember hashgraphMember) {
        this.hashgraphMember = hashgraphMember;
    }

    public Response process(Request request) {
        Response response = new Response();
        // 约定通信的内容为json字符串对象
        if (request.getMapping() == null) {
            request.setMapping("/default");
        }
        switch (request.getMapping()) {
            case "/shutdown":
                shutdownMapping(request, response);
                break;
            case "/pullEvent":
                pullEventMapping(request, response);
                break;
            default:
                defaultMapping(request, response);
                break;
        }
        return response;
    }


    private void pullEventMapping(Request request, Response response) {
        String requestDataJSonString = request.getData();
        // Map<id:Integer, Chain.size():Integer>
        HashMap<Integer, Integer> hashMap;
        try {
            hashMap = JSONObject.parseObject(requestDataJSonString, HashMap.class);
        }catch (Exception e) {
            e.printStackTrace();
            response.setCode(400);
            response.setData("request parameter error: "+ request.getData());
            return;
        }

        ConcurrentHashMap<Integer, List<Event>> subEventList = new ConcurrentHashMap<>();
        this.hashgraphMember.getHashgraph().forEach((id, chain)->{
            int my_size = chain.size();
            int guest_size = hashMap.get(id);
            if (my_size > guest_size) {
                subEventList.put(id,  chain.subList(guest_size, my_size));
            }
        });
        String res;
        res = JSON.toJSONString(subEventList);

        response.setCode(200);
        response.setMessage("SUCCESS");
        response.setData(res);
    }


    private static String getRequestContent(BufferedReader reader) {
       /* StringBuilder  sb = new StringBuilder();
        String buff = null;
        try {
            while ((buff = reader.readLine()) != null) {
                sb.append(buff);
            }
        }catch (Exception e) {
            throw new BusinessException(e);
        }*/
        String content ;
        try {
            // 将所有的内容以一行输出
            content = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return content;
    }

    public static Request getRequestObject(BufferedReader reader) {
        String jsonContent = getRequestContent(reader);
        Request request = JSONObject.parseObject(jsonContent, Request.class);
        return request;
    }

    public static String  responseObject2JsonString(Response response) {
        return JSON.toJSONString(response);
    }

    public static String requestObject2JsonString(Request request) {
        return JSON.toJSONString(request);
    }

    public static Response getResponseObject(BufferedReader reader) {
        String content = getRequestContent(reader);
        return JSONObject.parseObject(content, Response.class);
    }

    private void  defaultMapping(Request request, Response response) {
        response.setCode(200);
        response.setMessage("SUCCESS");
        response.setData("server received this message: " + request.getData());
    }

    private void shutdownMapping(Request request, Response response) {
        this.hashgraphMember.setShutdown(true);
        response.setCode(200);
        response.setMessage("SUCCESS");
    }

}
