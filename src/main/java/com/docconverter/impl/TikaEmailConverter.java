package com.docconverter.impl;

import com.docconverter.api.ConvertResult;
import com.docconverter.api.DocumentConverter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.metadata.Metadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Email (.eml, .msg) -> PDF using Apache Tika + PDFBox.
 * STRENGTH: Handles EML, MSG, MBOX; extracts headers, body, attachments.
 * WEAKNESS: HTML rendering is text-only (no CSS fidelity); no charset detection issues.
 */
public class TikaEmailConverter implements DocumentConverter {

    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @Override
    public String libraryName() {
        return "Tika 3.1 + PDFBox (EMail→PDF)";
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"eml", "msg", "mbox", "elm"};
    }

    @Override
    public ConvertResult convert(File input, File outputDir) {
        long start = System.currentTimeMillis();
        try {
            String outputName = changeExtension(input.getName(), ".pdf");
            File output = new File(outputDir, outputName);

            // Step 1: Extract content with Tika
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // no size limit
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            try (InputStream is = new FileInputStream(input)) {
                parser.parse(is, handler, metadata, context);
            } catch (org.xml.sax.SAXException e) {
                return ConvertResult.failed(libraryName(), input.getName(),
                        "SAX parse error: " + e.getMessage());
            }

            String bodyText = handler.toString();
            String contentType = metadata.get("Content-Type");
            String subject = metadata.get("Subject");
            String author = metadata.get("Author");
            String from = metadata.get("From");
            String to = metadata.get("To");
            String date = metadata.get("Date");

            if (subject == null) subject = "(no subject)";
            if (contentType == null) contentType = "unknown";
            if (author == null) author = from != null ? from : "—";

            // Step 2: Create PDF with the extracted text
            try (PDDocument pdf = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    float y = 780;
                    int lineHeight = 14;

                    cs.beginText();
                    cs.setFont(FONT_BOLD, 11);
                    cs.newLineAtOffset(50, y);
                    cs.showText("Email: " + subject);
                    y -= 20;

                    cs.setFont(FONT_NORMAL, 9);
                    cs.newLineAtOffset(0, -20);
                    cs.showText("Type: " + contentType);
                    y -= 14;
                    cs.newLineAtOffset(0, -14);
                    cs.showText("From: " + author);
                    y -= 14;
                    cs.newLineAtOffset(0, -14);
                    cs.showText("To: " + nvl(to, "—"));
                    y -= 14;
                    cs.newLineAtOffset(0, -14);
                    cs.showText("Date: " + nvl(date, "—"));
                    y -= 22;
                    cs.newLineAtOffset(0, -22);

                    // Separator
                    cs.setFont(FONT_BOLD, 9);
                    cs.showText("---- Body ----");
                    y -= 18;
                    cs.newLineAtOffset(0, -18);

                    // Body content (text only — Tika strips HTML to plain text)
                    cs.setFont(FONT_NORMAL, 9);
                    if (bodyText.length() > 5000) {
                        cs.showText(bodyText.substring(0, 5000));
                        cs.newLineAtOffset(0, -14);
                        cs.showText("... [truncated at 5000 chars]");
                    } else {
                        String[] lines = bodyText.split("\n");
                        int maxLines = Math.min(lines.length, 55);
                        for (int i = 0; i < maxLines; i++) {
                            String line = lines[i];
                            cs.showText(line.length() > 85 ? line.substring(0, 85) + "…" : line);
                            cs.newLineAtOffset(0, -10);
                        }
                        if (lines.length > maxLines) {
                            cs.showText("... [" + (lines.length - maxLines) + " more lines]");
                        }
                    }
                    cs.endText();
                }

                pdf.save(output);
            }

            long duration = System.currentTimeMillis() - start;
            return new ConvertResult(libraryName(), input.getName(),
                    output.getAbsolutePath(), true, duration, output.length(),
                    String.format("Sujet: «%s». Fidélité: texte seulement (HTML/CSS perdus).",
                            subject));
        } catch (Exception e) {
            return ConvertResult.failed(libraryName(), input.getName(), e.getMessage());
        }
    }

    private static String changeExtension(String name, String toExt) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(0, dot) : name) + toExt;
    }

    private static String nvl(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "—";
    }
}
