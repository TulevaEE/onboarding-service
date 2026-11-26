package ee.tuleva.onboarding.investment.transaction.export;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.server.ResponseStatusException;

@ConfigurationProperties("transaction-registry.custodian-order-email")
public record CustodianOrderEmailProperties(
    boolean enabled, List<String> cc, Venue ft, Venue sebEtf, Venue sebFund, Venue uuid) {

  public CustodianOrderEmailProperties {
    cc = cc == null ? List.of() : List.copyOf(cc);
    ft = ft == null ? Venue.UNSET : ft;
    sebEtf = sebEtf == null ? Venue.UNSET : sebEtf;
    sebFund = sebFund == null ? Venue.UNSET : sebFund;
    uuid = uuid == null ? Venue.UNSET : uuid;
  }

  public record Venue(
      List<String> to, List<String> cc, @Nullable String subject, @Nullable String text) {

    static final Venue UNSET = new Venue(List.of(), List.of(), null, null);

    public Venue {
      to = to == null ? List.of() : List.copyOf(to);
      cc = cc == null ? List.of() : List.copyOf(cc);
    }
  }

  Venue venueOf(OrderEmail email) {
    return switch (email) {
      case FT -> ft;
      case SEB_ETF -> sebEtf;
      case SEB_FUND -> sebFund;
      case UUID -> uuid;
    };
  }

  String subjectOf(OrderEmail email) {
    return required(venueOf(email).subject(), "subject", email);
  }

  String textOf(OrderEmail email) {
    return required(venueOf(email).text(), "text", email);
  }

  void requireConfigured(OrderEmail email) {
    if (venueOf(email).to().isEmpty()) {
      throw notConfigured("to", email);
    }
    if (email.takesItsTextFromConfiguration()) {
      subjectOf(email);
      textOf(email);
    }
  }

  private static String required(@Nullable String value, String what, OrderEmail email) {
    if (value == null || value.isBlank()) {
      throw notConfigured(what, email);
    }
    return value;
  }

  private static ResponseStatusException notConfigured(String what, OrderEmail email) {
    return new ResponseStatusException(
        SERVICE_UNAVAILABLE,
        "Order email not configured for a venue with orders, nothing sent: email="
            + email
            + ", missing="
            + what);
  }
}
