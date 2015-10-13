/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.service.azure.sql.connection.AzureSQLConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.sql.operations.AzureSQLInstanceOperations;
import com.servicemesh.agility.adapters.service.azure.sql.operations.AzureSQLProviderOperations;
import com.servicemesh.agility.adapters.service.azure.sql.operations.AzureSQLServiceLifecycleOperations;
import com.servicemesh.agility.adapters.service.azure.sql.operations.AzureSQLServiceOperations;
import com.servicemesh.agility.api.AssetType;
import com.servicemesh.agility.api.ConnectionDefinition;
import com.servicemesh.agility.api.FieldValidators;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.PrimitiveType;
import com.servicemesh.agility.api.PropertyDefinition;
import com.servicemesh.agility.api.PropertyType;
import com.servicemesh.agility.api.PropertyTypeValue;
import com.servicemesh.agility.api.RegexValidator;
import com.servicemesh.agility.api.Service;
import com.servicemesh.agility.api.ServiceProviderOption;
import com.servicemesh.agility.api.ServiceProviderType;
import com.servicemesh.agility.api.ValueConstraintType;
import com.servicemesh.agility.api.Workload;
import com.servicemesh.agility.sdk.service.msgs.RegistrationRequest;
import com.servicemesh.agility.sdk.service.msgs.RegistrationResponse;
import com.servicemesh.agility.sdk.service.spi.IInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceInstance;
import com.servicemesh.agility.sdk.service.spi.IServiceInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceProvider;
import com.servicemesh.agility.sdk.service.spi.ServiceAdapter;
import com.servicemesh.core.reactor.TimerReactor;

public class AzureSQLAdapter extends ServiceAdapter
{

    private static final Logger logger = Logger.getLogger(AzureSQLAdapter.class);
    public static final String SERVICE_PROVIDER_NAME = "Azure SQL Provider";
    public static final String SERVICE_PROVIDER_DESCRIPTION;
    public static final String SERVICE_PROVIDER_TYPE = "azure-sql-provider";
    public static final String SERVICE_PROVIDER_VERSION;

    public static final String SERVICE_TYPE = "azure-sql";
    public static final String SERVICE_NAME = "Azure SQL";
    public static final String SERVICE_DESCRIPTION;

    static {
        String PROP_FILE = "/resources/AzureSQLAdapter.properties";
        Properties props = new Properties();
        try {
            InputStream rs = AzureSQLAdapter.class.getResourceAsStream(PROP_FILE);
            if (rs != null) {
                props.load(rs);
            }
            else {
                AzureSQLAdapter.logger.error("Resource not found " + PROP_FILE);
            }
        }
        catch (Exception ex) {
            AzureSQLAdapter.logger.error("Failed to load " + PROP_FILE + ": " + ex);
        }
        SERVICE_PROVIDER_VERSION = props.getProperty("adapter.version", "0.0.0");
        String revision = props.getProperty("adapter.revision", "");
        String vendor = props.getProperty("adapter.vendor", "");

        StringBuilder sb = new StringBuilder();
        sb.append(" (").append(AzureSQLAdapter.SERVICE_PROVIDER_VERSION);
        if (!revision.isEmpty()) {
            sb.append(" ").append(revision);
        }
        if (!vendor.isEmpty()) {
            sb.append(" ").append(vendor);
        }
        sb.append(")");
        SERVICE_PROVIDER_DESCRIPTION = AzureSQLAdapter.SERVICE_PROVIDER_NAME + sb.toString();
        SERVICE_DESCRIPTION = AzureSQLAdapter.SERVICE_NAME + sb.toString();
    }

    private final AzureSQLConnectionFactory _factory = new AzureSQLConnectionFactory();

    public AzureSQLAdapter() throws Exception
    {
        super(TimerReactor.getTimerReactor(AzureSQLAdapter.SERVICE_PROVIDER_NAME));
        AzureSQLAdapter.logger.info(AzureSQLAdapter.SERVICE_PROVIDER_DESCRIPTION);
    }

