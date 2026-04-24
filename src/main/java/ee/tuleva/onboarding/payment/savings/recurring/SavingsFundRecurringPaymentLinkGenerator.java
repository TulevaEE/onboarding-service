package ee.tuleva.onboarding.payment.savings.recurring;

import static ee.tuleva.onboarding.payment.recurring.PaymentDateProvider.format;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import ee.tuleva.onboarding.payment.recurring.PaymentDateProvider;
import ee.tuleva.onboarding.payment.savings.SavingsFundRecipientConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundRecurringPaymentLinkGenerator implements PaymentLinkGenerator {

  private static final String SWEDBANK_BANK_CODE = "22";

  private final SavingsFundRecipientConfiguration savingsFundConfiguration;
  private final PaymentDateProvider paymentDateProvider;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    var personalCode = person.getPersonalCode();
    var amount = paymentData.getAmount().toPlainString();
    var firstPaymentDate = paymentDateProvider.tenthDayOfMonth();

    var url =
        switch (paymentData.getPaymentChannel()) {
          case LHV -> buildLhvUrl(personalCode, amount, firstPaymentDate);
          case COOP, COOP_WEB, PARTNER -> buildCoopUrl(personalCode, amount, firstPaymentDate);
          case SWEDBANK -> buildSwedbankUrl(personalCode, amount, person);
          case SEB -> "https://e.seb.ee/ib/p/payments/new-standing-order";
          case LUMINOR -> "https://luminor.ee/auth/#/web/view/autopilot/newpayment";
          case OTHER -> null;
          case TULUNDUSUHISTU ->
              throw new IllegalArgumentException(
                  "Recurring savings fund payments to the specified payment channel are not"
                      + " supported");
        };

    return new PaymentLink(
        url,
        savingsFundConfiguration.getRecipientName(),
        savingsFundConfiguration.getRecipientIban(),
        personalCode,
        amount);
  }

  private String buildLhvUrl(String personalCode, String amount, LocalDate firstPaymentDate) {
    var params = new LinkedHashMap<String, String>();
    params.put("i_receiver_name", savingsFundConfiguration.getRecipientName());
    params.put("i_receiver_account_no", savingsFundConfiguration.getRecipientIban());
    params.put("i_payment_desc", personalCode);
    params.put("i_amount", amount);
    params.put("i_currency_id", "38");
    params.put("i_interval_type", "K");
    params.put("i_date_first_payment", format(firstPaymentDate));
    return "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?" + encode(params);
  }

  private String buildCoopUrl(String personalCode, String amount, LocalDate firstPaymentDate) {
    var params = new LinkedHashMap<String, String>();
    params.put("whatform", "PermPaymentNew");
    params.put("SaajaNimi", savingsFundConfiguration.getRecipientName());
    params.put("SaajaKonto", savingsFundConfiguration.getRecipientIban());
    params.put("MakseSumma", amount);
    params.put("MaksePohjus", personalCode);
    params.put("MakseSagedus", "3");
    params.put("MakseEsimene", format(firstPaymentDate));
    return "https://i.cooppank.ee/newpmt?" + encode(params);
  }

  private String buildSwedbankUrl(String personalCode, String amount, Person person) {
    var params = new LinkedHashMap<String, String>();
    params.put(
        "standingOrder.beneficiaryAccountNumber", savingsFundConfiguration.getRecipientIban());
    params.put("standingOrder.beneficiaryName", savingsFundConfiguration.getRecipientName());
    params.put("standingOrder.amount", amount);
    params.put("standingOrder.details", personalCode);
    params.put("frequency", "K");
    swedbankAccountId(person).ifPresent(accountId -> params.put("account", accountId));
    return "https://www.swedbank.ee/private/d2d/payments2/standing_order/new?" + encode(params);
  }

  private Optional<String> swedbankAccountId(Person person) {
    return savingFundPaymentRepository
        .findLastRemitterIban(new PartyId(PartyId.Type.PERSON, person.getPersonalCode()))
        .filter(SavingsFundRecurringPaymentLinkGenerator::isSwedbankIban)
        .map(iban -> iban.substring(8));
  }

  private static boolean isSwedbankIban(String iban) {
    return iban != null
        && iban.length() >= 6
        && iban.startsWith("EE")
        && iban.substring(4, 6).equals(SWEDBANK_BANK_CODE);
  }

  private static String encode(Map<String, String> params) {
    var sb = new StringBuilder();
    for (var entry : params.entrySet()) {
      if (!sb.isEmpty()) {
        sb.append('&');
      }
      sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8).replace("+", "%20"));
      sb.append('=');
      sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
    }
    return sb.toString();
  }
}
