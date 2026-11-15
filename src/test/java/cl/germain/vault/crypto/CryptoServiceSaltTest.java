package cl.germain.vault.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CryptoServiceSaltTest {

  @Test
  void generatesSixteenByteSaltsThatDifferInConsecutiveCalls() {
    CryptoService crypto = new CryptoService();

    byte[] first = crypto.generateSalt();
    byte[] second = crypto.generateSalt();
    try {
      assertEquals(16, first.length);
      assertEquals(16, second.length);
      assertFalse(Arrays.equals(first, second));
    } finally {
      Arrays.fill(first, (byte) 0);
      Arrays.fill(second, (byte) 0);
    }
  }
}
