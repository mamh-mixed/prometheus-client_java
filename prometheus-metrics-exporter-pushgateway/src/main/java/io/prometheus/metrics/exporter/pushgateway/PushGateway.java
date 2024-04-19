package io.prometheus.metrics.exporter.pushgateway;

import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Export metrics via the Prometheus Pushgateway.
 * <p>
 * The Prometheus Pushgateway exists to allow ephemeral and batch jobs to expose their metrics to Prometheus.
 * Since these kinds of jobs may not exist long enough to be scraped, they can instead push their metrics
 * to a Pushgateway. This class allows pushing the contents of a {@link PrometheusRegistry} to
 * a Pushgateway.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   void executeBatchJob() throws Exception {
 *     PrometheusRegistry registry = new PrometheusRegistry();
 *     Gauge duration = Gauge.builder()
 *         .name("my_batch_job_duration_seconds").help("Duration of my batch job in seconds.").register(registry);
 *     Gauge.Timer durationTimer = duration.startTimer();
 *     try {
 *       // Your code here.
 *
 *       // This is only added to the registry after success,
 *       // so that a previous success in the Pushgateway isn't overwritten on failure.
 *       Gauge lastSuccess = Gauge.builder()
 *           .name("my_batch_job_last_success").help("Last time my batch job succeeded, in unixtime.").register(registry);
 *       lastSuccess.setToCurrentTime();
 *     } finally {
 *       durationTimer.setDuration();
 *       PushGateway pg = new PushGateway("127.0.0.1:9091");
 *       pg.pushAdd(registry, "my_batch_job");
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * See <a href="https://github.com/prometheus/pushgateway">https://github.com/prometheus/pushgateway</a>
 */
public class PushGateway {

    private static final int MILLISECONDS_PER_SECOND = 1000;

    // Visible for testing.
    protected final String gatewayBaseURL;

    private HttpConnectionFactory connectionFactory = new DefaultHttpConnectionFactory();

    /**
     * Construct a Pushgateway, with the given address.
     * <p>
     *
     * @param address host:port or ip:port of the Pushgateway.
     */
    public PushGateway(String address) {
        this(createURLSneakily("http://" + address));
    }

    /**
     * Construct a Pushgateway, with the given URL.
     * <p>
     *
     * @param serverBaseURL the base URL and optional context path of the Pushgateway server.
     */
    public PushGateway(URL serverBaseURL) {
        this.gatewayBaseURL = URI.create(serverBaseURL.toString() + "/metrics/")
                .normalize()
                .toString();
    }

    public void setConnectionFactory(HttpConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Creates a URL instance from a String representation of a URL without throwing a checked exception.
     * Required because you can't wrap a call to another constructor in a try statement.
     *
     * @param urlString the String representation of the URL.
     * @return The URL instance.
     */
    private static URL createURLSneakily(final String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pushes all metrics in a registry, replacing all those with the same job and no grouping key.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push(PrometheusRegistry registry, String job) throws IOException {
        doRequest(registry, job, null, "PUT");
    }

    /**
     * Pushes all metrics in a Collector, replacing all those with the same job and no grouping key.
     * <p>
     * This is useful for pushing a single Gauge.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push(Collector collector, String job) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        push(registry, job);
    }

    /**
     * Pushes all metrics in a registry, replacing all those with the same job and grouping key.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push(PrometheusRegistry registry, String job, Map<String, String> groupingKey) throws IOException {
        doRequest(registry, job, groupingKey, "PUT");
    }

    /**
     * Pushes all metrics in a Collector, replacing all those with the same job and grouping key.
     * <p>
     * This is useful for pushing a single Gauge.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push(Collector collector, String job, Map<String, String> groupingKey) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        push(registry, job, groupingKey);
    }

    /**
     * Pushes all metrics in a registry, replacing only previously pushed metrics of the same name and job and no grouping key.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd(PrometheusRegistry registry, String job) throws IOException {
        doRequest(registry, job, null, "POST");
    }

    /**
     * Pushes all metrics in a Collector, replacing only previously pushed metrics of the same name and job and no grouping key.
     * <p>
     * This is useful for pushing a single Gauge.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd(Collector collector, String job) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        pushAdd(registry, job);
    }

    /**
     * Pushes all metrics in a registry, replacing only previously pushed metrics of the same name, job and grouping key.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd(PrometheusRegistry registry, String job, Map<String, String> groupingKey) throws IOException {
        doRequest(registry, job, groupingKey, "POST");
    }

    /**
     * Pushes all metrics in a Collector, replacing only previously pushed metrics of the same name, job and grouping key.
     * <p>
     * This is useful for pushing a single Gauge.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd(Collector collector, String job, Map<String, String> groupingKey) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        pushAdd(registry, job, groupingKey);
    }


    /**
     * Deletes metrics from the Pushgateway.
     * <p>
     * Deletes metrics with no grouping key and the provided job.
     * This uses the DELETE HTTP method.
     */
    public void delete(String job) throws IOException {
        doRequest(null, job, null, "DELETE");
    }

    /**
     * Deletes metrics from the Pushgateway.
     * <p>
     * Deletes metrics with the provided job and grouping key.
     * This uses the DELETE HTTP method.
     */
    public void delete(String job, Map<String, String> groupingKey) throws IOException {
        doRequest(null, job, groupingKey, "DELETE");
    }

    void doRequest(PrometheusRegistry registry, String job, Map<String, String> groupingKey, String method) throws IOException {
        String url = gatewayBaseURL;
        if (job.contains("/")) {
            url += "job@base64/" + base64url(job);
        } else {
            url += "job/" + URLEncoder.encode(job, "UTF-8");
        }

        if (groupingKey != null) {
            for (Map.Entry<String, String> entry : groupingKey.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    url += "/" + entry.getKey() + "@base64/=";
                } else if (entry.getValue().contains("/")) {
                    url += "/" + entry.getKey() + "@base64/" + base64url(entry.getValue());
                } else {
                    url += "/" + entry.getKey() + "/" + URLEncoder.encode(entry.getValue(), "UTF-8");
                }
            }
        }
        HttpURLConnection connection = connectionFactory.create(url);
        connection.setRequestProperty("Content-Type", PrometheusTextFormatWriter.CONTENT_TYPE);
        if (!method.equals("DELETE")) {
            connection.setDoOutput(true);
        }
        connection.setRequestMethod(method);

        connection.setConnectTimeout(10 * MILLISECONDS_PER_SECOND);
        connection.setReadTimeout(10 * MILLISECONDS_PER_SECOND);
        connection.connect();

        try {
            if (!method.equals("DELETE")) {
                OutputStream outputStream = connection.getOutputStream();
                new PrometheusTextFormatWriter(false).write(outputStream, registry.scrape());
                outputStream.flush();
                outputStream.close();
            }

            int response = connection.getResponseCode();
            if (response / 100 != 2) {
                String errorMessage;
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    String errBody = readFromStream(errorStream);
                    errorMessage = "Response code from " + url + " was " + response + ", response body: " + errBody;
                } else {
                    errorMessage = "Response code from " + url + " was " + response;
                }
                throw new IOException(errorMessage);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String base64url(String v) {
        return  Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)).replace("+", "-").replace("/", "_");
    }

    /**
     * Returns a grouping key with the instance label set to the machine's IP address.
     * <p>
     * This is a convenience function, and should only be used where you want to
     * push per-instance metrics rather than cluster/job level metrics.
     */
    public static Map<String, String> instanceIPGroupingKey() throws UnknownHostException {
        Map<String, String> groupingKey = new HashMap<String, String>();
        groupingKey.put("instance", InetAddress.getLocalHost().getHostAddress());
        return groupingKey;
    }

    private static String readFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

    public static Builder builder() {
        return builder(PrometheusProperties.get());
    }

    public static Builder builder(PrometheusProperties config) {
        return new Builder(config);
    }

    public static class Builder {
        private final PrometheusProperties config;
        private Builder(PrometheusProperties config) {
            this.config = config;
        }

        public PushGateway build() {
            return new PushGateway(config.getExporterPushgatewayProperties().getAddress());
        }
    }
}
