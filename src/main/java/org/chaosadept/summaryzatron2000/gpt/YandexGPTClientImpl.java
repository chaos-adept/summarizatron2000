package org.chaosadept.summaryzatron2000.gpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@Profile({"GPT"})
@Component()
@Slf4j
public class YandexGPTClientImpl implements YandexGPTClient {
    private static final String API_URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public int totalTokens = 0;
    public int totalChars = 0;

    @Value("${gpt.api-key}")
    private String API_KEY;

    @Value("gpt://${gpt.cloudId}/yandexgpt-lite/latest")
    public String GPT_MODEL_RC;

    @Value("gpt://${gpt.cloudId}/yandexgpt/latest")
    public String GPT_MODEL_PRO;

    public YandexGPTClientImpl() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getCompletion(String prompt, String messages, float temperature, ModelLevel modelLevel) throws IOException, InterruptedException {
        // Создаем JSON тело запроса
        //ObjectNode requestBody = ;

        Thread.sleep(1000); //fixme replace with lath

        String requestBodyJson = ("{\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"text\": %s,\n".formatted(objectMapper.writeValueAsString(prompt)) +
                "      \"role\": \"system\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"text\": %s,\n".formatted(objectMapper.writeValueAsString(messages)) +
                "      \"role\": \"user\" \n" +
                "    }\n" +
                "  ],\n" +
                "  \"completionOptions\": {\n" +
                "    \"stream\": false,\n" +
                "    \"maxTokens\":  2000,\n" +
                "    \"temperature\":  %s".formatted(temperature) +
                "  },\n" +
                "  \"modelUri\": \"" + getModel(modelLevel) + "\"\n" +
                "}");

        totalChars += prompt.length() + messages.length();

        // Создаем запрос
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        // Выполняем запрос и получаем ответ
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Обрабатываем JSON ответ
        if (response.statusCode() == 200) {
            ObjectNode responseBody = objectMapper.readValue(response.body(), ObjectNode.class);
            var tokens = responseBody.get("result").get("usage").get("totalTokens").asInt();
            totalTokens += tokens;
            return responseBody.get("result").get("alternatives").get(0).get("message").get("text").asText();
        } else {
            throw new RuntimeException("Error: " + response.statusCode() + ", Response: " + response.body());
        }
    }

    @Override
    public AsyncOperationRequestId getCompletionAsync(String prompt, String messages, float temperature, ModelLevel modelLevel) throws IOException, InterruptedException {
        Thread.sleep(500); //fixme replace with lath

        String requestBodyJson = ("{\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"text\": %s,\n".formatted(objectMapper.writeValueAsString(prompt)) +
                "      \"role\": \"system\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"text\": %s,\n".formatted(objectMapper.writeValueAsString(messages)) +
                "      \"role\": \"user\" \n" +
                "    }\n" +
                "  ],\n" +
                "  \"completionOptions\": {\n" +
                "    \"stream\": false,\n" +
                "    \"maxTokens\":  2000,\n" +
                "    \"temperature\":  %s".formatted(temperature) +
                "  },\n" +
                "  \"modelUri\": \"" + getModel(modelLevel) + "\"\n" +
                "}");

        // Создаем запрос
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://llm.api.cloud.yandex.net/foundationModels/v1/completionAsync"))
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        // Выполняем запрос и получаем ответ
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Обрабатываем JSON ответ
        if (response.statusCode() == 200) {
            ObjectNode responseBody = objectMapper.readValue(response.body(), ObjectNode.class);
            var operationId = responseBody.get("id").asText();
            return AsyncOperationRequestId.builder().id(operationId).build();
        } else {
            throw new RuntimeException("Error: " + response.statusCode() + ", Response: " + response.body());
        }
    }

    @SneakyThrows
    @Override
    public AsyncOperationResult getCompletionResult(String operationId) {

        Thread.sleep(500);

        // Создаем запрос
        HttpRequest fetchImageRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://operation.api.cloud.yandex.net/operations/%s".formatted(operationId)))
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Content-Type", "application/json")
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();

        // Выполняем запрос и получаем ответ
        HttpResponse<String> pullOperationResultResponse;
        ObjectNode operationResponseJson;
        try {
            pullOperationResultResponse = httpClient.send(fetchImageRequest, HttpResponse.BodyHandlers.ofString());
            operationResponseJson = objectMapper.readValue(pullOperationResultResponse.body(), ObjectNode.class);
        } catch (Exception e) {
            log.error("error during fetch result", e);
            throw e;
        }

        var done = operationResponseJson.get("done").asBoolean();

        if (done && operationResponseJson.has("response")) {
            var tokens = operationResponseJson.get("response").get("usage").get("totalTokens").asInt();
            var totalTokens = tokens;
            var result = operationResponseJson.get("response").get("alternatives").get(0).get("message").get("text").asText();
            return AsyncOperationResult.builder().id(operationId).isFinished(true).result(result).totalTokens(totalTokens).build();
        } else if (done && operationResponseJson.has("error")) {
            //throw new RuntimeException("Cant retrive reponse: " + operationResponseJson.get("error").asText());
            log.error("Cant retrive reponse: " + operationResponseJson.get("error").asText());
            return AsyncOperationResult.builder().id(operationId).hasError(true).errorMessage(operationResponseJson.get("error").asText()).isFinished(true).build();
        }

        return AsyncOperationResult.builder().id(operationId).isFinished(false).build();
    }

    private String getModel(ModelLevel modelLevel) {
        return modelLevel == ModelLevel.LITE ? GPT_MODEL_RC : GPT_MODEL_PRO;
    }

    @Override
    public int getTotalTokens() {
        return totalTokens;
    }

    @Override
    public int getTotalChars() {
        return totalChars;
    }

    @Override
    public void resetStats() {
        totalChars = 0;
        totalTokens = 0;
    }
}
