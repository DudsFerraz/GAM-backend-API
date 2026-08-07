package br.org.gam.api.member.domain;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structural - Member information rich aggregate ownership")
class MemberInformationAggregateArchitectureTest {

    @Test
    @DisplayName("REQ-MEMBER-INFO-001 and ADR-0027 - Member owns every current-information component")
    void memberShouldOwnAllCurrentInformationComponents() {
        Map<String, Class<?>> requiredState = Map.of(
                "gamEntryDate", LocalDate.class,
                "residentialCity", String.class,
                "contactEmail", GamEmail.class,
                "dietaryRestriction", DietaryRestriction.class,
                "experiences", Map.class,
                "sacraments", Map.class,
                "contributionAreas", Set.class,
                "otherContributionAreas", Set.class
        );
        Map<String, Class<?>> actual = Arrays.stream(Member.class.getDeclaredFields())
                .collect(java.util.stream.Collectors.toMap(Field::getName, Field::getType));

        assertThat(actual).containsAllEntriesOf(requiredState);
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-001/009 and ADR-0027 - Member exposes aggregate mutations for owned information")
    void memberShouldExposeOwnedInformationMutations() {
        Set<String> operations = Arrays.stream(Member.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(operations).contains(
                "replaceCoreProfile",
                "changeGamEntryDate",
                "replaceDietaryRestriction",
                "replaceExperiences",
                "replaceSacraments",
                "replaceContributionProfile"
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-001 and ADR-0027 - workflows cannot bypass Member through entity construction or setters")
    void memberWorkflowsShouldPersistOnlyAggregateDerivedEntities() throws Exception {
        Map<String, Path> workflows = Map.of(
                "registration", Path.of("src/main/java/br/org/gam/api/member/application/useCases/registerMember/RegisterMember.java"),
                "linking", Path.of("src/main/java/br/org/gam/api/member/application/useCases/Activation.java"),
                "import", Path.of("src/main/java/br/org/gam/api/member/application/useCases/MemberInformationImportJob.java"),
                "information mutation", Path.of("src/main/java/br/org/gam/api/member/application/useCases/MemberInformation.java")
        );

        org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
        workflows.forEach((workflow, path) -> {
            String source;
            try {
                source = Files.readString(path);
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
            softly.assertThat(source).as(workflow + " rich aggregate seam")
                    .contains("br.org.gam.api.member.domain.Member");
            softly.assertThat(source).as(workflow + " direct persistence bypass")
                    .doesNotContain(
                            "new MemberEntity()",
                            "newMemberEntity.set",
                            "member.setAccount(",
                            "member.setName(",
                            "member.setBirthDate(",
                            "member.setGamEntryDate(",
                            "member.setResidentialCity(",
                            "member.setPhoneNumber(",
                            "member.setContactEmail(",
                            "member.setStatus(",
                            "member.setDietaryRestriction",
                            "member.setExperiences(",
                            "member.setSacraments(",
                            "member.setContributionAreas(",
                            "member.setOtherContributionAreas("
                    );
        });
        softly.assertAll();
    }
}
