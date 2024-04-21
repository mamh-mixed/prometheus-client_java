package io.prometheus.metrics.exporter.pushgateway;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;


public class ExamplePushGateway {
    static final PrometheusRegistry pushRegistry = new PrometheusRegistry();
    static final Gauge g = (Gauge) Gauge.builder().name("gauge").help("blah").register(pushRegistry);

    /**
     * Example of how to use the pushgateway, pass in the host:port of a pushgateway.
     */
    public static void main(String[] args) throws Exception {
        PushGateway pg = PushGateway.builder().address(args[0]).build();
        g.set(42);
        pg.push(pushRegistry, "job");
    }
}

