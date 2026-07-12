package br.org.gam.api.shared.exception;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Global Exception Handler")
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("active Account-role uniqueness violation -> HTTP 409")
    void activeAccountRoleUniquenessViolationShouldReturnConflict() {
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "duplicate active Account-role assignment",
                null,
                "idx_account_role_not_deleted"
        );
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "active Account-role uniqueness violation",
                constraintViolation
        );

        var response = new GlobalExceptionHandler().dataIntegrityViolationHandler(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_CONFLICT");
    }
}
