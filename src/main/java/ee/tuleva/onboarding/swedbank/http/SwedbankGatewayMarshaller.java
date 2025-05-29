package ee.tuleva.onboarding.swedbank.http;

import ee.swedbank.gateway.request.AccountStatement;
import ee.swedbank.gateway.request.GetBalance;
import ee.swedbank.gateway.request.Ping;
import ee.swedbank.gateway.response.B4B;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
class SwedbankGatewayMarshaller {

  private final Marshaller marshaller;
  private final Unmarshaller unmarshaller;

  @SneakyThrows
  public String marshalToString(Object object) {
    StringWriter sw = new StringWriter();
    marshaller.marshal(object, sw);
    return sw.toString();
  }

  @SneakyThrows
  public <T> T unMarshal(String response, Class<T> responseClass) {
    return responseClass.cast(unmarshaller.unmarshal(new StringReader(response)));
  }

  @SneakyThrows
  public SwedbankGatewayMarshaller() {
    Class[] requestClasses = {
      AccountStatement.class, GetBalance.class, Ping.class,
    };

    JAXBContext marshalContext = JAXBContext.newInstance(requestClasses);
    this.marshaller = marshalContext.createMarshaller();

    Class[] responseClasses = {
      B4B.class,
    };
    JAXBContext unMarshalContext = JAXBContext.newInstance(responseClasses);
    this.unmarshaller = unMarshalContext.createUnmarshaller();
  }
}
