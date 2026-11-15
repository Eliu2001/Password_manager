package cl.germain.vault.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EncryptedDataTest {

  @Test
  void validatesLengthsAndRejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> new EncryptedData(null, new byte[16]));
    assertThrows(NullPointerException.class, () -> new EncryptedData(new byte[12], null));
    assertThrows(
        IllegalArgumentException.class, () -> new EncryptedData(new byte[11], new byte[16]));
    assertThrows(
        IllegalArgumentException.class, () -> new EncryptedData(new byte[13], new byte[16]));
    assertThrows(
        IllegalArgumentException.class, () -> new EncryptedData(new byte[12], new byte[15]));
  }

  @Test
  void defensivelyCopiesArraysOnConstructionAndAccess() {
    byte[] nonce = new byte[12];
    byte[] ciphertext = new byte[16];
    nonce[0] = 1;
    ciphertext[0] = 2;
    EncryptedData encryptedData = new EncryptedData(nonce, ciphertext);
    nonce[0] = 3;
    ciphertext[0] = 4;

    byte[] returnedNonce = encryptedData.nonce();
    byte[] returnedCiphertext = encryptedData.ciphertextAndTag();
    assertArrayEquals(new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, returnedNonce);
    assertArrayEquals(
        new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, returnedCiphertext);

    returnedNonce[0] = 5;
    returnedCiphertext[0] = 6;
    assertNotSame(returnedNonce, encryptedData.nonce());
    assertNotSame(returnedCiphertext, encryptedData.ciphertextAndTag());
    assertArrayEquals(new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, encryptedData.nonce());
    assertArrayEquals(
        new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        encryptedData.ciphertextAndTag());
  }
}
