package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import lombok.Builder;

@Builder
public record NewListingRequest(
    @NotNull ListingType type,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal units,
    @DecimalMin("1") @Digits(integer = 4, fraction = 2) BigDecimal pricePerUnit,
    @NotNull Currency currency,
    @NotNull Instant expiryTime) {

  public Listing toListing(Long memberId, String language) {
    return Listing.builder()
        .memberId(memberId)
        .type(type)
        .units(units)
        .pricePerUnit(pricePerUnit)
        .language(language)
        .currency(currency)
        .state(ACTIVE)
        .expiryTime(expiryTime)
        .build();
  }

  @AssertTrue(message = "expiryDate must be within one year")
  private boolean isWithinOneYear() {
    return Optional.ofNullable(expiryTime).orElseThrow().isBefore(Instant.now().plus(365, DAYS));
  }
}
