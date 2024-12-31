package org.chaosadept.summaryzatron2000.schedule;

import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.chaosadept.summaryzatron2000.TelegramChatSaverService;
import org.chaosadept.summaryzatron2000.config.TelegramProperties;
import org.chaosadept.summaryzatron2000.model.LatestAnalyzedRunMessage;
import org.chaosadept.summaryzatron2000.repository.ChatLatestAnalyzedMessageRepository;
import org.chaosadept.summaryzatron2000.telegram.ClientProvider;
import org.chaosadept.summaryzatron2000.utils.MsgUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


@Profile("scheduled")
@Component()
@Slf4j
public class AnalyzeScheduler {

    @Autowired
    TelegramChatSaverService chatSaverService;

    @Autowired
    TelegramProperties telegramProperties;

    @Autowired
    ClientProvider clientProvider;


    @Value("${cron.minNumMessagesToSummarize}")
    private int minNumMessagesToSummarize;

    @Value("${cron.firstRunMessageMaxCount}")
    private int firstRunMessageMaxCount;

    @Value("${hashtag}")
    private String summaryHashtag;

    @Value("${REINIT_FIRST_RUN:false}")
    private boolean reinitFirstRun;

    private int runCount = 0;

    @Autowired
    private ChatLatestAnalyzedMessageRepository latestAnalyzedMessageRepository;

    @PostConstruct
    public void init() {
        log.info("init analyze scheduler");
        run();
    }

    //todo add run daily summary based on daily parts

    @Value("${cron.expression}")
    private String cronSchedule;

    @Scheduled(cron = "${cron.expression}")
    @SneakyThrows
    public void run() {
        runCount++;
        // ваш код здесь
        try {

            log.info("executed analyze");

            var chatId = clientProvider.getChatId();


            Optional<LatestAnalyzedRunMessage> latestAnalyzedMessage = (runCount == 1 && reinitFirstRun) ? Optional.empty() : latestAnalyzedMessageRepository.findByChatId(chatId);

            var predicate = new TelegramChatSaverService.FetchMessagesPredicate() {
                private int count = 0;
                private boolean isCancelled = false;
                private boolean hasCompleted = false;
                public Optional<TdApi.Message> nextLastMessage = Optional.empty();

                @Override
                public List<TdApi.Message> acceptNextMessages(List<TdApi.Message> messages) {

                    if (messages.isEmpty()) {
                        hasCompleted = true;
                        log.info("no left messages to summarize finished");
                        return Collections.emptyList();
                    }

                    var result = new ArrayList<TdApi.Message>();

                    if (nextLastMessage.isEmpty()) {
                        nextLastMessage = Optional.of(messages.get(messages.size() - 1));
                    }

                    for (int i = 0; i <= messages.size() - 1; i++) {
                        var msg = messages.get(i);
                        log.debug("msgId: {}, msgContent:{}", msg.id, MsgUtils.extractMsgText(msg));

                        var reachedLastAnalyzedMessage = latestAnalyzedMessage.isPresent() && msg.id == latestAnalyzedMessage.get().getMessageId();
                        var reachedMaxCount = count >= firstRunMessageMaxCount;
                        var reachedHashTag = MsgUtils.hasText(msg) && MsgUtils.extractMsgText(msg).toLowerCase().contains(summaryHashtag);

                        if (reachedLastAnalyzedMessage || reachedMaxCount || reachedHashTag) {
                            log.info("reached max count={} or reached last analyzed message={} reachedHashTag={}", reachedMaxCount, reachedLastAnalyzedMessage, reachedHashTag);
                            hasCompleted = true;
                            break;
                        }

                        result.add(msg);
                        count++;

                    }

                    return result;
                }

                @Override
                public boolean isCancelled() {
                    return isCancelled;
                }

                @Override
                public boolean hasCompleted() {
                    return hasCompleted;
                }

                @Override
                public void notifyMessagesFetched() {
                    if (count < minNumMessagesToSummarize) {
                        log.info("not enough messages to run {} expected to be more than {}", count, minNumMessagesToSummarize);
                        isCancelled = true;
                    }
                }
            };

            var executed = chatSaverService.analyzeChatAsync(predicate, false);

            if (executed) {
                var latest = new LatestAnalyzedRunMessage(chatId, predicate.nextLastMessage.get().id, (predicate.nextLastMessage.get().date));
                latestAnalyzedMessageRepository.save(latest);

                finishAsyncResults();
            }

        } catch (Exception e) {
            log.error("cant execute", e);
            throw new RuntimeException(e);
        }
    }

    @Scheduled(cron = "${cron.finishAsyncExpression}")
    @SneakyThrows
    public void finishAsyncResults() {
        chatSaverService.attemptToFinishAsyncResults();
    }

}
