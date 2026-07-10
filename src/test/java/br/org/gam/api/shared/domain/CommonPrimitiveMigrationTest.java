package br.org.gam.api.shared.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberDTO;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.oratoriano.application.OratorianoRDTO;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("Common Primitive Migration")
class CommonPrimitiveMigrationTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - member registration -> uses GamName and GamPhoneNumber")
        void memberRegistrationShouldUseGamNameAndGamPhoneNumber() {
            Account account = Account.register(MyEmail.of("member@example.com"), "hash", "Member Account");
            GamName name = new GamName("Ana", "Silva");
            GamPhoneNumber phoneNumber = GamPhoneNumber.fromString("+5519998877665");

            Member member = Member.register(account, name, LocalDate.of(1995, 1, 10), phoneNumber);

            assertThat(member.getName()).isSameAs(name);
            assertThat(member.getPhoneNumber()).isSameAs(phoneNumber);
        }

        @Test
        @DisplayName("EP - oratoriano registration -> uses GamName and GamPhoneNumber")
        void oratorianoRegistrationShouldUseGamNameAndGamPhoneNumber() {
            GamName name = new GamName("Ana", "Silva");
            GamPhoneNumber phoneNumber = GamPhoneNumber.fromString("+5519998877665");

            Oratoriano oratoriano = Oratoriano.register(name, LocalDate.of(2015, 1, 10), phoneNumber);

            assertThat(oratoriano.getName()).isSameAs(name);
            assertThat(oratoriano.getPhoneNumber()).isSameAs(phoneNumber);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.domain.CommonPrimitiveMigrationTest#nameFields")
        @DisplayName("COND - person name field type -> GamName")
        void personNameFieldShouldUseGamName(Class<?> ownerType, String fieldName) throws NoSuchFieldException {
            assertThat(ownerType.getDeclaredField(fieldName).getType()).isEqualTo(GamName.class);
        }

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.domain.CommonPrimitiveMigrationTest#phoneNumberFields")
        @DisplayName("COND - person phone field type -> GamPhoneNumber")
        void personPhoneFieldShouldUseGamPhoneNumber(Class<?> ownerType, String fieldName) throws NoSuchFieldException {
            assertThat(ownerType.getDeclaredField(fieldName).getType()).isEqualTo(GamPhoneNumber.class);
        }

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.domain.CommonPrimitiveMigrationTest#phoneNumberRecordComponents")
        @DisplayName("COND - API and use-case phone component type -> GamPhoneNumber")
        void apiAndUseCasePhoneComponentShouldUseGamPhoneNumber(Class<?> recordType, String componentName) {
            assertThat(recordComponentType(recordType, componentName)).isEqualTo(GamPhoneNumber.class);
        }

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.domain.CommonPrimitiveMigrationTest#retiredValueObjectClassNames")
        @DisplayName("COND - retired value object class -> absent")
        void retiredValueObjectClassShouldBeAbsent(String className) {
            assertThatThrownBy(() -> Class.forName(className))
                    .isInstanceOf(ClassNotFoundException.class);
        }

        @Test
        @DisplayName("COND - retired value object source files -> absent")
        void retiredValueObjectSourceFilesShouldBeAbsent() {
            assertThat(retiredSourceFiles())
                    .noneSatisfy(path -> assertThat(path).exists());
        }

        @Test
        @DisplayName("COND - production source -> no retired value object references")
        void productionSourceShouldNotReferenceRetiredValueObjects() throws IOException {
            List<Path> offenders;
            try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
                offenders = files
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(CommonPrimitiveMigrationTest::containsRetiredReference)
                        .toList();
            }

            assertThat(offenders).isEmpty();
        }
    }

    private static Stream<Arguments> nameFields() {
        return Stream.of(
                Arguments.of(Member.class, "name"),
                Arguments.of(MemberEntity.class, "name"),
                Arguments.of(Oratoriano.class, "name"),
                Arguments.of(OratorianoEntity.class, "name")
        );
    }

    private static Stream<Arguments> phoneNumberFields() {
        return Stream.of(
                Arguments.of(Member.class, "phoneNumber"),
                Arguments.of(MemberEntity.class, "phoneNumber"),
                Arguments.of(Oratoriano.class, "phoneNumber"),
                Arguments.of(OratorianoEntity.class, "phoneNumber")
        );
    }

    private static Stream<Arguments> phoneNumberRecordComponents() {
        return Stream.of(
                Arguments.of(RegisterMemberDTO.class, "phoneNumber"),
                Arguments.of(MemberRDTO.class, "phoneNumber"),
                Arguments.of(OratorianoRDTO.class, "phoneNumber")
        );
    }

    private static Stream<String> retiredValueObjectClassNames() {
        return Stream.of(
                "br.org.gam.api.shared.domain.Name",
                "br.org.gam.api.shared.phonenumber.MyPhoneNumber",
                "br.org.gam.api.shared.phonenumber.MyPhoneNumberConverter",
                "br.org.gam.api.shared.phonenumber.MyPhoneNumberConverterJPA"
        );
    }

    private static List<Path> retiredSourceFiles() {
        return List.of(
                Path.of("src", "main", "java", "br", "org", "gam", "api", "shared", "domain", "Name.java"),
                Path.of("src", "main", "java", "br", "org", "gam", "api", "shared", "phonenumber", "MyPhoneNumber.java"),
                Path.of("src", "main", "java", "br", "org", "gam", "api", "shared", "phonenumber", "MyPhoneNumberConverter.java"),
                Path.of("src", "main", "java", "br", "org", "gam", "api", "shared", "phonenumber", "MyPhoneNumberConverterJPA.java")
        );
    }

    private static Class<?> recordComponentType(Class<?> recordType, String componentName) {
        RecordComponent[] components = recordType.getRecordComponents();
        assertThat(components).isNotNull();

        return Stream.of(components)
                .filter(component -> component.getName().equals(componentName))
                .findFirst()
                .map(RecordComponent::getType)
                .orElseThrow();
    }

    private static boolean containsRetiredReference(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("br.org.gam.api.shared.domain.Name")
                    || source.contains("br.org.gam.api.shared.phonenumber.MyPhoneNumber")
                    || source.contains("MyPhoneNumberConverter")
                    || path.endsWith(Path.of("shared", "domain", "Name.java"))
                    || path.endsWith(Path.of("shared", "phonenumber", "MyPhoneNumber.java"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect source file " + path, e);
        }
    }
}
