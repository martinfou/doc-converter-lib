package com.docconverter;

import com.docconverter.api.ConvertResult;
import com.docconverter.api.DocumentConverter;
import com.docconverter.impl.Docx4jWordConverter;
import com.docconverter.impl.PdfBoxTiffConverter;
import com.docconverter.impl.PoiWordConverter;
import com.docconverter.impl.TikaEmailConverter;

import java.io.File;
import java.util.*;

/**
 * Runs all converters on all available sample files and prints a comparison table.
 */
public class DemoRunner {

    static final String ANSI_RESET = "\033[0m";
    static final String ANSI_BOLD = "\033[1m";
    static final String ANSI_RED = "\033[31m";
    static final String ANSI_GREEN = "\033[32m";
    static final String ANSI_YELLOW = "\033[33m";
    static final String ANSI_BLUE = "\033[34m";
    static final String ANSI_CYAN = "\033[36m";

    public static void main(String[] args) {
        File samplesDir = new File("src/test/resources/fixtures");
        File outputDir = new File("target/benchmark-output");
        outputDir.mkdirs();

        if (!samplesDir.exists()) {
            System.err.println("Sample directory not found: " + samplesDir.getAbsolutePath());
            System.err.println("Run tests first with: mvn test");
            System.exit(1);
        }

        // Collect sample files
        File[] sampleFiles = samplesDir.listFiles((dir, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".docx") || n.endsWith(".doc")
                    || n.endsWith(".tiff") || n.endsWith(".tif")
                    || n.endsWith(".eml") || n.endsWith(".msg");
        });

        if (sampleFiles == null || sampleFiles.length == 0) {
            System.err.println("No sample files found in " + samplesDir.getAbsolutePath());
            System.exit(1);
        }
        Arrays.sort(sampleFiles);

        // Register all converters
        List<DocumentConverter> converters = Arrays.asList(
                new PdfBoxTiffConverter(),
                new TikaEmailConverter(),
                new Docx4jWordConverter(),
                new PoiWordConverter()
        );

        // Run benchmarks
        System.out.println();
        System.out.println(ANSI_BOLD + ANSI_CYAN + "╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       DOCUMENT CONVERTER LIBRARY — BENCHMARK REPORT      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝" + ANSI_RESET);
        System.out.println();
        System.out.printf("Samples directory: %s%n", samplesDir.getAbsolutePath());
        System.out.printf("Output directory:  %s%n", outputDir.getAbsolutePath());
        System.out.printf("Converters:        %d%n", converters.size());
        System.out.printf("Sample files:      %d%n", sampleFiles.length);
        System.out.println();

        // Group by format
        Map<String, List<File>> byFormat = new TreeMap<>();
        for (File f : sampleFiles) {
            String ext = getExtension(f).toLowerCase();
            byFormat.computeIfAbsent(ext, k -> new ArrayList<>()).add(f);
        }

        System.out.println(ANSI_BOLD + "Files by format:" + ANSI_RESET);
        for (var entry : byFormat.entrySet()) {
            System.out.printf("  .%-6s → %d file(s)%n", entry.getKey(), entry.getValue().size());
            for (File f : entry.getValue()) {
                System.out.printf("           └─ %s (%,d bytes)%n", f.getName(), f.length());
            }
        }
        System.out.println();

        // Process each converter on matching files
        for (DocumentConverter converter : converters) {
            System.out.println(ANSI_BOLD + ANSI_BLUE + "─── " + converter.libraryName() + " ───" + ANSI_RESET);
            Set<String> exts = new HashSet<>(Arrays.asList(converter.supportedExtensions()));

            boolean anyRun = false;
            for (File sample : sampleFiles) {
                String ext = getExtension(sample).toLowerCase();
                if (!exts.contains(ext)) {
                    System.out.printf("  ⏭️  Skip %s (extension .%s not supported)%n", sample.getName(), ext);
                    continue;
                }
                anyRun = true;
                ConvertResult result = converter.convert(sample, outputDir);
                System.out.println(result);
            }

            if (!anyRun) {
                System.out.println("  ⚠️  No matching files for this converter");
            }
            System.out.println();
        }

        // Summary statistics
        System.out.println(ANSI_BOLD + ANSI_GREEN + "══════════════════════════ SUMMARY ══════════════════════════" + ANSI_RESET);
        System.out.println();

        // Re-run all to collect stats
        List<BenchEntry> allResults = new ArrayList<>();
        for (DocumentConverter converter : converters) {
            Set<String> exts = new HashSet<>(Arrays.asList(converter.supportedExtensions()));
            for (File sample : sampleFiles) {
                String ext = getExtension(sample).toLowerCase();
                if (!exts.contains(ext)) continue;
                ConvertResult result = converter.convert(sample, outputDir);
                allResults.add(new BenchEntry(
                        converter.libraryName(),
                        sample.getName(),
                        result.success(),
                        result.durationMs(),
                        result.outputSizeBytes()));
            }
        }

        // Print table
        System.out.printf(ANSI_BOLD + "%-30s %-20s %-10s %-12s %s%n" + ANSI_RESET,
                "Library", "File", "Status", "Time (ms)", "Size (bytes)");

        long totalSuccess = 0, totalFail = 0;
        for (BenchEntry entry : allResults) {
            String status = entry.success ? ANSI_GREEN + "✅ OK" + ANSI_RESET : ANSI_RED + "❌ FAIL" + ANSI_RESET;
            System.out.printf("%-30s %-20s %-10s %-12d %d%n",
                    entry.library, entry.file, status, entry.timeMs, entry.sizeBytes);
            if (entry.success) totalSuccess++;
            else totalFail++;
        }

        System.out.println();
        System.out.printf("Total: %d ✅ success, %d ❌ failed out of %d%n",
                totalSuccess, totalFail, totalSuccess + totalFail);

        long fastest = allResults.stream()
                .filter(e -> e.success)
                .mapToLong(e -> e.timeMs)
                .min().orElse(0);
        long slowest = allResults.stream()
                .filter(e -> e.success)
                .mapToLong(e -> e.timeMs)
                .max().orElse(0);
        System.out.printf("Fastest conversion: %d ms | Slowest: %d ms%n", fastest, slowest);

        System.out.println();
        System.out.println(ANSI_YELLOW + "📋 Verdict :" + ANSI_RESET);
        System.out.println("  ✅ PDFBox pour TIFF → PDF : parfait, rapide, Java pur.");
        System.out.println("  ✅ Tika pour Email → PDF : extraction correcte mais rendu texte seulement.");
        System.out.println("  ⚠️  docx4j pour DOCX → PDF : meilleur rendu Java pur, mais complexe à configurer.");
        System.out.println("  ❌ POI pour Word → PDF : perte totale de mise en page. Fallback seulement.");
        System.out.println("  🏆 LibreOffice/OnlyOffice manquent (besoin d'exécutable externe).");
        System.out.println();
        System.out.println(ANSI_BOLD + "Output PDFs: " + outputDir.getAbsolutePath() + ANSI_RESET);
    }

    static String getExtension(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : "";
    }

    record BenchEntry(String library, String file, boolean success, long timeMs, long sizeBytes) {}
}
