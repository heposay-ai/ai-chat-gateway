package com.example.aichat.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentParseService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    private final DocumentParser documentParser;

    public DocumentParseService() {
        // LangChain4j 的 Tika 解析器，支持几乎所有文档格式
        this.documentParser = new ApacheTikaDocumentParser();
    }

    /**
     * 解析上传的文件
     */
    public Document parse(MultipartFile file) throws IOException {
        // LangChain4j 的 Document 对象包含文本和元数据
        Document document = documentParser.parse(file.getInputStream());

        // 补充元数据
        document.metadata()
                .put("file_name", file.getOriginalFilename())
                .put("file_size", String.valueOf(file.getSize()))
                .put("content_type", file.getContentType())
                .put("upload_time", LocalDateTime.now().toString());

        return document;
    }

    /**
     * 解析本地文件
     */
    public Document parseFile(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            Document document = documentParser.parse(is);
            document.metadata()
                    .put("file_name", filePath.getFileName().toString())
                    .put("file_path", filePath.toString());
            return document;
        }
    }

    /**
     * 解析本地文件为一个或多个 Document。PDF 会按页拆分并写入 page metadata。
     */
    public List<Document> parseFileToDocuments(Path filePath, Map<String, String> metadata) throws IOException {
        if (isPdf(filePath)) {
            return parsePdfPages(filePath, metadata);
        }

        Document document = parseFile(filePath);
        metadata.forEach((key, value) -> document.metadata().put(key, value));
        return List.of(document);
    }

    private List<Document> parsePdfPages(Path filePath, Map<String, String> metadata) throws IOException {
        List<Document> documents = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pageCount = pdf.getNumberOfPages();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).trim();
                if (text.isBlank()) {
                    continue;
                }

                Metadata pageMetadata = Metadata.from(metadata);
                pageMetadata.put("page", String.valueOf(page));
                pageMetadata.put("page_number", String.valueOf(page));
                pageMetadata.put("page_count", String.valueOf(pageCount));
                documents.add(Document.from(text, pageMetadata));
            }
        }
        log.info("[文档解析] PDF按页解析完成: file={}, 有效页数={}", filePath.getFileName(), documents.size());
        return documents;
    }

    private boolean isPdf(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }
}
