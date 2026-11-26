package ee.tuleva.onboarding.investment.transaction.export;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent.TRADE_ORDER_TAG;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.investment.transaction.OrderType;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRefusedException;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CustodianOrderMessageFactory {

  private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final String DATE_PLACEHOLDER = "{date}";

  private final CustodianOrderEmailProperties properties;

  MandrillMessage create(OrderDay day, OrderEmail email) {
    var message = new MandrillMessage();
    message.setFromEmail(day.sender().email());
    message.setFromName(day.sender().name());
    message.setSubject(subject(day, email));
    message.setText(text(day, email));
    message.setPreserveRecipients(true);
    message.setTrackOpens(false);
    message.setTrackClicks(false);
    message.setTags(List.of(TRADE_ORDER_TAG));
    message.setTo(recipients(day, properties.venueOf(email)));
    message.setAttachments(List.of(attachment(day, email)));
    return message;
  }

  String describe(OrderDay day, OrderEmail email) {
    return email.file().driveLabel() + " " + EMAIL_DATE.format(day.tradeDate());
  }

  static void requireOneFtOrderType(OrderDay day) {
    ftOrderTypeOf(day);
  }

  private static OrderType ftOrderTypeOf(OrderDay day) {
    Set<OrderType> orderTypes =
        day.orders().stream()
            .filter(BrokerOrders::isFtOrder)
            .map(TransactionOrder::getOrderType)
            .collect(toCollection(() -> EnumSet.noneOf(OrderType.class)));
    if (orderTypes.size() != 1) {
      throw new TransactionBatchRefusedException(
          "The FT email gives one order type for all its orders, set every FT order to the same"
              + " type: orderTypes="
              + orderTypes);
    }
    return orderTypes.iterator().next();
  }

  private String subject(OrderDay day, OrderEmail email) {
    String date = EMAIL_DATE.format(day.tradeDate());
    return switch (email) {
      case FT -> "Tuleva ETF orders " + date + " – " + ftOrderTypeOf(day).name();
      case UUID -> "Tehingukorralduste UUID " + date;
      case SEB_ETF, SEB_FUND ->
          properties.venueOf(email).requiredSubject(email).replace(DATE_PLACEHOLDER, date);
    };
  }

  private String text(OrderDay day, OrderEmail email) {
    String date = EMAIL_DATE.format(day.tradeDate());
    String signature = day.sender().name() + "\nTuleva";
    return switch (email) {
      case FT -> ftText(day, date) + "\n" + signature;
      case UUID ->
          "Tere,\n\nLisatud tänaste tehingukorralduste fail (" + date + ").\n\n" + signature;
      case SEB_ETF, SEB_FUND ->
          "Tere,\n\n"
              + properties.venueOf(email).requiredText(email).replace(DATE_PLACEHOLDER, date)
              + "\n\n"
              + signature;
    };
  }

  private static String ftText(OrderDay day, String date) {
    return """
        Hello,

        Please find attached our ETF orders for %s: %s.

        Order type: %s for all orders.
        Approximate total: EUR %s.

        Please confirm receipt by replying to all.

        Kind regards,"""
        .formatted(
            date,
            ExportFile.FT_ETF.fileName(day.sentAt()),
            wordingOf(ftOrderTypeOf(day)),
            String.format(Locale.ENGLISH, "%,.2f", BrokerOrders.ftApproximateTotal(day.orders())));
  }

  private static String wordingOf(OrderType orderType) {
    return switch (orderType) {
      case MOC -> "Market on Close (MOC)";
      case NAV -> "NAV";
      case RISK -> "RISK";
    };
  }

  private List<Recipient> recipients(OrderDay day, CustodianOrderEmailProperties.Venue venue) {
    Set<String> to = new LinkedHashSet<>(venue.to());
    Set<String> cc =
        Stream.of(properties.cc(), venue.cc(), List.of(day.sender().email()))
            .flatMap(List::stream)
            .filter(address -> !to.contains(address))
            .collect(toCollection(LinkedHashSet::new));
    return Stream.concat(
            to.stream().map(address -> recipient(address, TO)),
            cc.stream().map(address -> recipient(address, CC)))
        .toList();
  }

  private static Recipient recipient(String address, Recipient.Type type) {
    var recipient = new Recipient();
    recipient.setEmail(address);
    recipient.setType(type);
    return recipient;
  }

  private static MessageContent attachment(OrderDay day, OrderEmail email) {
    ExportFile file = email.file();
    var attachment = new MessageContent();
    attachment.setName(file.fileName(day.sentAt()));
    attachment.setType(file.mimeType());
    attachment.setContent(
        Base64.getEncoder()
            .encodeToString(
                requireNonNull(
                    day.brokerFiles().get(file), "No file for the order email: email=" + email)));
    return attachment;
  }
}
