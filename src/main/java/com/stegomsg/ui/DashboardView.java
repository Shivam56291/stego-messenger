package com.stegomsg.ui;

import com.stegomsg.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The single main application shell (ARCHITECTURE.md section 9): a header, a left
 * navigation sidebar, and a content area that swaps between Chats / Image History /
 * Settings without ever opening a second window.
 */
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
        setLeft(buildSidebar());
        setCenter(content);

        showChats();
    }

    private HBox buildHeader(User user) {
        Label title = new Label("Secure Stego Messenger");
        title.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label(user.publicFacingLabel());
        userLabel.getStyleClass().add("muted");

        HBox header = new HBox(12, title, spacer, userLabel);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildSidebar() {
        ToggleGroup navGroup = new ToggleGroup();

        ToggleButton chatsBtn = navButton("\uD83D\uDCAC  Chats", navGroup, true);
        ToggleButton historyBtn = navButton("\uD83D\uDDBC  Image History", navGroup, false);
        ToggleButton settingsBtn = navButton("\u2699  Settings", navGroup, false);

        chatsBtn.setOnAction(e -> showChats());
        historyBtn.setOnAction(e -> showImageHistory());
        settingsBtn.setOnAction(e -> showSettings());

        Button logoutBtn = new Button("Log Out");
        logoutBtn.getStyleClass().add("button-secondary");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> {
            sceneManager.services().authService.logout();
            sceneManager.showLogin();
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(6, chatsBtn, historyBtn, settingsBtn, spacer, logoutBtn);
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPadding(new Insets(12, 8, 12, 8));
        sidebar.setPrefWidth(220);
        return sidebar;
    }

    private ToggleButton navButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
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
