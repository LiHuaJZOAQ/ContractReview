package com.contractreview.util;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
public class FileUtil {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt");

    public void validateFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !isAllowedExtension(fileName)) {
            throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    public String extractText(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED);
        }

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return extractPdfText(file);
        } else if (lower.endsWith(".docx")) {
            return extractDocxText(file);
        } else if (lower.endsWith(".txt")) {
            return extractTxtText(file);
        }
        throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED);
    }

    private boolean isAllowedExtension(String fileName) {
        String lower = fileName.toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String extractPdfText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            PDDocument document = Loader.loadPDF(bytes);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            if (text == null || text.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "PDF内容为空或无法解析");
            }
            return text;
        } catch (IOException e) {
            log.error("PDF parse failed", e);
            throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "PDF解析失败: " + e.getMessage());
        }
    }

    private String extractDocxText(MultipartFile file) {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            if (text == null || text.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "Word文档内容为空");
            }
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DOCX parse failed", e);
            throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "Word文档解析失败: " + e.getMessage());
        }
    }

    private String extractTxtText(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (text.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "文本文件内容为空");
            }
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("TXT read failed", e);
            throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "文本文件读取失败: " + e.getMessage());
        }
    }
}
