package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.AWAITING_APPROVAL;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.EXECUTED;
import static java.math.RoundingMode.UNNECESSARY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.UnitTransferInstruction;
import ee.tuleva.onboarding.ledger.UnitTransferQuote;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Plan;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Planned;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Refused;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitTransferService {

  private final SavingsFundLedger savingsFundLedger;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final UnitTransferRepository transfers;
  private final Clock clock;

  public UnitTransferVerdict preview(UnitTransferCommand command) {
    Optional<String> unrecordable =
        whyTheAcquisitionCostCannotBeRecorded(command.recipientAcquisitionCostEur());
    if (unrecordable.isPresent()) {
      return new Refused(unrecordable.get());
    }
    PartyRef from = new PartyRef(command.fromType(), command.fromCode());
    PartyRef to = new PartyRef(command.toType(), command.toCode());
    Optional<String> ineligible = whyTheRecipientCannotHoldUnits(to);
    if (ineligible.isPresent()) {
      return new Refused(ineligible.get());
    }
    try {
      return planned(command, savingsFundLedger.quoteUnitTransfer(from, to, command.fundUnits()));
    } catch (IllegalArgumentException | IllegalStateException cannot) {
      return new Refused(requireNonNullElse(cannot.getMessage(), cannot.toString()));
    }
  }

  @Transactional
  public UnitTransfer submit(UnitTransferCommand command, String confirm, String submittedBy) {
    UnitTransferVerdict verdict = preview(command);
    if (verdict instanceof Refused refused) {
      throw new IllegalStateException("Refusing to submit a transfer: " + refused.refused());
    }
    Planned planned = (Planned) verdict;
    if (!planned.planHash().equals(confirm)) {
      throw new IllegalArgumentException(
          "Confirm does not match the plan; preview again and pass its planHash: planHash="
              + planned.planHash());
    }

    var alreadyAwaiting =
        transfers.findFirstByPlanHashAndStateOrderByCreatedAtAsc(
            planned.planHash(), AWAITING_APPROVAL);
    if (alreadyAwaiting.isPresent()) {
      return alreadyAwaiting.get();
    }

    return transfers.save(
        UnitTransfer.builder()
            .fromPartyCode(command.fromCode())
            .fromPartyType(command.fromType())
            .toPartyCode(command.toCode())
            .toPartyType(command.toType())
            .fundUnits(command.fundUnits())
            .recipientAcquisitionCostEur(inWholeCents(command.recipientAcquisitionCostEur()))
            .notifiedAt(command.notifiedAt())
            .evidence(command.evidence())
            .giverPaidInEur(planned.plan().giverPaidIn())
            .giverUnitsOwned(planned.plan().giverUnitsOwned())
            .planHash(planned.planHash())
            .state(AWAITING_APPROVAL)
            .submittedBy(whoeverIsActing(submittedBy, "submitting it"))
            .build());
  }

  @Transactional
  public UnitTransfer approve(UUID id, String approvedBy) {
    UnitTransfer transfer =
        transfers
            .findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("No such transfer: id=" + id));

    if (!transfer.isAwaitingApproval()) {
      throw new IllegalStateException(
          "Only a transfer awaiting approval can be approved: id="
              + id
              + ", state="
              + transfer.getState());
    }
    String approver = whoeverIsActing(approvedBy, "approving it");
    if (isTheSamePerson(transfer.getSubmittedBy(), approver)) {
      throw new IllegalStateException(
          "A transfer must be approved by someone other than whoever submitted it: id="
              + id
              + ", submittedBy="
              + transfer.getSubmittedBy());
    }

    if (transfers.existsByPlanHashAndState(transfer.getPlanHash(), EXECUTED)) {
      throw new IllegalStateException(
          "A transfer under this plan has already executed: id="
              + id
              + ", planHash="
              + transfer.getPlanHash());
    }

    UnitTransferCommand command = transfer.asCommand();
    if (preview(command) instanceof Refused refused) {
      throw new IllegalStateException(
          "Refusing to approve a transfer the ledger would no longer make: id="
              + id
              + ", refused="
              + refused.refused());
    }

    var recorded =
        savingsFundLedger.recordUnitTransfer(
            new UnitTransferInstruction(
                transfer.from(),
                transfer.to(),
                transfer.getFundUnits(),
                command.recipientAcquisitionCostEur(),
                id));

    transfer.executedBy(approver, recorded.getId(), Instant.now(clock));
    return transfers.save(transfer);
  }

  @Transactional
  public UnitTransfer cancel(UUID id) {
    UnitTransfer transfer =
        transfers
            .findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("No such transfer: id=" + id));
    if (!transfer.isAwaitingApproval()) {
      throw new IllegalStateException(
          "Only a transfer awaiting approval can be cancelled: id="
              + id
              + ", state="
              + transfer.getState());
    }
    transfer.cancelled(Instant.now(clock));
    return transfers.save(transfer);
  }

  public List<UnitTransfer> awaitingApproval() {
    return transfers.findAllByStateOrderByCreatedAtDesc(AWAITING_APPROVAL);
  }

  private static boolean isTheSamePerson(String submittedBy, String approvedBy) {
    return submittedBy.strip().equalsIgnoreCase(approvedBy.strip());
  }

  private static String whoeverIsActing(String actor, String what) {
    if (actor.isBlank()) {
      throw new IllegalArgumentException("A transfer must name who is " + what);
    }
    return actor.strip();
  }

  private static Optional<String> whyTheAcquisitionCostCannotBeRecorded(BigDecimal cost) {
    if (cost.signum() < 0) {
      return Optional.of(
          "The recipient's acquisition cost cannot be negative: recipientAcquisitionCostEur="
              + cost.toPlainString());
    }
    if (cost.stripTrailingZeros().scale() > 2) {
      return Optional.of(
          "The recipient's acquisition cost is held in cents, so it cannot be finer than that:"
              + " recipientAcquisitionCostEur="
              + cost.toPlainString());
    }
    return Optional.empty();
  }

  private Optional<String> whyTheRecipientCannotHoldUnits(PartyRef to) {
    boolean onboarded =
        savingsFundOnboardingService.isOnboardingCompleted(
            to.code(), PartyId.Type.valueOf(to.type().name()));
    return onboarded
        ? Optional.empty()
        : Optional.of(
            "The recipient has not completed savings fund onboarding, so they may not hold units:"
                + " partyCode="
                + to.code());
  }

  private Planned planned(UnitTransferCommand command, UnitTransferQuote quote) {
    Plan plan =
        new Plan(
            command.fromCode(),
            command.toCode(),
            quote.fundUnits(),
            quote.giverUnitsAfter(),
            quote.receiverUnitsAfter(),
            command.recipientAcquisitionCostEur(),
            quote.giverPaidIn(),
            quote.giverUnitsOwned(),
            quote.giverRemainingCost(),
            quote.contributionMoved());
    return new Planned(hashOf(command, plan), plan);
  }

  private String hashOf(UnitTransferCommand command, Plan plan) {
    String canonical =
        Stream.of(
                command.fromType().name(),
                plan.fromCode(),
                command.toType().name(),
                plan.toCode(),
                plan.fundUnits().toPlainString(),
                plan.giverUnitsAfter().toPlainString(),
                plan.receiverUnitsAfter().toPlainString(),
                plan.giverPaidIn().toPlainString(),
                plan.giverUnitsOwned().toPlainString(),
                plan.giverRemainingCost().toPlainString(),
                plan.contributionMoved().toPlainString(),
                command.notifiedAt().toString(),
                command.evidence(),
                inWholeCents(command.recipientAcquisitionCostEur()).toPlainString())
            .map(field -> field.length() + ":" + field)
            .collect(joining("|"));
    return HexFormat.of().formatHex(sha256().digest(canonical.getBytes(UTF_8)));
  }

  private static BigDecimal inWholeCents(BigDecimal cost) {
    return cost.setScale(2, UNNECESSARY);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException never) {
      throw new IllegalStateException("SHA-256 is required of every JRE", never);
    }
  }
}
