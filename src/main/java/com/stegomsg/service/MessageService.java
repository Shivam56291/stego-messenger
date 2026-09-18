package com.stegomsg.service;

import com.stegomsg.db.ImageHistoryRepository;
import com.stegomsg.db.MessageRepository;
import com.stegomsg.model.ImageHistoryEntry;
import com.stegomsg.model.Message;
import com.stegomsg.model.User;
import com.stegomsg.security.CryptoService;
import com.stegomsg.security.KeyManager;
import com.stegomsg.stego.CapacityCalculator;
import com.stegomsg.stego.SteganographyService;
import com.stegomsg.util.Constants;
import com.stegomsg.util.Ids;

import javax.crypto.SecretKey;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;

/**
 * Implements the exact end-to-end pipelines from ARCHITECTURE.md sections 32/33:
 *
 * SEND: plaintext -> AES-256-GCM encrypt (fresh key+nonce) -> RSA-OAEP wrap the AES key
 *       for the recipient -> frame [keyLen|wrappedKey|nonce|ciphertext+tag] -> LSB-embed
 *       into the chosen carrier image -> save stego PNG -> insert message + image-history
 *       rows.
 *
 * RECEIVE: authorization-checked message lookup -> load stego image -> LSB-extract ->
 *          parse frame -> RSA-OAEP unwrap the AES key with the RECIPIENT's own private
 *          key (never the sender's) -> AES-256-GCM decrypt+verify -> plaintext.
 *
 * Every cryptographic primitive call is delegated to CryptoService/KeyManager — this
 * class only handles wire-framing and orchestration, never touches raw key bytes itself
 * beyond what's needed to concatenate/parse the frame.
 */
public final class MessageService {

    private static final int KEY_LENGTH_FIELD_BYTES = 2;

    private final MessageRepository messageRepository;
    private final ImageHistoryRepository imageHistoryRepository;
    private final CryptoService cryptoService;
    private final KeyManager keyManager;
    private final SteganographyService steganographyService;
    private final CapacityCalculator capacityCalculator;
    private final ImageService imageService;

    public MessageService(MessageRepository messageRepository, ImageHistoryRepository imageHistoryRepository,
                           CryptoService cryptoService, KeyManager keyManager,
                           SteganographyService steganographyService, CapacityCalculator capacityCalculator,
                           ImageService imageService) {
        this.messageRepository = messageRepository;
        this.imageHistoryRepository = imageHistoryRepository;
        this.cryptoService = cryptoService;
        this.keyManager = keyManager;
        this.steganographyService = steganographyService;
        this.capacityCalculator = capacityCalculator;
        this.imageService = imageService;
    }

