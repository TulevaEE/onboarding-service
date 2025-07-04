package ee.tuleva.onboarding.listing;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.user.User;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record ListingDto(
    @NotNull Long id,
    @NotNull ListingType type,
    @Positive @Digits(integer = 12, fraction = 2) BigDecimal units,
    @DecimalMin("1") @Digits(integer = 4, fraction = 2) BigDecimal pricePerUnit,
    @NotNull Currency currency,
    @NotNull String language,
    @NotNull boolean isOwnListing,
    @NotNull @Future Instant expiryTime,
    @NotNull Instant createdTime) {

  public static ListingDto from(Listing listing, User user) {

    var isOwner = user.getMemberId().equals(listing.getMemberId());
    return new ListingDto(
        listing.getId(),
        listing.getType(),
        listing.getUnits(),
        listing.getPricePerUnit(),
        listing.getCurrency(),
        listing.getLanguage(),
        isOwner,
        listing.getExpiryTime(),
        listing.getCreatedTime());
  }
}
