package org.chaosadept.summaryzatron2000.gpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Profile("mock")
@Component
@Slf4j
public class FakeGptClientImpl implements YandexGPTClient {

    private static final String API_URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion";

    public int totalTokens = 0;
    public int charsTotal = 0;

    @Value("${gpt.api-key}")
    private String API_KEY;

    @Value("gpt://${gpt.cloudId}/yandexgpt-lite/rc")
    public String GPT_MODEL_RC;

    @Value("gpt://${gpt.cloudId}/yandexgpt/rc")
    public String GPT_MODEL_PRO;

    @Value("gpt://${gpt.cloudId}/yandexgpt-32k/rc")
    public String GPT_MODEL_PRO_32;

    private Map<String, String> results = new HashMap<>();

    @Override
    public String getCompletion(String prompt, String messages, float temperature, ModelLevel modelLevel) throws IOException, InterruptedException {
        charsTotal += prompt.length() + messages.length();
        return "fake " + messages;
    }

    @Override
    public AsyncOperationRequestId getCompletionAsync(String prompt, String messages, float temperature, ModelLevel modelLevel) throws IOException, InterruptedException {
        if (messages == null || messages.trim().isEmpty()) {
            throw new IllegalArgumentException("messages is empty");
        }

        var id = "fake async " + System.nanoTime();
        results.put(id, messages);
        return AsyncOperationRequestId.builder().id(id).build();
    }

    @SneakyThrows
    @Override
    public AsyncOperationResult getCompletionResult(String operationId) {
        var isFinished = Math.random() > 0.1;
        log.info("getCompletionResult {} isFinished {}", operationId, isFinished);
        if (!isFinished) {
            return AsyncOperationResult.builder().id(operationId).isFinished(false).build();
        } else {
            return AsyncOperationResult.builder().id(operationId).isFinished(true).result(results.getOrDefault(operationId, operationId)).build();
        }
    }

    @Override
    public int getTotalTokens() {
        return 0;
    }

    @Override
    public int getTotalChars() {
        return charsTotal;
    }

    @Override
    public void resetStats() {
        totalTokens = 0;
        charsTotal = 0;
    }

}
