package com.cystrix.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class Demo {

    private static final String URL ="redis://localhost:6379";
    public static void main(String[] args) {
        RedisClient client = RedisClient.create(URL);

        // 创建与Redis的连接
        StatefulRedisConnection<String,String> connection = client.connect();

        // 获取同步执行Redis命令的RedisCommands对象
        RedisCommands<String, String> commands = connection.sync();

        commands.set("username", "Chen Yue");
        String value = commands.get("username");
        System.out.println("value:" + value);

        // 关闭Redis连接
        connection.close();
        client.shutdown();
    }
}
