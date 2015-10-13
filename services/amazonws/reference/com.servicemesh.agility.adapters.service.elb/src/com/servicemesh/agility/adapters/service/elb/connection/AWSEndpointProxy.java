/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.connection;

import com.servicemesh.agility.adapters.core.aws.AWSEndpoint;

public interface AWSEndpointProxy
{
    /** Returns the underlying AWSEndpoint */
    public AWSEndpoint getEndpoint();
}
