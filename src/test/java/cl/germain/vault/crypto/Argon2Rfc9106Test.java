package cl.germain.vault.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.junit.jupiter.api.Test;

class Argon2Rfc9106Test {

  @Test
  void matchesRfc9106Section53Argon2idVector() {
    byte[] password = filled(32, (byte) 0x01);
    byte[] salt = filled(16, (byte) 0x02);
    byte[] secret = filled(8, (byte) 0x03);
    byte[] associatedData = filled(12, (byte) 0x04);
    byte[] actual = new byte[32];

    org.bouncycastle.crypto.params.Argon2Parameters parameters =
        new org.bouncycastle.crypto.params.Argon2Parameters.Builder(
                org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
            .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(32)
            .withIterations(3)
            .withParallelism(4)
            .withSalt(salt)
            .withSecret(secret)
            .withAdditional(associatedData)
            .build();

    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(parameters);
    generator.generateBytes(password, actual);

    assertArrayEquals(
        hex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"), actual);
  }

  private static byte[] filled(int length, byte value) {
    byte[] bytes = new byte[length];
    Arrays.fill(bytes, value);
    return bytes;
  }

  private static byte[] hex(String value) {
    return java.util.HexFormat.of().parseHex(value);
  }
}
