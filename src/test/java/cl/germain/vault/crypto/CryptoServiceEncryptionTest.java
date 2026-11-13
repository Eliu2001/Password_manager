package cl.germain.vault.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoServiceEncryptionTest {

    @Test
    void encryptsWithExpectedNonceAndTagLengths() {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);

        try (key) {
            EncryptedData encrypted = crypto.encrypt(key, new byte[0], new byte[0]);
            assertEquals(12, encrypted.nonce().length);
            assertEquals(16, encrypted.ciphertextAndTag().length);
        }
    }

    @Test
    void rejectsNullArgumentsAndClosedKey() {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);
        assertThrows(NullPointerException.class, () -> crypto.encrypt(null, new byte[0], new byte[0]));
        assertThrows(NullPointerException.class, () -> crypto.encrypt(key, null, new byte[0]));
        assertThrows(NullPointerException.class, () -> crypto.encrypt(key, new byte[0], null));
        key.close();
        assertThrows(IllegalStateException.class, () -> crypto.encrypt(key, new byte[0], new byte[0]));
    }

    @Test
    void encryptingSameInputTwiceProducesDifferentSampledNonces() {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);
        try (key) {
            EncryptedData first = crypto.encrypt(key, new byte[0], new byte[0]);
            EncryptedData second = crypto.encrypt(key, new byte[0], new byte[0]);

            assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        }
    }

    @Test
    void decryptsCiphertextAfterSuccessfulAuthentication() throws DecryptionException {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);
        byte[] plaintext = new byte[]{10, 20, 30, 40};
        byte[] aad = new byte[]{1, 2, 3};

        try (key) {
            EncryptedData encrypted = crypto.encrypt(key, plaintext, aad);
            byte[] decrypted = crypto.decrypt(key, encrypted, aad);
            try {
                assertTrue(Arrays.equals(plaintext, decrypted));
            } finally {
                Arrays.fill(decrypted, (byte) 0);
            }
        }
    }

    @Test
    void authenticationFailuresShareOneCauseFreeExternalException() throws DecryptionException {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);
        byte[] plaintext = new byte[]{11, 22, 33};
        byte[] aad = new byte[]{7, 8};
        byte[] wrongKeyBytes = new byte[32];
        wrongKeyBytes[0] = 1;

        try (key; DerivedKey wrongKey = new DerivedKey(wrongKeyBytes)) {
            EncryptedData valid = crypto.encrypt(key, plaintext, aad);
            assertDecryptionFailure(crypto, wrongKey, valid, aad);
            assertDecryptionFailure(crypto, key, valid, new byte[]{7, 9});

            byte[] alteredNonce = valid.nonce();
            alteredNonce[0] ^= 1;
            assertDecryptionFailure(crypto, key,
                    new EncryptedData(alteredNonce, valid.ciphertextAndTag()), aad);

            byte[] alteredCiphertext = valid.ciphertextAndTag();
            alteredCiphertext[0] ^= 1;
            assertDecryptionFailure(crypto, key,
                    new EncryptedData(valid.nonce(), alteredCiphertext), aad);

            byte[] alteredTag = valid.ciphertextAndTag();
            alteredTag[alteredTag.length - 1] ^= 1;
            assertDecryptionFailure(crypto, key,
                    new EncryptedData(valid.nonce(), alteredTag), aad);
        }
    }

    @Test
    void decryptionArgumentErrorsRemainDistinctFromAuthenticationFailures() {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);
        EncryptedData encrypted = new EncryptedData(new byte[12], new byte[16]);

        assertThrows(NullPointerException.class, () -> crypto.decrypt(null, encrypted, new byte[0]));
        assertThrows(NullPointerException.class, () -> crypto.decrypt(key, null, new byte[0]));
        assertThrows(NullPointerException.class, () -> crypto.decrypt(key, encrypted, null));
        key.close();
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(key, encrypted, new byte[0]));
    }

    @Test
    void roundTripsEmptyAndLargePlaintextsWithEmptyAssociatedData() throws DecryptionException {
        CryptoService crypto = new CryptoService();
        DerivedKey key = new DerivedKey(new byte[32]);
        byte[] empty = new byte[0];
        byte[] large = new byte[1024 * 1024];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i * 31);
        }

        try (key) {
            byte[] emptyResult = crypto.decrypt(key, crypto.encrypt(key, empty, empty), empty);
            byte[] largeResult = crypto.decrypt(key, crypto.encrypt(key, large, empty), empty);
            try {
                assertTrue(Arrays.equals(empty, emptyResult));
                assertTrue(Arrays.equals(large, largeResult));
            } finally {
                Arrays.fill(emptyResult, (byte) 0);
                Arrays.fill(largeResult, (byte) 0);
                Arrays.fill(large, (byte) 0);
            }
        }
    }

    private static void assertDecryptionFailure(CryptoService crypto, DerivedKey key,
                                                EncryptedData encrypted, byte[] aad) {
        DecryptionException failure = assertThrows(DecryptionException.class,
                () -> crypto.decrypt(key, encrypted, aad));
        assertEquals("Decryption failed", failure.getMessage());
        assertNull(failure.getCause());
    }
}
