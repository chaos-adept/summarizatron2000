package org.chaosadept.summaryzatron2000.model;

import it.tdlight.jni.TdApi;
import lombok.Builder;
import lombok.Data;
import org.chaosadept.summaryzatron2000.gpt.YandexGPTClient;

import java.util.List;

@Data
@Builder
public class AnalyzeResult {
    private String formatedSummaryAsMessage;
    private String chatTitle;
    private long chatId;
    private int totalTokens;
    private int totalChars;
    private List<TdApi.Message> messages;
    private String imagePromntResult;
    private String finalSummary;
    private String summaryMethod;
    private YandexGPTClient.ModelLevel model;
}
