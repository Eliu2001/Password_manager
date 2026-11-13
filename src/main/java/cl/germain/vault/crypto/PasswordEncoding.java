package cl.germain.vault.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/** UTF-8 encoding for password characters with scoped temporary buffers. */
final class PasswordEncoding {

    private PasswordEncoding() {}

    /**
     * Encodes a password as UTF-8 and gives the bytes to {@code consumer} only
     * for the duration of the call. All owned character and byte buffers are
     * cleared on both success and failure.
     */
    static <T> T withUtf8Password(char[] password, Function<byte[], T> consumer) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(consumer, "consumer");

        char[] normalized = PasswordNormalizer.normalizeIfNeeded(password);
        ByteBuffer encodedBuffer = null;
        byte[] encodedBytes = null;
        try {
            int capacity = Math.multiplyExact(normalized.length, 3);
            encodedBuffer = ByteBuffer.allocate(capacity);
            var encoder = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                CoderResult encodeResult = encoder.encode(CharBuffer.wrap(normalized), encodedBuffer, true);
                if (encodeResult.isError()) {
                    encodeResult.throwException();
                }
                CoderResult flushResult = encoder.flush(encodedBuffer);
                if (flushResult.isError()) {
                    flushResult.throwException();
                }
            } catch (CharacterCodingException e) {
                throw new IllegalArgumentException("Password contains malformed Unicode", e);
            }

            encodedBuffer.flip();
            encodedBytes = new byte[encodedBuffer.remaining()];
            encodedBuffer.get(encodedBytes);
            return consumer.apply(encodedBytes);
        } finally {
            if (encodedBuffer != null) {
                Arrays.fill(encodedBuffer.array(), (byte) 0);
            }
            if (encodedBytes != null) {
                Arrays.fill(encodedBytes, (byte) 0);
            }
            if (normalized != password) {
                Arrays.fill(normalized, '\0');
            }
        }
    }
}
