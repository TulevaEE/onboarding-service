package ee.tuleva.onboarding.mandate.builder;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandWrapper;
import ee.tuleva.onboarding.mandate.command.MandateFundTransferExchangeCommand;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateMandateCommandToMandateConverter
    implements Converter<CreateMandateCommandWrapper, Mandate> {

  private final AccountStatementService accountStatementService;
  private final FundRepository fundRepository;
  private final ConversionDecorator conversionDecorator;

  @Override
  @NonNull
  public Mandate convert(CreateMandateCommandWrapper wrapper) {
    User user = wrapper.getUser();
    val createMandateCommand = wrapper.getCreateMandateCommand();
    ConversionResponse conversion = wrapper.getConversion();
    ContactDetails contactDetails = wrapper.getContactDetails();
    AuthenticatedPerson authenticatedPerson = wrapper.getAuthenticatedPerson();

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setPillar(getPillar(createMandateCommand));
    mandate.setAddress(createMandateCommand.getAddress());
    mandate.setPaymentRate(createMandateCommand.getPaymentRate());
    conversionDecorator.addConversionMetadata(
        mandate.getMetadata(), conversion, contactDetails, authenticatedPerson);

    List<FundTransferExchange> fundTransferExchanges =
        createMandateCommand.getFundTransferExchanges().stream()
            .map(
                exchange ->
                    FundTransferExchange.builder()
                        .sourceFundIsin(exchange.getSourceFundIsin())
                        .targetFundIsin(exchange.getTargetFundIsin())
                        .amount(getAmount(exchange, mandate))
                        .mandate(mandate)
                        .build())
            .collect(toList());

    mandate.setFundTransferExchanges(fundTransferExchanges);
    mandate.setFutureContributionFundIsin(createMandateCommand.getFutureContributionFundIsin());

    return mandate;
  }

  private Integer getPillar(CreateMandateCommand createMandateCommand) {
    val sourceIsin = getIsin(createMandateCommand);

    if (sourceIsin == null) {
      throw new IllegalArgumentException("Source isin not found: " + createMandateCommand);
    }

    val fund = fundRepository.findByIsin(sourceIsin);

    if (fund == null) {
      throw new IllegalArgumentException(
          "Provided fund isin not found in the database: " + sourceIsin);
    }
    return fund.getPillar();
  }

  private String getIsin(CreateMandateCommand createMandateCommand) {
    if (createMandateCommand.getFutureContributionFundIsin() != null) {
      return createMandateCommand.getFutureContributionFundIsin();
    }
    return createMandateCommand.getFundTransferExchanges().stream()
        .map(MandateFundTransferExchangeCommand::getSourceFundIsin)
        .findFirst()
        .orElse(null);
  }

  private BigDecimal getAmount(MandateFundTransferExchangeCommand exchange, Mandate mandate) {
    val pillar = mandate.getPillar();
    if (pillar == 2) {
      return exchange.getAmount();
    } else if (pillar == 3) {
      val statement = accountStatementService.getAccountStatement(mandate.getUser());
      val balance = getFundBalance(statement, exchange.getSourceFundIsin());
      val exchangeAmount = balance.getUnits().multiply(exchange.getAmount());
      return exchangeAmount.setScale(4, RoundingMode.HALF_UP);
    } else {
      throw new IllegalStateException("Unknown pillar " + pillar);
    }
  }

  private FundBalance getFundBalance(List<FundBalance> accountStatement, String isin) {
    return accountStatement.stream()
        .filter(fundBalance -> fundBalance.getFund().getIsin().equals(isin))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Fund not found: " + isin));
  }
}
