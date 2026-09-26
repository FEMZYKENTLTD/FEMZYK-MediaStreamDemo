package com.femzyk.mediademo.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * MediaStreamService - the reusable stream layer of the FEMZYK Media
 * Stream Demo (CS 1103-01, Unit 4 discussion).
 *
 * The class contains no user-interface code, so the JavaFX front end and
 * the console benchmark reuse exactly the same tested logic:
 *
 *   - copyImage():       BYTE streams; copies a media file in chunks of a
 *                        configurable buffer size, reports progress through
 *                        a listener, and verifies the copy byte-for-byte.
 *   - readReviews():     CHARACTER streams; reads text reviews.
 *   - appendReview():    CHARACTER streams; appends one review.
 *   - rewriteReviews():  CHARACTER streams; replaces the review file.
 *
 * Every stream is opened in a try-with-resources block so it is closed
 * automatically even if an error occurs.
 */
public class MediaStreamService {

    /** Callback used to report copy progress (bytes done / bytes total). */
    public interface ProgressListener {
        void onProgress(long bytesDone, long bytesTotal);
    }

    /** Immutable result of one image copy. */
    public record CopyResult(long bytesCopied, long elapsedNanos, int bufferSize, boolean verified) {

        /** Returns the elapsed time in milliseconds with two decimals. */
        public double elapsedMilliseconds() {
            return elapsedNanos / 1_000_000.0;
        }
    }

    /**
     * Copies a media file using BYTE streams with the given buffer size.
     *
     * @param source     the file to copy, must exist
     * @param target     the destination file (parent folders are created)
     * @param bufferSize the buffer size in bytes (the experiment variable)
     * @param listener   optional progress callback, may be null
     * @return a CopyResult with size, duration, buffer size and verification
     * @throws IOException if any stream operation fails
     */
    public CopyResult copyImage(Path source, Path target, int bufferSize, ProgressListener listener)
            throws IOException {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive.");
        }
        Files.createDirectories(target.getParent());
        long totalBytes = Files.size(source);

        long startNanos = System.nanoTime();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(source), bufferSize);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), bufferSize)) {
            byte[] chunk = new byte[bufferSize];
            long bytesDone = 0;
            int bytesRead;
            while ((bytesRead = in.read(chunk)) != -1) {
                out.write(chunk, 0, bytesRead);
                bytesDone += bytesRead;
                if (listener != null) {
                    listener.onProgress(bytesDone, totalBytes);
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        boolean verified = Files.mismatch(source, target) == -1;
        return new CopyResult(totalBytes, elapsedNanos, bufferSize, verified);
    }

    /**
     * Reads all review lines with CHARACTER streams.
     *
     * @param file the reviews file
     * @return the list of review lines (empty if the file does not exist)
     * @throws IOException if reading fails
     */
    public List<String> readReviews(Path file) throws IOException {
        if (Files.notExists(file)) {
            return new ArrayList<>();
        }
        List<String> reviews = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                reviews.add(line);
            }
        }
        return reviews;
    }

    /**
     * Appends one review with CHARACTER streams, creating the file if needed.
     *
     * @param file   the reviews file
     * @param review the review text to append
     * @throws IOException if writing fails
     */
    public void appendReview(Path file, String review) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(review);
            writer.newLine();
        }
    }

    /**
     * Replaces the whole reviews file with the given lines.
     *
     * @param file    the reviews file
     * @param reviews the new review lines
     * @throws IOException if writing fails
     */
    public void rewriteReviews(Path file, List<String> reviews) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String review : reviews) {
                writer.write(review);
                writer.newLine();
            }
        }
    }
}
