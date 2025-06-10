package ee.tuleva.onboarding.listing;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListingService {

  private final ListingRepository listingRepository;
  private final UserService userService;
  //  private final EmailService emailService;
  //  private final EmailPersistenceService emailPersistenceService;
  private final Clock clock;

  //  private final LocaleService localeService;

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

  public void deleteListing(Long id, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId());
    Member member = user.getMember().orElseThrow();
    listingRepository.deleteByIdAndMemberId(id, member.getId());
  }

  public MessageResponse contactListingOwner(
      Long listingId,
      ContactMessageRequest messageRequest,
      AuthenticatedPerson authenticatedPerson) {
    // TODO
    return new MessageResponse(1L, "QUEUED");
  }
  //  public MessageResponse contactListingOwner(
  //      Long listingId,
  //      ContactMessageRequest messageRequest,
  //      AuthenticatedPerson authenticatedPerson) {
  //    var listing = listingRepository.findById(listingId).orElseThrow();
  //    User user = userService.getByMemberId(listing.getMemberId());
  //    Map<String, Object> mergeVars =
  //        Map.of(
  //            "message", messageRequest.message(),
  //            "contactPreference", messageRequest.contactPreference().name(),
  //            "counterParty", new PersonImpl(authenticatedPerson));
  //    List<String> tags = List.of();
  //    EmailType emailType = LISTING_CONTACT;
  //    Locale locale = localeService.getCurrentLocale();
  //    String templateName = emailType.getTemplateName(locale);
  //
  //    MandrillMessage message =
  //        emailService.newMandrillMessage(user.getEmail(), templateName, mergeVars, tags, null);
  //
  //    return emailService
  //        .send(user, message, templateName)
  //        .map(
  //            response -> {
  //              Email saved =
  //                  emailPersistenceService.save(
  //                      user, response.getId(), emailType, response.getStatus());
  //              return new MessageResponse(saved.getId(), response.getStatus());
  //            })
  //        .orElseThrow();
  //  }
}
