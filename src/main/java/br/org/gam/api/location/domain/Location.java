package br.org.gam.api.location.domain;

import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class Location {
    UUID id;
    String name;
    String street;
    String city;
    String state;
    String postalCode;
    String countryCode;
    BigDecimal latitude;
    BigDecimal longitude;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(String name, String street, String city, String state, String postalCode, String countryCode,
     *                      BigDecimal latitude, BigDecimal longitude)}.
     */
    @Deprecated
    public Location(UUID id, String name, String street, String city, String state, String postalCode, String countryCode, BigDecimal latitude, BigDecimal longitude) {
        this.id = id;
        this.name = name;
        this.street = street;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Location register(String name, String street, String city, String state, String postalCode,
                                    String countryCode, BigDecimal latitude, BigDecimal longitude){

        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(city, "City cannot be null");
        Objects.requireNonNull(state, "State cannot be null");
        Objects.requireNonNull(countryCode, "CountryCode cannot be null");

        name = name.trim();
        city = city.trim();
        state = state.trim();
        countryCode = countryCode.trim();

        if (street == null) street = "";
        street = street.trim();
        if (postalCode == null) postalCode = "";
        postalCode = postalCode.trim();

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Location(id, name, street, city, state, postalCode, countryCode, latitude, longitude);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
