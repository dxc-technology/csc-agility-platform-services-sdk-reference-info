package com.servicemesh.agility.adapters.service.mockns;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.service.mockns.operations.MockNetSvcBindingOperations;
import com.servicemesh.agility.adapters.service.mockns.operations.MockNetSvcInstanceOperations;
import com.servicemesh.agility.adapters.service.mockns.operations.MockNetSvcProviderOperations;

import com.servicemesh.agility.api.AssetType;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.ServiceProviderOption;
import com.servicemesh.agility.api.ServiceProviderType;
import com.servicemesh.agility.sdk.service.msgs.RegistrationRequest;
import com.servicemesh.agility.sdk.service.msgs.RegistrationResponse;
import com.servicemesh.agility.sdk.service.spi.IInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceInstance;
import com.servicemesh.agility.sdk.service.spi.IServiceProvider;
import com.servicemesh.agility.sdk.service.spi.ServiceAdapter;
import com.servicemesh.core.reactor.TimerReactor;

public class MockNetworkServiceAdapter extends ServiceAdapter
{
    private static final Logger logger =
        Logger.getLogger(MockNetworkServiceAdapter.class);
    public static final String SERVICE_PROVIDER_NAME = "Mock Network Service Provider";
    public static final String SERVICE_PROVIDER_DESCRIPTION;
    public static final String SERVICE_PROVIDER_TYPE = "mock-network-service-provider";
    public static final String SERVICE_PROVIDER_VERSION;

    static {
        String PROP_FILE = "/resources/MockNetworkServiceAdapter.properties";
        Properties props = new Properties();
        try {
            InputStream rs =
                MockNetworkServiceAdapter.class.getResourceAsStream(PROP_FILE);
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
    }

    public MockNetworkServiceAdapter() throws Exception
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

        Link assetType = new Link();
        assetType.setName(SERVICE_PROVIDER_TYPE);
        assetType.setType("application/" + AssetType.class.getName() + "+xml");
        serviceProviderType.setAssetType(assetType);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_HOSTNAME);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_USERNAME);
        serviceProviderType.getOptions().add(ServiceProviderOption.NO_PASSWORD);
        serviceProviderTypes.add(serviceProviderType);
        return serviceProviderTypes;
    }

    @Override
    public void onRegistration(RegistrationResponse response)
    {
    }

    @Override
    public IInstanceLifecycle getInstanceOperations()
    {
        return new MockNetSvcInstanceOperations();
    }

    @Override
    public IServiceProvider getServiceProviderOperations()
    {
        return new MockNetSvcProviderOperations();
    }

    @Override
    public IServiceInstance getServiceInstanceOperations()
    {
        return new MockNetSvcBindingOperations();
    }

    @Override
    public RegistrationRequest getRegistrationRequest()
    {
        logger.debug("getRegistrationRequest");
        RegistrationRequest registration = new RegistrationRequest();
        registration.setName(SERVICE_PROVIDER_NAME);
        registration.setVersion(SERVICE_PROVIDER_VERSION);

        // This adapter only supports a Service Provider asset type. It doesn't
        // need to define a Service asset type. Inheriting from networkservice
        // will cause the Agility Platform to automatically create a Service
        // Instance and connect it to a VM instance that has the same network
        // as one registered to an instance of the the Service Provider
        Link networkservice = new Link();
        networkservice.setName("networkservice");

        AssetType svcProvider = new AssetType();
        svcProvider.setName(SERVICE_PROVIDER_TYPE);
        svcProvider.setDisplayName(SERVICE_PROVIDER_NAME);
        svcProvider.setDescription(SERVICE_PROVIDER_DESCRIPTION);
        svcProvider.setSuperType(networkservice);

        registration.getAssetTypes().add(svcProvider);
        registration.getServiceProviderTypes().addAll(getServiceProviderTypes());
        return registration;
    }
}
