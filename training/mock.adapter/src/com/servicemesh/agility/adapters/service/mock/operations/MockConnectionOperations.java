package com.servicemesh.agility.adapters.service.mock.operations;

import java.util.Properties;

import com.servicemesh.agility.adapters.service.mock.MockServiceConfig;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPostCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPostDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPostUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPreCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPreDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPreUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.ConnectionOperations;
import com.servicemesh.core.async.Promise;

import org.apache.log4j.Logger;

public class MockConnectionOperations extends ConnectionOperations
{
    private static final Logger logger =
        Logger.getLogger(MockConnectionOperations.class);

    public MockConnectionOperations()
    {
    }

    @Override
    public Promise<ServiceProviderResponse> preCreate(ConnectionPreCreateRequest request)
    {
        return execute("preCreate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postCreate(ConnectionPostCreateRequest request)
    {
        return execute("postCreate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preUpdate(ConnectionPreUpdateRequest request)
    {
        return execute("preUpdate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postUpdate(ConnectionPostUpdateRequest request)
    {
        return execute("postUpdate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> preDelete(ConnectionPreDeleteRequest request)
    {
        return execute("preDelete", request);
    }

    @Override
    public Promise<ServiceProviderResponse> postDelete(ConnectionPostDeleteRequest request)
    {
        return execute("postDelete", request);
    }

    private Promise<ServiceProviderResponse> execute(String operation,
                                                     ConnectionRequest request)
    {
        Properties props = null;
        Connection connection = request.getConnection();
        if (connection != null) {
            props = MockServiceConfig.getTestProperties(connection);
            if (logger.isDebugEnabled()) {
                logger.debug(operation + " Connection id=" +
                             connection.getId() + ", name=" + connection.getName());
            }
        }
        return MockOperationsManager.execute(this, operation, request,
                                             ServiceProviderResponse.class,
                                             props);
    }
}
