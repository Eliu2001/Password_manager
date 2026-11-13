package cl.germain.vault.crypto;

import java.util.Objects;

/** AES-GCM output: a 96-bit nonce and ciphertext followed by its tag. */
public final class EncryptedData {
    static final int NONCE_LENGTH_BYTES = 12;
    static final int GCM_TAG_LENGTH_BYTES = 16;

    private final byte[] nonce;
    private final byte[] ciphertextAndTag;

    public EncryptedData(byte[] nonce, byte[] ciphertextAndTag) {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(ciphertextAndTag, "ciphertextAndTag");
        if (nonce.length != NONCE_LENGTH_BYTES) {
            throw new IllegalArgumentException("Nonce must be 12 bytes");
        }
        if (ciphertextAndTag.length < GCM_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("Ciphertext must contain a complete GCM tag");
        }
        this.nonce = nonce.clone();
        this.ciphertextAndTag = ciphertextAndTag.clone();
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertextAndTag() {
        return ciphertextAndTag.clone();
    }
}
