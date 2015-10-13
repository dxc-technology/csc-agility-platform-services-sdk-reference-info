/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.microsoft.schemas.azure.trafficmgr.Endpoint;
import com.microsoft.schemas.azure.trafficmgr.LoadBalancingMethod;
import com.microsoft.schemas.azure.trafficmgr.Profile;
import com.microsoft.schemas.azure.trafficmgr.StatusDetails;
import com.microsoft.schemas.azure.trafficmgr.Type;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.core.azure.Config;
import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.agility.adapters.core.azure.util.AzureUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.operations.TrafficManagerConnectionOperations;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.operations.TrafficManagerInstanceOperations;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.operations.TrafficManagerProviderOperations;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.operations.TrafficManagerServiceInstanceOperations;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.operations.TrafficManagerServiceLifecycleOperations;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.AssetType;
import com.servicemesh.agility.api.Cloud;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.ConnectionDefinition;
import com.servicemesh.agility.api.FieldValidators;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.IntegerRange;
import com.servicemesh.agility.api.IntegerRangeValidator;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.PrimitiveType;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.PropertyDefinition;
import com.servicemesh.agility.api.PropertyType;
import com.servicemesh.agility.api.PropertyTypeValue;
import com.servicemesh.agility.api.RegexValidator;
import com.servicemesh.agility.api.Service;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.api.ServiceProviderOption;
import com.servicemesh.agility.api.ServiceProviderType;
import com.servicemesh.agility.api.StringLengthValidator;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.api.ValueConstraintType;
import com.servicemesh.agility.sdk.service.msgs.RegistrationRequest;
import com.servicemesh.agility.sdk.service.msgs.RegistrationResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.spi.Constants;
import com.servicemesh.agility.sdk.service.spi.IConnection;
import com.servicemesh.agility.sdk.service.spi.IInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceInstance;
import com.servicemesh.agility.sdk.service.spi.IServiceInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceProvider;
import com.servicemesh.agility.sdk.service.spi.ServiceAdapter;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.core.reactor.TimerReactor;
import com.servicemesh.io.http.IHttpResponse;

public class TrafficManagerAdapter extends ServiceAdapter
{

    private static final Logger logger = Logger.getLogger(TrafficManagerAdapter.class);
    public static final String SERVICE_PROVIDER_NAME = "Azure Traffic Manager Provider";
    public static final String SERVICE_PROVIDER_DESCRIPTION;
    public static final String SERVICE_PROVIDER_TYPE = "azure-traffic-manager-provider";
    public static final String SERVICE_PROVIDER_VERSION;

    public static final String SERVICE_TYPE = "azure-traffic-manager";
    public static final String SERVICE_NAME = "Azure Traffic Manager";
    public static final String SERVICE_DESCRIPTION;

    public static final String CONNECTION_TYPE = "azure-traffic-manager-connection";
    public static final String CONNECTION_NAME = "Azure Traffic Manager Connection";
    public static final String CONNECTION_DESCRIPTION;

    static {
        String PROP_FILE = "/resources/TrafficManagerAdapter.properties";
        Properties props = new Properties();
        try {
            InputStream rs = TrafficManagerAdapter.class.getResourceAsStream(PROP_FILE);
            if (rs != null) {
                props.load(rs);
            }
            else {
                TrafficManagerAdapter.logger.error("Resource not found " + PROP_FILE);
            }
        }
        catch (Exception ex) {
            TrafficManagerAdapter.logger.error("Failed to load " + PROP_FILE + ": " + ex);
        }
        SERVICE_PROVIDER_VERSION = props.getProperty("adapter.version", "0.0.0");
        String revision = props.getProperty("adapter.revision", "");
        String vendor = props.getProperty("adapter.vendor", "");

        StringBuilder sb = new StringBuilder();
        sb.append(" (").append(TrafficManagerAdapter.SERVICE_PROVIDER_VERSION);
        if (!revision.isEmpty()) {
            sb.append(" ").append(revision);
        }
        if (!vendor.isEmpty()) {
            sb.append(" ").append(vendor);
        }
        sb.append(")");
        SERVICE_PROVIDER_DESCRIPTION = TrafficManagerAdapter.SERVICE_PROVIDER_NAME + sb.toString();
        SERVICE_DESCRIPTION = TrafficManagerAdapter.SERVICE_NAME + sb.toString();
        CONNECTION_DESCRIPTION = TrafficManagerAdapter.CONNECTION_NAME + sb.toString();
    }

    private final ConnectionFactory _factory = new ConnectionFactory();

    public TrafficManagerAdapter() throws Exception
    {
        super(TimerReactor.getTimerReactor(TrafficManagerAdapter.SERVICE_PROVIDER_NAME));
        _factory.unregisterContext();
        TrafficManagerAdapter.logger.info(TrafficManagerAdapter.SERVICE_PROVIDER_DESCRIPTION);
    }

    @Override
    public List<ServiceProviderType> getServiceProviderTypes()
    {
        List<ServiceProviderType> serviceProviderTypes = new ArrayList<ServiceProviderType>();
        ServiceProviderType serviceProviderType = new ServiceProviderType();
        serviceProviderType.setName(TrafficManagerAdapter.SERVICE_PROVIDER_NAME);
        serviceProviderType.setDescription(TrafficManagerAdapter.SERVICE_PROVIDER_DESCRIPTION);
        Link serviceType = new Link();
        serviceType.setName(TrafficManagerAdapter.SERVICE_TYPE);
        serviceProviderType.getServiceTypes().add(serviceType);
        Link assetType = new Link();
        assetType.setName(TrafficManagerAdapter.SERVICE_PROVIDER_TYPE);
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
    }

    @Override
    public IInstanceLifecycle getInstanceOperations()
    {
        return new TrafficManagerInstanceOperations(this, _factory);
    }

    @Override
    public IServiceProvider getServiceProviderOperations()
    {
        return new TrafficManagerProviderOperations(this, _factory);
    }

