package org.chaosadept.summaryzatron2000.telegram;

import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.chaosadept.summaryzatron2000.config.TelegramProperties;
import org.chaosadept.summaryzatron2000.model.ChatLatestMessage;
import org.chaosadept.summaryzatron2000.repository.ChatLatestMessageRepository;
import org.chaosadept.summaryzatron2000.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class ClientProvider {


    private final ConcurrentMap<Long, TdApi.Message> latestMessages = new ConcurrentHashMap<>();


    @Getter
    private SimpleTelegramClient client;

    @Autowired
    private TelegramProperties telegramProperties;

    @Autowired
    private ChatLatestMessageRepository chatLatestMessageRepository;

    @PostConstruct
    private void init() {
        // Инициализация TDLib клиента
        SimpleTelegramClientFactory clientFactory = new SimpleTelegramClientFactory();
        APIToken apiToken = new APIToken(telegramProperties.getApiTokenId(), telegramProperties.getApiTokenHash());

        // Настройка TDLib клиента
        TDLibSettings settings = TDLibSettings.create(apiToken);
        settings.setDatabaseDirectoryPath(Paths.get(telegramProperties.getSessionPath()).resolve("data"));
        settings.setDownloadedFilesDirectoryPath(Paths.get(telegramProperties.getSessionPath()).resolve("downloads"));

        SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);
        clientBuilder.addUpdateHandler(TdApi.UpdateChatLastMessage.class, this::updateLastChatMessageHandler);
        clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::updateAuthState);

        var authenticationData = AuthenticationSupplier.qrCode();
        client = clientBuilder.build(authenticationData);
    }



    @SneakyThrows
    @PreDestroy
    private void destroy() {
        client.closeAndWait();
    }


    private void updateLastChatMessageHandler(TdApi.UpdateChatLastMessage update) {
        if (update.lastMessage == null) {
            log.warn("last message is null for chatId={}", update.chatId);
            return ;
        }

        log.debug("chat update was recived chatId={} messageId={}", update.chatId, update.lastMessage.id);
        latestMessages.put(update.chatId, update.lastMessage);
    }

    @SneakyThrows
    private void updateAuthState(TdApi.UpdateAuthorizationState state) {
        log.info("state updated={}", state);
        if (state.authorizationState instanceof TdApi.AuthorizationStateWaitPassword) {
            var result = client.send(new TdApi.CheckAuthenticationPassword(telegramProperties.getPassword())).get();
            log.info("supply password results {}", result);
        }

    }

    @SneakyThrows
    public TdApi.Message waitUpdate(Long chatId) {
        var waitAttempt = 0;
        var initialLastMsgId = chatLatestMessageRepository.findByChatId(chatId).map(ChatLatestMessage::getMessageId).orElse(0L);
        while (waitAttempt < telegramProperties.getChatUpdateWaitAttempts()) {
            log.trace("wait chat to be updated waitAttempt= {}/{}", waitAttempt, telegramProperties.getChatUpdateWaitAttempts());

            if (latestMessages.containsKey(chatId)) {
                if (initialLastMsgId.equals(latestMessages.get(chatId).id)) {
                    log.info("chat has old last msg id");
                } else {
                    log.info("chat last message has been updated");
                    break;
                }
            }

            waitAttempt++;
            Thread.sleep(telegramProperties.getChatUpdateWaitMilliseconds());
        }

        chatLatestMessageRepository.save(new ChatLatestMessage(chatId, latestMessages.get(chatId).id));

        return latestMessages.get(chatId);
    }

    public Long getChatId() throws Exception {
        return getChatIdByTitle(telegramProperties.getChatTitle());
    }

    @SneakyThrows
    public Long getChatIdByTitle(String chatTitle) {
        var client = getClient();
        TdApi.Chats chats = client.send(new TdApi.GetChats(new TdApi.ChatListMain(), 100)).get();
        for (var id : chats.chatIds) {
            CompletableFuture<TdApi.Chat> chatFuture = client.send(new TdApi.GetChat(id));
            TdApi.Chat chat = chatFuture.join();
            log.trace("fetched chat id={} title={}", chat.id, chat.title);
            if (chat.title.contains(chatTitle)) {
                return chat.id;
            }
        }

        return null;
    }


}
