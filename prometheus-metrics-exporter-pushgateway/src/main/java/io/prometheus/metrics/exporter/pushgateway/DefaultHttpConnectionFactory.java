package io.prometheus.metrics.exporter.pushgateway;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class DefaultHttpConnectionFactory implements HttpConnectionFactory {
    @Override
    public HttpURLConnection create(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }
}
