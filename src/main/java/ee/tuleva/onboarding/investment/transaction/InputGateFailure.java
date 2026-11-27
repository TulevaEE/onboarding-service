package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.report.ReportType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

sealed interface InputGateFailure {

  String slackLine();

  record StalePositionReport(LocalDate latestReportDate, LocalDate requiredReportDate)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return "SEB positsiooniraport on %s, vaja %s: küsi %s raport SEB-lt"
          .formatted(latestReportDate, requiredReportDate, requiredReportDate);
    }
  }

  record MissingPositionReport(LocalDate requiredReportDate) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "SEB positsiooniraport puudub, vaja %s: küsi %s raport SEB-lt"
          .formatted(requiredReportDate, requiredReportDate);
    }
  }

  record MissingR45(LocalDate reportDate, LocalDate latestReportDate) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "R45 %s puudub (viimane %s): lae R45 EPIS-ist".formatted(reportDate, latestReportDate);
    }
  }

  record NoR45Loaded(LocalDate reportDate) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "R45 %s puudub: lae R45 EPIS-ist".formatted(reportDate);
    }
  }

  record MissingPevaRavaReports(
      List<ReportType> missingReports, LocalDate executionDate, LocalDate countedFrom)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      String reports =
          missingReports.stream().map(MissingPevaRavaReports::reportName).collect(joining(" ja "));
      String verb = missingReports.size() == 1 ? "puudub" : "puuduvad";
      return "%s %s (PEVA/RAVA täitmine %s, vood arvesse alates %s): lae EPIS-ist"
          .formatted(reports, verb, executionDate, countedFrom);
    }

    private static String reportName(ReportType reportType) {
      if (reportType == R17_PEVA) {
        return "R17";
      }
      if (reportType == R21_RAVA) {
        return "R21";
      }
      throw new IllegalArgumentException("Not a PEVA/RAVA report type: reportType=" + reportType);
    }
  }

  record MissingPrices(List<String> isins) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "Hind puudub: %s: kontrolli hinnaallikaid ja instrument_reference'it"
          .formatted(String.join(", ", isins));
    }
  }

  record ManualPriceDeviates(
      List<PriceDeviation> deviations, List<PriceDeviationConfirmation> confirmationsAtAnotherPrice)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return deviations.stream()
          .map(
              deviation ->
                  ("⚠️ %s: käsitsi hind %s, viimane indeksihind %s (%s), erinevus %s%%%s: kontrolli"
                          + " hinda või kinnita see (confirmedPriceDeviations)")
                      .formatted(
                          deviation.isin(),
                          deviation.manualPrice().stripTrailingZeros().toPlainString(),
                          deviation.lastIndexPrice().stripTrailingZeros().toPlainString(),
                          deviation.lastIndexPriceDate(),
                          deviation.gapPercent(),
                          confirmedPriceOf(deviation)))
          .collect(joining("\n"));
    }

    private String confirmedPriceOf(PriceDeviation deviation) {
      List<String> prices =
          confirmationsAtAnotherPrice.stream()
              .filter(confirmation -> confirmation.isin().equals(deviation.isin()))
              .map(confirmation -> confirmation.price().stripTrailingZeros().toPlainString())
              .toList();
      return switch (prices.size()) {
        case 0 -> "";
        case 1 -> ", kinnitus on hinnale " + prices.getFirst();
        default -> ", kinnitused on hindadele " + String.join(", ", prices);
      };
    }
  }

  record MissingIndexLevels(List<MissingIndexLevel> levels) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "Indeksi tase puudub: %s: oota MSCI taset või vali teine hinnapäeva reegel"
          .formatted(
              levels.stream()
                  .map(
                      level -> "%s (%s %s)".formatted(level.isin(), level.indexKey(), level.date()))
                  .collect(joining(", ")));
    }
  }

  record NoFundPriceOnPositionDate(List<String> isins, LocalDate positionDate)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return "Fondi hind puudub positsiooniraporti päevaks %s: %s: kontrolli hinnaallikaid või vali teine hinnapäeva reegel"
          .formatted(positionDate, String.join(", ", isins));
    }
  }

  record NoCommonPriceDate(LocalDate from, LocalDate to) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "Ühist hinnapäeva pole (%s–%s): kontrolli hinnaallikaid või kasuta igaühe värskeimat hinda"
          .formatted(from, to);
    }
  }

  record HoldingMismatch(
      String isin,
      LocalDate positionDate,
      @Nullable BigDecimal registryQuantity,
      @Nullable BigDecimal reportQuantity)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return "%s: registris %s, SEB raportis %s (%s): kontrolli registri tehinguid"
          .formatted(isin, shown(registryQuantity), shown(reportQuantity), positionDate);
    }

    private static String shown(@Nullable BigDecimal quantity) {
      return quantity == null ? "puudub" : quantity.stripTrailingZeros().toPlainString();
    }
  }

  record NoRegistryHoldings(LocalDate positionDate) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "Registris pole %s positsioone: kontrolli portfelli baseline'i ja cost-basis tööd"
          .formatted(positionDate);
    }
  }

  record UnquantifiedReportHoldings(LocalDate positionDate, List<String> isins)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return "SEB raportis pole kogust (%s): %s: kontrolli raporti importi"
          .formatted(positionDate, String.join(", ", isins));
    }
  }

  record NoReportSecurities(LocalDate positionDate) implements InputGateFailure {

    @Override
    public String slackLine() {
      return "SEB raportis pole %s väärtpabereid: kontrolli raporti importi"
          .formatted(positionDate);
    }
  }

  record MissingR16(YearMonth paymentMonth, LocalDate paymentDeadline, LocalDate countedFrom)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return "R16 %s puudub (väljamaksed %s, arvesse alates %s): lae R16 EPIS-ist"
          .formatted(paymentMonth, paymentDeadline, countedFrom);
    }
  }
}
