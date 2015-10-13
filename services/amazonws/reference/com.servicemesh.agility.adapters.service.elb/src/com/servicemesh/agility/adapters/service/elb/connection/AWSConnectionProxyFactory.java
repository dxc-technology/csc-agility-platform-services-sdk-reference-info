/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.connection;

import com.servicemesh.agility.adapters.service.elb.ELBAdapter;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;

public class AWSConnectionProxyFactory
{

    public AWSConnectionProxyFactory() throws Exception
    {
    }

    public AWSConnectionProxy getELBConnection(ServiceProviderRequest request) throws Exception
    {
        return AWSConnectionProxy.create(request.getProvider(), request.getClouds(),
                ELBEndpoint.getInstance(request.getProvider().getHostname()), ELBAdapter.getProxyConfig(request),
                request.getSettings());
    }

    public AWSConnectionProxy getEC2Connection(ServiceProviderRequest request) throws Exception
    {
        return AWSConnectionProxy.create(request.getProvider(), request.getClouds(), EC2Endpoint.getInstance(request),
                ELBAdapter.getProxyConfig(request), request.getSettings());
    }

    public AWSConnectionProxy getSecurityGroupsConnection(ServiceProviderRequest request) throws Exception
    {
        return AWSConnectionProxy.createSecurityGroupConnection(request.getProvider(), request.getClouds(),
                ELBAdapter.getProxyConfig(request), request.getSettings());
    }

}
