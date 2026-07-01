package br.org.gam.api.common.mapper;

import br.org.gam.api.Entities.RBAC.permission.Permission;
import br.org.gam.api.Entities.RBAC.permission.PermissionMapper;
import br.org.gam.api.Entities.RBAC.permission.persistence.PermissionEntity;
import br.org.gam.api.Entities.RBAC.permission.services.PermissionRDTO;
import br.org.gam.api.Entities.RBAC.role.Role;
import br.org.gam.api.Entities.RBAC.role.RoleMapper;
import br.org.gam.api.Entities.RBAC.role.persistence.RoleEntity;
import br.org.gam.api.Entities.RBAC.role.services.RoleRDTO;
import br.org.gam.api.Entities.location.Location;
import br.org.gam.api.Entities.location.LocationMapper;
import br.org.gam.api.Entities.location.persistence.LocationEntity;
import br.org.gam.api.Entities.location.services.LocationRDTO;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.UUID;

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
        @DisplayName("permission domain -> entity")
        void permissionDomainShouldMapToEntity() {
            Permission permission = Permission.register("MEMBER_GET", "View active members");

            PermissionEntity entity = permissionMapper.domainToEntity(permission);

            assertThat(entity.getId()).isEqualTo(permission.getId());
            assertThat(entity.getName()).isEqualTo("MEMBER_GET");
            assertThat(entity.getDescription()).isEqualTo("View active members");
        }

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
        @DisplayName("role domain -> entity")
        void roleDomainShouldMapToEntity() {
            Role role = Role.register("ADMIN", "System administrator");

            RoleEntity entity = roleMapper.domainToEntity(role);

            assertThat(entity.getId()).isEqualTo(role.getId());
            assertThat(entity.getName()).isEqualTo("ADMIN");
            assertThat(entity.getDescription()).isEqualTo("System administrator");
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
        @DisplayName("location domain -> entity")
        void locationDomainShouldMapToEntity() {
            BigDecimal latitude = new BigDecimal("-22.90684670");
            BigDecimal longitude = new BigDecimal("-47.06158810");
            Location location = Location.register("Parish Hall", "Street", "Campinas", "SP", "13000-000", "BRA", latitude, longitude);

            LocationEntity entity = locationMapper.domainToEntity(location);

            assertThat(entity.getId()).isEqualTo(location.getId());
            assertThat(entity.getName()).isEqualTo("Parish Hall");
            assertThat(entity.getStreet()).isEqualTo("Street");
            assertThat(entity.getCity()).isEqualTo("Campinas");
            assertThat(entity.getState()).isEqualTo("SP");
            assertThat(entity.getPostalCode()).isEqualTo("13000-000");
            assertThat(entity.getCountryCode()).isEqualTo("BRA");
            assertThat(entity.getLatitude()).isEqualTo(latitude);
            assertThat(entity.getLongitude()).isEqualTo(longitude);
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
            entity.setLatitude(new BigDecimal("-22.90684670"));
            entity.setLongitude(new BigDecimal("-47.06158810"));

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
