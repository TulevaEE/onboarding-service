package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP_WEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;

import java.util.Map;

public final class ThirdPillarRecipientConfigurationFixture {

  private ThirdPillarRecipientConfigurationFixture() {}

  public static ThirdPillarRecipientConfiguration thirdPillarRecipientConfiguration() {
    var config = new ThirdPillarRecipientConfiguration();
    config.setRecipientName("AS Pensionikeskus");
    config.setDescription("30101119828, EE3600001707");
    config.setBankAccounts(
        Map.of(
            LHV, "EE547700771002908125",
            SEB, "EE141010220263146225",
            SWEDBANK, "EE362200221067235244",
            COOP, "EE362200221067235244",
            COOP_WEB, "EE362200221067235244",
            PARTNER, "EE362200221067235244"));
    return config;
  }
}