    @Override
    public IServiceInstance getServiceInstanceOperations()
    {
        return new TrafficManagerServiceInstanceOperations(this, _factory);
    }

    @Override
    public IServiceInstanceLifecycle getServiceInstanceLifecycleOperations()
    {
        return new TrafficManagerServiceLifecycleOperations(this, _factory);
    }

    @Override
    public IConnection getConnectionOperations()
    {
        return new TrafficManagerConnectionOperations(this, _factory);
    }

    /**
     * Build up the set of asset types exposed by the adapter and return these in the registration request. If the adapter exposes
     * a service to an application via a blueprint, a sub-class of service is defined to expose this functionality and define
     * configuration parameters for the service. A sub-class of serviceprovider exposes configuration parameters for the adapter
     * itself.
     */

    @Override
    public RegistrationRequest getRegistrationRequest()
    {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setName(TrafficManagerAdapter.SERVICE_PROVIDER_NAME);
        registration.setVersion(TrafficManagerAdapter.SERVICE_PROVIDER_VERSION);

        // references to common types
        Link string_type = new Link();
        string_type.setName("string-any");
        string_type.setType("application/" + PropertyType.class.getName() + "+xml");

        Link integer_type = new Link();
        integer_type.setName("integer-any");
        integer_type.setType("application/" + PropertyType.class.getName() + "+xml");

        Link byte_type = new Link();
        byte_type.setName("binary");
        byte_type.setType("application/" + PropertyType.class.getName() + "+xml");

        Link encrypted_type = new Link();
        encrypted_type.setName("encrypted");
        encrypted_type.setType("application/" + PropertyType.class.getName() + "+xml");

        // property definition for profile name
        PropertyDefinition profileNamePD = new PropertyDefinition();
        profileNamePD.setName(TrafficManagerConfig.CONFIG_PROFILE_NAME);
        profileNamePD.setDisplayName("Profile Name");
        profileNamePD
                .setDescription("Specifies the name of the profile. Profile name uniquely identifies the Azure Traffic Manager service. If not specified, Domain Name is used with hyphens replacing dots.");
        profileNamePD.setReadable(true);
        profileNamePD.setWritable(true);
        profileNamePD.setMaxAllowed(1);
        profileNamePD.setPropertyType(string_type);
        StringLengthValidator validator = new StringLengthValidator();
        validator.setMaxLength(256);
        RegexValidator validatorProfile = new RegexValidator();
        validatorProfile.getExpressions().add("^[A-Za-z0-9-]*$");
        profileNamePD.setValidator(new FieldValidators());
        profileNamePD.getValidator().getValidators().add(validator);
        profileNamePD.getValidator().getValidators().add(validatorProfile);

        // property definition for monitor interval
        PropertyDefinition intervalPD = new PropertyDefinition();
        intervalPD.setName(TrafficManagerConfig.CONFIG_MONITOR_INTERVAL);
        intervalPD.setDisplayName("Interval In Seconds");
        intervalPD
                .setDescription("Not configurable. 30 seconds between consecutive attempts to check the status of a monitoring endpoint.");
        intervalPD.setReadable(true);
        intervalPD.setWritable(false);
        intervalPD.setMaxAllowed(1);
        intervalPD.setPropertyType(integer_type);
        AssetProperty default1 = new AssetProperty();
        default1.setIntValue(TrafficManagerConfig.MONITOR_POLL_INTERVAL_DEFAULT);
        IntegerRange rangeInterval = new IntegerRange();
        rangeInterval.setMax(String.valueOf(TrafficManagerConfig.MONITOR_POLL_INTERVAL_DEFAULT));
        rangeInterval.setMin(String.valueOf(TrafficManagerConfig.MONITOR_POLL_INTERVAL_DEFAULT));
        IntegerRangeValidator validatorInterval = new IntegerRangeValidator();
        validatorInterval.getRanges().add(rangeInterval);
        intervalPD.setValidator(new FieldValidators());
        intervalPD.getValidator().getValidators().add(validatorInterval);
        intervalPD.getDefaultValues().add(default1);

        // property definition for monitor interval
        PropertyDefinition timeoutPD = new PropertyDefinition();
        timeoutPD.setName(TrafficManagerConfig.CONFIG_MONITOR_TIMEOUT);
        timeoutPD.setDisplayName("Timeout In Seconds");
        timeoutPD.setDescription("Not configurable. 10 second wait for response from the monitoring endpoint.");
        timeoutPD.setReadable(true);
        timeoutPD.setWritable(false);
        timeoutPD.setMaxAllowed(1);
        timeoutPD.setPropertyType(integer_type);
        AssetProperty default2 = new AssetProperty();
        default2.setIntValue(TrafficManagerConfig.MONITOR_POLL_TIMEOUT_DEFAULT);
        IntegerRange rangeTimeout = new IntegerRange();
        rangeTimeout.setMax(String.valueOf(TrafficManagerConfig.MONITOR_POLL_TIMEOUT_DEFAULT));
        rangeTimeout.setMin(String.valueOf(TrafficManagerConfig.MONITOR_POLL_TIMEOUT_DEFAULT));
        IntegerRangeValidator validatorTimeout = new IntegerRangeValidator();
        validatorTimeout.getRanges().add(rangeTimeout);
        timeoutPD.setValidator(new FieldValidators());
        timeoutPD.getValidator().getValidators().add(validatorTimeout);
        timeoutPD.getDefaultValues().add(default2);

        // property definition for monitor tolerated number of failures
        PropertyDefinition retryPD = new PropertyDefinition();
        retryPD.setName(TrafficManagerConfig.CONFIG_MONITOR_RETRIES);
        retryPD.setDisplayName("Number Of Failures");
        retryPD.setDescription("Not configurable. 3 consecutive failures to probe an endpoint before taking the endpoint out of rotation.");
        retryPD.setReadable(true);
        retryPD.setWritable(false);
        retryPD.setMaxAllowed(1);
        retryPD.setPropertyType(integer_type);
        AssetProperty default3 = new AssetProperty();
        default3.setIntValue(TrafficManagerConfig.MONITOR_POLL_RETRY_DEFAULT);
        IntegerRange rangeRetry = new IntegerRange();
        rangeRetry.setMax(String.valueOf(TrafficManagerConfig.MONITOR_POLL_RETRY_DEFAULT));
        rangeRetry.setMin(String.valueOf(TrafficManagerConfig.MONITOR_POLL_RETRY_DEFAULT));
        IntegerRangeValidator validatorRetry = new IntegerRangeValidator();
        validatorRetry.getRanges().add(rangeRetry);
        retryPD.setValidator(new FieldValidators());
        retryPD.getValidator().getValidators().add(validatorRetry);
        retryPD.getDefaultValues().add(default3);

        // build up monitor protocol type options
        PropertyTypeValue http = new PropertyTypeValue();
        http.setName(TrafficManagerConfig.CONFIG_PROTOCOL_HTTP);
        http.setDisplayName(http.getName());
        http.setValue(http.getName());

        PropertyTypeValue https = new PropertyTypeValue();
        https.setName(TrafficManagerConfig.CONFIG_PROTOCOL_HTTPS);
        https.setDisplayName(https.getName());
        https.setValue(https.getName());

        PropertyType protocolType = new PropertyType();
        protocolType.setName(TrafficManagerConfig.CONFIG_MONITOR_PROTOCOL);
        protocolType.setType(PrimitiveType.STRING);
        protocolType.setDisplayName("Monitor Protocol");
        protocolType.setValueConstraint(ValueConstraintType.LIST);
        protocolType.getRootValues().add(http);
        protocolType.getRootValues().add(https);

        Link protocol_link = new Link();
        protocol_link.setName(TrafficManagerConfig.CONFIG_MONITOR_PROTOCOL);
        protocol_link.setType("application/" + PropertyType.class.getName() + "+xml");

        // property definition for monitor protocol type
        PropertyDefinition protocolPD = new PropertyDefinition();
        protocolPD.setName(TrafficManagerConfig.CONFIG_MONITOR_PROTOCOL);
        protocolPD.setDisplayName("Protocol");
        protocolPD.setDescription("Protocol to monitor endpoint health.");
        protocolPD.setReadable(true);
        protocolPD.setWritable(true);
        protocolPD.setMinRequired(1);
        protocolPD.setMaxAllowed(1);
        protocolPD.setPropertyType(protocol_link);
        protocolPD.setPropertyTypeValue(protocolType);

        // property definition for monitor port
        PropertyDefinition portPD = new PropertyDefinition();
        portPD.setName(TrafficManagerConfig.CONFIG_MONITOR_PORT);
        portPD.setDisplayName("Port");
        portPD.setDescription("Port used to monitor endpoint health.");
        portPD.setReadable(true);
        portPD.setWritable(true);
        portPD.setMinRequired(1);
        portPD.setMaxAllowed(1);
        portPD.setPropertyType(integer_type);
        IntegerRange range1 = new IntegerRange();
        range1.setMax("65535");
        range1.setMin("1");
        IntegerRangeValidator validator2 = new IntegerRangeValidator();
        validator2.getRanges().add(range1);
        portPD.setValidator(new FieldValidators());
        portPD.getValidator().getValidators().add(validator2);

        // property definition for monitor relative path
        PropertyDefinition pathPD = new PropertyDefinition();
        pathPD.setName(TrafficManagerConfig.CONFIG_MONITOR_RELATIVE_PATH);
        pathPD.setDescription("Path relative to the endpoint domain name to probe for health state.");
        pathPD.setDisplayName("Relative Path");
        pathPD.setReadable(true);
        pathPD.setWritable(true);
        pathPD.setMaxAllowed(1);
        pathPD.setPropertyType(string_type);
        AssetProperty default4 = new AssetProperty();
        default4.setStringValue(TrafficManagerConfig.MONITOR_RELATIVE_PATH_DEFAULT);
        pathPD.getDefaultValues().add(default4);
        StringLengthValidator validator3 = new StringLengthValidator();
        validator3.setMinLength(1);
        validator3.setMaxLength(1000);
        RegexValidator validator4 = new RegexValidator();
        validator4.getExpressions().add("(/[a-zA-Z0-9._/-]*?)(\\.)?$");
        pathPD.setValidator(new FieldValidators());
        pathPD.getValidator().getValidators().add(validator3);
        pathPD.getValidator().getValidators().add(validator4);

        // property definition for monitor interval
        PropertyDefinition verbPD = new PropertyDefinition();
        verbPD.setName(TrafficManagerConfig.CONFIG_MONITOR_VERB);
        verbPD.setDisplayName("Verb");
        verbPD.setDescription("Verb to use when making an HTTP request to monitor endpoint health.");
        verbPD.setReadable(true);
        verbPD.setWritable(true);
        verbPD.setMaxAllowed(1);
        verbPD.setPropertyType(string_type);
        AssetProperty default5 = new AssetProperty();
        default5.setStringValue(TrafficManagerConfig.MONITOR_VERB_DEFAULT);
        verbPD.getDefaultValues().add(default5);

        // property definition for monitor expected status code
        PropertyDefinition statusCodePD = new PropertyDefinition();
        statusCodePD.setName(TrafficManagerConfig.CONFIG_MONITOR_STATUS_CODE);
        statusCodePD.setDisplayName("Expected Status Code");
        statusCodePD
                .setDescription("HTTP status code expected from a healthy endpoint. The endpoint is considered unhealthy otherwise.");
        statusCodePD.setReadable(true);
        statusCodePD.setWritable(true);
        statusCodePD.setMaxAllowed(1);
        statusCodePD.setPropertyType(integer_type);
        AssetProperty default6 = new AssetProperty();
        default6.setIntValue(TrafficManagerConfig.MONITOR_STATUS_CODE_DEFAULT);
        statusCodePD.getDefaultValues().add(default6);

        // define azure traffic manager service
        Link service = new Link();
        service.setName("dns-lbaas");
        service.setType("application/" + Service.class.getName() + "+xml");

        AssetType serviceType = new AssetType();
        serviceType.setName(TrafficManagerAdapter.SERVICE_TYPE);
        serviceType.setDisplayName(TrafficManagerAdapter.SERVICE_NAME);
        serviceType.setDescription(TrafficManagerAdapter.SERVICE_DESCRIPTION);
        serviceType.setSuperType(service);
        serviceType.getPropertyDefinitions().add(profileNamePD);
        serviceType.getPropertyDefinitions().add(intervalPD);
        serviceType.getPropertyDefinitions().add(timeoutPD);
        serviceType.getPropertyDefinitions().add(retryPD);
        serviceType.getPropertyDefinitions().add(protocolPD);
        serviceType.getPropertyDefinitions().add(portPD);
        serviceType.getPropertyDefinitions().add(pathPD);
        serviceType.getPropertyDefinitions().add(verbPD);
        serviceType.getPropertyDefinitions().add(statusCodePD);

        // define connections
        Link azureConnectionLink = new Link();
        azureConnectionLink.setName(TrafficManagerAdapter.CONNECTION_TYPE);
        azureConnectionLink.setType("application/" + Connection.class.getName() + "+xml");
        Link workloadLink = new Link();
        workloadLink.setName("designworkload");
        Link atmLink = new Link();
        atmLink.setName(TrafficManagerAdapter.SERVICE_TYPE);
        Link serviceLink = new Link();
        serviceLink.setName("service");
        // workload to traffic manager connection
        ConnectionDefinition workloadDestConnection = new ConnectionDefinition();
        workloadDestConnection.setName("designworkload");
        workloadDestConnection.setDescription("Represents a workload dependency");
        workloadDestConnection.setConnectionType(azureConnectionLink);
        workloadDestConnection.setSourceType(workloadLink);
        serviceType.getDestConnections().add(workloadDestConnection);
        // traffic manager to traffic manager connection
        ConnectionDefinition trafficDestConnection = new ConnectionDefinition();
        trafficDestConnection.setName("trafficmanager");
        trafficDestConnection.setDescription("Represents nested traffic manager service dependency");
        trafficDestConnection.setConnectionType(azureConnectionLink);
        trafficDestConnection.setSourceType(atmLink);
        serviceType.getDestConnections().add(trafficDestConnection);
        // any service to traffic manager connection
        ConnectionDefinition anyServiceDestConnection = new ConnectionDefinition();
        anyServiceDestConnection.setName("service");
        anyServiceDestConnection.setDescription("Represents any service dependency");
        anyServiceDestConnection.setConnectionType(azureConnectionLink);
        anyServiceDestConnection.setSourceType(serviceLink);
        serviceType.getDestConnections().add(anyServiceDestConnection);

        Link connection = new Link();
        connection.setName("designconnection");
        connection.setType("application/" + Connection.class.getName() + "+xml");

        // property definition for minimum child endpoints
        PropertyDefinition minChildPD = new PropertyDefinition();
        minChildPD.setName(TrafficManagerConfig.CONFIG_MIN_CHILD_ENDPOINTS);
        minChildPD.setDisplayName("Min Child Endpoints");
        minChildPD
                .setDescription("Requires Traffic Manager endpoint. Min number of healthy endpoints within a nested profile that determines whether any of the endpoints within that profile can receive traffic.");
        minChildPD.setReadable(true);
        minChildPD.setWritable(true);
        minChildPD.setMaxAllowed(1);
        minChildPD.setPropertyType(integer_type);
        AssetProperty default7 = new AssetProperty();
        default7.setIntValue(TrafficManagerConfig.MIN_CHILD_ENDPOINTS_DEFAULT);
        minChildPD.getDefaultValues().add(default7);

        // property definition for service endpoint location
        // Required when LoadBalancingMethod is set to Performance and Type is set to Any or TrafficManager.
        PropertyDefinition locationPD = new PropertyDefinition();
        locationPD.setName(TrafficManagerConfig.CONFIG_LOCATION);
        locationPD.setDisplayName("Location");
        locationPD
                .setDescription("Name of the Azure region. Required when Load Balancing Method is set to Performance and endpoint is Traffic Manager or another service. The Location cannot be specified for workload endpoints in which the locations are determined from the service.");
        locationPD.setReadable(true);
        locationPD.setWritable(true);
        locationPD.setMaxAllowed(1);
        locationPD.setPropertyType(string_type);

        // weight
        PropertyDefinition weightPD = new PropertyDefinition();
        weightPD.setName(TrafficManagerConfig.CONFIG_WEIGHT);
        weightPD.setDisplayName("Weight");
        weightPD.setDescription("Priority of the endpoint in load balancing. The higher the weight, the more frequently the endpoint will be made available to the load balancer.");
        weightPD.setReadable(true);
        weightPD.setWritable(true);
        weightPD.setMaxAllowed(1);
        weightPD.setPropertyType(integer_type);
        AssetProperty defaultWeight = new AssetProperty();
        defaultWeight.setIntValue(TrafficManagerConfig.WEIGHT_DEFAULT);
        weightPD.getDefaultValues().add(defaultWeight);
        IntegerRange rangeWeight = new IntegerRange();
        rangeWeight.setMin("1");
        IntegerRangeValidator validatorWeight = new IntegerRangeValidator();
        validatorWeight.getRanges().add(rangeWeight);
        weightPD.setValidator(new FieldValidators());
        weightPD.getValidator().getValidators().add(validatorWeight);

        // order
        PropertyDefinition orderPD = new PropertyDefinition();
        orderPD.setName(TrafficManagerConfig.CONFIG_ORDER);
        orderPD.setDisplayName("Order");
        orderPD.setDescription("Priority of the endpoint in failover load balancing. Endpoints are ordered in ascending order of this number. If order not specified, endpoint will be in any order at the end of ordered endpoints.");
        orderPD.setReadable(true);
        orderPD.setWritable(true);
        orderPD.setMaxAllowed(1);
        orderPD.setPropertyType(integer_type);
        IntegerRange rangeOrder = new IntegerRange();
        rangeOrder.setMin("1");
        IntegerRangeValidator validatorOrder = new IntegerRangeValidator();
        validatorOrder.getRanges().add(rangeOrder);
        orderPD.setValidator(new FieldValidators());
        orderPD.getValidator().getValidators().add(validatorOrder);

        AssetType connectionType = new AssetType();
        connectionType.setName(TrafficManagerAdapter.CONNECTION_TYPE);
        connectionType.setDescription(TrafficManagerAdapter.CONNECTION_DESCRIPTION);
        connectionType.setDisplayName(TrafficManagerAdapter.CONNECTION_NAME);
        connectionType.setSuperType(connection);
        connectionType.getPropertyDefinitions().add(weightPD);
        connectionType.getPropertyDefinitions().add(minChildPD);
        connectionType.getPropertyDefinitions().add(locationPD);
        connectionType.getPropertyDefinitions().add(orderPD);
        connectionType.setAllowExtensions(true);

        // define azure traffic manager provider
        Link serviceprovidertype = new Link();
        serviceprovidertype.setName("serviceprovidertype");
        serviceprovidertype.setType("application/" + ServiceProviderType.class.getName() + "+xml");

        // define azure traffic manager provider subscription
        PropertyDefinition subscriptionPD = new PropertyDefinition();
        subscriptionPD.setName(com.servicemesh.agility.adapters.core.azure.Config.CONFIG_SUBSCRIPTION);
        subscriptionPD.setDescription("Traffic Manager provider subscription.");
        subscriptionPD.setDisplayName("Subscription");
        subscriptionPD.setReadable(true);
        subscriptionPD.setWritable(true);
        subscriptionPD.setMaxAllowed(1);
        subscriptionPD.setPropertyType(string_type);

        // define azure traffic manager provider certificate
        PropertyDefinition certificatePD = new PropertyDefinition();
        certificatePD.setName(com.servicemesh.agility.adapters.core.azure.Config.CONFIG_CERTIFICATE);
        certificatePD.setDescription("Traffic Manager provider certificate.");
        certificatePD.setDisplayName("Certificate");
        certificatePD.setReadable(true);
        certificatePD.setWritable(true);
        certificatePD.setMaxAllowed(1);
        certificatePD.setPropertyType(byte_type);

        // define azure traffic manager provider certificate private key
        PropertyDefinition privatePD = new PropertyDefinition();
        privatePD.setName(com.servicemesh.agility.adapters.core.azure.Config.CONFIG_PRIVATE_KEY);
        privatePD.setDescription("Traffic Manager provider certificate private key.");
        privatePD.setDisplayName("Private Key");
        privatePD.setReadable(true);
        privatePD.setWritable(true);
        privatePD.setMaxAllowed(1);
        privatePD.setPropertyType(encrypted_type);

        AssetType atmprovider = new AssetType();
        atmprovider.setName(TrafficManagerAdapter.SERVICE_PROVIDER_TYPE);
        atmprovider.setDisplayName(TrafficManagerAdapter.SERVICE_PROVIDER_NAME);
        atmprovider.setDescription(TrafficManagerAdapter.SERVICE_PROVIDER_DESCRIPTION);
        atmprovider.getPropertyDefinitions().add(subscriptionPD);
        atmprovider.getPropertyDefinitions().add(certificatePD);
        atmprovider.getPropertyDefinitions().add(privatePD);
        atmprovider.setSuperType(serviceprovidertype);
        registration.getAssetTypes().add(connectionType);
        registration.getAssetTypes().add(serviceType);
        registration.getAssetTypes().add(atmprovider);
        registration.getServiceProviderTypes().addAll(getServiceProviderTypes());

        return registration;
    }

