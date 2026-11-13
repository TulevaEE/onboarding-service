package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Client.UNKNOWN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Code.MISSING;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Minor.NOT_NAMED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name.DIFFERS;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Onboarding.INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.FREEZE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETRY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_CODE_MISMATCH;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_NAME_MISMATCH;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_NOT_A_CLIENT;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_NOT_ONBOARDED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_UNIDENTIFIED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.VERIFY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.ANOTHER_COMPANY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.ANOTHER_PERSON;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.COMPANY_NAME_WITHOUT_CODE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.NO_CODE;

import ee.tuleva.onboarding.aml.ScreeningOutcome;

public final class PaymentAcceptance {

  public enum Outcome {
    RETURN_UNIDENTIFIED,
    RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE,
    RETURN_CODE_MISMATCH,
    RETURN_NOT_A_CLIENT,
    RETURN_NAME_MISMATCH,
    RETURN_NOT_ONBOARDED,
    FREEZE,
    RETRY,
    VERIFY
  }

  public enum Code {
    FOUND,
    MISSING
  }

  public enum Remitter {
    HOLDER,
    GUARDIAN,
    ANOTHER_PERSON,
    ANOTHER_COMPANY,
    COMPANY_NAME_WITHOUT_CODE,
    NO_CODE,
    NOT_CHECKED
  }

  public enum Minor {
    NAMED_IN_DESCRIPTION,
    NOT_NAMED,
    NOT_CHECKED
  }

  public enum Client {
    KNOWN,
    UNKNOWN,
    NOT_CHECKED
  }

  public enum Name {
    MATCHES,
    DIFFERS,
    NOT_CHECKED
  }

  public enum Onboarding {
    COMPLETED,
    INCOMPLETE,
    NOT_CHECKED
  }

  public enum Screening {
    CLEAR,
    PEP_HIT,
    SANCTION_HIT,
    UNAVAILABLE,
    NOT_CHECKED;

    static Screening of(ScreeningOutcome outcome) {
      return switch (outcome) {
        case CLEAR -> CLEAR;
        case PEP_HIT -> PEP_HIT;
        case SANCTION_HIT -> SANCTION_HIT;
        case UNAVAILABLE -> UNAVAILABLE;
      };
    }
  }

  public record Payment(
      Code code,
      Remitter remitter,
      Minor minor,
      Client client,
      Name name,
      Onboarding onboarding,
      Screening screening) {

    static Payment identified(Code code) {
      return new Payment(
          code,
          Remitter.NOT_CHECKED,
          Minor.NOT_CHECKED,
          Client.NOT_CHECKED,
          Name.NOT_CHECKED,
          Onboarding.NOT_CHECKED,
          Screening.NOT_CHECKED);
    }

    Payment withRemitter(Remitter remitter) {
      return new Payment(code, remitter, minor, client, name, onboarding, screening);
    }

    Payment withMinor(Minor minor) {
      return new Payment(code, remitter, minor, client, name, onboarding, screening);
    }

    Payment withParty(Client client, Name name) {
      return new Payment(code, remitter, minor, client, name, onboarding, screening);
    }

    Payment withOnboarding(Onboarding onboarding) {
      return new Payment(code, remitter, minor, client, name, onboarding, screening);
    }

    Payment withScreening(Screening screening) {
      return new Payment(code, remitter, minor, client, name, onboarding, screening);
    }
  }

  private PaymentAcceptance() {}

  public static Outcome outcome(Payment payment) {
    if (payment.code() == MISSING) {
      return RETURN_UNIDENTIFIED;
    }
    if (companyPaysIntoAnotherAccount(payment)) {
      return RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE;
    }
    if (anotherPersonPaysIntoAnAdultsAccount(payment)) {
      return RETURN_CODE_MISMATCH;
    }
    if (payment.client() == UNKNOWN) {
      return RETURN_NOT_A_CLIENT;
    }
    if (nameDiffersWithoutARemitterCode(payment)) {
      return RETURN_NAME_MISMATCH;
    }
    if (payment.onboarding() == INCOMPLETE) {
      return RETURN_NOT_ONBOARDED;
    }
    return switch (payment.screening()) {
      case SANCTION_HIT -> FREEZE;
      case UNAVAILABLE -> RETRY;
      case CLEAR, PEP_HIT, NOT_CHECKED -> VERIFY;
    };
  }

  private static boolean companyPaysIntoAnotherAccount(Payment payment) {
    return payment.remitter() == ANOTHER_COMPANY || payment.remitter() == COMPANY_NAME_WITHOUT_CODE;
  }

  private static boolean anotherPersonPaysIntoAnAdultsAccount(Payment payment) {
    return payment.minor() == NOT_NAMED && payment.remitter() == ANOTHER_PERSON;
  }

  private static boolean nameDiffersWithoutARemitterCode(Payment payment) {
    return payment.minor() == NOT_NAMED
        && payment.remitter() == NO_CODE
        && payment.name() == DIFFERS;
  }
}
