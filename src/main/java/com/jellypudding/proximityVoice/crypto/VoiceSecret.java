package com.jellypudding.proximityVoice.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

public class VoiceSecret {

    public static final int SECRET_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;

    private VoiceSecret(byte[] secret) {
        this.secret = secret;
        this.keySpec = new SecretKeySpec(secret, "AES");
    }

    public static VoiceSecret generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return new VoiceSecret(bytes);
    }

    public static VoiceSecret fromBytes(byte[] bytes) {
        if (bytes.length != SECRET_BYTES) throw new IllegalArgumentException("Bad secret length");
        return new VoiceSecret(bytes.clone());
    }

    public byte[] getBytes() { return secret.clone(); }

    // Wire format: [IV (12 bytes)][AES-GCM ciphertext + 16-byte tag]
    public byte[] encrypt(byte[] plaintext) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] result = new byte[IV_BYTES + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_BYTES);
        System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
        return result;
    }

    public byte[] decrypt(byte[] payload) throws Exception {
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VoiceSecret other)) return false;
        return Arrays.equals(secret, other.secret);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(secret); }
}