    public Type getEndpointType(ServiceInstance instance)
    {
        if (instance.getAssetType() != null && instance.getAssetType().getName().equals(TrafficManagerAdapter.SERVICE_TYPE)) {
            return Type.TRAFFIC_MANAGER;
        }
        else {
            return Type.ANY;
        }
    }

    public Asset findAsset(List<Asset> assets, Link link)
    {
        // connections are relative to service instance
        // one of destination connection source is dependent asset
        for (Asset asset : assets) {
            if (asset instanceof ServiceInstance
                    && link.getType().equals("application/com.servicemesh.agility.api.ServiceInstance+xml")
                    && asset.getId() == link.getId()) {
                return asset;
            }
            if (asset instanceof Template && link.getType().equals("application/com.servicemesh.agility.api.Template+xml")
                    && asset.getId() == link.getId()) {
                return asset;
            }
        }
        return null;
    }

    public Connection findDependentConnection(List<Connection> destConnections, Asset item, String type)
    {
        // connections are relative to service instance
        // one of destination connection source is dependent asset
        for (Connection conn : destConnections) {
            Link source = conn.getSource();
            if (source == null) {
                continue;
            }
            if (item.getId() == source.getId() && source.getType().equals(type)) {
                return conn;
            }
        }
        return null;
    }

