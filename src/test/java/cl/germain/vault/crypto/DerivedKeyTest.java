package cl.germain.vault.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DerivedKeyTest {

    @Test
    void providesKeyBeforeCloseAndClearsItWhenClosed() {
        byte[] ownedBytes = new byte[32];
        java.util.Arrays.fill(ownedBytes, (byte) 0x5a);
        DerivedKey derivedKey = new DerivedKey(ownedBytes);

        int keyLength = derivedKey.useBytes(bytes -> bytes.length);
        byte firstKeyByte = derivedKey.useBytes(bytes -> bytes[0]);
        assertEquals(32, keyLength);
        assertEquals((byte) 0x5a, firstKeyByte);
        derivedKey.close();

        assertArrayEquals(new byte[32], ownedBytes);
        assertThrows(IllegalStateException.class, () -> derivedKey.useBytes(bytes -> bytes.length));
    }

    @Test
    void closeIsIdempotentAndRejectsInvalidKeyLength() {
        DerivedKey derivedKey = new DerivedKey(new byte[32]);
        derivedKey.close();
        derivedKey.close();

        assertThrows(IllegalStateException.class, () -> derivedKey.useBytes(bytes -> bytes.length));
        assertThrows(IllegalArgumentException.class, () -> new DerivedKey(new byte[31]));
    }
}
