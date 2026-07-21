package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentChildLinkServiceTest {

  private static final String PARENT = "38001010000";
  private static final String CHILD = "61001010000";
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 22);

  @Mock ParentChildLinkRepository parentChildLinkRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private ParentChildLinkService service;

  @BeforeEach
  void setUp() {
    service = new ParentChildLinkService(parentChildLinkRepository, clock);
  }

  @Test
  void findsActivelyRepresentedChildCodesAsOfToday() {
    var link =
        ParentChildLink.builder()
            .parentPersonalCode(PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(LocalDate.of(2030, 1, 1))
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(PARENT, TODAY))
        .willReturn(List.of(link));

    assertThat(service.findActivelyRepresentedChildCodes(PARENT)).containsExactly(CHILD);
  }

  @Test
  void isActiveRepresentationWhenActiveLinkExistsAsOfToday() {
    given(
            parentChildLinkRepository
                .existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, TODAY))
        .willReturn(true);

    assertThat(service.isActiveRepresentation(PARENT, CHILD)).isTrue();
  }

  @Test
  void isNotActiveRepresentationWhenNoActiveLink() {
    assertThat(service.isActiveRepresentation(PARENT, CHILD)).isFalse();
  }

  @Test
  void findsPendingChildrenForParent() {
    var pending =
        ParentChildLink.builder()
            .parentPersonalCode(PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(LocalDate.of(2030, 1, 1))
            .status(ParentChildLinkStatus.PENDING_KYC)
            .build();
    given(
            parentChildLinkRepository
                .findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, ParentChildLinkStatus.PENDING_KYC, TODAY))
        .willReturn(List.of(pending));

    assertThat(service.findPendingChildren(PARENT)).containsExactly(pending);
  }
}
