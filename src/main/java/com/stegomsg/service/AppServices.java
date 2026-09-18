package com.stegomsg.service;

import com.stegomsg.db.ConversationRepository;
import com.stegomsg.db.Database;
import com.stegomsg.db.ImageHistoryRepository;
import com.stegomsg.db.MessageRepository;
import com.stegomsg.db.SecurityEventRepository;
import com.stegomsg.db.UserRepository;
import com.stegomsg.security.CryptoService;
import com.stegomsg.security.DeviceKeyStore;
import com.stegomsg.security.KeyManager;
import com.stegomsg.security.PasswordHasher;
import com.stegomsg.security.Pbkdf2PasswordHasher;
import com.stegomsg.stego.CapacityCalculator;
import com.stegomsg.stego.SteganographyService;
import com.stegomsg.util.DefaultImageGenerator;
import com.stegomsg.model.ImageHistoryEntry;

import java.nio.file.Path;
import java.util.List;

/**
 * Simple hand-wired dependency container (no DI framework — deliberately, per
 * ARCHITECTURE.md section 48: a student project should favor a few extra constructor
 * arguments over pulling in Spring/Guice for an app this size). Every field is a
 * long-lived singleton for the life of the running application.
 */
public final class AppServices {

    public final SessionService sessionService;
    public final AuthService authService;
    public final ChatService chatService;
    public final MessageService messageService;
    public final ImageService imageService;
    private final ImageHistoryRepository imageHistoryRepository;

    public AppServices(Path appDataDir) {
        Path dbFile = appDataDir.resolve("app.db");
        Path sentImagesDir = appDataDir.resolve("images/sent");
        Path deviceKeysDir = appDataDir.resolve("device-keys");
        Path defaultImagesDir = appDataDir.resolve("images/defaults");

        Database database = new Database(dbFile);
        database.initializeSchema();

        UserRepository userRepository = new UserRepository(database);
        ConversationRepository conversationRepository = new ConversationRepository(database);
        MessageRepository messageRepository = new MessageRepository(database);
        this.imageHistoryRepository = new ImageHistoryRepository(database);
        SecurityEventRepository securityEventRepository = new SecurityEventRepository(database);

        PasswordHasher passwordHasher = new Pbkdf2PasswordHasher();
        PasswordHasher pinHasher = new Pbkdf2PasswordHasher();
        KeyManager keyManager = new KeyManager();
        CryptoService cryptoService = new CryptoService();
        DeviceKeyStore deviceKeyStore = new DeviceKeyStore(deviceKeysDir);
        SteganographyService steganographyService = new SteganographyService();
        CapacityCalculator capacityCalculator = new CapacityCalculator();

        DefaultImageGenerator.ensureDefaultImagesExist(defaultImagesDir);

        this.sessionService = new SessionService();
        this.authService = new AuthService(userRepository, securityEventRepository, passwordHasher, pinHasher,
                keyManager, cryptoService, deviceKeyStore, sessionService);
        this.chatService = new ChatService(conversationRepository, messageRepository, userRepository);
        this.imageService = new ImageService(sentImagesDir, defaultImagesDir);
        this.messageService = new MessageService(messageRepository, imageHistoryRepository, cryptoService,
                keyManager, steganographyService, capacityCalculator, imageService);
    }

    /** Exposed for the Image History screen — see ARCHITECTURE.md sections 19/38. */
    public List<ImageHistoryEntry> imageHistory(String userId) {
        return imageHistoryRepository.findForUser(userId);
    }
}
