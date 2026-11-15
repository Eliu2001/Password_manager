package cl.germain.vault.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;

/** RFC 6238 TOTP using HMAC-SHA-1, six digits and a 30-second step. */
public final class TotpGenerator {
    private final Clock clock;

    public TotpGenerator() { this(Clock.systemUTC()); }
    public TotpGenerator(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** Returns a caller-owned code array; the caller must clear it after use. */
    public char[] generate(byte[] secret) {
        Objects.requireNonNull(secret, "secret");
        validateSecret(secret);
        long second = clock.instant().getEpochSecond();
        if (second < 0) throw new IllegalArgumentException("Time is before the Unix epoch");
        return generate(secret, second);
    }

    public Result generateCurrent(byte[] secret) {
        long second = clock.instant().getEpochSecond();
        if (second < 0) throw new IllegalArgumentException("Time is before the Unix epoch");
        return new Result(generate(secret, second), 30 - (int) Math.floorMod(second, 30));
    }

    /** The caller owns and must clear {@code code}. */
    public record Result(char[] code, int secondsRemaining) { }

    public static char[] generate(byte[] secret, long epochSecond) {
        Objects.requireNonNull(secret, "secret");
        validateSecret(secret);
        if (epochSecond < 0) throw new IllegalArgumentException("Time is before the Unix epoch");
        byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(epochSecond / 30).array();
        byte[] digest = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            digest = mac.doFinal(counter);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            int code = binary % 1_000_000;
            char[] result = new char[6];
            for (int i = result.length - 1; i >= 0; i--) {
                result[i] = (char) ('0' + code % 10);
                code /= 10;
            }
            return result;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP calculation failed");
        } finally {
            Arrays.fill(counter, (byte) 0);
            if (digest != null) Arrays.fill(digest, (byte) 0);
        }
    }

    private static void validateSecret(byte[] secret) {
        if (secret.length < 20 || secret.length > 64) throw new IllegalArgumentException("TOTP secret length is invalid");
    }

    public int secondsRemaining() {
        long second = clock.instant().getEpochSecond();
        if (second < 0) throw new IllegalArgumentException("Time is before the Unix epoch");
        return 30 - (int) Math.floorMod(second, 30);
    }
}
