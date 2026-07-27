package br.org.gam.api.shared.maintenance;

import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Soft-deleted Record Maintenance Job")
class SoftDeletedRecordMaintenanceJobTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private ActivityEvents activityEvents;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInspectionReasons")
    @DisplayName("REQ-ACTIVITY-014 and REQ-PERSISTENCE-012 - invalid inspection reason -> rejected before deleted-row query")
    void invalidInspectionReasonShouldBeRejectedBeforeDeletedRowsAreQueried(String scenario, String reason) {
        SoftDeletedRecordMaintenanceJob job = newJob();
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.getResultList()).thenReturn(List.of());

        try (MockedStatic<SpringApplication> ignored = Mockito.mockStatic(SpringApplication.class)) {
            assertThatThrownBy(() -> job.run(arguments(reason)))
                    .as(scenario)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(entityManager, activityEvents);
    }

    @Test
    @DisplayName("REQ-ACTIVITY-011/014 and REQ-PERSISTENCE-012 - inspection without commit proof -> identifier never disclosed")
    void inspectionWithoutCommitProofShouldNeverDiscloseIdentifiers() {
        UUID deletedRecordId = UUID.randomUUID();
        Logger logger = (Logger) LoggerFactory.getLogger(SoftDeletedRecordMaintenanceJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(deletedRecordId));
        doAnswer(invocation -> {
            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains(deletedRecordId.toString()));
            return null;
        }).when(activityEvents).developerMaintenance(
                any(ActivityAction.class),
                any(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyMap()
        );

        try (MockedStatic<SpringApplication> ignored = Mockito.mockStatic(SpringApplication.class)) {
            runWithAppenderCleanup(newJob(), arguments("Investigate deleted records"), logger, appender);
        }

        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains(deletedRecordId.toString()));
    }

    @Test
    @StructuralTest
    @DisplayName("REQ-ACTIVITY-011/014 and REQ-PERSISTENCE-012 - audit commit failure -> no deleted identifier disclosed")
    void inspectionShouldNotDiscloseIdentifiersWhenAuditFailsAtCommit() {
        UUID deletedRecordId = UUID.randomUUID();
        Logger logger = (Logger) LoggerFactory.getLogger(SoftDeletedRecordMaintenanceJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(deletedRecordId));
        doAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("forced activity commit failure");
                }
            });
            return null;
        }).when(activityEvents).developerMaintenance(
                any(ActivityAction.class),
                any(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyMap()
        );

        try (MockedStatic<SpringApplication> ignored = Mockito.mockStatic(SpringApplication.class)) {
            TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());

            assertThatThrownBy(() -> transaction.executeWithoutResult(
                    status -> newJob().run(arguments("Investigate deleted records"))
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("forced activity commit failure");

            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains(deletedRecordId.toString()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void runWithAppenderCleanup(
            SoftDeletedRecordMaintenanceJob job,
            DefaultApplicationArguments arguments,
            Logger logger,
            ListAppender<ILoggingEvent> appender
    ) {
        try {
            job.run(arguments);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private SoftDeletedRecordMaintenanceJob newJob() {
        return new SoftDeletedRecordMaintenanceJob(entityManager, activityEvents, applicationContext);
    }

    private static DefaultApplicationArguments arguments(String reason) {
        List<String> options = new ArrayList<>(List.of(
                "--maintenance.action=inspect-soft-deleted",
                "--maintenance.table=events"
        ));
        if (reason != null) {
            options.add("--maintenance.reason=" + reason);
        }
        return new DefaultApplicationArguments(options.toArray(String[]::new));
    }

    private static Stream<Arguments> invalidInspectionReasons() {
        return Stream.of(
                Arguments.of("missing reason", null),
                Arguments.of("empty reason", ""),
                Arguments.of("Unicode whitespace-only reason", "\u00A0\u202F"),
                Arguments.of("reason above 2,000 code points", "a".repeat(2_001))
        );
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
