/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.ec2.doc._2013_10_15.DescribeInstancesResponseType;
import com.amazonaws.ec2.doc._2013_10_15.GroupItemType;
import com.amazonaws.ec2.doc._2013_10_15.ReservationInfoType;
import com.amazonaws.ec2.doc._2013_10_15.RunningInstancesItemType;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.AttachLoadBalancerToSubnetsResponse;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.DeregisterInstancesFromLoadBalancerResponse;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.EnableAvailabilityZonesForLoadBalancerResponse;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.RegisterInstancesWithLoadBalancerResponse;
import com.servicemesh.agility.adapters.core.aws.security.group.resources.AuthorizeSecurityGroupIngressResponseType;
import com.servicemesh.agility.adapters.core.aws.security.group.resources.IpPermissionType;
import com.servicemesh.agility.adapters.core.aws.security.group.resources.SecurityGroupItemType;
import com.servicemesh.agility.adapters.core.aws.security.group.resources.UserIdGroupPairSetType;
import com.servicemesh.agility.adapters.core.aws.security.group.resources.UserIdGroupPairType;
import com.servicemesh.agility.adapters.core.aws.util.EC2SecurityGroupOperations;
import com.servicemesh.agility.adapters.service.elb.ELBAdapter;
import com.servicemesh.agility.adapters.service.elb.connection.AWSConnectionProxy;
import com.servicemesh.agility.adapters.service.elb.connection.AWSConnectionProxyFactory;
import com.servicemesh.agility.adapters.service.elb.connection.EC2Endpoint;
import com.servicemesh.agility.adapters.service.elb.connection.ELBEndpoint;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.Location;
import com.servicemesh.agility.api.Network;
import com.servicemesh.agility.api.NetworkInterface;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.Resource;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.sdk.service.helper.PropertyHelper;
import com.servicemesh.agility.sdk.service.msgs.InstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceResponse;
import com.servicemesh.agility.sdk.service.operations.InstanceOperations;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.QueryParam;
import com.servicemesh.io.http.QueryParams;

public class ELBInstanceOperations extends InstanceOperations
{

    private AWSConnectionProxyFactory _factory;

    public ELBInstanceOperations(ELBAdapter adapter, AWSConnectionProxyFactory factory)
    {
        _factory = factory;
    }

    @Override
    public Promise<InstanceResponse> postProvision(InstancePostProvisionRequest request)
    {
        List<String> subnetIds = new ArrayList<String>();
        Template template = request.getTemplate();
        Map<Integer, Network> networks = new HashMap<Integer, Network>();
        for (Network network : request.getNetworks())
            networks.put(network.getId(), network);

        for (Resource r1 : template.getResources())
            if (r1 instanceof NetworkInterface) {
                NetworkInterface ni = (NetworkInterface) r1;
                if (ni.getNetwork() != null) {
                    Network network = networks.get(ni.getNetwork().getId());
                    if (network != null) {
                        String subnetId = PropertyHelper.getProperty(network.getProperties(), ELBEndpoint.CONFIG_SUBNET_ID);
                        if (subnetId != null)
                            subnetIds.add(subnetId);
                    }
                }
            }

        if (subnetIds.size() > 0)
            // if this is a vpc - need to add the subnet to the load balancer
            return enableSubnets(request, subnetIds);
        else
            // otherwise - make sure availability zone has been added
            return enableAvailabilityZone(request);
    }

    @Override
    public Promise<InstanceResponse> postStart(InstancePostStartRequest request)
    {
        return enableAvailabilityZone(request);
    }

    @Override
    public Promise<InstanceResponse> preStop(InstancePreStopRequest request)
    {
        return deregisterInstance(request);
    }

    @Override
    public Promise<InstanceResponse> preRelease(InstancePreReleaseRequest request)
    {
        return deregisterInstance(request);
    }

    //
    // Attach subnet to the load balancer
    //

