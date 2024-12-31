package org.chaosadept.summaryzatron2000;

import jakarta.annotation.PostConstruct;
import org.chaosadept.summaryzatron2000.config.BlacklistProperties;
import org.chaosadept.summaryzatron2000.config.TelegramProperties;
import org.chaosadept.summaryzatron2000.gpt.YandexArtClient;
import org.chaosadept.summaryzatron2000.gpt.YandexGPTClient;
import org.chaosadept.summaryzatron2000.model.*;
import org.chaosadept.summaryzatron2000.repository.ChatLatestMessageRepository;
import org.chaosadept.summaryzatron2000.repository.GptJobRepository;
import org.chaosadept.summaryzatron2000.repository.GptOperationRepository;
import org.chaosadept.summaryzatron2000.repository.UserRepository;
import org.chaosadept.summaryzatron2000.telegram.ClientProvider;
import org.chaosadept.summaryzatron2000.utils.MsgUtils;
import it.tdlight.jni.TdApi;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.chaosadept.summaryzatron2000.model.OperationType.*;
import static org.chaosadept.summaryzatron2000.utils.MsgUtils.extractMsgText;
import static org.chaosadept.summaryzatron2000.utils.MsgUtils.hasText;

@Slf4j
@Service
public class TelegramChatSaverService {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter folder_suffix_formatter = DateTimeFormatter.ofPattern("yy-MM-dd_HH_mm_ss")            .withZone(ZoneId.systemDefault());

    @Autowired
    private BlacklistProperties blacklistProperties;

    @Value("${hashtag}")
    private String summaryHashtag;

    @Value("${gpt.maxCharLen:5000}")
    private int maxChatCount;

    @Autowired
    private YandexGPTClient gptClient;

    @Autowired
    private YandexArtClient artClient;

    @Value("${gpt.model}")
    private YandexGPTClient.ModelLevel currentModel;

    @Autowired
    private ChatLatestMessageRepository chatLatestMessageRepository;

    @Autowired
    private TelegramProperties telegramProperties;

    @Autowired
    private ClientProvider telegramClientProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    GptOperationRepository gptOperationRepository;

    @Autowired
    GptJobRepository gptJobRepository;

   // @Autowired
   // private ResourceLoader resourceLoader;

    @Value("${gpt.partSummaryPromtFile}")
    private Resource partSummaryPromtFilePath;

    @Value("${gpt.combinedSummaryPromtFile}")
    private Resource combinedSummaryPromtFilePath;

    @Value("${gpt.imagedSummaryPromtFile}")
    private Resource imageSummaryPromtFilePath;

    @Value("${gpt.enableImageSummaryGeneration}")
    private boolean enabledImageGeneration;

    private int MIN_PARTS_COUNT_TO_RECOMBINE = 2;

    @SneakyThrows
    @PostConstruct
    public void init() {
        if (!partSummaryPromtFilePath.exists()) {
            throw new FileNotFoundException("combinedSummaryPromtFilePath not found " + partSummaryPromtFilePath.getFile().getAbsolutePath());
        }

        if (!combinedSummaryPromtFilePath.exists()) {
            throw new FileNotFoundException("combinedSummaryPromtFilePath not found " + combinedSummaryPromtFilePath.getFile().getAbsolutePath());
        }
    }

    @SneakyThrows
    public void attemptToFinishAsyncResults() {
        AsyncAnalyzeJob job = null;
        var jobOpt = gptJobRepository.findFirstByHasFinishedFalseOrderByStartDateAsc();

        if (jobOpt.isEmpty()) {
            log.info("No jobs to finish.");
            return;
        }

        job = jobOpt.get();

        log.info("start to checking job id: {}", job.getId());
        try {

            switch (job.getStage()) {
                case Registered:
                    toStage(job, JobStage.Parts);
                    break;
                case Parts:
                    processParts(job);
                    break;
                case Summary:
                    processSummary(job);
                    break;
                case ImagePromt:
                    processImagePromt(job);
                    break;
                case ImageContent:
                    processImageContent(job);
                    break;
                case Published:
                    flushSummary(job);
                    break;
                default:
                    log.warn("unknown job stage: {}", job.getStage());
            }

        } catch (Exception e) {
            log.error("Unexpected error during processing the job.", e);
            job.setHasError(true);
            job.setFinishDate(Instant.now().toEpochMilli());
            job.setHasFinished(true);
            gptJobRepository.save(job);
            log.warn("job id={} has error and will be cancelled.", job.getId());
        }

        log.info("job {} processing cycle finished", job.getId());
    }

