package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublishedOngoingChargesFigure {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final OngoingChargesFigures ongoingChargesFigures;
  private final Clock clock;

  public <T extends ApiFundResponse> T applyTo(T response) {
    inForceToday(response.getIsin()).ifPresent(response::showPublishedOngoingCharges);
    return response;
  }

  public Optional<PublishedFigure> inForceToday(Fund fund) {
    return inForceToday(fund.getIsin());
  }

  private Optional<PublishedFigure> inForceToday(String isin) {
    return TulevaFund.findByIsin(isin).flatMap(this::figureInForceToday);
  }

  private Optional<PublishedFigure> figureInForceToday(TulevaFund fund) {
    var today = LocalDate.now(clock.withZone(ESTONIAN_ZONE));
    try {
      var figure =
          ongoingChargesFigures
              .figureInForceOn(fund, today)
              .map(PublishedFigure::withoutTrailingZeros);
      if (figure.isEmpty()) {
        log.error(
            "No published ongoing charges figure in force, showing the fund table figure: fund={}, date={}",
            fund,
            today);
      }
      return figure;
    } catch (DataAccessException e) {
      log.error(
          "Published ongoing charges figure lookup failed, showing the fund table figure: fund={}, date={}",
          fund,
          today,
          e);
      return Optional.empty();
    }
  }
}
