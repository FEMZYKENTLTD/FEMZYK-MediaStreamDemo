package com.femzyk.mediademo.gui;

import java.io.File;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Development utility (not part of the submitted program): renders the real
 * MediaFxApp user interface, drives it through a representative session,
 * and saves a screenshot so the README can include a genuine image of the
 * program's output.
 */
public class SnapshotRunner extends Application {

    /** Directory where the screenshots are written (override with -Dsnapshot.dir=...). */
    private static final String OUT_DIR = System.getProperty("snapshot.dir", ".");

    @Override
    public void start(Stage stage) {
        try {
            new MediaFxApp().start(stage);

            // Copy the image with the default 8 KB buffer.
            fire(stage, "#copyButton");
            Thread.sleep(1500);

            // Add one review through the character-stream path.
            TextField reviewInput = (TextField) stage.getScene().lookup("#reviewInput");
            reviewInput.setText("Works flawlessly with my laptop - five stars.");
            fire(stage, "#addReviewButton");
            Thread.sleep(500);

            Platform.runLater(() -> {
                try {
                    WritableImage image = stage.getScene().snapshot(null);
                    File output = new File(OUT_DIR, "screenshot-gui.png");
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output);
                    System.out.println("Saved screenshot: " + output.getPath());
                } catch (Exception error) {
                    error.printStackTrace();
                } finally {
                    Platform.exit();
                }
            });
        } catch (Exception error) {
            error.printStackTrace();
            Platform.exit();
        }
    }

    /** Fires the button with the given id. */
    private void fire(Stage stage, String buttonId) {
        Button button = (Button) stage.getScene().lookup(buttonId);
        if (button != null) {
            button.fire();
        }
    }

    /**
     * Launches the utility.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
