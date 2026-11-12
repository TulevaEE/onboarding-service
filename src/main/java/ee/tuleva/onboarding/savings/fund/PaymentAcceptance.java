package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Client.KNOWN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Client.UNKNOWN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Code.FOUND;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Code.MISSING;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Minor.NAMED_IN_DESCRIPTION;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Minor.NOT_NAMED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name.DIFFERS;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name.MATCHES;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Onboarding.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Onboarding.INCOMPLETE;
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
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.GUARDIAN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.HOLDER;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.NO_CODE;

import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Condition;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import java.util.List;

public final class PaymentAcceptance {

  public enum Outcome {
    RETURN_UNIDENTIFIED,
    RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE,
    RETURN_CODE_MISMATCH,
    RETURN_NOT_A_CLIENT,
    RETURN_NAME_MISMATCH,
    RETURN_NOT_ONBOARDED,
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

  public record Payment(
      Code code, Remitter remitter, Minor minor, Client client, Name name, Onboarding onboarding) {

    static Payment identified(Code code) {
      return new Payment(
          code,
          Remitter.NOT_CHECKED,
          Minor.NOT_CHECKED,
          Client.NOT_CHECKED,
          Name.NOT_CHECKED,
          Onboarding.NOT_CHECKED);
    }

    Payment withRemitter(Remitter remitter) {
      return new Payment(code, remitter, minor, client, name, onboarding);
    }

    Payment withMinor(Minor minor) {
      return new Payment(code, remitter, minor, client, name, onboarding);
    }

    Payment withParty(Client client, Name name) {
      return new Payment(code, remitter, minor, client, name, onboarding);
    }

    Payment withOnboarding(Onboarding onboarding) {
      return new Payment(code, remitter, minor, client, name, onboarding);
    }
  }

  private static final Fact<Payment> NO_CODE_FOUND =
      new Fact<>(
          new Phrase(
              "maksest ei leita kehtivat isikukoodi ega registrikoodi",
              "maksest leitakse kehtiv isikukood või registrikood",
              "kood otsitakse esmalt maksekirjeldusest, selle puudumisel võetakse pangalt saadud"
                  + " maksja kood",
              "registrikoodiga saavad sissemakseid teha ka ettevõtted"),
          payment -> payment.code() == MISSING);

  private static final Fact<Payment> COMPANY_PAYS_INTO_ANOTHER_ACCOUNT =
      new Fact<>(
          new Phrase(
              "maksja on ettevõte, kes maksab teise isiku kontole",
              "maksja ei ole teise isiku kontole maksev ettevõte",
              "kui pank maksja koodi ei edasta, loetakse maksja ettevõtteks nime järgi"),
          payment ->
              payment.remitter() == ANOTHER_COMPANY
                  || payment.remitter() == COMPANY_NAME_WITHOUT_CODE);

  private static final Fact<Payment> NO_MINOR_NAMED_IN_DESCRIPTION =
      new Fact<>(
          new Phrase(
              "maksekirjelduses ei ole piiratud teovõimega isiku isikukoodi",
              "maksekirjelduses on piiratud teovõimega isiku isikukood",
              "teovõimet vaadatakse raha laekumise päeva seisuga",
              "piiratud teovõimega isiku kontole võib maksta igaüks"),
          payment -> payment.minor() == NOT_NAMED);

  private static final Fact<Payment> REMITTER_IS_ANOTHER_PERSON =
      new Fact<>(
          new Phrase(
              "maksja on keegi muu kui osakuomanik või tema eestkostja",
              "maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta"),
          payment -> payment.remitter() == ANOTHER_PERSON);

  private static final Fact<Payment> NOT_A_CLIENT =
      new Fact<>(
          new Phrase("osakuomanikku ei leita Tuleva kliendina", "osakuomanik on Tuleva klient"),
          payment -> payment.client() == UNKNOWN);

  private static final Fact<Payment> REMITTER_WITHOUT_CODE =
      new Fact<>(
          new Phrase("pank ei edasta maksja koodi", "pank edastab maksja koodi"),
          payment -> payment.remitter() == NO_CODE);

  private static final Fact<Payment> NAME_DIFFERS =
      new Fact<>(
          new Phrase(
              "maksja nimi ei klapi osakuomaniku nimega",
              "maksja nimi klapib osakuomaniku nimega",
              "nime erinevus loeb ainult siis, kui pank maksja koodi ei edasta"),
          payment -> payment.name() == DIFFERS);

  private static final Fact<Payment> NOT_ONBOARDED =
      new Fact<>(
          new Phrase(
              "osakuomanik ei ole täiendava kogumisfondiga liitunud",
              "osakuomanik on täiendava kogumisfondiga liitunud",
              "liitumine on lõpetatud, kui osakuomanik on läbinud Tuleva hoolsusmeetmed",
              "sissemakse vastuvõtmisel sanktsiooni- ega PEP-kontrolli ei korrata"),
          payment -> payment.onboarding() == INCOMPLETE);

