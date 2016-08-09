package services.training.lab8;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.servicemesh.agility.api.AssetType;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.ConnectionDefinition;
import com.servicemesh.agility.api.Editor;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.PropertyDefinition;
import com.servicemesh.agility.api.PropertyType;
import com.servicemesh.agility.api.Service;
import com.servicemesh.agility.api.ServiceProviderOption;
import com.servicemesh.agility.api.ServiceProviderType;
import com.servicemesh.agility.sdk.service.msgs.RegistrationRequest;
import com.servicemesh.agility.sdk.service.msgs.RegistrationResponse;
import com.servicemesh.agility.sdk.service.spi.IInstanceLifecycle;
import com.servicemesh.agility.sdk.service.spi.IServiceInstance;
import com.servicemesh.agility.sdk.service.spi.IServiceProvider;
import com.servicemesh.agility.sdk.service.spi.ServiceAdapter;
import com.servicemesh.core.reactor.TimerReactor;

/**
 * TODO If you are using eclipse to edit or build this project make sure you
 * install the IvyDE plugin.
 * Apache Ivy update site - http://www.apache.org/dist/ant/ivyde/updatesite
 *
 */


public class TrainingServiceAdapter extends ServiceAdapter {
	private static final Logger logger = Logger.getLogger(TrainingServiceAdapter.class);

	// 1. Change "my" to your name or initials. *_TYPE aka "database name"
	//	  while *_NAME aka "UI name"
	// 2. Use *_TYPE (aka the "database
	//	  name" for: a) Link setName(); b) AssetType setName()
	// 3. Use SERVICE_PROVIDER_NAME with: a) TimerReactor getTimerReactor();
	//	  b) RegistrationRequest setName(); c) ServiceProviderType setName();
	//	  d) provider AssetType setDisplayName()
	// 4. Use SERVICE_NAME for service AssetType setDisplayName()
	public static final String SERVICE_PROVIDER_TYPE = "ak-service-provider";
	public static final String SERVICE_PROVIDER_NAME = "AK Service Provider";
	public static final String SERVICE_PROVIDER_DESCRIPTION = "AK Provider Description";
	public static final String SERVICE_TYPE = "ak-service";
	public static final String SERVICE_NAME = "AK Service";
	public static final String CONNECTION_TYPE = "ak-service-connection";
    public static final String CONNECTION_NAME = "AK Service Connection";

	public TrainingServiceAdapter() throws Exception {
		super(TimerReactor.getTimerReactor(SERVICE_PROVIDER_NAME));
		logger.info(SERVICE_PROVIDER_DESCRIPTION);
	}

	@Override
	public RegistrationRequest getRegistrationRequest() {
		RegistrationRequest registration = new RegistrationRequest();
        registration.setName(SERVICE_PROVIDER_NAME);
        registration.setVersion("1.0.0");

        // references to common types
        String X_PROPERTY_TYPE =
            "application/" + PropertyType.class.getName() + "+xml";
        Link string_type = new Link();
        string_type.setName("string-any");
        string_type.setType(X_PROPERTY_TYPE);
        
        Link integer_type = new Link();
        integer_type.setName("integer-any");
        integer_type.setType(X_PROPERTY_TYPE);
        
        // property definition to specify test properties
        PropertyDefinition svcTestPD = new PropertyDefinition();
        svcTestPD.setName("test-service-prop");
        svcTestPD.setDisplayName(SERVICE_NAME + " Test Properties");
        svcTestPD.setDescription(svcTestPD.getDisplayName());
        svcTestPD.setReadable(true);
        svcTestPD.setWritable(true);
        svcTestPD.setMaxAllowed(1);
        svcTestPD.setPropertyType(integer_type);

        // define demo service
        Link service = new Link();
        service.setName("service");
        service.setType("application/" + Service.class.getName() + "+xml");

        AssetType serviceType = new AssetType();
        serviceType.setName(SERVICE_TYPE);
        serviceType.setDisplayName(SERVICE_NAME);
        serviceType.setDescription("AK Service Description");
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
        
        Link connection = new Link();
        connection.setName("designconnection");
        connection.setType(X_CONNECTION_TYPE);

        // workload to demo connection
        ConnectionDefinition workloadDestConnection =
            new ConnectionDefinition();
        workloadDestConnection.setName("workload-to-demo-service");
        workloadDestConnection.setDescription("From any workload");
        workloadDestConnection.setConnectionType(mockLink);
        workloadDestConnection.setSourceType(workloadLink);
        serviceType.getDestConnections().add(workloadDestConnection);
        
        AssetType connectionType = new AssetType();
        connectionType.setName(CONNECTION_TYPE);
        connectionType.setDisplayName(CONNECTION_NAME);
        connectionType.setDescription(CONNECTION_NAME);
        connectionType.setSuperType(connection);
        connectionType.setAllowExtensions(true);
        
        // define demo service provider
        Link serviceprovidertype = new Link();
        serviceprovidertype.setName("serviceprovidertype");
        serviceprovidertype.setType("application/" +
                                    ServiceProviderType.class.getName() +
                                    "+xml");

        // define demo provider properties
        PropertyDefinition provTestPD = new PropertyDefinition();
        provTestPD.setName("test-serviceprovider-prop");
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
        
        serviceType.getEditors().add(Editor.VARIABLES);
        serviceType.getEditors().add(Editor.FIREWALL);
        serviceType.getEditors().add(Editor.POLICY);

        registration.getAssetTypes().add(connectionType);
        registration.getAssetTypes().add(serviceType);
        registration.getAssetTypes().add(mockProvider);
        registration.getServiceProviderTypes().addAll(getServiceProviderTypes());
        return registration;
	}

	@Override
	public IServiceInstance getServiceInstanceOperations() {
		return new TrainingServiceInstanceOperations();
	}

	@Override
	public IServiceProvider getServiceProviderOperations() {
		return new TrainingServiceProviderOperations();
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
	public void onRegistration(RegistrationResponse arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IInstanceLifecycle getInstanceOperations() {
		return new TrainingInstanceOperations();
	}
	
	
}
