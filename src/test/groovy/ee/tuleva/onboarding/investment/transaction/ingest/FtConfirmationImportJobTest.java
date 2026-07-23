package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtConfirmationImportJobTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-09T08:00:00Z"), ZoneOffset.UTC);
  private static final String KEY_1 = "ft-confirmations/a.pdf";
  private static final String KEY_2 = "ft-confirmations/b.pdf";

  @Mock private FtConfirmationS3Source s3Source;
  @Mock private FtConfirmationPdfParser parser;
  @Mock private FtConfirmationVerificationService verificationService;

  private FtConfirmationImportJob job;

  @BeforeEach
  void setUp() {
    job = new FtConfirmationImportJob(s3Source, parser, verificationService, FIXED_CLOCK);
  }

  @Test
  void run_parsesAndVerifiesEachListedConfirmation() {
    byte[] bytes1 = "pdf-1".getBytes();
    byte[] bytes2 = "pdf-2".getBytes();
    FtConfirmation confirmation1 = confirmation("IE00BFG1TM61");
    FtConfirmation confirmation2 = confirmation("IE000I9HGDZ3");
    given(s3Source.list()).willReturn(List.of(KEY_1, KEY_2));
    given(s3Source.get(KEY_1)).willReturn(Optional.of(bytes1));
    given(s3Source.get(KEY_2)).willReturn(Optional.of(bytes2));
    given(parser.parse(bytes1)).willReturn(confirmation1);
    given(parser.parse(bytes2)).willReturn(confirmation2);
    given(verificationService.verifyAll(any(), any())).willReturn(List.of());

    job.run();

    verify(verificationService)
        .verifyAll(List.of(confirmation1, confirmation2), FtConfirmationImportJob.JOB_ACTOR);
  }

  @Test
  void run_noNewPdfs_doesNothing() {
    given(s3Source.list()).willReturn(List.of());

    job.run();

    verifyNoInteractions(parser, verificationService);
  }

  @Test
  void run_objectDisappearedBeforeFetch_skipsItAndVerifiesTheRest() {
    byte[] bytes2 = "pdf-2".getBytes();
    FtConfirmation confirmation2 = confirmation("IE000I9HGDZ3");
    given(s3Source.list()).willReturn(List.of(KEY_1, KEY_2));
    given(s3Source.get(KEY_1)).willReturn(Optional.empty());
    given(s3Source.get(KEY_2)).willReturn(Optional.of(bytes2));
    given(parser.parse(bytes2)).willReturn(confirmation2);
    given(verificationService.verifyAll(any(), any())).willReturn(List.of());

    job.run();

    verify(verificationService)
        .verifyAll(List.of(confirmation2), FtConfirmationImportJob.JOB_ACTOR);
  }

  @Test
  void run_unparsablePdf_skipsItAndVerifiesTheRest() {
    byte[] bytes1 = "bad-pdf".getBytes();
    byte[] bytes2 = "pdf-2".getBytes();
    FtConfirmation confirmation2 = confirmation("IE000I9HGDZ3");
    given(s3Source.list()).willReturn(List.of(KEY_1, KEY_2));
    given(s3Source.get(KEY_1)).willReturn(Optional.of(bytes1));
    given(s3Source.get(KEY_2)).willReturn(Optional.of(bytes2));
    given(parser.parse(bytes1)).willThrow(new FtConfirmationPdfParseException("malformed"));
    given(parser.parse(bytes2)).willReturn(confirmation2);
    given(verificationService.verifyAll(any(), any())).willReturn(List.of());

    job.run();

    verify(verificationService)
        .verifyAll(List.of(confirmation2), FtConfirmationImportJob.JOB_ACTOR);
  }

  @Test
  void run_allPdfsFailToParse_doesNotCallVerifyAll() {
    byte[] bytes1 = "bad-pdf".getBytes();
    given(s3Source.list()).willReturn(List.of(KEY_1));
    given(s3Source.get(KEY_1)).willReturn(Optional.of(bytes1));
    given(parser.parse(bytes1)).willThrow(new FtConfirmationPdfParseException("malformed"));

    job.run();

    verify(verificationService, never()).verifyAll(any(), any());
  }

  @Test
  void run_verifyAllThrows_doesNotPropagate() {
    byte[] bytes1 = "pdf-1".getBytes();
    FtConfirmation confirmation1 = confirmation("IE00BFG1TM61");
    given(s3Source.list()).willReturn(List.of(KEY_1));
    given(s3Source.get(KEY_1)).willReturn(Optional.of(bytes1));
    given(parser.parse(bytes1)).willReturn(confirmation1);
    given(verificationService.verifyAll(any(), any())).willThrow(new RuntimeException("db down"));

    assertThat(job).isNotNull();
    job.run();
  }

  private static FtConfirmation confirmation(String isin) {
    return new FtConfirmation(
        TulevaFund.TKF100,
        isin,
        LocalDate.of(2026, 1, 15),
        new BigDecimal("2500"),
        new BigDecimal("12.345600"));
  }
}
