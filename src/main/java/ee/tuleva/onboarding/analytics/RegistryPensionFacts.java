package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwner;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerBalanceEmbeddable;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.nudge.PensionRegistry;
import ee.tuleva.onboarding.nudge.PensionRegistrySnapshot;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistryPensionFacts implements PensionRegistry {

  private static final String LEFT_SECOND_PILLAR = "R";
  private static final Set<String> TULEVA_SECOND_PILLAR_FUNDS =
      codesOf(TulevaFund.getPillar2Funds());
  private static final Set<String> TULEVA_THIRD_PILLAR_FUNDS =
      codesOf(TulevaFund.getPillar3Funds());

  private final UnitOwnerRepository unitOwnerRepository;

  @Override
  public Optional<PensionRegistrySnapshot> snapshotFor(String personalCode) {
    return unitOwnerRepository.findInLatestSnapshot(personalCode).map(RegistryPensionFacts::facts);
  }

  private static PensionRegistrySnapshot facts(UnitOwner owner) {
    boolean left = LEFT_SECOND_PILLAR.equals(owner.getP2ravaStatus());
    boolean hasSecondPillar = owner.getP2choice() != null || owner.getP2rate() != null;
    return new PensionRegistrySnapshot(
        hasSecondPillar && !left,
        TULEVA_SECOND_PILLAR_FUNDS.contains(owner.getP2choice())
            || holdsAnyOf(owner, TULEVA_SECOND_PILLAR_FUNDS),
        left,
        new PaymentRates(owner.getP2rate(), owner.getP2nextRate()).canIncrease(),
        owner.getP3identifier() != null
            || owner.getP3identificationDate() != null
            || holdsAnyOf(owner, TULEVA_THIRD_PILLAR_FUNDS));
  }

  private static boolean holdsAnyOf(UnitOwner owner, Set<String> fundCodes) {
    return owner.getBalances() != null
        && owner.getBalances().stream()
            .map(UnitOwnerBalanceEmbeddable::getSecurityShortName)
            .anyMatch(fundCodes::contains);
  }

  private static Set<String> codesOf(List<TulevaFund> funds) {
    return funds.stream().map(TulevaFund::getCode).collect(Collectors.toUnmodifiableSet());
  }
}
