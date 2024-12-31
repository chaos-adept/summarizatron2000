package org.chaosadept.summaryzatron2000.gpt;

import java.io.IOException;

public interface YandexArtClient {
    YandexGPTClient.AsyncOperationRequestId getCompletionAsync(String prompt) throws IOException, InterruptedException;
    YandexGPTClient.AsyncOperationResult getCompletionResult(String operationId);
}
