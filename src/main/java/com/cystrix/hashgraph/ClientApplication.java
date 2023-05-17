package com.cystrix.hashgraph;

import com.cystrix.hashgraph.net.Request;
import com.cystrix.hashgraph.net.RequestHandler;
import com.cystrix.hashgraph.net.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientApplication {
    public static void main(String[] args) {

        for (int i = 0; i < 4; i ++) {
            int port = 8080 + i;
            try (Socket socket = new Socket("127.0.0.1",port)){
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                Request request = new Request();
                request.setMapping("/shutdown");
                writer.println(RequestHandler.requestObject2JsonString(request));
                Response response = RequestHandler.getResponseObject(reader);
                if (response.getCode() == 200) {
                    System.out.println("node_id: " + i + "shutdown success!");
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
