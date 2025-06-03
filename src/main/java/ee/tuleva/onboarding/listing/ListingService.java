package ee.tuleva.onboarding.listing;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListingService {
  public ListingDto createListing(NewListingRequest newListingRequest) {
    return null;
  }

  public List<ListingDto> findActiveListings() {
    return null;
  }

  public void deleteListing(Long id) {}

  public MessageResponse contactListingOwner(Long listingId, ContactMessageRequest messageRequest) {
    return null;
  }
}
