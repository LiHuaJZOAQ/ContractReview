package com.contractreview.util;

import java.util.regex.Pattern;

public class DesensitizationUtil {

    private static final Pattern NAME_WITH_TITLE = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,4}(?:先生|女士|同志)");
    private static final Pattern NAME_AFTER_PARTY_2 = Pattern.compile(
            "(甲方|乙方|丙方)([，：:]?\\s*)[\\u4e00-\\u9fa5]{2,4}");
    private static final Pattern NAME_AFTER_PARTY_3 = Pattern.compile(
            "(承租人|出租人|用人单位|劳动者)([，：:]?\\s*)[\\u4e00-\\u9fa5]{2,4}");
    private static final Pattern ID_CARD = Pattern.compile(
            "[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");
    private static final Pattern PHONE = Pattern.compile(
            "1[3-9]\\d{9}");
    private static final Pattern BANK_CARD = Pattern.compile(
            "(?<=账号|卡号|户名)[：:\\s]*\\d{16,19}");

    private static final Pattern CLAUSE_HEADING = Pattern.compile(
            "[\\s\\S]*?第[零一二三四五六七八九十百千0-9]+条");

    private static final int HEADER_FALLBACK_CHARS = 500;

    public static String desensitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        result = NAME_WITH_TITLE.matcher(result).replaceAll("***");
        result = NAME_AFTER_PARTY_2.matcher(result).replaceAll("$1$2***");
        result = NAME_AFTER_PARTY_3.matcher(result).replaceAll("$1$2***");
        result = ID_CARD.matcher(result).replaceAll("***");
        result = PHONE.matcher(result).replaceAll("***");
        result = BANK_CARD.matcher(result).replaceAll(m -> m.group().replaceAll("\\d{16,19}", "***"));
        return result;
    }

    public static int findHeaderEndIndex(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        var matcher = CLAUSE_HEADING.matcher(text);
        if (matcher.find()) {
            return matcher.end();
        }
        return Math.min(HEADER_FALLBACK_CHARS, text.length());
    }

    public static String desensitizeHeader(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int headerEnd = findHeaderEndIndex(text);
        String header = desensitize(text.substring(0, headerEnd));
        return header + text.substring(headerEnd);
    }
}
