package org.chaosadept.summaryzatron2000.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {
    private int apiTokenId;
    private String apiTokenHash;
    private String chatTitle;
    private String chatInviteLink;
    private String targetChatTitle;
    private String sessionPath;
    private String password;
    private long chatUpdateWaitAttempts;
    private long chatUpdateWaitMilliseconds;

    // геттеры и сеттеры
}
