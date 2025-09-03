package com.juca.util; // 패키지 경로에 맞게 수정

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogUtil.class);

    public static void logSchedulerStart(String schedulerName, String methodName) {
        LOGGER.info("[SCHEDULER_START] {} - Method: {}", schedulerName, methodName);
    }

    public static void logSchedulerCompletion(String schedulerName, String methodName, String message) {
        LOGGER.info("[SCHEDULER_COMPLETION] {} - Method: {} - Result: {}", schedulerName, methodName, message);
    }

    public static void logSchedulerException(String schedulerName, String methodName, Throwable e, String additionalMessage) {
        String msg = String.format("[SCHEDULER_EXCEPTION] %s - Method: %s - Error: %s",
                schedulerName, methodName, additionalMessage != null ? additionalMessage : "An unexpected error occurred.");
        LOGGER.error(msg, e);
    }

    public static void logInfo(String message) {
        LOGGER.info(message);
    }

    public static void logError(String message, Throwable e) {
        LOGGER.error(message, e);
    }
}