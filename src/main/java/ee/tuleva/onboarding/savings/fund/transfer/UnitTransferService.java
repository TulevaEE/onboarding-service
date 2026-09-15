package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.AWAITING_APPROVAL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.UnitTransferQuote;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Plan;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Planned;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Refused;
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
    Optional<String> ineligible = whyTheRecipientCannotHoldUnits(command.to());
    if (ineligible.isPresent()) {
      return new Refused(ineligible.get());
    }
    try {
      return planned(
          command,
          savingsFundLedger.quoteUnitTransfer(command.from(), command.to(), command.fundUnits()));
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

    var alreadyAwaiting = transfers.findByPlanHashAndState(planned.planHash(), AWAITING_APPROVAL);
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
            .recipientAcquisitionCostEur(command.recipientAcquisitionCostEur())
            .notifiedAt(command.notifiedAt())
            .evidence(command.evidence())
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
    if (isTheSamePerson(transfer.getSubmittedBy(), approvedBy)) {
      throw new IllegalStateException(
          "A transfer must be approved by someone other than whoever submitted it: id="
              + id
              + ", submittedBy="
              + transfer.getSubmittedBy());
    }

    var recorded =
        savingsFundLedger.recordUnitTransfer(
            transfer.from(), transfer.to(), transfer.getFundUnits(), id);

    transfer.executedBy(
        whoeverIsActing(approvedBy, "approving it"), recorded.getId(), Instant.now(clock));
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

  /**
   * Whoever holds the token names themselves, so the two people are only two people if the names
   * cannot be made to differ trivially. Compared ignoring case and surrounding space, in case the
   * identity ever arrives as an address rather than an opaque subject.
   */
  private static boolean isTheSamePerson(String submittedBy, String approvedBy) {
    return submittedBy.strip().equalsIgnoreCase(approvedBy.strip());
  }

  private static String whoeverIsActing(String actor, String what) {
    if (actor.isBlank()) {
      throw new IllegalArgumentException("A transfer must name who is " + what);
    }
    return actor.strip();
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
            quote.giverUnitsOwned());
    return new Planned(hashOf(command, plan), plan);
  }

  /**
   * Binds the approval to the exact numbers the operator was shown. Anything that changes what the
   * transfer does changes the hash, so a stale confirm cannot be submitted. Each field carries its
   * length because evidence is free text: without that, a value containing the separator could be
   * read as two fields and two different transfers could hash alike.
   */
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
                command.notifiedAt().toString(),
                command.evidence(),
                String.valueOf(command.recipientAcquisitionCostEur()))
            .map(field -> field.length() + ":" + field)
            .collect(joining("|"));
    return HexFormat.of().formatHex(sha256().digest(canonical.getBytes(UTF_8)));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException never) {
      throw new IllegalStateException("SHA-256 is required of every JRE", never);
    }
  }
}
