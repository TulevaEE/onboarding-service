package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_PERSON;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AmlAlertNotifierTest {

  private OperationsNotificationService notificationService;
  private UserService userService;
  private AmlAlertNotifier notifier;

  @BeforeEach
  void setUp() {
    notificationService = Mockito.mock(OperationsNotificationService.class);
    userService = Mockito.mock(UserService.class);
    notifier = new AmlAlertNotifier(notificationService, userService);
  }

  @Test
  @DisplayName("sends an AML alert with the resolved Tuleva userId to the AML channel")
  void onAmlThresholdAlert_resolvesUserId_sendsToAmlChannel() {
    User user = sampleUser().build();
    when(userService.findByPersonalCode("38001010000")).thenReturn(Optional.of(user));

    notifier.onAmlThresholdAlert(
        new AmlThresholdAlertEvent(
            this, III_PILLAR_DEPOSIT_PERSON, "38001010000", new BigDecimal("6001.00"), "7"));

    verify(notificationService)
        .sendMessage(
            "AML alert: III_PILLAR_DEPOSIT_PERSON, userId=%s, amount=6001.00, ref=7"
                .formatted(user.getId()),
            AML);
  }

  @Test
  @DisplayName("keeps the personal id out of Slack and shows userId=null when no user is found")
  void onAmlThresholdAlert_noUser_userIdNull() {
    when(userService.findByPersonalCode("38001010000")).thenReturn(Optional.empty());

    notifier.onAmlThresholdAlert(
        new AmlThresholdAlertEvent(
            this, III_PILLAR_DEPOSIT_PERSON, "38001010000", new BigDecimal("6001.00"), "7"));

    verify(notificationService)
        .sendMessage(
            "AML alert: III_PILLAR_DEPOSIT_PERSON, userId=null, amount=6001.00, ref=7", AML);
  }

  @Test
  @DisplayName("does not silently swallow a Slack send failure")
  void onAmlThresholdAlert_whenSlackFails_propagatesException() {
    when(userService.findByPersonalCode(any())).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("Slack down"))
        .when(notificationService)
        .sendMessage(any(), any());

    assertThatThrownBy(
            () ->
                notifier.onAmlThresholdAlert(
                    new AmlThresholdAlertEvent(
                        this,
                        III_PILLAR_DEPOSIT_PERSON,
                        "38001010000",
                        new BigDecimal("6001.00"),
                        "7")))
        .isInstanceOf(IllegalStateException.class);
  }
}
