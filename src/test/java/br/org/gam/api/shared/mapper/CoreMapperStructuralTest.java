package br.org.gam.api.shared.mapper;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.application.LocationRDTO;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Role.application.RoleMapper;
import br.org.gam.api.rbac.Role.application.RoleRDTO;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@DisplayName("Core MapStruct Mappers")
class CoreMapperStructuralTest {

    private final PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);
    private final RoleMapper roleMapper = Mappers.getMapper(RoleMapper.class);
    private final LocationMapper locationMapper = Mappers.getMapper(LocationMapper.class);

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("permission entity -> response DTO")
        void permissionEntityShouldMapToResponseDto() {
            PermissionEntity entity = permissionEntity(UUID.randomUUID(), "MEMBER_GET", "View active members");

            PermissionRDTO dto = permissionMapper.entityToPermissionRDTO(entity);

            assertThat(dto.id()).isEqualTo(entity.getId());
            assertThat(dto.name()).isEqualTo("MEMBER_GET");
            assertThat(dto.description()).isEqualTo("View active members");
        }

        @Test
        @DisplayName("role entity -> response DTO")
        void roleEntityShouldMapToResponseDto() {
            RoleEntity entity = roleEntity(UUID.randomUUID(), "ADMIN", "System administrator");

            RoleRDTO dto = roleMapper.entityToRoleRDTO(entity);

            assertThat(dto.id()).isEqualTo(entity.getId());
            assertThat(dto.name()).isEqualTo("ADMIN");
            assertThat(dto.description()).isEqualTo("System administrator");
        }

        @Test
        @DisplayName("location entity -> response DTO")
        void locationEntityShouldMapToResponseDto() {
            LocationEntity entity = new LocationEntity();
            entity.setId(UUID.randomUUID());
            entity.setName("Parish Hall");
            entity.setStreet("Street");
            entity.setCity("Campinas");
            entity.setState("SP");
            entity.setPostalCode("13000-000");
            entity.setCountryCode("BRA");
            entity.setLatitude(new java.math.BigDecimal("-22.90684670"));
            entity.setLongitude(new java.math.BigDecimal("-47.06158810"));

            LocationRDTO dto = locationMapper.entityToLocationRDTO(entity);

            assertThat(dto.id()).isEqualTo(entity.getId());
            assertThat(dto.name()).isEqualTo("Parish Hall");
            assertThat(dto.street()).isEqualTo("Street");
            assertThat(dto.city()).isEqualTo("Campinas");
            assertThat(dto.state()).isEqualTo("SP");
            assertThat(dto.postalCode()).isEqualTo("13000-000");
            assertThat(dto.countryCode()).isEqualTo("BRA");
            assertThat(dto.latitude()).isEqualTo(entity.getLatitude());
            assertThat(dto.longitude()).isEqualTo(entity.getLongitude());
        }
    }

    private static PermissionEntity permissionEntity(UUID id, String name, String description) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription(description);
        return entity;
    }

    private static RoleEntity roleEntity(UUID id, String name, String description) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription(description);
        return entity;
    }
}
