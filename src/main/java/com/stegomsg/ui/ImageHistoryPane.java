package com.stegomsg.ui;

import com.stegomsg.model.ImageHistoryEntry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ImageHistoryPane extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final SceneManager sceneManager;
    private final TableView<ImageHistoryEntry> table = new TableView<>();
    private final StackPane tableStack = new StackPane();
    private final ProgressIndicator spinner = new ProgressIndicator();

    public ImageHistoryPane(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        getStyleClass().add("history-page");

        Label title = new Label("Image history");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("A private activity view of your last 10 sent carrier images.");
        subtitle.getStyleClass().add("muted");

        HBox heading = new HBox(10, UiIcon.icon(UiIcon.Name.HISTORY, 20),
                new VBox(3, title, subtitle));
        heading.setAlignment(Pos.CENTER_LEFT);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-secondary");
        refresh.setGraphic(UiIcon.icon(UiIcon.Name.REFRESH, 14));
        refresh.setOnAction(e -> refresh());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, heading, spacer, refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("page-toolbar");

        configureTable();

        spinner.setPrefSize(32, 32);
        spinner.setVisible(false);
        spinner.setManaged(false);
        tableStack.getChildren().setAll(table, spinner);
        StackPane.setAlignment(spinner, Pos.CENTER);

        setTop(toolbar);
        setCenter(tableStack);
        BorderPane.setMargin(tableStack, new Insets(16, 0, 0, 0));
    }

    private void configureTable() {
        TableColumn<ImageHistoryEntry, String> imageColumn = new TableColumn<>("Image");
        imageColumn.setPrefWidth(180);
        imageColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getImageName()));

        TableColumn<ImageHistoryEntry, String> dimensionsColumn = new TableColumn<>("Dimensions");
        dimensionsColumn.setPrefWidth(110);
        dimensionsColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getWidth() + "×" + data.getValue().getHeight()));

        TableColumn<ImageHistoryEntry, String> capacityColumn = new TableColumn<>("Capacity");
        capacityColumn.setPrefWidth(115);
        capacityColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        formatBytes(data.getValue().getCapacityBytes())));

        TableColumn<ImageHistoryEntry, String> payloadColumn = new TableColumn<>("Payload used");
        payloadColumn.setPrefWidth(125);
        payloadColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        formatBytes(data.getValue().getPayloadBytes())));

        TableColumn<ImageHistoryEntry, String> recipientColumn = new TableColumn<>("Recipient");
        recipientColumn.setPrefWidth(180);
        recipientColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getRecipientAlias() == null
                                ? "Unknown"
                                : data.getValue().getRecipientAlias()));

        TableColumn<ImageHistoryEntry, String> sentColumn = new TableColumn<>("Sent");
        sentColumn.setPrefWidth(160);
        sentColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getCreatedAt() == null
                                ? "—"
                                : DATE_FORMAT.format(data.getValue().getCreatedAt())));

        table.getColumns().setAll(
                imageColumn,
                dimensionsColumn,
                capacityColumn,
                payloadColumn,
                recipientColumn,
                sentColumn
        );

        Label emptyLabel = new Label("No sent images yet");
        emptyLabel.getStyleClass().add("empty-state-copy");
        table.setPlaceholder(emptyLabel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(540);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public void refresh() {
        var user = sceneManager.services().sessionService.requireCurrentUser();

        spinner.setVisible(true);
        spinner.setManaged(true);
        table.setDisable(true);

        UiTaskRunner.run(
                () -> sceneManager.services().imageHistory(user.getId()),
                () -> {},
                entries -> table.getItems().setAll(entries),
                error -> showError("We couldn't load image history. Check your connection and try again."),
                () -> {
                    spinner.setVisible(false);
                    spinner.setManaged(false);
                    table.setDisable(false);
                }
        );
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Image history");
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());
        alert.showAndWait();
    }
}
