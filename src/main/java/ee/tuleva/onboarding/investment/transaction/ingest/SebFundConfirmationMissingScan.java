package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

record SebFundConfirmationMissingScan(
    List<MissingSebFundConfirmation> missing, List<LocalDate> incompleteReportDates) {

  private static final DateTimeFormatter REPORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  Optional<SebFundConfirmationCheckFailure> incompleteReportsFailure() {
    if (incompleteReportDates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new SebFundConfirmationCheckFailure(
            "SEB ootel tehingute raportite lugemine",
            "lugemata ridu raportites "
                + incompleteReportDates.stream().map(REPORT_DATE::format).collect(joining(", "))));
  }
}
