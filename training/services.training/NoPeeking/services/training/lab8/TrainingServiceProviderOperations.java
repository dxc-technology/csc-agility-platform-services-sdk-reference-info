package services.training.lab8;

import org.apache.log4j.Logger;

import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPingRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.ServiceProviderOperations;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;

public class TrainingServiceProviderOperations extends ServiceProviderOperations
{

	private static final Logger logger = Logger.getLogger(TrainingServiceProviderOperations.class);
	
	@Override
	public Promise<ServiceProviderResponse> ping(ServiceProviderPingRequest request) {
		String value = null;
		for (AssetProperty prop : request.getProvider().getProperties()) {
			if (prop.getName().equals("test-serviceprovider-prop")) {
				value = prop.getStringValue();
				break;
			}
		}
		logger.debug("Service Provider " + request.getProvider().getName() + " ping operation. Property value: " + value);
		
		ServiceProviderResponse resp = new ServiceProviderResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

}
