package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Fixture - production release artifacts")
class ProductionArtifactFixtureContractTest {

    private static final Path FIXTURE_ROOT = Path.of(
            "src", "test", "resources", "production", "fixtures"
    );
    private static final Path FRONTEND_ARCHIVE = FIXTURE_ROOT.resolve(
            "gam-frontend-v1.4.0.tar.gz"
    );
    private static final Path FRONTEND_CHECKSUM = FIXTURE_ROOT.resolve(
            "gam-frontend-v1.4.0.tar.gz.sha256"
    );
    private static final Path RELEASE_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest.yml"
    );
    private static final Path MISSING_DATABASE_POLICY_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest-missing-database-policy.yml"
    );
    private static final Path UNSAFE_PAIR_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest-unsafe-pair.yml"
    );
    private static final Path INVALID_DATABASE_POLICY_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest-invalid-database-policy.yml"
    );
    private static final Path INVALID_BACKEND_DIGEST_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest-invalid-backend-digest.yml"
    );
    private static final Path INVALID_FORMAT_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest-invalid-format.yml"
    );
    private static final Path INCOMPLETE_ROLLBACK_MANIFEST = FIXTURE_ROOT.resolve(
            "release-manifest-incomplete-rollback.yml"
    );
    private static final Path INVALID_CHECKSUM_SIDECAR = FIXTURE_ROOT.resolve(
            "gam-frontend-v1.4.0-invalid-extra-newline.sha256"
    );

    @Test
    @DisplayName("REQ-WEB-013 - fixture release contains a pinned digest and checksum")
    void fixtureManifestShouldUseContractValidImmutableArtifactIdentity() throws IOException {
        String manifest = Files.readString(RELEASE_MANIFEST);
        String image = scalar(manifest, "image");
        String frontendDigest = scalar(manifest, "sha256");

        assertThat(image)
                .matches("ghcr\\.io/[a-z0-9._/-]+@sha256:[0-9a-f]{64}");
        assertThat(frontendDigest).matches("[0-9a-f]{64}");
        assertThat(scalar(manifest, "repository")).matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
        assertThat(scalar(manifest, "tag")).isEqualTo("v1.4.0");
        assertThat(scalar(manifest, "artifact")).isEqualTo(FRONTEND_ARCHIVE.getFileName().toString());
        assertThat(manifest).contains("release_commit:");
        assertThat(scalar(manifest, "published")).isEqualTo("true");
        assertThat(scalar(manifest, "prerelease")).isEqualTo("false");
        assertThat(scalar(manifest, "immutable")).isEqualTo("true");
    }

    @Test
    @DisplayName("REQ-WEB-013 - fixture sidecar, manifest, and computed archive digest agree")
    void fixtureChecksumsShouldAgree() throws IOException, NoSuchAlgorithmException {
        String sidecar = Files.readString(FRONTEND_CHECKSUM);
        String archiveName = FRONTEND_ARCHIVE.getFileName().toString();
        assertThat(sidecar).matches("[0-9a-f]{64}  " + Pattern.quote(archiveName) + "\\R");

        String sidecarDigest = sidecar.substring(0, 64);
        String computedDigest = hexDigest(FRONTEND_ARCHIVE);
        String manifestDigest = scalar(Files.readString(RELEASE_MANIFEST), "sha256");

        assertThat(computedDigest).isEqualTo(sidecarDigest).isEqualTo(manifestDigest);
    }

    @Test
    @DisplayName("REQ-OPS-008/WEB-013 - manifest validators accept the valid fixture and reject unsafe policy fixtures")
    void manifestFixtureValidatorShouldExerciseValidAndNegativePolicyCases() throws IOException {
        assertThat(isValidReleaseManifest(Files.readString(RELEASE_MANIFEST))).isTrue();
        assertThat(isValidReleaseManifest(Files.readString(MISSING_DATABASE_POLICY_MANIFEST))).isFalse();
        assertThat(isValidReleaseManifest(Files.readString(UNSAFE_PAIR_MANIFEST))).isFalse();
        assertThat(isValidReleaseManifest(Files.readString(INVALID_DATABASE_POLICY_MANIFEST))).isFalse();
        assertThat(isValidReleaseManifest(Files.readString(INVALID_BACKEND_DIGEST_MANIFEST))).isFalse();
        assertThat(isValidReleaseManifest(Files.readString(INVALID_FORMAT_MANIFEST))).isFalse();
        assertThat(isValidReleaseManifest(Files.readString(INCOMPLETE_ROLLBACK_MANIFEST))).isFalse();
    }

    @Test
    @DisplayName("REQ-WEB-013 - checksum validator accepts one exact line and rejects extra whitespace")
    void checksumFixtureValidatorShouldExerciseValidAndNegativeSidecars() throws IOException {
        String manifest = Files.readString(RELEASE_MANIFEST);
        String digest = scalar(manifest, "sha256");
        String artifact = scalar(manifest, "artifact");

        assertThat(isExactSha256sumLine(Files.readString(FRONTEND_CHECKSUM), digest, artifact)).isTrue();
        assertThat(isExactSha256sumLine(Files.readString(INVALID_CHECKSUM_SIDECAR), digest, artifact)).isFalse();
    }

    @Test
    @DisplayName("REQ-WEB-013 - fixture archive is a safe root-level static release")
    void fixtureArchiveShouldHaveSafeEntriesAndRootIndex() throws IOException {
        List<TarEntry> entries = readTarGz(FRONTEND_ARCHIVE);
        List<String> names = entries.stream().map(TarEntry::name).toList();

        assertThat(names).contains("index.html");
        assertThat(names).anyMatch(name -> name.startsWith("assets/") && name.endsWith(".js"));
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.name()).doesNotStartWith("/");
            assertThat(entry.name()).doesNotContain(".." + "/");
            assertThat(entry.type()).isIn((byte) 0, (byte) '0', (byte) '5');
        });
    }

    @Test
    @DisplayName("REQ-WEB-013 - archive validator rejects traversal and link entries")
    void archiveFixtureValidatorShouldRejectUnsafeEntries() throws IOException {
        byte[] traversalArchive = tarGz(List.of(
                new TarEntry("index.html", (byte) '0'),
                new TarEntry("../escape.txt", (byte) '0')
        ));
        byte[] linkArchive = tarGz(List.of(
                new TarEntry("index.html", (byte) '0'),
                new TarEntry("assets/current.js", (byte) '2')
        ));

        assertThat(isSafeArchive(readTarGz(new ByteArrayInputStream(traversalArchive)))).isFalse();
        assertThat(isSafeArchive(readTarGz(new ByteArrayInputStream(linkArchive)))).isFalse();
    }

    private static boolean isValidReleaseManifest(String yaml) {
        String pair = scalarOptional(yaml, "pair");
        String backendImage = scalarOptional(yaml, "image");
        String backendDigest = scalarOptional(yaml, "digest");
        return "1".equals(scalarOptional(yaml, "format_version"))
                && backendImage != null
                && backendDigest != null
                && backendImage.endsWith("@" + backendDigest)
                && pair != null
                && pair.matches("[a-z0-9][a-z0-9._-]{0,127}")
                && isStrictBoolean(yaml, "database_change")
                && isStrictBoolean(yaml, "database_rollback_compatible")
                && isStrictBooleanValue(yaml, "published", "true")
                && isStrictBooleanValue(yaml, "prerelease", "false")
                && isStrictBooleanValue(yaml, "immutable", "true")
                && atLeast(yaml, "minimum_days", 14)
                && atLeast(yaml, "minimum_verified_releases", 2)
                && isStrictBooleanValue(yaml, "retain_backend_digest", "true")
                && isStrictBooleanValue(yaml, "retain_frontend_archive", "true")
                && isStrictBooleanValue(yaml, "retain_frontend_checksum", "true")
                && isStrictBooleanValue(yaml, "retain_manifest", "true")
                && isStrictBooleanValue(yaml, "retain_fingerprinted_assets", "true")
                && isStrictBooleanValue(yaml, "retained", "true");
    }

    private static boolean isStrictBoolean(String yaml, String key) {
        return "true".equals(rawScalarOptional(yaml, key))
                || "false".equals(rawScalarOptional(yaml, key));
    }

    private static boolean isStrictBooleanValue(String yaml, String key, String expected) {
        return expected.equals(rawScalarOptional(yaml, key));
    }

    private static boolean atLeast(String yaml, String key, int minimum) {
        String value = scalarOptional(yaml, key);
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= minimum;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isExactSha256sumLine(String sidecar, String digest, String artifact) {
        String normalized = sidecar.replace("\r\n", "\n");
        return normalized.equals(digest + "  " + artifact + "\n")
                && normalized.matches("[0-9a-f]{64}  " + Pattern.quote(artifact) + "\\n");
    }

    private static boolean isSafeArchive(List<TarEntry> entries) {
        return entries.stream().map(TarEntry::name).anyMatch("index.html"::equals)
                && entries.stream().allMatch(entry ->
                !entry.name().startsWith("/")
                        && !entry.name().contains("../")
                        && (entry.type() == 0 || entry.type() == '0' || entry.type() == '5'));
    }

    private static String scalar(String yaml, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + ":\\s*([^#\\r\\n]+)").matcher(yaml);
        assertThat(matcher.find()).as("fixture manifest key: %s", key).isTrue();
        return matcher.group(1).trim();
    }

    private static String scalarOptional(String yaml, String key) {
        String value = rawScalarOptional(yaml, key);
        if (value == null) {
            return null;
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String rawScalarOptional(String yaml, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + ":\\s*([^#\\r\\n]+)").matcher(yaml);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private static String hexDigest(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static List<TarEntry> readTarGz(Path path) throws IOException {
        List<TarEntry> entries = new ArrayList<>();
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            entries.addAll(readTar(input));
        }
        return entries;
    }

    private static List<TarEntry> readTarGz(InputStream source) throws IOException {
        try (InputStream input = new GZIPInputStream(source)) {
            return readTar(input);
        }
    }

    private static List<TarEntry> readTar(InputStream input) throws IOException {
        List<TarEntry> entries = new ArrayList<>();
        while (true) {
            byte[] header = input.readNBytes(512);
            assertThat(header).hasSize(512);
            if (isZeroBlock(header)) {
                break;
            }
            String name = tarString(header, 0, 100);
            byte type = header[156];
            long size = tarOctal(header, 124, 12);
            entries.add(new TarEntry(name, type));
            drain(input, size);
            long padding = (512 - (size % 512)) % 512;
            drain(input, padding);
        }
        return entries;
    }

    private static byte[] tarGz(List<TarEntry> entries) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (TarEntry entry : entries) {
            byte[] header = new byte[512];
            writeAscii(header, 0, 100, entry.name());
            writeOctal(header, 100, 8, 0644);
            writeOctal(header, 108, 8, 0);
            writeOctal(header, 116, 8, 0);
            writeOctal(header, 124, 12, 0);
            writeOctal(header, 136, 12, 0);
            header[156] = entry.type();
            writeAscii(header, 257, 6, "ustar\0");
            writeAscii(header, 263, 2, "00");
            Arrays.fill(header, 148, 156, (byte) ' ');
            long checksum = 0;
            for (byte value : header) {
                checksum += Byte.toUnsignedInt(value);
            }
            writeAscii(header, 148, 8, String.format("%06o\0 ", checksum));
            tar.write(header);
        }
        tar.write(new byte[1024]);

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(tar.toByteArray());
        }
        return compressed.toByteArray();
    }

    private static void writeAscii(byte[] target, int offset, int length, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, Math.min(encoded.length, length));
    }

    private static void writeOctal(byte[] target, int offset, int length, long value) {
        writeAscii(target, offset, length, String.format("%0" + (length - 1) + "o", value));
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String tarString(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static long tarOctal(byte[] header, int offset, int length) {
        String value = tarString(header, offset, length).trim();
        return value.isEmpty() ? 0 : Long.parseLong(value, 8);
    }

    private static void drain(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (input.read() == -1) {
                throw new IOException("unexpected end of fixture archive");
            }
            remaining--;
        }
    }

    private record TarEntry(String name, byte type) {
    }
}
