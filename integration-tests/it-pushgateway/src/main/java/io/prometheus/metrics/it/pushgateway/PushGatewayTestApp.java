package io.prometheus.metrics.it.pushgateway;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;

import java.io.IOException;

public class PushGatewayTestApp {
    public static void main(String[] args) throws IOException, InterruptedException {
        PrometheusRegistry registry = new PrometheusRegistry();
        Histogram sizes = Histogram.builder()
                .name("file_sizes_bytes")
                .classicUpperBounds(256, 512, 1024, 2048)
                .unit(Unit.BYTES)
                .register(registry);
        sizes.observe(513);
        sizes.observe(814);
        sizes.observe(1553);
        Gauge duration = Gauge.builder()
                .name("my_batch_job_duration_seconds")
                .help("Duration of my batch job in seconds.")
                .unit(Unit.SECONDS)
                .register(registry);
        duration.set(0.5);
        PushGateway pg = PushGateway.builder().build();
        pg.pushAdd(registry, "my_batch_job");
    }
}