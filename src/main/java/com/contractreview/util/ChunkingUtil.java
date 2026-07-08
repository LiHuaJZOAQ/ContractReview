package com.contractreview.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChunkingUtil {

    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
            "(?m)^\\s*第[\\u4e00-\\u9fa5\\d]+[条款].*?(?=\\n\\s*第[\\u4e00-\\u9fa5\\d]+[条款]|\\z)");

    private static final int MAX_CHUNK_LENGTH = 1500;

    public static List<String> chunkByClause(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = CLAUSE_PATTERN.matcher(text);
        List<String> clauses = new ArrayList<>();
        while (matcher.find()) {
            clauses.add(matcher.group().trim());
        }
        if (clauses.isEmpty()) {
            return chunkByLength(text);
        }
        return clauses;
    }

    public static List<String> chunkByLength(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, text.length());
            if (end < text.length()) {
                int breakPoint = text.lastIndexOf('\n', end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }
}
