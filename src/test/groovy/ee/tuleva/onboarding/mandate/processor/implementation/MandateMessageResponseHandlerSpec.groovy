package ee.tuleva.onboarding.mandate.processor.implementation

import ee.tuleva.onboarding.mandate.processor.MandateProcessResult
import ee.tuleva.onboarding.mandate.processor.implementation.MandateMessageResponseHandler
import ee.tuleva.onboarding.mandate.processor.implementation.MandateMessageResponseReader
import spock.lang.Specification

import javax.jms.Message

class MandateMessageResponseHandlerSpec extends Specification {

    MandateMessageResponseReader mandateMessageResponseReader = Mock(MandateMessageResponseReader)

    MandateMessageResponseHandler service = new MandateMessageResponseHandler(mandateMessageResponseReader);

    def "GetMandateProcessResponse: Handle error response"() {
        given:
        mandateMessageResponseReader.getText(_ as Message) >> errorMessageText
        when:
        MandateProcessResult result = service.getMandateProcessResponse(Mock(Message))
        then:
        result.processId == "ccf4ea436daf48e7a86c7a47954f5e2c"
        result.isSuccessful() == false
        result.errorCode.get() == 40551
    }

    def "GetMandateProcessResponse: Handle success response"() {
        given:
        mandateMessageResponseReader.getText(_ as Message) >> successfulMessageText
        when:
        MandateProcessResult result = service.getMandateProcessResponse(Mock(Message))
        then:
        result.processId == "20d45002ccb044e181ec61436503b33f"
        result.isSuccessful() == true
        result.errorCode.isPresent() == false
    }

    def "GetMandateProcessResponse: Handle unknwown response"() {
        given:
        mandateMessageResponseReader.getText(_ as Message) >> "Unknown response"
        when:
        MandateProcessResult result = service.getMandateProcessResponse(Mock(Message))
        then:
        thrown Exception
    }

    String errorMessageText = "<Ex xmlns=\"urn:mhub:xsd:Envelope:01\" xmlns:ns2=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.01\"><BizMsg><ns2:AppHdr><ns2:Fr><ns2:FIId><ns2:FinInstnId><ns2:BICFI>ECSDEE2XXXX</ns2:BICFI></ns2:FinInstnId></ns2:FIId></ns2:Fr><ns2:To><ns2:FIId><ns2:FinInstnId><ns2:BICFI>TULEVA20</ns2:BICFI></ns2:FinInstnId></ns2:FIId></ns2:To><ns2:BizMsgIdr>ccf4ea436daf48e7a86c7a47954f5e2c</ns2:BizMsgIdr><ns2:MsgDefIdr>epis</ns2:MsgDefIdr><ns2:CreDt>2017-03-13T10:00:39.731Z</ns2:CreDt></ns2:AppHdr><ns2:VALIKUAVALDUS xmlns:ns2=\"http://epis.x-road.ee/producer/\" xmlns:ns3=\"http://x-road.ee/xsd/x-road.xsd\"><ns2:Sender CODE=\"EVK\"/><ns2:Response PersonId=\"38010002700\"><ns2:Results ErrorTextEng=\"This security is assigned to the person already!\" ErrorTextEst=\"See on juba antud isiku aktiivne väärtpaber!\" Result=\"NOK\" ResultCode=\"40551\"/></ns2:Response></ns2:VALIKUAVALDUS></BizMsg></Ex>";

    String successfulMessageText = "<Ex xmlns=\"urn:mhub:xsd:Envelope:01\" xmlns:ns2=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.01\"><BizMsg><ns2:AppHdr><ns2:Fr><ns2:FIId><ns2:FinInstnId><ns2:BICFI>ECSDEE2XXXX</ns2:BICFI></ns2:FinInstnId></ns2:FIId></ns2:Fr><ns2:To><ns2:FIId><ns2:FinInstnId><ns2:BICFI>TULEVA20</ns2:BICFI></ns2:FinInstnId></ns2:FIId></ns2:To><ns2:BizMsgIdr>20d45002ccb044e181ec61436503b33f</ns2:BizMsgIdr><ns2:MsgDefIdr>epis</ns2:MsgDefIdr><ns2:CreDt>2017-03-13T10:00:39.731Z</ns2:CreDt></ns2:AppHdr><ns2:OSAKUTE_VAHETAMISE_AVALDUS xmlns:ns2=\"http://epis.x-road.ee/producer/\" xmlns:ns3=\"http://x-road.ee/xsd/x-road.xsd\"><ns2:Sender CODE=\"EVK\"/><ns2:Response ApplicationId=\"2651886\" Currency=\"EUR\" PaymentAmount=\"1.50\" PersonId=\"38010002700\"><ns2:Results Result=\"OK\"/><ns2:Address AddressRow1=\"Tuleva, Telliskivi 60\" AddressRow2=\"TALLINN\" Country=\"EE\" PostalIndex=\"10412\" Territory=\"0784\"/></ns2:Response></ns2:OSAKUTE_VAHETAMISE_AVALDUS></BizMsg></Ex>";

}
