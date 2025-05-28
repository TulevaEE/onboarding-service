package ee.tuleva.onboarding.swedbank.http;

import static org.junit.jupiter.api.Assertions.*;

import ee.swedbank.gateway.request.Ping;
import ee.swedbank.gateway.response.B4B;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SwedbankGatewayMarshallerTest {

  @Autowired SwedbankGatewayMarshaller swedbankGatewayMarshaller;

  @Test
  @DisplayName("marshals request class")
  public void marshalRequestClass() {
    Ping ping = new Ping();
    ping.setValue("Test");

    var requestXml = swedbankGatewayMarshaller.marshalToString(ping);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Ping><Value>Test</Value></Ping>", requestXml);
  }

  @Test
  @DisplayName("unmarshals response class")
  public void unmarshalResponseClass() {
    var responseXml =
        "<B4B xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://swedbankgateway.net/valid/hgw-response.xsd\"> <Pong from=\"EE\"> <Value>Test</Value> </Pong> </B4B>";

    var pong = swedbankGatewayMarshaller.unMarshal(responseXml, B4B.class);

    assertEquals("Test", pong.getPong().getValue());
  }
}
