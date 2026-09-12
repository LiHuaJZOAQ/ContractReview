package com.contractreview.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class LogTruncator {

    private static final int DEFAULT_HEAD = 200;
    private static final int DEFAULT_TAIL = 200;

    private LogTruncator() {
    }

    public static String truncate(String msg, int maxLen) {
        if (msg == null) return null;
        if (msg.length() <= maxLen) return msg;
        int head = Math.min(DEFAULT_HEAD, maxLen / 2);
        int tail = Math.min(DEFAULT_TAIL, maxLen - head - 20);
        if (tail < 0) tail = 0;
        return msg.substring(0, head)
                + "...[truncated " + (msg.length() - head - tail) + " chars]..."
                + msg.substring(msg.length() - tail);
    }

    public static String truncate(String msg) {
        return truncate(msg, 500);
    }

    public static String truncateStack(Throwable t) {
        return truncateStack(t, 2000);
    }

    public static String truncateStack(Throwable t, int maxLen) {
        if (t == null) return null;
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
        }
        String full = sw.toString();
        return truncate(full, maxLen);
    }

    public static String truncateHtml(String html, int maxLen) {
        if (html == null) return null;
        String stripped = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return truncate(stripped, maxLen);
    }
}
