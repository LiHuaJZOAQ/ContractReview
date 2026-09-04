package com.contractreview.util;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.CoreConstants;

public class FullThrowableConverter extends ThrowableHandlingConverter {

    @Override
    public String convert(ILoggingEvent event) {
        IThrowableProxy tp = event.getThrowableProxy();
        if (tp == null) return CoreConstants.EMPTY_STRING;

        StringBuilder sb = new StringBuilder();
        sb.append(CoreConstants.LINE_SEPARATOR);

        String className = tp.getClassName();
        String message = tp.getMessage();

        sb.append(className);
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }

        StackTraceElementProxy[] frames = tp.getStackTraceElementProxyArray();
        for (StackTraceElementProxy frame : frames) {
            sb.append(CoreConstants.LINE_SEPARATOR).append("\tat ").append(frame.getSTEAsString());
        }

        IThrowableProxy cause = tp.getCause();
        if (cause != null && cause != tp) {
            appendCause(sb, cause, 1);
        }

        Throwable t = getThrowable(tp);
        if (t != null) {
            Throwable c = t.getCause();
            int depth = 1;
            while (c != null) {
                if (c == t) break;
                appendThrowable(sb, c, depth);
                c = c.getCause();
                depth++;
            }
        }

        return sb.toString();
    }

    private void appendCause(StringBuilder sb, IThrowableProxy cause, int depth) {
        sb.append(CoreConstants.LINE_SEPARATOR);
        for (int i = 0; i < depth; i++) sb.append("Caused by: ");
        sb.append(cause.getClassName());
        if (cause.getMessage() != null) {
            sb.append(": ").append(cause.getMessage());
        }
        StackTraceElementProxy[] frames = cause.getStackTraceElementProxyArray();
        for (StackTraceElementProxy frame : frames) {
            sb.append(CoreConstants.LINE_SEPARATOR).append("\tat ").append(frame.getSTEAsString());
        }
        IThrowableProxy next = cause.getCause();
        if (next != null && next != cause) {
            appendCause(sb, next, depth + 1);
        }
    }

    private void appendThrowable(StringBuilder sb, Throwable t, int depth) {
        sb.append(CoreConstants.LINE_SEPARATOR);
        for (int i = 0; i < depth; i++) sb.append("Caused by: ");
        sb.append(t.getClass().getName());
        if (t.getMessage() != null) {
            sb.append(": ").append(t.getMessage());
        }
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append(CoreConstants.LINE_SEPARATOR).append("\tat ").append(element.toString());
        }
    }

    private static Throwable getThrowable(IThrowableProxy tp) {
        if (tp instanceof ch.qos.logback.classic.spi.ThrowableProxy) {
            return ((ch.qos.logback.classic.spi.ThrowableProxy) tp).getThrowable();
        }
        return null;
    }
}
