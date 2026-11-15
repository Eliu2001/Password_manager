package cl.germain.vault.totp;

import java.util.Arrays;
import java.util.Objects;

/** Strict RFC 4648 Base32 decoder that never materializes the input as a String. */
public final class Base32 {
  private Base32() {}

  public static byte[] decode(char[] input) {
    Objects.requireNonNull(input, "input");
    int end = input.length;
    while (end > 0 && input[end - 1] == '=') end--;
    int padding = input.length - end;
    if (end > 103) throw invalid();
    if (padding != 0) {
      int expected =
          switch (end % 8) {
            case 2 -> 6;
            case 4 -> 4;
            case 5 -> 3;
            case 7 -> 1;
            default -> 0;
          };
      if (expected != padding || input.length % 8 != 0) throw invalid();
    }
    int remainder = end % 8;
    if (!(remainder == 0 || remainder == 2 || remainder == 4 || remainder == 5 || remainder == 7))
      throw invalid();
    int outputLength = end * 5 / 8;
    if (outputLength < 20 || outputLength > 64) throw invalid();
    byte[] out = new byte[outputLength];
    int accumulator = 0, bits = 0, at = 0;
    try {
      for (int i = 0; i < end; i++) {
        char c = input[i];
        int value =
            c >= 'A' && c <= 'Z'
                ? c - 'A'
                : c >= 'a' && c <= 'z' ? c - 'a' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
        if (value < 0) throw invalid();
        accumulator = (accumulator << 5) | value;
        bits += 5;
        if (bits >= 8) {
          bits -= 8;
          out[at++] = (byte) (accumulator >> bits);
        }
      }
      if (bits > 0 && (accumulator & ((1 << bits) - 1)) != 0) throw invalid();
      if (at != out.length) throw invalid();
      return out;
    } catch (RuntimeException exception) {
      Arrays.fill(out, (byte) 0);
      if (exception instanceof IllegalArgumentException) throw exception;
      throw invalid();
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Base32 secret is invalid");
  }
}