    @SneakyThrows
    private void processImagePromt(AsyncAnalyzeJob job) {
        var finalSummary = getFinalSummary(job);
        //generate image promnt
        var imagePromtOperation = job.getImageSummaryPromtOperation();
        if (job.getImageSummaryPromtOperation() == null) {
            var r = execPromt(imageSummaryPromtFilePath.getContentAsString(StandardCharsets.UTF_8), finalSummary);
            job.setImageSummaryPromtOperation(newAsyncOperation(job.getId(), r, TEXT_GENERATION));

            gptJobRepository.save(job);
            log.info("wait image promt to be generated");
            return;
        } else {
            var imagePromtGenResult = gptClient.getCompletionResult(imagePromtOperation.getOperationId());
            updateOperation(imagePromtOperation, imagePromtGenResult);
        }

        if (imagePromtOperation.getHasFinished()) {
            log.info("image promt has been generated");
            //totalToken += imagePromtOperation.getTotalTokens();
            try (var writerGptFile = new FileWriter(new File(job.getWorkspaceDir(), "image_promt_result.txt"))) {
                writerGptFile.append(imagePromtOperation.getResult());
            }

            toStage(job, JobStage.ImageContent);
        } else {
            log.info("image promt has not been generated, wait");
            return;
        }

    }

    private void toStage(AsyncAnalyzeJob job, JobStage stage) {
        log.info("job id={} stage={} will be moved to stage={}", job.getId(), job.getStage(), stage);
        job.setStage(stage);
        gptJobRepository.save(job);
    }

    private String getFinalSummary(AsyncAnalyzeJob job) {
        if (job.getCombinedSummaryOperation() != null) {
            return job.getCombinedSummaryOperation().getResult();
        } else {
            return job.getParts().stream().filter(o -> o.getType() == TEXT_PART_GENERATION).findFirst().orElseThrow().getResult();
        }
    }

    @SneakyThrows
    private void processSummary(AsyncAnalyzeJob job) {
        var textParts = job.getParts().stream().filter(o -> o.getType() == TEXT_PART_GENERATION).collect(Collectors.toList());
        var nextStage = enabledImageGeneration ? JobStage.ImagePromt : JobStage.Published;
        log.info("start processSummary nextStage={}", nextStage);

        if (textParts.size() < MIN_PARTS_COUNT_TO_RECOMBINE) {
            log.info("job id={} is too small and will be just returned without recombination.", job.getId());
            var finalSummary = textParts.stream().map(o -> (o.getResult())).collect(Collectors.joining("\n\r"));
            var finalSummaryMethod = "x1";
            toStage(job, nextStage);
            return;
        }

        if (job.getCombinedSummaryOperation() == null) {
            String finalSummaryPromt = combinedSummaryPromtFilePath.getContentAsString(StandardCharsets.UTF_8);

            final var parts = job.getParts();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < textParts.size(); i++) {
                sb.append("начало фрагмента (")
                        .append(i + 1)
                        .append(")\n\r")
                        .append((parts.get(i).getResult()))
                        .append("\n\r");
            }

            var gptResponsesCombined = sb.toString();
            Files.copy(combinedSummaryPromtFilePath.getInputStream(), Path.of(job.getWorkspaceDir()).resolve(combinedSummaryPromtFilePath.getFilename()));

            var req = execPromt(finalSummaryPromt, gptResponsesCombined);

            job.setCombinedSummaryOperation(newAsyncOperation(job.getId(), req, OperationType.TEXT_GENERATION));
            gptJobRepository.save(job);
            return;
        }

        var r = gptClient.getCompletionResult(job.getCombinedSummaryOperation().getOperationId());
        job.getCombinedSummaryOperation().setLastCheckDate(Instant.now().toEpochMilli());
        log.info("gpt getCombinedSummaryOperation operation id: {} result: {}", job.getCombinedSummaryOperation().getOperationId(), r);
        updateOperation(job.getCombinedSummaryOperation(), r);

