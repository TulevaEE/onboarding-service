package ee.tuleva.onboarding.notification.email;

import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.member.Member;
import java.util.Locale;

public enum EmailType {
  SECOND_PILLAR_MANDATE("second_pillar_mandate"),
  SECOND_PILLAR_WITHDRAWAL_CANCELLATION("second_pillar_withdrawal_cancellation"),
  SECOND_PILLAR_TRANSFER_CANCELLATION("second_pillar_transfer_cancellation"),
  SECOND_PILLAR_PAYMENT_RATE("second_pillar_payment_rate"),

  SECOND_PILLAR_LEAVERS("second_pillar_leavers"),
  SECOND_PILLAR_EARLY_WITHDRAWAL("second_pillar_early_withdrawal"),
  PAYMENT_RATE_ABANDONMENT("payment_rate_abandonment"),
  SECOND_PILLAR_ABANDONMENT("second_pillar_abandonment"),

  THIRD_PILLAR_SUGGEST_SECOND("third_pillar_suggest_second"),
  THIRD_PILLAR_PAYMENT_REMINDER_MANDATE("third_pillar_payment_reminder_mandate"),
  THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE("third_pillar_payment_success_mandate"),
  THIRD_PILLAR_PAYMENT_ARRIVED("third_pillar_payment_arrived"),

  MEMBERSHIP("membership"),
  BATCH_FAILED("batch_failed"),

  WITHDRAWAL_BATCH("withdrawal_batch"),

  LISTING_REPLY_TO_SELLER("listing_reply_to_seller"),
  LISTING_EXPIRES("listing_expires"),
  LISTING_REPLY_TO_BUYER("listing_reply_to_buyer"),
  CAPITAL_TRANSFER_BUYER_TO_SIGN("capital_transfer_buyer_to_sign"),
  CAPITAL_TRANSFER_CONFIRMED_BY_BUYER("capital_transfer_confirmed_by_buyer"),
  CAPITAL_TRANSFER_CONFIRMED_BY_SELLER("capital_transfer_confirmed_by_seller"),
  CAPITAL_TRANSFER_APPROVED_BY_BOARD("capital_transfer_approved_by_board"),

  SAVINGS_FUND_PAYMENT_SUCCESS("savings_fund_payment_success"),
  SAVINGS_FUND_PAYMENT_SUCCESS_PERSON("savings_fund_payment_success_person"),
  SAVINGS_FUND_PAYMENT_SUCCESS_CHILD("savings_fund_payment_success_child"),
  SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY("savings_fund_payment_success_company"),
  SAVINGS_FUND_PAYMENT_CANCEL("savings_fund_payment_cancelled"),
  SAVINGS_FUND_PAYMENT_FAIL("savings_fund_payment_failed"),
  SAVINGS_FUND_COMPANY_ONBOARDED("savings_fund_company_onboarded"),

  PARENT_CHILD_LINK_CONFIRMATION("parent_child_link_confirmation"),
  PARENT_CHILD_LINK_ADDED("parent_child_link_added"),

  MAILCHIMP_CAMPAIGN("mailchimp_campaign"),

  HACKATHON_REGISTRATION("hackathon_registration"),
  ;

  private final String templateName;

  EmailType(String templateName) {
    this.templateName = templateName;
  }

  public static EmailType from(Payment payment) {
    if (PaymentData.PaymentType.SAVINGS.equals(payment.getPaymentType())) {
      return SAVINGS_FUND_PAYMENT_SUCCESS;
    }
    return THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE;
  }

  public static EmailType from(Member member) {
    return MEMBERSHIP;
  }

  public String getTemplateName(Locale locale) {
    return getTemplateName(locale.getLanguage());
  }

  public String getTemplateName(String language) {
    return templateName + "_" + language;
  }
}
