package com.servicemesh.agility.adapters.service.mock;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.service.mock.operations.MockConnectionOperations;
import com.servicemesh.agility.adapters.service.mock.operations.MockInstanceOperations;
import com.servicemesh.agility.adapters.service.mock.operations.MockOperationsManager;
import com.servicemesh.agility.adapters.service.mock.operations.MockServiceProviderOperations;
import com.servicemesh.agility.adapters.service.mock.operations.MockServiceInstanceOperations;
import com.servicemesh.agility.adapters.service.mock.operations.MockServiceInstanceLifecycleOperations;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetType;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.ConnectionDefinition;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.Package;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.PropertyDefinition;
import com.servicemesh.agility.api.PropertyType;
import com.servicemesh.agility.api.Script;
import com.servicemesh.agility.api.Service;
import com.servicemesh.agility.api.ServiceProviderOption;
import com.servicemesh.agility.api.ServiceProviderType;
import com.servicemesh.agility.sdk.service.msgs.RegistrationRequest;
import com.servicemesh.agility.sdk.service.msgs.RegistrationResponse;
import com.servicemesh.agility.sdk.service.spi.IConnection;
import com.servicemesh.agility.sdk.service.spi.IInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceInstance;
import com.servicemesh.agility.sdk.service.spi.IServiceInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceProvider;
import com.servicemesh.agility.sdk.service.spi.ServiceAdapter;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.reactor.TimerReactor;

public class MockServiceAdapter extends ServiceAdapter
{
    private static final Logger logger =
        Logger.getLogger(MockServiceAdapter.class);
    public static final String SERVICE_PROVIDER_NAME = "Mock Service Provider";
    public static final String SERVICE_PROVIDER_DESCRIPTION;
    public static final String SERVICE_PROVIDER_TYPE = "mock-service-provider";
    public static final String SERVICE_PROVIDER_VERSION;

    public static final String SERVICE_TYPE = "mockservice";
    public static final String SERVICE_NAME = "Mock Service";
    public static final String SERVICE_DESCRIPTION;

    public static final String CONNECTION_TYPE = "mockserviceconnection";
    public static final String CONNECTION_NAME = "Mock Service Connection";
    public static final String CONNECTION_DESCRIPTION;

    public static final String PACKAGE_NAME = SERVICE_NAME + " Package";
    public static final String SCRIPT_NAME = SERVICE_NAME + " Script";
    public static final String SCRIPT_VARIABLE_NAME = "MockServiceHostname";

    static {
        String PROP_FILE = "/resources/MockServiceAdapter.properties";
        Properties props = new Properties();
        try {
            InputStream rs =
                MockServiceAdapter.class.getResourceAsStream(PROP_FILE);
            if (rs != null)
                props.load(rs);
            else
                logger.error("Resource not found " + PROP_FILE);
        }
        catch (Exception ex) {
            logger.error("Failed to load " + PROP_FILE + ": " + ex);
        }
        SERVICE_PROVIDER_VERSION = props.getProperty("adapter.version", "0.0.0");
        String vendor = props.getProperty("adapter.vendor", "");

        StringBuilder sb = new StringBuilder();
        sb.append(" (").append(SERVICE_PROVIDER_VERSION);
        if (! vendor.isEmpty()) {
            sb.append(" ").append(vendor);
        }
        sb.append(")");
        SERVICE_PROVIDER_DESCRIPTION = SERVICE_PROVIDER_NAME + sb.toString();
        SERVICE_DESCRIPTION = SERVICE_NAME + sb.toString();
        CONNECTION_DESCRIPTION = CONNECTION_NAME + sb.toString();
    }

    public MockServiceAdapter() throws Exception
    {
        super(TimerReactor.getTimerReactor(SERVICE_PROVIDER_NAME));
        logger.info(SERVICE_PROVIDER_DESCRIPTION);
    }

