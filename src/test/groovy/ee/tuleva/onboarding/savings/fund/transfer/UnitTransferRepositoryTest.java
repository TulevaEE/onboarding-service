package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.AWAITING_APPROVAL;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.CANCELLED;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class UnitTransferRepositoryTest {

  @Autowired UnitTransferRepository repository;
  @Autowired TestEntityManager entityManager;

  @Test
  void savesATransferAndReadsBackEverythingItWasGiven() {
    var saved = repository.save(aTransfer().build());
    entityManager.flush();
    entityManager.clear();

    var read = repository.findById(saved.getId()).orElseThrow();

    assertThat(read).usingRecursiveComparison().isEqualTo(saved);
  }

  @Test
  void savingATransferStampsTheTimeItWasCreated() {
    var saved = repository.save(aTransfer().build());
    entityManager.flush();

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void findsTheTransferSubmittedUnderAPlanHash() {
    var saved = repository.save(aTransfer().planHash("the-plan-that-was-shown").build());
    entityManager.flush();
    entityManager.clear();

    var found = repository.findByPlanHashAndState("the-plan-that-was-shown", AWAITING_APPROVAL);

    assertThat(found).get().usingRecursiveComparison().isEqualTo(saved);
  }

  @Test
  void findsNoTransferUnderAPlanHashInAnotherState() {
    repository.save(aTransfer().planHash("the-plan-that-was-shown").build());
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findByPlanHashAndState("the-plan-that-was-shown", CANCELLED)).isEmpty();
  }

  @Test
  void listsTheTransfersAwaitingApprovalNewestFirst() {
    var older = repository.save(aTransfer().planHash("submitted-first").build());
    var newer = repository.save(aTransfer().planHash("submitted-second").build());
    var cancelled = repository.save(aTransfer().planHash("gave-up-on-it").state(CANCELLED).build());
    createdAt(older, Instant.parse("2026-09-14T09:00:00Z"));
    createdAt(newer, Instant.parse("2026-09-15T09:00:00Z"));
    createdAt(cancelled, Instant.parse("2026-09-16T09:00:00Z"));
    entityManager.clear();

    var awaiting = repository.findAllByStateOrderByCreatedAtDesc(AWAITING_APPROVAL);

    assertThat(awaiting)
        .extracting(UnitTransfer::getPlanHash)
        .containsExactly("submitted-second", "submitted-first");
  }

  @Test
  void refusesATransferApprovedByWhoeverSubmittedIt() {
    var selfApproved =
        aTransfer().submittedBy("operator@tuleva.ee").approvedBy("operator@tuleva.ee").build();

    assertThatThrownBy(() -> entityManager.persistAndFlush(selfApproved))
        .isInstanceOf(ConstraintViolationException.class);
  }

  private void createdAt(UnitTransfer transfer, Instant when) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "UPDATE savings_fund_unit_transfer SET created_at = :when WHERE id = :id")
        .setParameter("when", OffsetDateTime.ofInstant(when, UTC))
        .setParameter("id", transfer.getId())
        .executeUpdate();
  }

  private UnitTransfer.UnitTransferBuilder aTransfer() {
    return UnitTransfer.builder()
        .fromPartyCode("38888888888")
        .fromPartyType(PERSON)
        .toPartyCode("39999999999")
        .toPartyType(LEGAL_ENTITY)
        .fundUnits(new BigDecimal("40.00000"))
        .recipientAcquisitionCostEur(new BigDecimal("1000.00"))
        .notifiedAt(LocalDate.parse("2026-09-14"))
        .evidence("Notice by email from the owner, 2026-09-14")
        .planHash("whatever-was-previewed")
        .state(AWAITING_APPROVAL)
        .submittedBy("operator@tuleva.ee");
  }
}
