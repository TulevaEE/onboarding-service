package ee.tuleva.onboarding.conversion;

import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.ConversionResponse.Conversion;
import ee.tuleva.onboarding.pillar.Pillar;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserConversionService {

  private final ConversionHoldings conversionHoldings;
  private final ConversionCashFlows conversionCashFlows;
  private final Clock estonianClock;
  private final PendingMandateApplications pendingMandateApplications;
  private final ThirdPillarRecurringPayments thirdPillarRecurringPayments;

  private final WeightedAverageFeeCalculator weightedAverageFeeCalculator =
      new WeightedAverageFeeCalculator();

  public ConversionResponse getConversion(Person person) {
    List<ConversionHolding> holdings = conversionHoldings.forPerson(person);
    CashFlowTotals cashFlowTotals =
        new CashFlowTotals(conversionCashFlows.forPerson(person), estonianClock);

    var pendingExchanges =
        Stream.concat(getPendingExchanges(2, person), getPendingExchanges(3, person)).toList();
    var weightedAverageFee =
        weightedAverageFeeCalculator.getWeightedAverageFee(holdings, pendingExchanges);
    log.info(
        "Weighted average fee is {} for person {} with {} pending exchanges",
        weightedAverageFee,
        person.getPersonalCode(),
        pendingExchanges.size());

    return ConversionResponse.builder()
        .weightedAverageFee(weightedAverageFee)
        .secondPillar(
            Conversion.builder()
                .selectionComplete(isSelectionComplete(holdings, 2))
                .selectionPartial(isSelectionPartial(holdings, 2))
                .transfersComplete(isTransfersComplete(holdings, 2, person))
                .transfersPartial(isTransfersPartial(holdings, 2, person))
                .pendingWithdrawal(pendingMandateApplications.hasPendingWithdrawals(person, SECOND))
                .contribution(cashFlowTotals.contribution(2))
                .subtraction(cashFlowTotals.subtraction(2))
                .weightedAverageFee(
                    weightedAverageFeeCalculator.getWeightedAverageFee(
                        filter(holdings, 2).toList(), getPendingExchanges(2, person).toList()))
                .build())
        .thirdPillar(
            Conversion.builder()
                .selectionComplete(isSelectionComplete(holdings, 3))
                .selectionPartial(isSelectionPartial(holdings, 3))
                .transfersComplete(isTransfersComplete(holdings, 3, person))
                .transfersPartial(isTransfersPartial(holdings, 3, person))
                .pendingWithdrawal(pendingMandateApplications.hasPendingWithdrawals(person, THIRD))
                .contribution(cashFlowTotals.contribution(3))
                .subtraction(cashFlowTotals.subtraction(3))
                .paymentComplete(cashFlowTotals.paymentComplete(3))
                .recurringPaymentDetected(thirdPillarRecurringPayments.detectedFor(person))
                .weightedAverageFee(
                    weightedAverageFeeCalculator.getWeightedAverageFee(
                        filter(holdings, 3).toList(), getPendingExchanges(3, person).toList()))
                .build())
        .build();
  }

  private static boolean isSelectionComplete(List<ConversionHolding> holdings, int pillar) {
    return filter(holdings, pillar).findFirst().isPresent()
        && filter(holdings, pillar).anyMatch(ConversionHolding::activeContributions)
        && filter(holdings, pillar)
            .filter(ConversionHolding::activeContributions)
            .allMatch(ConversionHolding::ownFund);
  }

  private static boolean isSelectionPartial(List<ConversionHolding> holdings, int pillar) {
    return filter(holdings, pillar)
        .filter(ConversionHolding::activeContributions)
        .anyMatch(ConversionHolding::ownFund);
  }

  private static Stream<ConversionHolding> filter(List<ConversionHolding> holdings, int pillar) {
    return holdings.stream().filter(holding -> holding.pillar() == pillar);
  }

  private boolean isTransfersComplete(List<ConversionHolding> holdings, int pillar, Person person) {
    return getIsinsOfFullPendingTransfersToConvertedFundManager(person, holdings, pillar)
            .containsAll(unConvertedIsins(holdings, pillar))
        && !hasAnyPendingTransfersAwayFromConvertedFundManager(person, pillar);
  }

  private boolean hasAnyPendingTransfersAwayFromConvertedFundManager(Person person, int pillar) {
    return getPendingExchanges(pillar, person)
        .anyMatch(exchange -> exchange.isFromOwnFund() && !exchange.isToOwnFund());
  }

  private boolean isTransfersPartial(List<ConversionHolding> holdings, int pillar, Person person) {
    return filter(holdings, pillar).findFirst().isEmpty()
        || hasAnyValueInOwnFundsWithNoPendingTransfersAway(holdings, pillar, person)
        || hasAnyPendingTransfersToOwnFunds(person, pillar);
  }

  private boolean hasAnyValueInOwnFundsWithNoPendingTransfersAway(
      List<ConversionHolding> holdings, int pillar, Person person) {
    var fullyAwayIsins =
        getIsinsOfFullPendingTransfersAwayFromConvertedFundManager(person, holdings, pillar);
    return filter(holdings, pillar)
        .filter(ConversionHolding::hasAnyValue)
        .anyMatch(holding -> holding.ownFund() && !fullyAwayIsins.contains(holding.isin()));
  }

  private boolean hasAnyPendingTransfersToOwnFunds(Person person, int pillar) {
    return getPendingExchanges(pillar, person).anyMatch(PendingExchange::isToOwnFund);
  }

  private Set<String> getIsinsOfFullPendingTransfersToConvertedFundManager(
      Person person, List<ConversionHolding> holdings, int pillar) {
    return getPendingExchanges(pillar, person)
        .filter(exchange -> exchange.isToOwnFund() && amountMatches(exchange, holdings))
        .map(PendingExchange::getSourceIsin)
        .collect(toSet());
  }

  private Set<String> getIsinsOfFullPendingTransfersAwayFromConvertedFundManager(
      Person person, List<ConversionHolding> holdings, int pillar) {
    return getPendingExchanges(pillar, person)
        .filter(exchange -> exchange.isFromOwnFund() && amountMatches(exchange, holdings))
        .map(PendingExchange::getSourceIsin)
        .collect(toSet());
  }

  private Stream<PendingExchange> getPendingExchanges(int pillar, Person person) {
    return pendingMandateApplications.getPendingExchanges(Pillar.fromInt(pillar), person).stream();
  }

  private static boolean amountMatches(PendingExchange exchange, List<ConversionHolding> holdings) {
    if (exchange.getPillar() == 2) {
      return exchange.isFullAmount();
    }
    if (exchange.getPillar() == 3) {
      ConversionHolding holding = holding(exchange, holdings);
      return exchange.isFullAmount(holding.units());
    }
    throw new IllegalStateException("Invalid pillar: " + exchange.getPillar());
  }

  private static ConversionHolding holding(
      PendingExchange exchange, List<ConversionHolding> holdings) {
    return holdings.stream()
        .filter(holding -> exchange.getSourceIsin().equals(holding.isin()))
        .findFirst()
        .orElseGet(
            () ->
                new ConversionHolding(
                    exchange.getPillar(),
                    exchange.getSourceIsin(),
                    false,
                    false,
                    false,
                    ZERO,
                    ZERO,
                    ZERO));
  }

  private static List<String> unConvertedIsins(List<ConversionHolding> holdings, int pillar) {
    return filter(holdings, pillar)
        .filter(holding -> !holding.ownFund() && holding.hasAnyValue() && !holding.exitRestricted())
        .map(ConversionHolding::isin)
        .collect(toList());
  }
}
