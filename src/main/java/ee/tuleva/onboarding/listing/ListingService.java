package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.listing.MessageResponse.Status.QUEUED;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListingService {

  private final ListingRepository listingRepository;
  private final UserService userService;
  private final Clock clock;

  public ListingDto createListing(
      NewListingRequest request, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId());
    Listing saved = listingRepository.save(request.toListing(user.getMemberId()));
    return ListingDto.from(saved);
  }

  public List<ListingDto> findActiveListings() {
    return listingRepository.findByExpiryTimeAfter(clock.instant()).stream()
        .map(ListingDto::from)
        .toList();
  }

  public void deleteListing(Long id) {
    listingRepository.deleteById(id);
  }

  public MessageResponse contactListingOwner(Long listingId, ContactMessageRequest messageRequest) {
    // TODO
    return new MessageResponse(1L, QUEUED);
  }
}
