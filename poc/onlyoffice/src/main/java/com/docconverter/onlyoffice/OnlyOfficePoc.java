package com.docconverter.onlyoffice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;

/**
 * Proof-of-Concept: ONLYOFFICE Document Server DOCX → PDF conversion.
 *
 * Architecture:
 *   1. Starts a temporary HTTP file server (Java built-in) on port 9876
 *   2. Serves a test DOCX file to the Document Server
 *   3. Calls the Document Server ConvertService.ashx API
 *   4. Downloads the resulting PDF
 *
 * Requires: ONLYOFFICE Document Server running in Docker:
 *   docker compose -f docker-compose.onlyoffice.yml up -d
 */
public class OnlyOfficePoc {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final int FILE_SERVER_PORT = 9876;
    private static final String DS_BASE = "http://localhost:3080";
    private static final String CONVERT_API = DS_BASE + "/ConvertService.ashx";

    private static Path testDocx;
    private static Path outputDir;
    private static HttpServer fileServer;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   ONLYOFFICE Document Converter — POC      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        // ── Step 1: Verify Document Server is running ──
        System.out.print("[1/5] Checking Document Server... ");
        if (!checkDocumentServer()) {
            System.out.println("❌ NOT REACHABLE");
            System.out.println("   Run: docker compose -f docker-compose.onlyoffice.yml up -d");
            System.exit(1);
        }
        System.out.println("✅ http://localhost:3080");

        // ── Step 2: Create a test DOCX ──
        System.out.print("[2/5] Creating test document... ");
        outputDir = Path.of("target/onlyoffice-poc");
        Files.createDirectories(outputDir);
        testDocx = outputDir.resolve("hello-onlyoffice.docx");
        createTestDocx(testDocx);
        System.out.println("✅ " + testDocx.getFileName());

        // ── Step 3: Start temporary file server ──
        System.out.print("[3/5] Starting file server on port " + FILE_SERVER_PORT + "... ");
        fileServer = startFileServer(testDocx.getParent());
        System.out.println("✅");

        // ── Step 4: Call the conversion API ──
        System.out.print("[4/5] Converting DOCX → PDF... ");
        String fileUrl = "http://host.docker.internal:" + FILE_SERVER_PORT + "/" + testDocx.getFileName();
        String resultUrl = convertDocument(fileUrl, "docx", "pdf");
        System.out.println("✅ converted");

        // ── Step 5: Download result ──
        System.out.print("[5/5] Downloading PDF... ");
        Path pdfOutput = outputDir.resolve("hello-onlyoffice.pdf");
        downloadResult(resultUrl, pdfOutput);
        System.out.println("✅ " + pdfOutput.toAbsolutePath().normalize());
        System.out.println();

        // ── Done ──
        fileServer.stop(0);
        long size = Files.size(pdfOutput);
        System.out.println("🎉 POC COMPLETE!");
        System.out.println("   Output: " + pdfOutput.toAbsolutePath().normalize());
        System.out.println("   Size:   " + size + " bytes");
        System.out.println("   Try:    xdg-open " + pdfOutput.toAbsolutePath().normalize());
        System.out.println();
        System.exit(0);
    }

    /** Check if Document Server is alive. */
    static boolean checkDocumentServer() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(DS_BASE + "/healthcheck"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** Create a simple DOCX with Apache POI. */
    static void createTestDocx(Path path) throws Exception {
        try (java.io.OutputStream os = Files.newOutputStream(path)) {
            // Minimal valid DOCX (Office Open XML) - enough for a conversion test
            var zip = new java.util.zip.ZipOutputStream(os);

            // [Content_Types].xml
            zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes()
            );

            // _rels/.rels
            zip.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
            zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """.getBytes()
            );

            // word/_rels/document.xml.rels
            zip.putNextEntry(new java.util.zip.ZipEntry("word/_rels/document.xml.rels"));
            zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                    """.getBytes()
            );

            // word/document.xml
            zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p>
                          <w:r>
                            <w:t>ONLYOFFICE Document Server — POC réussi!</w:t>
                          </w:r>
                        </w:p>
                        <w:p>
                          <w:r>
                            <w:t>Ce document a été converti via l'API ONLYOFFICE.</w:t>
                          </w:r>
                        </w:p>
                      </w:body>
                    </w:document>
                    """.getBytes()
            );

            zip.finish();
        }
    }

    /** Start a temporary HTTP file server on the given directory. */
    static HttpServer startFileServer(Path dir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(FILE_SERVER_PORT), 0);
        server.createContext("/", exchange -> {
            try {
                String filePath = exchange.getRequestURI().getPath().substring(1);
                Path file = dir.resolve(filePath).normalize();
                if (!file.startsWith(dir.normalize())) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                if (Files.exists(file) && !Files.isDirectory(file)) {
                    exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, Files.size(file));
                    try (OutputStream os = exchange.getResponseBody()) {
                        Files.copy(file, os);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    /** Call Document Server ConvertService.ashx and return the result file URL. */
    static String convertDocument(String fileUrl, String fileType, String outputType) throws Exception {
        String key = UUID.randomUUID().toString();

        ObjectNode body = JSON.createObjectNode();
        body.put("async", false);
        body.put("filetype", fileType);
        body.put("outputtype", outputType);
        body.put("key", key);
        body.put("title", "document." + fileType);
        body.put("url", fileUrl);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(CONVERT_API))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Conversion failed: HTTP " + res.statusCode() + " — " + res.body());
        }

        JsonNode response = JSON.readTree(res.body());
        if (!response.has("endConvert") || !response.get("endConvert").asBoolean()) {
            // Async would need polling — simple POC uses sync mode
            int percent = response.has("percent") ? response.get("percent").asInt() : 0;
            throw new RuntimeException("Conversion in progress? percent=" + percent + " — body: " + res.body());
        }

        JsonNode error = response.get("error");
        if (error != null && error.asInt() != 0) {
            throw new RuntimeException("Document Server error code: " + error.asInt() + " — body: " + res.body());
        }

        return response.get("fileUrl").asText();
    }

    /** Download the converted file from Document Server. */
    static void downloadResult(String resultUrl, Path outputPath) throws Exception {
        // Result URL is relative to Document Server
        String fullUrl;
        if (resultUrl.startsWith("http")) {
            fullUrl = resultUrl;
        } else {
            fullUrl = DS_BASE + resultUrl;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<Path> res = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(outputPath));

        if (res.statusCode() != 200) {
            throw new RuntimeException("Download failed: HTTP " + res.statusCode());
        }
    }
}
