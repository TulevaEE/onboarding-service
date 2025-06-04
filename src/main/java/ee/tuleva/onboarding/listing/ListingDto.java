package ee.tuleva.onboarding.listing;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record ListingDto(
    @NotNull Long id,
    @NotNull ListingType type,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal units,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal pricePerUnit,
    @NotNull Currency currency,
    @NotNull @Future Instant expiryTime,
    @NotNull Instant createdTime) {

  public static ListingDto from(Listing listing) {
    return new ListingDto(
        listing.getId(),
        listing.getType(),
        listing.getUnits(),
        listing.getPricePerUnit(),
        listing.getCurrency(),
        listing.getExpiryTime(),
        listing.getCreatedTime());
  }
}
