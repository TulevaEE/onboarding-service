package ee.tuleva.onboarding.kpr;

import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.net.ssl.SSLContext;
import java.security.Security;


@Configuration
@Slf4j
public class MhubConfiguration {

    @Value("${mhub.host}")
    private String host;

    @Value("${mhub.port}")
    private int port;

    @Value("${mhub.queueManager}")
    private String queueManager;

    @Value("${mhub.channel}")
    private String channel;

    @Value("${mhub.peerName}")
    private String peerName;

    @Value("${mhub.inboundQueue}")
    private String inboundQueue;

    @Value("${mhub.outboundQueue}")
    private String outboundQueue;

    @Value("${mhub.trustStore}")
    private String trustStore;

    @Value("${mhub.trustStorePassword}")
    private String trustStorePassword;

    @Value("${mhub.keyStore}")
    private String keyStore;

    @Value("${mhub.keyStorePassword}")
    private String keyStorePassword;

    @Bean
    @Scope("singleton")
    public MQQueueConnectionFactory createMQConnectionFactory() {
        // it requires SSLv3 to be enabled but integrated with some app that has already brought up the JCA provider
        // it may be too late for that here - do it earlier
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        SSLContext sslContext = KeyUtils.createSSLContext(
                keyStore,
                keyStorePassword,
                trustStore,
                trustStorePassword);

        try {
            MQQueueConnectionFactory factory = new MQQueueConnectionFactory();
            factory.setSSLSocketFactory(sslContext.getSocketFactory());
            factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            factory.setHostName(this.host);
            factory.setPort(this.port);
            factory.setQueueManager(this.queueManager);
            factory.setChannel(this.channel);
            factory.setCCSID(WMQConstants.CCSID_UTF8);
            // only cipher that works
            factory.setSSLCipherSuite("SSL_RSA_WITH_3DES_EDE_CBC_SHA");
            factory.setSSLPeerName(this.peerName);
            factory.setSSLFipsRequired(false);
            return factory;
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }


    @Bean
    public JmsTemplate createJmsTemplate(MQQueueConnectionFactory factory) {
        JmsTemplate jmsTemplate = new JmsTemplate();
        SingleConnectionFactory singleConnectionFactory = new SingleConnectionFactory();
        singleConnectionFactory.setTargetConnectionFactory(factory);
        jmsTemplate.setConnectionFactory(singleConnectionFactory);
        jmsTemplate.setDefaultDestinationName(this.outboundQueue);
        return jmsTemplate;
    }

    @Autowired
    private MessageListener messageListener;

    @Bean
    @Scope("singleton")
    public DefaultMessageListenerContainer createMessageListenerContainer(
            MQQueueConnectionFactory factory) {
        log.info("MessageListener found in Spring context, creating DefaultMessageListenerContainer too.");
        DefaultMessageListenerContainer defaultMessageListenerContainer = new DefaultMessageListenerContainer();
        defaultMessageListenerContainer.setConnectionFactory(factory);
        defaultMessageListenerContainer.setDestinationName(this.inboundQueue);
        defaultMessageListenerContainer.setRecoveryInterval(4000); // todo
        defaultMessageListenerContainer.setMessageListener(this.messageListener);
        return defaultMessageListenerContainer;
    }


}
