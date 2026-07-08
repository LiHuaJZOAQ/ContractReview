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
}
