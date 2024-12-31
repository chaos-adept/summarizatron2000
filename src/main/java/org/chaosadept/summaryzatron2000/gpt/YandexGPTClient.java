package org.chaosadept.summaryzatron2000.gpt;

import lombok.Builder;
import lombok.Data;

import java.io.IOException;

public interface YandexGPTClient {
    String getCompletion(String prompt, String messages, float temperature, ModelLevel modelLevel) throws IOException, InterruptedException;
    AsyncOperationRequestId getCompletionAsync(String prompt, String messages, float temperature, ModelLevel modelLevel) throws IOException, InterruptedException;
    AsyncOperationResult getCompletionResult(String operationId);

    int getTotalTokens();

    int getTotalChars();

    void resetStats();

    enum ModelLevel {
        PRO,
        LITE
    }

    @Data
    @Builder
    public class AsyncOperationRequestId {
        String id;
    }

    @Data
    @Builder
    public class AsyncOperationResult {
        String id;
        boolean isFinished = false;
        boolean hasError = false;
        String errorMessage;
        String result;
        int totalTokens;
    }

}
