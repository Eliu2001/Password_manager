package cl.germain.vault.cli;

import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import cl.germain.vault.generator.PasswordGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliApplicationTest {
    private static final Object END_OF_INPUT = new Object();
    private static final Object INTERRUPTED = new Object();
    private static final Object IO_FAILURE = new Object();
    @TempDir
    Path directory;

    @Test
    void helpAndInvalidArgumentsDoNotRequireConsoleOrTouchStorage() {
        ScriptedConsole noConsole = new ScriptedConsole(false);
        assertEquals(0, app(noConsole).run("--help"));
        assertTrue(noConsole.outputs.getFirst().contains("create, add, list, show, delete, generate"));
        assertTrue(noConsole.outputs.getFirst().contains("generate [--length <n>]"));
        assertTrue(noConsole.outputs.getFirst().contains("predeterminado: 20; rango: 12–128"));
        assertEquals(0, noConsole.readCalls);

        ScriptedConsole available = new ScriptedConsole(true);
        assertEquals(1, app(available).run("list", "--vault"));
        assertTrue(available.errors.getFirst().contains("Argumentos inválidos"));
        assertEquals(0, available.availabilityChecks);
    }

    @Test
    void generateUsesInteractiveGateAndPrivateChannelWithoutVaultAccess() {
        ScriptedConsole console = new ScriptedConsole(true);
        assertEquals(0, app(console).run("generate"));
        assertEquals(1, console.secretWrites);
        assertEquals(20, console.revealedSecret.length);
        assertCleared(console.lastSecretArgument);
        assertEquals(List.of("Contraseña generada."), console.outputs);
        assertTrue(console.errors.isEmpty());
        Arrays.fill(console.revealedSecret, '\0');

        ScriptedConsole noConsole = new ScriptedConsole(false);
        AtomicInteger dispatches = new AtomicInteger();
        CliApplication gated = new CliApplication(new CliArgumentParser(), noConsole, command -> {
            dispatches.incrementAndGet();
            return CliExitStatus.SUCCESS;
        });
        assertEquals(1, gated.run("generate", "--length", "12"));
        assertEquals(0, dispatches.get());
        assertEquals(0, noConsole.readCalls);

        ScriptedConsole invalid = new ScriptedConsole(true);
        assertEquals(1, app(invalid).run("generate", "--length", "11"));
        assertEquals(0, invalid.availabilityChecks);
    }

    @Test
    void generateHonorsExplicitLengthAndClearsThePrivateChannelBuffer() {
        ScriptedConsole console = new ScriptedConsole(true);
        CliApplication application = new CliApplication(new CliArgumentParser(), console,
                new VaultCliCommands(console, new VaultStorage(), new PasswordGenerator()));

        assertEquals(0, application.run("generate", "--length", "12"));
        assertEquals(1, console.secretWrites);
        assertEquals(12, console.revealedSecret.length);
        assertCleared(console.lastSecretArgument);
        Arrays.fill(console.revealedSecret, '\0');
    }

    @Test
    void generateFailuresAreGenericAndClearBuffersWithoutFallback() {
        ScriptedConsole generatorFailureConsole = new ScriptedConsole(true);
        CliApplication generatorFailure = new CliApplication(new CliArgumentParser(), generatorFailureConsole,
                new VaultCliCommands(generatorFailureConsole, new VaultStorage(), length -> {
                    throw new IllegalStateException("synthetic-generated-value");
                }));
        assertEquals(1, generatorFailure.run("generate"));
        assertEquals(List.of("No se pudo completar la operación."), generatorFailureConsole.errors);
        assertTrue(generatorFailureConsole.outputs.isEmpty());
        assertEquals(0, generatorFailureConsole.secretWrites);

        ScriptedConsole writeFailureConsole = new ScriptedConsole(true);
        writeFailureConsole.failSecretWrite = true;
        CliApplication writeFailure = new CliApplication(new CliArgumentParser(), writeFailureConsole,
                new VaultCliCommands(writeFailureConsole, new VaultStorage(), length -> {
                    char[] generated = new char[length];
                    Arrays.fill(generated, 'x');
                    return generated;
                }));
        assertEquals(1, writeFailure.run("generate", "--length", "12"));
        assertEquals(List.of("No se pudo completar la operación."), writeFailureConsole.errors);
        assertTrue(writeFailureConsole.outputs.isEmpty());
        assertEquals(1, writeFailureConsole.secretWrites);
        assertCleared(writeFailureConsole.lastSecretArgument);
        assertTrue(writeFailureConsole.revealedSecret == null);
    }

    @Test
    void applicationMapsIoAndInterruptFailuresToSafeSpanishErrors() {
        ScriptedConsole console = new ScriptedConsole(true);
        CliApplication ioFailure = new CliApplication(new CliArgumentParser(), console, command -> {
            throw new IOException("synthetic-sensitive-cause");
        });
        assertEquals(1, ioFailure.run("list", "--vault", "vault"));
        assertEquals("No se pudo completar la operación.", console.errors.getFirst());

        ScriptedConsole interruptedConsole = new ScriptedConsole(true);
        CliApplication interrupted = new CliApplication(new CliArgumentParser(), interruptedConsole, command -> {
            throw new InterruptedException("synthetic-sensitive-cause");
        });
        try {
            assertEquals(1, interrupted.run("list", "--vault", "vault"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals("Operación cancelada o lectura interrumpida.", interruptedConsole.errors.getFirst());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void createRequiresMatchingPasswordsAndClearsBothArrays() throws Exception {
        Path vaultPath = directory.resolve("created.pvlt");
        char[] first = "synthetic-master".toCharArray();
        char[] second = "synthetic-master".toCharArray();
        ScriptedConsole matching = new ScriptedConsole(true, first, second);
        assertEquals(0, app(matching).run("create", "--vault", vaultPath.toString()));
        assertTrue(Files.exists(vaultPath));
        assertCleared(first, second);

        byte[] existingVault = Files.readAllBytes(vaultPath);
        char[] existingOne = "synthetic-master".toCharArray();
        char[] existingTwo = "synthetic-master".toCharArray();
        assertEquals(1, app(new ScriptedConsole(true, existingOne, existingTwo))
                .run("create", "--vault", vaultPath.toString()));
        assertArrayEquals(existingVault, Files.readAllBytes(vaultPath));
        assertCleared(existingOne, existingTwo);

        Path rejectedPath = directory.resolve("not-created.pvlt");
        char[] mismatchOne = "synthetic-one".toCharArray();
        char[] mismatchTwo = "synthetic-two".toCharArray();
        ScriptedConsole mismatch = new ScriptedConsole(true, mismatchOne, mismatchTwo);
        assertEquals(1, app(mismatch).run("create", "--vault", rejectedPath.toString()));
        assertFalse(Files.exists(rejectedPath));
        assertCleared(mismatchOne, mismatchTwo);

        Path cancelledPath = directory.resolve("cancelled.pvlt");
        char[] cancelledPassword = "synthetic-cancelled".toCharArray();
        ScriptedConsole cancelled = new ScriptedConsole(true, cancelledPassword, END_OF_INPUT);
        assertEquals(1, app(cancelled).run("create", "--vault", cancelledPath.toString()));
        assertFalse(Files.exists(cancelledPath));
        assertCleared(cancelledPassword);

        char[] interruptedPassword = "synthetic-interrupted".toCharArray();
        ScriptedConsole interrupted = new ScriptedConsole(true, interruptedPassword, INTERRUPTED);
        try {
            assertEquals(1, app(interrupted).run("create", "--vault", cancelledPath.toString()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertFalse(Files.exists(cancelledPath));
        assertCleared(interruptedPassword);
    }

    @Test
    void addListShowAndDeleteRespectChannelsPersistenceAndSessionLifecycle() throws Exception {
        Path vaultPath = createVault("roundtrip.pvlt");
        VaultStorage storage = new VaultStorage();

        char[] masterForEmptyList = "synthetic-master".toCharArray();
        ScriptedConsole emptyList = new ScriptedConsole(true, masterForEmptyList);
        assertEquals(0, app(emptyList, storage).run("list", "--vault", vaultPath.toString()));
        assertTrue(emptyList.outputs.getFirst().contains("La bóveda está vacía"));
        assertCleared(masterForEmptyList);

        char[] masterForAdd = "synthetic-master".toCharArray();
        char[] entryPassword = "synthetic-entry-password".toCharArray();
        ScriptedConsole addConsole = new ScriptedConsole(true, masterForAdd,
                " service with spaces ", "user-ñ", entryPassword, "private note");
        assertEquals(0, app(addConsole, storage).run("add", "--vault", vaultPath.toString()));
        assertCleared(masterForAdd, entryPassword);
        String addOutput = String.join("\n", addConsole.outputs);
        String id = addOutput.substring(addOutput.lastIndexOf("ID: ") + 4);
        assertEquals(32, id.length());
        assertTrue(id.matches("[0-9a-f]{32}"));

        char[] masterForSecondAdd = "synthetic-master".toCharArray();
        char[] secondEntryPassword = "synthetic-second-password".toCharArray();
        ScriptedConsole secondAdd = new ScriptedConsole(true, masterForSecondAdd,
                "second service", "second-user", secondEntryPassword, "");
        assertEquals(0, app(secondAdd, storage).run("add", "--vault", vaultPath.toString()));
        String secondOutput = String.join("\n", secondAdd.outputs);
        String secondId = secondOutput.substring(secondOutput.lastIndexOf("ID: ") + 4);
        assertTrue(secondId.matches("[0-9a-f]{32}"));
        assertCleared(masterForSecondAdd, secondEntryPassword);

        char[] masterForList = "synthetic-master".toCharArray();
        ScriptedConsole listConsole = new ScriptedConsole(true, masterForList);
        assertEquals(0, app(listConsole, storage).run("list", "--vault", vaultPath.toString()));
        String listed = String.join("\n", listConsole.outputs);
        assertTrue(listed.contains(" service with spaces  | user-ñ"));
        assertTrue(listed.contains("second service | second-user"));
        assertTrue(listed.indexOf(id) < listed.indexOf(secondId));
        assertFalse(listed.contains("synthetic-entry-password"));
        assertFalse(listed.contains("synthetic-second-password"));
        assertFalse(listed.contains("private note"));
        assertCleared(masterForList);

        char[] masterForShow = "synthetic-master".toCharArray();
        ScriptedConsole showConsole = new ScriptedConsole(true, masterForShow, "sí");
        assertEquals(0, app(showConsole, storage).run("show", "--vault", vaultPath.toString(), "--id", id));
        assertArrayEquals("synthetic-entry-password".toCharArray(), showConsole.revealedSecret);
        assertFalse(String.join("\n", showConsole.outputs).contains("synthetic-entry-password"));
        assertFalse(String.join("\n", showConsole.errors).contains("synthetic-entry-password"));
        assertCleared(masterForShow);
        assertCleared(showConsole.returnedPasswords.toArray(char[][]::new));

        char[] masterForShowCancel = "synthetic-master".toCharArray();
        ScriptedConsole showCancel = new ScriptedConsole(true, masterForShowCancel, "sí ");
        assertEquals(0, app(showCancel, storage).run("show", "--vault", vaultPath.toString(), "--id", id));
        assertTrue(showCancel.revealedSecret == null);
        assertFalse(String.join("\n", showCancel.outputs).contains("synthetic-entry-password"));
        assertCleared(masterForShowCancel);
        assertCleared(showCancel.returnedPasswords.toArray(char[][]::new));

        char[] masterForShowEmpty = "synthetic-master".toCharArray();
        ScriptedConsole showEmpty = new ScriptedConsole(true, masterForShowEmpty, "");
        assertEquals(0, app(showEmpty, storage).run("show", "--vault", vaultPath.toString(), "--id", id));
        assertTrue(showEmpty.revealedSecret == null);
        assertCleared(masterForShowEmpty);

        byte[] fileBeforeCancel = Files.readAllBytes(vaultPath);
        Object[] cancelResponses = {"si", "", END_OF_INPUT, INTERRUPTED};
        for (Object response : cancelResponses) {
            char[] masterForCancel = "synthetic-master".toCharArray();
            ScriptedConsole cancelDelete = new ScriptedConsole(true, masterForCancel, response);
            try {
                int expectedStatus = response == INTERRUPTED ? 1 : 0;
                assertEquals(expectedStatus,
                        app(cancelDelete, storage).run("delete", "--vault", vaultPath.toString(), "--id", id));
            } finally {
                Thread.interrupted();
            }
            assertArrayEquals(fileBeforeCancel, Files.readAllBytes(vaultPath));
            assertCleared(masterForCancel);
        }

        char[] masterForDelete = "synthetic-master".toCharArray();
        ScriptedConsole deleteConsole = new ScriptedConsole(true, masterForDelete, "sí");
        assertEquals(0, app(deleteConsole, storage).run("delete", "--vault", vaultPath.toString(), "--id", id));
        assertCleared(masterForDelete);

        char[] masterForVerify = "synthetic-master".toCharArray();
        try (VaultSession reopened = storage.open(vaultPath, masterForVerify)) {
            assertEquals(1, reopened.vault().list().size());
            assertEquals("second service", reopened.vault().list().getFirst().service());
            assertEquals("", reopened.vault().list().getFirst().notes());
        } finally {
            Arrays.fill(masterForVerify, '\0');
        }
    }

    @Test
    void noConsoleAndAuthenticationFailureDoNotMutateVaultOrLeaveLockHeld() throws Exception {
        Path vaultPath = createVault("failure.pvlt");
        byte[] before = Files.readAllBytes(vaultPath);
        String id = "00112233445566778899aabbccddeeff";
        String[][] commands = {
                {"create", "--vault", vaultPath.toString()},
                {"add", "--vault", vaultPath.toString()},
                {"list", "--vault", vaultPath.toString()},
                {"show", "--vault", vaultPath.toString(), "--id", id},
                {"delete", "--vault", vaultPath.toString(), "--id", id}
        };
        for (String[] arguments : commands) {
            ScriptedConsole noConsole = new ScriptedConsole(false);
            assertEquals(1, app(noConsole).run(arguments));
            assertEquals(0, noConsole.readCalls);
            assertArrayEquals(before, Files.readAllBytes(vaultPath));
        }
        Path absentCreateTarget = directory.resolve("must-not-be-created.pvlt");
        ScriptedConsole noConsoleForCreate = new ScriptedConsole(false);
        assertEquals(1, app(noConsoleForCreate).run("create", "--vault", absentCreateTarget.toString()));
        assertFalse(Files.exists(absentCreateTarget));
        assertEquals(0, noConsoleForCreate.readCalls);

        char[] wrongPassword = "synthetic-wrong".toCharArray();
        ScriptedConsole wrongAuth = new ScriptedConsole(true, wrongPassword);
        assertEquals(1, app(wrongAuth).run("list", "--vault", vaultPath.toString()));
        assertTrue(wrongAuth.errors.getFirst().contains("No se pudo completar"));
        assertFalse(wrongAuth.errors.getFirst().contains("synthetic-wrong"));
        assertCleared(wrongPassword);

        char[] correctPassword = "synthetic-master".toCharArray();
        try (VaultSession ignored = new VaultStorage().open(vaultPath, correctPassword)) {
            assertTrue(true, "a failed open must release its lock");
        } finally {
            Arrays.fill(correctPassword, '\0');
        }
    }

    @Test
    void invalidAddAndUnknownIdsDoNotPersistOrLeakSessionLocks() throws Exception {
        Path vaultPath = createVault("invalid-add.pvlt");
        byte[] before = Files.readAllBytes(vaultPath);

        char[] master = "synthetic-master".toCharArray();
        char[] password = "synthetic-entry".toCharArray();
        ScriptedConsole invalidAdd = new ScriptedConsole(true, master, "", "user", password, "notes");
        assertEquals(1, app(invalidAdd).run("add", "--vault", vaultPath.toString()));
        assertArrayEquals(before, Files.readAllBytes(vaultPath));
        assertCleared(master, password);

        char[] showMaster = "synthetic-master".toCharArray();
        ScriptedConsole unknownShow = new ScriptedConsole(true, showMaster);
        assertEquals(1, app(unknownShow).run("show", "--vault", vaultPath.toString(), "--id",
                "00112233445566778899aabbccddeeff"));
        assertArrayEquals(before, Files.readAllBytes(vaultPath));
        assertCleared(showMaster);

        char[] deleteMaster = "synthetic-master".toCharArray();
        ScriptedConsole unknownDelete = new ScriptedConsole(true, deleteMaster);
        assertEquals(1, app(unknownDelete).run("delete", "--vault", vaultPath.toString(), "--id",
                "00112233445566778899aabbccddeeff"));
        assertArrayEquals(before, Files.readAllBytes(vaultPath));
        assertCleared(deleteMaster);
    }

    @Test
    void readAndSaveFailuresAreGenericPreserveExternalFileAndCloseSession() throws Exception {
        Path vaultPath = createVault("io-failure.pvlt");
        VaultStorage storage = new VaultStorage();

        char[] masterForReadFailure = "synthetic-master".toCharArray();
        ScriptedConsole readFailure = new ScriptedConsole(true, masterForReadFailure, IO_FAILURE);
        assertEquals(1, app(readFailure, storage).run("add", "--vault", vaultPath.toString()));
        assertEquals("No se pudo completar la operación.", readFailure.errors.getFirst());
        assertFalse(readFailure.errors.getFirst().contains("synthetic"));
        assertCleared(masterForReadFailure);
        assertCanOpen(storage, vaultPath);

        byte[] externalContents = Files.readAllBytes(vaultPath);
        externalContents[externalContents.length - 1] ^= 1;
        char[] masterForSaveFailure = "synthetic-master".toCharArray();
        char[] entryPassword = "synthetic-entry".toCharArray();
        ScriptedConsole saveFailure = new ScriptedConsole(true, masterForSaveFailure,
                "service", "user", entryPassword, "notes");
        saveFailure.beforeNextLine = () -> {
            try {
                Files.write(vaultPath, externalContents);
            } catch (IOException exception) {
                throw new RuntimeException("synthetic-sensitive-io", exception);
            }
        };

        assertEquals(1, app(saveFailure, storage).run("add", "--vault", vaultPath.toString()));
        assertEquals("No se pudo completar la operación.", saveFailure.errors.getFirst());
        assertFalse(saveFailure.errors.getFirst().contains("synthetic"));
        assertTrue(saveFailure.outputs.isEmpty());
        assertArrayEquals(externalContents, Files.readAllBytes(vaultPath));
        assertCleared(masterForSaveFailure, entryPassword);
        Arrays.fill(externalContents, (byte) 0);
    }

    private static void assertCanOpen(VaultStorage storage, Path path) throws Exception {
        char[] password = "synthetic-master".toCharArray();
        try (VaultSession ignored = storage.open(path, password)) {
            assertTrue(true, "failed command must release the session lock");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private Path createVault(String name) throws Exception {
        Path path = directory.resolve(name);
        char[] password = "synthetic-master".toCharArray();
        try (VaultSession ignored = new VaultStorage().create(path, password)) {
            return path;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private CliApplication app(ScriptedConsole console) {
        return app(console, new VaultStorage());
    }

    private CliApplication app(ScriptedConsole console, VaultStorage storage) {
        return new CliApplication(new CliArgumentParser(), console, new VaultCliCommands(console, storage));
    }

    private static void assertCleared(char[]... arrays) {
        for (char[] array : arrays) {
            assertTrue(Arrays.stream(toIntArray(array)).allMatch(value -> value == 0));
        }
    }

    private static int[] toIntArray(char[] value) {
        int[] result = new int[value.length];
        for (int index = 0; index < value.length; index++) result[index] = value[index];
        return result;
    }

    private static final class ScriptedConsole implements CliConsole {
        private final boolean available;
        private final Queue<Object> inputs = new LinkedList<>();
        private final List<String> outputs = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<char[]> returnedPasswords = new ArrayList<>();
        private int availabilityChecks;
        private int readCalls;
        private int secretWrites;
        private char[] revealedSecret;
        private char[] lastSecretArgument;
        private boolean failSecretWrite;
        private Runnable beforeNextLine = () -> { };

        private ScriptedConsole(boolean available, Object... inputs) {
            this.available = available;
            this.inputs.addAll(Arrays.asList(inputs));
        }

        @Override
        public boolean isAvailable() {
            availabilityChecks++;
            return available;
        }

        @Override
        public String readLine(String prompt) throws IOException, InterruptedException {
            readCalls++;
            beforeNextLine.run();
            beforeNextLine = () -> { };
            Object value = inputs.remove();
            if (value == INTERRUPTED) throw new InterruptedException();
            if (value == IO_FAILURE) throw new IOException("synthetic-sensitive-io");
            if (value == END_OF_INPUT) return null;
            if (!(value instanceof String result)) throw new AssertionError("Expected visible input");
            return result;
        }

        @Override
        public char[] readPassword(String prompt) throws IOException, InterruptedException {
            readCalls++;
            Object value = inputs.remove();
            if (value == INTERRUPTED) throw new InterruptedException();
            if (value == IO_FAILURE) throw new IOException("synthetic-sensitive-io");
            if (value == END_OF_INPUT) return null;
            if (!(value instanceof char[] result)) throw new AssertionError("Expected password input");
            returnedPasswords.add(result);
            return result;
        }

        @Override
        public void printOutput(String message) {
            outputs.add(message);
        }

        @Override
        public void printError(String message) {
            errors.add(message);
        }

        @Override
        public void printSecret(char[] secret) throws IOException {
            secretWrites++;
            lastSecretArgument = secret;
            if (failSecretWrite) {
                throw new IOException("synthetic-console-write-error");
            }
            revealedSecret = secret.clone();
        }
    }
}
