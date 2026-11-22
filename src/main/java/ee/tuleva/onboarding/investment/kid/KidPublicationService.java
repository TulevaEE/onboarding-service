package ee.tuleva.onboarding.investment.kid;

import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.kid.KidPublicationRequest.MonthToPublish;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
class KidPublicationService {

  private final KidFunds kidFunds;
  private final KidCalculationRepository calculations;
  private final KidPublicationRepository publications;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  List<KidPublication> publish(TulevaFund fund, KidPublicationRequest request) {
    refuseAMonthNamedTwice(fund, request);
    var publishedAt = clock.instant();
    var toPublish =
        request.months().stream()
            .map(month -> publishable(fund, month, request.publishedBy(), publishedAt))
            .toList();
    try {
      transactionTemplate.executeWithoutResult(
          status -> toPublish.forEach(this::insertUnlessANewerVersionWasStoredMeanwhile));
    } catch (DuplicateKeyException publishedMeanwhile) {
      throw new KidPublicationRefusedException(
          "KID months not published, one was published meanwhile: fund=" + fund,
          publishedMeanwhile);
    }
    log.info(
        "KID months published: fund={}, months={}, publishedBy={}",
        fund,
        toPublish.stream().map(KidPublication::month).toList(),
        request.publishedBy());
    return toPublish;
  }

  Optional<List<PublishedScenario>> publishedScenarioHistory(String isin) {
    return TulevaFund.findByIsin(isin)
        .filter(kidFunds.funds()::containsKey)
        .map(publications::findPublishedScenarios);
  }

  private KidPublication publishable(
      TulevaFund fund, MonthToPublish month, String publishedBy, Instant publishedAt) {
    var calculation =
        calculations
            .findById(month.calculationId())
            .filter(stored -> stored.fund() == fund && stored.month().equals(month.month()))
            .orElseThrow(() -> refused("no such calculation of the month", fund, month));
    var latest = calculations.findLatest(fund, month.month()).orElseThrow();
    if (latest.id() != calculation.id()) {
      throw refused(
          "a newer calculation of the month exists: id=%d".formatted(latest.id()), fund, month);
    }
    if (!calculation.complete()) {
      throw refused("the calculation is incomplete", fund, month);
    }
    if (publications.isPublished(fund, month.month())) {
      throw refused("the month is already published", fund, month);
    }
    return new KidPublication(fund, month.month(), calculation.id(), publishedBy, publishedAt);
  }

  private void insertUnlessANewerVersionWasStoredMeanwhile(KidPublication publication) {
    if (!publications.insertUnlessANewerVersionOfTheMonthExists(publication)) {
      throw new KidPublicationRefusedException(
          "KID months not published, a newer calculation of the month was stored meanwhile:"
              + " fund=%s, month=%s, calculationId=%d"
                  .formatted(publication.fund(), publication.month(), publication.calculationId()));
    }
  }

  private static void refuseAMonthNamedTwice(TulevaFund fund, KidPublicationRequest request) {
    if (request.months().stream().map(MonthToPublish::month).collect(toSet()).size()
        < request.months().size()) {
      throw new InvalidKidRequestException(
          "A month is named more than once: fund=%s, months=%s".formatted(fund, request.months()));
    }
  }

  private static KidPublicationRefusedException refused(
      String why, TulevaFund fund, MonthToPublish month) {
    return new KidPublicationRefusedException(
        "KID month not published, %s: fund=%s, month=%s, calculationId=%s"
            .formatted(why, fund, month.month(), month.calculationId()));
  }
}
