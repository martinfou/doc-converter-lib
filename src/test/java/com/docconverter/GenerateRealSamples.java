package com.docconverter;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Generates real-world sample documents for all supported formats.
 *
 * Run once:  mvn exec:java -Dexec.mainClass="com.docconverter.GenerateRealSamples"
 * The generated files are committed to git — NOT generated at test time.
 *
 * Formats generated:
 *   sample-real.docx   — Word with headers, tables, lists, images
 *   sample-real.xlsx   — Excel with data, formulas, formatting
 *   sample-real.pptx   — PowerPoint with slides, tables
 *   sample-real.odt    — ODF text document
 *   sample-real.rtf    — Rich Text Format
 *   sample-real.html   — HTML page with CSS styling
 */
public class GenerateRealSamples {

    static final Path OUTPUT = Path.of("src/test/resources/fixtures");
    static final String[][] BENCH_DATA = {
            {"Library", "Format", "Time (ms)", "Size (bytes)", "Status"},
            {"PDFBox 3.0", "TIFF→PDF", "264", "6891", "✅"},
            {"Tika 3.1", "EML→PDF", "1420", "1182", "✅"},
            {"docx4j 11.5", "DOCX→PDF", "3850", "12450", "⚠️"},
            {"POI 5.4", "DOCX→PDF", "950", "1163", "⚠️"},
            {"pdfbox", "TIFF→PDF", "15", "1909", "✅"},
    };

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        System.out.println("Generating real-world sample documents...");

        generateDocx();
        generateXlsx();
        generatePptx();
        generateOdt();
        generateRtf();
        generateHtml();

