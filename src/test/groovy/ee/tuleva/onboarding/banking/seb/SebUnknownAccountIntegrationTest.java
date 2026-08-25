package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@SebIntegrationTest
class SebUnknownAccountIntegrationTest {

  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private SavingFundPaymentRepository savingFundPaymentRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;

  @Test
  void statementFromUnknownAccount_failsTheMessageAndBooksNothing() throws IOException {
    // Rewrite the fixture's IBAN to one no registry entry can match
    var xml =
        loadFixture("eod-transactions-response.xml")
            .replace("EE651010220306497226", "EE009999999999999999");
    var message =
        bankingMessageRepository.save(
            BankingMessage.builder()
                .bankType(SEB)
                .requestId("unknown-account-test")
                .trackingId("unknown-account-test")
                .rawResponse(xml)
                .timezone("Europe/Tallinn")
                .build());

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    var processed = bankingMessageRepository.findById(message.getId()).orElseThrow();
    assertThat(processed.getFailedAt()).isNotNull();
    assertThat(processed.getProcessedAt()).isNull();
    assertThat(savingFundPaymentRepository.findAll()).isEmpty();
  }

  @Test
  void statementFailingTheIntegrityCheck_failsTheMessage() {
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>integrity-test</MsgId>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>integrity-test-stmt</Id>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>2026-02-10T00:00:00</FrDtTm>
                <ToDtTm>2026-02-10T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE001234567890123456</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA FONDID AS</Nm>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>14118923</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
              </Acct>
              <TxsSummry>
                <TtlNtries>
                  <NbOfNtries>5</NbOfNtries>
                </TtlNtries>
              </TxsSummry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """;
    var message =
        bankingMessageRepository.save(
            BankingMessage.builder()
                .bankType(SEB)
                .requestId("integrity-test")
                .trackingId("integrity-test")
                .rawResponse(xml)
                .timezone("Europe/Tallinn")
                .build());

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    var processed = bankingMessageRepository.findById(message.getId()).orElseThrow();
    assertThat(processed.getFailedAt()).isNotNull();
    assertThat(processed.getProcessedAt()).isNull();
  }

  private String loadFixture(String filename) throws IOException {
    try (var stream =
        Objects.requireNonNull(getClass().getResourceAsStream("/banking/seb/" + filename))) {
      return new String(stream.readAllBytes(), UTF_8);
    }
  }
}
