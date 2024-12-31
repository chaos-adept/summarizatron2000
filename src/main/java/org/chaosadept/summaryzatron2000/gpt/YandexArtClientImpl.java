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

@Profile({"GPT"})
@Component()
@Slf4j
public class YandexArtClientImpl implements YandexArtClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;


    @Value("${gpt.cloudId}")
    public String cloudId;

    @Value("${gpt.api-key}")
    private String API_KEY;

    public YandexArtClientImpl() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public YandexGPTClient.AsyncOperationRequestId getCompletionAsync(String prompt) throws IOException, InterruptedException {


        String requestBodyJson = (("{\"model_uri\":\"art://%s/yandex-art/latest\",".formatted(cloudId) +
                "\"messages\":[{\"text\":%s,\"weight\":\"1\"}]," +
                "\"generation_options\":{\"mime_type\":\"image/jpg\",\"seed\":\"1863\",     " +
                "       \"aspectRatio\": {\n" +
                "           \"widthRatio\": \"2\",\n" +
                "           \"heightRatio\": \"1\"\n" +
                "    }}}").formatted(objectMapper.writeValueAsString(prompt)));


        // Создаем запрос
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://llm.api.cloud.yandex.net/foundationModels/v1/imageGenerationAsync"))
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
            //<идентификатор_операции>

            return YandexGPTClient.AsyncOperationRequestId.builder().id(operationId).build();
        } else {
            throw new RuntimeException("Error: " + response.statusCode() + ", Response: " + response.body());
        }
    }

    @SneakyThrows
    @Override
    public YandexGPTClient.AsyncOperationResult getCompletionResult(String operationId) {

        HttpRequest fetchImageRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://llm.api.cloud.yandex.net:443/operations/%s".formatted(operationId)))
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        // Выполняем запрос и получаем ответ
        HttpResponse<String> fetchImageResult = httpClient.send(fetchImageRequest, HttpResponse.BodyHandlers.ofString());
        ObjectNode fetchImageResultBody = objectMapper.readValue(fetchImageResult.body(), ObjectNode.class);

        var done = fetchImageResultBody.get("done").asBoolean();

        if (done && fetchImageResultBody.has("response")) {
            var result = fetchImageResultBody.get("response").get("image").asText();
            return YandexGPTClient.AsyncOperationResult.builder().result(result).isFinished(true).hasError(false).build();
        } else if (done && fetchImageResultBody.has("error")) {
            return YandexGPTClient.AsyncOperationResult.builder().isFinished(true).hasError(true).errorMessage(fetchImageResultBody.get("error").asText()).build();
        }

        return null;
    }

}