    public List<Asset> findInstanceAssets(List<Asset> assets, Template template)
    {
        List<Asset> instances = new ArrayList<Asset>();
        for (Asset asset : assets) {
            if (asset instanceof Instance) {
                for (Link instLink : template.getInstances()) {
                    if (instLink.getId() == asset.getId()) {
                        instances.add(asset);
                    }
                }
            }
        }
        return instances;
    }

    public Template findTemplate(Instance inst, List<Asset> dependents)
    {
        for (Asset dep : dependents) {
            if (dep instanceof Template) {
                for (Link link : ((Template) dep).getInstances()) {
                    if (link.getId() == inst.getId()) {
                        return (Template) dep;
                    }
                }
            }
        }
        return null;
    }

    public Connection findDependentConnection(Endpoint endpoint, List<Asset> dependents, List<Connection> destConnections)
    {
        // find dependent by endpoint domain name
        for (Asset dep : dependents) {
            if (dep instanceof ServiceInstance) {
                String dns = this.getDomainName((ServiceInstance) dep);
                if (dns != null && dns.equals(endpoint.getDomainName())) {
                    return this.findDependentConnection(destConnections, dep,
                            "application/com.servicemesh.agility.api.ServiceInstance+xml");
                }
            }
            else if (dep instanceof Instance) {
                String dns = this.getDomainName((Instance) dep);
                // try to reverse it as it may not have been set yet due to timing
                if (dns == null) {
                    try {
                        certifyCanonicalName((Instance) dep);
                        dns = this.getDomainName((Instance) dep);
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }
                if (dns != null && dns.equals(endpoint.getDomainName())) {
                    // find instance template
                    Template template = findTemplate((Instance) dep, dependents);
                    if (template != null) {
                        return this.findDependentConnection(destConnections, template,
                                "application/com.servicemesh.agility.api.Template+xml");
                    }
                }
            }
        }
        return null;
    }

    public String getPropertyAsString(String name, List<Property> properties, String defaultValue)
    {
        String value = defaultValue;

        if (properties != null) {
            for (Property property : properties) {
                if (property.getName().equals(name)) {
                    value = property.getValue();
                    break;
                }
            }
        }
        return value;
    }

    public void addOrUpdateAssetProperty(String name, String value, List<AssetProperty> properties)
    {
        AssetProperty prop = null;
        for (AssetProperty curr : properties) {
            if (curr.getName().equals(name)) {
                prop = curr;
                break;
            }
        }
        if (prop == null) {
            prop = new AssetProperty();
            prop.setName(name);
            properties.add(prop);
        }
        prop.setStringValue(value);
    }

    public void addOrUpdateAssetPropertyAsInteger(String name, int value, List<AssetProperty> properties)
    {
        AssetProperty prop = null;
        for (AssetProperty curr : properties) {
            if (curr.getName().equals(name)) {
                prop = curr;
                break;
            }
        }
        if (prop == null) {
            prop = new AssetProperty();
            prop.setName(name);
            properties.add(prop);
        }
        prop.setIntValue(value);
    }

    public void addOrUpdateProperty(String name, String value, List<Property> properties)
    {
        Property prop = null;
        for (Property curr : properties) {
            if (curr.getName().equals(name)) {
                prop = curr;
                break;
            }
        }
        if (prop == null) {
            prop = new Property();
            prop.setName(name);
            properties.add(prop);
        }
        prop.setValue(value);
    }

    public void removeProperty(String name, List<Property> properties)
    {
        for (Iterator<Property> iter = properties.iterator(); iter.hasNext();) {
            Property prop = (Property) iter.next();
            if (prop.getName().equals(name)) {
                iter.remove();
            }
        }
    }

    public void removeAssetProperty(String name, List<AssetProperty> properties)
    {
        for (Iterator<AssetProperty> iter = properties.iterator(); iter.hasNext();) {
            AssetProperty prop = (AssetProperty) iter.next();
            if (prop.getName().equals(name)) {
                iter.remove();
            }
        }
    }

    public String getDomainName(ServiceInstance instance)
    {
        // first try meta model asset properties by standard name defined dns based load balancers
        String domainName = Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME, instance.getAssetProperties());
        if (domainName != null) {
            return domainName;
        }
        // try under a different name for non dns based services
        domainName = Config.getAssetPropertyAsString(Constants.FQ_DOMAIN_NAME, instance.getAssetProperties());
        if (domainName != null) {
            return domainName;
        }
        // try under a different name for non dns based services in configuration
        domainName = Config.getAssetPropertyAsString(Constants.FQ_DOMAIN_NAME, instance.getConfigurations());
        if (domainName != null) {
            return domainName;
        }
        return null;
    }

