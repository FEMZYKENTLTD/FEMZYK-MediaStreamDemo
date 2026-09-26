package com.femzyk.mediademo.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.femzyk.mediademo.core.MediaStreamService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * MediaFxApp - JavaFX front end of the FEMZYK Media Stream Demo
 * (CS 1103-01, Unit 4 discussion).
 *
 * The window shows the two stream families at work in an e-commerce
 * product page:
 *
 *   - Left: the product image, loaded through an InputStream.
 *   - Right: a byte-stream copy experiment - pick a buffer size, copy the
 *     image on a background worker thread (Unit 3 revisited), watch the
 *     ProgressBar fill through the service's progress listener, and see
 *     the byte-for-byte verification result.
 *   - Bottom: character streams - reviews are read, added, and stored as
 *     text with Unicode preserved.
 *
 * All file work happens on worker threads and touches the UI only through
 * Platform.runLater, so the interface never freezes.
 */
public class MediaFxApp extends Application {

    private static final String APP_TITLE = "FEMZYK Media Stream Demo - CS 1103-01 Unit 4";

    private static final Path IMAGE_SOURCE = Path.of("media", "product.png");
    private static final Path IMAGE_COPY = Path.of("output", "product-copy.png");
    private static final Path REVIEWS_FILE = Path.of("output", "reviews.txt");

    private static final String[] SEED_REVIEWS = {
            "Great mouse - the battery lasts for weeks.",
            "Le bouton lateral est tres pratique. (Unicode works!)",
            "Buen precio y envio rapido. Recomendado."
    };

    private final MediaStreamService service = new MediaStreamService();

