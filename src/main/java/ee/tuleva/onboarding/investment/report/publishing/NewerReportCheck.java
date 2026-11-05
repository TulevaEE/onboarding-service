package ee.tuleva.onboarding.investment.report.publishing;

import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NewerReportCheck {

  private static final String NAMED_FILE_REPLACES_NOTHING =
      "replaceUnrecognised names a file that no fund page links as an unrecognised file, so"
          + " nothing was published: value=%s";

  private final WordPressPageReader pageReader;

  FundPagesBeforePublish reasonsNotToPublish(
      List<FundReportMapping> reports, YearMonth month, Set<String> unrecognisedFilesToReplace) {
    var linkedReports = new LinkedHashMap<FundReportMapping, String>();
    var reasons =
        reports.stream()
            .flatMap(
                mapping ->
                    reasonNotToReplace(
                        mapping,
                        month,
                        unrecognisedFilesToReplace,
                        linked -> linkedReports.put(mapping, linked))
                        .stream())
            .toList();
    var linkedUnrecognised =
        linkedReports.entrySet().stream()
            .filter(linked -> linked.getKey().reportPeriodLinkedBy(linked.getValue()).isEmpty())
            .map(Map.Entry::getValue)
            .collect(toSet());
    var replacingNothing =
        unrecognisedFilesToReplace.stream()
            .filter(named -> !linkedUnrecognised.contains(named))
            .sorted()
            .map(NAMED_FILE_REPLACES_NOTHING::formatted);
    return new FundPagesBeforePublish(
        Stream.concat(reasons.stream(), replacingNothing).toList(), linkedReports);
  }

  List<String> reasonsNotToRepoint(
      FundReportMapping mapping, YearMonth month, Set<String> unrecognisedFilesToReplace) {
    return reasonNotToReplace(mapping, month, unrecognisedFilesToReplace, linked -> {}).stream()
        .toList();
  }

  private Optional<String> reasonNotToReplace(
      FundReportMapping mapping,
      YearMonth month,
      Set<String> unrecognisedFilesToReplace,
      Consumer<String> linkedReport) {
    var fundCode = mapping.fund().getCode();
    try {
      return pageReader
          .investmentReportUrl(mapping.pageSlug())
          .flatMap(
              linked -> {
                linkedReport.accept(linked);
                return reasonNotToReplace(mapping, linked, month, unrecognisedFilesToReplace);
              });
    } catch (RuntimeException e) {
      log.error("Could not read fund page to check for a newer report: fund={}", fundCode, e);
      return Optional.of(
          "Could not read the fund page to check it links no newer report: fund=%s, error=%s"
              .formatted(fundCode, e.getMessage()));
    }
  }

  private static Optional<String> reasonNotToReplace(
      FundReportMapping mapping,
      String linked,
      YearMonth month,
      Set<String> unrecognisedFilesToReplace) {
    var fundCode = mapping.fund().getCode();
    var linkedPeriod = mapping.reportPeriodLinkedBy(linked);
    if (linkedPeriod.isEmpty()) {
      return unrecognisedFilesToReplace.contains(linked)
          ? Optional.empty()
          : Optional.of(
              ("Fund page links a file that is not this fund's report, so it may be newer; check"
                      + " it, then pass its URL as replaceUnrecognised to replace it: fund=%s,"
                      + " linked=%s, publishing=%s")
                  .formatted(fundCode, linked, month));
    }
    return linkedPeriod
        .filter(period -> period.isAfter(month))
        .map(
            period ->
                "Fund page already links a newer report: fund=%s, linked=%s, publishing=%s"
                    .formatted(fundCode, period, month));
  }
}
