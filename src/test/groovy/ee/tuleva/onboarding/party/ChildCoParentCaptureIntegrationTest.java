package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ChildCoParentCaptureIntegrationTest {

  private static final String PARENT = "48503150000";
  private static final String CO_PARENT = "38002020008";
  private static final String CHILD = "61506150006";

  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ApplicationEventPublisher applicationEventPublisher;
  @Autowired private ParentChildLinkRepository parentChildLinkRepository;
  @Autowired private UserRepository userRepository;

  @MockitoBean private CustodyVerificationService custodyVerificationService;

  @AfterEach
  void cleanUp() {
    parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            CO_PARENT, CHILD, LEGAL_REPRESENTATIVE)
        .ifPresent(parentChildLinkRepository::delete);
    userRepository.findByPersonalCode(CHILD).ifPresent(userRepository::delete);
  }

  // The listener fires AFTER_COMMIT, where a REQUIRED transaction would join the already-committed
  // onboarding transaction and the pending link would silently never be persisted — this test
  // fails unless registerPending opens its own transaction (REQUIRES_NEW).
  @Test
  void persistsThePendingCoParentLinkAfterTheOnboardingTransactionCommits() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT));

    transactionTemplate.executeWithoutResult(
        status ->
            applicationEventPublisher.publishEvent(
                new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas")));

    ParentChildLink saved =
        parentChildLinkRepository
            .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                CO_PARENT, CHILD, LEGAL_REPRESENTATIVE)
            .orElseThrow();
    assertThat(saved.isPending()).isTrue();
    assertThat(userRepository.findByPersonalCode(CHILD)).isPresent();
  }
}
