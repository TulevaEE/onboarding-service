package ee.tuleva.onboarding.listing;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record ListingDto(
    @NotNull Long id,
    @NotNull Long ownerId,
    @NotNull ListingType type,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal units,
    @Positive BigDecimal pricePerUnit,
    @NotNull Currency currency,
    @NotNull Instant expiryTime,
    @NotNull Instant createdTime) {}
