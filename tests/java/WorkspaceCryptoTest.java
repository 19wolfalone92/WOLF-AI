package ai.wolf.autopilot;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;

/**
 * A real, dependency-free JVM integration test for the SAME crypto class used
 * in Android's encrypted .wolfpack exporter/importer.
 * Run: java -cp <build> ai.wolf.autopilot.WorkspaceCryptoTest
 */
public final class WorkspaceCryptoTest {
    private static int assertions;
    private WorkspaceCryptoTest() {}

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static void failDecrypt(byte[] ciphertext, char[] password,
                                    byte[] salt, byte[] iv, String scenario)
            throws Exception {
        boolean failed = false;
        try {
            WorkspaceCrypto.cipher(Cipher.DECRYPT_MODE, password, salt, iv)
                    .doFinal(ciphertext);
        } catch (AEADBadTagException expected) {
            failed = true;
        }
        check(failed, scenario + " must fail AES-GCM authentication");
    }

    public static void main(String[] args) throws Exception {
        check(new String(WorkspaceCrypto.MAGIC, StandardCharsets.US_ASCII)
                .equals("WOLFWK9\n"), "preserve portable WOLF v9 backup file marker");
        check(WorkspaceCrypto.ITERATIONS == 240_000, "preserve existing KDF cost");
        check(WorkspaceCrypto.SALT_LENGTH == 16 && WorkspaceCrypto.IV_LENGTH == 12,
                "preserve existing archive header sizes");

        byte[] salt = new byte[WorkspaceCrypto.SALT_LENGTH];
        byte[] nonce = new byte[WorkspaceCrypto.IV_LENGTH];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(nonce);
        char[] password = "wolf secure user password!".toCharArray();
        byte[] plain = ("{\"schema\":1,\"documents\":[\"Пример русского текста\"]}" +
                " — WOLF AI portable encrypted backup").getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = WorkspaceCrypto.cipher(Cipher.ENCRYPT_MODE,
                password, salt, nonce).doFinal(plain);
        check(encrypted.length == plain.length + 16, "GCM must add 128-bit tag");
        check(!Arrays.equals(plain, encrypted), "ciphertext must not equal plaintext");

        byte[] restored = WorkspaceCrypto.cipher(Cipher.DECRYPT_MODE,
                password, salt, nonce).doFinal(encrypted);
        check(Arrays.equals(plain, restored), "real UTF-8 round trip must match bytes");

        byte[] modified = encrypted.clone();
        modified[modified.length / 2] ^= 0x11;
        failDecrypt(modified, password, salt, nonce, "tampered ciphertext");

        byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - 1);
        failDecrypt(truncated, password, salt, nonce, "truncated authentication tag");

        byte[] anotherSalt = salt.clone();
        anotherSalt[0] ^= 0x23;
        failDecrypt(encrypted, password, anotherSalt, nonce, "altered KDF salt");

        byte[] anotherNonce = nonce.clone();
        anotherNonce[0] ^= 0x21;
        failDecrypt(encrypted, password, salt, anotherNonce, "altered nonce");

        failDecrypt(encrypted, "wrong password 12345!".toCharArray(),
                salt, nonce, "wrong passphrase");

        byte[] encryptAgain = WorkspaceCrypto.cipher(Cipher.ENCRYPT_MODE,
                password, anotherSalt, anotherNonce).doFinal(plain);
        check(!Arrays.equals(encrypted, encryptAgain),
                "unique salt/nonce must produce distinct ciphertext");

        boolean rejected = false;
        try {
            WorkspaceCrypto.cipher(Cipher.ENCRYPT_MODE, "short".toCharArray(),
                    salt, nonce);
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "short passwords must be refused");

        rejected = false;
        try {
            WorkspaceCrypto.cipher(Cipher.DECRYPT_MODE, password,
                    new byte[8], nonce);
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid salt length must be refused");

        rejected = false;
        try {
            WorkspaceCrypto.cipher(Cipher.ENCRYPT_MODE, password,
                    salt, new byte[8]);
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid nonce length must be refused");

        Arrays.fill(password, '\0');
        check(password[0] == '\0', "sensitive caller-provided char[] can be erased");
        System.out.println("PASS WOLF AES-GCM v9 real JVM tests: " +
                assertions + " assertions; byte-exact roundtrip, tamper, " +
                "wrong password, nonce, salt, truncated tag.");
    }
}
