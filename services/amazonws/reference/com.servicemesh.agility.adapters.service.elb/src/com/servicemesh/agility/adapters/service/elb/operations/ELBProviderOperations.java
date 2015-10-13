/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.operations;

import com.servicemesh.agility.adapters.service.elb.ELBAdapter;
import com.servicemesh.agility.adapters.service.elb.connection.AWSConnectionProxy;
import com.servicemesh.agility.adapters.service.elb.connection.AWSConnectionProxyFactory;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPingRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderSyncRequest;
import com.servicemesh.agility.sdk.service.operations.ServiceProviderOperations;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class ELBProviderOperations extends ServiceProviderOperations
{

    private AWSConnectionProxyFactory _factory;

    public ELBProviderOperations(ELBAdapter adapter, AWSConnectionProxyFactory factory)
    {
        _factory = factory;
    }

    @Override
    public Promise<ServiceProviderResponse> sync(ServiceProviderSyncRequest request)
    {
        ServiceProviderResponse response = new ServiceProviderResponse();
        response.setStatus(Status.COMPLETE);
        return Promise.pure(response);
    }

    @Override
    public Promise<ServiceProviderResponse> ping(final ServiceProviderPingRequest request)
    {
        final AWSConnectionProxy connection;
        try {
            connection = _factory.getELBConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        Promise<IHttpResponse> promise = connection.ping();
        return promise.map(new Function<IHttpResponse, ServiceProviderResponse>() {

            @Override
            public ServiceProviderResponse invoke(IHttpResponse arg)
            {
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.setStatus(Status.COMPLETE);
                return response;
            }
        });
    }
}
