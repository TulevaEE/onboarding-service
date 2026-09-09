package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class KnownLookups {

  private final SecondPillarLeaverStatus leaverStatus;
  private final RecurringContributionStatus recurringStatus;
  private final SavingsFundSaverStatus saverStatus;
  private final TaxHeadroom taxHeadroom;
  private final ActingParties actingParties;

  Known leftSecondPillar(User user) {
    return known("leftSecondPillar", () -> leaverStatus.hasLeft(user.getPersonalCode()));
  }

  Known thirdPillarRecurring(User user) {
    return known("thirdPillarRecurring", () -> recurringStatus.thirdPillar(user.getPersonalCode()));
  }

  Known savingsFundRecurring(NudgeAccount account) {
    return known("savingsFundRecurring", () -> recurringStatus.savingsFund(account));
  }

  Known savesFor(NudgeAccount account) {
    return known("savesFor", () -> saverStatus.savesFor(account));
  }

  Known savesForAnyRepresentedParty(User user, Known ownSaver) {
    if (ownSaver.isYes()) {
      return Known.YES;
    }
    List<Known> others = actingParties.representedBy(user).stream().map(this::savesFor).toList();
    if (others.stream().anyMatch(Known::isYes)) {
      return Known.YES;
    }
    if (!ownSaver.isKnown() || others.stream().anyMatch(known -> !known.isKnown())) {
      return Known.UNKNOWN;
    }
    return Known.NO;
  }

  Known taxHeadroom(User user) {
    return known("taxHeadroom", () -> taxHeadroom.hasHeadroom(user));
  }

  private static Known known(String input, BooleanSupplier lookup) {
    try {
      return Known.of(lookup.getAsBoolean());
    } catch (RuntimeException e) {
      log.warn("Nudge input unavailable, skipping the nudges that need it: input={}", input, e);
      return Known.UNKNOWN;
    }
  }
}
