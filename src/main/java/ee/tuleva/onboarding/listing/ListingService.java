package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.UNVESTED_WORK_COMPENSATION;
import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE;
import static ee.tuleva.onboarding.listing.ListingType.BUY;
import static ee.tuleva.onboarding.listing.ListingType.SELL;
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.*;
import static org.springframework.web.util.HtmlUtils.htmlEscape;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.CapitalRow;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractService;
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
  private final CapitalTransferContractService capitalTransferContractService;
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

  public Long getActiveListingCount() {
    return listingRepository.countListingsByExpiryTimeAfterAndStateEquals(clock.instant(), ACTIVE);
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

    mergeVars.put("message", getContactMessage(listingId, messageRequest, authenticatedPerson));

    mergeVars.putAll(getNameMergeVars(listingOwner));

    List<String> tags = List.of();
    EmailType emailType =
        listing.getType() == BUY ? LISTING_REPLY_TO_BUYER : LISTING_REPLY_TO_SELLER;

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

  public String getContactMessage(
      Long listingId,
      ContactMessageRequest contactMessageRequest,
      AuthenticatedPerson interestedParty) {

    var listing = listingRepository.findById(listingId).orElseThrow();
    var interestedUser = userService.getByIdOrThrow(interestedParty.getUserId());

    var interestedUserName = htmlEscape(interestedUser.getFullName());
    var interestedUserEmail = htmlEscape(interestedUser.getEmail());
    var interestedUserPhoneNumber = htmlEscape(interestedUser.getPhoneNumber());
    var interestedUserPersonalCode = htmlEscape(interestedUser.getPersonalCode());

    var bookValue = listing.getBookValue().toString();
    var totalAmount = listing.getTotalPrice();
    var language = String.valueOf(listing.getLanguage());

    if ("en".equalsIgnoreCase(language)) {
      return transformMessageNewlines(
              """
              %s %s, amount: %s €; price: %s €

              If you’d like to proceed, <b>please contact the interested party and agree on the details</b>: price, amount, and where and when you should transfer the money. You can simply reply to this email — that will start a direct email thread between you two. Tuleva won’t see your messages.

              Once you’ve agreed on the transfer, the seller must start the application. You can use the <a href="https://pension.tuleva.ee/capital/listings">Initiate the sale</a> button on Tuleva’s membership capital transfer page. In addition to the deal details, the seller will need the buyer's personal identification code.

              Here are the %s details:,
              %s
              %s%s
              """
                  .formatted(
                      interestedUserName,
                      listing.getType() == BUY
                          ? "wants to sell you their membership capital"
                          : "wants to buy your membership capital",
                      bookValue,
                      totalAmount,
                      listing.getType() == BUY ? "seller's" : "buyer's",
                      contactMessageRequest.addPersonalCode()
                          ? interestedUserName + " (" + interestedUserPersonalCode + ")"
                          : interestedUserName,
                      interestedUserEmail,
                      contactMessageRequest.addPhoneNumber()
                          ? "\n" + interestedUserPhoneNumber
                          : ""))
          .trim();
    }

    return transformMessageNewlines(
            """
            %s %s raamatupidamislikus väärtuses %s € hinnaga %s €.

            Kui soovid tehinguga edasi minna, siis <b>võta palun huvilisega ise ühendust ja lepi kokku detailides</b>: kogus, hind ning kuhu ja millal ostja raha peab kandma. Selleks vasta praegusele kirjale – nii algab teie omavaheline suhtlus. Tulevani need kirjad ei jõua.

            Kui olete liikmekapitali võõrandamises kokku leppinud, siis peab müüja alustama avalduse vormistamist. Kasuta selleks <a href="https://pension.tuleva.ee/capital/listings">Vormistan müügi</a> nuppu Tuleva liikmekapitali võõrandamise lehel. Sul on vaja siis lisaks tehingu detailidele ka ostja isikukoodi.

            Siin on sulle %s andmed:
            %s
            %s%s
            """
                .formatted(
                    interestedUserName,
                    listing.getType() == BUY
                        ? "soovib sulle sulle müüa oma liikmekapitali"
                        : "soovib osta sinu liikmekapitali",
                    bookValue,
                    totalAmount,
                    listing.getType() == BUY ? "müüja" : "ostja",
                    contactMessageRequest.addPersonalCode()
                        ? interestedUserName + " (" + interestedUserPersonalCode + ")"
                        : interestedUserName,
                    interestedUserEmail,
                    contactMessageRequest.addPhoneNumber() ? "\n" + interestedUserPhoneNumber : ""))
        .trim();
  }

  private boolean hasEnoughMemberCapital(User user, NewListingRequest request) {
    if (request.type() == BUY) {
      return true;
    }

    var capitalStatement = capitalService.getCapitalRows(user.getMemberId());
    var totalMemberCapital =
        capitalStatement.stream()
            .filter(event -> event.type() != UNVESTED_WORK_COMPENSATION)
            .map(CapitalRow::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var totalMemberCapitalBeingSold =
        capitalTransferContractService
            .getCapitalBeingSoldInOtherTransfers(user.getMemberOrThrow())
            .values()
            .stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var sellerCapitalAlreadyListed = getSellerListingsBookValueSum(user);

    var totalMemberCapitalAvailableForSale =
        totalMemberCapital
            .subtract(totalMemberCapitalBeingSold)
            .subtract(sellerCapitalAlreadyListed);

    return totalMemberCapitalAvailableForSale.compareTo(request.bookValue()) >= 0;
  }

  private BigDecimal getSellerListingsBookValueSum(User seller) {
    var myActiveSaleListings =
        listingRepository
            .findByExpiryTimeAfterAndMemberIdEquals(clock.instant(), seller.getMemberId())
            .stream()
            .filter(listing -> listing.getType().equals(SELL) && listing.getState().equals(ACTIVE));

    return myActiveSaleListings.map(Listing::getBookValue).reduce(BigDecimal.ZERO, BigDecimal::add);
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
