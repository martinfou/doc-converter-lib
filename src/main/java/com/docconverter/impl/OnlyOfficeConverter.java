package com.docconverter.impl;

import com.docconverter.api.ConvertResult;
import com.docconverter.api.DocumentConverter;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * DOCX → PDF converter using ONLYOFFICE Document Server.
 *
 * Requires: ONLYOFFICE Document Server running in Docker:
 *   docker compose -f docker-compose.onlyoffice.yml up -d
 *
 * Architecture:
 *   - Starts a temporary HTTP file server on a random port
 *   - Serves the source file to the Document Server
 *   - Calls ConvertService.ashx API
 *   - Downloads the resulting PDF
 *   - Cleans up the file server
 *
 * Supports all formats that ONLYOFFICE Document Server can convert:
 * DOCX, DOC, ODT, RTF, TXT → PDF (and many more).
 */
public class OnlyOfficeConverter implements DocumentConverter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Base URL of the ONLYOFFICE Document Server. */
    private final String dsBaseUrl;

    /** Timeout for the conversion (seconds). */
    private final int convertTimeoutSec;

    /**
     * Creates a converter pointing to the default Document Server URL.
     */
    public OnlyOfficeConverter() {
        this("http://localhost:3080", 120);
    }

    /**
     * Creates a converter with a custom Document Server URL.
     *
     * @param dsBaseUrl  The Document Server base URL (e.g. "http://localhost:3080")
     * @param timeoutSec Max seconds to wait for conversion
     */
    public OnlyOfficeConverter(String dsBaseUrl, int timeoutSec) {
        this.dsBaseUrl = dsBaseUrl.replaceAll("/+$", "");
        this.convertTimeoutSec = timeoutSec;
    }

    @Override
    public ConvertResult convert(File input, File outputDir) {
        long startTime = System.currentTimeMillis();
        Path inputPath = input.toPath();
        String ext = getExtension(input.getName());

        HttpServer fileServer = null;

        try {
            // 1. Resolve output path
            String baseName = removeExtension(input.getName());
            Path outputPath = outputDir.toPath().resolve(baseName + ".pdf");

            // 2. Start a temporary file server to host the source file
            Path servingDir = inputPath.getParent();
            fileServer = startFileServer(servingDir);
            int port = fileServer.getAddress().getPort();

            // 3. Build the URL for Document Server to fetch the file
            String fileUrl = "http://host.docker.internal:" + port + "/" + input.getName();

            // 4. Call Document Server conversion API
            String resultUrl = convertDocument(fileUrl, ext, "pdf");

            // 5. Download the result
            downloadResult(resultUrl, outputPath);

            long duration = System.currentTimeMillis() - startTime;
            long size = Files.size(outputPath);

            return new ConvertResult(libraryName(), input.getName(), outputPath.toString(),
                    true, duration, size, null);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new ConvertResult(libraryName(), input.getName(), null,
                    false, duration, 0, "FAILED: " + e.getMessage());
        } finally {
            if (fileServer != null) {
                fileServer.stop(0);
            }
        }
    }

    @Override
    public String libraryName() {
        return "ONLYOFFICE Document Server";
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"docx", "doc", "odt", "rtf", "txt", "xlsx", "xls",
                "pptx", "ppt", "csv", "html", "xml"};
    }

    // ─── Internal helpers ─────────────────────────────────────────────

    /** Start a temporary HTTP file server on a random port. */
    private HttpServer startFileServer(Path dir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
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
    private String convertDocument(String fileUrl, String fileType, String outputType) throws Exception {
        String key = UUID.randomUUID().toString();
        String apiUrl = dsBaseUrl + "/ConvertService.ashx";

        ObjectNode body = JSON.createObjectNode();
        body.put("async", false);
        body.put("filetype", fileType);
        body.put("outputtype", outputType);
        body.put("key", key);
        body.put("title", "document." + fileType);
        body.put("url", fileUrl);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(convertTimeoutSec))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new IOException("Conversion API returned HTTP " + res.statusCode() + ": " + res.body());
        }

        JsonNode response = JSON.readTree(res.body());

        if (!response.has("endConvert") || !response.get("endConvert").asBoolean()) {
            int percent = response.has("percent") ? response.get("percent").asInt() : 0;
            throw new IOException("Conversion not complete (percent=" + percent + "): " + res.body());
        }

        JsonNode error = response.get("error");
        if (error != null && error.asInt() != 0) {
            throw new IOException("Document Server error code " + error.asInt() + ": " + res.body());
        }

        return response.get("fileUrl").asText();
    }

    /** Download the converted file from Document Server. */
    private void downloadResult(String resultUrl, Path outputPath) throws Exception {
        String fullUrl = resultUrl.startsWith("http") ? resultUrl : dsBaseUrl + resultUrl;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<Path> res = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(outputPath));

        if (res.statusCode() != 200) {
            throw new IOException("Download returned HTTP " + res.statusCode());
        }
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private String removeExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}