    private Promise<InstanceResponse> enableSubnets(final InstanceRequest request, List<String> subnetIds)
    {
        try {
            final AWSConnectionProxy connection;
            try {
                connection = _factory.getELBConnection(request);
            }
            catch (Exception ex) {
                return Promise.pure(ex);
            }
            QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_ATTACH_SUBNETS);
            ServiceInstance binding = request.getServiceInstance();
            ServiceProvider provider = request.getProvider();
            if (binding == null || provider == null)
                return Promise.pure(new Exception("Invalid parameters"));

            params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(binding)));

            int i = 1;
            for (String subnetId : subnetIds) {
                params.add(new QueryParam(String.format(ELBEndpoint.ELB_SUBNETS, i), subnetId));
                i++;
            }

            Promise<AttachLoadBalancerToSubnetsResponse> promise =
                    connection.execute(params, AttachLoadBalancerToSubnetsResponse.class);
            return promise.flatMap(new Function<AttachLoadBalancerToSubnetsResponse, Promise<InstanceResponse>>() {

                @Override
                public Promise<InstanceResponse> invoke(AttachLoadBalancerToSubnetsResponse lbResponse)
                {
                    return enableSecurityGroups(request);
                }
            });
        }
        catch (Throwable t) {
            return Promise.pure(t);
        }
    }

    //
    // Enable availability zone on the load balancer
    //

    private Promise<InstanceResponse> enableAvailabilityZone(final InstanceRequest request)
    {
        final AWSConnectionProxy connection;
        try {
            connection = _factory.getELBConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_ENABLE_AVAILABILITY_ZONES);
        ServiceInstance binding = request.getServiceInstance();
        ServiceProvider provider = request.getProvider();
        if (binding == null || provider == null)
            return Promise.pure(new Exception("Invalid parameters"));

        params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(binding)));
        if (request.getLocations().size() == 0)
            return Promise.pure(new Exception("Invalid location parameter"));

        Location location = request.getLocations().get(0);
        params.add(new QueryParam(String.format(ELBEndpoint.ELB_AVAILABILITY_ZONES, 1), location.getName()));
        Promise<EnableAvailabilityZonesForLoadBalancerResponse> promise =
                connection.execute(params, EnableAvailabilityZonesForLoadBalancerResponse.class);
        return promise.flatMap(new Function<EnableAvailabilityZonesForLoadBalancerResponse, Promise<InstanceResponse>>() {

            @Override
            public Promise<InstanceResponse> invoke(EnableAvailabilityZonesForLoadBalancerResponse arg)
            {
                return enableSecurityGroups(request);
            }

        });
    }

    //
    // Enable access to load balancer security groups
    //

    private Promise<InstanceResponse> enableSecurityGroups(final InstanceRequest request)
    {
        ServiceInstance serviceInstance = request.getServiceInstance();
        final String lb_groups =
                PropertyHelper.getString(serviceInstance.getConfigurations(), ELBEndpoint.CONFIG_SECURITY_GROUPS, null);
        if (lb_groups == null || lb_groups.trim().length() == 0) // assume that it has been granted access via some other approach
            return registerInstance(request);

        Map<String, List<AssetProperty>> config = new HashMap<String, List<AssetProperty>>();
        for (AssetProperty property : serviceInstance.getAssetProperties()) {
            List<AssetProperty> list = config.get(property.getName());
            if (list == null) {
                list = new ArrayList<AssetProperty>();
                config.put(property.getName(), list);
            }
            list.add(property);
        }

        // required ports/protocols on instance
        final List<AssetProperty> instance_port = config.get(ELBEndpoint.CONFIG_INSTANCE_PORT);
        final List<AssetProperty> instance_protocol = config.get(ELBEndpoint.CONFIG_INSTANCE_PROTOCOL);

        // otherwise grant the elb security group(s) access to the instance security group(s)
        final AWSConnectionProxy ec2;
        final AWSConnectionProxy securityGroups;
        try {
            ec2 = _factory.getEC2Connection(request);
            securityGroups = _factory.getSecurityGroupsConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        QueryParams params = ec2.newQueryParams(EC2Endpoint.EC2_DESCRIBE_INSTANCE);
        params.add(new QueryParam(EC2Endpoint.EC2_INSTANCE_ID + ".1", request.getInstance().instanceId));
        Promise<DescribeInstancesResponseType> promise = ec2.execute(params, DescribeInstancesResponseType.class);
        return promise.flatMap(new Function<DescribeInstancesResponseType, Promise<InstanceResponse>>() {

            @Override
            public Promise<InstanceResponse> invoke(DescribeInstancesResponseType response)
            {
                List<String> sg_ids = new ArrayList<String>();
                for (ReservationInfoType rsv : response.getReservationSet().getItem()) {
                    for (RunningInstancesItemType item : rsv.getInstancesSet().getItem()) {
                        for (GroupItemType group : item.getGroupSet().getItem()) {
                            sg_ids.add(group.getGroupId());
                        }
                    }
                }

                final EC2SecurityGroupOperations ops = new EC2SecurityGroupOperations(securityGroups.getAWSConnection());
                Promise<List<SecurityGroupItemType>> promise = ops.getSecurityGroups(sg_ids);
                return promise.flatMap(new Function<List<SecurityGroupItemType>, Promise<InstanceResponse>>() {

                    @Override
                    public Promise<InstanceResponse> invoke(List<SecurityGroupItemType> securityGroups)
                    {
                        List<Promise<AuthorizeSecurityGroupIngressResponseType>> promises =
                                new ArrayList<Promise<AuthorizeSecurityGroupIngressResponseType>>();
                        for (int p = 0; p < instance_protocol.size() && p < instance_port.size(); p++) {
                            String to_protocol = instance_protocol.get(p).getStringValue();
                            int to_port = instance_port.get(p).getIntValue();
                            if (to_protocol.equalsIgnoreCase("udp") == false)
                                to_protocol = "tcp"; // http/https/tcp

                            // Walk through the security groups assigned to the instance
                            // and verify that if the security group opens the port the 
                            // load balancer has access to it

                            for (SecurityGroupItemType sg : securityGroups) {
                                IpPermissionType perm = securityGroupContains(sg, to_protocol, to_port);
                                if (perm != null) {
                                    boolean found = false;
                                    for (UserIdGroupPairType group : perm.getGroups().getItem())
                                        if (lb_groups.contains(group.getGroupId())) {
                                            found = true;
                                            break;
                                        }
                                    if (found == false) {
                                        // open up the security group to the load balancer
                                        for (String id : lb_groups.split(",")) {
                                            UserIdGroupPairType groupToAdd = new UserIdGroupPairType();
                                            groupToAdd.setGroupId(id);
                                            UserIdGroupPairSetType groups = new UserIdGroupPairSetType();
                                            groups.getItem().add(groupToAdd);
                                            IpPermissionType permToAdd = new IpPermissionType();
                                            permToAdd.setFromPort(perm.getFromPort());
                                            permToAdd.setToPort(perm.getToPort());
                                            permToAdd.setIpProtocol(perm.getIpProtocol());
                                            permToAdd.setGroups(groups);
                                            promises.add(ops.authorizeSecurityGroupIngress(sg.getGroupId(), permToAdd));
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        return Promise.sequence(promises).flatMap(
                                new Function<List<AuthorizeSecurityGroupIngressResponseType>, Promise<InstanceResponse>>() {

                                    @Override
                                    public Promise<InstanceResponse> invoke(List<AuthorizeSecurityGroupIngressResponseType> arg)
                                    {
                                        return registerInstance(request);
                                    }
                                });
                    }
                });
            }
        });
    }

    private IpPermissionType securityGroupContains(SecurityGroupItemType sg, String protocol, int port)
    {
        for (IpPermissionType perm : sg.getIpPermissions().getItem()) {
            if (perm.getIpProtocol().equalsIgnoreCase(protocol) == false)
                continue;
            if (perm.getFromPort() > port || perm.getToPort() < port)
                continue;
            return perm;
        }
        return null;
    }

    //
    // add the instance to the load balancer
    //

    private Promise<InstanceResponse> registerInstance(final InstanceRequest request)
    {
        try {
            final AWSConnectionProxy connection;
            try {
                connection = _factory.getELBConnection(request);
            }
            catch (Exception ex) {
                return Promise.pure(ex);
            }
            QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_REGISTER_INSTANCES);
            final ServiceInstance binding = request.getServiceInstance();
            ServiceProvider provider = request.getProvider();
            if (binding == null || provider == null)
                return Promise.pure(new Exception("Invalid parameters"));

            params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(binding)));
            params.add(new QueryParam(String.format(ELBEndpoint.ELB_INSTANCES, 1), request.getInstance().getInstanceId()));
            Promise<RegisterInstancesWithLoadBalancerResponse> promise =
                    connection.execute(params, RegisterInstancesWithLoadBalancerResponse.class);
            return promise.map(new Function<RegisterInstancesWithLoadBalancerResponse, InstanceResponse>() {

                @Override
                public InstanceResponse invoke(RegisterInstancesWithLoadBalancerResponse lbResponse)
                {
                    InstanceResponse response = new InstanceResponse();
                    Instance instance = request.getInstance();
                    Map<String, Property> properties = new HashMap<String, Property>();
                    for (Property property : instance.getProperties())
                        properties.put(property.getName(), property);

                    for (AssetProperty property : binding.getConfigurations()) {
                        if (property.getName().equals(ELBEndpoint.CONFIG_DNS_NAME)) {
                            Property lb_name = properties.get("Load Balancer");
                            if (lb_name == null) {
                                lb_name = new Property();
                                lb_name.setName("Load Balancer");
                                instance.getProperties().add(lb_name);
                            }
                            lb_name.setValue(property.getStringValue());
                            response.setInstance(instance);
                        }
                        if (property.getName().equals(ELBEndpoint.CONFIG_ELB_NAME)) {
                            Property elb_name = properties.get("ELB Name");
                            if (elb_name == null) {
                                elb_name = new Property();
                                elb_name.setName("ELB Name");
                                instance.getProperties().add(elb_name);
                            }
                            elb_name.setValue(property.getStringValue());
                            response.setInstance(instance);
                        }
                    }
                    response.setStatus(Status.COMPLETE);
                    return response;
                }
            });
        }
        catch (Throwable t) {
            return Promise.pure(t);
        }
    }

    //
    // remove the instance from the load balancer
    //

    private Promise<InstanceResponse> deregisterInstance(final InstanceRequest request)
    {
        try {
            final AWSConnectionProxy connection;
            try {
                connection = _factory.getELBConnection(request);
            }
            catch (Exception ex) {
                return Promise.pure(ex);
            }
            QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_DEREGISTER_INSTANCES);
            ServiceInstance binding = request.getServiceInstance();
            ServiceProvider provider = request.getProvider();
            if (binding == null || provider == null)
                return Promise.pure(new Exception("Invalid parameters"));

            String instanceId = request.getInstance().getInstanceId();
            if (instanceId == null) {
                // nothing to do
                InstanceResponse response = new InstanceResponse();
                response.setStatus(Status.COMPLETE);
                return Promise.pure(response);
            }

            params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(binding)));
            params.add(new QueryParam(String.format(ELBEndpoint.ELB_INSTANCES, 1), request.getInstance().getInstanceId()));
            Promise<DeregisterInstancesFromLoadBalancerResponse> promise =
                    connection.execute(params, DeregisterInstancesFromLoadBalancerResponse.class);
            Promise<InstanceResponse> retval =
                    promise.map(new Function<DeregisterInstancesFromLoadBalancerResponse, InstanceResponse>() {

                        @Override
                        public InstanceResponse invoke(DeregisterInstancesFromLoadBalancerResponse arg)
                        {
                            InstanceResponse response = new InstanceResponse();
                            response.setStatus(Status.COMPLETE);
                            return response;
                        }
                    });

            // ignore errors unmapping
            return retval.recover(new Function<Throwable, InstanceResponse>() {

                @Override
                public InstanceResponse invoke(Throwable t)
                {
                    InstanceResponse response = new InstanceResponse();
                    response.setStatus(Status.COMPLETE);
                    return response;
                }

            });
        }
        catch (Throwable t) {
            return Promise.pure(t);
        }
    }

}
