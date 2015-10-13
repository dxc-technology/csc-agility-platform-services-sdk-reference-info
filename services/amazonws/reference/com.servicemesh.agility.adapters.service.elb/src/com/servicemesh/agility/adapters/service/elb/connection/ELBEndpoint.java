/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.connection;

import org.apache.log4j.Logger;

import com.amazonaws.elasticloadbalancing.doc._2012_06_01.Error;
import com.servicemesh.agility.adapters.core.aws.AWSEndpoint;
import com.servicemesh.agility.adapters.core.aws.AWSEndpointFactory;
import com.servicemesh.agility.api.ServiceInstance;

public class ELBEndpoint implements AWSEndpointProxy
{
    private static final Logger logger = Logger.getLogger(ELBEndpoint.class);
    private AWSEndpoint _endpoint;

    // the JAXB context will use so if the Amazon types are updated, this must be changed to match
    public static final String ELB_VERSION = "2012-06-01";

    // Per AWS doc: "If you just specify the general endpoint
    // (elasticloadbalancing.amazonaws.com), Elastic Load Balancing directs
    // your request to the us-east-1 endpoint.
    public static final String ELB_GENERAL_ENDPOINT = "elasticloadbalancing.amazonaws.com";
    public static final String ELB_DEFAULT_REGION = "us-east-1";

    // Property Parameters
    public static final String CONFIG_SUBNET_ID = "subnetId";
    public static final String CONFIG_VPC_ID = "vpcId";
    public static final String CONFIG_LB_PORT = "lb-port";
    public static final String CONFIG_LB_PROTOCOL = "lb-protocol";
    public static final String CONFIG_LB_TYPE = "lb-type";
    public static final String CONFIG_LB_TYPE_INTERNAL = "internal";
    public static final String CONFIG_LB_TYPE_INTERNET = "internet";
    public static final String CONFIG_INSTANCE_PORT = "instance-port";
    public static final String CONFIG_INSTANCE_PROTOCOL = "instance-protocol";
    public static final String CONFIG_ELB_NAME = "elb-name";
    public static final String CONFIG_DNS_NAME = "dns-name";
    public static final String CONFIG_SECURITY_GROUPS = "security-groups";
    public static final String CONFIG_ACCESS_LOG_ENABLED = "access-log-enabled";
    public static final String CONFIG_ACCESS_LOG_INTERVAL = "access-log-interval";
    public static final String CONFIG_ACCESS_LOG_S3_BUCKET = "access-log-s3bucket";
    public static final String CONFIG_ACCESS_LOG_S3_PREFIX = "access-log-s3prefix";
    public static final String CONFIG_CONNECTION_DRAINING_ENABLED = "connection-draining-enabled";
    public static final String CONFIG_CONNECTION_DRAINING_TIMEOUT = "connection-draining-timeout";
    public static final String CONFIG_CONNECTION_IDLE_TIMEOUT = "connection-idle-timeout";
    public static final String CONFIG_CROSS_ZONE_LOAD_BALANCING = "cross-zone-load-balancing";

    // ELB Commands
    public static final String DESCRIBE_LOAD_BALANCERS = "DescribeLoadBalancers";

    // ELB Create Load Balancer Parameters
    public static final String ELB_CREATE_LOAD_BALANCER = "CreateLoadBalancer";
    public static final String ELB_DELETE_LOAD_BALANCER = "DeleteLoadBalancer";
    public static final String ELB_SCHEME = "Scheme";
    public static final String ELB_NAME = "LoadBalancerName";
    public static final String ELB_INTERNAL = "internal";
    public static final String ELB_LISTENER_LB_PROTOCOL = "Listeners.member.%d.Protocol";
    public static final String ELB_LISTENER_LB_PORT = "Listeners.member.%s.LoadBalancerPort";
    public static final String ELB_LISTENER_INSTANCE_PORT = "Listeners.member.%d.InstancePort";
    public static final String ELB_LISTENER_INSTANCE_PROTOCOL = "Listeners.member.%d.InstanceProtocol";

