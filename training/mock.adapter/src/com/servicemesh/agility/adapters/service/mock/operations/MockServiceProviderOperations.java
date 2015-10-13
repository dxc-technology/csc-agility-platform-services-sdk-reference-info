package com.servicemesh.agility.adapters.service.mock.operations;

import java.util.Properties;

import com.servicemesh.agility.adapters.service.mock.MockServiceConfig;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPingRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPostCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPostDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPostUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderSyncRequest;
import com.servicemesh.agility.sdk.service.operations.ServiceProviderOperations;
import com.servicemesh.core.async.Promise;

import org.apache.log4j.Logger;

public class MockServiceProviderOperations extends ServiceProviderOperations
{
    private static final Logger logger =
        Logger.getLogger(MockServiceProviderOperations.class);

    public MockServiceProviderOperations()
    {
    }

    @Override
    public Promise<ServiceProviderResponse> preCreate(ServiceProviderPreCreateRequest request)
    {
        return execute("preCreate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postCreate(ServiceProviderPostCreateRequest request)
    {
        return execute("postCreate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preUpdate(ServiceProviderPreUpdateRequest request)
    {
        return execute("preUpdate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postUpdate(ServiceProviderPostUpdateRequest request)
    {
        return execute("postUpdate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preDelete(ServiceProviderPreDeleteRequest request)
    {
        return execute("preDelete", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postDelete(ServiceProviderPostDeleteRequest request)
    {
        return execute("postDelete", request);
    }

    @Override
    public Promise<ServiceProviderResponse> sync(ServiceProviderSyncRequest request)
    {
        return execute("sync", request);
    }

    @Override
    public Promise<ServiceProviderResponse> ping(ServiceProviderPingRequest request)
    {
        return execute("ping", request);
    }

    @Override
    public Promise<ServiceProviderResponse> start(ServiceProviderStartRequest request)
    {
        return execute("start", request);
    }

    @Override
    public Promise<ServiceProviderResponse> stop(ServiceProviderStopRequest request)
    {
        return execute("stop", request);
    }

    private Promise<ServiceProviderResponse> execute(String operation,
                                                     ServiceProviderRequest request)
    {
        Properties props = null;
        ServiceProvider provider = request.getProvider();
        if (provider != null) {
            props = MockServiceConfig.getTestProperties(provider);
            if (logger.isDebugEnabled()) {
                logger.debug(operation + " ServiceProvider id=" +
                             provider.getId() + ", name=" + provider.getName());
            }
        }
        return MockOperationsManager.execute(this, operation, request,
                                             ServiceProviderResponse.class,
                                             props);
    }
}
