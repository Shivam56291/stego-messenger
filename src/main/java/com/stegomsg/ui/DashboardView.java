package com.stegomsg.ui;

import com.stegomsg.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class DashboardView extends BorderPane {

    private final SceneManager sceneManager;
    private final StackPane content = new StackPane();
    private final ChatPane chatPane;
    private final ImageHistoryPane imageHistoryPane;
    private final SettingsPane settingsPane;

    public DashboardView(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        getStyleClass().add("app-content");

        User user = sceneManager.services().sessionService.requireCurrentUser();

        this.chatPane = new ChatPane(sceneManager);
        this.imageHistoryPane = new ImageHistoryPane(sceneManager);
        this.settingsPane = new SettingsPane(sceneManager);

        setTop(buildHeader(user));
        setLeft(buildSidebar(user));
        setCenter(content);

        showChats();
    }

    private HBox buildHeader(User user) {
        Label title = new Label("Secure Stego Messenger");
        title.getStyleClass().add("app-title");
        title.setGraphic(UiIcon.icon(UiIcon.Name.SHIELD, 18));
        title.setGraphicTextGap(9);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox securityChip = new HBox(7,
                UiIcon.icon(UiIcon.Name.LOCK, 13),
                new Label("Protected session"));
        securityChip.getStyleClass().add("header-security-chip");
        ((Label) securityChip.getChildren().get(1)).getStyleClass().add("header-security-text");
        securityChip.setAlignment(Pos.CENTER);

        Label userLabel = new Label(user.publicFacingLabel());
        userLabel.getStyleClass().add("header-user");

        HBox header = new HBox(16, title, spacer, securityChip, userLabel);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildSidebar(User user) {
        ToggleGroup navGroup = new ToggleGroup();

        ToggleButton chatsBtn = navButton("Chats", UiIcon.Name.CHAT, navGroup, true);
        ToggleButton historyBtn = navButton("Image History", UiIcon.Name.HISTORY, navGroup, false);
        ToggleButton settingsBtn = navButton("Settings", UiIcon.Name.SETTINGS, navGroup, false);

        chatsBtn.setOnAction(e -> showChats());
        historyBtn.setOnAction(e -> showImageHistory());
        settingsBtn.setOnAction(e -> showSettings());

        VBox userChip = new VBox(3);
        HBox identity = new HBox(9,
                UiIcon.icon(UiIcon.Name.USER, 17),
                new VBox(2,
                        label(user.publicFacingLabel(), "user-chip-name"),
                        label("Signed in", "user-chip-status")));
        identity.setAlignment(Pos.CENTER_LEFT);
        userChip.getChildren().add(identity);
        userChip.getStyleClass().add("user-chip");

        Button logoutBtn = new Button("Log Out");
        logoutBtn.setGraphic(UiIcon.icon(UiIcon.Name.LOGOUT, 15));
        logoutBtn.getStyleClass().add("button-secondary");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> UiTaskRunner.run(
                () -> {
                    sceneManager.services().authService.logout();
                    return null;
                },
                () -> {
                    logoutBtn.setDisable(true);
                    logoutBtn.setText("Signing out…");
                },
                ignored -> sceneManager.showLogin(),
                error -> {
                    logoutBtn.setDisable(false);
                    logoutBtn.setText("Log Out");
                },
                () -> {
                    if (logoutBtn.getScene() != null) {
                        logoutBtn.setDisable(false);
                        logoutBtn.setText("Log Out");
                        logoutBtn.setGraphic(UiIcon.icon(UiIcon.Name.LOGOUT, 15));
                    }
                }
        ));
        logoutBtn.setTooltip(new javafx.scene.control.Tooltip("End the current session and clear the in-memory private key"));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(8,
                userChip,
                new Separator(),
                chatsBtn,
                historyBtn,
                settingsBtn,
                spacer,
                logoutBtn);
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPadding(new Insets(14, 10, 14, 10));
        sidebar.setPrefWidth(230);
        return sidebar;
    }

    private ToggleButton navButton(String text, UiIcon.Name icon, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setGraphic(UiIcon.icon(icon, 16));
        button.setGraphicTextGap(12);
        button.getStyleClass().add("sidebar-nav-button");
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setMaxWidth(Double.MAX_VALUE);
        button.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) button.getStyleClass().add("sidebar-nav-button-active");
            else button.getStyleClass().remove("sidebar-nav-button-active");
        });
        if (selected) button.getStyleClass().add("sidebar-nav-button-active");
        return button;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private void showChats() {
        content.getChildren().setAll(chatPane);
        chatPane.refresh();
    }

    private void showImageHistory() {
        content.getChildren().setAll(imageHistoryPane);
        imageHistoryPane.refresh();
    }

    private void showSettings() {
        content.getChildren().setAll(settingsPane);
        settingsPane.refresh();
    }
}
