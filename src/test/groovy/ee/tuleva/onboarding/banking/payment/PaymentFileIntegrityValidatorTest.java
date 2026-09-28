package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.UNSTRUCTURED_ADDRESS;
import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.XSD_SCHEMA;
import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityViolation.mismatch;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentFileIntegrityValidatorTest {

  private static final String REMITTER_IBAN = "EE111111111111111111";
  private static final String BENEFICIARY_IBAN = "EE222222222222222222";

  private final Clock clock = Clock.fixed(Instant.parse("2026-08-13T14:13:15Z"), UTC);
  private final PaymentMessageGenerator generator = new PaymentMessageGenerator(clock);
  private final PaymentFileIntegrityValidator validator = new PaymentFileIntegrityValidator(clock);

  @Test
  void validPaymentHasNoViolations() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");

    var violations = validator.validate(generate(request), request);

    assertThat(violations).isEmpty();
  }

  // The same document, with the ISO namespace behind a prefix instead of as the default. Reading
  // raw tag names would fail every field here, which looks like a tampered file rather than a
  // reformatted one -- fail-closed, but for entirely the wrong reason.
  @Test
  void theSameFileIsReadTheSameWayWhenTheNamespaceIsPrefixed() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var prefixed =
        generate(request)
            .replace("xmlns=\"urn:iso", "xmlns:p=\"urn:iso")
            .replaceAll("<(/?)(?!\\?)([A-Za-z][A-Za-z0-9]*)", "<$1p:$2");

    var violations = validator.validate(prefixed, request);

    assertThat(violations).isEmpty();
  }

  @Test
  void detectsTamperedBeneficiaryIban() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var tampered = generate(request).replace(BENEFICIARY_IBAN, "EE333333333333333333");

    var violations = validator.validate(tampered, request);

    assertThat(violations).containsExactly(mismatch("beneficiaryIban"));
  }

  @Test
  void detectsTamperedAmount() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var tampered =
        generate(request).replace("<InstdAmt Ccy=\"EUR\">111.03", "<InstdAmt Ccy=\"EUR\">911.03");

    var violations = validator.validate(tampered, request);

    assertThat(violations).containsExactly(mismatch("amount"));
  }

  @Test
  void detectsAmountNotMatchingControlSums() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var tampered =
        generate(request).replace("<CtrlSum>111.03</CtrlSum>", "<CtrlSum>222.06</CtrlSum>");

    var violations = validator.validate(tampered, request);

    assertThat(violations)
        .containsExactly(mismatch("groupControlSum"), mismatch("paymentControlSum"));
  }

  @Test
  void detectsNonEurCurrency() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var tampered = generate(request).replace("Ccy=\"EUR\"", "Ccy=\"USD\"");

    var violations = validator.validate(tampered, request);

    assertThat(violations).contains(mismatch("currency"));
  }

  @Test
  void detectsUnstructuredAddress() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var withAddress =
        generate(request)
            .replace(
                "<Nm>John Doe</Nm>",
                "<Nm>John Doe</Nm><PstlAdr><AdrLine>Some street 1, Tallinn</AdrLine></PstlAdr>");

    var violations = validator.validate(withAddress, request);

    assertThat(violations)
        .containsExactly(
            new PaymentIntegrityViolation(XSD_SCHEMA, "document"),
            new PaymentIntegrityViolation(UNSTRUCTURED_ADDRESS, "address"));
  }

  @Test
  void detectsAddressLinesEvenBesideTheTownAndCountry() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var withAddress =
        generate(request)
            .replace(
                "<Nm>John Doe</Nm>",
                "<Nm>John Doe</Nm><PstlAdr><TwnNm>Tallinn</TwnNm><Ctry>EE</Ctry>"
                    + "<AdrLine>Some street 1</AdrLine></PstlAdr>");

    var violations = validator.validate(withAddress, request);

    assertThat(violations)
        .containsExactly(new PaymentIntegrityViolation(UNSTRUCTURED_ADDRESS, "address"));
  }

  @Test
  void detectsAddressWithoutTheTownAndCountrySebRequires() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var withAddress =
        generate(request)
            .replace(
                "<Nm>John Doe</Nm>",
                "<Nm>John Doe</Nm><PstlAdr><StrtNm>Some street</StrtNm><BldgNb>1</BldgNb></PstlAdr>");

    var violations = validator.validate(withAddress, request);

    assertThat(violations).containsExactly(new PaymentIntegrityViolation(XSD_SCHEMA, "document"));
  }

  @Test
  void detectsTamperedAmountWithFractionsOfACent() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var tampered =
        generate(request).replace("<InstdAmt Ccy=\"EUR\">111.03", "<InstdAmt Ccy=\"EUR\">111.031");

    var violations = validator.validate(tampered, request);

    assertThat(violations).containsExactly(mismatch("amount"));
  }

  @Test
  void detectsMissingCreditorAccount() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var withoutAccount = generate(request).replaceAll("(?s)<CdtrAcct>.*?</CdtrAcct>", "");

    var violations = validator.validate(withoutAccount, request);

    assertThat(violations).containsExactly(mismatch("beneficiaryIban"));
  }

  @Test
  void detectsMissingCreditor() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var withoutCreditor = generate(request).replaceAll("(?s)<Cdtr>.*?</Cdtr>", "");

    var violations = validator.validate(withoutCreditor, request);

    assertThat(violations).containsExactly(mismatch("beneficiaryName"));
  }

  @Test
  void detectsSecondDescriptionLine() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var withSecondLine =
        generate(request)
            .replace(
                "<Ustrd>Fondi tagasivõtmine</Ustrd>",
                "<Ustrd>Fondi tagasivõtmine</Ustrd><Ustrd>Another line</Ustrd>");

    var violations = validator.validate(withSecondLine, request);

    assertThat(violations).containsExactly(mismatch("description"));
  }

  @Test
  void detectsSchemaViolation() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var broken = generate(request).replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>NOPE</PmtMtd>");

    var violations = validator.validate(broken, request);

    assertThat(violations).containsExactly(new PaymentIntegrityViolation(XSD_SCHEMA, "document"));
  }

  @Test
  void detectsMoreThanOneTransaction() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var xml = generate(request);
    var transaction =
        xml.substring(xml.indexOf("<CdtTrfTxInf>"), xml.indexOf("</CdtTrfTxInf>") + 14);
    var duplicated = xml.replace(transaction, transaction + transaction);

    var violations = validator.validate(duplicated, request);

    assertThat(violations).contains(mismatch("transactionCount"));
  }

  @Test
  void detectsExecutionDateInThePast() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var backdated = generate(request).replace("<Dt>2026-08-13</Dt>", "<Dt>2026-08-12</Dt>");

    var violations = validator.validate(backdated, request);

    assertThat(violations).containsExactly(mismatch("executionDate"));
  }

  @Test
  void acceptsLegitimateTruncationOfLongBeneficiaryName() {
    var longName = "A".repeat(120);
    var request = paymentRequest(new BigDecimal("111.03"), longName);

    var violations = validator.validate(generate(request), request);

    assertThat(violations).isEmpty();
  }

  @Test
  void detectsTamperedEndToEndId() {
    var request = paymentRequest(new BigDecimal("111.03"), "John Doe");
    var tampered =
        generate(request)
            .replace("<EndToEndId>e2e-1</EndToEndId>", "<EndToEndId>e2e-2</EndToEndId>");

    var violations = validator.validate(tampered, request);

    assertThat(violations).containsExactly(mismatch("endToEndId"));
  }

  private String generate(PaymentRequest request) {
    return generator.generatePaymentMessage(request, "EEUHEE2X");
  }

  private PaymentRequest paymentRequest(BigDecimal amount, String beneficiaryName) {
    return PaymentRequest.builder()
        .remitterName("Tuleva Täiendav Kogumisfond")
        .remitterId("1162")
        .remitterIban(REMITTER_IBAN)
        .beneficiaryName(beneficiaryName)
        .beneficiaryIban(BENEFICIARY_IBAN)
        .amount(amount)
        .description("Fondi tagasivõtmine")
        .ourId("our-1")
        .endToEndId("e2e-1")
        .build();
  }
}
