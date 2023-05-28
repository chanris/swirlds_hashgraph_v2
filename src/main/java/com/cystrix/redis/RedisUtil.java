package com.cystrix.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class RedisUtil {
    private static final String URL ="redis://localhost:6379";
    private static RedisClient client = RedisClient.create(URL);

    private static void set(String key, String value) {
        StatefulRedisConnection<String,String> connection = client.connect();
        RedisCommands<String, String> commands = connection.sync();
    }

}
