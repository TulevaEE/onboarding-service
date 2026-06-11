package ee.tuleva.onboarding.payment.savings.recurring;

import static ee.tuleva.onboarding.payment.PaymentDateProvider.format;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.CoopLanguage;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentDateProvider;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import ee.tuleva.onboarding.payment.PaymentUrlEncoder;
import ee.tuleva.onboarding.payment.PrefilledLink;
import ee.tuleva.onboarding.payment.savings.SavingsFundRecipientConfiguration;
import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundRecurringPaymentLinkGenerator implements PaymentLinkGenerator {

  private static final String COOP_STANDING_ORDER_URL =
      "https://i.cooppank.ee/i/standing-orders/new";
  private static final String COOP_MONTHLY_FREQ = "2";
  private static final String SWEDBANK_PRIVATE_STANDING_ORDER_URL =
      "https://www.swedbank.ee/private/d2d/payments2/standing_order/new_foreign?";
  private static final String SWEDBANK_BUSINESS_STANDING_ORDER_URL =
      "https://www.swedbank.ee/business/d2d/payments/standing_order?language=EST";

  private static final PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();

  private final SavingsFundRecipientConfiguration recipientConfiguration;
  private final PaymentDateProvider paymentDateProvider;
  private final LocaleService localeService;

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    var description = paymentData.getRecipientPersonalCode();
    var amount = paymentData.getAmount() == null ? null : paymentData.getAmount().toPlainString();
    var firstPaymentDate = paymentDateProvider.tenthDayOfMonth();

    var channel = paymentData.getPaymentChannel();
    var url =
        channel == null
            ? null
            : switch (channel) {
              case LHV -> buildLhvUrl(description, amount, firstPaymentDate);
              case COOP, COOP_WEB, PARTNER -> buildCoopUrl(description, amount, firstPaymentDate);
              case SWEDBANK -> buildSwedbankUrl(description, amount, firstPaymentDate);
              case SEB -> "https://e.seb.ee/ib/p/payments/new-standing-order";
              case LUMINOR -> "https://luminor.ee/auth/#/web/view/autopilot/newpayment";
              case TULUNDUSUHISTU ->
                  throw new ErrorsResponseException(
                      ErrorsResponse.ofSingleError(
                          "payment.channel.not.supported",
                          "Recurring savings fund payments to the specified payment channel are"
                              + " not supported."));
            };

    return new PrefilledLink(
        url,
        recipientConfiguration.getRecipientName(),
        recipientConfiguration.getRecipientIban(),
        description,
        amount);
  }

  private String buildLhvUrl(
      String description, @Nullable String amount, LocalDate firstPaymentDate) {
    var params = new LinkedHashMap<String, String>();
    params.put("i_receiver_name", recipientConfiguration.getRecipientName());
    params.put("i_receiver_account_no", recipientConfiguration.getRecipientIban());
    params.put("i_payment_desc", description);
    if (amount != null) {
      params.put("i_amount", amount);
    }
    params.put("i_currency_id", "38");
    params.put("i_interval_type", "K");
    params.put("i_date_first_payment", format(firstPaymentDate));
    return "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?"
        + PaymentUrlEncoder.encode(params);
  }

  private String buildCoopUrl(
      String description, @Nullable String amount, LocalDate firstPaymentDate) {
    var params = new LinkedHashMap<String, String>();
    params.put("bname", recipientConfiguration.getRecipientName());
    params.put("bacc", recipientConfiguration.getRecipientIban());
    if (amount != null) {
      params.put("amt", amount);
    }
    params.put("cur", "EUR");
    params.put("desc", description);
    params.put("date", format(firstPaymentDate));
    params.put("freq", COOP_MONTHLY_FREQ);
    params.put("lang", CoopLanguage.code(localeService.getCurrentLanguage()));
    return COOP_STANDING_ORDER_URL + "?" + PaymentUrlEncoder.encode(params);
  }

  private String buildSwedbankUrl(
      String description, @Nullable String amount, LocalDate firstPaymentDate) {
    if (isLegalEntity(description)) {
      return SWEDBANK_BUSINESS_STANDING_ORDER_URL;
    }
    var params = new LinkedHashMap<String, String>();
    params.put("standingOrder.beneficiaryAccountNumber", recipientConfiguration.getRecipientIban());
    params.put("standingOrder.beneficiaryName", recipientConfiguration.getRecipientName());
    if (amount != null) {
      params.put("standingOrder.amount", amount);
    }
    params.put("standingOrder.details", description);
    params.put("standingOrder.firstPaymentDate", format(firstPaymentDate));
    params.put("frequency", "K");
    return SWEDBANK_PRIVATE_STANDING_ORDER_URL + PaymentUrlEncoder.encode(params);
  }

  private static boolean isLegalEntity(String recipientCode) {
    return !personalCodeValidator.isValid(recipientCode);
  }
}
