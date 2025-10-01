package ee.tuleva.onboarding.swedbank.payment;

import static ee.tuleva.onboarding.swedbank.payment.XmlHelper.add;
import static ee.tuleva.onboarding.swedbank.payment.XmlHelper.asString;
import static ee.tuleva.onboarding.swedbank.payment.XmlHelper.createDocument;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMessageGenerator {
  private final Clock clock;

  public String generatePaymentMessage(PaymentRequest paymentRequest) {
    var document = createDocument();

    var documentElement = add(document, "Document");
    documentElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    documentElement.setAttribute("xmlns", "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09");

    var root = add(documentElement, "CstmrCdtTrfInitn");
    var header = add(root, "GrpHdr");
    var now = clock.instant();
    var messageId = now.getEpochSecond();

    add(header, "MsgId", messageId);
    add(header, "CreDtTm", now.toString());

    add(header, "NbOfTxs", 1);
    add(header, "CtrlSum", paymentRequest.amount());
    add(add(header, "InitgPty"), "Nm", paymentRequest.remitterName(), 70);

    var paymentInfo = add(root, "PmtInf");
    add(paymentInfo, "PmtInfId", messageId);
    add(paymentInfo, "PmtMtd", "TRF");
    add(paymentInfo, "NbOfTxs", 1);
    add(paymentInfo, "CtrlSum", paymentRequest.amount());
    add(add(add(paymentInfo, "PmtTpInf"), "SvcLvl"), "Cd", "SEPA");
    add(add(paymentInfo, "ReqdExctnDt"), "Dt", LocalDate.now(clock).toString());

    var debtor = add(paymentInfo, "Dbtr");
    add(debtor, "Nm", paymentRequest.remitterName(), 70);
    add(add(add(add(debtor, "Id"), "OrgId"), "Othr"), "Id", paymentRequest.remitterId(), 35);
    add(add(add(paymentInfo, "DbtrAcct"), "Id"), "IBAN", paymentRequest.remitterIban());
    add(add(add(paymentInfo, "DbtrAgt"), "FinInstnId"), "BICFI", paymentRequest.remitterBic());

    var paymentRoot = add(paymentInfo, "CdtTrfTxInf");
    var paymentId = add(paymentRoot, "PmtId");
    add(paymentId, "InstrId", paymentRequest.ourId(), 35);
    add(paymentId, "EndToEndId", paymentRequest.endToEndId(), 35);
    add(add(paymentRoot, "Amt"), "InstdAmt", paymentRequest.amount()).setAttribute("Ccy", "EUR");
    add(add(paymentRoot, "Cdtr"), "Nm", paymentRequest.beneficiaryName(), 70);
    add(add(add(paymentRoot, "CdtrAcct"), "Id"), "IBAN", paymentRequest.beneficiaryIban());
    var remittanceInfo = add(paymentRoot, "RmtInf");
    add(remittanceInfo, "Ustrd", paymentRequest.description(), 140);

    return asString(document);
  }
}
