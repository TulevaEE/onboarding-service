package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.UNVESTED_WORK_COMPENSATION;
import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.ApiCapitalEvent;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
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
  private final CapitalService capitalService;
  private final LocaleService localeService;

  public ListingDto createListing(
      NewListingRequest request, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId());

    if (!user.isMember()) {
      throw new IllegalArgumentException("Need to be member to create listing");
    }

    if (!hasEnoughMemberCapital(user, request)) {
      throw new IllegalArgumentException("Not enough member capital to create listing");
    }

    Listing saved =
        listingRepository.save(
            request.toListing(user.getMemberId(), localeService.getCurrentLanguage()));
    return ListingDto.from(saved, user);
  }

  public List<ListingDto> findActiveListings(AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId());

    return listingRepository.findByExpiryTimeAfter(clock.instant()).stream()
        .filter(listing -> listing.getState().equals(ACTIVE))
        .map((listing) -> ListingDto.from(listing, user))
        .toList();
  }

  public Listing cancelListing(Long id, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId());
    Member member = user.getMember().orElseThrow();
    Listing listing = listingRepository.findByIdAndMemberId(id, member.getId()).orElseThrow();
    listing.cancel();
    return listingRepository.save(listing);
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

  private boolean hasEnoughMemberCapital(User user, NewListingRequest request) {
    var totalMemberCapital =
        capitalService.getCapitalEvents(user.getMemberId()).stream()
            .filter(event -> event.type() != UNVESTED_WORK_COMPENSATION)
            .map(ApiCapitalEvent::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return totalMemberCapital.compareTo(request.units()) >= 0;
  }
}
