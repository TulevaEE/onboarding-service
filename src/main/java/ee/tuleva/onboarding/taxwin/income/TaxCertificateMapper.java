package ee.tuleva.onboarding.taxwin.income;

import static ee.tuleva.onboarding.taxwin.DeclaredMonth.NOTHING_DECLARED;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.taxwin.DeclaredMonth;
import ee.tuleva.onboarding.taxwin.IncomeWindow;
import ee.tuleva.onboarding.taxwin.income.generated.TSDFPType;
import ee.tuleva.onboarding.taxwin.income.generated.TSDFType;
import ee.tuleva.onboarding.taxwin.income.generated.TSDFemployerType;
import ee.tuleva.onboarding.taxwin.income.generated.TSDFpaymentsSimpleType;
import ee.tuleva.onboarding.taxwin.income.generated.TaxCertificateResponseType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class TaxCertificateMapper {

  private static final String SPACES = "[\\s\\u00A0\\u202F]";

  private TaxCertificateMapper() {}

  static Map<YearMonth, DeclaredMonth> declaredMonths(
      TaxCertificateResponseType response, IncomeWindow window) {
    Map<YearMonth, DeclaredMonth> declared =
        Stream.concat(salaries(response.getTSDF()), benefits(response.getTSDFP()))
            .filter(entry -> window.contains(entry.getKey()))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, DeclaredMonth::plus));
    return declared.keySet().stream()
        .max(YearMonth::compareTo)
        .map(
            lastDeclared ->
                Stream.iterate(
                        window.first(),
                        month -> !month.isAfter(lastDeclared),
                        month -> month.plusMonths(1))
                    .collect(
                        toMap(
                            month -> month,
                            month -> declared.getOrDefault(month, NOTHING_DECLARED))))
        .orElse(Map.of());
  }

  static BigDecimal amount(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return ZERO;
    }
    return new BigDecimal(value.replaceAll(SPACES, "").replace(',', '.'));
  }

  private static Stream<Map.Entry<YearMonth, DeclaredMonth>> salaries(@Nullable TSDFType section) {
    return Optional.ofNullable(section).stream()
        .flatMap(it -> it.getPeriod().stream())
        .flatMap(
            period ->
                period.getEmployer().stream()
                    .map(
                        employer ->
                            Map.entry(
                                month(period.getYear(), period.getMonth()),
                                salaryAndOtherPayments(employer))));
  }

  private static Stream<Map.Entry<YearMonth, DeclaredMonth>> benefits(@Nullable TSDFPType section) {
    return Optional.ofNullable(section).stream()
        .flatMap(it -> it.getPeriod().stream())
        .flatMap(
            period ->
                period.getEmployer().stream()
                    .flatMap(employer -> employer.getPayments().stream())
                    .map(
                        payments ->
                            Map.entry(
                                month(period.getYear(), period.getMonth()), declared(payments))));
  }

  private static DeclaredMonth salaryAndOtherPayments(TSDFemployerType employer) {
    return Stream.of(employer.getSalary(), employer.getOtherPayments())
        .filter(Objects::nonNull)
        .map(TaxCertificateMapper::declared)
        .reduce(NOTHING_DECLARED, DeclaredMonth::plus);
  }

  private static DeclaredMonth declared(TSDFpaymentsSimpleType payments) {
    return new DeclaredMonth(
        amount(payments.getSum()),
        amount(payments.getUnemploymentInsurancePay()).add(amount(payments.getFundedPensionPay())));
  }

  private static YearMonth month(BigInteger year, int month) {
    return YearMonth.of(year.intValueExact(), month);
  }
}
