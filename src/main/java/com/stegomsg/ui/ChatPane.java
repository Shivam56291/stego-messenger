package com.stegomsg.ui;

import com.stegomsg.model.Conversation;
import com.stegomsg.model.Message;
import com.stegomsg.model.User;
import com.stegomsg.service.ChatService;
import com.stegomsg.service.ImageService;
import com.stegomsg.service.MessageService;
import com.stegomsg.stego.CapacityCalculator;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The chat system (ARCHITECTURE.md section 10): a conversation list on the left, a
 * message thread + compose bar on the right, supporting multiple simultaneous
 * conversations exactly as the spec describes ("a user may communicate with 3-4 people
 * simultaneously").
 *
 * Decoded plaintext is cached ONLY in memory for this pane's lifetime (a plain HashMap,
 * cleared on logout since the whole pane is discarded) — never written to disk. This
 * matches ARCHITECTURE.md's "server/database never sees plaintext" property extending
 * to local storage too (section 34).
 */
public final class ChatPane extends BorderPane {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final SceneManager sceneManager;
    private final ChatService chatService;
    private final MessageService messageService;
    private final ImageService imageService;

    private final ListView<ChatService.ConversationSummary> conversationList = new ListView<>();
    private final VBox threadContainer = new VBox(12);
    private final Label threadHeaderLabel = new Label("Select a conversation");
    private final TextArea messageInput = new TextArea();
    private final ComboBox<ImageService.DefaultImageOption> imageCombo = new ComboBox<>();
    private final Label capacityLabel = new Label();
    private final ProgressBar capacityBar = new ProgressBar(0);
    private final Button sendButton = new Button("Send");

    private final Map<String, String> decodedCache = new HashMap<>();
    private Conversation currentConversation;
    private User currentOtherUser;
    private BufferedImage currentCarrierImage;
    private String currentCarrierDisplayName;

    public ChatPane(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.chatService = sceneManager.services().chatService;
        this.messageService = sceneManager.services().messageService;
        this.imageService = sceneManager.services().imageService;

        setLeft(buildConversationList());
        setCenter(buildThreadArea());
    }

    // ---------------------------------------------------------------- left: conversations

