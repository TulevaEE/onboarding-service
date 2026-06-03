package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_EMPLOYER;
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_INSURANCE;
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_PERSON;
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_TRANSFER;
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_WITHDRAWAL;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ThirdPillarAlertEvaluator {

  private static final String INHERITANCE_SOURCE = "Pärimine";
  private static final String DEPOSIT_PERSON_SOURCE = "Osakute väljalase isikult laekumiste alusel";
  private static final String DEPOSIT_EMPLOYER_SOURCE =
      "Osakute väljalase tööandjalt laekumiste alusel";
  private static final String DEPOSIT_TRANSFER_SOURCE = "Osakute vahetamine (väljalase) 3. sammas";
  private static final String DEPOSIT_INSURANCE_SOURCE =
      "Osakute väljalase kindlustuslepingust laekumiste alusel";
  private static final Set<String> WITHDRAWAL_TYPES =
      Set.of("Osakute ülekanne", "Broneeritud osakute kustutamine");

  private static final BigDecimal DEPOSIT_THRESHOLD = new BigDecimal("6001");
  private static final BigDecimal TRANSFER_THRESHOLD = new BigDecimal("20001");
  private static final BigDecimal WITHDRAWAL_THRESHOLD = new BigDecimal("20001");

  public List<AmlAlertType> evaluate(AnalyticsThirdPillarTransaction transaction) {
    String source = transaction.getTransactionSource();
    if (INHERITANCE_SOURCE.equals(source)) {
      return List.of();
    }

    String type = transaction.getTransactionType();
    BigDecimal amount = transaction.getTransactionValue();
    var alerts = new ArrayList<AmlAlertType>();

    if (DEPOSIT_PERSON_SOURCE.equals(source) && atLeast(amount, DEPOSIT_THRESHOLD)) {
      alerts.add(III_PILLAR_DEPOSIT_PERSON);
    }
    if (DEPOSIT_EMPLOYER_SOURCE.equals(source) && atLeast(amount, DEPOSIT_THRESHOLD)) {
      alerts.add(III_PILLAR_DEPOSIT_EMPLOYER);
    }
    if (DEPOSIT_TRANSFER_SOURCE.equals(source) && atLeast(amount, TRANSFER_THRESHOLD)) {
      alerts.add(III_PILLAR_DEPOSIT_TRANSFER);
    }
    if (DEPOSIT_INSURANCE_SOURCE.equals(source) && atLeast(amount, TRANSFER_THRESHOLD)) {
      alerts.add(III_PILLAR_DEPOSIT_INSURANCE);
    }
    if (WITHDRAWAL_TYPES.contains(type) && atLeast(amount, WITHDRAWAL_THRESHOLD)) {
      alerts.add(III_PILLAR_WITHDRAWAL);
    }
    return alerts;
  }

  private static boolean atLeast(BigDecimal amount, BigDecimal threshold) {
    return amount.compareTo(threshold) >= 0;
  }
}
