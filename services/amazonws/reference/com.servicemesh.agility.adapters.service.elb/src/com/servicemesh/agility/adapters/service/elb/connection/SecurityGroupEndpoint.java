/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.connection;

import com.servicemesh.agility.adapters.core.aws.AWSEndpoint;

public class SecurityGroupEndpoint implements AWSEndpointProxy
{
    AWSEndpoint _endpoint;

    public SecurityGroupEndpoint(AWSEndpoint ep)
    {
        _endpoint = ep;
    }

    @Override
    public AWSEndpoint getEndpoint()
    {
        return _endpoint;
    }

}
