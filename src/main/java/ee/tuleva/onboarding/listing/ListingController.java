package ee.tuleva.onboarding.listing;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/v1/listings")
@RequiredArgsConstructor
public class ListingController {

  private final ListingService listingService;

  @PostMapping
  public ResponseEntity<ListingDto> create(
      @Valid @RequestBody NewListingRequest request,
      UriComponentsBuilder uriBuilder,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    ListingDto listing = listingService.createListing(request, authenticatedPerson);
    URI location = uriBuilder.path("/v1/listings/{id}").buildAndExpand(listing.id()).toUri();
    return ResponseEntity.created(location).body(listing);
  }

  @GetMapping
  public List<ListingDto> find(@AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return listingService.findActiveListings(authenticatedPerson);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    listingService.cancelListing(id, authenticatedPerson);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/preview-message")
  public ResponseEntity<String> previewMessage(
      @PathVariable Long id,
      @Valid @RequestBody ContactMessageRequest request,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return ResponseEntity.ok()
        .body(listingService.getContactMessage(id, request, authenticatedPerson));
  }

  @PostMapping("/{id}/contact")
  public ResponseEntity<MessageResponse> contact(
      @PathVariable Long id,
      @Valid @RequestBody ContactMessageRequest request,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    MessageResponse response = listingService.contactListingOwner(id, request, authenticatedPerson);
    return ResponseEntity.accepted().body(response);
  }
}
