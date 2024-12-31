package org.chaosadept.summaryzatron2000;

import org.chaosadept.summaryzatron2000.config.TelegramProperties;
import org.chaosadept.summaryzatron2000.utils.MsgUtils;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@SpringBootApplication
@EntityScan({"org.chaosadept.summaryzatron2000"})
@EnableScheduling
@Slf4j
public class TelegramChatSaverApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramChatSaverApplication.class, args);
    }

}
