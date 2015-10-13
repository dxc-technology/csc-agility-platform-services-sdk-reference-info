/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.connection;

import java.util.List;

import com.microsoft.schemas.azure.trafficmgr.Error;

import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.core.azure.AzureConnectionFactory;
import com.servicemesh.agility.adapters.core.azure.AzureEndpoint;
import com.servicemesh.agility.adapters.core.azure.AzureEndpointFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter;
import com.servicemesh.agility.api.Credential;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;
import com.servicemesh.io.proxy.Proxy;

public class ConnectionFactory
{

    public ConnectionFactory() throws Exception
    {
    }

    /**
     * Creates a connection for a request
     * 
     * @param request
     *            A service provider request
     * @return A connection to a Microsoft Azure service
     */
    public AzureConnection getConnection(ServiceProviderRequest request) throws Exception
    {
        return getConnection(request, getEndpoint(request));
    }

    /**
     * Creates a connection for a request
     * 
     * @param request
     *            A service provider request
     * @param endpoint
     *            An endpoint for a Microsoft Azure service
     * @return A connection to a Microsoft Azure service
     */
    public AzureConnection getConnection(ServiceProviderRequest request, AzureEndpoint endpoint) throws Exception
    {
        ServiceProvider provider = request.getProvider();
        if (provider == null) {
            throw new Exception("ServiceProvider not supplied");
        }

        // Just use the first proxy if defined
        List<Proxy> proxy = TrafficManagerAdapter.getProxyConfig(request);
        Proxy theProxy = null;
        if (proxy != null && !proxy.isEmpty()) {
            theProxy = proxy.get(0);
        }
        Credential cred = AzureConnectionFactory.getCredentials(request.getProvider(), request.getClouds());
        if (cred == null) {
            throw new Exception("No credentials found.");
        }
        return AzureConnectionFactory.getInstance().getConnection(request.getSettings(), cred, theProxy, endpoint);
    }

    /**
     * Creates an endpoint for a request
     * 
     * @param request
     *            The service provider request
     * @return An endpoint for a Microsoft Azure service
     */
    public AzureEndpoint getEndpoint(ServiceProviderRequest request) throws Exception
    {
        String subscription = AzureConnectionFactory.getSubscription(request.getProvider(), request.getClouds());
        if (subscription == null) {
            throw new Exception("No subscription found.");
        }
        return AzureEndpointFactory.getInstance().getEndpoint(subscription, Constants.TRAFFIC_MGR_VERSION,
                Error.class.getPackage().getName(), Error.class);
    }

    /**
     * Unregisters the context for the Traffic Manager schema
     */
    public void unregisterContext()
    {
        AzureEndpointFactory.getInstance().unregisterContext(Error.class.getPackage().getName());
    }
}
