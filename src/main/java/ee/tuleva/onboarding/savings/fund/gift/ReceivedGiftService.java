package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReceivedGiftService {

  private final SavingFundPaymentRepository payments;
  private final GiftMessageRepository giftMessages;
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

    var messagesByDescription = messagesByDescription(arrived);

    return arrived.stream()
        .map(
            payment ->
                new ReceivedGift(
                    payment.getCreatedAt(),
                    payment.getAmount(),
                    payment.getRemitterName(),
                    messagesByDescription.get(payment.getDescription()),
                    hasReachedTheAccount(payment)))
        .sorted(comparing(ReceivedGift::receivedAt, Comparator.reverseOrder()))
        .toList();
  }

  private static boolean hasReachedTheAccount(SavingFundPayment payment) {
    return switch (payment.getStatus()) {
      case VERIFIED, RESERVED, ISSUED, PROCESSED -> true;
      default -> false;
    };
  }

  // Two gifts to the same child in the same second share a description, so rather than guess which
  // message belongs to which payment, neither gets one.
  private Map<String, String> messagesByDescription(List<SavingFundPayment> arrived) {
    var descriptions = arrived.stream().map(SavingFundPayment::getDescription).distinct().toList();
    return giftMessages.findByDescriptionIn(descriptions).stream()
        .collect(groupingBy(GiftMessage::getDescription))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue().size() == 1)
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().getFirst().getMessage()));
  }
}
