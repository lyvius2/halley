package banghak.home.halley.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** 금융결제원 OAuth 토큰을 AES-GCM으로 암호화한다. */
@Component
public class KftcTokenCipher {
    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final KftcOpenBankingProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public KftcTokenCipher(KftcOpenBankingProperties properties) {
        this(properties, new SecureRandom());
    }

    KftcTokenCipher(KftcOpenBankingProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    /** 평문 토큰을 암호문으로 변환한다. */
    public String encrypt(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        try {
            final byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + encode(iv) + ":" + encode(encrypted);
        } catch (GeneralSecurityException e) {
            throw new KftcTokenEncryptionConfigurationException("Unable to encrypt KFTC token", e);
        }
    }

    /** 암호문 토큰을 평문으로 복호화한다. */
    public String decrypt(String encryptedToken) {
        final String[] parts = encryptedToken == null ? new String[0] : encryptedToken.split(":", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new KftcTokenEncryptionConfigurationException("Unsupported KFTC token ciphertext");
        }
        try {
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, decode(parts[1])));
            return new String(cipher.doFinal(decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new KftcTokenEncryptionConfigurationException("Unable to decrypt KFTC token", e);
        }
    }

    private SecretKey secretKey() {
        final byte[] bytes;
        try {
            bytes = decode(properties.getTokenEncryptionKey());
        } catch (IllegalArgumentException e) {
            throw new KftcTokenEncryptionConfigurationException("KFTC token encryption key must be Base64", e);
        }
        if (bytes.length != 32) {
            throw new KftcTokenEncryptionConfigurationException(
                    "KFTC token encryption key must contain 32 bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