        if (r.isFinished() && !r.isHasError()) {
            try (var writerGptFile = new FileWriter(new File(job.getWorkspaceDir(), "messages_part_gpt_combined_result.txt"), true)) {
                writerGptFile.append(r.getResult());
            }
            toStage(job, nextStage);
        } else {
            log.info("gpt getCombinedSummaryOperation operation id: {} is not finished", job.getCombinedSummaryOperation().getOperationId());
            gptJobRepository.save(job);
        }
    }

    @SneakyThrows
    private void processParts(AsyncAnalyzeJob job) {
        var textParts = job.getParts().stream().filter(o -> o.getType() == TEXT_PART_GENERATION).collect(Collectors.toList());
        var fileIndex = 0;

        if (CollectionUtils.isEmpty(textParts)) {
            log.error("job id={} has no text parts", job.getId());
            job.setHasError(true);
            job.setHasFinished(true);
            gptJobRepository.save(job);
            return;
        }

        for (var gptOperation : textParts) {
            fileIndex++;
            if ((gptOperation.getHasFinished() != null) && (!gptOperation.getHasFinished())) {
                log.info("gpt operation id: {}", gptOperation.getOperationId());
                var r = gptClient.getCompletionResult(gptOperation.getOperationId());
                if (r.isFinished()) {
                    updateOperation(gptOperation, r);
                    if (!r.isHasError()) {
                        var f = new File(job.getWorkspaceDir(), "messages_part_gpt_result_" + fileIndex + ".txt");
                        try (var writerGpt = new FileWriter(f)) {
                            log.info("save gpt result to file: {}", f.getAbsolutePath());
                            writerGpt.append((gptOperation.getResult()));
                            writerGpt.append("\n\r");
                        }
                    }
                } else {
                    gptOperation.setLastCheckDate(Instant.now().toEpochMilli());
                    log.info("gpt operation id: {} is not finished last check time was updated", gptOperation.getOperationId());
                }

                gptOperationRepository.save(gptOperation);
            } else if (gptOperation.getHasError() != null && !gptOperation.getHasError()) {
                log.info("gpt operation id: {} is finished", gptOperation.getOperationId());
            }
        }

        var hasError = textParts.stream().anyMatch(o -> o.getHasError() != null && o.getHasError());
        if (hasError) {
            job.setHasError(hasError);
            job.setFinishDate(Instant.now().toEpochMilli());
            job.setHasFinished(true);
            gptJobRepository.save(job);
            log.warn("job id={} has error and will be cancelled.", job.getId());
            return;
        }

        var hasUnFinishedPartsCount = textParts.stream().filter(o -> o.getHasFinished() != null && !o.getHasFinished()).count();
        if (hasUnFinishedPartsCount > 0) {
            log.warn("job id={} has unprocessed parts count={} and will be rechecked later.", job.getId(), hasUnFinishedPartsCount);
            return;
        }

        toStage(job, JobStage.Summary);
    }

    private void updateOperation(GptOperation gptOperation, YandexGPTClient.AsyncOperationResult r) {
        gptOperation.setFinishedDate(Instant.now().toEpochMilli());
        gptOperation.setHasError(r.isHasError());
        gptOperation.setHasFinished(r.isFinished());
        gptOperation.setErrorMessage(r.getErrorMessage());
        gptOperation.setResult(r.getResult());
        gptOperation.setTotalTokens(r.getTotalTokens());
        gptOperationRepository.save(gptOperation);
    }

    @SneakyThrows
    private void processImageContent(AsyncAnalyzeJob job) {
        var imageContentOperation = job.getImageSummaryContentOperation();

        if (imageContentOperation == null) {
            var artOperId = artClient.getCompletionAsync(job.getImageSummaryPromtOperation().getResult());
            imageContentOperation = newAsyncOperation(job.getId(), artOperId, IMAGE_GENERATION);
            gptOperationRepository.save(imageContentOperation);
            job.setImageSummaryContentOperation(imageContentOperation);
            gptJobRepository.save(job);
            log.info("wait image content to be generated");
            return;
        } else {
            var imageContentGenResult = artClient.getCompletionResult(imageContentOperation.getOperationId());
            updateOperation(job.getImageSummaryContentOperation(), imageContentGenResult);

            if (!imageContentGenResult.isFinished()) {
                log.info("image content has not been generated, wait");
            } else {
                toStage(job, JobStage.Published);
            }
        }
    }


    @SneakyThrows
    private void flushSummary(AsyncAnalyzeJob job) {

        var runOutfolder = job.getWorkspaceDir();
        var fileIndex = 0;
        var totalToken = 0;

        for (var gptOperation : job.getParts()) {
            fileIndex++;
            totalToken += gptOperation.getTotalTokens();
        }

        String finalSummary = getFinalSummary(job);

        var targetChatId = telegramClientProvider.getChatIdByTitle(telegramProperties.getTargetChatTitle());
        if (targetChatId == null) {
            log.error("cant find target chatId by title: {}", telegramProperties.getTargetChatTitle());
            throw new RuntimeException("cant find target chatId by title: " + telegramProperties.getTargetChatTitle());
        }

        if (enabledImageGeneration) {
            byte[] data = Base64.getDecoder().decode(job.getImageSummaryContentOperation().getResult());
            var f = new File(runOutfolder, "image_conent_result.jpg");
            try (OutputStream stream = new FileOutputStream(f)) {
                stream.write(data);
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            TdApi.InputFile file = new TdApi.InputFileLocal(f.getAbsolutePath());
            TdApi.InputMessageContent messageContent = new TdApi.InputMessagePhoto(file, null, null, image.getWidth(), image.getHeight(), null, null, false);
            var notifyResult = this.telegramClientProvider.getClient().send(new TdApi.SendMessage(targetChatId, 0L, null, null, null, messageContent)).get();
            log.info("image sent: {}",  notifyResult.id);
        } else {
            log.info("image generation is disabled");
        }


        var writerGpt = new StringBuilder();
        writerGpt.append(finalSummary);
        writerGpt.append("\n\r");
        writerGpt.append("\n\r");
        writerGpt.append(summaryHashtag + "\n\r");
        //writerGpt.append("\nвремя: " + formatter.format(firstDate) + " - " + formatter.format(lastDate));
        writerGpt.append("\nвсего сообщений: " + job.getMessageCount());
        //writerGpt.append("\nсимволов: " + gptClient.getTotalChars());
        writerGpt.append("\nтокенов: " + totalToken);
        writerGpt.append("\n" +"model: " + currentModel.name());
        //writerGpt.append("\n" +"sm: " + finalSummaryMethod);
        writerGpt.append("\n" +"источник: " + this.telegramProperties.getChatInviteLink());

        var formatedSummaryAsMessage = (writerGpt.toString());



        TdApi.FormattedText text = new TdApi.FormattedText(formatedSummaryAsMessage, null);
        TdApi.LinkPreviewOptions linkPreviewOptions = new TdApi.LinkPreviewOptions(true, null, false, false, false);
        TdApi.InputMessageContent imgMessageContent = new TdApi.InputMessageText(text, linkPreviewOptions, false);
        var imgNotifyResult = this.telegramClientProvider.getClient().send(new TdApi.SendMessage(targetChatId, 0L, null, null, null, imgMessageContent)).get();
        log.info("notifyResult: {}",  imgNotifyResult.id);

        job.setFinishDate(Instant.now().toEpochMilli());
        job.setHasFinished(true);
        gptJobRepository.save(job);
        log.info("job id={} has published", job.getId());

    }

    public interface FetchMessagesPredicate {
        List<TdApi.Message> acceptNextMessages(List<TdApi.Message> message);
        boolean isCancelled();
        boolean hasCompleted();
        void notifyMessagesFetched();
    }

    @SneakyThrows
    public boolean analyzeChatAsync(FetchMessagesPredicate messagePredicate, boolean waitUpdate) {

        var client = telegramClientProvider.getClient();
        // Получаем ID чата
        var chatTitle = telegramProperties.getChatTitle();
        Long chatId = telegramClientProvider.getChatId();
        if (chatId == null) {
            log.error("Чат не найден {}.", chatTitle);
            throw new IllegalArgumentException("Чат "+chatTitle+" не найден.");
        }

        var lastMsg = telegramClientProvider.waitUpdate(chatId);

        List<TdApi.Message> messages = new ArrayList<>();
        var lastMsgId = lastMsg.id;

        var initialResult = messagePredicate.acceptNextMessages(List.of(lastMsg));
        messages.addAll(initialResult);

        if (messagePredicate.isCancelled()) {
            log.info("has cancelled");
            return false;
        }

        while (!messagePredicate.hasCompleted()) {
            TdApi.GetChatHistory getChatHistory = new TdApi.GetChatHistory(chatId, lastMsgId, 0, 50, false);
            CompletableFuture<TdApi.Messages> messagesFuture = client.send(getChatHistory);

            var msgPart = messagesFuture.join().messages;
            if (msgPart.length > 0) {
                lastMsgId = msgPart[msgPart.length - 1].id;
            } else {
                break;
            }

            var lastDate = Instant.ofEpochSecond(msgPart[msgPart.length - 1].date);
            log.info("lastDate: " + lastDate);

            var firstDate = Instant.ofEpochSecond(msgPart[0].date);
            log.info("firstDate: " + firstDate);


            Arrays.stream(msgPart)
                    .filter(MsgUtils::hasText)
                    .forEach(m -> log.trace("found message: {}", MsgUtils.extractMsgText(m)));


            var filteredMessages =  messagePredicate.acceptNextMessages(List.of(msgPart));
            messages.addAll(filteredMessages);
        }

        log.info("has finished fetching");

        Collections.reverse(messages);

        messagePredicate.notifyMessagesFetched();

        if (messagePredicate.isCancelled()) {
            log.info("has cancelled after fetching all messages");
            return false;
        }

        log.info("all message loaded: " + messages.size());

        var lastDate = Instant.ofEpochSecond(messages.get(messages.size() - 1).date);
        log.info("analyze lastDate: " + lastDate);

        var firstDate = Instant.ofEpochSecond(messages.get(0).date);
        log.info("analyze firstDate: " + firstDate);



        gptClient.resetStats();

        int fileIndex = 1;
        int charCount = 0;


        var suffixFormatedTime = folder_suffix_formatter.format(Calendar.getInstance().getTime().toInstant());
        File runOutfolder = new File("./run-results/" + chatId + "/" + suffixFormatedTime);
        UUID jobId = UUID.randomUUID();
        runOutfolder.mkdirs();

        AsyncAnalyzeJob job =
                AsyncAnalyzeJob.builder()
                        .stage(JobStage.Registered)
                        .hasFinished(false)
                        .parts(new ArrayList<>())
                        .messageCount(messages.size())
                        .startDate(Instant.now().toEpochMilli())
                        .workspaceDir(runOutfolder.getAbsolutePath())
                        .id(jobId)
                        .build();

        List<YandexGPTClient.AsyncOperationRequestId> gptPartResponse = new ArrayList<>();
        //List<String> gptPartResponses = new ArrayList<>();
        StringBuilder partMessages = new StringBuilder(maxChatCount);

        var partPromtPath = partSummaryPromtFilePath;
        String prompt = partPromtPath.getContentAsString(StandardCharsets.UTF_8);

        Files.copy(partPromtPath.getInputStream(), runOutfolder.toPath().resolve(partSummaryPromtFilePath.getFilename()));

        FileWriter writer = new FileWriter(new File(runOutfolder, chatTitle + "_messages_part" + fileIndex + ".txt"));

        int partMessagesCount = 0;
        for (TdApi.Message message : messages) {
            // Фильтр сообщений по времени
            partMessagesCount++;
            // Получение ника отправителя
            long userId = -1;
            if (message.senderId instanceof TdApi.MessageSenderUser) {
                TdApi.MessageSenderUser senderUser = (TdApi.MessageSenderUser) message.senderId;
                userId = senderUser.userId;
            } else {
                continue;
            }


            var msgTime = Instant.ofEpochSecond(message.date);
            String formattedDate = formatter.format(msgTime);

            // Получаем никнейм из кэша или запрашиваем его через API, если в кэше нет

            final var finalUserId = userId;
            var userInfo = userRepository.findById(finalUserId).orElseGet(() -> {
                log.info("get username: " + finalUserId);
                CompletableFuture<TdApi.User> userFuture = client.send(new TdApi.GetUser(finalUserId));
                TdApi.User user = userFuture.join();
                var username = user.usernames != null && user.usernames.editableUsername != null ? user.usernames.editableUsername
                        : user.lastName;
                var userEntity = new User();
                userEntity.setId(user.id);
                userEntity.setUsername(username);
                return userRepository.save(userEntity);
            });

            var username = userInfo.getUsername();


            int totalReactions = 0;
            if (message.interactionInfo != null
                    && message.interactionInfo.reactions != null
                    && message.interactionInfo.reactions.reactions != null
            ) {
                for (var r : message.interactionInfo.reactions.reactions) {

                    totalReactions += r.totalCount;
                }
            }

            // Форматирование и запись в файл
            if (message.forwardInfo == null && hasText(message)) {
                var msgTextForSummary = extractMsgText(message);
                if (isBlacklisted(msgTextForSummary)) {
                    continue;
                }

                String messageContent = String.format("\n---\n@%s [%s] {%s}" + "\n", username, formattedDate, totalReactions);


                try {
                    if (message.replyTo instanceof TdApi.MessageReplyToMessage) {
                        var r = (TdApi.MessageReplyToMessage) message.replyTo;

                        var msg = client.send(new TdApi.GetMessage(chatId, r.messageId)).join();
                        var replyText = hasText(msg) ? extractMsgText(msg) : null;

                        if (message.senderId instanceof TdApi.MessageSenderUser && replyText != null && !isBlacklisted(replyText)) {
                            TdApi.MessageSenderUser senderUser = (TdApi.MessageSenderUser) message.senderId;
                            messageContent += "<<<\"" + replyText.substring(0, Math.min(replyText.length(), 120)) + "\" ... >>>";
                        }

                    }
                } catch (Exception e) {
                    log.error("cant process reply infomation", e);
                }


                messageContent += "\n\n" + msgTextForSummary + "\n";

                partMessages.append(messageContent);

                // Проверяем, сколько символов осталось до лимита, и создаем новый файл, если лимит превышен
                if (charCount + messageContent.length() > maxChatCount) {

                    var response = execPromt(prompt, partMessages.toString());

                    job.getParts().add(newAsyncOperation(jobId, response, TEXT_PART_GENERATION));

                    partMessages = new StringBuilder(maxChatCount * 2);

                    writer.close(); // Закрываем текущий файл
                    fileIndex++; // Увеличиваем индекс файла
                    writer = new FileWriter(new File(runOutfolder, chatTitle + "_messages_part" + fileIndex + ".txt")); // Создаем новый файл
                    charCount = 0; // Сбрасываем счётчик символов
                    partMessagesCount = 0;
                }

                writer.write(messageContent); // Пишем сообщение в файл
                charCount += messageContent.length(); // Увеличиваем счётчик символов
            }
        }

        // Закрываем последний файл

        writer.close();

        //допереводим
        if (StringUtils.hasText(partMessages.toString().trim())) {
            var response = execPromt(prompt, partMessages.toString());
            job.getParts().add(newAsyncOperation(job.getId(), response, TEXT_PART_GENERATION));
        }

        gptJobRepository.save(job);

        log.info("registered job: {}", job.getId());
        log.info("всего сообщений: " + messages.size());

        return true;
    }

    private GptOperation newAsyncOperation(UUID jobId, YandexGPTClient.AsyncOperationRequestId operationId, OperationType operationType) {
        var o = GptOperation.builder()
                    .type(operationType)
                    .jobId(jobId)
                    .operationId(operationId.getId())
                    .hasError(false)
                    .hasFinished(false)
                    .startDate(Instant.now().toEpochMilli())
                .build();

        return gptOperationRepository.save(o);
    }

    private YandexGPTClient.AsyncOperationRequestId execPromt(String prompt, String partMessages) throws IOException, InterruptedException {
        return gptClient.getCompletionAsync(prompt, partMessages, 0.3f, currentModel);
    }

    private boolean isBlacklisted(String text) {
        return text.contains(summaryHashtag)
                || blacklistProperties.getKeywords().stream().anyMatch(text::contains);
    }
}
