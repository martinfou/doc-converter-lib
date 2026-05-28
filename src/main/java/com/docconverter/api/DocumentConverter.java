package com.docconverter.api;

import java.io.File;

/** Common interface for document-to-PDF converters. */
public interface DocumentConverter {
    /** Convert a document to PDF. */
    ConvertResult convert(File input, File outputDir);

    /** Human-readable name of the library. */
    String libraryName();

    /** File extensions this converter supports (e.g., "docx", "doc", "tiff", "eml"). */
    String[] supportedExtensions();
}
