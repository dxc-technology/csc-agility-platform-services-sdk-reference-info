/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.util;

import org.apache.log4j.Logger;

import com.microsoft.schemas.windowsazure.ServiceResource;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.core.azure.AzureEndpoint;
import com.servicemesh.agility.adapters.core.azure.action.StatusPoller;
import com.servicemesh.agility.sdk.service.msgs.MethodResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.azure.sql.models.Database;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.async.CompletablePromise;
import com.servicemesh.core.messaging.Request;
import com.servicemesh.io.http.IHttpResponse;

public class DatabasePoller extends StatusPoller<MethodResponse>
{
    private static final long serialVersionUID = 20150325;
    private static final Logger logger = Logger.getLogger(DatabasePoller.class);

    public DatabasePoller(ServiceInstanceRequest request, CompletablePromise<MethodResponse> responsePromise, long interval,
            long retries, String desiredState, AzureConnection conn, boolean retryOn404)
    {
        super(request, responsePromise, interval, retries, desiredState, conn, retryOn404);
    }

    @Override
    protected Promise<?> run()
    {
        Promise<?> retval = null;
        String serverName = AzureSQLUtil.getServerName((ServiceInstanceRequest) request);
        String dbName = AzureSQLUtil.getDatabaseName((ServiceInstanceRequest) request);
        String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/databases/" + AzureSQLUtil.escapeSpaces(dbName);

        try {
            retval = conn.get(uri, null, IHttpResponse.class);
        }
        catch (Exception e) {
            DatabasePoller.logger.error("Error checking database status.", e);
        }
        return retval;
    }

    @Override
    protected Object decode(IHttpResponse httpResponse)
    {
        ServiceResource retval = null;
        AzureEndpoint ep = conn.getEndpoint();
        retval = ep.decode(httpResponse, AzureSQLConstants.GENERIC_AZURE_CONTEXT, ServiceResource.class);
        return retval;
    }

    @Override
    protected String getStatus(IHttpResponse httpResponse)
    {
        String retval = null;
        ServiceResource resource = (ServiceResource) getJaxbObject(httpResponse);

        if (resource != null) {
            retval = resource.getState();
        }
        return retval;
    }

    @Override
    protected MethodResponse getResponseObject()
    {
        return new MethodResponse();
    }

    @Override
    protected MethodResponse updateResponseObject(Request request, MethodResponse response, Object cloudObject)
    {
        ServiceInstanceRequest req = (ServiceInstanceRequest) request;
        Database db = AzureSQLUtil.mapResourceToDatabase((ServiceResource) cloudObject);
        AzureSQLUtil.updateDatabase(req.getServiceInstance(), db);
        return response;
    }

    @Override
    protected Logger getLogger()
    {
        return DatabasePoller.logger;
    }

    @Override
    protected boolean isFailedState(String statusValue)
    {
        // if the state was the expected state then this method would not get called. Don't need to check for the expected state here.
        if (!statusValue.equals("Creating")) {
            return true;
        }
        return false;
    }

}
