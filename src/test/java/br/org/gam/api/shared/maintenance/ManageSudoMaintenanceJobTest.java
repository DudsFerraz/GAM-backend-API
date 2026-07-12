package br.org.gam.api.shared.maintenance;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.rbac.accountRole.application.useCases.ManageSudoRole;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@UnitTest
@FunctionalTest
@DisplayName("SUDO Maintenance Job")
class ManageSudoMaintenanceJobTest {

    @Mock
    private ManageSudoRole manageSudoRole = Mockito.mock(ManageSudoRole.class);

    @Mock
    private AccountEntityLoader accountEntityLoader = Mockito.mock(AccountEntityLoader.class);

    @Mock
    private ConfigurableApplicationContext applicationContext = Mockito.mock(ConfigurableApplicationContext.class);

    @Test
    @DisplayName("invalid reason -> rejected before Account selector resolution")
    void invalidReasonShouldBeRejectedBeforeAccountResolution() {
        ManageSudoMaintenanceJob job = newJob();
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(
                "--maintenance.action=assign-sudo",
                "--maintenance.reason=" + "a".repeat(2_001),
                "--maintenance.account-email=account@example.com"
        );

        assertThatThrownBy(() -> job.run(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SUDO role changes require an audit reason.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    @Test
    @DisplayName("blank Account selector -> rejected even when email selector is valid")
    void blankAccountSelectorShouldNotFallBackToEmailSelector() {
        ManageSudoMaintenanceJob job = newJob();
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(
                "--maintenance.action=assign-sudo",
                "--maintenance.reason=Grant recovery access",
                "--maintenance.account-id=",
                "--maintenance.account-email=account@example.com"
        );

        assertThatThrownBy(() -> job.run(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only one account selector: --maintenance.account-id or --maintenance.account-email.");

        verifyNoInteractions(accountEntityLoader, manageSudoRole);
    }

    private ManageSudoMaintenanceJob newJob() {
        return new ManageSudoMaintenanceJob(manageSudoRole, accountEntityLoader, applicationContext);
    }
}
