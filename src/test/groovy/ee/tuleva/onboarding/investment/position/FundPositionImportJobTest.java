package ee.tuleva.onboarding.investment.position;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionImportJobTest {

  @Mock private FundPositionSource source;
  @Spy private SwedbankFundPositionParser parser = new SwedbankFundPositionParser();
  @Mock private FundPositionRepository repository;

  private FundPositionImportService importService;
  private FundPositionImportJob job;

  @BeforeEach
  void setUp() {
    importService = new FundPositionImportService(repository);
    job = new FundPositionImportJob(source, parser, importService);
  }

  private static final String HEADER =
      "ReportDate;NAVDate;Portfolio;AssetType;FundCurr;ISIN;AssetName;Quantity;AssetCurr;PricePC;PriceQC;BookCostPC;BookCostQC;BookPriceQC;ValuationPC;MarketValuePC;ValuationQC;InterestPC;InterestQC; ;GainPC;GainQC;PriceEffect;FxEffect;InstrumentType;PrctNav;IssuerName;TNA;pGroupCode;MaturityDate;FxRate;Security_ID;Detailed Asset Type;Trade ID;Trade Date;ClassCode";

  private static final String SAMPLE_CSV =
      HEADER
          + "\n"
          + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;IE00BFG1TM61;ISHARES DEV WLD ESG;1000000;EUR;33.5;33.5;0;0;0;0;33500000;0;0;0;;0;0;0;0;Equity Fund;0;;0;18;;1;;;;;\n"
          + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Cash & Cash Equiv;EUR;;Overnight Deposit;5000000;EUR;1;1;0;0;0;0;5000000;0;0;0;;0;0;0;0;Money Market;0;;0;18;;1;;;;;\n"
          + "06.01.2026;05.01.2026;Tuleva Vabatahtlik Pensionifon;Equities;EUR;IE00BFNM3G45;ISHARES USA ESG;500000;EUR;12;12;0;0;0;0;6000000;0;0;0;;0;0;0;0;Equity Fund;0;;0;18;;1;;;;;";

  @Test
  void importForDate_parsesAndSavesPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(source.fetch(date))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(repository.existsByReportingDateAndFundCodeAndAccountName(any(), any(), any()))
        .thenReturn(false);

    job.importForDate(date);

    verify(repository, times(3)).save(any(FundPosition.class));
  }

  @Test
  void importForDate_skipsExistingPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(source.fetch(date))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUK75", "ISHARES DEV WLD ESG"))
        .thenReturn(true);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUK75", "Overnight Deposit"))
        .thenReturn(false);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUV100", "ISHARES USA ESG"))
        .thenReturn(false);

    job.importForDate(date);

    verify(repository, times(2)).save(any(FundPosition.class));
  }

  @Test
  void importForDate_handlesEmptyFile() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(source.fetch(date)).thenReturn(Optional.empty());

    job.importForDate(date);

    verify(repository, never()).save(any());
  }

  @Test
  void runImport_processesMultipleDays() {
    when(source.fetch(any())).thenReturn(Optional.empty());

    job.runImport();

    verify(source, times(7)).fetch(any());
  }

  @Test
  void runImport_continuesOnError() {
    when(source.fetch(any())).thenThrow(new RuntimeException("S3 error"));

    job.runImport();

    verify(source, times(7)).fetch(any());
  }

  @Test
  void importForDate_throwsRuntimeException_whenParsingFails() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    InputStream failingStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Stream error");
          }
        };
    when(source.fetch(date)).thenReturn(Optional.of(failingStream));

    assertThatThrownBy(() -> job.importForDate(date))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Fund position import failed");
  }
}
