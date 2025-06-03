package ee.tuleva.onboarding.listing;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/v1/listings")
@RequiredArgsConstructor
public class ListingController {

  private final ListingService listingService;

  @PostMapping
  public ResponseEntity<ListingDto> create(
      @Valid @RequestBody NewListingRequest request, UriComponentsBuilder uriBuilder) {
    ListingDto created = listingService.createListing(request);
    URI location = uriBuilder.path("/v1/listings/{id}").buildAndExpand(created.id()).toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping
  public List<ListingDto> find() {
    return listingService.findActiveListings();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    listingService.deleteListing(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/contact")
  public ResponseEntity<MessageResponse> contact(
      @PathVariable Long id, @Valid @RequestBody ContactMessageRequest request) {
    MessageResponse response = listingService.contactListingOwner(id, request);
    return ResponseEntity.accepted().body(response);
  }
}
