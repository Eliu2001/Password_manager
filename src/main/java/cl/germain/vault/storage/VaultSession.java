package cl.germain.vault.storage;

import cl.germain.vault.crypto.*;
import cl.germain.vault.model.Vault;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;

/** An exclusively locked, explicitly saved vault session. */
public final class VaultSession implements AutoCloseable {
    private final VaultPaths paths; private final VaultLock lock; private final DerivedKey key;
    private final Vault vault; private final CryptoService crypto; private byte[] fingerprint; private boolean closed;
    private final BeforeAtomicMove beforeAtomicMove;
    VaultSession(VaultPaths p,VaultLock l,DerivedKey k,Vault v,CryptoService c,byte[] f,BeforeAtomicMove hook){paths=p;lock=l;key=k;vault=v;crypto=c;fingerprint=f.clone();beforeAtomicMove=hook;}
    public Vault vault(){ensureOpen();return vault;}
    public synchronized void save() throws IOException {
        ensureOpen(); byte[] current=readCurrent(); byte[] digest=VaultStorage.hash(current); VaultStorage.clear(current);
        if(!Arrays.equals(digest,fingerprint)){VaultStorage.clear(digest);throw new IOException("Vault changed outside this session");}
        byte[] salt=null,prefix=null,plain=null,cipher=null,nonce=null,header=null,file=null,tempBytes=null;
        Path temp=null;
        try {
            byte[] oldBytes=readCurrent();
            try { VaultHeader old=VaultHeader.parse(Arrays.copyOf(oldBytes, VaultHeader.HEADER_LENGTH_BYTES)); salt=old.salt(); prefix=VaultHeader.createAadPrefix(old.parameters(),salt); }
            finally { VaultStorage.clear(oldBytes); }
            plain=new VaultPayloadCodec().encode(vault); EncryptedData encrypted=crypto.encrypt(key,plain,prefix); cipher=encrypted.ciphertextAndTag();nonce=encrypted.nonce();
            header=VaultHeader.assemble(prefix,nonce,cipher.length);file=VaultStorage.join(header,cipher);
            if(file.length>VaultStorage.MAX_FILE_BYTES) throw new IOException("Vault exceeds maximum size");
            temp=Files.createTempFile(paths.vault().getParent(), ".vault-", ".tmp"); PrivateFilePermissions.ensurePrivateFile(temp);
            try(FileChannel ch=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){VaultStorage.writeFully(ch,file);ch.force(true);}
            current=readCurrent(); digest=VaultStorage.hash(current);VaultStorage.clear(current);
            if(!Arrays.equals(digest,fingerprint)){VaultStorage.clear(digest);throw new IOException("Vault changed outside this session");} VaultStorage.clear(digest);
            beforeAtomicMove.run(temp);
            Files.move(temp,paths.vault(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); temp=null;
            VaultStorage.clear(fingerprint);fingerprint=VaultStorage.hash(file);
        } catch(AtomicMoveNotSupportedException e){throw new IOException("Atomic vault replacement is unsupported");}
        finally { if(temp!=null) try{Files.deleteIfExists(temp);}catch(IOException ignored){} VaultStorage.clear(salt,prefix,plain,cipher,nonce,header,file); }
    }
    private void ensureOpen(){if(closed)throw new IllegalStateException("Vault session is closed");}
    private byte[] readCurrent() throws IOException {
        long size=Files.size(paths.vault());
        if(size < VaultHeader.HEADER_LENGTH_BYTES + VaultHeader.MIN_CIPHERTEXT_LENGTH_BYTES || size > VaultStorage.MAX_FILE_BYTES)
            throw new IOException("Vault file size is invalid");
        return Files.readAllBytes(paths.vault());
    }
    @Override public synchronized void close() throws IOException { if(!closed){closed=true;vault.close();key.close();VaultStorage.clear(fingerprint);lock.close();} }
}
