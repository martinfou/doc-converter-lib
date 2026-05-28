package com.docconverter.impl;

import com.docconverter.api.ConvertResult;
import com.docconverter.api.DocumentConverter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * TIFF -> PDF using pure Java (PDFBox + ImageIO).
 * Supports multi-page TIFF, all common compression types.
 * STRENGTH: Perfect conversion, Java-only, fast.
 * WEAKNESS: Not an OCR — text in TIFF stays as image.
 */
public class PdfBoxTiffConverter implements DocumentConverter {

    @Override
    public String libraryName() {
        return "PDFBox 3.0 (TIFF→PDF)";
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"tiff", "tif"};
    }

    @Override
    public ConvertResult convert(File input, File outputDir) {
        long start = System.currentTimeMillis();
        try {
            String outputName = input.getName().replaceAll("(?i)\\.tiff?$", ".pdf");
            File output = new File(outputDir, outputName);

            // Determine if this is a TIFF by checking the file extension
            String name = input.getName().toLowerCase();
            if (!name.endsWith(".tiff") && !name.endsWith(".tif")) {
                // Try loading as generic image first
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    return ConvertResult.failed(libraryName(), input.getName(),
                            "Cannot read as TIFF or image");
                }
                try (PDDocument pdf = new PDDocument()) {
                    PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
                    pdf.addPage(page);
                    PDImageXObject pdImage = LosslessFactory.createFromImage(pdf, image);
                    try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                        cs.drawImage(pdImage, 0, 0);
                    }
                    pdf.save(output);
                }
                long duration = System.currentTimeMillis() - start;
                return new ConvertResult(libraryName(), input.getName(),
                        output.getAbsolutePath(), true, duration, output.length(),
                        "Single-page image (not TIFF). Fidélité: parfaite.");
            }

            // Multi-page TIFF handling
            try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("tiff");
                if (!readers.hasNext()) {
                    return ConvertResult.failed(libraryName(), input.getName(),
                            "No TIFF reader available. Add imageio-tiff dependency?");
                }
                ImageReader reader = readers.next();
                reader.setInput(iis);

                int numPages = reader.getNumImages(true);

                if (numPages <= 0) {
                    return ConvertResult.failed(libraryName(), input.getName(),
                            "TIFF file contains 0 pages");
                }

                try (PDDocument pdf = new PDDocument()) {
                    for (int i = 0; i < numPages; i++) {
                        BufferedImage image = reader.read(i);
                        PDPage page = new PDPage(new PDRectangle(
                                image.getWidth(), image.getHeight()));
                        pdf.addPage(page);
                        PDImageXObject pdImage = LosslessFactory.createFromImage(pdf, image);
                        try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                            cs.drawImage(pdImage, 0, 0);
                        }
                    }
                    pdf.save(output);
                }

                long duration = System.currentTimeMillis() - start;
                return new ConvertResult(libraryName(), input.getName(),
                        output.getAbsolutePath(), true, duration, output.length(),
                        String.format("%d pages TIFF → PDF. Fidélité: parfaite (image brute).", numPages));
            }
        } catch (Exception e) {
            return ConvertResult.failed(libraryName(), input.getName(), e.getMessage());
        }
    }
}
