package com.discordclone.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsumerApplication {

    public static void main(String[] args) {
        // The name keeps this module's settings apart from the application's application.properties.
        new SpringApplication(ConsumerApplication.class).run(
                withConfigName(args));
    }

    private static String[] withConfigName(String[] args) {
        String[] result = new String[args.length + 1];
        result[0] = "--spring.config.name=consumer";
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }
}