  private static final Condition<Payment> CODE_MISMATCH =
      and(NO_MINOR_NAMED_IN_DESCRIPTION, REMITTER_IS_ANOTHER_PERSON);

  private static final Condition<Payment> NAME_MISMATCH =
      and(NO_MINOR_NAMED_IN_DESCRIPTION, REMITTER_WITHOUT_CODE, NAME_DIFFERS);

  public static final Decision<Payment, Outcome> SISEKORD_16_P_1_2_4 =
      new Decision<>(
          "1.2.4",
          "Sissemakse vastuvõtmine või tagastamine maksjale (bounce-back)",
          List.of(
              new Branch<>(RETURN_UNIDENTIFIED, "makse tagastatakse", NO_CODE_FOUND),
              new Branch<>(
                  RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE,
                  "makse tagastatakse",
                  COMPANY_PAYS_INTO_ANOTHER_ACCOUNT),
              new Branch<>(RETURN_CODE_MISMATCH, "makse tagastatakse", CODE_MISMATCH),
              new Branch<>(RETURN_NOT_A_CLIENT, "makse tagastatakse", NOT_A_CLIENT),
              new Branch<>(RETURN_NAME_MISMATCH, "makse tagastatakse", NAME_MISMATCH),
              new Branch<>(RETURN_NOT_ONBOARDED, "makse tagastatakse", NOT_ONBOARDED),
              new Branch<>(
                  VERIFY,
                  "makse võetakse vastu ja kantakse osakuomaniku kontole",
                  not(
                      or(
                          NO_CODE_FOUND,
                          COMPANY_PAYS_INTO_ANOTHER_ACCOUNT,
                          CODE_MISMATCH,
                          NOT_A_CLIENT,
                          NAME_MISMATCH,
                          NOT_ONBOARDED)))),
          List.of(
              new Case<>(
                  "maksekirjelduses ega maksja andmetes ei ole kehtivat koodi",
                  Payment.identified(MISSING),
                  RETURN_UNIDENTIFIED),
              new Case<>(
                  "ettevõte maksab töötaja kontole",
                  Payment.identified(FOUND).withRemitter(ANOTHER_COMPANY),
                  RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE),
              new Case<>(
                  "koodita maksja, kelle nimi on ettevõtte nimi, maksab isiku kontole",
                  Payment.identified(FOUND).withRemitter(COMPANY_NAME_WITHOUT_CODE),
                  RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE),
              new Case<>(
                  "teine isik maksab täisealise osakuomaniku kontole",
                  Payment.identified(FOUND).withRemitter(ANOTHER_PERSON).withMinor(NOT_NAMED),
                  RETURN_CODE_MISMATCH),
              new Case<>(
                  "vanavanem maksab alaealise lapselapse kontole",
                  accepted(ANOTHER_PERSON, NAMED_IN_DESCRIPTION, DIFFERS),
                  VERIFY),
              new Case<>(
                  "eestkostja maksab hoolealuse kontole",
                  accepted(GUARDIAN, NAMED_IN_DESCRIPTION, DIFFERS),
                  VERIFY),
              new Case<>(
                  "osakuomanikku ei leita Tuleva kliendina",
                  Payment.identified(FOUND)
                      .withRemitter(HOLDER)
                      .withMinor(NOT_NAMED)
                      .withParty(UNKNOWN, Name.NOT_CHECKED),
                  RETURN_NOT_A_CLIENT),
              new Case<>(
                  "pank ei edasta maksja koodi ja nimi ei klapi",
                  accepted(NO_CODE, NOT_NAMED, DIFFERS),
                  RETURN_NAME_MISMATCH),
              new Case<>(
                  "pank ei edasta maksja koodi, nimi klapib",
                  accepted(NO_CODE, NOT_NAMED, MATCHES),
                  VERIFY),
              new Case<>(
                  "pank edastab osakuomaniku koodi, nimi ei klapi",
                  accepted(HOLDER, NOT_NAMED, DIFFERS),
                  VERIFY),
              new Case<>(
                  "osakuomanik maksab ise, kuid ei ole fondiga liitunud",
                  accepted(HOLDER, NOT_NAMED, MATCHES).withOnboarding(INCOMPLETE),
                  RETURN_NOT_ONBOARDED),
              new Case<>(
                  "osakuomanik maksab ise oma pangakontolt",
                  accepted(HOLDER, NOT_NAMED, MATCHES),
                  VERIFY)));

  private PaymentAcceptance() {}

  private static Payment accepted(Remitter remitter, Minor minor, Name name) {
    return Payment.identified(FOUND)
        .withRemitter(remitter)
        .withMinor(minor)
        .withParty(KNOWN, name)
        .withOnboarding(COMPLETED);
  }
}
