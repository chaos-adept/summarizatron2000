package org.chaosadept.summaryzatron2000.utils;

import it.tdlight.jni.TdApi;
import org.apache.logging.log4j.util.Strings;

public class MsgUtils {
    public static boolean hasText(TdApi.Message message) {
        return message.content instanceof TdApi.MessageText
                || (message.content instanceof TdApi.MessagePhoto)
                || (message.content instanceof TdApi.MessageVideo);
    }

    public static String extractMsgText(TdApi.Message message) {
        if (message.content instanceof TdApi.MessageText) {
            return ((TdApi.MessageText)message.content).text.text;
        } else if (message.content instanceof TdApi.MessagePhoto) {
            return " ((фото)) " + ((TdApi.MessagePhoto) message.content).caption.text;
        } else if (message.content instanceof TdApi.MessageVideo) {
            return " ((видео)) " + ((TdApi.MessageVideo) message.content).caption.text;
        } else {
            return null;
        }
    }
}
