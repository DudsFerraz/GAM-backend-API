package br.org.gam.api.gamLocation.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GamLocationRepository extends BaseRepository<GamLocationEntity, UUID> {

    @Query("""
            select location from GamLocationEntity location
            where location.deletedAt is null
              and location.identityName = :identityName
              and location.identityStreet = :identityStreet
              and location.identityCity = :identityCity
              and location.identityState = :identityState
              and location.identityPostalCode = :identityPostalCode
              and location.identityCountryCode = :identityCountryCode
            """)
    Optional<GamLocationEntity> findActiveDuplicate(
            @Param("identityName") String identityName,
            @Param("identityStreet") String identityStreet,
            @Param("identityCity") String identityCity,
            @Param("identityState") String identityState,
            @Param("identityPostalCode") String identityPostalCode,
            @Param("identityCountryCode") String identityCountryCode
    );

    @Query("""
            select location from GamLocationEntity location
            where location.deletedAt is null
              and location.id <> :excludedId
              and location.identityName = :identityName
              and location.identityStreet = :identityStreet
              and location.identityCity = :identityCity
              and location.identityState = :identityState
              and location.identityPostalCode = :identityPostalCode
              and location.identityCountryCode = :identityCountryCode
            """)
    Optional<GamLocationEntity> findActiveDuplicateExcluding(
            @Param("excludedId") UUID excludedId,
            @Param("identityName") String identityName,
            @Param("identityStreet") String identityStreet,
            @Param("identityCity") String identityCity,
            @Param("identityState") String identityState,
            @Param("identityPostalCode") String identityPostalCode,
            @Param("identityCountryCode") String identityCountryCode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select location from GamLocationEntity location where location.id = :id")
    Optional<GamLocationEntity> findActiveByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select count(*) from events where gam_location_id = :locationId", nativeQuery = true)
    long countEventReferencesIncludingDeleted(@Param("locationId") UUID locationId);
}
