/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.operations;

import java.util.Calendar;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLAdapter;
import com.servicemesh.agility.adapters.service.azure.sql.connection.AzureSQLConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLUtil;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPingRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPostCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPostDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPostUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderSyncRequest;
import com.servicemesh.agility.sdk.service.operations.ServiceProviderOperations;
import com.servicemesh.azure.sql.models.Servers;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;

public class AzureSQLProviderOperations extends ServiceProviderOperations
{
    private static Logger logger = Logger.getLogger(AzureSQLProviderOperations.class);

    private AzureSQLAdapter adapter;
    private AzureSQLConnectionFactory factory;

    public AzureSQLProviderOperations(AzureSQLAdapter adapter, AzureSQLConnectionFactory factory)
    {
        if (adapter != null && factory != null) {
            this.adapter = adapter;
            this.factory = factory;

            AzureSQLProviderOperations.logger.trace("AzureSQLProviderOperations has been created.");
            AzureSQLProviderOperations.logger.trace("Adapter has a value - " + AzureSQLUtil.isValued(this.adapter));
            AzureSQLProviderOperations.logger.trace("Factory has a value - " + AzureSQLUtil.isValued(this.factory));
        }
        else {
            StringBuilder errMsg =
                    new StringBuilder("AzureSQLProviderOperations requires both an adapter and a factory to be set.");

            if (adapter == null) {
                errMsg.append("  The adapter value is null.");
            }

            if (factory == null) {
                errMsg.append("  The factory value is null.");
            }

            throw new AzureAdapterException(errMsg.toString());
        }
    }

    /**
     * This method is used to verify the provider is operational. The ping operation is designed as a simple call, which if
     * successful, indicates the provider is working normally.
     * 
     * @param ServiceProviderPingRequest
     *            request - the HTTP request object. This call does not required any properties.
     * @return Promise<ServiceProviderResponse> - promise for a response where a status of COMPLETE indicates the provider is
     *         working normally.
     * @return Promise<AzureAdapterException> - promise for a wrapped Azure exception.
     */
    @Override
    public Promise<ServiceProviderResponse> ping(final ServiceProviderPingRequest request)
    {
        if (AzureSQLUtil.isValued(request)) {
            try {
                return new AzureSQLRestHelper(request).listServers().map(new Function<Servers, ServiceProviderResponse>() {
                    @Override
                    public ServiceProviderResponse invoke(Servers servers)
                    {
                        ServiceProviderResponse response = new ServiceProviderResponse();

                        response.setReqId(request.getReqId());
                        response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                        response.setStatus(Status.COMPLETE);
                        response.setMessage("SUCCESS - " + (servers != null ? servers.getServers().size() : 0) + " servers found");

                        return response;
                    }
                });
            }
            catch (Exception ex) {
                String msg = "An exception occurred while performing the 'ping' operation.";

                AzureSQLProviderOperations.logger.error(msg, ex);

                return Promise.pure(new AzureAdapterException(msg + "\n" + ex));
            }
        }
        else {
            String msg = "No request object was provided to the ping method.";

            AzureSQLProviderOperations.logger.error(msg);

            return Promise.pure(new AzureAdapterException(msg));
        }
    }

    @Override
    public Promise<ServiceProviderResponse> preCreate(ServiceProviderPreCreateRequest request)
    {
        return super.preCreate(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postCreate(ServiceProviderPostCreateRequest request)
    {
        return super.postCreate(request);
    }

    @Override
    public Promise<ServiceProviderResponse> preUpdate(ServiceProviderPreUpdateRequest request)
    {
        return super.preUpdate(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postUpdate(ServiceProviderPostUpdateRequest request)
    {
        return super.postUpdate(request);
    }

    @Override
    public Promise<ServiceProviderResponse> preDelete(ServiceProviderPreDeleteRequest request)
    {
        return super.preDelete(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postDelete(ServiceProviderPostDeleteRequest request)
    {
        return super.postDelete(request);
    }

    @Override
    public Promise<ServiceProviderResponse> sync(ServiceProviderSyncRequest request)
    {
        return super.sync(request);
    }

    @Override
    public Promise<ServiceProviderResponse> start(ServiceProviderStartRequest request)
    {
        return super.start(request);
    }

    @Override
    public Promise<ServiceProviderResponse> stop(ServiceProviderStopRequest request)
    {
        return super.stop(request);
    }

}
