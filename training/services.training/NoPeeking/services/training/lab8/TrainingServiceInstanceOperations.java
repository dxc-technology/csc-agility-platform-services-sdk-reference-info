package services.training.lab8;

import org.apache.log4j.Logger;

import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceValidateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ValidateMode;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;

public class TrainingServiceInstanceOperations extends ServiceInstanceOperations {

	private static final Logger logger = Logger.getLogger(TrainingServiceInstanceOperations.class);
	
	@Override
	public Promise<ServiceProviderResponse> release(ServiceInstanceReleaseRequest request) {
		logger.debug("Service " + request.getServiceInstance().getName() + " taking a nap.");
		ServiceProviderResponse resp = new ServiceProviderResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

	@Override
	public Promise<ServiceProviderResponse> start(ServiceInstanceStartRequest request) {
		logger.debug("Service " + request.getServiceInstance().getName() + " says hello.");
		ServiceProviderResponse resp = new ServiceProviderResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}
	
	@Override
	public Promise<ServiceProviderResponse> provision(ServiceInstanceProvisionRequest request) {
		logger.debug("Service " + request.getServiceInstance().getName() + " says hello.");
		ServiceProviderResponse resp = new ServiceProviderResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

	@Override
	public Promise<ServiceProviderResponse> validate(ServiceInstanceValidateRequest request) {
		if (request.getMode() == ValidateMode.CREATE) 
		{
			Integer value = null;
			for (AssetProperty prop : request.getServiceInstance().getAssetProperties()) {
				if (prop.getName().equals("test-service-prop")) {
					value = prop.getIntValue();
					break;
				}
			}
			if (value != null && value < 0) 
			{
				ServiceProviderResponse resp = new ServiceProviderResponse();
				resp.setStatus(Status.FAILURE);
				resp.setMessage("Service property test-service-prop cannot be negative.");
				return Promise.pure(resp);
			}
		}
		ServiceProviderResponse resp = new ServiceProviderResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

}
