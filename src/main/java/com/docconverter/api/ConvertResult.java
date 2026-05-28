package com.docconverter.api;

/** Result of a single document conversion attempt. */
public class ConvertResult {
    private final String libraryName;
    private final String inputFile;
    private final String outputFile;
    private final boolean success;
    private final long durationMs;
    private final long outputSizeBytes;
    private final String notes;

    public ConvertResult(String libraryName, String inputFile, String outputFile,
                         boolean success, long durationMs, long outputSizeBytes, String notes) {
        this.libraryName = libraryName;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.success = success;
        this.durationMs = durationMs;
        this.outputSizeBytes = outputSizeBytes;
        this.notes = notes;
    }

    public static ConvertResult failed(String library, String input, String reason) {
        return new ConvertResult(library, input, null, false, 0, 0, "FAILED: " + reason);
    }

    public String libraryName() { return libraryName; }
    public String inputFile() { return inputFile; }
    public String outputFile() { return outputFile; }
    public boolean success() { return success; }
    public long durationMs() { return durationMs; }
    public long outputSizeBytes() { return outputSizeBytes; }
    public String notes() { return notes; }

    @Override
    public String toString() {
        String status = success ? "✅ OK" : "❌ FAIL";
        String size = success ? String.format("%,d bytes", outputSizeBytes) : "—";
        return String.format("  %s | %s | %d ms | %s | %s",
                padRight(libraryName(), 25),
                status,
                durationMs(),
                padRight(size, 14),
                notes());
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
