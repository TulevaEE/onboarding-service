package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.PublishedFigure;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({OcfPublicationRepository.class, KidOngoingChargesFigures.class})
class KidOngoingChargesFiguresIntegrationTest {

  private static final LocalDate SEPTEMBER_1 = LocalDate.parse("2026-09-01");
  private static final LocalDate OCTOBER_1 = LocalDate.parse("2026-10-01");
  private static final LocalDate NOVEMBER_2 = LocalDate.parse("2026-11-02");
  private static final LocalDate DECEMBER_1 = LocalDate.parse("2026-12-01");

  @Autowired private JdbcClient jdbcClient;
  @Autowired private KidOngoingChargesFigures figures;

  @Test
  void aKidPrintingTwoDecimalsOfAPercentGivesTheFigureToFourDecimalsOfAFraction() {
    insertKid(TUK75, OCTOBER_1, "0.00459700", 2);

    assertThat(figures.figureInForceOn(TUK75, NOVEMBER_2)).contains(kidFigure("0.0046", 2));
  }

  @Test
  void aKidPrintingOneDecimalOfAPercentGivesTheFigureToThreeDecimalsOfAFraction() {
    insertKid(TKF100, OCTOBER_1, "0.00459700", 1);

    assertThat(figures.figureInForceOn(TKF100, NOVEMBER_2)).contains(kidFigure("0.005", 1));
  }

  @Test
  void aTwoDecimalKidPrintingAWholeTenthOfAPercentKeepsItsTwoDecimals() {
    insertKid(TUK75, OCTOBER_1, "0.00500000", 2);

    assertThat(figures.figureInForceOn(TUK75, NOVEMBER_2)).contains(kidFigure("0.0050", 2));
  }

  @Test
  void aFigureHalfwayBetweenTwoPrintedOnesRoundsUp() {
    insertKid(TUK75, OCTOBER_1, "0.00445000", 2);

    assertThat(figures.figureInForceOn(TUK75, NOVEMBER_2)).contains(kidFigure("0.0045", 2));
  }

  @Test
  void theKidWithTheLatestValidFromOnOrBeforeTheDayIsInForce() {
    insertKid(TUK75, SEPTEMBER_1, "0.00520000", 2);
    insertKid(TUK75, OCTOBER_1, "0.00460000", 2);
    insertKid(TUK75, DECEMBER_1, "0.00500000", 2);

    assertThat(figures.figureInForceOn(TUK75, OCTOBER_1.minusDays(1)))
        .contains(kidFigure("0.0052", 2));
    assertThat(figures.figureInForceOn(TUK75, NOVEMBER_2)).contains(kidFigure("0.0046", 2));
    assertThat(figures.figureInForceOn(TUK75, DECEMBER_1)).contains(kidFigure("0.0050", 2));
  }

  @Test
  void aKidValidFromAfterTheDayIsNotYetInForce() {
    insertKid(TUK75, DECEMBER_1, "0.00460000", 2);

    assertThat(figures.figureInForceOn(TUK75, NOVEMBER_2)).isEmpty();
  }

  @Test
  void anotherFundsKidIsNotThisFundsFigure() {
    insertKid(TUK00, OCTOBER_1, "0.00460000", 2);

    assertThat(figures.figureInForceOn(TUK75, NOVEMBER_2)).isEmpty();
  }

  private static PublishedFigure kidFigure(String figure, int decimals) {
    return new PublishedFigure(new BigDecimal(figure), decimals);
  }

  private void insertKid(
      TulevaFund fund, LocalDate validFrom, String publishedOcf, int publishedDecimals) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               published_in, published_by)
            VALUES (:fundCode, :validFrom, :publishedOcf, :publishedDecimals, 'MONTHLY_ESTIMATE',
                    'the KID in force', :publishedIn, 'portfolio-manager')
            """)
        .param("fundCode", fund.getCode())
        .param("validFrom", validFrom)
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .param("publishedDecimals", publishedDecimals)
        .param("publishedIn", "KID " + validFrom)
        .update();
  }
}
