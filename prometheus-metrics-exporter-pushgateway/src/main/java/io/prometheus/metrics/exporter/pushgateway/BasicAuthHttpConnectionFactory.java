package io.prometheus.metrics.exporter.pushgateway;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthHttpConnectionFactory implements HttpConnectionFactory {
    private final HttpConnectionFactory originConnectionFactory;
    private final String basicAuthHeader;

    public BasicAuthHttpConnectionFactory(HttpConnectionFactory connectionFactory, String user, String password) {
        this.originConnectionFactory = connectionFactory;
        this.basicAuthHeader = encode(user, password);
    }

    public BasicAuthHttpConnectionFactory(String user, String password) {
        this(new DefaultHttpConnectionFactory(), user, password);
    }

    @Override
    public HttpURLConnection create(String url) throws IOException {
        HttpURLConnection connection = originConnectionFactory.create(url);
        connection.setRequestProperty("Authorization", basicAuthHeader);
        return connection;
    }

    private String encode(String user, String password) {
        byte[] credentialsBytes = (user + ":" + password).getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.getEncoder().encodeToString(credentialsBytes);
        return String.format("Basic %s", encoded);
    }
}
