package io.prometheus.metrics.config;

import java.util.Map;

public class ExporterPushgatewayProperties {
    private static final String ADDRESS = "address";
    private final String address;

    private ExporterPushgatewayProperties(String address) {
        this.address = address == null ? "localhost:9091" : address;
    }

    public String getAddress() {
        return address;
    }

   /**
     * Note that this will remove entries from {@code properties}.
     * This is because we want to know if there are unused properties remaining after all properties have been loaded.
     */
    static ExporterPushgatewayProperties load(String prefix, Map<Object, Object> properties) throws PrometheusPropertiesException {
        String address = Util.loadString(prefix + "." + ADDRESS, properties);
        return new ExporterPushgatewayProperties(address);
    }
}
