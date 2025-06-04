package ee.tuleva.onboarding.listing;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record NewListingRequest(
    @NotNull ListingType type,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal units,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal pricePerUnit,
    @NotNull Currency currency,
    @NotNull Instant expiryDate) {

  public Listing toListing(Long memberId) {
    return Listing.builder()
        .memberId(memberId)
        .type(type)
        .units(units)
        .pricePerUnit(pricePerUnit)
        .currency(currency)
        .expiryTime(expiryDate)
        .build();
  }
}