    @Override
    public List<ServiceProviderType> getServiceProviderTypes()
    {
        List<ServiceProviderType> serviceProviderTypes = new ArrayList<ServiceProviderType>();
        ServiceProviderType serviceProviderType = new ServiceProviderType();
        serviceProviderType.setName(AzureSQLAdapter.SERVICE_PROVIDER_NAME);
        serviceProviderType.setDescription(AzureSQLAdapter.SERVICE_PROVIDER_DESCRIPTION);
        Link serviceType = new Link();
        serviceType.setName(AzureSQLAdapter.SERVICE_TYPE);
        serviceProviderType.getServiceTypes().add(serviceType);
        Link assetType = new Link();
        assetType.setName(AzureSQLAdapter.SERVICE_PROVIDER_TYPE);
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
        return new AzureSQLInstanceOperations();
    }

    @Override
    public IServiceProvider getServiceProviderOperations()
    {
        return new AzureSQLProviderOperations(this, _factory);
    }

    @Override
    public IServiceInstance getServiceInstanceOperations()
    {
        return new AzureSQLServiceOperations(this, _factory);
    }

    @Override
    public IServiceInstanceLifecycle getServiceInstanceLifecycleOperations()
    {
        return new AzureSQLServiceLifecycleOperations();
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

        // common types
        Link string_type = new Link();
        string_type.setName("string-any");
        string_type.setType("application/" + PropertyType.class.getName() + "+xml");

        Link encrypted_type = new Link();
        encrypted_type.setName("encrypted");
        encrypted_type.setType("application/" + PropertyType.class.getName() + "+xml");

        Link integer_type = new Link();
        integer_type.setName("integer-any");
        integer_type.setType("application/" + PropertyType.class.getName() + "+xml");

        Link byte_type = new Link();
        byte_type.setName("binary");
        byte_type.setType("application/" + PropertyType.class.getName() + "+xml");

        // property definition for server location
        PropertyTypeValue eastAsia = new PropertyTypeValue();
        eastAsia.setName("East Asia");
        eastAsia.setDisplayName(eastAsia.getName());
        eastAsia.setValue(AzureSQLConfig.CONFIG_LOCATION_EAST_ASIA);

        PropertyTypeValue southeastAsia = new PropertyTypeValue();
        southeastAsia.setName("Southeast Asia");
        southeastAsia.setDisplayName(southeastAsia.getName());
        southeastAsia.setValue(AzureSQLConfig.CONFIG_LOCATION_SOUTHEAST_ASIA);

        PropertyTypeValue japanWest = new PropertyTypeValue();
        japanWest.setName("Japan West");
        japanWest.setDisplayName(japanWest.getName());
        japanWest.setValue(AzureSQLConfig.CONFIG_LOCATION_JAPAN_WEST);

        PropertyTypeValue japanEast = new PropertyTypeValue();
        japanEast.setName("Japan East");
        japanEast.setDisplayName(japanEast.getName());
        japanEast.setValue(AzureSQLConfig.CONFIG_LOCATION_JAPAN_EAST);

        PropertyTypeValue centralUS = new PropertyTypeValue();
        centralUS.setName("Central US");
        centralUS.setDisplayName(centralUS.getName());
        centralUS.setValue(AzureSQLConfig.CONFIG_LOCATION_CENTRAL_US);

        PropertyTypeValue northCentralUS = new PropertyTypeValue();
        northCentralUS.setName("North Central US");
        northCentralUS.setDisplayName(northCentralUS.getName());
        northCentralUS.setValue(AzureSQLConfig.CONFIG_LOCATION_NORTH_CENTRAL_US);

        PropertyTypeValue southCentralUS = new PropertyTypeValue();
        southCentralUS.setName("South Central US");
        southCentralUS.setDisplayName(southCentralUS.getName());
        southCentralUS.setValue(AzureSQLConfig.CONFIG_LOCATION_SOUTH_CENTRAL_US);

        PropertyTypeValue westUS = new PropertyTypeValue();
        westUS.setName("West US");
        westUS.setDisplayName(westUS.getName());
        westUS.setValue(AzureSQLConfig.CONFIG_LOCATION_WEST_US);

        PropertyTypeValue eastUS = new PropertyTypeValue();
        eastUS.setName("East US");
        eastUS.setDisplayName(eastUS.getName());
        eastUS.setValue(AzureSQLConfig.CONFIG_LOCATION_EAST_US);

        PropertyTypeValue eastUS2 = new PropertyTypeValue();
        eastUS2.setName("East US 2");
        eastUS2.setDisplayName(eastUS2.getName());
        eastUS2.setValue(AzureSQLConfig.CONFIG_LOCATION_EAST_US_2);

        PropertyTypeValue northEurope = new PropertyTypeValue();
        northEurope.setName("North Europe");
        northEurope.setDisplayName(northEurope.getName());
        northEurope.setValue(AzureSQLConfig.CONFIG_LOCATION_NORTH_EUROPE);

        PropertyTypeValue westEurope = new PropertyTypeValue();
        westEurope.setName("West Europe");
        westEurope.setDisplayName(westEurope.getName());
        westEurope.setValue(AzureSQLConfig.CONFIG_LOCATION_WEST_EUROPE);

        PropertyTypeValue brazilSouth = new PropertyTypeValue();
        brazilSouth.setName("Brazil South");
        brazilSouth.setDisplayName(brazilSouth.getName());
        brazilSouth.setValue(AzureSQLConfig.CONFIG_LOCATION_BRAZIL_SOUTH);

        PropertyType locationType = new PropertyType();
        locationType.setName(AzureSQLConfig.CONFIG_AZURE_SQL_SERVER_LOCATION);
        locationType.setType(PrimitiveType.STRING);
        locationType.setDisplayName("Azure SQL Server Location");
        locationType.setValueConstraint(ValueConstraintType.LIST);
        locationType.getRootValues().add(eastAsia);
        locationType.getRootValues().add(southeastAsia);
        locationType.getRootValues().add(japanWest);
        locationType.getRootValues().add(japanEast);
        locationType.getRootValues().add(centralUS);
        locationType.getRootValues().add(northCentralUS);
        locationType.getRootValues().add(southCentralUS);
        locationType.getRootValues().add(westUS);
        locationType.getRootValues().add(eastUS);
        locationType.getRootValues().add(eastUS2);
        locationType.getRootValues().add(northEurope);
        locationType.getRootValues().add(westEurope);
        locationType.getRootValues().add(brazilSouth);

        Link locationTypeLink = new Link();
        locationTypeLink.setName(AzureSQLConfig.CONFIG_AZURE_SQL_SERVER_LOCATION);
        locationTypeLink.setType("application/" + PropertyType.class.getName() + "+xml");

        PropertyDefinition locationDef = new PropertyDefinition();
        locationDef.setName(AzureSQLConfig.CONFIG_AZURE_SQL_SERVER_LOCATION);
        locationDef.setDisplayName("Location");
        locationDef.setReadable(true);
        locationDef.setWritable(true);
        locationDef.setMinRequired(1);
        locationDef.setMaxAllowed(1);
        locationDef.setPropertyType(locationTypeLink);
        locationDef.setPropertyTypeValue(locationType);

        // define Firewall Rule Name property
        PropertyDefinition firewallRuleName = new PropertyDefinition();
        firewallRuleName.setName(AzureSQLConfig.CONFIG_FIREWALL_RULE_NAME);
        firewallRuleName.setDisplayName("Firewall Rule Name");
        firewallRuleName.setReadable(true);
        firewallRuleName.setWritable(true);
        firewallRuleName.setMinRequired(0);
        firewallRuleName.setMaxAllowed(1);
        firewallRuleName.setPropertyType(string_type);

        // define starting allowed IP property
        PropertyDefinition startingAllowedIP = new PropertyDefinition();
        startingAllowedIP.setName(AzureSQLConfig.CONFIG_STARTING_ALLOWED_IP);
        startingAllowedIP.setDisplayName("Starting Allowed IP");
        startingAllowedIP.setReadable(true);
        startingAllowedIP.setWritable(true);
        startingAllowedIP.setMinRequired(0);
        startingAllowedIP.setMaxAllowed(1);
        startingAllowedIP.setPropertyType(string_type);
        RegexValidator validatorIP = new RegexValidator();
        validatorIP.getExpressions().add("^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");
        startingAllowedIP.setValidator(new FieldValidators());
        startingAllowedIP.getValidator().getValidators().add(validatorIP);

        // define ending allowed IP property
        PropertyDefinition endingAllowedIP = new PropertyDefinition();
        endingAllowedIP.setName(AzureSQLConfig.CONFIG_ENDING_ALLOWED_IP);
        endingAllowedIP.setDisplayName("Ending Allowed IP");
        endingAllowedIP.setReadable(true);
        endingAllowedIP.setWritable(true);
        endingAllowedIP.setMinRequired(0);
        endingAllowedIP.setMaxAllowed(1);
        endingAllowedIP.setPropertyType(string_type);
        endingAllowedIP.setValidator(new FieldValidators());
        endingAllowedIP.getValidator().getValidators().add(validatorIP);

        //define database name property
        PropertyDefinition dbName = new PropertyDefinition();
        dbName.setName(AzureSQLConfig.CONFIG_DB_NAME);
        dbName.setDisplayName("Database Name");
        dbName.setReadable(true);
        dbName.setWritable(true);
        dbName.setMinRequired(0);
        dbName.setMaxAllowed(1);
        dbName.setPropertyType(string_type);

        //define edition property
        PropertyTypeValue basic = new PropertyTypeValue();
        basic.setName(AzureSQLConfig.CONFIG_EDITION_BASIC);
        basic.setDisplayName(basic.getName());
        basic.setValue(AzureSQLConfig.CONFIG_EDITION_BASIC);

        PropertyTypeValue standard = new PropertyTypeValue();
        standard.setName(AzureSQLConfig.CONFIG_EDITION_STANDARD);
        standard.setDisplayName(standard.getName());
        standard.setValue(AzureSQLConfig.CONFIG_EDITION_STANDARD);

        PropertyTypeValue premium = new PropertyTypeValue();
        premium.setName(AzureSQLConfig.CONFIG_EDITION_PREMIUM);
        premium.setDisplayName(premium.getName());
        premium.setValue(AzureSQLConfig.CONFIG_EDITION_PREMIUM);

        PropertyType editionType = new PropertyType();
        editionType.setName(AzureSQLConfig.CONFIG_EDITION);
        editionType.setDisplayName("Edition");
        editionType.setType(PrimitiveType.STRING);
        editionType.setValueConstraint(ValueConstraintType.LIST);
        editionType.getRootValues().add(basic);
        editionType.getRootValues().add(standard);
        editionType.getRootValues().add(premium);

        Link editionTypeLink = new Link();
        editionTypeLink.setName(AzureSQLConfig.CONFIG_EDITION);
        editionTypeLink.setType("application/" + PropertyType.class.getName() + "+xml");

        PropertyDefinition edition = new PropertyDefinition();
        edition.setName(AzureSQLConfig.CONFIG_EDITION);
        edition.setDisplayName("Edition");
        edition.setReadable(true);
        edition.setWritable(true);
        edition.setMinRequired(0);
        edition.setMaxAllowed(1);
        edition.setPropertyType(editionTypeLink);
        edition.setPropertyTypeValue(editionType);

        // define collation property
        PropertyDefinition collation = new PropertyDefinition();
        collation.setName(AzureSQLConfig.CONFIG_COLLATION);
        collation.setDisplayName("Collation");
        collation.setReadable(true);
        collation.setWritable(true);
        collation.setMinRequired(0);
        collation.setMaxAllowed(1);
        collation.setPropertyType(string_type);

        //define max size property
        //values came from https://msdn.microsoft.com/en-us/library/azure/dn369872.aspx
        PropertyTypeValue _100MB = new PropertyTypeValue();
        _100MB.setName(AzureSQLConfig.CONFIG_100_MB);
        _100MB.setDisplayName(_100MB.getName());
        _100MB.setValue(AzureSQLConfig.CONFIG_100_MB);

        PropertyTypeValue _500MB = new PropertyTypeValue();
        _500MB.setName(AzureSQLConfig.CONFIG_500_MB);
        _500MB.setDisplayName(_500MB.getName());
        _500MB.setValue(AzureSQLConfig.CONFIG_500_MB);

        PropertyTypeValue _1GB = new PropertyTypeValue();
        _1GB.setName(AzureSQLConfig.CONFIG_1_GB);
        _1GB.setDisplayName(_1GB.getName());
        _1GB.setValue(AzureSQLConfig.CONFIG_1_GB);

        PropertyTypeValue _2GB = new PropertyTypeValue();
        _2GB.setName(AzureSQLConfig.CONFIG_2_GB);
        _2GB.setDisplayName(_2GB.getName());
        _2GB.setValue(AzureSQLConfig.CONFIG_2_GB);

        PropertyTypeValue _5GB = new PropertyTypeValue();
        _5GB.setName(AzureSQLConfig.CONFIG_5_GB);
        _5GB.setDisplayName(_5GB.getName());
        _5GB.setValue(AzureSQLConfig.CONFIG_5_GB);

        PropertyTypeValue _10GB = new PropertyTypeValue();
        _10GB.setName(AzureSQLConfig.CONFIG_10_GB);
        _10GB.setDisplayName(_10GB.getName());
        _10GB.setValue(AzureSQLConfig.CONFIG_10_GB);

        PropertyTypeValue _20GB = new PropertyTypeValue();
        _20GB.setName(AzureSQLConfig.CONFIG_20_GB);
        _20GB.setDisplayName(_20GB.getName());
        _20GB.setValue(AzureSQLConfig.CONFIG_20_GB);

        PropertyTypeValue _30GB = new PropertyTypeValue();
        _30GB.setName(AzureSQLConfig.CONFIG_30_GB);
        _30GB.setDisplayName(_30GB.getName());
        _30GB.setValue(AzureSQLConfig.CONFIG_30_GB);

        PropertyTypeValue _40GB = new PropertyTypeValue();
        _40GB.setName(AzureSQLConfig.CONFIG_40_GB);
        _40GB.setDisplayName(_40GB.getName());
        _40GB.setValue(AzureSQLConfig.CONFIG_40_GB);

        PropertyTypeValue _50GB = new PropertyTypeValue();
        _50GB.setName(AzureSQLConfig.CONFIG_50_GB);
        _50GB.setDisplayName(_50GB.getName());
        _50GB.setValue(AzureSQLConfig.CONFIG_50_GB);

        PropertyTypeValue _100GB = new PropertyTypeValue();
        _100GB.setName(AzureSQLConfig.CONFIG_100_GB);
        _100GB.setDisplayName(_100GB.getName());
        _100GB.setValue(AzureSQLConfig.CONFIG_100_GB);

        PropertyTypeValue _150GB = new PropertyTypeValue();
        _150GB.setName(AzureSQLConfig.CONFIG_150_GB);
        _150GB.setDisplayName(_150GB.getName());
        _150GB.setValue(AzureSQLConfig.CONFIG_150_GB);

        PropertyTypeValue _200GB = new PropertyTypeValue();
        _200GB.setName(AzureSQLConfig.CONFIG_200_GB);
        _200GB.setDisplayName(_200GB.getName());
        _200GB.setValue(AzureSQLConfig.CONFIG_200_GB);

        PropertyTypeValue _250GB = new PropertyTypeValue();
        _250GB.setName(AzureSQLConfig.CONFIG_250_GB);
        _250GB.setDisplayName(_250GB.getName());
        _250GB.setValue(AzureSQLConfig.CONFIG_250_GB);

        PropertyTypeValue _300GB = new PropertyTypeValue();
        _300GB.setName(AzureSQLConfig.CONFIG_300_GB);
        _300GB.setDisplayName(_300GB.getName());
        _300GB.setValue(AzureSQLConfig.CONFIG_300_GB);

        PropertyTypeValue _400GB = new PropertyTypeValue();
        _400GB.setName(AzureSQLConfig.CONFIG_400_GB);
        _400GB.setDisplayName(_400GB.getName());
        _400GB.setValue(AzureSQLConfig.CONFIG_400_GB);

        PropertyTypeValue _500GB = new PropertyTypeValue();
        _500GB.setName(AzureSQLConfig.CONFIG_500_GB);
        _500GB.setDisplayName(_500GB.getName());
        _500GB.setValue(AzureSQLConfig.CONFIG_500_GB);

        PropertyType maxSizeType = new PropertyType();
        maxSizeType.setName(AzureSQLConfig.CONFIG_MAX_SIZE);
        maxSizeType.setDisplayName("Max Size");
        maxSizeType.setType(PrimitiveType.STRING);
        maxSizeType.setValueConstraint(ValueConstraintType.LIST);
        maxSizeType.getRootValues().add(_100MB);
        maxSizeType.getRootValues().add(_500MB);
        maxSizeType.getRootValues().add(_1GB);
        maxSizeType.getRootValues().add(_2GB);
        maxSizeType.getRootValues().add(_5GB);
        maxSizeType.getRootValues().add(_10GB);
        maxSizeType.getRootValues().add(_20GB);
        maxSizeType.getRootValues().add(_30GB);
        maxSizeType.getRootValues().add(_40GB);
        maxSizeType.getRootValues().add(_50GB);
        maxSizeType.getRootValues().add(_100GB);
        maxSizeType.getRootValues().add(_150GB);
        maxSizeType.getRootValues().add(_200GB);
        maxSizeType.getRootValues().add(_250GB);
        maxSizeType.getRootValues().add(_300GB);
        maxSizeType.getRootValues().add(_400GB);
        maxSizeType.getRootValues().add(_500GB);

        Link maxSizeTypeLink = new Link();
        maxSizeTypeLink.setName(AzureSQLConfig.CONFIG_MAX_SIZE);
        maxSizeTypeLink.setType("application/" + PropertyType.class.getName() + "+xml");

        PropertyDefinition maxSize = new PropertyDefinition();
        maxSize.setName(AzureSQLConfig.CONFIG_MAX_SIZE);
        maxSize.setDisplayName("Max Size");
        maxSize.setReadable(true);
        maxSize.setWritable(true);
        maxSize.setMinRequired(0);
        maxSize.setMaxAllowed(1);
        maxSize.setPropertyType(maxSizeTypeLink);
        maxSize.setPropertyTypeValue(maxSizeType);

        //define service objective id property
        PropertyDefinition serviceObjectiveId = new PropertyDefinition();
        serviceObjectiveId.setName(AzureSQLConfig.CONFIG_SERVICE_OBJECTIVE_ID);
        serviceObjectiveId.setDisplayName("Service Objective Id");
        serviceObjectiveId.setReadable(true);
        serviceObjectiveId.setWritable(true);
        serviceObjectiveId.setMinRequired(0);
        serviceObjectiveId.setMaxAllowed(1);
        serviceObjectiveId.setPropertyType(string_type);

        // setup allowed connections
        Link workload_link = new Link();
        workload_link.setName("designworkload");
        workload_link.setType("application/" + Workload.class.getName() + "+xml");

        Link azureSQL_link = new Link();
        azureSQL_link.setName(AzureSQLAdapter.SERVICE_TYPE);
        azureSQL_link.setType("application/" + Service.class.getName() + "+xml");

        Link service_link = new Link();
        service_link.setName("service");
        service_link.setType("application/" + Service.class.getName() + "+xml");

        Link connection_link = new Link();
        connection_link.setName("designconnection");
        connection_link.setType("application/" + Service.class.getName() + "+xml");

        ConnectionDefinition workload_to_azuresql = new ConnectionDefinition();
        workload_to_azuresql.setName(AzureSQLConfig.CONFIG_WORKLOAD_CONN);
        workload_to_azuresql.setDisplayName("Dependency on Azure SQL");
        workload_to_azuresql.setSourceType(workload_link);
        workload_to_azuresql.setDestinationType(azureSQL_link);
        workload_to_azuresql.setConnectionType(connection_link);

        ConnectionDefinition service_to_azuresql = new ConnectionDefinition();
        service_to_azuresql.setName(AzureSQLConfig.CONFIG_SERVICE_SQL_CONN);
        service_to_azuresql.setDisplayName("Dependency on Azure SQL");
        service_to_azuresql.setSourceType(service_link);
        service_to_azuresql.setDestinationType(azureSQL_link);
        service_to_azuresql.setConnectionType(connection_link);

        ConnectionDefinition azuresql_to_service = new ConnectionDefinition();
        azuresql_to_service.setName(AzureSQLConfig.CONFIG_SQL_SERVICE_CONN);
        azuresql_to_service.setDisplayName("Dependency on a Service");
        azuresql_to_service.setSourceType(azureSQL_link);
        azuresql_to_service.setDestinationType(service_link);
        azuresql_to_service.setConnectionType(connection_link);

        // define Azure SQL Service
        Link serviceType = new Link();
        serviceType.setName("rdbms");
        serviceType.setType("application/" + Service.class.getName() + "+xml");

        AssetType azureSql = new AssetType();
        azureSql.setName(AzureSQLAdapter.SERVICE_TYPE);
        azureSql.setDisplayName(AzureSQLAdapter.SERVICE_NAME);
        azureSql.setDescription(AzureSQLAdapter.SERVICE_DESCRIPTION);
        azureSql.getPropertyDefinitions().add(locationDef);
        azureSql.getPropertyDefinitions().add(firewallRuleName);
        azureSql.getPropertyDefinitions().add(startingAllowedIP);
        azureSql.getPropertyDefinitions().add(endingAllowedIP);
        azureSql.getPropertyDefinitions().add(dbName);
        azureSql.getPropertyDefinitions().add(edition);
        azureSql.getPropertyDefinitions().add(collation);
        azureSql.getPropertyDefinitions().add(maxSize);
        azureSql.getPropertyDefinitions().add(serviceObjectiveId);
        azureSql.getDestConnections().add(workload_to_azuresql);
        azureSql.getDestConnections().add(service_to_azuresql);
        azureSql.getSrcConnections().add(azuresql_to_service);
        azureSql.setSuperType(serviceType);
        registration.getAssetTypes().add(azureSql);

        // define Azure SQL provider subscription
        PropertyDefinition subscriptionPD = new PropertyDefinition();
        subscriptionPD.setName(AzureSQLConfig.CONFIG_SUBSCRIPTION);
        subscriptionPD.setDescription("Azure SQL provider subscription.");
        subscriptionPD.setDisplayName("Subscription");
        subscriptionPD.setReadable(true);
        subscriptionPD.setWritable(true);
        subscriptionPD.setMaxAllowed(1);
        subscriptionPD.setPropertyType(string_type);

        // define Azure SQL provider certificate
        PropertyDefinition certificatePD = new PropertyDefinition();
        certificatePD.setName(AzureSQLConfig.CONFIG_CERTIFICATE);
        certificatePD.setDescription("Azure SQL provider certificate.");
        certificatePD.setDisplayName("Certificate");
        certificatePD.setReadable(true);
        certificatePD.setWritable(true);
        certificatePD.setMaxAllowed(1);
        certificatePD.setPropertyType(byte_type);

        // define Azure SQL provider certificate private key
        PropertyDefinition privatePD = new PropertyDefinition();
        privatePD.setName(AzureSQLConfig.CONFIG_PRIVATE_KEY);
        privatePD.setDescription("Azure SQL provider certificate private key.");
        privatePD.setDisplayName("Private Key");
        privatePD.setReadable(true);
        privatePD.setWritable(true);
        privatePD.setMaxAllowed(1);
        privatePD.setPropertyType(encrypted_type);

        // define Azure SQL Provider
        Link serviceProviderType = new Link();
        serviceProviderType.setName("serviceprovidertype");

        AssetType azureSQLProvider = new AssetType();
        azureSQLProvider.setName(AzureSQLAdapter.SERVICE_PROVIDER_TYPE);
        azureSQLProvider.setDescription(AzureSQLAdapter.SERVICE_PROVIDER_DESCRIPTION);
        azureSQLProvider.setDisplayName(AzureSQLAdapter.SERVICE_PROVIDER_NAME);
        azureSQLProvider.setSuperType(serviceProviderType);
        azureSQLProvider.getPropertyDefinitions().add(subscriptionPD);
        azureSQLProvider.getPropertyDefinitions().add(certificatePD);
        azureSQLProvider.getPropertyDefinitions().add(privatePD);
        registration.getAssetTypes().add(azureSQLProvider);

        registration.setName(AzureSQLAdapter.SERVICE_PROVIDER_NAME);
        registration.setVersion(AzureSQLAdapter.SERVICE_PROVIDER_VERSION);
        registration.getServiceProviderTypes().addAll(getServiceProviderTypes());

        return registration;
    }

}
