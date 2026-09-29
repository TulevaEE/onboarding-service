package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.CREATED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.gift.GiftLinkFixture.aReplacedLink;
import static ee.tuleva.onboarding.savings.fund.gift.GiftLinkFixture.anOpenLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPayment.Status;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceivedGiftServiceTest {

  private static final String PARENT = "38888888888";
  private static final String CHILD = "61001010000";
  private static final String CO_PARENT = "48002020009";
  private static final String GRANDPARENT = "39999999999";
  private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

  @Mock SavingFundPaymentRepository payments;
  @Mock GiftRepository gifts;
  @Mock ParentChildLinkService parentChildLinks;
  @Mock GiftLinkRepository giftLinks;

  ReceivedGiftService service;

  @BeforeEach
  void setUp() {
    service = new ReceivedGiftService(payments, gifts, parentChildLinks, giftLinks);
  }

  @Test
  void aStrangerCannotReadWhatAChildReceived() {
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(false);

    assertThatThrownBy(() -> service.receivedGifts(PARENT, CHILD))
        .isInstanceOf(NotAllowedToGiftForException.class);
  }

  @Test
  void readsWhatTheChildALinkWasMadeForReceived() {
    givenParentRepresentsChild();
    var link = anOpenLink(CHILD, PARENT);
    given(giftLinks.findById(link.getId())).willReturn(Optional.of(link));
    givenPayments(payment("from-grandma", GRANDPARENT, "Leida Tamm", VERIFIED));
    given(parentChildLinks.isGuardian(GRANDPARENT, CHILD)).willReturn(false);
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, link.getId()))
        .singleElement()
        .satisfies(gift -> assertThat(gift.giverName()).isEqualTo("Leida Tamm"));
  }

  @Test
  void stillReadsTheGiftsThroughALinkTheParentHasSinceReplaced() {
    givenParentRepresentsChild();
    var replaced = aReplacedLink(CHILD, PARENT);
    given(giftLinks.findById(replaced.getId())).willReturn(Optional.of(replaced));
    givenPayments(payment("from-grandma", GRANDPARENT, "Leida Tamm", VERIFIED));
    given(parentChildLinks.isGuardian(GRANDPARENT, CHILD)).willReturn(false);
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, replaced.getId())).hasSize(1);
  }

  @Test
  void holdingALinkIdDoesNotLetAStrangerReadWhatTheChildReceived() {
    var link = anOpenLink(CHILD, PARENT);
    given(giftLinks.findById(link.getId())).willReturn(Optional.of(link));
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(false);

    assertThatThrownBy(() -> service.receivedGifts(PARENT, link.getId()))
        .isInstanceOf(NotAllowedToGiftForException.class);
  }

  @Test
  void aLinkThatIsNotThereHasNoGiftsToRead() {
    var id = UUID.randomUUID();
    given(giftLinks.findById(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.receivedGifts(PARENT, id))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void leavesOutTheParentsOwnDeposit() {
    givenParentRepresentsChild();
    var own = payment("own-deposit", PARENT, "Kristjan Tamm", VERIFIED);
    givenPayments(own);
    given(parentChildLinks.isGuardian(PARENT, CHILD)).willReturn(true);
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, CHILD)).isEmpty();
  }

  @Test
  void leavesOutTheChildsOwnMoney() {
    givenParentRepresentsChild();
    givenPayments(payment("childs-own", CHILD, "Mari Tamm", VERIFIED));
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, CHILD)).isEmpty();
  }

  @Test
  void leavesOutTheDepositOfAParentWhoseOwnKycHasNotClearedYet() {
    givenParentRepresentsChild();
    givenPayments(payment("co-parent-deposit", CO_PARENT, "Kristjan Tamm", VERIFIED));
    given(parentChildLinks.isGuardian(CO_PARENT, CHILD)).willReturn(true);
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, CHILD)).isEmpty();
  }

  @Test
  void showsATransferFromSomebodyWhoDoesNotActForTheChild() {
    givenParentRepresentsChild();
    givenPayments(payment("from-grandma", GRANDPARENT, "Leida Tamm", VERIFIED));
    given(parentChildLinks.isGuardian(GRANDPARENT, CHILD)).willReturn(false);
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, CHILD))
        .singleElement()
        .satisfies(
            gift -> {
              assertThat(gift.giverName()).isEqualTo("Leida Tamm");
              assertThat(gift.confirmed()).isTrue();
            });
  }

  @Test
  void showsALinkPaymentBeforeTheBankHasSaidWhoPaid() {
    givenParentRepresentsChild();
    givenPayments(payment("through-the-link", null, null, CREATED));
    given(gifts.findByDescriptionIn(any()))
        .willReturn(List.of(gift("through-the-link", "Palju õnne!")));

    assertThat(service.receivedGifts(PARENT, CHILD))
        .singleElement()
        .satisfies(
            gift -> {
              assertThat(gift.giverName()).isNull();
              assertThat(gift.message()).isEqualTo("Palju õnne!");
              assertThat(gift.confirmed()).isFalse();
            });
  }

  @Test
  void leavesOutMoneyOnItsWayBackOut() {
    givenParentRepresentsChild();
    givenPayments(payment("returned", GRANDPARENT, "Leida Tamm", RETURNED));
    given(gifts.findByDescriptionIn(any())).willReturn(List.of());

    assertThat(service.receivedGifts(PARENT, CHILD)).isEmpty();
  }

  @Test
  void showsBothGiftsButNeitherMessageWhenTwoShareADescription() {
    givenParentRepresentsChild();
    givenPayments(
        payment("same-second", null, null, VERIFIED), payment("same-second", null, null, VERIFIED));
    given(gifts.findByDescriptionIn(any()))
        .willReturn(
            List.of(gift("same-second", "From Grandma"), gift("same-second", "From Uncle")));

    assertThat(service.receivedGifts(PARENT, CHILD))
        .hasSize(2)
        .allSatisfy(gift -> assertThat(gift.message()).isNull());
  }

  @Test
  void showsNoMessageWhenOnlyOneOfTwoGiftsSharingADescriptionCarriesWords() {
    givenParentRepresentsChild();
    givenPayments(
        payment("same-second", null, null, VERIFIED), payment("same-second", null, null, VERIFIED));
    given(gifts.findByDescriptionIn(any()))
        .willReturn(List.of(gift("same-second", "From Grandma"), gift("same-second", null)));

    assertThat(service.receivedGifts(PARENT, CHILD))
        .hasSize(2)
        .allSatisfy(gift -> assertThat(gift.message()).isNull());
  }

  private void givenPayments(SavingFundPayment... found) {
    given(payments.findPayments(any())).willReturn(List.of(found));
  }

  private void givenParentRepresentsChild() {
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(true);
  }

  private static SavingFundPayment payment(
      String description,
      @Nullable String remitterIdCode,
      @Nullable String remitterName,
      Status status) {
    return SavingFundPayment.builder()
        .id(UUID.randomUUID())
        .amount(new BigDecimal("50.00"))
        .description(description)
        .remitterIdCode(remitterIdCode)
        .remitterName(remitterName)
        .createdAt(NOW)
        .status(status)
        .build();
  }

  private static Gift gift(String description, @Nullable String message) {
    return Gift.builder()
        .giftLinkId(UUID.randomUUID())
        .description(description)
        .amount(new BigDecimal("50.00"))
        .message(message)
        .createdAt(NOW)
        .build();
  }
}
