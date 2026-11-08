package ai.wolf.autopilot;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Java SE-compatible core of WOLF's v9 portable backup encryption.
 * This exact class is executed in CI with real encryption round-trip tests
 * and is also used by the Android .wolfpack exporter/importer.
 *
 * Existing WOLFWK9 backups remain byte-for-byte format compatible.
 */
public final class WorkspaceCrypto {
    public static final byte[] MAGIC = "WOLFWK9\n".getBytes(StandardCharsets.US_ASCII);
    public static final int ITERATIONS = 240_000;
    public static final int SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12;
    private WorkspaceCrypto() {}

    /**
     * Fresh salt and IV are required for EVERY encryption. The caller generates
     * them with SecureRandom and stores them unencrypted in the file header.
     * The 128-bit authentication tag is checked during full decryption.
     */
    public static Cipher cipher(int mode, char[] passphrase, byte[] salt, byte[] iv)
            throws Exception {
        if (mode != Cipher.ENCRYPT_MODE && mode != Cipher.DECRYPT_MODE)
            throw new IllegalArgumentException("Недопустимый режим AES-GCM.");
        if (passphrase == null || passphrase.length < 12 || passphrase.length > 256)
            throw new IllegalArgumentException("Пароль должен содержать 12–256 символов.");
        if (salt == null || salt.length != SALT_LENGTH ||
                iv == null || iv.length != IV_LENGTH)
            throw new IllegalArgumentException("Неверный размер соли или IV.");
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, 256);
        byte[] material = null;
        try {
            material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            Cipher result = Cipher.getInstance("AES/GCM/NoPadding");
            result.init(mode, new SecretKeySpec(material, "AES"),
                    new GCMParameterSpec(128, iv));
            result.updateAAD(MAGIC);
            return result;
        } finally {
            spec.clearPassword();
            if (material != null) Arrays.fill(material, (byte) 0);
        }
    }
}
