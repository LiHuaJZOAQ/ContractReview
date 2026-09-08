package com.contractreview.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DesensitizationUtilTest {

    @ParameterizedTest
    @CsvSource({
        "'甲方：张三，身份证号：110101199001011234', '甲方：***，身份证号：***'",
        "'联系电话：13800138000', '联系电话：***'",
        "'无敏感信息', '无敏感信息'",
        "'', ''",
        "'账号：6222021234567890123', '账号：***'",
        "'卡号：6222021234567890123', '卡号：***'",
        "'户名：1234567890123456789', '户名：***'",
        "'承租人：李四', '承租人：***'",
        "'出租人：王五同志', '出租人：***'",
        "'劳动者：赵六', '劳动者：***'",
        "'用人单位：钱七', '用人单位：***'",
    })
    @DisplayName("常规脱敏场景")
    void testDesensitizeNormal(String input, String expected) {
        assertEquals(expected, DesensitizationUtil.desensitize(input));
    }

    @Test
    @DisplayName("姓名带称谓脱敏")
    void testNameWithTitle() {
        assertEquals("***您好", DesensitizationUtil.desensitize("张三先生您好"));
        assertEquals("***你好", DesensitizationUtil.desensitize("李四女士你好"));
        assertEquals("***再见", DesensitizationUtil.desensitize("王五同志再见"));
    }

    @Test
    @DisplayName("单姓加称谓不脱敏（姓氏1字+先生=3字，需姓名≥2字）")
    void testSingleCharNameNotDesensitized() {
        assertEquals("张先生", DesensitizationUtil.desensitize("张先生"));
    }

    @Test
    @DisplayName("null 输入返回 null")
    void testNullInput() {
        assertNull(DesensitizationUtil.desensitize(null));
    }

    @Test
    @DisplayName("无前后文的纯数字不脱敏")
    void testPlainNumberNotDesensitized() {
        assertEquals("6222021234567890123", DesensitizationUtil.desensitize("6222021234567890123"));
    }

    @Test
    @DisplayName("半角冒号也应脱敏")
    void testHalfWidthColon() {
        assertEquals("甲方：***", DesensitizationUtil.desensitize("甲方：张三"));
        assertEquals("甲方:***", DesensitizationUtil.desensitize("甲方:张三"));
    }

    @Test
    @DisplayName("多规则叠加：身份证 + 手机号 + 银行卡号")
    void testMultipleSensitiveFields() {
        String input = "甲方身份证110101199001011234，手机13800138000，账号：6222021234567890123";
        String result = DesensitizationUtil.desensitize(input);
        assertEquals("甲方******，手机***，账号：***", result);
    }

    @Test
    @DisplayName("仅对头部脱敏：第一条之后正文不处理")
    void testHeaderOnly() {
        String input = "甲方：张三，电话13800138000。\n第一条 标的\n合同编号 ABC-1234567890-2026-001";
        String result = DesensitizationUtil.desensitizeHeader(input);
        assertEquals("甲方：***，电话***。\n第一条 标的\n合同编号 ABC-1234567890-2026-001", result);
    }

    @Test
    @DisplayName("无第X条时 fallback 到前 500 字符")
    void testFallbackToCharLimit() {
        StringBuilder sb = new StringBuilder("甲方：张三，电话13800138000。");
        while (sb.length() < 600) sb.append("无敏感信息行。");
        String input = sb.toString();
        String result = DesensitizationUtil.desensitizeHeader(input);
        assertTrue(result.startsWith("甲方：***，电话***。"));
        // 600+ 字符开始不在脱敏范围
        assertTrue(result.contains("无敏感信息行。无敏感信息行。"));
    }

    @Test
    @DisplayName("正文中 18 位数字不脱敏")
    void testIdCardInBodyNotMasked() {
        String input = "甲方：张三。\n第一条 标的\n合同编号 110101199001011234 应于 2026-01-01 履行";
        String result = DesensitizationUtil.desensitizeHeader(input);
        // 头部"甲方：张三"会被 NAME_AFTER_PARTY_2 脱敏；
        // 正文中 18 位数字即使形似身份证也不应被处理
        String body = result.substring(result.indexOf("第一条"));
        assertEquals("第一条 标的\n合同编号 110101199001011234 应于 2026-01-01 履行", body);
    }

    @Test
    @DisplayName("头部的手机号被脱敏，正文中的不被处理")
    void testPhoneInHeaderMasked() {
        String input = "联系人为张先生 13800138000。\n第一条 标的\n正文 13800138000 不应被处理";
        String result = DesensitizationUtil.desensitizeHeader(input);
        // 头部的姓名被脱敏，电话被替换（无论替换为几个 *** 都算成功）
        assertFalse(result.contains("张先生"));
        // 正文中 13800138000 保持原样（只出现一次，正文那一次）
        int count = (result.length() - result.replace("13800138000", "").length()) / "13800138000".length();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("findHeaderEndIndex 边界")
    void testFindHeaderEndIndex() {
        assertEquals(0, DesensitizationUtil.findHeaderEndIndex(null));
        assertEquals(0, DesensitizationUtil.findHeaderEndIndex(""));
        // 500 字符内无第X条 → 返回 500
        String s = "x".repeat(600);
        assertEquals(500, DesensitizationUtil.findHeaderEndIndex(s));
        // 包含第一条
        String s2 = "前言\n第一条 标的\n正文";
        int idx = DesensitizationUtil.findHeaderEndIndex(s2);
        assertTrue(idx > 0 && idx <= s2.length());
    }
}
