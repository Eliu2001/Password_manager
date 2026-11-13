package cl.germain.vault.crypto;

/** Validated Argon2id cost parameters. Memory is expressed in KiB. */
public record Argon2Parameters(int memoryKiB, int iterations, int parallelism) {
    public static final int MIN_MEMORY_KIB = 19 * 1024;
    public static final int MAX_MEMORY_KIB = 256 * 1024;
    public static final int MIN_ITERATIONS = 2;
    public static final int MAX_ITERATIONS = 10;
    public static final int MIN_PARALLELISM = 1;
    public static final int MAX_PARALLELISM = 4;

    public Argon2Parameters {
        if (memoryKiB < MIN_MEMORY_KIB || memoryKiB > MAX_MEMORY_KIB) {
            throw new IllegalArgumentException("Argon2 memory is outside the allowed range");
        }
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Argon2 iterations are outside the allowed range");
        }
        if (parallelism < MIN_PARALLELISM || parallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException("Argon2 parallelism is outside the allowed range");
        }
    }

    public static Argon2Parameters defaults() {
        return new Argon2Parameters(64 * 1024, 3, 1);
    }
}
