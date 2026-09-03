package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportFormatException;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult.RowError;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryCsvParser.ParseResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HistoricalRegistryCsvParserTest {

  private static final String FUND_ISIN = "EE3600001707";
  private static final String INSTRUMENT_ISIN = "US0000000001";

  @Mock private InstrumentReferenceService instrumentReferenceService;

  private HistoricalRegistryCsvParser parser;

  @BeforeEach
  void setUp() {
    parser =
        new HistoricalRegistryCsvParser(
            new HistoricalRegistryRowParser(instrumentReferenceService));
  }

  @Test
  void rowIsRejectedWhenInstrumentReferenceHasNoInstrumentType() {
    InstrumentReference reference = instrumentReference(INSTRUMENT_ISIN, null);
    given(instrumentReferenceService.findByIsin(INSTRUMENT_ISIN))
        .willReturn(Optional.of(reference));

    ParseResult result = parser.parse(csvWithoutInstrumentTypeColumn());

    assertThat(result.errors()).extracting(RowError::rowNumber).containsExactly(2);
    assertThat(result.rows()).isEmpty();
    then(instrumentReferenceService).should().findByIsin(INSTRUMENT_ISIN);
  }

  @Test
  void rowIsRejectedWhenInstrumentReferenceHasUnrecognisedInstrumentType() {
    InstrumentReference reference = instrumentReference(INSTRUMENT_ISIN, "BOND");
    given(instrumentReferenceService.findByIsin(INSTRUMENT_ISIN))
        .willReturn(Optional.of(reference));

    ParseResult result = parser.parse(csvWithoutInstrumentTypeColumn());

    assertThat(result.errors()).extracting(RowError::rowNumber).containsExactly(2);
    assertThat(result.rows()).isEmpty();
    then(instrumentReferenceService).should().findByIsin(INSTRUMENT_ISIN);
  }

  @Test
  void importWithMissingHeadersThrowsFormatException() {
    String csv =
        """
        order_id,fund_isin
        GAS-2024-030,EE3600109435
        """;

    HistoricalImportFormatException exception =
        catchThrowableOfType(HistoricalImportFormatException.class, () -> parser.parse(csv));

    assertThat(exception.getMissingHeaders())
        .containsExactly(
            "instrument_isin",
            "order_timestamp",
            "order_status",
            "expected_settlement_date",
            "comment");
    assertThat(exception.getRequiredHeaders())
        .containsExactly(
            "order_id",
            "fund_isin",
            "instrument_isin",
            "order_timestamp",
            "order_status",
            "expected_settlement_date",
            "comment");
  }

  @Test
  void importParsesSemicolonDelimiterAndEstonianDecimals() {
    String csv =
        """
        order_id;fund_isin;instrument_isin;transaction_id;transaction_type;instrument_type;order_amount;order_quantity;order_timestamp;order_status;expected_settlement_date;actual_settlement_date;execution_timestamp;executed_quantity;unit_price;total_consideration;net_settlement_amount;commission_amount;comment
        GAS-2024-020;EE3600109435;IE00BFG1TM61;BR-2020;BUY;ETF;25 000,00;250,000000;2025-03-10 09:00:00;EXECUTED;2025-03-12;;2025-03-10 14:30:00;250,000000;100,10;25 025,00;25 030,00;5,00;
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows()).hasSize(1);
    ParsedRow row = result.rows().getFirst();
    assertThat(row.orderAmount()).isEqualByComparingTo("25000.00");
    assertThat(row.orderQuantity()).isEqualByComparingTo("250.000000");
    assertThat(row.unitPrice()).isEqualByComparingTo("100.10");
    assertThat(row.totalConsideration()).isEqualByComparingTo("25025.00");
  }

  @Test
  void importResolvesSingleSeparatorAsThousandsUnderSemicolonDelimiter() {
    String csv =
        """
        order_id;fund_isin;instrument_isin;transaction_id;transaction_type;instrument_type;order_amount;order_quantity;order_timestamp;order_status;expected_settlement_date;actual_settlement_date;execution_timestamp;executed_quantity;unit_price;total_consideration;net_settlement_amount;commission_amount;comment
        GAS-2024-070;EE3600109435;IE00BFG1TM61;BR-2070;BUY;ETF;12.345;;2025-03-10 09:00:00;SENT;2025-03-12;;;;;;;;
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows().getFirst().orderAmount()).isEqualByComparingTo("12345");
  }

  @Test
  void importResolvesCommaDecimalConventionAcrossThousandsAndDecimalForms() {
    String csv =
        """
        order_id;fund_isin;instrument_isin;transaction_id;transaction_type;instrument_type;order_amount;order_quantity;order_timestamp;order_status;expected_settlement_date;actual_settlement_date;execution_timestamp;executed_quantity;unit_price;total_consideration;net_settlement_amount;commission_amount;comment
        GAS-2024-071;EE3600109435;IE00BFG1TM61;BR-2071;BUY;ETF;1.234.567;0.80000;2025-03-10 09:00:00;SENT;2025-03-12;;;;150000000,000;100,000;;;
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    ParsedRow row = result.rows().getFirst();
    assertThat(row.orderAmount()).isEqualByComparingTo("1234567");
    assertThat(row.orderQuantity()).isEqualByComparingTo("0.80000");
    assertThat(row.unitPrice()).isEqualByComparingTo("150000000.000");
    assertThat(row.totalConsideration()).isEqualByComparingTo("100.000");
  }

  @Test
  void importResolvesPeriodDecimalConventionAcrossThousandsAndDecimalForms() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-072,EE3600109435,IE00BFG1TM61,BR-2072,BUY,ETF,12.345,"1,234.56",2025-03-10 09:00:00,SENT,2025-03-12,,,,"100,000",1234.56,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    ParsedRow row = result.rows().getFirst();
    assertThat(row.orderAmount()).isEqualByComparingTo("12.345");
    assertThat(row.orderQuantity()).isEqualByComparingTo("1234.56");
    assertThat(row.unitPrice()).isEqualByComparingTo("100000");
    assertThat(row.totalConsideration()).isEqualByComparingTo("1234.56");
  }

  @Test
  void executedRowWithBlankOrderTimestampIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9001,EE3600109435,IE00BFG1TM61,BR-9001,BUY,ETF,1000.00,10.000000,,EXECUTED,2025-03-12,,2025-03-10 14:30:00,10.000000,100.00,1000.00,995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Missing value: column=order_timestamp, orderId=GAS-9001"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void executedRowWithNoExecutionDataIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9002,EE3600109435,IE00BFG1TM61,BR-9002,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Terminal order missing execution data: orderId=GAS-9002"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void executedRowWithBlankUnitPriceIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9003,EE3600109435,IE00BFG1TM61,BR-9003,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,10.000000,,1000.00,995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(new RowError(2, "Missing value: column=unit_price, orderId=GAS-9003"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void executedEtfBuyWithBlankExecutedQuantityIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9004,EE3600109435,IE00BFG1TM61,BR-9004,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,1000.00,995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Missing value: column=executed_quantity, orderId=GAS-9004"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importWithMultipleBlankBrokerTransactionIdsSucceeds() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-050,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        GAS-2024-051,EE3600109435,IE00BFG1TM61,,BUY,ETF,2000.00,,2025-03-11 09:00:00,SENT,2025-03-13,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows()).hasSize(2);
  }

  @Test
  void executedSellWithBlankExecutedQuantityIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9005,EE3600109443,IE0009FT4LX4,BR-9005,SELL,FUND,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,1000.00,995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Missing value: column=executed_quantity, orderId=GAS-9005"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void executedFundBuyWithBlankExecutedQuantityButPopulatedTotalConsiderationSucceeds() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9006,EE3600109443,IE0009FT4LX4,BR-9006,BUY,FUND,5000.00,,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,5000.00,4995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows()).hasSize(1);
    ParsedRow row = result.rows().getFirst();
    assertThat(row.executedQuantity()).isNull();
    assertThat(row.totalConsideration()).isEqualByComparingTo("5000.00");
  }

  @Test
  void nonTerminalRowsWithBlankTimestampAndEconomicsSucceed() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9007,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,10.000000,,DRAFT,2025-03-12,,,,,,,,
        GAS-9008,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,10.000000,,SENT,2025-03-12,,,,,,,,
        GAS-9009,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,10.000000,,CANCELLED,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void terminalFundBuyWithoutInstrumentTypeColumnResolvesTypeFromInstrumentReference() {
    given(instrumentReferenceService.findByIsin("IE0009FT4LX4"))
        .willReturn(Optional.of(instrumentReference("IE0009FT4LX4", "FUND")));
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9101,EE3600109443,IE0009FT4LX4,BR-9101,BUY,5000.00,,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,5000.00,4995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().getFirst().instrumentType()).isEqualTo(InstrumentType.FUND);
  }

  @Test
  void etfBuyWithoutInstrumentTypeColumnStillRequiresExecutedQuantity() {
    given(instrumentReferenceService.findByIsin("IE000I9HGDZ3"))
        .willReturn(Optional.of(instrumentReference("IE000I9HGDZ3", "ETF")));
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9102,EE3600109443,IE000I9HGDZ3,BR-9102,BUY,5000.00,,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,5000.00,4995.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Missing value: column=executed_quantity, orderId=GAS-9102"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void unknownInstrumentIsinWithoutInstrumentTypeColumnIsRejectedWithoutEtfGuess() {
    given(instrumentReferenceService.findByIsin("XX0000000099")).willReturn(Optional.empty());
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9103,EE3600109443,XX0000000099,,BUY,5000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(new RowError(2, "Unknown instrument: instrumentIsin=XX0000000099"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void explicitInstrumentTypeColumnWinsOverInstrumentReference() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9104,EE3600109443,IE000I9HGDZ3,,BUY,FUND,5000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.rows().getFirst().instrumentType()).isEqualTo(InstrumentType.FUND);
  }

  @Test
  void importReadsTimestampsInIsoInstantIsoLocalAndDateOnlyForms() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9204,EE3600109435,IE00BFG1TM61,BR-9204,BUY,ETF,25000.00,250.000000,2025-03-10T09:00:00Z,EXECUTED,2025-03-12,,2025-03-10T14:30:00Z,250.000000,100.10,25025.00,25030.00,5.00,
        GAS-9205,EE3600109435,IE00BFG1TM61,BR-9205,BUY,ETF,25000.00,250.000000,2025-03-11T09:00:00,EXECUTED,2025-03-13,,2025-03-11T14:30:00,250.000000,100.10,25025.00,25030.00,5.00,
        GAS-9206,EE3600109435,IE00BFG1TM61,BR-9206,BUY,ETF,25000.00,250.000000,2025-03-12,EXECUTED,2025-03-14,,2025-03-12,250.000000,100.10,25025.00,25030.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(rowWithBrokerTransactionId(result, "BR-9204").executionTimestamp())
        .isEqualTo(Instant.parse("2025-03-10T14:30:00Z"));
    assertThat(rowWithBrokerTransactionId(result, "BR-9205").executionTimestamp())
        .isEqualTo(Instant.parse("2025-03-11T14:30:00Z"));
    assertThat(rowWithBrokerTransactionId(result, "BR-9206").executionTimestamp())
        .isEqualTo(Instant.parse("2025-03-12T00:00:00Z"));
  }

  @Test
  void importReadsEstonianDates() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9207,EE3600109435,IE00BFG1TM61,BR-9207,BUY,ETF,25000.00,250.000000,2025-03-10 09:00:00,EXECUTED,12.03.2025,,2025-03-10 14:30:00,250.000000,100.10,25025.00,25030.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(rowWithBrokerTransactionId(result, "BR-9207").expectedSettlementDate())
        .isEqualTo(LocalDate.of(2025, 3, 12));
  }

  @Test
  void importRejectsAnUnreadableDate() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9208,EE3600109435,IE00BFG1TM61,,BUY,ETF,25000.00,250.000000,,SENT,2025/03/12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Invalid date: column=expected_settlement_date, value=2025/03/12"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importRejectsAnUnreadableNumber() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9209,EE3600109435,IE00BFG1TM61,,BUY,ETF,not a number,250.000000,,SENT,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Invalid number: column=order_amount, value=not a number"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importRejectsAnUnknownOrderStatus() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9210,EE3600109435,IE00BFG1TM61,,BUY,ETF,25000.00,250.000000,,PARTIALLY_FILLED,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Invalid value: column=order_status, value=PARTIALLY_FILLED"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importRejectsARowWithNoOrderId() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        ,EE3600109435,IE00BFG1TM61,,BUY,ETF,25000.00,250.000000,,SENT,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors()).containsExactly(new RowError(2, "Missing value: column=order_id"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importRejectsASettledRowWithNoSettlementDateOnEitherSide() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9211,EE3600109435,IE00BFG1TM61,BR-9211,BUY,ETF,25000.00,250.000000,2025-03-10 09:00:00,SETTLED,,,2025-03-10 14:30:00,250.000000,100.10,25025.00,25030.00,5.00,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(
                2,
                "Settled row missing both actual and expected settlement date: orderId=GAS-9211"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importRejectsAFundSubscriptionWithNoTotalConsideration() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9212,EE3600109443,LU0826455353,BR-9212,BUY,FUND,5000.00,,2025-05-05 09:00:00,EXECUTED,2025-05-09,,2025-05-05 14:30:00,,100.00,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "Missing value: column=total_consideration, orderId=GAS-9212"));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void importRejectsTheFileWhenTheSameOrderIdAppearsTwice() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9213,EE3600109435,IE00BFG1TM61,,BUY,ETF,25000.00,250.000000,,SENT,2025-03-12,,,,,,,,
        GAS-9213,EE3600109435,IE00BFG1TM61,,BUY,ETF,25000.00,250.000000,,SENT,2025-03-12,,,,,,,,
        """;

    ParseResult result = parser.parse(csv);

    assertThat(result.errors())
        .containsExactly(new RowError(3, "Duplicate order_id in file: orderId=GAS-9213"));
    assertThat(result.rows()).hasSize(1);
  }

  private static ParsedRow rowWithBrokerTransactionId(
      ParseResult result, String brokerTransactionId) {
    return result.rows().stream()
        .filter(row -> brokerTransactionId.equals(row.brokerTransactionId()))
        .findFirst()
        .orElseThrow();
  }

  private static String csvWithoutInstrumentTypeColumn() {
    return """
        order_id,fund_isin,instrument_isin,order_timestamp,order_status,expected_settlement_date,comment,transaction_type
        GAS-9201,%s,%s,,SENT,,,BUY
        """
        .formatted(FUND_ISIN, INSTRUMENT_ISIN);
  }

  private static InstrumentReference instrumentReference(String isin, String instrumentType) {
    InstrumentReference reference = BeanUtils.instantiateClass(InstrumentReference.class);
    ReflectionTestUtils.setField(reference, "isin", isin);
    ReflectionTestUtils.setField(reference, "instrumentType", instrumentType);
    return reference;
  }
}
