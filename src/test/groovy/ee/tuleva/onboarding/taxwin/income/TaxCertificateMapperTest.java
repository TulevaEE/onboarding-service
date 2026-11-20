package ee.tuleva.onboarding.taxwin.income;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.taxwin.DeclaredMonth;
import ee.tuleva.onboarding.taxwin.IncomeWindow;
import ee.tuleva.onboarding.taxwin.income.generated.TaxCertificateResponse;
import ee.tuleva.onboarding.taxwin.income.generated.TaxCertificateResponseType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.xml.transform.StringSource;

class TaxCertificateMapperTest {

  private static final IncomeWindow WINDOW =
      new IncomeWindow(YearMonth.of(2025, 11), YearMonth.of(2026, 10));

  private final Jaxb2Marshaller marshaller = TaxDataConfiguration.taxDataMarshaller();

  @Test
  void addsUpSalaryAndOtherPaymentsFromEveryEmployerForEachMonth() throws IOException {
    var response =
        (TaxCertificateResponse)
            marshaller.unmarshal(
                new StreamSource(
                    new ClassPathResource("taxwin/tax-certificate-response.xml").getInputStream()));

    var months = TaxCertificateMapper.declaredMonths(response.getResponse(), WINDOW);

    assertThat(months).hasSize(12);
    assertThat(months.get(YearMonth.of(2026, 10)))
        .isEqualTo(new DeclaredMonth(new BigDecimal("2500.00"), new BigDecimal("90.00")));
  }

  @Test
  void addsBenefitsAndPensionsToTheSalaryOfTheSameMonth() {
    var response =
        response(
            period(
                2026,
                10,
                employer(salary("1 000,00", "16,00", "20,00"), salary("200,00", "3,20", "4,00"))),
            period(2026, 10, benefitsEmployer(salary("300,00", "4,80", "0,00"))));

    assertThat(TaxCertificateMapper.declaredMonths(response, WINDOW).get(YearMonth.of(2026, 10)))
        .isEqualTo(new DeclaredMonth(new BigDecimal("1500.00"), new BigDecimal("48.00")));
  }

  @Test
  void aMonthWithoutDataBeforeADeclaredMonthIsZeroAndTrailingMonthsWithoutDataAreUnknown() {
    var response =
        response(
            period(2026, 8, "<nodata>1</nodata>")
                + period(2026, 9, employer(salary("2 000,00", "32,00", "40,00"), ""))
                + period(2026, 10, "<nodata>1</nodata>"),
            "");

    assertThat(TaxCertificateMapper.declaredMonths(response, WINDOW))
        .containsOnlyKeys(
            YearMonth.of(2025, 11),
            YearMonth.of(2025, 12),
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 2),
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6),
            YearMonth.of(2026, 7),
            YearMonth.of(2026, 8),
            YearMonth.of(2026, 9))
        .containsEntry(YearMonth.of(2026, 8), new DeclaredMonth(ZERO, ZERO));
  }

  @Test
  void withNoMonthDeclaredEveryMonthIsUnknown() {
    var response = response(period(2026, 10, "<nodata>1</nodata>"), "");

    assertThat(TaxCertificateMapper.declaredMonths(response, WINDOW)).isEmpty();
  }

  @Test
  void ignoresTheListOfAllIncomeByTypeThatRepeatsTheDeclarations() {
    var response =
        unmarshal(
            """
            <response>
              <TSD-F>%s</TSD-F>
              <TSD-F-PANK>%s</TSD-F-PANK>
            </response>
            """
                .formatted(
                    period(2026, 10, employer(salary("1 000,00", "16,00", "20,00"), "")),
                    period(2026, 10, benefitsEmployer(salary("1 000,00", "16,00", "20,00")))));

    assertThat(TaxCertificateMapper.declaredMonths(response, WINDOW).get(YearMonth.of(2026, 10)))
        .isEqualTo(new DeclaredMonth(new BigDecimal("1000.00"), new BigDecimal("36.00")));
  }

  @Test
  void ignoresMonthsOutsideTheWindow() {
    var response =
        response(
            period(2025, 10, employer(salary("9 999,00", "0,00", "0,00"), ""))
                + period(2026, 10, employer(salary("1 000,00", "16,00", "20,00"), "")),
            "");

    assertThat(TaxCertificateMapper.declaredMonths(response, WINDOW))
        .doesNotContainKey(YearMonth.of(2025, 10))
        .hasSize(12);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1 577,70", "1 577,70", "1577,70", "1577.70", " 1 577,70 "})
  void readsAmountsWrittenWithOrWithoutAThousandsSeparator(String amount) {
    assertThat(TaxCertificateMapper.amount(amount)).isEqualByComparingTo("1577.70");
  }

  @Test
  void readsAMissingOrEmptyAmountAsNothingPaid() {
    assertThat(TaxCertificateMapper.amount(null)).isEqualByComparingTo(ZERO);
    assertThat(TaxCertificateMapper.amount("")).isEqualByComparingTo(ZERO);
  }

  private TaxCertificateResponseType response(String salaryPeriods, String benefitPeriods) {
    return unmarshal(
        """
        <response>
          <TSD-F>%s</TSD-F>
          <TSD-F-P>%s</TSD-F-P>
        </response>
        """
            .formatted(salaryPeriods, benefitPeriods));
  }

  private TaxCertificateResponseType unmarshal(String responseElement) {
    var xml =
        """
        <tns:TaxCertificateResponse xmlns:tns="http://emta-v6.x-road.eu">%s</tns:TaxCertificateResponse>
        """
            .formatted(responseElement);
    return ((TaxCertificateResponse) marshaller.unmarshal(new StringSource(xml))).getResponse();
  }

  private static String period(int year, int month, String content) {
    return "<period><year>%d</year><month>%d</month>%s</period>".formatted(year, month, content);
  }

  private static String employer(String salary, String otherPayments) {
    return "<employer><employerCode>10000003</employerCode>%s%s</employer>"
        .formatted(
            salary.replace("payments>", "salary>"),
            otherPayments.replace("payments>", "otherPayments>"));
  }

  private static String benefitsEmployer(String payments) {
    return "<employer><employerCode>70001975</employerCode>%s</employer>".formatted(payments);
  }

  private static String salary(String sum, String unemploymentInsurance, String fundedPension) {
    return """
        <payments><sum>%s</sum><incomeTax>0,00</incomeTax>\
        <unemploymentInsurancePay>%s</unemploymentInsurancePay>\
        <fundedPensionPay>%s</fundedPensionPay></payments>"""
        .formatted(sum, unemploymentInsurance, fundedPension);
  }
}
