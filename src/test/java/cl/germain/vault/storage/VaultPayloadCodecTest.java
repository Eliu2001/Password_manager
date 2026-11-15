package cl.germain.vault.storage;

import cl.germain.vault.model.Vault;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultPayloadCodecTest {
    private final VaultPayloadCodec codec = new VaultPayloadCodec();
    private final VaultPayloadParser parser = new VaultPayloadParser();

    @Test
    void encodesEntriesInTheSpecifiedOrderAndBigEndianLayout() {
        byte[] id = new byte[16];
        for (int i = 0; i < id.length; i++) {
            id[i] = (byte) i;
        }
        char[] password = {'p', '\uD83D', '\uDD10'};

        try (Vault vault = new Vault()) {
            vault.restoreEntry(id, Instant.ofEpochMilli(0x0000010203040506L),
                    Instant.ofEpochMilli(0x0000010203040507L), "café", "user", password, "n");
            byte[] payload = codec.encode(vault);
            try {
                byte[] expected = HexFormat.of().parseHex(
                        "00000001" +
                        "000102030405060708090a0b0c0d0e0f" +
                        "0000010203040506" +
                        "0000010203040507" +
                        "00000005636166c3a9" +
                        "0000000475736572" +
                        "0000000570f09f9490" +
                        "000000016e");
                assertArrayEquals(expected, payload);
            } finally {
                Arrays.fill(payload, (byte) 0);
            }
        } finally {
            Arrays.fill(id, (byte) 0);
            Arrays.fill(password, '\0');
        }
    }

    @Test
    void encodesEmptyVaultAsZeroEntryCount() {
        try (Vault vault = new Vault()) {
            byte[] payload = codec.encode(vault);
            try {
                assertArrayEquals(new byte[]{0, 0, 0, 0}, payload);
            } finally {
                Arrays.fill(payload, (byte) 0);
            }
        }
    }

    @Test
    void parsesCompletePayloadAndRestoresOrderAndValues() {
        byte[] id = new byte[16];
        id[0] = 12;
        char[] password = {'p', 'a', 's', 's'};
        byte[] payload = null;
        try (Vault source = new Vault()) {
            source.restoreEntry(id, Instant.ofEpochMilli(1234), Instant.ofEpochMilli(5678),
                    "servicio", "usuario", password, "nota");
            payload = codec.encode(source);
        }
        Arrays.fill(password, '\0');

        try (Vault restored = parser.parse(payload)) {
            assertArrayEquals(id, restored.list().get(0).id());
            assertEquals(Instant.ofEpochMilli(1234), restored.list().get(0).createdAt());
            assertEquals(Instant.ofEpochMilli(5678), restored.list().get(0).updatedAt());
            assertEquals("servicio", restored.list().get(0).service());
            assertEquals("usuario", restored.list().get(0).username());
            char[] restoredPassword = restored.list().get(0).password();
            try {
                assertArrayEquals(new char[]{'p', 'a', 's', 's'}, restoredPassword);
            } finally {
                Arrays.fill(restoredPassword, '\0');
            }
            assertEquals("nota", restored.list().get(0).notes());
        } finally {
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(id, (byte) 0);
        }
    }

    @Test void v2RoundTripAddsOptionalTotpFieldAndRejectsMalformedSecretLengths() {
        byte[] id = new byte[16]; id[0] = 4;
        byte[] secret = new byte[20]; Arrays.fill(secret, (byte) 11);
        char[] password = {'s', 'y', 'n'};
        byte[] payload;
        try (Vault source = new Vault()) {
            source.restoreEntry(id, Instant.ofEpochMilli(10), Instant.ofEpochMilli(20),
                    "service", "user", password, "", secret);
            payload = codec.encode(source, 2);
        } finally { Arrays.fill(password, '\0'); Arrays.fill(secret, (byte) 0); }
        try (Vault restored = parser.parse(payload, 2)) {
            assertArrayEquals(id, restored.list().get(0).id());
            byte[] actual = restored.totpSecret(id).orElseThrow();
            assertEquals(20, actual.length);
            for (byte value : actual) assertEquals(11, value);
            Arrays.fill(actual, (byte) 0);
        }
        byte[] badLength = payload.clone();
        ByteBuffer.wrap(badLength).putInt(badLength.length - 24, 19);
        byte[] oversizedLength = payload.clone();
        ByteBuffer.wrap(oversizedLength).putInt(oversizedLength.length - 24, 0xffff_ffff);
        byte[] trailing = Arrays.copyOf(payload, payload.length + 1);
        byte[] truncated = Arrays.copyOf(payload, payload.length - 1);
        try {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(badLength, 2));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(oversizedLength, 2));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(trailing, 2));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(truncated, 2));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(payload, 1));
        } finally {
            Arrays.fill(id, (byte) 0);
            Arrays.fill(payload, (byte) 0); Arrays.fill(badLength, (byte) 0);
            Arrays.fill(oversizedLength, (byte) 0); Arrays.fill(trailing, (byte) 0);
            Arrays.fill(truncated, (byte) 0);
        }
    }

    @Test void v2RoundTripsEntryWithoutTotpAsZeroLength() {
        byte[] id = new byte[16];
        char[] password = {'x'};
        byte[] payload;
        try (Vault vault = new Vault()) {
            vault.restoreEntry(id, Instant.ofEpochMilli(1), Instant.ofEpochMilli(2),
                    "service", "user", password, "");
            payload = codec.encode(vault, 2);
        } finally { Arrays.fill(password, '\0'); }
        try (Vault parsed = parser.parse(payload, 2)) {
            assertEquals(1, parsed.list().size());
            assertEquals(java.util.Optional.empty(), parsed.totpSecret(id));
        } finally { Arrays.fill(id, (byte) 0); Arrays.fill(payload, (byte) 0); }
    }

    @Test
    void rejectsImpossibleCountsTrailingBytesAndInvalidUtf8() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{0, 0, 0, 1}));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{0, 0, 0, 0, 1}));

        byte[] invalidUtf8 = singleEntryPayload(new byte[16],
                new byte[]{(byte) 0xc0, (byte) 0xaf},
                "user".getBytes(StandardCharsets.UTF_8),
                "pass".getBytes(StandardCharsets.UTF_8), new byte[0]);
        try {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(invalidUtf8));
        } finally {
            Arrays.fill(invalidUtf8, (byte) 0);
        }
    }

    @Test
    void rejectsDuplicateIdsAndIncoherentFieldLengths() {
        byte[] repeatedId = new byte[16];
        byte[] first = singleEntryPayload(repeatedId,
                "site".getBytes(StandardCharsets.UTF_8),
                "user".getBytes(StandardCharsets.UTF_8),
                "pass".getBytes(StandardCharsets.UTF_8), new byte[0]);
        byte[] secondEntry = Arrays.copyOfRange(first, Integer.BYTES, first.length);
        byte[] duplicatePayload = ByteBuffer.allocate(first.length + secondEntry.length)
                .putInt(2).put(first, Integer.BYTES, first.length - Integer.BYTES)
                .put(secondEntry).array();
        byte[] incoherentLength = ByteBuffer.allocate(Integer.BYTES + 16 + 2 * Long.BYTES + Integer.BYTES)
                .putInt(1).put(new byte[16]).putLong(1).putLong(2).putInt(-1).array();

        try {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(duplicatePayload));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(incoherentLength));
        } finally {
            Arrays.fill(first, (byte) 0);
            Arrays.fill(secondEntry, (byte) 0);
            Arrays.fill(duplicatePayload, (byte) 0);
            Arrays.fill(incoherentLength, (byte) 0);
        }
    }

    @Test
    void roundTripsDuplicateServiceUserPairsInOrderWithEmptyNotes() {
        byte[] firstId = new byte[16];
        byte[] secondId = new byte[16];
        firstId[0] = 1;
        secondId[0] = 2;
        char[] firstPassword = {'a'};
        char[] secondPassword = {'b'};
        byte[] payload = null;
        try (Vault source = new Vault()) {
            source.restoreEntry(firstId, Instant.ofEpochMilli(10), Instant.ofEpochMilli(10),
                    "same-service", "same-user", firstPassword, "");
            source.restoreEntry(secondId, Instant.ofEpochMilli(20), Instant.ofEpochMilli(20),
                    "same-service", "same-user", secondPassword, "");
            payload = codec.encode(source);
            try (Vault restored = parser.parse(payload)) {
                assertEquals(2, restored.list().size());
                assertArrayEquals(firstId, restored.list().get(0).id());
                assertArrayEquals(secondId, restored.list().get(1).id());
                assertEquals("", restored.list().get(0).notes());
                assertEquals("", restored.list().get(1).notes());
            }
        } finally {
            Arrays.fill(firstId, (byte) 0);
            Arrays.fill(secondId, (byte) 0);
            Arrays.fill(firstPassword, '\0');
            Arrays.fill(secondPassword, '\0');
            if (payload != null) {
                Arrays.fill(payload, (byte) 0);
            }
        }
    }

    @Test
    void parserRejectsEmptyRequiredTextFields() {
        byte[] id = new byte[16];
        byte[] valid = "value".getBytes(StandardCharsets.UTF_8);
        byte[] emptyService = singleEntryPayload(id, new byte[0], valid, valid, new byte[0]);
        byte[] emptyUsername = singleEntryPayload(id, valid, new byte[0], valid, new byte[0]);
        byte[] emptyPassword = singleEntryPayload(id, valid, valid, new byte[0], new byte[0]);
        try {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(emptyService));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(emptyUsername));
            assertThrows(IllegalArgumentException.class, () -> parser.parse(emptyPassword));
        } finally {
            Arrays.fill(valid, (byte) 0);
            Arrays.fill(emptyService, (byte) 0);
            Arrays.fill(emptyUsername, (byte) 0);
            Arrays.fill(emptyPassword, (byte) 0);
        }
    }

    private static byte[] singleEntryPayload(byte[] id, byte[] service, byte[] username,
                                             byte[] password, byte[] notes) {
        int length = Integer.BYTES + 16 + 2 * Long.BYTES
                + 4 * Integer.BYTES + service.length + username.length + password.length + notes.length;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.putInt(1).put(id).putLong(1).putLong(2);
        putField(buffer, service);
        putField(buffer, username);
        putField(buffer, password);
        putField(buffer, notes);
        return buffer.array();
    }

    private static void putField(ByteBuffer buffer, byte[] bytes) {
        buffer.putInt(bytes.length).put(bytes);
    }
}
