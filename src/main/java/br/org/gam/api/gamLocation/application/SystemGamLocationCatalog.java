package br.org.gam.api.gamLocation.application;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SystemGamLocationCatalog {
    private static final List<Entry> ENTRIES = List.of(
            new Entry(
                    "DBSM",
                    Lifecycle.CURRENT,
                    "Dom Bosco São Mário",
                    "Av. Santa Rosa, 653 - Areião",
                    "Piracicaba",
                    "SP",
                    "13414-038",
                    "BR",
                    null,
                    null
            ),
            new Entry(
                    "DBA",
                    Lifecycle.CURRENT,
                    "Dom Bosco Assunção",
                    "Rua Boa Morte, 1835 - Centro",
                    "Piracicaba",
                    "SP",
                    "13400-140",
                    "BR",
                    null,
                    null
            ),
            new Entry(
                    "DBCA",
                    Lifecycle.CURRENT,
                    "Dom Bosco Cidade Alta",
                    "Rua Alfredo Guedes, 1199 - Bairro Alto",
                    "Piracicaba",
                    "SP",
                    "13419-080",
                    "BR",
                    null,
                    null
            ),
            new Entry(
                    "REMOTE",
                    Lifecycle.CURRENT,
                    "Remoto",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            )
    );

    static {
        validateRegistry();
    }

    private SystemGamLocationCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String code) {
        return ENTRIES.stream()
                .filter(entry -> entry.code().equals(code))
                .findFirst();
    }

    public static Optional<Entry> findCurrent(String code) {
        return find(code).filter(Entry::current);
    }

    private static void validateRegistry() {
        Set<String> codes = new HashSet<>();
        Set<DuplicateIdentity> identities = new HashSet<>();

        for (Entry entry : ENTRIES) {
            if (!entry.code().matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalStateException("Invalid system GamLocation code: " + entry.code());
            }
            if (!codes.add(entry.code())) {
                throw new IllegalStateException("Duplicate system GamLocation code: " + entry.code());
            }
            if (!identities.add(DuplicateIdentity.from(entry.normalizedValues()))) {
                throw new IllegalStateException(
                        "Duplicate system GamLocation identity in registry: " + entry.code()
                );
            }
        }
    }

    public enum Lifecycle {
        CURRENT,
        RETIRED
    }

    public record Entry(
            String code,
            Lifecycle lifecycle,
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        public boolean current() {
            return lifecycle == Lifecycle.CURRENT;
        }

        public GamLocationNormalizer.Values normalizedValues() {
            if ("REMOTE".equals(code)) {
                return GamLocationNormalizer.normalizeRemoteCatalogEntry(
                        name,
                        street,
                        city,
                        state,
                        postalCode,
                        countryCode,
                        latitude,
                        longitude
                );
            }
            return GamLocationNormalizer.normalize(
                    name,
                    street,
                    city,
                    state,
                    postalCode,
                    countryCode,
                    latitude,
                    longitude
            );
        }
    }

    private record DuplicateIdentity(
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode
    ) {
        private static DuplicateIdentity from(GamLocationNormalizer.Values values) {
            return new DuplicateIdentity(
                    values.identityName(),
                    values.identityStreet(),
                    values.identityCity(),
                    values.identityState(),
                    values.identityPostalCode(),
                    values.identityCountryCode()
            );
        }
    }
}
