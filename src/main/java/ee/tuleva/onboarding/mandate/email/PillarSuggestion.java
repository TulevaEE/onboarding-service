package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.analytics.RecurringPayments;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Getter
public class PillarSuggestion {

  private final boolean suggestPaymentRate;
  private final boolean suggestThirdPillar;
  private final boolean suggestSecondPillar;
  private final boolean suggestMembership;
  private final boolean leftSecondPillar;
  private final boolean suggestSavingsFund;
  private final boolean suggestThirdPillarRecurringPayment;
  private final boolean suggestThirdPillarRaise;
  private final boolean suggestSavingsFundRecurringPayment;

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates) {
    this(
        user,
        contactDetails,
        conversion,
        paymentRates,
        Set.of(),
        false,
        true,
        false,
        new RecurringPayments(true, true));
  }

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates,
      Set<Integer> mandatePillars,
      boolean leftSecondPillar) {
    this(
        user,
        contactDetails,
        conversion,
        paymentRates,
        mandatePillars,
        leftSecondPillar,
        true,
        false,
        new RecurringPayments(true, true));
  }

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates,
      Set<Integer> mandatePillars,
      boolean leftSecondPillar,
      boolean savesInSavingsFund) {
    this(
        user,
        contactDetails,
        conversion,
        paymentRates,
        mandatePillars,
        leftSecondPillar,
        savesInSavingsFund,
        false,
        new RecurringPayments(true, true));
  }

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates,
      Set<Integer> mandatePillars,
      boolean leftSecondPillar,
      boolean savesInSavingsFund,
      boolean concernsPaymentRate) {
    this(
        user,
        contactDetails,
        conversion,
        paymentRates,
        mandatePillars,
        leftSecondPillar,
        savesInSavingsFund,
        concernsPaymentRate,
        new RecurringPayments(true, true));
  }

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates,
      Set<Integer> mandatePillars,
      boolean leftSecondPillar,
      boolean savesInSavingsFund,
      boolean concernsPaymentRate,
      RecurringPayments recurringPayments) {
    this(
        user,
        contactDetails,
        conversion,
        paymentRates,
        mandatePillars,
        leftSecondPillar,
        savesInSavingsFund,
        concernsPaymentRate,
        recurringPayments,
        false);
  }

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates,
      Set<Integer> mandatePillars,
      boolean leftSecondPillar,
      boolean savesInSavingsFund,
      boolean concernsPaymentRate,
      RecurringPayments recurringPayments,
      boolean thirdPillarTaxHeadroom) {
    this.leftSecondPillar = leftSecondPillar;
    boolean adult = user.getAge() >= 18;
    this.suggestPaymentRate =
        adult
            && !leftSecondPillar
            && !concernsPaymentRate
            && contactDetails.isSecondPillarActive()
            && paymentRates.canIncrease();
    this.suggestSecondPillar =
        adult
            && !leftSecondPillar
            && !mandatePillars.contains(2)
            && (!contactDetails.isSecondPillarActive()
                || !conversion.isSecondPillarPartiallyConverted()
                || (conversion.getSecondPillarWeightedAverageFee() != null
                    && conversion
                            .getSecondPillarWeightedAverageFee()
                            .compareTo(new BigDecimal("0.005"))
                        > 0));
    this.suggestThirdPillar =
        !mandatePillars.contains(3)
            && (!contactDetails.isThirdPillarActive()
                || !conversion.isThirdPillarPartiallyConverted()
                || (conversion.getThirdPillarWeightedAverageFee() != null
                    && conversion
                            .getThirdPillarWeightedAverageFee()
                            .compareTo(new BigDecimal("0.005"))
                        > 0));
    this.suggestMembership = !user.isMember();
    this.suggestThirdPillarRecurringPayment =
        adult && contactDetails.isThirdPillarActive() && !recurringPayments.thirdPillar();
    this.suggestThirdPillarRaise =
        adult
            && contactDetails.isThirdPillarActive()
            && recurringPayments.thirdPillar()
            && thirdPillarTaxHeadroom;
    this.suggestSavingsFund =
        adult
            && !savesInSavingsFund
            && !suggestSecondPillar
            && !suggestPaymentRate
            && !suggestThirdPillar
            && !suggestThirdPillarRecurringPayment
            && !suggestThirdPillarRaise;
    this.suggestSavingsFundRecurringPayment =
        adult && savesInSavingsFund && !recurringPayments.savingsFund();
  }

  public Optional<String> renderedNudgeTag() {
    if (suggestSecondPillar) return Optional.of("nudge_second_pillar");
    if (suggestPaymentRate) return Optional.of("nudge_payment_rate");
    if (suggestThirdPillar) return Optional.of("nudge_third_pillar");
    if (suggestThirdPillarRecurringPayment) return Optional.of("nudge_third_pillar_recurring");
    if (suggestThirdPillarRaise) return Optional.of("nudge_third_pillar_raise");
    if (suggestSavingsFund) return Optional.of("nudge_savings_fund");
    if (suggestSavingsFundRecurringPayment) return Optional.of("nudge_savings_fund_recurring");
    if (suggestMembership) return Optional.of("nudge_membership");
    return Optional.of("nudge_none");
  }
}
