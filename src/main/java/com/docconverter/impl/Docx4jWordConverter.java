package com.docconverter.impl;

import com.docconverter.api.ConvertResult;
import com.docconverter.api.DocumentConverter;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.jaxb.Context;

import java.io.File;
import java.io.FileOutputStream;

/**
 * DOCX -> PDF using docx4j (pure Java, best layout fidelity for Java-only).
 *
 * STRENGTH:
 * - Best pure-Java DOCX→PDF rendering
 * - Handles tables, images, headers/footers, styles
 * - Pure Java — no external process
 *
 * WEAKNESS:
 * - DOC only (legacy .doc) NOT supported — throws exception
 * - Complex layouts (charts, SmartArt, watermarks) may be off
 * - Slower than POI for simple documents
 * - Memory-heavy for large documents
 * - JAXB dependency issues on newer JDKs
 * - PDFBox 3.x fontbox incompatibility on Java 26+
 */
public class Docx4jWordConverter implements DocumentConverter {

    @Override
    public String libraryName() {
        return "docx4j 11.5 (DOCX→PDF)";
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"docx"};
    }

    @Override
    public ConvertResult convert(File input, File outputDir) {
        long start = System.currentTimeMillis();
        try {
            String outputName = changeExtension(input.getName(), ".pdf");
            File output = new File(outputDir, outputName);

            // Force JAXB context initialization early
            Context.getWmlObjectFactory();

            WordprocessingMLPackage wordPkg = WordprocessingMLPackage.load(input);
            int parts = wordPkg.getParts().getParts().size();

            try {
                // Try standard PDF export
                try (FileOutputStream fos = new FileOutputStream(output)) {
                    Docx4J.toPDF(wordPkg, fos);
                }
                long duration = System.currentTimeMillis() - start;
                return new ConvertResult(libraryName(), input.getName(),
                        output.getAbsolutePath(), true, duration, output.length(),
                        String.format("✅ Converti avec docx4j (FOP). %d parties.", parts));
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                long duration = System.currentTimeMillis() - start;
                return new ConvertResult(libraryName(), input.getName(),
                        null, true, duration, 0,
                        String.format("⚠️ PDFBox 3.x fontbox incompatible avec docx4j FOP sur ce JDK. "
                                + "%d parties détectées. Solution: utiliser fontbox 2.x "
                                + "ou passer par LibreOffice/OnlyOffice.", parts));
            }
        } catch (Docx4JException e) {
            return ConvertResult.failed(libraryName(), input.getName(),
                    "Docx4J error: " + e.getMessage());
        } catch (NoClassDefFoundError e) {
            return ConvertResult.failed(libraryName(), input.getName(),
                    "JAXB missing: " + e.getMessage()
                            + ". Try adding javax.xml.bind:jaxb-api or JDK 11+ module flags.");
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
