package io.prometheus.metrics.config;

import java.text.Format;
import java.util.Map;

public class ExporterPushgatewayProperties {
    private static final String ADDRESS = "address";
    private static final String JOB = "job";
    private String address;
    private String job;

    private ExporterPushgatewayProperties(String address, String job) {
        this.address = address;
        this.job = job;
    }

    public String getAddress() {
        return address;
    }

    public String getJob() {
        return job;
    }

    /**
     * Note that this will remove entries from {@code properties}.
     * This is because we want to know if there are unused properties remaining after all properties have been loaded.
     */
    static ExporterPushgatewayProperties load(String prefix, Map<Object, Object> properties) throws PrometheusPropertiesException {
        String address = Util.loadString(prefix + "." + ADDRESS, properties);
        String job = Util.loadString(prefix + "." + JOB, properties);
        return new ExporterPushgatewayProperties(address, job);
    }
}
