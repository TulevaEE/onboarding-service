package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.event.TrackableEventType.CONTACT_DETAILS_CHANGE;
import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactDetailsChanged;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ContactDetailsChangedBroadcasterTest {

  private static final Instant CHANGED_AT = Instant.parse("2026-11-18T10:00:00Z");

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private ClientConnection clientConnection;
  @InjectMocks private ContactDetailsChangedBroadcaster broadcaster;

  @Test
  void broadcastsWhichFieldsChangedAndFromWhereWithoutTheContactValues() {
    var user = sampleUserNonMember().build();
    given(clientConnection.ipAddress()).willReturn(Optional.of("203.0.113.7"));
    given(clientConnection.userAgent()).willReturn(Optional.of("Mozilla/5.0"));

    broadcaster.onContactDetailsChanged(
        new ContactDetailsChanged(
            user,
            SELF_SERVICE,
            List.of(
                new ContactChange(EMAIL, "old@example.com", "new@example.com", CHANGED_AT),
                new ContactChange(PHONE, "+37255555555", null, CHANGED_AT))));

    then(eventPublisher)
        .should()
        .publishEvent(
            new TrackableEvent(
                user,
                CONTACT_DETAILS_CHANGE,
                Map.of(
                    "fields", List.of("EMAIL", "PHONE"),
                    "source", "SELF_SERVICE",
                    "ipAddress", "203.0.113.7",
                    "userAgent", "Mozilla/5.0")));
  }

  @Test
  void broadcastsWithoutAnOriginWhenNotOnARequestThread() {
    var user = sampleUserNonMember().build();
    given(clientConnection.ipAddress()).willReturn(Optional.empty());
    given(clientConnection.userAgent()).willReturn(Optional.empty());

    broadcaster.onContactDetailsChanged(
        new ContactDetailsChanged(
            user,
            SELF_SERVICE,
            List.of(new ContactChange(EMAIL, "old@example.com", "new@example.com", CHANGED_AT))));

    then(eventPublisher)
        .should()
        .publishEvent(
            new TrackableEvent(
                user,
                CONTACT_DETAILS_CHANGE,
                Map.of("fields", List.of("EMAIL"), "source", "SELF_SERVICE")));
  }
}
