package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.message.BankMessageType.*;
import static ee.tuleva.onboarding.swedbank.SwedbankGatewayTime.SWEDBANK_GATEWAY_TIME_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwedbankMessageDelegatorTest {

  private Clock clock;
  private BankingMessageRepository bankingMessageRepository;

  private SwedbankMessageProcessor firstProcessor;
  private SwedbankMessageProcessor secondProcessor;

  private SwedbankMessageDelegator delegator;

  @BeforeEach
  void setUp() {
    clock = TestClockHolder.clock;

    firstProcessor = mock(SwedbankMessageProcessor.class);
    secondProcessor = mock(SwedbankMessageProcessor.class);
    bankingMessageRepository = mock(BankingMessageRepository.class);

    delegator =
        new SwedbankMessageDelegator(
            clock, bankingMessageRepository, List.of(firstProcessor, secondProcessor));
  }

  @Test
  @DisplayName("delegates pain payment message")
  void processMessages() {
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.10\"></Document>")
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    var messageType = PAYMENT_ORDER_CONFIRMATION;

    when(firstProcessor.supports(messageType)).thenReturn(false);
    when(secondProcessor.supports(messageType)).thenReturn(true);

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));

    delegator.processMessages();

    verify(firstProcessor, never()).processMessage(any(), any(), any());
    verify(secondProcessor, times(1))
        .processMessage(message.getRawResponse(), messageType, message.getTimezoneId());

    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());

    verify(bankingMessageRepository, times(1)).save(message);
  }

  @Test
  @DisplayName("delegates camt intraday message")
  void processCamtIntraday() {
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"></Document>")
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    var messageType = INTRA_DAY_REPORT;

    when(firstProcessor.supports(messageType)).thenReturn(false);
    when(secondProcessor.supports(messageType)).thenReturn(true);

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));

    delegator.processMessages();

    verify(firstProcessor, never()).processMessage(any(), any(), any());
    verify(secondProcessor, times(1))
        .processMessage(message.getRawResponse(), messageType, message.getTimezoneId());

    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());

    verify(bankingMessageRepository, times(1)).save(message);
  }

  @Test
  @DisplayName("delegates camt historic statement message")
  void processCamtHistoric() {
    var xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"> <BkToCstmrStmt> <GrpHdr> <MsgId>202506051114025350001</MsgId> <CreDtTm>2025-06-05T09:04:24</CreDtTm> </GrpHdr> <Stmt> <Id>111402535-EUR-1</Id> <ElctrncSeqNb>111402535</ElctrncSeqNb> <CreDtTm>2025-06-05T09:04:24</CreDtTm> <FrToDt> <FrDtTm>2025-06-05T00:00:00</FrDtTm> <ToDtTm>2025-06-05T23:59:59</ToDtTm> </FrToDt> <Acct> <Id> <IBAN>EE062200221055091966</IBAN> </Id> <Ccy>EUR</Ccy> <Ownr> <Nm>Pööripäeva Päikesekell OÜ</Nm> <PstlAdr> <Ctry>EE</Ctry> <AdrLine>Universumimaagia tee 15, Astrofantaasia, 56789</AdrLine> </PstlAdr> <Id> <OrgId> <Othr> <Id>10006901</Id> </Othr> </OrgId> </Id> </Ownr> <Svcr> <FinInstnId> <BIC>HABAEE2X</BIC> <Nm>SWEDBANK AS</Nm> <PstlAdr> <Ctry>EE</Ctry> <AdrLine>LIIVALAIA 8, 15040, TALLINN</AdrLine> </PstlAdr> <Othr> <Id>10060701</Id> <SchmeNm> <Cd>COID</Cd> </SchmeNm> </Othr> </FinInstnId> </Svcr> </Acct> <Bal> <Tp> <CdOrPrtry> <Cd>OPBD</Cd> </CdOrPrtry> </Tp> <CdtLine> <Incl>false</Incl> </CdtLine> <Amt Ccy=\"EUR\">3332.17</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Dt> <Dt>2025-06-05</Dt> </Dt> </Bal> <Bal> <Tp> <CdOrPrtry> <Cd>CLBD</Cd> </CdOrPrtry> </Tp> <Amt Ccy=\"EUR\">766.10</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Dt> <Dt>2025-06-05</Dt> </Dt> </Bal> <TxsSummry> <TtlCdtNtries> <NbOfNtries>4</NbOfNtries> <Sum>2566.07</Sum> </TtlCdtNtries> <TtlDbtNtries> <NbOfNtries>0</NbOfNtries> <Sum>0</Sum> </TtlDbtNtries> </TxsSummry> <Ntry> <NtryRef>2025060525827070-1</NtryRef> <Amt Ccy=\"EUR\">772.88</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060525827070-1</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060525827070-1</AcctSvcrRef> <InstrId>179</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">772.88</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">772.88</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Toomas Raudsepp</Nm> <Id> <PrvtId> <Othr> <Id>38009293505</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE042200000000100031</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Subscription renewal for a productivity app or software</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060591569146-2</NtryRef> <Amt Ccy=\"EUR\">612.37</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060591569146-2</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060591569146-2</AcctSvcrRef> <InstrId>180</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">612.37</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">612.37</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Kristjan Värk</Nm> <Id> <PrvtId> <Othr> <Id>39609307495</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE082200000000100056</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Purchase of airline tickets</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060538364998-3</NtryRef> <Amt Ccy=\"EUR\">454.76</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060538364998-3</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060538364998-3</AcctSvcrRef> <InstrId>181</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">454.76</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">454.76</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Jüri Tamm</Nm> <Id> <PrvtId> <Othr> <Id>39910273027</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE532200000000010006</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Donation to a charity organization</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060515070077-4</NtryRef> <Amt Ccy=\"EUR\">726.06</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060515070077-4</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060515070077-4</AcctSvcrRef> <InstrId>182</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">726.06</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">726.06</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Rasmus Lind</Nm> <Id> <PrvtId> <Othr> <Id>37603135585</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE522200000000100040</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Shopping for books or e-books</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> </Stmt> </BkToCstmrStmt> </Document>";
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(xml)
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    var messageType = HISTORIC_STATEMENT;

    when(firstProcessor.supports(messageType)).thenReturn(false);
    when(secondProcessor.supports(messageType)).thenReturn(true);

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));

    delegator.processMessages();

    verify(firstProcessor, never()).processMessage(any(), any(), any());
    verify(secondProcessor, times(1))
        .processMessage(message.getRawResponse(), messageType, message.getTimezoneId());

    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());

    verify(bankingMessageRepository, times(1)).save(message);
  }

  @Test
  @DisplayName("does not process when no supported services")
  void processMessagesNoSupportedServices() {
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.10\"></Document>")
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    when(firstProcessor.supports(PAYMENT_ORDER_CONFIRMATION)).thenReturn(false);
    when(secondProcessor.supports(PAYMENT_ORDER_CONFIRMATION)).thenReturn(false);

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));

    delegator.processMessages();

    verify(firstProcessor, never()).processMessage(any(), any(), any());
    verify(secondProcessor, never()).processMessage(any(), any(), any());

    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());
    verify(bankingMessageRepository).save(message);
  }

  @Test
  @DisplayName("marks message as failed when processing throws exception")
  void processMessagesMarksAsFailed() {
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.10\"></Document>")
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    var messageType = PAYMENT_ORDER_CONFIRMATION;

    when(firstProcessor.supports(messageType)).thenReturn(false);
    when(secondProcessor.supports(messageType)).thenReturn(true);
    doThrow(new RuntimeException("Processing failed"))
        .when(secondProcessor)
        .processMessage(message.getRawResponse(), messageType, message.getTimezoneId());

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));

    delegator.processMessages();

    verify(secondProcessor, times(1))
        .processMessage(message.getRawResponse(), messageType, message.getTimezoneId());

    assertThat(message.getFailedAt()).isEqualTo(clock.instant());
    assertThat(message.getProcessedAt()).isNull();

    verify(bankingMessageRepository, times(1)).save(message);
  }
}
