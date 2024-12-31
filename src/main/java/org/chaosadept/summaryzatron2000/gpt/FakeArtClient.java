package org.chaosadept.summaryzatron2000.gpt;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@Profile({"mock"})
@Component()
@Slf4j
public class FakeArtClient implements YandexArtClient {


    private String fakeBase64;

    @Value("classpath:fake_image_conent_result.jpg")
    private Resource resource;

    @SneakyThrows
    @PostConstruct
    private void init() {
        fakeBase64 = Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
    }

    @Override
    public YandexGPTClient.AsyncOperationRequestId getCompletionAsync(String prompt) throws IOException, InterruptedException {
        return YandexGPTClient.AsyncOperationRequestId.builder().id(UUID.randomUUID().toString()).build();
    }

    @Override
    public YandexGPTClient.AsyncOperationResult getCompletionResult(String operationId) {
        return YandexGPTClient.AsyncOperationResult.builder().id(operationId).isFinished(true).result(fakeBase64).build();
    }
}
