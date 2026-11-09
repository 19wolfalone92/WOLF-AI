package ai.wolf.autopilot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Independent BYOK Hugging Face token vault. Never shares GitHub PAT.
 * Token remains in Android Keystore-backed encrypted SharedPreferences only.
 * Only explicit cloud requests send the user's current question upstream.
 */
public final class CloudBrainStore {
    private static final String ALIAS = "WOLF_CLOUD_HF_AES_V1";
    private static final String PREFS = "wolf_cloud_brain_v1";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-20b:cheapest";
    private final SharedPreferences prefs;

    public CloudBrainStore(Context app) {
        prefs = app.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String validateModel(String value) {
        return CloudBrainPolicy.model(value);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry current = store.getEntry(ALIAS, null);
        if (current instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)current).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    public void save(String rawToken, String requestedModel) throws Exception {
        String value = rawToken == null ? "" : rawToken.trim();
        if (value.length() < 15 || value.length() > 512 ||
                !value.matches("hf_[A-Za-z0-9]{12,509}"))
            throw new IllegalArgumentException(
                    "Нужен токен Hugging Face hf_… с разрешением Inference Providers.");
        String model = validateModel(requestedModel);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        if (!prefs.edit()
                .putString("token",Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString("nonce",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .putString("model",model)
                .commit())
            throw new IllegalStateException("Не удалось сохранить данные в Android.");
    }

    public String token() throws Exception {
        String encrypted=prefs.getString("token",null);
        String nonce=prefs.getString("nonce",null);
        if (encrypted==null || nonce==null)
            throw new IllegalStateException("Hugging Face ещё не подключён.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),
                new GCMParameterSpec(128,Base64.decode(nonce,Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(encrypted,Base64.DEFAULT)),
                StandardCharsets.UTF_8);
    }

    public String model() {
        return validateModel(prefs.getString("model",DEFAULT_MODEL));
    }
    public boolean configured() {
        return prefs.contains("token") && prefs.contains("nonce");
    }
    public void clear() {
        prefs.edit().remove("token").remove("nonce").remove("model").commit();
    }
}
