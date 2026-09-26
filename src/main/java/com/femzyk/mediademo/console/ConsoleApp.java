package com.femzyk.mediademo.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import com.femzyk.mediademo.core.MediaStreamService;

/**
 * ConsoleApp - console front end of the FEMZYK Media Stream Demo
 * (CS 1103-01, Unit 4 discussion).
 *
 * Three experiments, all powered by the shared MediaStreamService:
 *
 *   1. Buffer-size benchmark - a generated 5 MB media file is copied with
 *      512-byte, 8 KB and 64 KB buffers, showing why buffering matters for
 *      large multimedia files (the trade-off behind the discussion question).
 *   2. Byte streams - the real product image is copied and verified
 *      byte-for-byte with Files.mismatch.
 *   3. Character streams - reviews are written and read back, preserving
 *      Unicode text.
 */
public class ConsoleApp {

    private static final Path IMAGE_SOURCE = Path.of("media", "product.png");
    private static final Path IMAGE_COPY = Path.of("output", "product-copy.png");
    private static final Path REVIEWS_FILE = Path.of("output", "reviews.txt");
    private static final Path BENCHMARK_FILE = Path.of("output", "benchmark-5mb.bin");

    /** Size of the generated benchmark file (5 MB). */
    private static final int BENCHMARK_BYTES = 5 * 1024 * 1024;

    /** How often each buffer size copies the benchmark file. */
    private static final int BENCHMARK_REPETITIONS = 20;

    private static final String[] SEED_REVIEWS = {
            "Great mouse - the battery lasts for weeks.",
            "Le bouton lateral est tres pratique. (Unicode works!)",
            "Buen precio y envio rapido. Recomendado."
    };

    /**
     * Runs the three demonstrations in order.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) throws IOException {
        System.out.println("==================================================");
        System.out.println("   FEMZYK MEDIA STREAM DEMO (I/O Streams, Unit 4) ");
        System.out.println("==================================================");

        Files.createDirectories(Path.of("output"));
        MediaStreamService service = new MediaStreamService();

        benchmarkBufferSizes(service);
        copyProductImage(service);
        demonstrateReviews(service);

        System.out.println();
        System.out.println("Done: byte streams moved the images, character streams moved the text.");
    }

    /**
     * Experiment 1: copies the benchmark file with three buffer sizes and
     * reports the average time and throughput of each.
     */
    private static void benchmarkBufferSizes(MediaStreamService service) throws IOException {
        System.out.println();
        System.out.println("--- 1. Buffer-size benchmark (byte streams) ---");

        generateBenchmarkFileIfNeeded();

        int[] bufferSizes = {512, 8192, 65536};
        System.out.printf("Copying a %.2f MB file %d times per buffer size:%n",
                BENCHMARK_BYTES / (1024.0 * 1024.0), BENCHMARK_REPETITIONS);
        System.out.printf("%-10s %-15s %-15s%n", "Buffer", "Avg copy time", "Throughput");

        for (int bufferSize : bufferSizes) {
            service.copyImage(BENCHMARK_FILE, BENCHMARK_FILE.resolveSibling("benchmark-tmp.bin"),
                    bufferSize, null); // one warm-up copy, excluded from timing
            long totalNanos = 0;
            for (int i = 0; i < BENCHMARK_REPETITIONS; i++) {
                MediaStreamService.CopyResult result = service.copyImage(BENCHMARK_FILE,
                        BENCHMARK_FILE.resolveSibling("benchmark-tmp.bin"), bufferSize, null);
                totalNanos += result.elapsedNanos();
            }
            double avgMillis = totalNanos / (double) BENCHMARK_REPETITIONS / 1_000_000.0;
            double avgNanos = totalNanos / (double) BENCHMARK_REPETITIONS;
            double throughputMBps = BENCHMARK_BYTES / 1_000_000.0 / (avgNanos / 1_000_000_000.0);
            System.out.printf("%-10s %-15.3f %-15.1f%n", formatBytes(bufferSize), avgMillis, throughputMBps);
        }
        System.out.println("Bigger buffers = fewer OS calls = smoother streaming for large media.");
    }

    /** Experiment 2: copies the product image with byte streams and verifies it. */
    private static void copyProductImage(MediaStreamService service) {
        System.out.println();
        System.out.println("--- 2. Byte streams: copying the product image ---");
        try {
            MediaStreamService.CopyResult result = service.copyImage(IMAGE_SOURCE, IMAGE_COPY, 8192, null);
            System.out.printf("Copied %,d bytes in %.2f ms with an 8 KB buffer.%n",
                    result.bytesCopied(), result.elapsedMilliseconds());
            System.out.println("Verification: the copy is "
                    + (result.verified() ? "byte-for-byte IDENTICAL to the original." : "DIFFERENT - failed!"));
        } catch (IOException error) {
            System.out.println("Image copy failed: " + error.getMessage());
        }
    }

    /** Experiment 3: writes and reads back reviews with character streams. */
    private static void demonstrateReviews(MediaStreamService service) {
        System.out.println();
        System.out.println("--- 3. Character streams: product reviews (text) ---");
        try {
            service.rewriteReviews(REVIEWS_FILE, java.util.Arrays.asList(SEED_REVIEWS));
            System.out.println("Wrote " + SEED_REVIEWS.length + " review line(s).");
            for (String review : service.readReviews(REVIEWS_FILE)) {
                System.out.println("  read back: " + review);
            }
            System.out.println("Unicode preserved by the character streams (Reader/Writer).");
        } catch (IOException error) {
            System.out.println("Review demo failed: " + error.getMessage());
        }
    }

    /** Generates the 5 MB benchmark file once, with deterministic content. */
    private static void generateBenchmarkFileIfNeeded() throws IOException {
        if (Files.exists(BENCHMARK_FILE) && Files.size(BENCHMARK_FILE) == BENCHMARK_BYTES) {
            return;
        }
        Random random = new Random(42);
        byte[] chunk = new byte[8192];
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(BENCHMARK_FILE))) {
            int written = 0;
            while (written < BENCHMARK_BYTES) {
                random.nextBytes(chunk);
                int toWrite = Math.min(chunk.length, BENCHMARK_BYTES - written);
                out.write(chunk, 0, toWrite);
                written += toWrite;
            }
        }
    }

    /** Formats a byte count as "512 B", "8 KB" or "64 KB". */
    private static String formatBytes(int bytes) {
        if (bytes >= 1024) {
            return (bytes / 1024) + " KB";
        }
        return bytes + " B";
    }
}
