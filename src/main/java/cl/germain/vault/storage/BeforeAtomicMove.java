package cl.germain.vault.storage;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
interface BeforeAtomicMove {
  void run(Path temporaryFile) throws IOException;
}
