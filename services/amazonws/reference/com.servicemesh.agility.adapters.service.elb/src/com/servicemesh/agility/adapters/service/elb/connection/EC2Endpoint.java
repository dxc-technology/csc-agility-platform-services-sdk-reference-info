/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.connection;

import org.apache.log4j.Logger;

import com.amazonaws.ec2.doc._2013_10_15.SecurityGroupItemType;
import com.servicemesh.agility.adapters.core.aws.AWSEndpoint;
import com.servicemesh.agility.adapters.core.aws.AWSEndpointFactory;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;

public class EC2Endpoint implements AWSEndpointProxy
{
    private static final Logger logger = Logger.getLogger(EC2Endpoint.class);
    private AWSEndpoint _endpoint;

    // Per AWS doc: A few services, such as Amazon EC2, let you specify an
    // endpoint that does not include a specific region, for example,
    // https://ec2.amazonaws.com. In that case, AWS routes the endpoint to
    // us-east-1.
    public static final String EC2_GENERAL_ENDPOINT = "ec2.amazonaws.com";
    public static final String EC2_DEFAULT_REGION = "us-east-1";

    // the JAXB context will use so if the Amazon types are updated, this must be changed to match
    public static final String EC2_VERSION = "2013-10-15";
    public static final String EC2_JAXB_PACKAGE = "com.amazonaws.ec2.doc._" + EC2_VERSION.replaceAll("-", "_");

    public static final String CONFIG_ENDPOINT = "ec2-endpoint";

    public static final String EC2_CREATE_SECURITY_GROUP = "CreateSecurityGroup";
    public static final String EC2_DESCRIBE_INSTANCE = "DescribeInstances";
    public static final String EC2_INSTANCE_ID = "InstanceId";
    public static final String EC2_DESCRIBE_SECURITY_GROUPS = "DescribeSecurityGroups";
    public static final String EC2_AUTHORIZE_SECURITY_GROUP_INGRESS = "AuthorizeSecurityGroupIngress";
    public static final String EC2_AUTHORIZE_SECURITY_GROUP_EGRESS = "AuthorizeSecurityGroupEgress";
    public static final String EC2_REVOKE_SECURITY_GROUP_INGRESS = "RevokeSecurityGroupIngress";
    public static final String EC2_REVOKE_SECURITY_GROUP_EGRESS = "RevokeSecurityGroupEgress";
    public static final String EC2_CLASSIC_SECURITY_GROUP_NAME = "GroupName";
    public static final String EC2_CLASSIC_SECURITY_GROUP_ID = "GroupId";
    public static final String EC2_VPC_SECURITY_GROUP_NAME = "group-name";
    public static final String EC2_VPC_ID = "vpc-id";

    private EC2Endpoint(AWSEndpoint endpoint)
    {
        _endpoint = endpoint;
    }

    public static EC2Endpoint getInstance(String address) throws Exception
    {
        if (address == null) {
            throw new Exception("No address for EC2Endpoint");
        }
        AWSEndpointFactory aef = AWSEndpointFactory.getInstance();
        AWSEndpoint endpoint = null;

        if (address.contains(EC2_GENERAL_ENDPOINT)) {
            endpoint = aef.getEndpoint(address, EC2_DEFAULT_REGION, EC2_VERSION, SecurityGroupItemType.class);
        }
        else { // region should be contained within the address
            endpoint = aef.getEndpoint(address, EC2_VERSION, SecurityGroupItemType.class);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("create() EC2Endpoint: address=" + address);
        }
        return new EC2Endpoint(endpoint);
    }

    public static EC2Endpoint getInstance(ServiceProviderRequest request) throws Exception
    {
        EC2Endpoint endpoint = null;
        ServiceProvider provider = request.getProvider();
        if (provider == null) {
            throw new Exception("The service provider cannot be null");
        }
        for (AssetProperty property : provider.getProperties()) {
            if (property.getName().equals(EC2Endpoint.CONFIG_ENDPOINT)) {
                endpoint = getInstance(property.getStringValue());
                break;
            }
        }
        return endpoint;
    }

    @Override
    public AWSEndpoint getEndpoint()
    {
        return _endpoint;
    }
}