    public String getDomainName(Instance instance)
    {
        // Ideally we would use the Instance CanonicalName attribute but it
        // is already being used in ways that may not be compatible with
        // Traffic Manager. E.g., EC2 adapter sets it to the Amazon
        // private DNS name.
        return getPropertyAsString(Constants.FQ_DOMAIN_NAME, instance.getProperties(), null);
    }

    /**
     * Certifies the canonical name (fully qualified domain name) attribute for an Instance.
     * 
     * @param instance
     *            The Instance requiring a canonical name
     * @return true if instance was modified (i.e., Instance.setCanonicalName() was called)
     * @throws AzureAdapterException
     *             if no canonical name can be established
     */
    public boolean certifyCanonicalName(Instance instance) throws AzureAdapterException
    {
        String fqdn = getDomainName(instance);
        if (fqdn != null && !fqdn.isEmpty()) {
            // Could do a DNS lookup on this name but we will assume it
            // is valid
            return false;
        }

        // This would normally be unexpected, but perhaps a cloud adapter
        // placed a fqdn in the hostname attribute
        fqdn = instance.getHostname();
        if (fqdn != null && fqdn.indexOf('.') > 0) {
            saveCanonicalName(" via hostname", instance, fqdn);
            return true;
        }

        // Try a DNS lookup of the public address. Note that as of Mar2015 the
        // Azure cloud does not support reverse DNS lookups by default -
        // the customer has to provision it.
        if ((fqdn = AzureUtil.ipToFqdn(instance.getPublicAddress())) != null) {
            saveCanonicalName(" via public IP", instance, fqdn);
            return true;
        }

        // Try the private address
        if ((fqdn = AzureUtil.ipToFqdn(instance.getPrivateAddress())) != null) {
            saveCanonicalName(" via private IP", instance, fqdn);
            return true;
        }

        // Special case currently only applicable for an Azure Instance. The
        // Azure cloud adapter puts a hostname into DeploymentName property that
        // when combined with the default Azure domain may yield a valid FQDN.
        // However, we have to verify it since Azure customer may have
        // provisioned a custom domain name (which we don't know).
        String deploymentName = getPropertyAsString("DeploymentName", instance.getProperties(), null);
        if (deploymentName != null) {
            fqdn = deploymentName + ".cloudapp.net";
            List<String> ips = AzureUtil.domainNameToIp(fqdn);
            if (!ips.isEmpty()) {
                saveCanonicalName(" via DeploymentName", instance, fqdn);
                return true;
            }
        }
        throw new AzureAdapterException("certifyCanonicalName failed for Instance " + instance.getId() + ", name="
                + instance.getName());
    }

