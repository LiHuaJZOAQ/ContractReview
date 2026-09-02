package com.contractreview.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogTruncatorTest {

    @Test
    @DisplayName("短文本不截断")
    void testShortStringNotTruncated() {
        String shortMsg = "hello world";
        assertEquals(shortMsg, LogTruncator.truncate(shortMsg, 500));
    }

    @Test
    @DisplayName("null 文本返回 null")
    void testNullReturnsNull() {
        assertNull(LogTruncator.truncate(null, 500));
        assertNull(LogTruncator.truncateStack(null));
        assertNull(LogTruncator.truncateHtml(null, 500));
    }

    @Test
    @DisplayName("长文本截断后含首尾")
    void testLongStringTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append('a');
        String truncated = LogTruncator.truncate(sb.toString(), 200);
        assertTrue(truncated.length() < 250, "truncated length should be reasonable");
        assertTrue(truncated.contains("[truncated"), "should contain truncation marker");
        assertTrue(truncated.startsWith("a"), "should start with original prefix");
        assertTrue(truncated.endsWith("a"), "should end with original suffix");
    }

    @Test
    @DisplayName("截断堆栈保留首尾")
    void testStackTruncated() {
        Exception e = new RuntimeException("boom");
        for (int i = 0; i < 100; i++) e.addSuppressed(new RuntimeException("suppressed-" + i));
        String result = LogTruncator.truncateStack(e, 1000);
        assertNotNull(result);
        assertTrue(result.length() < 1500);
        assertTrue(result.contains("boom"));
    }

    @Test
    @DisplayName("HTML 去标签后截断")
    void testHtmlStrippedAndTruncated() {
        String html = "<html><body><h1>Error</h1><p>" + "x".repeat(2000) + "</p></body></html>";
        String result = LogTruncator.truncateHtml(html, 300);
        assertNotNull(result);
        assertTrue(result.length() < 400);
        assertFalse(result.contains("<"), "html tags should be removed");
        assertTrue(result.contains("Error"));
    }
}
