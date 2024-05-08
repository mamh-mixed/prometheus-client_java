package io.prometheus.metrics.exporter.pushgateway;

import io.prometheus.metrics.config.ExporterPushgatewayProperties;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesException;
import io.prometheus.metrics.expositionformats.PrometheusProtobufWriter;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.Registry;
import java.util.*;

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
    private final URL url;
    private final Format format;
    private final Map<String, String> requestHeaders;
    private final PrometheusRegistry registry;
    private final HttpConnectionFactory connectionFactory;

    private PushGateway(PrometheusRegistry registry, Format format, URL url, HttpConnectionFactory connectionFactory, Map<String, String> requestHeaders) {
        this.registry = registry;
        this.format = format;
        this.url = url;
        this.requestHeaders = Collections.unmodifiableMap(new HashMap<>(requestHeaders));
        this.connectionFactory = connectionFactory;
    }

    /**
     * Push all metrics. All metrics with the same job and grouping key are replaced.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push() throws IOException {
        doRequest(registry, "PUT");
    }

    /**
     * Push a single metric. All metrics with the same job and grouping key are replaced.
     * <p>
     * This is useful for pushing a single Gauge.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push(Collector collector) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        doRequest(registry, "PUT");
    }

    /**
     * Push a single collector. All metrics with the same job and grouping key are replaced.
     * <p>
     * This uses the PUT HTTP method.
     */
    public void push(MultiCollector collector) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        doRequest(registry, "PUT");
    }

    /**
     * Like {@link #push()}, but only metrics with the same name as the newly pushed metrics are replaced.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd() throws IOException {
        doRequest(registry, "POST");
    }

    /**
     * Like {@link #push(Collector)}, but only the specified metric will be replaced.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd(Collector collector) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        doRequest(registry, "POST");
    }

    /**
     * Like {@link #push(MultiCollector)}, but only the metrics from the collector will be replaced.
     * <p>
     * This uses the POST HTTP method.
     */
    public void pushAdd(MultiCollector collector) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        registry.register(collector);
        doRequest(registry, "POST");
    }

    /**
     * Deletes metrics from the Pushgateway.
     * <p>
     * This uses the DELETE HTTP method.
     */
    public void delete() throws IOException {
        doRequest(null, "DELETE");
    }

    void doRequest(PrometheusRegistry registry, String method) throws IOException {
        HttpURLConnection connection = connectionFactory.create(url);
        requestHeaders.forEach(connection::setRequestProperty);
        if (format == Format.PROMETHEUS_TEXT) {
            connection.setRequestProperty("Content-Type", PrometheusTextFormatWriter.CONTENT_TYPE);
        } else {
            connection.setRequestProperty("Content-Type", PrometheusProtobufWriter.CONTENT_TYPE);
        }
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
                if (format == Format.PROMETHEUS_TEXT) {
                    new PrometheusTextFormatWriter(false).write(outputStream, registry.scrape());
                } else {
                    new PrometheusProtobufWriter().write(outputStream, registry.scrape());
                }
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
        return Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)).replace("+", "-").replace("/", "_");
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
        private Format format;
        private String address;
        private String job;
        private final Map<String, String> requestHeaders = new HashMap<>();
        private PrometheusRegistry registry = PrometheusRegistry.defaultRegistry;
        private HttpConnectionFactory connectionFactory = new DefaultHttpConnectionFactory();
        private Map<String, String> groupingKey = new TreeMap<>();

        private Builder(PrometheusProperties config) {
            this.config = config;
        }

        public Builder format(Format format) {
            if (format == null) {
                throw new NullPointerException();
            }
            this.format = format;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder basicAuth(String user, String password) {
            byte[] credentialsBytes = (user + ":" + password).getBytes(StandardCharsets.UTF_8);
            String encoded = Base64.getEncoder().encodeToString(credentialsBytes);
            requestHeaders.put("Authorization", String.format("Basic %s", encoded));
            return this;
        }

        public Builder connectionFactory(HttpConnectionFactory connectionFactory) {
            if (connectionFactory == null) {
                throw new NullPointerException();
            }
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Builder groupingKey(String name, String value) {
            groupingKey.put(name, value);
            return this;
        }

        public Builder registry(PrometheusRegistry registry) {
            if (registry == null) {
                throw new NullPointerException();
            }
            this.registry = registry;
            return this;
        }

        public Builder job(String job) {
            if (job == null) {
                throw new NullPointerException();
            }
            this.job = job;
            return this;
        }

        private String getAddress(ExporterPushgatewayProperties properties) {
            String address = this.address;
            if (address == null) {
                if (properties != null && properties.getAddress() != null) {
                    address = properties.getAddress();
                } else {
                    address = "localhost:9091";
                }
            }
            return address;
        }

        private String getJob(ExporterPushgatewayProperties properties) {
            String job = this.job;
            if (job == null) {
                if (properties != null && properties.getJob() != null) {
                    job = properties.getJob();
                } else {
                    job = DefaultJobLabelDetector.getDefaultJobLabel();
                }
            }
            return job;
        }

        private URL makeUrl(ExporterPushgatewayProperties properties) throws UnsupportedEncodingException, MalformedURLException {
            String url = "http://" + getAddress(properties) + "/metrics/";
            String job = getJob(properties);
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
            return URI.create(url).normalize().toURL();
        }

        public PushGateway build() {
            ExporterPushgatewayProperties properties = config == null ? null : config.getExporterPushgatewayProperties();
            Format format = this.format;
            if (format == null) {
                format = Format.PROMETHEUS_PROTOBUF;
            }
            try {
                return new PushGateway(registry, format, makeUrl(properties), connectionFactory, requestHeaders);
            } catch (MalformedURLException e) {
                throw new PrometheusPropertiesException(address + ": Invalid address. Expecting <host>:<port>");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e); // cannot happen, UTF-8 is always supported
            }
        }
    }
}
