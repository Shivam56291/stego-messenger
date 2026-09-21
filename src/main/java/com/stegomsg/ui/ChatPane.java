package com.stegomsg.ui;

import com.stegomsg.model.Conversation;
import com.stegomsg.model.Message;
import com.stegomsg.model.User;
import com.stegomsg.service.ChatService;
import com.stegomsg.service.ImageService;
import com.stegomsg.service.MessageService;
import com.stegomsg.stego.CapacityCalculator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.embed.swing.SwingFXUtils;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ChatPane extends BorderPane {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final SceneManager sceneManager;
    private final ChatService chatService;
    private final MessageService messageService;
    private final ImageService imageService;

    private final TextField conversationSearch = new TextField();
    private final ListView<ChatService.ConversationSummary> conversationList = new ListView<>();
    private final ObservableList<ChatService.ConversationSummary> conversationItems =
            FXCollections.observableArrayList();
    private final FilteredList<ChatService.ConversationSummary> filteredConversations =
            new FilteredList<>(conversationItems);

    private final StackPane conversationListStack = new StackPane();
    private final StackPane threadStack = new StackPane();
    private final VBox threadContainer = new VBox(10);
    private final Label threadHeaderLabel = new Label("Select a conversation");
    private final Label threadSubLabel = new Label("Your encrypted conversations will appear here.");
    private final TextArea messageInput = new TextArea();
    private final ComboBox<ImageService.DefaultImageOption> imageCombo = new ComboBox<>();
    private final Label selectedImageLabel = new Label("Choose a PNG carrier");
    private final Label capacityLabel = new Label("Choose an image to calculate capacity");
    private final ProgressBar capacityBar = new ProgressBar(0);
    private final Label characterCounter = new Label("0 characters");
    private final Button sendButton = new Button("Send");
    private final Button newChatButton = new Button("New Chat");

    private final ProgressIndicator conversationSpinner = new ProgressIndicator();
    private final ProgressIndicator threadSpinner = new ProgressIndicator();

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

        getStyleClass().add("chat-pane");

        conversationList.setItems(filteredConversations);
        setLeft(buildConversationList());
        setCenter(buildThreadArea());
    }

    private Node buildConversationList() {
        newChatButton.setText("New Chat");
        newChatButton.setGraphic(UiIcon.icon(UiIcon.Name.PLUS, 15));
        newChatButton.getStyleClass().add("button-primary");
        newChatButton.setMaxWidth(Double.MAX_VALUE);
        newChatButton.setOnAction(e -> handleNewChat());

        conversationSearch.setPromptText("Search conversations");
        conversationSearch.getStyleClass().add("conversation-search");
        conversationSearch.textProperty().addListener((obs, old, value) ->
                filteredConversations.setPredicate(summary -> {
                    String q = value == null ? "" : value.trim().toLowerCase();
                    if (q.isBlank()) return true;
                    String alias = summary.otherParticipant().publicFacingLabel().toLowerCase();
                    String email = summary.otherParticipant().getEmail().toLowerCase();
                    return alias.contains(q) || email.contains(q);
                }));

        conversationList.getStyleClass().add("conversation-list");
        conversationList.setCellFactory(list -> new ConversationCell());
        conversationList.setPlaceholder(emptyConversations());
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, old, summary) -> {
            if (summary != null) {
                selectConversation(summary.conversation(), summary.otherParticipant());
            }
        });

        conversationSpinner.setPrefSize(30, 30);
        conversationSpinner.setVisible(false);
        conversationSpinner.setManaged(false);
        conversationSpinner.getStyleClass().add("loading-spinner");
        conversationListStack.getChildren().setAll(conversationList, conversationSpinner);
        StackPane.setAlignment(conversationSpinner, Pos.CENTER);

        VBox box = new VBox(12,
                newChatButton,
                conversationSearch,
                conversationListStack);
        VBox.setVgrow(conversationListStack, Priority.ALWAYS);
        box.getStyleClass().add("conversation-pane");
        box.setPrefWidth(320);
        return box;
    }

    private Node emptyConversations() {
        VBox empty = new VBox(8, UiIcon.icon(UiIcon.Name.CHAT, 28));
        Label title = new Label("No conversations yet");
        title.getStyleClass().add("empty-state-title");
        Label copy = new Label("Start a chat with a registered contact using their email address.");
        copy.setWrapText(true);
        copy.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        copy.getStyleClass().add("empty-state-copy");
        empty.getChildren().addAll(title, copy);
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("empty-state");
        return empty;
    }

    private final class ConversationCell extends ListCell<ChatService.ConversationSummary> {
        @Override
        protected void updateItem(ChatService.ConversationSummary summary, boolean empty) {
            super.updateItem(summary, empty);
            if (empty || summary == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            String alias = summary.otherParticipant().publicFacingLabel();
            Label avatar = new Label(initials(alias));
            avatar.getStyleClass().add("avatar");

            Label aliasLabel = new Label(alias);
            aliasLabel.getStyleClass().add("conversation-name");

            String preview = summary.lastMessage()
                    .map(m -> decodedCache.getOrDefault(m.getId(), "🔒 Encrypted image message"))
                    .orElse("No messages yet");
            Label previewLabel = new Label(preview);
            previewLabel.setMaxWidth(185);
            previewLabel.setEllipsisString("…");
            previewLabel.getStyleClass().add("conversation-preview");

            Label timeLabel = new Label(summary.lastMessage()
                    .map(m -> TIME_FORMAT.format(m.getCreatedAt())).orElse(""));
            timeLabel.getStyleClass().add("conversation-time");

            HBox top = new HBox(6, aliasLabel, new Region(), timeLabel);
            HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
            top.setAlignment(Pos.CENTER_LEFT);

            HBox text = new HBox(8);
            VBox details = new VBox(4, top, previewLabel);
            HBox.setHgrow(details, Priority.ALWAYS);

            if (summary.unreadCount() > 0) {
                Label badge = new Label(String.valueOf(summary.unreadCount()));
                badge.getStyleClass().add("badge");
                top.getChildren().add(1, badge);
            }

            text.getChildren().addAll(avatar, details);
            text.setAlignment(Pos.CENTER_LEFT);

            VBox cell = new VBox(text);
            cell.getStyleClass().add("conversation-cell");
            setGraphic(cell);
        }
    }

    private Node buildThreadArea() {
        threadHeaderLabel.getStyleClass().add("thread-contact");
        threadSubLabel.getStyleClass().add("thread-subtitle");

        HBox headerText = new HBox(10, UiIcon.icon(UiIcon.Name.LOCK, 17),
                new VBox(3, threadHeaderLabel, threadSubLabel));
        headerText.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(headerText);
        header.getStyleClass().add("thread-header");
        header.setAlignment(Pos.CENTER_LEFT);

        threadContainer.setPadding(new Insets(14));
        threadContainer.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(threadContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("clean-scroll");

        VBox composeArea = buildComposeArea();

        threadSpinner.setPrefSize(32, 32);
        threadSpinner.setVisible(false);
        threadSpinner.setManaged(false);
        threadSpinner.getStyleClass().add("loading-spinner");

        StackPane threadContent = new StackPane(scrollPane, threadSpinner);
        VBox.setVgrow(threadContent, Priority.ALWAYS);
        threadStack.getChildren().setAll(threadContent);

        VBox layout = new VBox(header, threadStack, composeArea);
        VBox.setVgrow(threadStack, Priority.ALWAYS);
        layout.getStyleClass().add("thread-area");
        return layout;
    }

    private VBox buildComposeArea() {
        imageCombo.getItems().addAll(imageService.defaultImageLibrary());
        imageCombo.setPromptText("Choose a carrier image");
        imageCombo.setCellFactory(list -> imageOptionCell());
        imageCombo.setButtonCell(imageOptionCell());
        imageCombo.setOnAction(e -> onImageSelected(imageCombo.getValue()));

        Button customImageButton = new Button("Browse PNG");
        customImageButton.getStyleClass().add("button-secondary");
        customImageButton.setGraphic(UiIcon.icon(UiIcon.Name.IMAGE, 14));
        customImageButton.setOnAction(e -> handleChooseCustomImage());

        selectedImageLabel.getStyleClass().add("compose-hint");

        HBox imageRow = new HBox(9, imageCombo, customImageButton, selectedImageLabel);
        imageRow.getStyleClass().add("compose-toolbar");
        imageRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(imageCombo, Priority.ALWAYS);

        capacityBar.setMaxWidth(Double.MAX_VALUE);
        capacityLabel.getStyleClass().add("capacity-caption");
        VBox capacityPanel = new VBox(5, capacityLabel, capacityBar);
        capacityPanel.getStyleClass().add("capacity-panel");

        messageInput.setPromptText("Write a message…");
        messageInput.setPrefRowCount(3);
        messageInput.setWrapText(true);
        messageInput.getStyleClass().add("message-input");
        messageInput.textProperty().addListener((obs, old, text) -> {
            characterCounter.setText(text.length() + (text.length() == 1 ? " character" : " characters"));
            updateCapacityIndicator();
            if (!text.isBlank()) messageInput.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("ready"), true);
        });
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                if (!sendButton.isDisabled()) handleSend();
            }
        });

        characterCounter.getStyleClass().add("char-counter");

        sendButton.getStyleClass().addAll("button-primary", "send-button");
        sendButton.setGraphic(UiIcon.icon(UiIcon.Name.SEND, 14));
        sendButton.setOnAction(e -> handleSend());
        sendButton.setTooltip(new Tooltip("Send this encrypted message"));

        HBox sendRow = new HBox(10, characterCounter, new Region(), sendButton);
        HBox.setHgrow(sendRow.getChildren().get(1), Priority.ALWAYS);
        sendRow.setAlignment(Pos.CENTER_LEFT);

        VBox compose = new VBox(9, imageRow, messageInput, capacityPanel, sendRow);
        compose.getStyleClass().add("compose-area");
        return compose;
    }

    private ListCell<ImageService.DefaultImageOption> imageOptionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ImageService.DefaultImageOption option, boolean empty) {
                super.updateItem(option, empty);
                setText(empty || option == null ? null :
                        option.name() + "  •  " + option.description());
            }
        };
    }

    private void onImageSelected(ImageService.DefaultImageOption option) {
        if (option == null) return;
        try {
            currentCarrierImage = imageService.loadAndValidate(option.path());
            currentCarrierDisplayName = option.name();
            selectedImageLabel.setText(option.name() + " selected");
            selectedImageLabel.getStyleClass().add("compose-hint-selected");
        } catch (ImageService.ImageValidationException e) {
            clearSelectedImage();
            showInlineAlert(e.getMessage());
        }
        updateCapacityIndicator();
    }

    private void handleChooseCustomImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a PNG carrier image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
        var file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;
        try {
            currentCarrierImage = imageService.loadAndValidate(file.toPath());
            currentCarrierDisplayName = file.getName();
            imageCombo.getSelectionModel().clearSelection();
            selectedImageLabel.setText(file.getName() + " selected");
            selectedImageLabel.getStyleClass().add("compose-hint-selected");
        } catch (ImageService.ImageValidationException e) {
            clearSelectedImage();
            showInlineAlert(e.getMessage());
        }
        updateCapacityIndicator();
    }

    private void clearSelectedImage() {
        currentCarrierImage = null;
        currentCarrierDisplayName = null;
        selectedImageLabel.setText("Choose a PNG carrier");
        selectedImageLabel.getStyleClass().remove("compose-hint-selected");
        imageCombo.getSelectionModel().clearSelection();
    }

    private void updateCapacityIndicator() {
        capacityBar.getStyleClass().removeAll("progress-bar-warning", "progress-bar-danger");
        capacityLabel.getStyleClass().removeAll("status-safe", "status-warning", "status-danger");

        if (currentCarrierImage == null) {
            capacityLabel.setText("Choose an image to calculate message capacity");
            capacityBar.setProgress(0);
            sendButton.setDisable(true);
            return;
        }

        CapacityCalculator.CapacityReport report =
                messageService.checkCapacity(currentCarrierImage, messageInput.getText());

        capacityBar.setProgress(Math.min(1.0, report.percentUsed() / 100.0));

        switch (report.status()) {
            case SAFE -> {
                capacityLabel.setText(String.format("✓ Safe  •  %,d / %,d bytes  •  %.1f%% used",
                        report.actualPayloadBytes(), report.maxPayloadBytes(), report.percentUsed()));
                capacityLabel.getStyleClass().add("status-safe");
            }
            case WARNING -> {
                capacityLabel.setText(String.format("⚠ Near capacity  •  %,d / %,d bytes  •  %.1f%% used",
                        report.actualPayloadBytes(), report.maxPayloadBytes(), report.percentUsed()));
                capacityLabel.getStyleClass().add("status-warning");
                capacityBar.getStyleClass().add("progress-bar-warning");
            }
            case TOO_LARGE -> {
                capacityLabel.setText(String.format("✕ Image too small  •  %,d / %,d bytes needed",
                        report.actualPayloadBytes(), report.maxPayloadBytes()));
                capacityLabel.getStyleClass().add("status-danger");
                capacityBar.getStyleClass().add("progress-bar-danger");
            }
        }

        sendButton.setDisable(currentConversation == null
                || currentCarrierImage == null
                || messageInput.getText().isBlank()
                || report.status() == CapacityCalculator.Status.TOO_LARGE);
    }

    private void handleSend() {
        if (currentConversation == null || currentCarrierImage == null || currentOtherUser == null) return;

        User self = sceneManager.services().sessionService.requireCurrentUser();
        Conversation conversation = currentConversation;
        User recipient = currentOtherUser;
        BufferedImage image = currentCarrierImage;
        String imageName = currentCarrierDisplayName;
        String plaintext = messageInput.getText();

        UiTaskRunner.run(
                () -> {
                    messageService.sendMessage(self, recipient, conversation.getId(),
                            plaintext, image, imageName);
                    return null;
                },
                () -> {
                    sendButton.setDisable(true);
                    sendButton.setText("Sending…");
                    ProgressIndicator spinner = new ProgressIndicator();
                    spinner.setPrefSize(16, 16);
                    spinner.getStyleClass().add("button-spinner");
                    sendButton.setGraphic(spinner);
                },
                ignored -> {
                    messageInput.clear();
                    decodedCache.clear();
                    refresh();
                    selectConversationById(conversation.getId());
                },
                error -> showInlineAlert(error instanceof MessageService.MessageException
                        ? error.getMessage()
                        : "We couldn't send this message. Check your connection and try again."),
                () -> restoreSendButton()
        );
    }

    private void restoreSendButton() {
        sendButton.setText("Send");
        sendButton.setGraphic(UiIcon.icon(UiIcon.Name.SEND, 14));
        updateCapacityIndicator();
    }

    private void handleNewChat() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New conversation");
        dialog.setHeaderText("Start a secure conversation");
        dialog.setContentText("Contact email:");
        dialog.getEditor().setPromptText("name@example.com");
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());

        dialog.showAndWait().ifPresent(email -> {
            String normalized = email == null ? "" : email.trim();
            if (!com.stegomsg.util.Validation.isValidEmail(normalized)) {
                showInlineAlert("Enter a valid email address.");
                return;
            }

            User self = sceneManager.services().sessionService.requireCurrentUser();
            UiTaskRunner.run(
                    () -> {
                        User other = chatService.findContactByEmail(normalized);
                        return chatService.getOrCreateConversation(self.getId(), other.getId());
                    },
                    () -> {
                        newChatButton.setDisable(true);
                        newChatButton.setText("Creating…");
                        ProgressIndicator spinner = new ProgressIndicator();
                        spinner.setPrefSize(16, 16);
                        spinner.getStyleClass().add("button-spinner");
                        newChatButton.setGraphic(spinner);
                    },
                    conversation -> {
                        refresh();
                        selectConversationById(conversation.getId());
                    },
                    error -> showInlineAlert(error instanceof IllegalArgumentException
                            ? error.getMessage()
                            : "We couldn't start the conversation. Check your connection and try again."),
                    () -> {
                        newChatButton.setDisable(false);
                        newChatButton.setText("New Chat");
                        newChatButton.setGraphic(UiIcon.icon(UiIcon.Name.PLUS, 15));
                    }
            );
        });
    }

    private void selectConversationById(String conversationId) {
        for (ChatService.ConversationSummary summary : conversationItems) {
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
        threadSubLabel.setText("End-to-end encrypted image messages");
        loadThreadAsync();
        updateCapacityIndicator();
    }

    private void loadThreadAsync() {
        if (currentConversation == null) return;
        String conversationId = currentConversation.getId();
        User self = sceneManager.services().sessionService.requireCurrentUser();

        threadContainer.getChildren().clear();
        setThreadLoading(true);

        UiTaskRunner.run(
                () -> chatService.listMessages(conversationId, self.getId()),
                () -> {},
                messages -> {
                    if (currentConversation == null || !conversationId.equals(currentConversation.getId())) return;
                    renderMessages(messages, self);
                },
                error -> showInlineAlert("We couldn't load this conversation. Check your connection and retry."),
                () -> setThreadLoading(false)
        );
    }

    private void renderMessages(List<Message> messages, User self) {
        threadContainer.getChildren().clear();

        if (messages.isEmpty()) {
            VBox empty = new VBox(10, UiIcon.icon(UiIcon.Name.LOCK, 28));
            Label title = new Label("Start the conversation");
            title.getStyleClass().add("empty-state-title");
            Label copy = new Label("Messages are encrypted and hidden inside PNG carrier images before they are stored.");
            copy.setWrapText(true);
            copy.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            copy.getStyleClass().add("empty-state-copy");
            empty.getChildren().addAll(title, copy);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("empty-state");
            threadContainer.getChildren().add(empty);
            return;
        }

        for (Message message : messages) {
            boolean mine = message.getSenderId().equals(self.getId());
            threadContainer.getChildren().add(buildMessageRow(message, mine));
        }
    }

    private Node buildMessageRow(Message message, boolean mine) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stego-card");
        if (mine) card.getStyleClass().add("stego-card-sent");
        card.setMaxWidth(430);

        String meta = (mine ? "You" : currentOtherUser.publicFacingLabel())
                + "  •  " + TIME_FORMAT.format(message.getCreatedAt());
        Label header = new Label(meta);
        header.getStyleClass().add("message-meta");

        HBox security = new HBox(6, UiIcon.icon(UiIcon.Name.LOCK, 13),
                new Label("Encrypted image message"));
        security.getStyleClass().add("security-badge");
        ((Label) security.getChildren().get(1)).getStyleClass().add("security-badge");
        security.setAlignment(Pos.CENTER_LEFT);

        ImageView thumbnail = new ImageView();
        thumbnail.setFitWidth(300);
        thumbnail.setFitHeight(210);
        thumbnail.setPreserveRatio(true);

        try {
            BufferedImage img = imageService.loadSentImage(Path.of(message.getImagePath()));
            thumbnail.setImage(SwingFXUtils.toFXImage(img, null));
        } catch (RuntimeException ignored) {
        }

        card.getChildren().addAll(header, security, thumbnail);

        String decoded = decodedCache.get(message.getId());
        if (decoded != null) {
            Label plaintext = new Label(decoded);
            plaintext.setWrapText(true);
            plaintext.setMaxWidth(390);
            plaintext.getStyleClass().add("plaintext-bubble");
            Label securedLabel = new Label("✓ Decoded securely for this session");
            securedLabel.getStyleClass().add("hint");
            card.getChildren().addAll(plaintext, securedLabel);
        } else if (!mine) {
            Button decodeButton = new Button("Decode Message");
            decodeButton.getStyleClass().add("button-primary");
            decodeButton.setGraphic(UiIcon.icon(UiIcon.Name.KEY, 14));
            decodeButton.setOnAction(e -> handleDecode(message, decodeButton));
            card.getChildren().add(decodeButton);
        } else {
            Label sentLabel = new Label("Sent • recipient can decode with their private key");
            sentLabel.getStyleClass().add("hint");
            card.getChildren().add(sentLabel);
        }

        HBox row = new HBox();
        row.getStyleClass().add("message-row");
        if (mine) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, card);
        } else {
            row.getChildren().add(card);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(spacer);
        }
        return row;
    }

    private void handleDecode(Message message, Button button) {
        User self = sceneManager.services().sessionService.requireCurrentUser();
        var privateKey = sceneManager.services().sessionService.requireCurrentPrivateKey();

        UiTaskRunner.run(
                () -> messageService.decodeMessage(message.getId(), self.getId(), privateKey),
                () -> {
                    button.setDisable(true);
                    button.setText("Decoding…");
                    ProgressIndicator spinner = new ProgressIndicator();
                    spinner.setPrefSize(16, 16);
                    spinner.getStyleClass().add("button-spinner");
                    button.setGraphic(spinner);
                },
                decoded -> {
                    decodedCache.put(message.getId(), decoded.plaintext());
                    loadThreadAsync();
                    refresh();
                },
                error -> showInlineAlert(error instanceof MessageService.MessageException
                        ? error.getMessage()
                        : "We couldn't decode this message."),
                () -> {}
        );
    }

    public void refresh() {
        User self = sceneManager.services().sessionService.requireCurrentUser();
        String selectedId = currentConversation != null ? currentConversation.getId() : null;

        conversationSpinner.setVisible(true);
        conversationSpinner.setManaged(true);
        conversationList.setDisable(true);

        UiTaskRunner.run(
                () -> chatService.listConversationSummaries(self.getId()),
                () -> {},
                summaries -> {
                    conversationItems.setAll(summaries);
                    if (selectedId != null) {
                        selectConversationById(selectedId);
                    } else if (!conversationItems.isEmpty()) {
                        conversationList.getSelectionModel().selectFirst();
                    }
                },
                error -> showInlineAlert("We couldn't load your conversations. Check your connection and retry."),
                () -> {
                    conversationList.setDisable(false);
                    conversationSpinner.setVisible(false);
                    conversationSpinner.setManaged(false);
                }
        );
    }

    private void setThreadLoading(boolean loading) {
        threadSpinner.setVisible(loading);
        threadSpinner.setManaged(loading);
        if (loading) {
            threadContainer.getChildren().clear();
        }
    }

    private void showInlineAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Something went wrong");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());
        alert.showAndWait();
    }

    private String initials(String value) {
        if (value == null || value.isBlank()) return "?";
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}
