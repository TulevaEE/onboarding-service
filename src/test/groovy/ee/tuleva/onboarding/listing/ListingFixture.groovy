package ee.tuleva.onboarding.listing

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.listing.Listing.ListingBuilder
import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE
import static ee.tuleva.onboarding.listing.Listing.builder
import static ee.tuleva.onboarding.listing.ListingType.BUY
import static ee.tuleva.onboarding.listing.NewListingRequest.NewListingRequestBuilder
import static ee.tuleva.onboarding.time.TestClockHolder.now
import static java.time.temporal.ChronoUnit.DAYS

class ListingsFixture {

  static ListingBuilder activeListing() {
    builder()
        .id(1L)
        .memberId(1L)
        .type(BUY)
        .units(100.00)
        .totalPrice(300.00)
        .currency(EUR)
        .language("et")
        .expiryTime(now.plus(30, DAYS))
        .state(ACTIVE)
        .createdTime(now)
  }

  static ListingBuilder expiredListing() {
    activeListing().expiryTime(now.minus(30, DAYS))
  }

  static ListingBuilder completedListing() {
    activeListing().completedTime(now.minus(30, DAYS))
  }

  static ListingBuilder cancelledListing() {
    activeListing().completedTime(now.minus(30, DAYS))
  }

  static NewListingRequestBuilder newListingRequest() {
    new NewListingRequestBuilder()
        .type(BUY)
        .units(100.00)
        .totalPrice(450.0)
        .currency(EUR)
        .expiryTime(now.plus(30, DAYS))
  }
}
