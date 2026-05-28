package com.docconverter.impl;

import com.docconverter.api.ConvertResult;
import com.docconverter.api.DocumentConverter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * DOC/DOCX -> PDF using Apache POI + OpenPDF.
 *
 * STRENGTH:
 * - Supports both .doc AND .docx (only pure Java converter that does)
 * - Fast, lightweight
 * - Pure Java
 *
 * WEAKNESS:
 * - TEXT-ONLY extraction — NO layout, NO images, NO tables, NO headers
 * - Rich formatting is completely lost
 * - Demonstrates WHY you need LibreOffice/OnlyOffice for real Word→PDF
 * - Best used as fallback when docx4j can't handle a format
 */
public class PoiWordConverter implements DocumentConverter {

    @Override
    public String libraryName() {
        return "POI 5.4 + OpenPDF (DOC→PDF)";
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"docx", "doc"};
    }

    @Override
    public ConvertResult convert(File input, File outputDir) {
        long start = System.currentTimeMillis();
        try {
            String outputName = changeExtension(input.getName(), ".pdf");
            File output = new File(outputDir, outputName);
            String text;
            String format;

            // Step 1: Extract text with POI
            String name = input.getName().toLowerCase();
            if (name.endsWith(".docx")) {
                format = "DOCX";
                try (XWPFDocument doc = new XWPFDocument(new FileInputStream(input));
                     XWPFWordExtractor ext = new XWPFWordExtractor(doc)) {
                    text = ext.getText();
                }
            } else if (name.endsWith(".doc")) {
                format = "DOC (legacy)";
                try (HWPFDocument doc = new HWPFDocument(new FileInputStream(input));
                     WordExtractor ext = new WordExtractor(doc)) {
                    text = ext.getText();
                }
            } else {
                return ConvertResult.failed(libraryName(), input.getName(),
                        "Unsupported format. Only .doc and .docx supported.");
            }

            // Step 2: Create PDF from plain text (no layout fidelity)
            if (text == null || text.trim().isEmpty()) {
                return ConvertResult.failed(libraryName(), input.getName(),
                        "POI extracted empty text from document");
            }

            try (com.lowagie.text.Document pdfDoc = new com.lowagie.text.Document()) {
                PdfWriter.getInstance(pdfDoc, new FileOutputStream(output));
                pdfDoc.open();

                Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
                Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

                pdfDoc.add(new Paragraph("[" + format + "] " + input.getName(), headerFont));
                pdfDoc.add(new Paragraph("Converted with Apache POI + OpenPDF (TEXT ONLY)", bodyFont));
                pdfDoc.add(Chunk.NEWLINE);

                String[] lines = text.split("\n");
                for (String line : lines) {
                    pdfDoc.add(new Paragraph(line.length() > 80 ? line.substring(0, 80) + "…" : line, bodyFont));
                }
            }

            long duration = System.currentTimeMillis() - start;
            return new ConvertResult(libraryName(), input.getName(),
                    output.getAbsolutePath(), true, duration, output.length(),
                    String.format("Format: %s. Fidélité: TRÈS FAIBLE (texte brut uniquement). "
                            + "Aucune mise en page, image, tableau ou style préservé. "
                            + "Démontre la limite des librairies Java pures pour Word→PDF.",
                            format));
        } catch (IOException e) {
            return ConvertResult.failed(libraryName(), input.getName(),
                    "IO Error: " + e.getMessage());
        } catch (DocumentException e) {
            return ConvertResult.failed(libraryName(), input.getName(),
                    "OpenPDF Error: " + e.getMessage());
        } catch (Exception e) {
            return ConvertResult.failed(libraryName(), input.getName(),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String changeExtension(String name, String toExt) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(0, dot) : name) + toExt;
    }
}
