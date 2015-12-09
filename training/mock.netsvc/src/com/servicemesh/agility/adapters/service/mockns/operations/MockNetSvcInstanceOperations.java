package com.servicemesh.agility.adapters.service.mockns.operations;

import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.State;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.sdk.service.msgs.InstancePostBootRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreBootRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceResponse;
import com.servicemesh.agility.sdk.service.operations.InstanceOperations;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;

import org.apache.log4j.Logger;

public class MockNetSvcInstanceOperations extends InstanceOperations
{
     private static final Logger logger =
        Logger.getLogger(MockNetSvcInstanceOperations.class);

    public MockNetSvcInstanceOperations()
    {
    }

    @Override
    public Promise<InstanceResponse> preProvision(InstancePreProvisionRequest request)
    {
        return execute("preProvision", request);
    }

    @Override
    public Promise<InstanceResponse> postProvision(InstancePostProvisionRequest request)
    {
        // Just call execute() for the logging aspect - real work will follow
        Promise<InstanceResponse> promise = execute("postProvision", request);
        InstanceResponse response = null;
        try {
            response = promise.get();
        }
        catch (Throwable t) {}

        // Real work: rename the template and instance to a pseudo-hostname
        if (response != null) {
            Instance instance = request.getInstance();
            Template template = request.getTemplate();

            if ((instance != null) && (template != null)) {
                // Generate a pseudo-hostname
                String myHostName = "myhost-" + System.currentTimeMillis();
                instance.setName(myHostName);
                response.setInstance(instance);
                template.setName(myHostName);
                response.getModified().add(template);
            }
        }
        return promise;
    }

    @Override
    public Promise<InstanceResponse> preBoot(InstancePreBootRequest request)
    {
        return execute("preBoot", request);
    }

    @Override
    public Promise<InstanceResponse> postBoot(InstancePostBootRequest request)
    {
        return execute("postBoot", request);
    }

    @Override
    public Promise<InstanceResponse> preStop(final InstancePreStopRequest request)
    {
        return execute("preStop", request);
    }

    @Override
    public Promise<InstanceResponse> postStop(final InstancePostStopRequest request)
    {
        return execute("postStop", request);
    }

    @Override
    public Promise<InstanceResponse> preStart(final InstancePreStartRequest request)
    {
        return execute("preStart", request);
    }

    @Override
    public Promise<InstanceResponse> postStart(final InstancePostStartRequest request)
    {
        return execute("postStart", request);
    }

    @Override
    public Promise<InstanceResponse> preRestart(InstancePreRestartRequest request)
    {
        return execute("preRestart", request);
    }

    @Override
    public Promise<InstanceResponse> postRestart(InstancePostRestartRequest request)
    {
        return execute("postRestart", request);
    }

    @Override
    public Promise<InstanceResponse> preRelease(InstancePreReleaseRequest request)
    {
        return execute("preRelease", request);
    }

    @Override
    public Promise<InstanceResponse> postRelease(InstancePostReleaseRequest request)
    {
        return execute("postRelease", request);
    }

    @Override
    public Promise<InstanceResponse> preReconfigure(final InstancePreReconfigureRequest request)
    {
        return execute("preReconfigure", request);
    }

    @Override
    public Promise<InstanceResponse> postReconfigure(final InstancePostReconfigureRequest request)
    {
        return execute("postReconfigure", request);
    }

    private Promise<InstanceResponse> execute(String operation,
                                              InstanceRequest request)
    {
        ServiceInstance dstInstance = request.getServiceInstance();
        Instance srcInstance = request.getInstance();
        StringBuilder sb = (logger.isDebugEnabled()) ?
            new StringBuilder(operation) : null;

        if (dstInstance != null) {
            if (sb != null) {
                ServiceState state = dstInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                sb.append(" { ServiceInstanceId=").append(dstInstance.getId())
                    .append(", name=").append(dstInstance.getName())
                    .append(", state=").append(strState).append(" }");
            }
        }

        if (srcInstance != null) {
            if (sb != null) {
                State state = srcInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                sb.append(" { Instance id=").append(srcInstance.getId())
                    .append(", name=").append(srcInstance.getName())
                    .append(", state=").append(strState).append(" }");
            }

            Link template = srcInstance.getTemplate();
            if (template != null) {
                sb.append(", template=").append(template.getName());
            }
        }
        if (sb != null) {
            logger.debug(sb.toString());
        }
        InstanceResponse response = new InstanceResponse();
        response.setStatus(Status.COMPLETE);
        return Promise.pure(response);
    }
}
