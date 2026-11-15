package cl.germain.vault.storage;

import cl.germain.vault.crypto.Argon2Parameters;
import cl.germain.vault.crypto.CryptoService;
import cl.germain.vault.crypto.DerivedKey;
import cl.germain.vault.crypto.EncryptedData;
import cl.germain.vault.model.Vault;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Creates, opens and persists encrypted vault files. */
public final class VaultStorage {
    static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private final CryptoService crypto;
    private final VaultPayloadCodec codec = new VaultPayloadCodec();
    private final VaultPayloadParser parser = new VaultPayloadParser();
    private final BeforeAtomicMove beforeAtomicMove;
    private final VaultKeyDeriver keyDeriver;

    public VaultStorage() { this(new CryptoService()); }
    public VaultStorage(CryptoService crypto) { this(crypto, path -> { }); }
    VaultStorage(CryptoService crypto, BeforeAtomicMove beforeAtomicMove) {
        this(crypto, beforeAtomicMove, crypto::deriveKey);
    }
    VaultStorage(CryptoService crypto, BeforeAtomicMove beforeAtomicMove, VaultKeyDeriver keyDeriver) {
        this.crypto = java.util.Objects.requireNonNull(crypto);
        this.beforeAtomicMove = java.util.Objects.requireNonNull(beforeAtomicMove);
        this.keyDeriver = java.util.Objects.requireNonNull(keyDeriver);
    }

    public VaultSession create(Path path, char[] masterPassword) throws IOException {
        java.util.Objects.requireNonNull(masterPassword, "masterPassword");
        VaultPaths paths = VaultPaths.resolve(path);
        if (Files.exists(paths.vault(), LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException("Vault already exists");
        VaultLock lock = VaultLock.acquire(paths.lock());
        DerivedKey key = null;
        Vault vault = new Vault();
        byte[] salt = null, prefix = null, payload = null, ciphertext = null, nonce = null, header = null, file = null;
        boolean createdFile = false;
        try {
            salt = crypto.generateSalt();
            Argon2Parameters params = Argon2Parameters.defaults();
            key = keyDeriver.derive(masterPassword, salt, params);
            prefix = VaultHeader.createAadPrefix(params, salt, 2);
            payload = codec.encode(vault, 2);
            EncryptedData encrypted = crypto.encrypt(key, payload, prefix);
            ciphertext = encrypted.ciphertextAndTag(); nonce = encrypted.nonce();
            header = VaultHeader.assemble(prefix, nonce, ciphertext.length);
            file = join(header, ciphertext);
            Files.createFile(paths.vault());
            createdFile = true;
            PrivateFilePermissions.ensurePrivateFile(paths.vault());
            try (FileChannel channel = FileChannel.open(paths.vault(), StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) { writeFully(channel, file); channel.force(true); }
            return new VaultSession(paths, lock, key, vault, crypto, hash(file), beforeAtomicMove, 2);
        } catch (Throwable e) {
            if (key != null) key.close(); vault.close(); lock.close();
            if (createdFile) try { Files.deleteIfExists(paths.vault()); } catch (IOException ignored) { }
            if (e instanceof IOException io) throw io;
            if (e instanceof RuntimeException re) throw re;
            throw new IOException("Vault creation failed");
        } finally { clear(salt,prefix,payload,ciphertext,nonce,header,file); }
    }

    public VaultSession open(Path path, char[] masterPassword) throws IOException, cl.germain.vault.crypto.DecryptionException {
        java.util.Objects.requireNonNull(masterPassword, "masterPassword");
        VaultPaths paths = VaultPaths.resolve(path);
        VaultLock lock = VaultLock.acquire(paths.lock());
        DerivedKey key = null; Vault vault = null;
        byte[] file = null, headerBytes = null, ciphertext = null, plain = null, salt = null, nonce = null;
        try {
            PrivateFilePermissions.ensurePrivateFile(paths.vault());
            long size = Files.size(paths.vault());
            if (size < VaultHeader.HEADER_LENGTH_BYTES + VaultHeader.MIN_CIPHERTEXT_LENGTH_BYTES || size > MAX_FILE_BYTES)
                throw new IOException("Vault file size is invalid");
            file = Files.readAllBytes(paths.vault());
            headerBytes = Arrays.copyOf(file, VaultHeader.HEADER_LENGTH_BYTES);
            VaultHeader header = VaultHeader.parse(headerBytes); header.validateFileLength(file.length);
            salt = header.salt(); nonce = header.nonce();
            key = keyDeriver.derive(masterPassword, salt, header.parameters());
            ciphertext = Arrays.copyOfRange(file, VaultHeader.HEADER_LENGTH_BYTES, file.length);
            plain = crypto.decrypt(key, new EncryptedData(nonce, ciphertext), header.aad());
            vault = parser.parse(plain, header.version());
            VaultSession session = new VaultSession(paths, lock, key, vault, crypto, hash(file), beforeAtomicMove, header.version());
            key = null; vault = null; return session;
        } catch (Throwable e) {
            if (key != null) key.close(); if (vault != null) vault.close(); lock.close();
            if (e instanceof IOException io) throw io;
            if (e instanceof cl.germain.vault.crypto.DecryptionException decryption) throw decryption;
            if (e instanceof RuntimeException re) throw re;
            throw new IOException("Vault opening failed");
        } finally { clear(file,headerBytes,ciphertext,plain,salt,nonce); }
    }

    static byte[] join(byte[] a, byte[] b) { byte[] out = Arrays.copyOf(a, a.length+b.length); System.arraycopy(b,0,out,a.length,b.length); return out; }
    static byte[] hash(byte[] data) { try { return MessageDigest.getInstance("SHA-256").digest(data); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    static void writeFully(FileChannel channel, byte[] bytes) throws IOException { ByteBuffer b=ByteBuffer.wrap(bytes); while(b.hasRemaining()) channel.write(b); }
    static void clear(byte[]... arrays) { for(byte[] a:arrays) if(a!=null) Arrays.fill(a,(byte)0); }
}
