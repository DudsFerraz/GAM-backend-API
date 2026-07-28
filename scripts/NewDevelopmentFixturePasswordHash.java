import java.io.Console;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Produces the delegated PBKDF2 format used by SecurityConfig without placing
 * the raw password in command arguments, shell history, logs, or a file.
 */
final class NewDevelopmentFixturePasswordHash {
    private static final int MINIMUM_CHARACTERS = 8;
    private static final int MAXIMUM_CHARACTERS = 128;
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 310_000;
    private static final int HASH_BITS = 256;

    private NewDevelopmentFixturePasswordHash() {
    }

    public static void main(String[] args) throws GeneralSecurityException {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this helper from an interactive terminal.");
            System.exit(2);
        }

        char[] password = console.readPassword(
                "Choose the local fixture password (%d-%d characters): ",
                MINIMUM_CHARACTERS,
                MAXIMUM_CHARACTERS
        );
        if (password == null) {
            System.err.println("No password was supplied.");
            System.exit(2);
        }

        try {
            int characterCount = Character.codePointCount(password, 0, password.length);
            if (characterCount < MINIMUM_CHARACTERS || characterCount > MAXIMUM_CHARACTERS) {
                System.err.printf(
                        "Password must contain between %d and %d characters.%n",
                        MINIMUM_CHARACTERS,
                        MAXIMUM_CHARACTERS
                );
                System.exit(2);
            }

            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            PBEKeySpec specification = new PBEKeySpec(
                    password,
                    salt,
                    ITERATIONS,
                    HASH_BITS
            );
            byte[] derived;
            try {
                derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(specification)
                        .getEncoded();
            } finally {
                specification.clearPassword();
            }

            byte[] encoded = new byte[salt.length + derived.length];
            System.arraycopy(salt, 0, encoded, 0, salt.length);
            System.arraycopy(derived, 0, encoded, salt.length, derived.length);
            try {
                System.out.println("{pbkdf2}" + HexFormat.of().formatHex(encoded));
            } finally {
                Arrays.fill(derived, (byte) 0);
                Arrays.fill(encoded, (byte) 0);
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