    /**
     * Live capacity check the UI binds to as the user types (section 14). Uses the exact
     * same math the send pipeline will use, so what's shown is never an approximation.
     */
    public CapacityCalculator.CapacityReport checkCapacity(BufferedImage carrier, String plaintext) {
        int plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8).length;
        return capacityCalculator.report(carrier.getWidth(), carrier.getHeight(),
                Constants.DEFAULT_BITS_PER_CHANNEL, plaintextBytes);
    }

    public Message sendMessage(User sender, User recipient, String conversationId, String plaintext,
                                BufferedImage carrierImage, String carrierDisplayName) {
        CapacityCalculator.CapacityReport report = checkCapacity(carrierImage, plaintext);
        if (report.status() == CapacityCalculator.Status.TOO_LARGE) {
            throw new MessageException("Selected image does not have enough capacity for this message.");
        }

        SecretKey aesKey = cryptoService.generateMessageKey();
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        CryptoService.EncryptedPayload encrypted = cryptoService.encrypt(plaintextBytes, aesKey);

        PublicKey recipientPublicKey = keyManager.decodePublicKey(recipient.getPublicKeyBase64());
        byte[] wrappedKey = keyManager.wrapMessageKey(aesKey, recipientPublicKey);

        byte[] framedPayload = buildFrame(wrappedKey, encrypted.nonce(), encrypted.ciphertextAndTag());

        BufferedImage stegoImage;
        try {
            stegoImage = steganographyService.embed(carrierImage, framedPayload, Constants.DEFAULT_BITS_PER_CHANNEL);
        } catch (IllegalArgumentException e) {
            // Defensive backstop in case the UI's live check and this check ever disagree.
            throw new MessageException("Selected image does not have enough capacity for this message.");
        }

        String filename = Ids.newId() + ".png";
        Path savedPath = imageService.saveSentImage(stegoImage, filename);

        Message message = new Message(Ids.newId(), conversationId, sender.getId(), recipient.getId(),
                savedPath.toString(), framedPayload.length, carrierImage.getWidth(), carrierImage.getHeight(),
                Message.Status.SENT, Instant.now());
        messageRepository.insert(message);

        imageHistoryRepository.insertAndPrune(new ImageHistoryEntry(
                Ids.newId(), sender.getId(), message.getId(), carrierDisplayName,
                carrierImage.getWidth(), carrierImage.getHeight(),
                report.maxPayloadBytes(), report.actualPayloadBytes(),
                recipient.publicFacingLabel(), Instant.now()
        ));

        return message;
    }

    public record DecodedMessage(String plaintext, Message message) {
    }

    /**
     * Decodes a received message. {@code requestingUserId} and {@code recipientPrivateKey}
     * MUST correspond to the same logged-in session — the repository call itself enforces
     * that only the true recipient (or sender) can even fetch the message row at all.
     */
    public DecodedMessage decodeMessage(String messageId, String requestingUserId, PrivateKey recipientPrivateKey) {
        Message message = messageRepository.findByIdForUser(messageId, requestingUserId)
                .orElseThrow(() -> new MessageException("Message not found."));

        BufferedImage stegoImage = imageService.loadSentImage(Path.of(message.getImagePath()));
        byte[] framedPayload;
        try {
            framedPayload = steganographyService.extract(stegoImage, Constants.DEFAULT_BITS_PER_CHANNEL);
        } catch (RuntimeException e) {
            throw new MessageException("Unable to verify this message. The image may be corrupted or modified.");
        }

        Frame frame = parseFrame(framedPayload);
        SecretKey aesKey;
        byte[] plaintextBytes;
        try {
            aesKey = keyManager.unwrapMessageKey(frame.wrappedKey(), recipientPrivateKey);
            plaintextBytes = cryptoService.decrypt(frame.nonce(), frame.ciphertextAndTag(), aesKey);
        } catch (RuntimeException | CryptoService.MessageIntegrityException e) {
            throw new MessageException("Unable to verify this message. The image may be corrupted or modified.");
        }

        messageRepository.markRead(message.getId(), requestingUserId);
        return new DecodedMessage(new String(plaintextBytes, StandardCharsets.UTF_8), message);
    }

    // ---- wire framing: [2-byte keyLen][wrappedKey][12-byte nonce][ciphertext+tag] ----

    private byte[] buildFrame(byte[] wrappedKey, byte[] nonce, byte[] ciphertextAndTag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((wrappedKey.length >>> 8) & 0xFF);
        out.write(wrappedKey.length & 0xFF);
        try {
            out.write(wrappedKey);
            out.write(nonce);
            out.write(ciphertextAndTag);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream never actually throws
        }
        return out.toByteArray();
    }

    private record Frame(byte[] wrappedKey, byte[] nonce, byte[] ciphertextAndTag) {
    }

    private Frame parseFrame(byte[] framedPayload) {
        ByteArrayInputStream in = new ByteArrayInputStream(framedPayload);
        int keyLen = (in.read() << 8) | in.read();
        if (keyLen <= 0 || keyLen > framedPayload.length) {
            throw new MessageException("Unable to verify this message. The image may be corrupted or modified.");
        }
        try {
            byte[] wrappedKey = in.readNBytes(keyLen);
            byte[] nonce = in.readNBytes(CryptoService.NONCE_LENGTH_BYTES);
            byte[] ciphertextAndTag = in.readAllBytes();
            return new Frame(wrappedKey, nonce, ciphertextAndTag);
        } catch (IOException e) {
            // ByteArrayInputStream never actually throws IOException in practice; this
            // catch only exists to satisfy InputStream's checked-exception contract.
            throw new MessageException("Unable to verify this message. The image may be corrupted or modified.");
        }
    }

    public static final class MessageException extends RuntimeException {
        public MessageException(String message) {
            super(message);
        }
    }
}
