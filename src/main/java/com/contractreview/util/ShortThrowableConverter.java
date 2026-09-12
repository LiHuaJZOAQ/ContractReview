package com.contractreview.util;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.CoreConstants;

public class ShortThrowableConverter extends ThrowableHandlingConverter {

    private static final int MAX_MESSAGE_LEN = 200;
    private static final int MAX_FRAMES = 5;

    @Override
    public String convert(ILoggingEvent event) {
        IThrowableProxy tp = event.getThrowableProxy();
        if (tp == null) return CoreConstants.EMPTY_STRING;

        StringBuilder sb = new StringBuilder();
        sb.append(CoreConstants.LINE_SEPARATOR);

        Throwable t = getThrowable(tp);
        String className = tp.getClassName();
        String message = tp.getMessage();

        sb.append(className);
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(truncateMessage(message));
        }

        StackTraceElementProxy[] frames = tp.getStackTraceElementProxyArray();
        int max = Math.min(frames.length, MAX_FRAMES);
        for (int i = 0; i < max; i++) {
            sb.append(CoreConstants.LINE_SEPARATOR).append("\tat ").append(frames[i].getSTEAsString());
        }
        if (frames.length > MAX_FRAMES) {
            sb.append(CoreConstants.LINE_SEPARATOR).append("\t... ").append(frames.length - MAX_FRAMES).append(" more");
        }

        IThrowableProxy cause = tp.getCause();
        if (cause != null && cause != tp) {
            sb.append(CoreConstants.LINE_SEPARATOR).append("Caused by: ").append(cause.getClassName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(truncateMessage(cause.getMessage()));
            }
        }

        if (t != null) {
            Throwable c = t.getCause();
            while (c != null) {
                sb.append(CoreConstants.LINE_SEPARATOR).append("Caused by: ")
                        .append(c.getClass().getName()).append(": ")
                        .append(truncateMessage(c.getMessage()));
                c = c.getCause();
                if (c == t) break;
            }
        }

        return sb.toString();
    }

    private static String truncateMessage(String message) {
        if (message == null) return "";
        if (message.length() <= MAX_MESSAGE_LEN) return message;
        return message.substring(0, MAX_MESSAGE_LEN) + "...[truncated " + (message.length() - MAX_MESSAGE_LEN) + " chars]";
    }

    private static Throwable getThrowable(IThrowableProxy tp) {
        if (tp instanceof ch.qos.logback.classic.spi.ThrowableProxy) {
            return ((ch.qos.logback.classic.spi.ThrowableProxy) tp).getThrowable();
        }
        return null;
    }
}
