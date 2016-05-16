package services.training.lab8;

import org.apache.log4j.Logger;

import com.servicemesh.agility.sdk.service.msgs.InstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.InstanceOperations;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;

public class TrainingInstanceOperations extends InstanceOperations {
	
	private static final Logger logger = Logger.getLogger(TrainingInstanceOperations.class);

	@Override
	public Promise<InstanceResponse> postProvision(InstancePostProvisionRequest request) {
		logger.debug(request.getInstance().getName() + " was started and connected to " + request.getServiceInstance().getName());
		InstanceResponse resp = new InstanceResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

	@Override
	public Promise<InstanceResponse> postRelease(InstancePostReleaseRequest request) {
		logger.debug(request.getInstance().getName() + " is gone. Time to clean up. ");
		InstanceResponse resp = new InstanceResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

	@Override
	public Promise<InstanceResponse> postStart(InstancePostStartRequest request) {
		logger.debug(request.getInstance().getName() + " was started and connected to " + request.getServiceInstance().getName());
		InstanceResponse resp = new InstanceResponse();
		resp.setStatus(Status.COMPLETE);
		return Promise.pure(resp);
	}

}
