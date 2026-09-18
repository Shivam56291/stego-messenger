package com.stegomsg.ui;

import com.stegomsg.model.ImageHistoryEntry;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * Image History (ARCHITECTURE.md sections 19/38) — shows exactly the last 10 images
 * this user has sent. The 10-item cap is enforced server-side by
 * ImageHistoryRepository.insertAndPrune, not by truncating a longer list here, so this
 * view never has more than 10 rows to show even if queried directly.
 */
public final class ImageHistoryPane extends VBox {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    private final SceneManager sceneManager;
    private final TableView<ImageHistoryEntry> table = new TableView<>();

    public ImageHistoryPane(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        setPadding(new Insets(20));
        setSpacing(12);

        Label title = new Label("Image History");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Your last 10 sent images. Older entries are automatically removed.");
        subtitle.getStyleClass().add("muted");

        table.getColumns().addAll(
                textColumn("Image", entry -> entry.getImageName()),
                textColumn("Dimensions", entry -> entry.getWidth() + "\u00d7" + entry.getHeight()),
                textColumn("Capacity", entry -> formatBytes(entry.getCapacityBytes())),
                textColumn("Payload Used", entry -> formatBytes(entry.getPayloadBytes())),
                textColumn("Recipient", entry -> entry.getRecipientAlias()),
                textColumn("Sent", entry -> DATE_FORMAT.format(entry.getCreatedAt()))
        );
        table.setPlaceholder(new Label("No images sent yet."));
        table.setPrefHeight(500);

        getChildren().addAll(title, subtitle, table);
    }

    private TableColumn<ImageHistoryEntry, String> textColumn(String title, java.util.function.Function<ImageHistoryEntry, String> extractor) {
        TableColumn<ImageHistoryEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(extractor.apply(data.getValue())));
        return column;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        return String.format("%.1f KB", bytes / 1024.0);
    }

    public void refresh() {
        var user = sceneManager.services().sessionService.requireCurrentUser();
        // ImageHistoryRepository is reached through MessageService's collaborators in
        // AppServices; exposed here via a small accessor kept on ChatService's sibling.
        table.getItems().setAll(sceneManager.services().imageHistory(user.getId()));
    }
}
