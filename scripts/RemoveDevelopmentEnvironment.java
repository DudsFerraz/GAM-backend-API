import java.io.Console;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

public class RemoveDevelopmentEnvironment {
    private static final String PROJECT_PREFIX = "gam-api-";
    private static final Pattern VALID_INSTANCE_ID =
            Pattern.compile("[a-z0-9][a-z0-9_-]*");

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length > 1) {
            fail("Usage: java scripts/RemoveDevelopmentEnvironment.java [instance-id]");
        }

        String instanceId = args.length == 1
                ? args[0]
                : firstNonBlank(
                        System.getenv("GAM_DEV_INSTANCE_ID"),
                        System.getenv("CODEX_THREAD_ID"),
                        "local"
                );

        if (!VALID_INSTANCE_ID.matcher(instanceId).matches()) {
            fail("Invalid instance identifier '" + instanceId + "'. Use lowercase letters, digits, "
                    + "hyphens, and underscores, beginning with a letter or digit.");
        }

        String projectName = PROJECT_PREFIX + instanceId;
        Console console = System.console();
        if (console == null) {
            fail("An interactive terminal is required to confirm deletion of " + projectName + ".");
        }

        String confirmation = console.readLine(
                "Delete Compose project %s and its database volume? [y/N] ",
                projectName
        );
        String normalizedConfirmation = confirmation == null
                ? ""
                : confirmation.strip().toLowerCase(Locale.ROOT);
        if (!"y".equals(normalizedConfirmation) && !"yes".equals(normalizedConfirmation)) {
            System.out.println("Development environment was not deleted.");
            return;
        }

        int exitCode = new ProcessBuilder(
                "docker",
                "compose",
                "--project-name",
                projectName,
                "down",
                "--volumes",
                "--remove-orphans"
        ).inheritIO().start().waitFor();

        if (exitCode != 0) {
            fail("Docker Compose cleanup failed with exit code " + exitCode + ".");
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        throw new IllegalStateException("A fallback instance identifier is required");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
