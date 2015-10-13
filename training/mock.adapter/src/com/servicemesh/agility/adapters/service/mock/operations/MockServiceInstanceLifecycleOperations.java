package com.servicemesh.agility.adapters.service.mock.operations;

import java.util.Properties;

import com.servicemesh.agility.adapters.service.mock.MockServiceConfig;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceLifecycleRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceLifecycleOperations;
import com.servicemesh.core.async.Promise;

import org.apache.log4j.Logger;

public class MockServiceInstanceLifecycleOperations
    extends ServiceInstanceLifecycleOperations
{
    private static final Logger logger =
        Logger.getLogger(MockServiceInstanceLifecycleOperations.class);

    public MockServiceInstanceLifecycleOperations()
    {
    }

    @Override
    public Promise<ServiceProviderResponse> preProvision(ServiceInstancePreProvisionRequest request)
    {
        return execute("preProvision", request);
    }

    @Override
    public Promise<ServiceProviderResponse>  postProvision(ServiceInstancePostProvisionRequest request)
    {
        return execute("postProvision", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preStop(ServiceInstancePreStopRequest request)
    {
        return execute("preStop", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postStop(ServiceInstancePostStopRequest request)
    {
        return execute("postStop", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preStart(ServiceInstancePreStartRequest request)
    {
        return execute("preStart", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postStart(ServiceInstancePostStartRequest request)
    {
        return execute("postStart", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preRestart(ServiceInstancePreRestartRequest request)
    {
        return execute("preRestart", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postRestart(ServiceInstancePostRestartRequest request)
    {
        return execute("postRestart", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preRelease(ServiceInstancePreReleaseRequest request)
    {
        return execute("preRelease", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postRelease(ServiceInstancePostReleaseRequest request)
    {
        return execute("postRelease", request);
    }

    private Promise<ServiceProviderResponse> execute(String operation,
                                                     ServiceInstanceLifecycleRequest request)
    {
        ServiceInstance dstInstance = request.getServiceInstance();
        ServiceInstance srcInstance = request.getDependentServiceInstance();
        Properties props = null;
        StringBuilder sb = (logger.isDebugEnabled()) ?
            new StringBuilder(operation) : null;

        if (dstInstance != null) {
            if (sb != null) {
                ServiceState state = dstInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                sb.append(" { ServiceInstance Id=").append(dstInstance.getId())
                    .append(", name=").append(dstInstance.getName())
                    .append(", state=").append(strState).append(" }");
            }
        }
        if (srcInstance != null) {
            if (sb != null) {
                ServiceState state = srcInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                sb.append(" { DependentSvcInst Id=")
                    .append(srcInstance.getId()).append(", name=")
                    .append(srcInstance.getName()) .append(", state=")
                    .append(strState).append(" }");
            }

            for (Connection conn : request.getDestConnections()) {
                Link src = conn.getSource();
                if ((src != null) && (src.getId() == srcInstance.getId()) &&
                    (src.getHref() != null) &&
                    (src.getHref().startsWith("serviceinstance"))) {
                    props = MockServiceConfig.getTestProperties(conn);
                    break;
                }
            }
        }
        if (sb != null) {
            logger.debug(sb.toString());
        }
        return MockOperationsManager.execute(this, operation, request,
                                             ServiceProviderResponse.class,
                                             props);
    }
}