    private VBox buildConversationList() {
        Button newChatButton = new Button("+ New Chat");
        newChatButton.getStyleClass().add("button-primary");
        newChatButton.setMaxWidth(Double.MAX_VALUE);
        newChatButton.setOnAction(e -> handleNewChat());

        conversationList.getStyleClass().add("conversation-list");
        conversationList.setCellFactory(list -> new ConversationCell());
        conversationList.setPrefWidth(300);
        VBox.setVgrow(conversationList, Priority.ALWAYS);
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, old, summary) -> {
            if (summary != null) selectConversation(summary.conversation(), summary.otherParticipant());
        });

        VBox box = new VBox(10, newChatButton, conversationList);
        box.setPadding(new Insets(12));
        box.setPrefWidth(320);
        return box;
    }

    private final class ConversationCell extends ListCell<ChatService.ConversationSummary> {
        @Override
        protected void updateItem(ChatService.ConversationSummary summary, boolean empty) {
            super.updateItem(summary, empty);
            if (empty || summary == null) {
                setGraphic(null);
                return;
            }
            Label aliasLabel = new Label(summary.otherParticipant().publicFacingLabel());
            aliasLabel.getStyleClass().add("section-title");

            String preview = summary.lastMessage()
                    .map(m -> decodedCache.getOrDefault(m.getId(), "\uD83D\uDD12 Encrypted message"))
                    .orElse("No messages yet");
            Label previewLabel = new Label(preview);
            previewLabel.getStyleClass().add("muted");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox top = new HBox(8, aliasLabel, spacer);
            if (summary.unreadCount() > 0) {
                Label badge = new Label(String.valueOf(summary.unreadCount()));
                badge.getStyleClass().addAll("badge", "badge-unread");
                top.getChildren().add(badge);
            }
            String time = summary.lastMessage().map(m -> TIME_FORMAT.format(m.getCreatedAt())).orElse("");
            Label timeLabel = new Label(time);
            timeLabel.getStyleClass().add("muted");
            top.getChildren().add(timeLabel);
            top.setAlignment(Pos.CENTER_LEFT);

            VBox cell = new VBox(4, top, previewLabel);
            cell.getStyleClass().add("conversation-cell");
            setGraphic(cell);
        }
    }

    private void handleNewChat() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Chat");
        dialog.setHeaderText("Start a conversation");
        dialog.setContentText("Contact's email:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(email -> {
            try {
                User self = sceneManager.services().sessionService.requireCurrentUser();
                User other = chatService.findContactByEmail(email);
                Conversation conversation = chatService.getOrCreateConversation(self.getId(), other.getId());
                refresh();
                selectConversationById(conversation.getId());
            } catch (IllegalArgumentException e) {
                alert(Alert.AlertType.ERROR, e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- right: thread + compose

    private VBox buildThreadArea() {
        threadHeaderLabel.getStyleClass().add("app-title");
        HBox header = new HBox(threadHeaderLabel);
        header.setPadding(new Insets(4, 4, 12, 4));

        threadContainer.setPadding(new Insets(12));
        ScrollPane scrollPane = new ScrollPane(threadContainer);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox composeArea = buildComposeArea();

        VBox layout = new VBox(8, header, scrollPane, composeArea);
        layout.setPadding(new Insets(12));
        return layout;
    }

    private VBox buildComposeArea() {
        imageCombo.getItems().addAll(imageService.defaultImageLibrary());
        imageCombo.setPromptText("Choose an image");
        imageCombo.setCellFactory(list -> imageOptionCell());
        imageCombo.setButtonCell(imageOptionCell());
        imageCombo.setOnAction(e -> onImageSelected(imageCombo.getValue()));

        Button customImageButton = new Button("Choose Your Image\u2026");
        customImageButton.getStyleClass().add("button-secondary");
        customImageButton.setOnAction(e -> handleChooseCustomImage());

        HBox imageRow = new HBox(10, imageCombo, customImageButton);
        imageRow.setAlignment(Pos.CENTER_LEFT);

        capacityBar.setMaxWidth(Double.MAX_VALUE);
        VBox capacityPanel = new VBox(4, capacityLabel, capacityBar);
        capacityPanel.getStyleClass().add("capacity-panel");

        messageInput.setPromptText("Type your message\u2026");
        messageInput.setPrefRowCount(3);
        messageInput.setWrapText(true);
        messageInput.textProperty().addListener((obs, old, text) -> updateCapacityIndicator());

        sendButton.getStyleClass().add("button-primary");
        sendButton.setOnAction(e -> handleSend());
        HBox sendRow = new HBox(sendButton);
        sendRow.setAlignment(Pos.CENTER_RIGHT);

        VBox compose = new VBox(8, imageRow, messageInput, capacityPanel, sendRow);
        compose.getStyleClass().add("card");
        updateCapacityIndicator();
        return compose;
    }

    private ListCell<ImageService.DefaultImageOption> imageOptionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ImageService.DefaultImageOption option, boolean empty) {
                super.updateItem(option, empty);
                setText(empty || option == null ? null : option.name() + " \u2014 " + option.description());
            }
        };
    }

    private void onImageSelected(ImageService.DefaultImageOption option) {
        if (option == null) return;
        try {
            currentCarrierImage = imageService.loadAndValidate(option.path());
            currentCarrierDisplayName = option.name();
        } catch (ImageService.ImageValidationException e) {
            alert(Alert.AlertType.ERROR, e.getMessage());
            currentCarrierImage = null;
        }
        updateCapacityIndicator();
    }

    private void handleChooseCustomImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an image (PNG only)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
        var file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;
        try {
            currentCarrierImage = imageService.loadAndValidate(file.toPath());
            currentCarrierDisplayName = file.getName();
            imageCombo.getSelectionModel().clearSelection();
            imageCombo.setPromptText(file.getName());
        } catch (ImageService.ImageValidationException e) {
            alert(Alert.AlertType.ERROR, e.getMessage());
            currentCarrierImage = null;
        }
        updateCapacityIndicator();
    }

    /** The live capacity calculator (ARCHITECTURE.md section 14) — recomputed on every keystroke. */
    private void updateCapacityIndicator() {
        capacityBar.getStyleClass().removeAll("progress-bar-warning", "progress-bar-danger");
        if (currentCarrierImage == null) {
            capacityLabel.setText("Choose an image to see capacity");
            capacityBar.setProgress(0);
            sendButton.setDisable(true);
            return;
        }
        CapacityCalculator.CapacityReport report = messageService.checkCapacity(currentCarrierImage, messageInput.getText());
        double fraction = Math.min(1.0, report.percentUsed() / 100.0);
        capacityBar.setProgress(fraction);

        String statusText = switch (report.status()) {
            case SAFE -> "\u2713 Safe";
            case WARNING -> "\u26A0 Near Capacity";
            case TOO_LARGE -> "\u2715 Image Too Small";
        };
        String styleClass = switch (report.status()) {
            case SAFE -> "status-safe";
            case WARNING -> "status-warning";
            case TOO_LARGE -> "status-danger";
        };
        capacityLabel.getStyleClass().removeAll("status-safe", "status-warning", "status-danger");
        capacityLabel.getStyleClass().add(styleClass);
        if (report.status() == CapacityCalculator.Status.WARNING) capacityBar.getStyleClass().add("progress-bar-warning");
        if (report.status() == CapacityCalculator.Status.TOO_LARGE) capacityBar.getStyleClass().add("progress-bar-danger");

        capacityLabel.setText(String.format("%s  \u2014  %,d / %,d bytes used (%.1f%%)",
                statusText, report.actualPayloadBytes(), report.maxPayloadBytes(), report.percentUsed()));

        sendButton.setDisable(currentConversation == null
                || messageInput.getText().isBlank()
                || report.status() == CapacityCalculator.Status.TOO_LARGE);
    }

    private void handleSend() {
        if (currentConversation == null || currentCarrierImage == null || currentOtherUser == null) return;
        try {
            User self = sceneManager.services().sessionService.requireCurrentUser();
            messageService.sendMessage(self, currentOtherUser, currentConversation.getId(),
                    messageInput.getText(), currentCarrierImage, currentCarrierDisplayName);
            messageInput.clear();
            refresh();
            selectConversationById(currentConversation.getId());
        } catch (MessageService.MessageException e) {
            alert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- thread rendering

    private void selectConversationById(String conversationId) {
        for (ChatService.ConversationSummary summary : conversationList.getItems()) {
            if (summary.conversation().getId().equals(conversationId)) {
                conversationList.getSelectionModel().select(summary);
                return;
            }
        }
    }

    private void selectConversation(Conversation conversation, User otherUser) {
        this.currentConversation = conversation;
        this.currentOtherUser = otherUser;
        threadHeaderLabel.setText(otherUser.publicFacingLabel());
        renderThread();
        updateCapacityIndicator();
    }

    private void renderThread() {
        threadContainer.getChildren().clear();
        if (currentConversation == null) return;
        User self = sceneManager.services().sessionService.requireCurrentUser();
        List<Message> messages = chatService.listMessages(currentConversation.getId(), self.getId());
        for (Message message : messages) {
            boolean isMine = message.getSenderId().equals(self.getId());
            threadContainer.getChildren().add(buildMessageCard(message, isMine));
        }
    }

    private VBox buildMessageCard(Message message, boolean isMine) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stego-card");
        if (isMine) card.getStyleClass().add("stego-card-sent");
        card.setMaxWidth(340);
        card.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label header = new Label((isMine ? "You \u2192 " + currentOtherUser.publicFacingLabel()
                : currentOtherUser.publicFacingLabel() + " \u2192 You")
                + "  \u2022  " + TIME_FORMAT.format(message.getCreatedAt()));
        header.getStyleClass().add("muted");

        Label securityBadge = new Label("\uD83D\uDD10 Encrypted Image Message");
        securityBadge.getStyleClass().add("section-title");

        ImageView thumbnail = new ImageView();
        thumbnail.setFitWidth(260);
        thumbnail.setPreserveRatio(true);
        try {
            BufferedImage img = imageService.loadSentImage(Path.of(message.getImagePath()));
            thumbnail.setImage(SwingFXUtils.toFXImage(img, null));
        } catch (RuntimeException ignored) {
            // Image missing/corrupted — the card still renders with header + decode button,
            // and decode will surface the proper "unable to verify" error if attempted.
        }

        VBox content = new VBox(6, header, securityBadge, thumbnail);

        String decoded = decodedCache.get(message.getId());
        if (decoded != null) {
            Label plaintext = new Label(decoded);
            plaintext.setWrapText(true);
            plaintext.getStyleClass().add("plaintext-bubble");
            Label securedLabel = new Label("Received securely");
            securedLabel.getStyleClass().add("hint");
            content.getChildren().addAll(plaintext, securedLabel);
        } else if (!isMine) {
            Button decodeButton = new Button("\uD83D\uDD13 Decode Message");
            decodeButton.getStyleClass().add("button-primary");
            decodeButton.setOnAction(e -> handleDecode(message));
            content.getChildren().add(decodeButton);
        } else {
            Label sentLabel = new Label("Sent \u2022 recipient can decode with their private key");
            sentLabel.getStyleClass().add("hint");
            content.getChildren().add(sentLabel);
        }

        card.getChildren().add(content);
        return card;
    }

    private void handleDecode(Message message) {
        try {
            User self = sceneManager.services().sessionService.requireCurrentUser();
            var privateKey = sceneManager.services().sessionService.requireCurrentPrivateKey();
            MessageService.DecodedMessage decoded = messageService.decodeMessage(message.getId(), self.getId(), privateKey);
            decodedCache.put(message.getId(), decoded.plaintext());
            renderThread();
            refresh(); // clears unread badge in the conversation list
        } catch (MessageService.MessageException e) {
            alert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- lifecycle

    public void refresh() {
        User self = sceneManager.services().sessionService.requireCurrentUser();
        String selectedId = currentConversation != null ? currentConversation.getId() : null;
        conversationList.getItems().setAll(chatService.listConversationSummaries(self.getId()));
        if (selectedId != null) selectConversationById(selectedId);
    }

    private void alert(Alert.AlertType type, String message) {
        Alert a = new Alert(type, message);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