    public void saveCanonicalName(String source, Instance instance, String fqdn)
    {
        addOrUpdateProperty(Constants.FQ_DOMAIN_NAME, fqdn, instance.getProperties());
        if (TrafficManagerAdapter.logger.isDebugEnabled()) {
            TrafficManagerAdapter.logger.debug("Instance " + instance.getId() + ", name='" + instance.getName() + "', fqdn="
                    + fqdn + source);
        }
    }

    public static class ConnectionOrderComparator implements Comparator<Connection>
    {

        public ConnectionOrderComparator()
        {
        }

        public int compare(Connection o1, Connection o2)
        {
            if (o1 == null && o2 == null) {
                return 0;
            }
            else if (o1 == null) {
                return 1;
            }
            else if (o2 == null) {
                return -1;
            }
            else {
                int order1 =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                                TrafficManagerConfig.CONFIG_ORDER, o1.getAssetProperties(), 0);
                int order2 =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                                TrafficManagerConfig.CONFIG_ORDER, o2.getAssetProperties(), 0);
                if (order1 == 0 && order2 == 0) {
                    return 0;
                }
                else if (order1 == 0) {
                    return 1;
                }
                else if (order2 == 0) {
                    return -1;
                }
                return Integer.compare(order1, order2);
            }
        }
    }

    public static class EndpointOrderComparator implements Comparator<Endpoint>
    {

        private List<Asset> dependents = null;
        private List<Connection> destConnections = null;
        private TrafficManagerAdapter adapter = null;

        public EndpointOrderComparator(List<Asset> dependents, List<Connection> destConnections, TrafficManagerAdapter adapter)
        {
            this.dependents = dependents;
            this.destConnections = destConnections;
            this.adapter = adapter;
        }

        @Override
        public int compare(Endpoint o1, Endpoint o2)
        {
            if (o1 == null && o2 == null) {
                return 0;
            }
            else if (o1 == null) {
                return 1;
            }
            else if (o2 == null) {
                return -1;
            }

            Connection l1 = adapter.findDependentConnection(o1, dependents, destConnections);
            Connection l2 = adapter.findDependentConnection(o2, dependents, destConnections);
            if (l1 == null && l2 == null) {
                return 0;
            }
            else if (l1 == null) {
                return 1;
            }
            else if (l2 == null) {
                return -1;
            }
            else {
                int order1 =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                                TrafficManagerConfig.CONFIG_ORDER, l1.getAssetProperties(), 0);
                int order2 =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                                TrafficManagerConfig.CONFIG_ORDER, l2.getAssetProperties(), 0);
                if (order1 == 0 && order2 == 0) {
                    return 0;
                }
                else if (order1 == 0) {
                    return 1;
                }
                else if (order2 == 0) {
                    return -1;
                }
                return Integer.compare(order1, order2);
            }
        }
    }

    public enum ServiceCategory
    {
        // Service Instance is an Azure service instance running 
        AZURE,

        // Service Instance cannot be identified as an Azure service
        NON_AZURE
    }

    public ServiceCategory getCategory(ServiceInstance serviceInstance, List<ServiceProvider> providers)
    {
        ServiceCategory category = ServiceCategory.NON_AZURE;
        ServiceProvider endpointProvider = null;

        Link providerType = serviceInstance.getProvider();
        for (ServiceProvider provider : providers) {
            if (providerType.getId() == provider.getId()) {
                endpointProvider = provider;
                break;
            }
        }
        if (endpointProvider != null && TrafficManagerAdapter.SERVICE_PROVIDER_NAME.equals(endpointProvider.getType().getName())) {
            category = ServiceCategory.AZURE;
        }
        return category;
    }

    public enum InstanceCategory
    {
        // Instance is an Azure instance running within the same subscription
        // as the Traffic Manager
        SHARED_SUBSCRIPTION,

        // Instance is an Azure instance running in a different subscription
        // from the Traffic Manager
        ALTERNATE_SUBSCRIPTION,

        // Instance cannot be identified as an Azure instance
        NON_AZURE
    }

    public InstanceCategory getCategory(String subscription, String domainName, Link endpointCloudLink, List<Cloud> clouds)
    {
        InstanceCategory category = InstanceCategory.NON_AZURE;
        Cloud endpointCloud = null;

        for (Cloud cloud : clouds) {
            if (endpointCloudLink.getId() == cloud.getId()) {
                endpointCloud = cloud;
                break;
            }
        }
        if (endpointCloud != null) {
            boolean isAzure = domainName != null ? domainName.endsWith(".cloudapp.net") : false;

            if (!isAzure) {
                // The VM is not using the default Azure domain, but perhaps
                // the customer has defined their own domain.
                Link cloudType = endpointCloud.getCloudType();
                if (cloudType != null && "Azure".equals(cloudType.getName())) {
                    isAzure = true;
                }
            }
            if (isAzure) {
                if (subscription.equals(endpointCloud.getSubscription())) {
                    category = InstanceCategory.SHARED_SUBSCRIPTION;
                }
                else {
                    category = InstanceCategory.ALTERNATE_SUBSCRIPTION;
                }
            }
        }
        return category;
    }

    public String getLocation(InstanceCategory category, Connection conn, Instance instance)
    {
        if (category == InstanceCategory.ALTERNATE_SUBSCRIPTION) {
            String location =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_LOCATION, conn.getAssetProperties(), null);
            if (location != null && !location.isEmpty()) {
                return location;
            }
            else {
                Link locationLink = instance.getLocation();
                if (locationLink != null) {
                    String name = locationLink.getName();
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        }
        return null;
    }

    public void validateLocation(AzureConnection connection, ServiceInstance service, Asset sourceEndpoint, Connection conn,
            List<Cloud> clouds, List<ServiceProvider> providers) throws Exception
    {
        // location is required for traffic manager or any type endpoints for performance load balancing
        LoadBalancingMethod lbm =
                LoadBalancingMethod.fromValue(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_LBM_NAME, service.getAssetProperties()));
        if (lbm == LoadBalancingMethod.PERFORMANCE) {
            if (sourceEndpoint instanceof ServiceInstance) {
                // another service endpoint
                ServiceInstance sourceService = (ServiceInstance) sourceEndpoint;
                Type type = getEndpointType(sourceService);
                if (type == Type.ANY || type == Type.TRAFFIC_MANAGER) {
                    // non Azure service cannot be be used for Performance load balancing method
                    if (getCategory(sourceService, providers) == TrafficManagerAdapter.ServiceCategory.NON_AZURE) {
                        throw new Exception("Endpoint " + sourceService.getName()
                                + " not valid. Performance load balancing method can only be used with Azure services. ");
                    }
                    String location = null;
                    if (conn != null) {
                        location =
                                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                        TrafficManagerConfig.CONFIG_LOCATION, conn.getAssetProperties(), null);
                    }
                    if (location == null || location.isEmpty()) {
                        throw new Exception(
                                "Endpoint "
                                        + sourceService.getName()
                                        + " not valid. Location is required for service endpoints when using Performance load balancing method. ");
                    }
                }
            }
            else if (sourceEndpoint instanceof Template) {
                // template endpoint
                Template sourceTemplate = (Template) sourceEndpoint;
                InstanceCategory category =
                        getCategory(connection.getEndpoint().getSubscription(), null, sourceTemplate.getCloud(), clouds);
                // this is Any endpoint and requires a location
                // non Azure instance cannot be be used for Performance load balancing method
                if (category == TrafficManagerAdapter.InstanceCategory.NON_AZURE) {
                    throw new Exception("Endpoint " + sourceTemplate.getName()
                            + " not valid. Performance load balancing method can only be used with Azure instances. ");
                }
                // check Azure instance from another subscripion
                if (category == InstanceCategory.ALTERNATE_SUBSCRIPTION) {
                    String location = null;
                    if (conn != null) {
                        location =
                                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                        TrafficManagerConfig.CONFIG_LOCATION, conn.getAssetProperties(), null);
                    }
                    if (location == null || location.isEmpty()) {
                        // check instance location
                        location = sourceTemplate.getLocation();
                    }
                    if (location == null || location.isEmpty()) {
                        throw new Exception(
                                "Endpoint "
                                        + sourceTemplate.getName()
                                        + " not valid. Location is required for outside of subscription instance endpoints when using Performance load balancing method. ");
                    }
                }
            }
        }
    }

    public Promise<ServiceProviderResponse> resetProfileStatus(final String profileName, final AzureConnection connection,
            final com.microsoft.schemas.azure.trafficmgr.Status status, final ServiceProviderResponse delegateResponse)
    {
        final Profile profile = new Profile();
        profile.setStatus(status);
        StatusDetails details = new StatusDetails();
        details.setEnabledVersion(1);
        profile.setStatusDetails(details);
        final String uri =
                com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.Constants.TRAFFIC_MGR_BASE_URI
                        + "/profiles/" + profileName;
        Promise<IHttpResponse> promise = connection.put(uri, profile, IHttpResponse.class);
        return promise.map(new Function<IHttpResponse, ServiceProviderResponse>() {
            public ServiceProviderResponse invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() == 200) {
                    return delegateResponse;
                }
                else {
                    String message = "Not able to reset profile status. " + ConnectionUtil.getStatusInfo(connection, arg);
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.FAILURE);
                    response.setMessage(message);
                    return response;
                }
            }
        });
    }

    public void updateInstanceAdded(Instance instance, ServiceInstance destService)
    {
        String lbDomainName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME,
                        destService.getAssetProperties());
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, destService.getAssetProperties());
        addOrUpdateProperty(TrafficManagerConfig.ENDPOINT_PROP_LB_NAME, lbDomainName, instance.getProperties());
        addOrUpdateProperty(TrafficManagerConfig.ENDPOINT_PROP_PROFILE_NAME, profileName, instance.getProperties());
    }

    public void updateInstanceRemoved(Instance instance, ServiceInstance destService)
    {
        addOrUpdateProperty(TrafficManagerConfig.ENDPOINT_PROP_LB_NAME, "", instance.getProperties());
        addOrUpdateProperty(TrafficManagerConfig.ENDPOINT_PROP_PROFILE_NAME, "", instance.getProperties());
    }

    public void updateServiceInstanceAdded(ServiceInstance instance, ServiceInstance destService)
    {
        String lbDomainName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME,
                        destService.getAssetProperties());
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, destService.getAssetProperties());
        addOrUpdateAssetProperty(TrafficManagerConfig.ENDPOINT_PROP_LB_NAME, lbDomainName, instance.getConfigurations());
        addOrUpdateAssetProperty(TrafficManagerConfig.ENDPOINT_PROP_PROFILE_NAME, profileName, instance.getConfigurations());
    }

    public void updateServiceInstanceRemoved(ServiceInstance instance, ServiceInstance destService)
    {
        removeAssetProperty(TrafficManagerConfig.ENDPOINT_PROP_LB_NAME, instance.getConfigurations());
        removeAssetProperty(TrafficManagerConfig.ENDPOINT_PROP_PROFILE_NAME, instance.getConfigurations());
    }

    public Asset lookupAsset(List<Asset> assets, int id, Class type)
    {
        for (Asset asset : assets) {
            if (asset.getId() == id && asset.getClass() == type) {
                return asset;
            }
        }
        return null;
    }
}
