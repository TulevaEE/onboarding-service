package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.RoundingMode.HALF_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.StreamSupport.stream;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
class FundService {

  private final FundRepository fundRepository;
  private final PensionFundStatisticsService pensionFundStatisticsService;
  private final FundValueRepository fundValueRepository;
  private final LocaleService localeService;
  private final LedgerService ledgerService;
  private final SavingsFundConfiguration savingsFundConfiguration;
  private final FundNavProvider fundNavProvider;

  List<ExtendedApiFundResponse> getFunds(Optional<String> fundManagerName) {
    return stream(fundsBy(fundManagerName).spliterator(), false)
        .sorted()
        .map(
            fund ->
                new ExtendedApiFundResponse(
                    fund, getStatistics(fund), localeService.getCurrentLocale()))
        .toList();
  }

  private PensionFundStatistics getStatistics(Fund fund) {
    List<PensionFundStatistics> statistics = pensionFundStatisticsService.getCachedStatistics();
    return statistics.stream()
        .filter(statistic -> Objects.equals(statistic.getIsin(), fund.getIsin()))
        .findFirst()
        .orElseGet(() -> fallbackNavStatistics(fund));
  }

  private PensionFundStatistics fallbackNavStatistics(Fund fund) {
    boolean isSavingsFund = savingsFundConfiguration.getIsin().equals(fund.getIsin());
    Optional<FundValue> latestValue =
        isSavingsFund
            ? fundValueRepository.getLatestValue(fund.getIsin(), fundNavProvider.safeMaxNavDate())
            : fundValueRepository.findLastValueForFund(fund.getIsin());
    return latestValue
        .map(fundValue -> buildSavingsFundStatistics(fund, fundValue))
        .orElseGet(PensionFundStatistics::getNull);
  }

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private PensionFundStatistics buildSavingsFundStatistics(Fund fund, FundValue latestFundValue) {
    if (!savingsFundConfiguration.getIsin().equals(fund.getIsin())) {
      return PensionFundStatistics.builder().nav(latestFundValue.value()).build();
    }

    var account = ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100);
    var currentBalance = account.getBalance();
    var cutoff = latestFundValue.date().plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    var balanceAtCutoff = account.getBalanceAt(cutoff);
    var peopleCount = ledgerService.countAccountsWithPositiveBalance(FUND_UNITS);

    boolean issuanceCompleted = currentBalance.compareTo(balanceAtCutoff) != 0;

    if (issuanceCompleted) {
      var nav = toNavScale(latestFundValue.value());
      return PensionFundStatistics.builder()
          .nav(nav)
          .volume(currentBalance.multiply(nav).setScale(2, HALF_UP))
          .activeCount(peopleCount)
          .build();
    }

    var previousNav =
        toNavScale(
            fundValueRepository
                .getLatestValue(fund.getIsin(), latestFundValue.date().minusDays(1))
                .map(FundValue::value)
                .orElse(latestFundValue.value()));
    return PensionFundStatistics.builder()
        .nav(previousNav)
        .volume(currentBalance.multiply(previousNav).setScale(2, HALF_UP))
        .activeCount(peopleCount)
        .build();
  }

  private BigDecimal toNavScale(BigDecimal nav) {
    return nav.setScale(TKF100.getNavScale());
  }

  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  List<NavValueResponse> getNavHistory(String isin, LocalDate startDate, LocalDate endDate) {
    if (fundRepository.findByIsin(isin) == null) {
      throw new ResponseStatusException(NOT_FOUND);
    }
    LocalDate start = startDate != null ? startDate : LocalDate.EPOCH;
    LocalDate end = endDate != null ? endDate : LocalDate.of(9999, 12, 31);
    return fundValueRepository.findValuesBetweenDates(isin, start, end).stream()
        .map(fv -> new NavValueResponse(fv.date(), fv.value()))
        .toList();
  }

  byte[] getNavHistoryCsv(String isin, LocalDate startDate, LocalDate endDate) {
    List<NavValueResponse> navHistory = getNavHistory(isin, startDate, endDate);
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {
      outputStream.write(UTF8_BOM);
      var csvFormat =
          CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader("Kuupäev", "NAV (EUR)").get();
      try (var printer = new CSVPrinter(writer, csvFormat)) {
        for (var row : navHistory) {
          printer.printRecord(ESTONIAN_DATE.format(row.date()), row.value().toPlainString());
        }
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate NAV history CSV: isin=" + isin, e);
    }
  }

  private Iterable<Fund> fundsBy(Optional<String> fundManagerName) {
    return fundManagerName
        .map(fundRepository::findAllByFundManagerNameIgnoreCase)
        .orElseGet(fundRepository::findAll);
  }
}
