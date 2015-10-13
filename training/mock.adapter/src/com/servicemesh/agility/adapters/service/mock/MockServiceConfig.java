package com.servicemesh.agility.adapters.service.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceProvider;

import org.apache.log4j.Logger;

public class MockServiceConfig
{
    private static final Logger logger =
        Logger.getLogger(MockServiceConfig.class);
    public static final String CONFIG_PROVIDER_TEST_PROPS =
        "mock-svc-provider-test";
    public static final String CONFIG_SERVICE_TEST_PROPS = "mock-svc-test";
    public static final String CONFIG_CONNECTION_TEST_PROPS =
        "mock-svc-conn-test";

    public static final int CONN_TIMEOUT_DEFAULT = 120;
    public static final int CONN_RETRIES_DEFAULT = 3;
    private static final String CONFIG_FILE = "/mockservice.properties";
    private static final String OPERATION_SUCCEED_KEY = ".succeed";
    private static final String OPERATION_TIME_KEY = ".millis";
    private static final String PACKAGE_GENERATE_KEY = "Package.generate";
    private static final String PACKAGE_ASSIGN_KEY = "Package.assign";

    private File propFile;
    private long propFileModTime = 0;
    private Properties config = new Properties();

    public class OperationOutcome
    {
        private static final int SUCCEEDED = 1, FAILED = 2, DEGRADED = 3;
        private int _result;
        private long _executionTimeMillis;

        public OperationOutcome(String result, long executionTimeMillis)
        {
            _result = SUCCEEDED;
            if (result != null) {
                if (result.equalsIgnoreCase("false"))
                    _result = FAILED;
                else if (result.equalsIgnoreCase("degraded"))
                    _result = DEGRADED;
            }
            _executionTimeMillis = executionTimeMillis;
        }
        public boolean isSuccessful() { return _result == SUCCEEDED; }
        public boolean isFailed() { return _result == FAILED; }
        public boolean isDegraded() { return _result == DEGRADED; }
        public long getExecutionTimeMillis() { return _executionTimeMillis; }
    }

    private static class Holder
    {
        private static final MockServiceConfig _instance =
            new MockServiceConfig();
    }

    public static MockServiceConfig getInstance()
    {
        return Holder._instance;
    }

    private MockServiceConfig()
    {
        propFile = new File(System.getProperty("user.home") + CONFIG_FILE);
        loadProperties();
    }

    public boolean isPackageGenerateEnabled()
    {
        return isEnabled(PACKAGE_GENERATE_KEY, "false");
    }

    public boolean isPackageAssignEnabled()
    {
        return isEnabled(PACKAGE_ASSIGN_KEY, "true");
    }

    private boolean isEnabled(String key, String defaultValue)
    {
        String enabled = null;
        loadProperties();
        synchronized(config) {
            enabled = config.getProperty(key, defaultValue);
        }
        return "true".equalsIgnoreCase(enabled);
    }

    public OperationOutcome getOperationOutcome(String operation,
                                                Properties testProperties)
    {
        loadProperties();
        String succeedKey = operation + OPERATION_SUCCEED_KEY;
        String timeKey = operation + OPERATION_TIME_KEY;
        String succeeds = null;
        String executionTimeMillis = null;
        if (testProperties != null) {
            succeeds = testProperties.getProperty(succeedKey);
            executionTimeMillis = testProperties.getProperty(timeKey);
        }
        synchronized(config) {
            if (succeeds == null) {
                succeeds = config.getProperty(succeedKey, "true");
            }
            if (executionTimeMillis == null) {
                executionTimeMillis = config.getProperty(timeKey, "0");
            }
        }
        long millis = 0;
        if (! executionTimeMillis.equals("0")) {
            try {
                millis = Long.parseLong(executionTimeMillis);
            }
            catch (Exception e) {}
        }
        return new OperationOutcome(succeeds, millis);
    }

    private void loadProperties()
    {
        synchronized(propFile) {
            try {
                if (propFile.lastModified() <= propFileModTime)
                    return;

                InputStream is = new FileInputStream(propFile);
                if (is != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Loading properties from " + propFile);
                    }
                    Properties props = new Properties();
                    props.load(is);
                    synchronized(config) {
                        config.clear();
                        config.putAll(props);
                    }
                    propFileModTime = propFile.lastModified();
                }
            }
            catch (Exception e) {
                logger.error(propFile + " read failed", e);
            }
        }
    }

    public static Properties getTestProperties(Connection conn)
    {
        return getProperties(conn.getAssetProperties(),
                             CONFIG_CONNECTION_TEST_PROPS);
    }

    public static Properties getTestProperties(ServiceInstance svcInstance)
    {
        return getProperties(svcInstance.getAssetProperties(),
                             CONFIG_SERVICE_TEST_PROPS);
    }

    public static Properties getTestProperties(ServiceProvider svcProvider)
    {
        return getProperties(svcProvider.getProperties(),
                             CONFIG_PROVIDER_TEST_PROPS);
    }

    protected static Properties getProperties(List<AssetProperty> assetProps,
                                              String key)
    {
        Properties props = null;
        if ((assetProps != null) && (! assetProps.isEmpty())) {
            for (AssetProperty ap : assetProps) {
                if (ap.getName().equals(key)) {
                    try {
                        String value = ap.getStringValue();
                        if (value != null) {
                            value = value.replace("\\n", "\n");
                            props = new Properties();
                            props.load(new StringReader(value));
                        }
                    }
                    catch (Exception e) {
                        props = null;
                        logger.error(ap.getStringValue() + " parse failed", e);
                    }
                    break;
                }
            }
        }
        return props;
    }
}
