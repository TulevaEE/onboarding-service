package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReceivedGiftService {

  private final SavingFundPaymentRepository payments;
  private final GiftRepository gifts;
  private final ParentChildLinkService parentChildLinks;

  public List<ReceivedGift> receivedGifts(String parentPersonalCode, String childPersonalCode) {
    if (!parentChildLinks.isActiveRepresentation(parentPersonalCode, childPersonalCode)) {
      throw new NotAllowedToGiftForException(childPersonalCode);
    }
    var arrived =
        payments.findPayments(new PartyId(PERSON, childPersonalCode)).stream()
            .filter(payment -> payment.getStatus() != TO_BE_RETURNED)
            .filter(payment -> payment.getStatus() != RETURNED)
            .toList();

    var recorded = gifts.findByDescriptionIn(descriptionsOf(arrived));
    var startedThroughALink = recorded.stream().map(Gift::getDescription).collect(toSet());
    var messages = unambiguousMessages(recorded);

    return arrived.stream()
        .filter(payment -> isAGift(payment, startedThroughALink, childPersonalCode))
        .map(
            payment ->
                new ReceivedGift(
                    payment.getCreatedAt(),
                    payment.getAmount(),
                    payment.getRemitterName(),
                    messages.get(payment.getDescription()),
                    hasReachedTheAccount(payment)))
        .sorted(comparing(ReceivedGift::receivedAt, Comparator.reverseOrder()))
        .toList();
  }

  /**
   * A gift and the parent's own deposit both arrive as third-party money into the child's account,
   * so the payment alone cannot tell them apart. Either it was started through the link, or it came
   * from somebody who is not acting for this child.
   */
  private boolean isAGift(
      SavingFundPayment payment, Set<String> startedThroughALink, String childPersonalCode) {
    if (startedThroughALink.contains(payment.getDescription())) {
      return true;
    }
    var remitter = payment.getRemitterIdCode();
    if (remitter == null) {
      return false;
    }
    return !remitter.equals(childPersonalCode)
        && !parentChildLinks.isGuardian(remitter, childPersonalCode);
  }

  private static boolean hasReachedTheAccount(SavingFundPayment payment) {
    return switch (payment.getStatus()) {
      case VERIFIED, RESERVED, ISSUED, PROCESSED -> true;
      default -> false;
    };
  }

  private static List<String> descriptionsOf(List<SavingFundPayment> arrived) {
    return arrived.stream().map(SavingFundPayment::getDescription).distinct().toList();
  }

  // Two gifts to the same child in the same second share a description. They are both still gifts,
  // but neither can claim the words, so that description carries no message.
  private static Map<String, String> unambiguousMessages(List<Gift> recorded) {
    return recorded.stream().collect(groupingBy(Gift::getDescription)).values().stream()
        .filter(sharingADescription -> sharingADescription.size() == 1)
        .map(List::getFirst)
        .filter(gift -> gift.getMessage() != null)
        .collect(toMap(Gift::getDescription, gift -> requireNonNull(gift.getMessage())));
  }
}
