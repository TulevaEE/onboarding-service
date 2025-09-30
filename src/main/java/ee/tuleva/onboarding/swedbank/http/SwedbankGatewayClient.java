package ee.tuleva.onboarding.swedbank.http;

import static ee.swedbank.gateway.iso.request.QueryType3Code.ALLL;
import static org.springframework.http.HttpMethod.*;

import ee.swedbank.gateway.iso.request.*;
import jakarta.xml.bind.JAXBElement;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class SwedbankGatewayClient {

  @Value("${swedbank-gateway.url}")
  private String baseUrl;

  @Value("${swedbank-gateway.client-id}")
  private String clientId;

  @Value("${swedbank-gateway.agreement-id}")
  private String agreementId;

  private final Clock clock;

  private final SwedbankGatewayMarshaller marshaller;

  private final Converter<LocalDate, XMLGregorianCalendar> dateConverter;
  private final Converter<ZonedDateTime, XMLGregorianCalendar> timeConverter;

  @Autowired
  @Qualifier("swedbankGatewayRestTemplate")
  private final RestTemplate restTemplate;

  public void sendStatementRequest(
      JAXBElement<ee.swedbank.gateway.iso.request.Document> entity, UUID uuid) {
    var requestXml = marshaller.marshalToString(entity);

    HttpEntity<String> requestEntity = new HttpEntity<>(requestXml, getHeaders(uuid));
    restTemplate.exchange(getRequestUrl("account-statements"), POST, requestEntity, String.class);
  }

  public Optional<SwedbankGatewayResponseDto> getResponse() {
    HttpEntity<Void> messageEntity = new HttpEntity<>(getHeaders(UUID.randomUUID()));

    var messagesResponse =
        restTemplate.exchange(getRequestUrl("messages"), GET, messageEntity, String.class);
    if (messagesResponse.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(204))) {
      // 204 no content
      return Optional.empty();
    }

    return Optional.of(
        new SwedbankGatewayResponseDto(
            messagesResponse.getBody(),
            // TODO handle weird statement request-id?
            messagesResponse.getHeaders().get("X-Request-ID").getFirst(),
            messagesResponse.getHeaders().get("X-Tracking-ID").getFirst()));
  }

  public void acknowledgeResponse(SwedbankGatewayResponseDto response) {
    var requestId = UUID.randomUUID();
    HttpEntity<Void> messageEntity = new HttpEntity<>(getHeaders(requestId));

    URI uri =
        UriComponentsBuilder.fromUriString(getRequestUrl("messages"))
            .queryParam("trackingId", response.responseTrackingId())
            .build()
            .toUri();

    restTemplate.exchange(uri, DELETE, messageEntity, String.class);
  }

  private String getRequestUrl(String path) {
    return UriComponentsBuilder.fromUriString(baseUrl + path)
        .queryParam("client_id", clientId)
        .build()
        .toUriString();
  }

  private HttpHeaders getHeaders(UUID requestId) {
    var headers = new HttpHeaders();
    headers.add("X-Request-ID", serializeRequestId(requestId));
    headers.add("X-Agreement-ID", agreementId);
    headers.add(
        "Date",
        DateTimeFormatter.RFC_1123_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(clock.instant()));
    headers.add("Content-Type", "application/xml; charset=utf-8");

    return headers;
  }

  public JAXBElement<ee.swedbank.gateway.iso.request.Document> getAccountStatementRequestEntity(
      String accountIban, UUID messageId) {
    AccountReportingRequestV03 accountReportingRequest = new AccountReportingRequestV03();

    GroupHeader59 groupHeader = new GroupHeader59();
    groupHeader.setMsgId(serializeRequestId(messageId));
    groupHeader.setCreDtTm(timeConverter.convert(ZonedDateTime.now(clock)));
    accountReportingRequest.setGrpHdr(groupHeader);

    ReportingRequest3 reportingRequest = new ReportingRequest3();

    reportingRequest.setId(serializeRequestId(messageId));
    reportingRequest.setReqdMsgNmId("camt.052.001.02"); // current day 52, past 53

    CashAccount24 cashAccount24 = new CashAccount24();
    AccountIdentification4Choice accountIdentification = new AccountIdentification4Choice();

    accountIdentification.setIBAN(accountIban);

    cashAccount24.setId(accountIdentification);
    reportingRequest.setAcct(cashAccount24);

    var partyChoice = new Party12Choice();
    var party = new PartyIdentification43();
    // party.setNm("Tuleva");
    partyChoice.setPty(party);

    reportingRequest.setAcctOwnr(partyChoice);

    ReportingPeriod1 period = new ReportingPeriod1();

    DatePeriodDetails1 datePeriodDetails = new DatePeriodDetails1();

    datePeriodDetails.setFrDt(dateConverter.convert(LocalDate.now(clock)));
    datePeriodDetails.setToDt(dateConverter.convert(LocalDate.now(clock)));

    period.setFrToDt(datePeriodDetails);
    period.setTp(ALLL);

    TimePeriodDetails1 timePeriodDetails = new TimePeriodDetails1();

    // TODO revisit this, maybe run for last hour to better deal with limits
    timePeriodDetails.setFrTm(
        timeConverter.convert(LocalDate.now(clock).atStartOfDay(ZoneId.of("Europe/Tallinn"))));
    timePeriodDetails.setToTm(
        timeConverter.convert(
            LocalDate.now(clock).atStartOfDay(ZoneId.of("Europe/Tallinn")).with(LocalTime.MAX)));

    period.setFrToTm(timePeriodDetails);

    reportingRequest.setRptgPrd(period);

    accountReportingRequest.getRptgReq().add(reportingRequest);

    ee.swedbank.gateway.iso.request.Document document =
        new ee.swedbank.gateway.iso.request.Document();
    document.setAcctRptgReq(accountReportingRequest);
    var objectFactory = new ObjectFactory();

    return objectFactory.createDocument(document);
  }

  private static String serializeRequestId(UUID requestId) {
    return requestId.toString().replace("-", "");
  }
}
