package ee.tuleva.onboarding.savings.fund.redemption.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_PERSON;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.PreviousContactNotices;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionEmailSenderTest {

  private static final long USER_ID = 999L;
  private static final Instant REQUESTED_AT = Instant.parse("2025-09-29T15:00:00Z");
  private static final List<String> TAGS = List.of("savings_fund", "redemption");
  private static final Map<String, Object> MERGE_VARS = Map.of("fname", "Jordan");
  private static final String TEMPLATE = "savings_fund_redemption_person_en";

  @Mock private UserService userService;
  @Mock private LocaleService localeService;
  @Mock private RedemptionConfirmationMapper confirmationMapper;
  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private PreviousContactNotices previousContactNotices;

  @InjectMocks private RedemptionEmailSender sender;

  private final User requester =
      sampleUserNonMember().id(USER_ID).email("current@example.com").build();
  private final RedemptionRequestedEvent event =
      new RedemptionRequestedEvent(
          UUID.fromString("11111111-1111-1111-1111-111111111111"),
          USER_ID,
          new PartyId(PERSON, requester.getPersonalCode()),
          new BigDecimal("250.00"),
          new BigDecimal("231.48148"),
          "EE471000001020145685",
          REQUESTED_AT,
          Instant.parse("2025-09-30T13:00:00Z"),
          Instant.parse("2025-10-01T13:00:00Z"));

  @Test
  void confirmsTheRequestAtTheRequestersCurrentAddressInTheirLanguageAndRecordsIt() {
    var message = givenAConfirmationFor(requester);
    given(emailService.send(requester, message, TEMPLATE))
        .willReturn(Optional.of(accepted("confirmation")));

    sender.onRedemptionRequested(event);

    then(emailPersistenceService)
        .should()
        .save(requester, "confirmation", SAVINGS_FUND_REDEMPTION_PERSON, "sent");
  }

  @Test
  void aConfirmationThatIsSentButCannotBeRecordedDoesNotFailTheRedemption() {
    var message = givenAConfirmationFor(requester);
    given(emailService.send(requester, message, TEMPLATE))
        .willReturn(Optional.of(accepted("confirmation")));
    willThrow(new IllegalStateException())
        .given(emailPersistenceService)
        .save(requester, "confirmation", SAVINGS_FUND_REDEMPTION_PERSON, "sent");

    assertThatCode(() -> sender.onRedemptionRequested(event)).doesNotThrowAnyException();

    then(previousContactNotices).should().sendActivityNotice(requester, ENGLISH, REQUESTED_AT);
  }

  @Test
  void aConfirmationMandrillDidNotAcceptIsNotRecorded() {
    var message = givenAConfirmationFor(requester);
    given(emailService.send(requester, message, TEMPLATE)).willReturn(Optional.empty());

    sender.onRedemptionRequested(event);

    then(emailPersistenceService).shouldHaveNoInteractions();
  }

  @Test
  void noticesTheAddressesReplacedBeforeTheRequestAsOfTheRequestTime() {
    givenAConfirmationFor(requester);

    sender.onRedemptionRequested(event);

    then(previousContactNotices).should().sendActivityNotice(requester, ENGLISH, REQUESTED_AT);
  }

  @Test
  void noticesTheReplacedAddressesBeforeConfirmingSoTheSecurityNoticeNeverWaitsBehindIt() {
    var message = givenAConfirmationFor(requester);

    sender.onRedemptionRequested(event);

    InOrder inOrder = inOrder(previousContactNotices, emailService);
    then(previousContactNotices)
        .should(inOrder)
        .sendActivityNotice(requester, ENGLISH, REQUESTED_AT);
    then(emailService).should(inOrder).send(requester, message, TEMPLATE);
  }

  @Test
  void aConfirmationThatCannotBeComposedStillLetsTheReplacedAddressesBeNoticed() {
    given(userService.getByIdOrThrow(USER_ID)).willReturn(requester);
    given(localeService.getCurrentLocale()).willReturn(ENGLISH);
    given(confirmationMapper.map(requester, event, ENGLISH)).willThrow(new IllegalStateException());

    assertThatCode(() -> sender.onRedemptionRequested(event)).doesNotThrowAnyException();

    then(previousContactNotices).should().sendActivityNotice(requester, ENGLISH, REQUESTED_AT);
    then(emailService).shouldHaveNoInteractions();
  }

  @Test
  void aNoticeThatFailsDoesNotFailTheRedemptionNorKeepTheConfirmationFromBeingSent() {
    var message = givenAConfirmationFor(requester);
    willThrow(new IllegalStateException())
        .given(previousContactNotices)
        .sendActivityNotice(requester, ENGLISH, REQUESTED_AT);

    assertThatCode(() -> sender.onRedemptionRequested(event)).doesNotThrowAnyException();

    then(emailService).should().send(requester, message, TEMPLATE);
  }

  @Test
  void aRequesterWhoCannotBeLoadedFailsNothingAndIsSentNothing() {
    given(userService.getByIdOrThrow(USER_ID)).willThrow(new IllegalStateException());

    assertThatCode(() -> sender.onRedemptionRequested(event)).doesNotThrowAnyException();

    then(emailService).shouldHaveNoInteractions();
    then(previousContactNotices).shouldHaveNoInteractions();
  }

  private MandrillMessage givenAConfirmationFor(User user) {
    var message = new MandrillMessage();
    given(userService.getByIdOrThrow(USER_ID)).willReturn(user);
    given(localeService.getCurrentLocale()).willReturn(ENGLISH);
    given(confirmationMapper.map(user, event, ENGLISH))
        .willReturn(new RedemptionConfirmation(SAVINGS_FUND_REDEMPTION_PERSON, MERGE_VARS));
    given(emailService.newMandrillMessage(user.getEmail(), TEMPLATE, MERGE_VARS, TAGS))
        .willReturn(message);
    return message;
  }

  private static MandrillMessageStatus accepted(String id) {
    return LutungGsonUtils.getGson()
        .fromJson("{\"_id\": \"" + id + "\", \"status\": \"sent\"}", MandrillMessageStatus.class);
  }
}
