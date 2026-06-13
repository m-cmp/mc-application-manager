package kr.co.mcmp.softwarecatalog.application.constants;

public final class ApplicationStatusValues {
    public static final String PREPARING_RUNTIME = "PREPARING_RUNTIME";
    public static final String PREPARING_METRICS_SERVER = "PREPARING_METRICS_SERVER";
    public static final String PREPARING_INGRESS_NGINX = "PREPARING_INGRESS_NGINX";
    public static final String DEPLOYING = "DEPLOYING";
    public static final String UNINSTALLED = "UNINSTALLED";

    private ApplicationStatusValues() {
    }
}
