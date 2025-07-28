package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.UNVESTED_WORK_COMPENSATION;
import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE;
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.LISTING_CONTACT;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.ApiCapitalEvent;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListingService {

  private final ListingRepository listingRepository;
  private final UserService userService;
  private final EmailPersistenceService emailPersistenceService;
  private final EmailService emailService;
  private final Clock clock;
  private final CapitalService capitalService;
  private final LocaleService localeService;

  public ListingDto createListing(
      NewListingRequest request, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();

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
    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();

    return listingRepository.findByExpiryTimeAfter(clock.instant()).stream()
        .filter(listing -> listing.getState().equals(ACTIVE))
        .map((listing) -> ListingDto.from(listing, user))
        .toList();
  }

  public Listing cancelListing(Long id, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();
    Member member = user.getMember().orElseThrow();
    Listing listing = listingRepository.findByIdAndMemberId(id, member.getId()).orElseThrow();
    listing.cancel();
    return listingRepository.save(listing);
  }

  public MessageResponse contactListingOwner(
      Long listingId,
      ContactMessageRequest messageRequest,
      AuthenticatedPerson authenticatedPerson) {
    var listing = listingRepository.findById(listingId).orElseThrow();
    User listingOwner = userService.getByMemberId(listing.getMemberId());
    User userContacting = userService.getById(authenticatedPerson.getUserId()).orElseThrow();

    var mergeVars = new HashMap<String, Object>();

    mergeVars.put("message", transformMessageNewlines(messageRequest.message()));

    mergeVars.putAll(getNameMergeVars(listingOwner));

    List<String> tags = List.of();
    EmailType emailType = LISTING_CONTACT;

    String listingLanguage = listing.getLanguage();
    String templateName = emailType.getTemplateName(listingLanguage);

    MandrillMessage message =
        emailService.newMandrillMessage(
            listingOwner.getEmail(),
            userContacting.getEmail(),
            templateName,
            mergeVars,
            tags,
            List.of());

    return emailService
        .send(listingOwner, message, templateName)
        .map(
            response -> {
              Email saved =
                  emailPersistenceService.save(
                      listingOwner, response.getId(), emailType, response.getStatus());
              return new MessageResponse(saved.getId(), response.getStatus());
            })
        .orElseThrow();
  }

  private boolean hasEnoughMemberCapital(User user, NewListingRequest request) {
    if (request.type() == ListingType.BUY) {
      return true;
    }

    var totalMemberCapital =
        capitalService.getCapitalEvents(user.getMemberId()).stream()
            .filter(event -> event.type() != UNVESTED_WORK_COMPENSATION)
            .map(ApiCapitalEvent::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return totalMemberCapital.compareTo(request.units()) >= 0;
  }

  private String transformMessageNewlines(String message) {
    var newLine = "<br />";

    return message
        .replace("\r\n", newLine)
        .replace("\n\r", newLine)
        .replace("\r", newLine)
        .replace("\n", newLine);
  }
}
