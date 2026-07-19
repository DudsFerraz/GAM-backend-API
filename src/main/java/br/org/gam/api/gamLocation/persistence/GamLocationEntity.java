package br.org.gam.api.gamLocation.persistence;

import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Setter
@Getter
@NoArgsConstructor
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "gam_locations")
public class GamLocationEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "street", length = 255)
    private String street;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 50)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "identity_name", nullable = false, columnDefinition = "TEXT")
    private String identityName;

    @Column(name = "identity_street", nullable = false, columnDefinition = "TEXT")
    private String identityStreet;

    @Column(name = "identity_city", nullable = false, columnDefinition = "TEXT")
    private String identityCity;

    @Column(name = "identity_state", nullable = false, columnDefinition = "TEXT")
    private String identityState;

    @Column(name = "identity_postal_code", nullable = false, columnDefinition = "TEXT")
    private String identityPostalCode;

    @Column(name = "identity_country_code", nullable = false, columnDefinition = "TEXT")
    private String identityCountryCode;
}
