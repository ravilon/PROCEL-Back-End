package com.procel.telemetry.service.mqtt;

import com.procel.telemetry.config.TelemetryProperties;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClientPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.StandardCharsets;

@Component
public class MqttTelemetryClientFactory {
    public MqttAsyncClient createClient(TelemetryProperties.Mqtt properties) throws MqttException {
        MqttClientPersistence persistence = new MemoryPersistence();
        MqttAsyncClient client = new MqttAsyncClient(properties.getBrokerUrl(), properties.getClientId(), persistence);
        client.setManualAcks(true);
        return client;
    }

    public MqttConnectionOptions connectionOptions(TelemetryProperties.Mqtt properties) {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(properties.isCleanStart());
        options.setSessionExpiryInterval(properties.getSessionExpiry().toSeconds());
        options.setConnectionTimeout((int) properties.getConnectionTimeout().toSeconds());
        options.setKeepAliveInterval((int) properties.getKeepAlive().toSeconds());
        options.setAutomaticReconnect(properties.isAutomaticReconnect());
        options.setAutomaticReconnectDelay(
                (int) properties.getReconnectMinDelay().toSeconds(),
                (int) properties.getReconnectMaxDelay().toSeconds()
        );
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().getBytes(StandardCharsets.UTF_8));
        }
        if (properties.getTls().isEnabled()) {
            applyTls(properties.getTls(), options);
        }
        return options;
    }

    private static void applyTls(TelemetryProperties.Tls tls, MqttConnectionOptions options) {
        setIfPresent("javax.net.ssl.trustStore", tls.getTrustStore());
        setIfPresent("javax.net.ssl.trustStorePassword", tls.getTrustStorePassword());
        setIfPresent("javax.net.ssl.keyStore", tls.getKeyStore());
        setIfPresent("javax.net.ssl.keyStorePassword", tls.getKeyStorePassword());
        options.setSocketFactory(SSLSocketFactory.getDefault());
    }

    private static void setIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            System.setProperty(key, value);
        }
    }
}