    @Override
    public List<ServiceProviderType> getServiceProviderTypes()
    {
        List<ServiceProviderType> serviceProviderTypes =
            new ArrayList<ServiceProviderType>();
        ServiceProviderType serviceProviderType = new ServiceProviderType();
        serviceProviderType.setName(SERVICE_PROVIDER_NAME);
        serviceProviderType.setDescription(SERVICE_PROVIDER_DESCRIPTION);

        Link serviceType = new Link();
        serviceType.setName(SERVICE_TYPE);
        serviceProviderType.getServiceTypes().add(serviceType);

        Link assetType = new Link();
        assetType.setName(SERVICE_PROVIDER_TYPE);
        assetType.setType("application/" + AssetType.class.getName() + "+xml");
        serviceProviderType.setAssetType(assetType);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_HOSTNAME);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_NETWORKS);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_USERNAME);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_PASSWORD);
        serviceProviderTypes.add(serviceProviderType);
        return serviceProviderTypes;
    }

    @Override
    public void onRegistration(RegistrationResponse response)
    {
        if (MockServiceConfig.getInstance().isPackageGenerateEnabled()) {
            makePackage();
        }
    }

    @Override
    public IConnection getConnectionOperations()
    {
        return new MockConnectionOperations();
    }

    @Override
    public IInstanceLifecycle getInstanceOperations()
    {
        return new MockInstanceOperations();
    }

    @Override
    public IServiceProvider getServiceProviderOperations()
    {
        return new MockServiceProviderOperations();
    }

    @Override
    public IServiceInstance getServiceInstanceOperations()
    {
        return new MockServiceInstanceOperations();
    }

    @Override
    public IServiceInstanceLifecycle getServiceInstanceLifecycleOperations()
    {
        return new MockServiceInstanceLifecycleOperations();
    }

    /**
     * Build up the set of asset types exposed by the adapter and return
     * these in the registration request. If the adapter exposes a service
     * to an application via a blueprint, a sub-class of service is defined
     * to expose this functionality and define configuration parameters for
     * the service. A sub-class of serviceprovider exposes configuration
     * parameters for the adapter itself.
     */
    @Override
    public RegistrationRequest getRegistrationRequest()
    {
        logger.debug("getRegistrationRequest");
        RegistrationRequest registration = new RegistrationRequest();
        registration.setName(SERVICE_PROVIDER_NAME);
        registration.setVersion(SERVICE_PROVIDER_VERSION);

        // references to common types
        String X_PROPERTY_TYPE =
            "application/" + PropertyType.class.getName() + "+xml";
        Link string_type = new Link();
        string_type.setName("string-any");
        string_type.setType(X_PROPERTY_TYPE);

        Link integer_type = new Link();
        integer_type.setName("integer-any");
        integer_type.setType(X_PROPERTY_TYPE);

        Link encrypted_type = new Link();
        encrypted_type.setName("encrypted");
        encrypted_type.setType(X_PROPERTY_TYPE);

        // property definition to specify test properties
        PropertyDefinition svcTestPD = new PropertyDefinition();
        svcTestPD.setName(MockServiceConfig.CONFIG_SERVICE_TEST_PROPS);
        svcTestPD.setDisplayName(SERVICE_NAME + " Test Properties");
        svcTestPD.setDescription(svcTestPD.getDisplayName());
        svcTestPD.setReadable(true);
        svcTestPD.setWritable(true);
        svcTestPD.setMaxAllowed(1);
        svcTestPD.setPropertyType(string_type);

        // define mock service
        Link service = new Link();
        service.setName("service");
        service.setType("application/" + Service.class.getName() + "+xml");

        AssetType serviceType = new AssetType();
        serviceType.setName(SERVICE_TYPE);
        serviceType.setDisplayName(SERVICE_NAME);
        serviceType.setDescription(SERVICE_DESCRIPTION);
        serviceType.setSuperType(service);
        serviceType.getPropertyDefinitions().add(svcTestPD);

        // define connections
        String X_CONNECTION_TYPE =
            "application/" + Connection.class.getName() + "+xml";
        Link mockLink = new Link();
        mockLink.setName(CONNECTION_TYPE);
        mockLink.setType(X_CONNECTION_TYPE);

        Link workloadLink = new Link();
        workloadLink.setName("designworkload");

        Link myLink = new Link();
        myLink.setName(SERVICE_TYPE);

        Link serviceLink = new Link();
        serviceLink.setName("service");

        // workload to mock connection
        ConnectionDefinition workloadDestConnection =
            new ConnectionDefinition();
        workloadDestConnection.setName("workload-to-mock-service");
        workloadDestConnection.setDescription("From any workload");
        workloadDestConnection.setConnectionType(mockLink);
        workloadDestConnection.setSourceType(workloadLink);
        serviceType.getDestConnections().add(workloadDestConnection);

        // any service to mock connection
        ConnectionDefinition anyServiceDestConnection =
            new ConnectionDefinition();
        anyServiceDestConnection.setName("service-to-mock-service");
        anyServiceDestConnection.setDescription("From any service");
        anyServiceDestConnection.setConnectionType(mockLink);
        anyServiceDestConnection.setSourceType(serviceLink);
        serviceType.getDestConnections().add(anyServiceDestConnection);

        // mock to workload connection
        ConnectionDefinition mockSrcWorkloadConnection  =
            new ConnectionDefinition();
        mockSrcWorkloadConnection.setName("mock-service-to-workload");
        mockSrcWorkloadConnection.setDescription("To any workload");
        mockSrcWorkloadConnection.setConnectionType(mockLink);
        mockSrcWorkloadConnection.setSourceType(myLink);
        mockSrcWorkloadConnection.setDestinationType(workloadLink);
        serviceType.getSrcConnections().add(mockSrcWorkloadConnection);

        // mock to service connection
        ConnectionDefinition mockSrcServiceConnection =
            new ConnectionDefinition();
        mockSrcServiceConnection.setName("mock-service-to-service");
        mockSrcServiceConnection.setDescription("To any service");
        mockSrcServiceConnection.setConnectionType(mockLink);
        mockSrcServiceConnection.setSourceType(myLink);
        mockSrcServiceConnection.setDestinationType(serviceLink);
        serviceType.getSrcConnections().add(mockSrcServiceConnection);

        Link connection = new Link();
        connection.setName("designconnection");
        connection.setType(X_CONNECTION_TYPE);

        // connection test properties
        PropertyDefinition connTestPD = new PropertyDefinition();
        connTestPD.setName(MockServiceConfig.CONFIG_CONNECTION_TEST_PROPS);
        connTestPD.setDisplayName(CONNECTION_NAME + " Test Properties");
        connTestPD.setDescription(connTestPD.getDisplayName());
        connTestPD.setReadable(true);
        connTestPD.setWritable(true);
        connTestPD.setMaxAllowed(1);
        connTestPD.setPropertyType(string_type);

        AssetType connectionType = new AssetType();
        connectionType.setName(CONNECTION_TYPE);
        connectionType.setDisplayName(CONNECTION_NAME);
        connectionType.setDescription(CONNECTION_DESCRIPTION);
        connectionType.setSuperType(connection);
        connectionType.getPropertyDefinitions().add(connTestPD);
        connectionType.setAllowExtensions(true);

        // launch item properties
        Link launchItemLink = new Link();
        launchItemLink.setName("storelaunchitem");
        String launchItemName = SERVICE_NAME + " Store Launch Item";

        PropertyDefinition svcLiTestPD = new PropertyDefinition();
        svcLiTestPD.setName(MockServiceConfig.CONFIG_SERVICE_TEST_PROPS);
        svcLiTestPD.setDisplayName(launchItemName + " Test Properties");
        svcLiTestPD.setDescription(svcTestPD.getDisplayName());
        svcLiTestPD.setReadable(true);
        svcLiTestPD.setWritable(true);
        svcLiTestPD.setMaxAllowed(1);
        svcLiTestPD.setPropertyType(string_type);

        AssetType launchItemType = new AssetType();
        launchItemType.setName("mock-service-store-launch-item");
        launchItemType.setDisplayName(launchItemName);
        launchItemType.setSuperType(launchItemLink);
        launchItemType.getPropertyDefinitions().add(svcLiTestPD);

        // define mock service provider
        Link serviceprovidertype = new Link();
        serviceprovidertype.setName("serviceprovidertype");
        serviceprovidertype.setType("application/" +
                                    ServiceProviderType.class.getName() +
                                    "+xml");

        // define mock provider properties
        PropertyDefinition provTestPD = new PropertyDefinition();
        provTestPD.setName(MockServiceConfig.CONFIG_PROVIDER_TEST_PROPS);
        provTestPD.setDisplayName(SERVICE_PROVIDER_NAME + " Test Properties");
        provTestPD.setDescription(provTestPD.getDisplayName());
        provTestPD.setReadable(true);
        provTestPD.setWritable(true);
        provTestPD.setMaxAllowed(1);
        provTestPD.setPropertyType(string_type);

        AssetType mockProvider = new AssetType();
        mockProvider.setName(SERVICE_PROVIDER_TYPE);
        mockProvider.setDisplayName(SERVICE_PROVIDER_NAME);
        mockProvider.setDescription(SERVICE_PROVIDER_DESCRIPTION);
        mockProvider.getPropertyDefinitions().add(provTestPD);
        mockProvider.setSuperType(serviceprovidertype);

        registration.getAssetTypes().add(launchItemType);
        registration.getAssetTypes().add(connectionType);
        registration.getAssetTypes().add(serviceType);
        registration.getAssetTypes().add(mockProvider);
        registration.getServiceProviderTypes().addAll(getServiceProviderTypes());
        return registration;
    }

    private void makePackage()
    {
        // Maintain a package that has one simple startup script
        String description = SERVICE_DESCRIPTION + " - " +
            new SimpleDateFormat().format(new Date());

        Script mockScript = makeScript(description);
        if (mockScript == null) {
            logger.error("onRegistration: unable to persist Script " +
                         SCRIPT_NAME);
            return;
        }

        Link scriptLink = new Link();
        scriptLink.setName(SCRIPT_NAME);
        scriptLink.setId(mockScript.getId());
        scriptLink.setType("application/" + Script.class.getName() + "+xml");

        Package pkg = new Package();
        pkg.setName(PACKAGE_NAME);
        pkg.setDescription(description);
        pkg.setVersion(0);
        pkg.getStartups().add(scriptLink);

        Package mockPkg = (Package)createOrUpdateAsset(pkg, null);
        if (mockPkg == null) {
            logger.error("onRegistration: unable to persist Package " +
                         PACKAGE_NAME);
        }
        MockOperationsManager.setMockPackage(mockPkg);
        MockOperationsManager.setMockScript(mockScript);
        if (logger.isDebugEnabled()) {
            logger.debug("Persisted Package '" + mockPkg.getName() + " ', id=" +
                         mockPkg.getId() + "; Script '" + mockScript.getName()
                         + "', id=" + mockScript.getId());
        }
    }

    private Script makeScript(String description)
    {
        Script script = new Script();
        script.setName(SCRIPT_NAME);
        script.setDescription(description);
        script.setRunAsAdmin(true);

        Link string_type = new Link();
        string_type.setName("string-any");
        string_type.setType("application/" + PropertyType.class.getName() +
                            "+xml");

        PropertyDefinition variable = new PropertyDefinition();
        variable.setName(SCRIPT_VARIABLE_NAME);
        variable.setDisplayName(SCRIPT_VARIABLE_NAME);
        variable.setReadable(true);
        variable.setWritable(true);
        variable.setMaxAllowed(1);
        variable.setPropertyType(string_type);
        script.getVariables().add(variable);

        String body = "#!/bin/bash\n# " + description + "\n" + "echo \"" +
            SCRIPT_VARIABLE_NAME + "=$" + SCRIPT_VARIABLE_NAME + "\"";
        script.setBody(body);

        return (Script)createOrUpdateAsset(script, null);
    }

    private Asset createOrUpdateAsset(Asset asset, Asset parent)
    {
        Asset dbAsset = null;
        try {
            if (asset.getId() == null) {
                asset.setId(getAssetId(asset));
            }

            if (asset.getId() == null) {
                dbAsset = createAsset(asset, parent).get();
            }
            else {
                dbAsset = updateAsset(asset, parent).get();
            }
        }
        catch (Throwable t) {
            logger.error("createOrUpdateAsset: " + asset.getClass().getName()
                         + " '" + asset.getName() + "': " + t);
        }
        return dbAsset;
    }

    private Integer getAssetId(Asset asset)
    {
        Integer id = null;
        try {
            Property filter = new Property();
            filter.setName("qterm.field.name");
            filter.setValue(asset.getName());

            List<Property> params = new ArrayList<Property>();
            params.add(filter);

            Promise<List<Asset>> promise =
                getAssets(asset.getClass().getName(), params);

            List<Asset> assets = promise.get();
            if (assets != null) {
                for (Asset listAsset : assets) {
                    if (asset.getName().equals(listAsset.getName())) {
                        id = listAsset.getId();

                        if (logger.isDebugEnabled()) {
                            logger.debug("retrieveAsset: " +
                                         asset.getClass().getName() + " '" +
                                         asset.getName() +
                                         "' , id=" + asset.getId());
                        }
                        break;
                    }
                }
            }
        }
        catch (Throwable t) {
            logger.error("retrieveAsset: " + asset.getClass().getName()
                         + " '" + asset.getName() + "': " + t);
        }
        return id;
    }
}
