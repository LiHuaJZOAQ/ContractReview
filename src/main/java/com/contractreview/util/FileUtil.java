package com.contractreview.util;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class FileUtil {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    public void validateFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase().endsWith(".pdf"))) {
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

        if (fileName.toLowerCase().endsWith(".pdf")) {
            return extractPdfText(file);
        }
        throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED);
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
}