        System.out.println("Done — " + countFiles(OUTPUT) + " files in " + OUTPUT);
        for (String name : new String[]{"sample-real.docx", "sample-real.xlsx", "sample-real.pptx",
                "sample-real.odt", "sample-real.rtf", "sample-real.html"}) {
            Path f = OUTPUT.resolve(name);
            System.out.printf("  %s  (%d bytes)%n", name, Files.size(f));
        }
    }

    // ─── WORD DOCX ────────────────────────────────────────────────────

    static void generateDocx() throws IOException {
        var doc = new XWPFDocument();

        // Header
        var header = doc.createHeader(HeaderFooterType.DEFAULT);
        header.createParagraph().createRun().setText("Document Converter Benchmark — Sample Report");

        // Title
        var title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        var tr = title.createRun();
        tr.setBold(true);
        tr.setFontSize(22);
        tr.setText("Document Converter Performance Report");

        // Subtitle
        var sub = doc.createParagraph();
        sub.setAlignment(ParagraphAlignment.CENTER);
        var sr = sub.createRun();
        sr.setItalic(true);
        sr.setFontSize(12);
        sr.setColor("666666");
        sr.setText("Generated: May 2026 — Multi-library comparison");

        // Introduction
        addPara(doc, false, 11,
                "This report compares five Java document conversion libraries across " +
                "multiple formats. Each library was tested on the same source documents " +
                "and measured for conversion speed, output size, and rendering fidelity.");

        // Section header
        var sh = doc.createParagraph();
        var shr = sh.createRun();
        shr.setBold(true);
        shr.setFontSize(14);
        shr.setText("1. Benchmark Results");

        // Data table (real numbers!)
        var table = doc.createTable(BENCH_DATA.length, 5);
        for (int r = 0; r < BENCH_DATA.length; r++) {
            for (int c = 0; c < 5; c++) {
                var cell = table.getRow(r).getCell(c);
                cell.setText(BENCH_DATA[r][c]);
                if (r == 0) {
                    cell.setColor("2B579A");
                    for (var p : cell.getParagraphs())
                        for (var run : p.getRuns()) {
                            run.setColor("FFFFFF");
                            run.setBold(true);
                        }
                }
            }
        }
        table.setWidth("100%");

        // Section 2 — key findings
        doc.createParagraph().createRun().setText(""); // spacer
        var sh2 = doc.createParagraph();
        var sh2r = sh2.createRun();
        sh2r.setBold(true);
        sh2r.setFontSize(14);
        sh2r.setText("2. Key Findings");

        addBullet(doc, "PDFBox is the fastest converter — perfect for TIFF batches.");
        addBullet(doc, "docx4j produces the best layout fidelity but requires FOP setup.");
        addBullet(doc, "POI is text-only; layout is completely lost.");
        addBullet(doc, "Tika handles email extraction well but strips all CSS.");
        addBullet(doc, "ONLYOFFICE (Docker) produces the best overall rendering.");

        // Page break
        doc.createParagraph().setPageBreak(true);

        // Conclusion
        var sh3 = doc.createParagraph();
        var sh3r = sh3.createRun();
        sh3r.setBold(true);
        sh3r.setFontSize(14);
        sh3r.setText("3. Recommendation");

        addPara(doc, false, 11,
                "For production deployments on Cloud Foundry (Tanzu) where system " +
                "binaries cannot be installed, the recommended approach is ONLYOFFICE " +
                "Document Server running as a separate container, accessed via the " +
                "Java integration SDK. This provides faithful rendering for all major " +
                "office formats without modifying the runtime environment.");

        // Footer
        var footer = doc.createFooter(HeaderFooterType.DEFAULT);
        var fp = footer.createParagraph();
        fp.setAlignment(ParagraphAlignment.CENTER);
        var fr = fp.createRun();
        fr.setFontSize(8);
        fr.setColor("999999");
        fr.setText("Confidential — Document Converter Library v1.1");

        // Table of contents-style spacer
        var spacer = doc.createParagraph();
        spacer.setSpacingAfter(200);

        try (var os = new FileOutputStream(OUTPUT.resolve("sample-real.docx").toFile())) {
            doc.write(os);
        }
        doc.close();
    }

    static void addPara(XWPFDocument doc, boolean bold, int size, String text) {
        var p = doc.createParagraph();
        var r = p.createRun();
        r.setBold(bold);
        r.setFontSize(size);
        r.setText(text);
    }

    static void addBullet(XWPFDocument doc, String text) {
        var p = doc.createParagraph();
        p.setIndentationLeft(400);
        var r = p.createRun();
        r.setFontSize(11);
        r.setText("•  " + text);
    }

    // ─── EXCEL XLSX ───────────────────────────────────────────────────

    static void generateXlsx() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Benchmark Results");
            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 3000);
            sheet.setColumnWidth(2, 3000);
            sheet.setColumnWidth(3, 3000);
            sheet.setColumnWidth(4, 2000);

            var headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var headerFont = wb.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Title row
            var titleRow = sheet.createRow(0);
            var tc = titleRow.createCell(0);
            tc.setCellValue("Document Converter Performance — May 2026");
            var ts = wb.createCellStyle();
            var tf = wb.createFont();
            tf.setFontHeightInPoints((short) 14);
            tf.setBold(true);
            ts.setFont(tf);
            tc.setCellStyle(ts);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            // Header row
            var headerRow = sheet.createRow(2);
            String[] cols = {"Library", "Format", "Time (ms)", "Size (bytes)", "Status"};
            for (int i = 0; i < cols.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            var dataStyle = wb.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            for (int r = 0; r < BENCH_DATA.length; r++) {
                var row = sheet.createRow(r + 3);
                for (int c = 0; c < BENCH_DATA[r].length; c++) {
                    var cell = row.createCell(c);
                    cell.setCellValue(BENCH_DATA[r][c]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // Summary
            var sumRow = sheet.createRow(BENCH_DATA.length + 4);
            var sumCell = sumRow.createCell(0);
            sumCell.setCellValue("Total tests: " + BENCH_DATA.length + " rows");
            var sumStyle = wb.createCellStyle();
            var sumFont = wb.createFont();
            sumFont.setBold(true);
            sumFont.setItalic(true);
            sumStyle.setFont(sumFont);
            sumCell.setCellStyle(sumStyle);

            try (var os = new FileOutputStream(OUTPUT.resolve("sample-real.xlsx").toFile())) {
                wb.write(os);
            }
        }
    }

    // ─── POWERPOINT PPTX ─────────────────────────────────────────────

    static void generatePptx() throws IOException {
        try (var ppt = new XMLSlideShow()) {
            // Slide 1: Title
            var titleSlide = ppt.createSlide();
            var titleLayout = titleSlide.getSlideLayout();
            var titleText = titleSlide.createTextBox();
            titleText.setAnchor(new java.awt.Rectangle(50, 100, 600, 100));
            var tp = titleText.addNewTextParagraph();
            var tr = tp.addNewTextRun();
            tr.setText("Document Converter Benchmark");
            tr.setFontSize(36.0);
            tr.setBold(true);

            var subtitleText = titleSlide.createTextBox();
            subtitleText.setAnchor(new java.awt.Rectangle(50, 220, 600, 60));
            var sp = subtitleText.addNewTextParagraph();
            var sr = sp.addNewTextRun();
            sr.setText("May 2026 — Multi-Library Comparison Report");
            sr.setFontSize(18.0);
            sr.setItalic(true);

            // Slide 2: Results table
            var dataSlide = ppt.createSlide();
            var dt = dataSlide.createTextBox();
            dt.setAnchor(new java.awt.Rectangle(30, 30, 640, 30));
            var dtp = dt.addNewTextParagraph();
            var dtr = dtp.addNewTextRun();
            dtr.setText("Benchmark Results");
            dtr.setFontSize(24.0);
            dtr.setBold(true);

            // Simple table representation
            var tableText = dataSlide.createTextBox();
            tableText.setAnchor(new java.awt.Rectangle(30, 70, 640, 350));
            for (var row : BENCH_DATA) {
                var p = tableText.addNewTextParagraph();
                var r = p.addNewTextRun();
                r.setText(String.join("\t", row));
                r.setFontSize(12.0);
            }

            try (var os = new FileOutputStream(OUTPUT.resolve("sample-real.pptx").toFile())) {
                ppt.write(os);
            }
        }
    }

    // ─── ODT (Open Document Text) ─────────────────────────────────────

    static void generateOdt() throws IOException {
        Path path = OUTPUT.resolve("sample-real.odt");
        try (var fos = new FileOutputStream(path.toFile());
             var zos = new ZipOutputStream(fos)) {

            // mimetype (must be first, uncompressed)
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/vnd.oasis.opendocument.text".getBytes());
            zos.closeEntry();

            // content.xml
            zos.putNextEntry(new ZipEntry("content.xml"));
            zos.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <office:document-content
                      xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                      xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                      xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                      office:version="1.2">
                      <office:body>
                        <office:text>
                          <text:h text:style-name="Heading_20_1">Document Converter Benchmark</text:h>
                          <text:p>Real-world ODT sample generated for testing ODT→PDF conversion via ONLYOFFICE.</text:p>
                          <text:p>Supported libraries: PDFBox, Tika, docx4j, POI, ONLYOFFICE</text:p>
                          <text:table table:name="Results" table:style-name="Table1">
                            <text:table-column table:number-columns-repeated="5"/>
                            <text:table-row>
                              <text:table-cell><text:p>Library</text:p></text:table-cell>
                              <text:table-cell><text:p>Format</text:p></text:table-cell>
                              <text:table-cell><text:p>Status</text:p></text:table-cell>
                            </text:table-row>
                            <text:table-row>
                              <text:table-cell><text:p>PDFBox 3.0</text:p></text:table-cell>
                              <text:table-cell><text:p>TIFF→PDF</text:p></text:table-cell>
                              <text:table-cell><text:p>✅</text:p></text:table-cell>
                            </text:table-row>
                            <text:table-row>
                              <text:table-cell><text:p>docx4j 11.5</text:p></text:table-cell>
                              <text:table-cell><text:p>DOCX→PDF</text:p></text:table-cell>
                              <text:table-cell><text:p>⚠️</text:p></text:table-cell>
                            </text:table-row>
                            <text:table-row>
                              <text:table-cell><text:p>ONLYOFFICE DS</text:p></text:table-cell>
                              <text:table-cell><text:p>DOCX→PDF</text:p></text:table-cell>
                              <text:table-cell><text:p>🏆</text:p></text:table-cell>
                            </text:table-row>
                          </text:table>
                        </office:text>
                      </office:body>
                    </office:document-content>
                    """.getBytes());
            zos.closeEntry();

            // META-INF/manifest.xml
            zos.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            zos.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <manifest:manifest
                      xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
                      manifest:version="1.2">
                      <manifest:file-entry manifest:media-type="application/vnd.oasis.opendocument.text" manifest:full-path="/"/>
                      <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="content.xml"/>
                    </manifest:manifest>
                    """.getBytes());
            zos.closeEntry();
        }
    }

    // ─── RTF ──────────────────────────────────────────────────────────

    static void generateRtf() throws IOException {
        try (var w = new PrintWriter(OUTPUT.resolve("sample-real.rtf").toFile(), "UTF-8")) {
            w.println("{\\rtf1\\ansi\\deff0");
            w.println("{\\fonttbl {\\f0 Arial;} {\\f1 Courier New;}}");
            w.println("{\\colortbl;\\red0\\green0\\blue255;\\red128\\green128\\blue128;}");
            w.println("\\pard\\qc{\\b\\fs36 Document Converter Benchmark Report}\\par");
            w.println("\\pard\\qc{\\i\\fs20\\cf2 Generated: May 2026}\\par");
            w.println("\\par");
            w.println("\\pard{\\b\\fs24 1. Results}\\par");
            w.println("\\par");
            w.println("\\pard{\\b Library}\\tab{\\b Format}\\tab{\\b Status}\\par");
            w.println("\\pard PDFBox\\tab TIFF\\rightarrow PDF\\tab {\\cf1 PASS}\\par");
            w.println("\\pard docx4j\\tab DOCX\\rightarrow PDF\\tab {\\cf2 WARN}\\par");
            w.println("\\pard ONLYOFFICE\\tab Multi\\rightarrow PDF\\tab {\\cf1 PASS}\\par");
            w.println("\\par");
            w.println("\\pard{\\b\\fs24 2. Conclusion}\\par");
            w.println("\\par");
            w.println("\\pard ONLYOFFICE Document Server provides the best multi-format\\par");
            w.println("\\pard conversion fidelity for Cloud Foundry deployments.\\par");
            w.println("}");
        }
    }

    // ─── HTML ──────────────────────────────────────────────────────────

    static void generateHtml() throws IOException {
        try (var w = new PrintWriter(OUTPUT.resolve("sample-real.html").toFile(), "UTF-8")) {
            w.println("<!DOCTYPE html>");
            w.println("<html lang=\"en\">");
            w.println("<head>");
            w.println("<meta charset=\"UTF-8\">");
            w.println("<title>Document Converter Benchmark Report</title>");
            w.println("<style>");
            w.println("  body { font-family: Arial, sans-serif; max-width: 800px; margin: 40px auto; line-height: 1.6; color: #333; }");
            w.println("  h1 { color: #2B579A; border-bottom: 2px solid #2B579A; padding-bottom: 8px; }");
            w.println("  h2 { color: #333; margin-top: 30px; }");
            w.println("  table { width: 100%; border-collapse: collapse; margin: 20px 0; }");
            w.println("  th { background: #2B579A; color: white; padding: 10px; text-align: left; }");
            w.println("  td { padding: 8px 10px; border-bottom: 1px solid #ddd; }");
            w.println("  tr:hover { background: #f5f5f5; }");
            w.println("  .pass { color: green; font-weight: bold; }");
            w.println("  .warn { color: orange; font-weight: bold; }");
            w.println("  .fail { color: red; font-weight: bold; }");
            w.println("  .footer { margin-top: 40px; font-size: 12px; color: #999; border-top: 1px solid #ccc; padding-top: 10px; }");
            w.println("</style>");
            w.println("</head>");
            w.println("<body>");
            w.println("<h1>📄 Document Converter Benchmark Report</h1>");
            w.println("<p><em>Generated: May 2026 — Multi-library comparison</em></p>");
            w.println("<h2>1. Benchmark Results</h2>");
            w.println("<table>");
            w.println("<tr><th>Library</th><th>Format</th><th>Time (ms)</th><th>Size</th><th>Status</th></tr>");
            for (var row : BENCH_DATA) {
                w.print("<tr>");
                for (int c = 0; c < row.length; c++) {
                    String cls = c == 4 ? (row[c].equals("✅") ? "pass" : "warn") : "";
                    w.printf("<td class=\"%s\">%s</td>", cls, row[c]);
                }
                w.println("</tr>");
            }
            w.println("</table>");
            w.println("<h2>2. Recommendation</h2>");
            w.println("<p>For Cloud Foundry (Tanzu) deployments, <strong>ONLYOFFICE Document Server</strong> " +
                      "is recommended as a separate container service. The Java SDK communicates " +
                      "with it via HTTP — no system binaries needed in the app container.</p>");
            w.println("<ul>");
            w.println("<li>Supports DOCX, XLSX, PPTX, ODT, RTF, HTML → PDF</li>");
            w.println("<li>Excellent rendering fidelity</li>");
            w.println("<li>Active open-source project (AGPL v3)</li>");
            w.println("</ul>");
            w.println("<div class=\"footer\">Document Converter Library v1.1 — doc-converter-lib</div>");
            w.println("</body>");
            w.println("</html>");
        }
    }

    static int countFiles(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return (int) stream.filter(f -> f.toString().endsWith(".docx") || f.toString().endsWith(".xlsx")
                    || f.toString().endsWith(".pptx") || f.toString().endsWith(".odt")
                    || f.toString().endsWith(".rtf") || f.toString().endsWith(".html")
                    || f.toString().endsWith(".tiff") || f.toString().endsWith(".eml")).count();
        }
    }
}
