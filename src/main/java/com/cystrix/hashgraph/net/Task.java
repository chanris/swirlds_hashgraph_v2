package com.cystrix.hashgraph.net;

import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.HashgraphMember;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


public class Task implements Runnable {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private HashgraphMember hashgraphMember;

    public Task(Socket socket, HashgraphMember hashgraphMember) {
        this.socket = socket;
        this.hashgraphMember = hashgraphMember;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            RequestHandler requestHandler = new RequestHandler(this.hashgraphMember);
            Request request = RequestHandler.getRequestObject(reader);
            Response response = requestHandler.process(request);
            String result = RequestHandler.responseObject2JsonString(response);
            writer.println(result);
            closeChannel();
        }catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private void closeChannel() throws Exception{
        reader.close();;
        writer.close();
        socket.close();
    }
}