    // ELB Modify Load Balancer Attributes
    public static final String ELB_MODIFY_LOAD_BALANCER = "ModifyLoadBalancerAttributes";
    public static final String ELB_ACCESS_LOG_ENABLED = "LoadBalancerAttributes.AccessLog.Enabled";
    public static final String ELB_ACCESS_LOG_INTERVAL = "LoadBalancerAttributes.AccessLog.EmitInterval";
    public static final String ELB_ACCESS_LOG_S3_BUCKET = "LoadBalancerAttributes.AccessLog.S3BucketName";
    public static final String ELB_ACCESS_LOG_S3_PREFIX = "LoadBalancerAttributes.AccessLog.S3BucketPrefix";
    public static final String ELB_CONNECTION_DRAINING_ENABLED = "LoadBalancerAttributes.ConnectionDraining.Enabled";
    public static final String ELB_CONNECTION_DRAINING_TIMEOUT = "LoadBalancerAttributes.ConnectionDraining.Timeout";
    public static final String ELB_CONNECTION_IDLE_TIMEOUT = "LoadBalancerAttributes.ConnectionSettings.IdleTimeout";
    public static final String ELB_CROSS_ZONE_LOAD_BALANCING = "LoadBalancerAttributes.CrossZoneLoadBalancing.Enabled";

    // ELB Register/Deregister Instance with the load balancer
    public static final String ELB_REGISTER_INSTANCES = "RegisterInstancesWithLoadBalancer";
    public static final String ELB_DEREGISTER_INSTANCES = "DeregisterInstancesFromLoadBalancer";
    public static final String ELB_INSTANCES = "Instances.member.%d.InstanceId";

    // ELB Enable/Disable availability zones with the load balancer
    public static final String ELB_ENABLE_AVAILABILITY_ZONES = "EnableAvailabilityZonesForLoadBalancer";
    public static final String ELB_DISABLE_AVAILABILITY_ZONES = "DisableAvailabilityZonesForLoadBalancer";
    public static final String ELB_AVAILABILITY_ZONES = "AvailabilityZones.member.%d";

    // ELB Apply/Remove security groups to/from th load balancer
    public static final String ELB_APPLY_SECURITY_GROUPS = "ApplySecurityGroupsToLoadBalancer";
    public static final String ELB_SECURITY_GROUPS = "SecurityGroups.member.%d";

    // ELB Attach/Detach subnets to/from the load balancer
    public static final String ELB_ATTACH_SUBNETS = "AttachLoadBalancerToSubnets";
    public static final String ELB_DETACH_SUBNETS = "DetachLoadBalancerFromSubnets";
    public static final String ELB_SUBNETS = "Subnets.member.%d";

    private ELBEndpoint(AWSEndpoint endpoint)
    {
        _endpoint = endpoint;
    }

    public static ELBEndpoint getInstance(String address) throws Exception
    {
        if (address == null) {
            throw new Exception("No address for ELBEndpoint");
        }
        AWSEndpointFactory aef = AWSEndpointFactory.getInstance();
        AWSEndpoint endpoint = null;

        if (address.contains(ELB_GENERAL_ENDPOINT)) {
            endpoint = aef.getEndpoint(address, ELB_DEFAULT_REGION, ELB_VERSION, Error.class);
        }
        else { // region should be contained within the address
            endpoint = aef.getEndpoint(address, ELB_VERSION, Error.class);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("create() ELBEndpoint: address=" + address);
        }
        return new ELBEndpoint(endpoint);
    }

    public static String getName(ServiceInstance binding)
    {
        StringBuilder name = new StringBuilder();
        String serviceName = binding.getService().getName();
        for (int i = 0; i < serviceName.length(); i++) {
            char ch = serviceName.charAt(i);
            if (Character.isLetterOrDigit(ch))
                name.append(ch);
            else
                name.append("-");
        }
        name.append("-");
        name.append(binding.getId());
        return name.toString();
    }

    @Override
    public AWSEndpoint getEndpoint()
    {
        return _endpoint;
    }
}
