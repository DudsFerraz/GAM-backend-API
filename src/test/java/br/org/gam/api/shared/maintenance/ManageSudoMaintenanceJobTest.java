package br.org.gam.api.shared.maintenance;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.application.useCases.ManageSudoRole;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("SUDO Maintenance Job")
class ManageSudoMaintenanceJobTest {

    @Mock
    private ManageSudoRole manageSudoRole;

    @Mock
    private AccountEntityLoader accountEntityLoader;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @ParameterizedTest
    @MethodSource("invalidReasons")
    @DisplayName("REQ-ACCOUNT-ROLE-005 - invalid reason -> rejected before Account selector resolution")
    void invalidReasonShouldBeRejectedBeforeAccountResolution(String reason) {
        ManageSudoMaintenanceJob job = newJob();
        DefaultApplicationArguments arguments = arguments(
                "assign-sudo", reason, null, "account@example.com");

        assertThatThrownBy(() -> job.run(arguments))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("blank Account selector -> rejected even when email selector is valid")
    void blankAccountSelectorShouldNotFallBackToEmailSelector() {
        ManageSudoMaintenanceJob job = newJob();
        DefaultApplicationArguments arguments = arguments(
                "assign-sudo", "Grant recovery access", "", "account@example.com");

        assertThatThrownBy(() -> job.run(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only one account selector: --maintenance.account-id or --maintenance.account-email.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-010 - missing Account selector -> rejected before mutation")
    void missingAccountSelectorShouldBeRejectedBeforeMutation() {
        ManageSudoMaintenanceJob job = newJob();

        assertThatThrownBy(() -> job.run(arguments(
                "assign-sudo", "Grant recovery access", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing required account selector --maintenance.account-id or --maintenance.account-email.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-010 - both Account selectors -> rejected before mutation")
    void bothAccountSelectorsShouldBeRejectedBeforeMutation() {
        ManageSudoMaintenanceJob job = newJob();

        assertThatThrownBy(() -> job.run(arguments(
                "assign-sudo", "Grant recovery access", "00000000-0000-0000-0000-000000000001", "account@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only one account selector: --maintenance.account-id or --maintenance.account-email.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-010 - blank Account UUID selector -> rejected before mutation")
    void blankAccountIdSelectorShouldBeRejectedBeforeMutation() {
        ManageSudoMaintenanceJob job = newJob();

        assertThatThrownBy(() -> job.run(arguments(
                "assign-sudo", "Grant recovery access", "", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account selector must not be blank.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-010 - blank Account email selector -> rejected before mutation")
    void blankAccountEmailSelectorShouldBeRejectedBeforeMutation() {
        ManageSudoMaintenanceJob job = newJob();

        assertThatThrownBy(() -> job.run(arguments(
                "assign-sudo", "Grant recovery access", null, "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account selector must not be blank.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-009 and REQ-ACCOUNT-ROLE-011 - assign-sudo by UUID -> delegates normalized reason")
    void assignSudoByUuidShouldDelegateNormalizedReason() {
        ManageSudoMaintenanceJob job = newJob();
        var accountId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

        try (MockedStatic<SpringApplication> ignored = Mockito.mockStatic(SpringApplication.class)) {
            job.run(arguments("assign-sudo", " Grant recovery access ", accountId.toString(), null));
        }

        verify(manageSudoRole).assignSudo(accountId, "Grant recovery access");
        verifyNoInteractions(accountEntityLoader);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-009 and REQ-ACCOUNT-ROLE-012 - remove-sudo by email -> resolves active Account and delegates")
    void removeSudoByEmailShouldResolveAccountAndDelegate() {
        ManageSudoMaintenanceJob job = newJob();
        var accountId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        String email = "account@example.com";
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        when(accountEntityLoader.requiredByEmail(GamEmail.of(email))).thenReturn(account);

        try (MockedStatic<SpringApplication> ignored = Mockito.mockStatic(SpringApplication.class)) {
            job.run(arguments("remove-sudo", " Remove recovery access ", null, email));
        }

        verify(accountEntityLoader).requiredByEmail(GamEmail.of(email));
        verify(manageSudoRole).removeSudo(accountId, "Remove recovery access");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-009 - unsupported SUDO maintenance action -> rejected")
    void unsupportedActionShouldBeRejected() {
        ManageSudoMaintenanceJob job = newJob();
        var accountId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000003");

        assertThatThrownBy(() -> job.run(arguments(
                "inspect-sudo", "Inspect recovery access", accountId.toString(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported maintenance.action for sudo job: inspect-sudo");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    private ManageSudoMaintenanceJob newJob() {
        return new ManageSudoMaintenanceJob(manageSudoRole, accountEntityLoader, applicationContext);
    }

    private static DefaultApplicationArguments arguments(
            String action,
            String reason,
            String accountId,
            String accountEmail
    ) {
        List<String> options = new ArrayList<>();
        if (action != null) {
            options.add("--maintenance.action=" + action);
        }
        if (reason != null) {
            options.add("--maintenance.reason=" + reason);
        }
        if (accountId != null) {
            options.add("--maintenance.account-id=" + accountId);
        }
        if (accountEmail != null) {
            options.add("--maintenance.account-email=" + accountEmail);
        }
        return new DefaultApplicationArguments(options.toArray(String[]::new));
    }

    private static java.util.stream.Stream<String> invalidReasons() {
        return java.util.stream.Stream.of(null, "", " \n\t", "a".repeat(2_001));
    }
}