    private ComboBox<Integer> bufferBox;
    private ProgressBar progressBar;
    private Label copyResult;
    private Button copyButton;
    private ListView<String> reviewList;
    private TextField reviewInput;
    private Label reviewStatus;

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);

        Label title = new Label("FEMZYK Media Stream Demo");
        title.getStyleClass().add("header-title");
        Label subtitle = new Label("CS 1103-01 - Unit 4 - byte streams for media, character streams for text");
        subtitle.getStyleClass().add("header-sub");

        // ----- Product card: image loaded through an InputStream -----
        ImageView productView = new ImageView();
        productView.setFitWidth(260);
        productView.setFitHeight(260);
        productView.setPreserveRatio(true);
        productView.getStyleClass().add("product-view");
        loadImageInto(productView);
        Label productCaption = new Label("Wireless Mouse - $25.99");
        productCaption.getStyleClass().add("product-caption");
        Label streamNote = new Label("Loaded through an InputStream");
        streamNote.getStyleClass().add("muted");
        VBox productCard = new VBox(8, sectionLabel("Product (binary data)"),
                productView, productCaption, streamNote);
        productCard.getStyleClass().add("card");

        // ----- Copy experiment card: byte streams with progress -----
        bufferBox = new ComboBox<>();
        bufferBox.setId("bufferBox");
        bufferBox.getItems().addAll(512, 8192, 65536);
        bufferBox.setValue(8192);
        copyButton = new Button("Copy image (byte streams)");
        copyButton.setId("copyButton");
        copyButton.setOnAction(event -> copyImage());
        HBox bufferRow = new HBox(10, new Label("Buffer size:"), bufferBox, copyButton);
        bufferRow.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setId("progressBar");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        copyResult = hiddenLabel("copyResult");
        copyResult.setWrapText(true);

        VBox copyCard = new VBox(8, sectionLabel("Byte-stream copy experiment"),
                new Label("Worker thread + service progress listener:"),
                bufferRow, progressBar, copyResult);
        copyCard.getStyleClass().add("card");

        // ----- Reviews card: character streams -----
        reviewList = new ListView<>();
        reviewList.setId("reviewList");
        reviewList.setPrefHeight(140);
        reviewInput = new TextField();
        reviewInput.setId("reviewInput");
        reviewInput.setPromptText("Write a review... (accents welcome: c'est super)");
        reviewInput.setPrefColumnCount(28);
        Button addReviewButton = new Button("Add review");
        addReviewButton.setId("addReviewButton");
        addReviewButton.setOnAction(event -> addReview());
        HBox reviewRow = new HBox(10, reviewInput, addReviewButton);
        reviewRow.setAlignment(Pos.CENTER_LEFT);
        reviewStatus = hiddenLabel("reviewStatus");
        reviewStatus.setWrapText(true);

        VBox reviewCard = new VBox(8, sectionLabel("Reviews (character data, stored as text)"),
                reviewList, reviewRow, reviewStatus);
        reviewCard.getStyleClass().add("card");

        // ----- Assemble -----
        GridPane top = new GridPane();
        top.setHgap(14);
        ColumnConstraints left = new ColumnConstraints();
        ColumnConstraints right = new ColumnConstraints();
        right.setHgrow(Priority.ALWAYS);
        top.getColumnConstraints().addAll(left, right);
        top.add(productCard, 0, 0);
        top.add(copyCard, 1, 0);
        GridPane.setHgrow(copyCard, Priority.ALWAYS);

        VBox root = new VBox(14, title, subtitle, top, reviewCard);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("root");

        seedReviews();
        refreshReviews();

        Scene scene = new Scene(root, 900, 760);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    /** Loads the product image through an InputStream (byte stream). */
    private void loadImageInto(ImageView view) {
        try (InputStream in = Files.newInputStream(IMAGE_SOURCE)) {
            view.setImage(new Image(in));
        } catch (IOException | NullPointerException error) {
            view.setImage(null);
        }
    }

    /**
     * Copies the image on a worker thread with the selected buffer size.
     * The service's progress listener pushes updates to the ProgressBar,
     * and the verification result is shown when the copy finishes.
     */
    private void copyImage() {
        int bufferSize = bufferBox.getValue();
        copyButton.setDisable(true);
        progressBar.setProgress(0);
        copyResult.setVisible(false);
        copyResult.setManaged(false);

        Thread worker = new Thread(() -> {
            try {
                MediaStreamService.CopyResult result = service.copyImage(IMAGE_SOURCE, IMAGE_COPY,
                        bufferSize,
                        (done, total) -> Platform.runLater(() ->
                                progressBar.setProgress(total == 0 ? 0 : (double) done / total)));
                Platform.runLater(() -> {
                    progressBar.setProgress(1);
                    show(copyResult, String.format(
                            "Copied %,d bytes in %.2f ms with a %,d-byte buffer - copy verified "
                                    + "%s. Try other buffer sizes and compare!",
                            result.bytesCopied(), result.elapsedMilliseconds(), result.bufferSize(),
                            result.verified() ? "byte-for-byte IDENTICAL" : "FAILED"), false);
                    copyButton.setDisable(false);
                });
            } catch (IOException | IllegalArgumentException error) {
                Platform.runLater(() -> {
                    show(copyResult, "Copy failed: " + error.getMessage(), true);
                    copyButton.setDisable(false);
                });
            }
        }, "ImageCopyWorker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Appends one review through the character-stream service. */
    private void addReview() {
        hide(reviewStatus);
        String text = reviewInput.getText().trim();
        if (text.isEmpty()) {
            show(reviewStatus, "Write something first - an empty review cannot be saved.", true);
            return;
        }
        try {
            service.appendReview(REVIEWS_FILE, text);
            reviewList.getItems().add(text);
            reviewList.scrollTo(reviewList.getItems().size() - 1);
            reviewInput.clear();
            show(reviewStatus, "Review stored with a BufferedWriter (character stream).", false);
        } catch (IOException error) {
            show(reviewStatus, "Saving failed: " + error.getMessage(), true);
        }
    }

    /** Creates the reviews file with seed content the first time. */
    private void seedReviews() {
        try {
            if (service.readReviews(REVIEWS_FILE).isEmpty()) {
                service.rewriteReviews(REVIEWS_FILE, List.of(SEED_REVIEWS));
            }
        } catch (IOException error) {
            show(reviewStatus, "Could not prepare the reviews file: " + error.getMessage(), true);
        }
    }

    /** Reloads the reviews from disk into the list. */
    private void refreshReviews() {
        try {
            reviewList.getItems().setAll(service.readReviews(REVIEWS_FILE));
        } catch (IOException error) {
            show(reviewStatus, "Could not read reviews: " + error.getMessage(), true);
        }
    }

    /** Creates a section heading label. */
    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    /** Creates an initially hidden label used for status messages. */
    private Label hiddenLabel(String id) {
        Label label = new Label();
        label.setId(id);
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    /** Shows a label with the given message in error (red) or success (green) style. */
    private void show(Label label, String message, boolean isError) {
        label.getStyleClass().removeAll("error", "result");
        label.getStyleClass().add(isError ? "error" : "result");
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    /** Hides a previously shown label. */
    private void hide(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
}
