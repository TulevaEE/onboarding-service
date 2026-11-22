package ee.tuleva.onboarding.investment.kid;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

final class KidOcfFigures {

  private KidOcfFigures() {}

  static long inForceFrom(JdbcClient jdbcClient, LocalDate validFrom, String publishedOcf) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               published_in, published_by)
            VALUES
              ('TKF100', :validFrom, :publishedOcf, 2, 'MONTHLY_ESTIMATE', 'the KID in force',
               :publishedIn, 'portfolio-manager')
            """)
        .param("validFrom", validFrom)
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .param("publishedIn", "KID " + validFrom)
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey()).longValue();
  }
}
