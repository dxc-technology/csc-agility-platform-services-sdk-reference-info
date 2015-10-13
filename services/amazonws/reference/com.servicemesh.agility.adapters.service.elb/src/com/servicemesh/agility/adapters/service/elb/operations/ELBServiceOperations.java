/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.elasticloadbalancing.doc._2012_06_01.ApplySecurityGroupsToLoadBalancerResponse;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.CreateLoadBalancerResponse;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.DeleteLoadBalancerResponse;
import com.amazonaws.elasticloadbalancing.doc._2012_06_01.ModifyLoadBalancerAttributesResponse;
import com.servicemesh.agility.adapters.core.aws.security.group.resources.CreateSecurityGroupResponseType;
import com.servicemesh.agility.adapters.core.aws.util.EC2SecurityGroupOperations;
import com.servicemesh.agility.adapters.service.elb.ELBAdapter;
import com.servicemesh.agility.adapters.service.elb.connection.AWSConnectionProxy;
import com.servicemesh.agility.adapters.service.elb.connection.AWSConnectionProxyFactory;
import com.servicemesh.agility.adapters.service.elb.connection.ELBEndpoint;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.Network;
import com.servicemesh.agility.api.NetworkInterface;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.PropertyType;
import com.servicemesh.agility.api.Resource;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.sdk.service.helper.PropertyHelper;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.QueryParam;
import com.servicemesh.io.http.QueryParams;

public class ELBServiceOperations extends ServiceInstanceOperations
{

    private AWSConnectionProxyFactory _factory;

    public ELBServiceOperations(ELBAdapter adapter, AWSConnectionProxyFactory factory)
    {
        _factory = factory;
    }

    @Override
    public Promise<ServiceProviderResponse> provision(final ServiceInstanceProvisionRequest request)
    {
        // create load balancer instance based on template settings
        final AWSConnectionProxy connection;
        try {
            connection = _factory.getELBConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_CREATE_LOAD_BALANCER);
        ServiceInstance serviceInstance = request.getServiceInstance();
        ServiceProvider provider = request.getProvider();
        if (serviceInstance == null || provider == null)
            return Promise.pure(new Exception("Invalid parameters"));

        Map<String, List<AssetProperty>> config = new HashMap<String, List<AssetProperty>>();
        for (AssetProperty property : serviceInstance.getAssetProperties()) {
            List<AssetProperty> list = config.get(property.getName());
            if (list == null) {
                list = new ArrayList<AssetProperty>();
                config.put(property.getName(), list);
            }
            list.add(property);
        }

        List<AssetProperty> lb_type = config.get(ELBEndpoint.CONFIG_LB_TYPE);
        if (lb_type != null && lb_type.size() == 1) {
            if (lb_type.get(0).getStringValue().equals("internal"))
                params.add(new QueryParam(ELBEndpoint.ELB_SCHEME, ELBEndpoint.ELB_INTERNAL));
        }

        List<AssetProperty> lb_port = config.get(ELBEndpoint.CONFIG_LB_PORT);
        List<AssetProperty> lb_proto = config.get(ELBEndpoint.CONFIG_LB_PROTOCOL);
        List<AssetProperty> instance_port = config.get(ELBEndpoint.CONFIG_INSTANCE_PORT);
        List<AssetProperty> instance_protocol = config.get(ELBEndpoint.CONFIG_INSTANCE_PROTOCOL);
        if (lb_port == null || lb_proto == null || instance_port == null || instance_protocol == null
                || lb_port.size() != lb_proto.size() || instance_port.size() != instance_protocol.size()
                || lb_port.size() != instance_port.size() || lb_port.size() < 1) {
            return Promise.pure(new Exception("Invalid listener configuration"));
        }

        for (int i = 0; i < lb_port.size(); i++) {
            params.add(new QueryParam(String.format(ELBEndpoint.ELB_LISTENER_LB_PROTOCOL, (i + 1)), lb_proto.get(i)
                    .getStringValue()));
            params.add(new QueryParam(String.format(ELBEndpoint.ELB_LISTENER_LB_PORT, (i + 1)), "" + lb_port.get(i).getIntValue()));
            params.add(new QueryParam(String.format(ELBEndpoint.ELB_LISTENER_INSTANCE_PROTOCOL, (i + 1)), lb_proto.get(i)
                    .getStringValue()));
            params.add(new QueryParam(String.format(ELBEndpoint.ELB_LISTENER_INSTANCE_PORT, (i + 1)), ""
                    + lb_port.get(i).getIntValue()));
        }

        Map<Integer, Network> networks = new HashMap<Integer, Network>();
        for (Network network : request.getNetworks())
            networks.put(network.getId(), network);
        Set<String> locations = new HashSet<String>();

        int i = 1;
        String vpcId = null;
        for (Asset asset : request.getDependents()) {
            if (asset instanceof Template) {
                Template template = (Template) asset;
                for (Resource r1 : template.getResources()) {
                    if (r1 instanceof NetworkInterface) {
                        NetworkInterface ni = (NetworkInterface) r1;
                        if (ni.getNetwork() != null) {
                            Network network = networks.get(ni.getNetwork().getId());
                            if (network != null) {
                                String subnetId = getProperty(network, ELBEndpoint.CONFIG_SUBNET_ID);
                                if (subnetId != null) {
                                    vpcId = getProperty(network, ELBEndpoint.CONFIG_VPC_ID);
                                    params.add(new QueryParam(String.format(ELBEndpoint.ELB_SUBNETS, i), subnetId));
                                    i++;
                                }
                            }
                        }
                    }
                    locations.add(template.getLocation());
                }
            }
        }

        // if not in vpc - must specify availability zone
        if (vpcId == null) {
            for (String location : locations) {
                params.add(new QueryParam(String.format(ELBEndpoint.ELB_AVAILABILITY_ZONES, i), location));
                i++;
            }
        }

        final String elb_name = ELBEndpoint.getName(serviceInstance);
        final String f_vpcId = vpcId;
        params.add(new QueryParam(ELBEndpoint.ELB_NAME, elb_name));

        Promise<CreateLoadBalancerResponse> promise = connection.execute(params, CreateLoadBalancerResponse.class);
        return promise.flatMap(new Function<CreateLoadBalancerResponse, Promise<ServiceProviderResponse>>() {

            @Override
            public Promise<ServiceProviderResponse> invoke(CreateLoadBalancerResponse lbResponse)
            {
                ServiceInstance serviceInstance = request.getServiceInstance();
                Link typeString = new Link();
                typeString.setName("string-any");
                typeString.setType("application/" + PropertyType.class.getName() + "+xml");

                AssetProperty elbName = new AssetProperty();
                elbName.setName(ELBEndpoint.CONFIG_ELB_NAME);
                elbName.setDescription("ELB Name");
                elbName.setPropertyType(typeString);
                elbName.setStringValue(elb_name);
                serviceInstance.getConfigurations().add(elbName);

                AssetProperty dnsName = new AssetProperty();
                dnsName.setName(ELBEndpoint.CONFIG_DNS_NAME);
                dnsName.setDescription("DNS Name");
                dnsName.setPropertyType(typeString);
                dnsName.setStringValue(lbResponse.getCreateLoadBalancerResult().getDNSName());
                serviceInstance.getConfigurations().add(dnsName);
                return update(connection, request, f_vpcId);
            }

        });
    }

    @Override
    public Promise<ServiceProviderResponse> reconfigure(final ServiceInstanceReconfigureRequest request)
    {
        try {
            final AWSConnectionProxy connection;
            try {
                connection = _factory.getELBConnection(request);
            }
            catch (Exception ex) {
                return Promise.pure(ex);
            }
            String vpcId = null;
            for (Network network : request.getNetworks()) {
                if ((vpcId = getProperty(network, ELBEndpoint.CONFIG_VPC_ID)) != null)
                    break;
            }
            return update(connection, request, vpcId);
        }
        catch (Throwable t) {
            return Promise.pure(t);
        }
    }

    private Promise<ServiceProviderResponse> update(final AWSConnectionProxy connection, final ServiceInstanceRequest request,
            final String vpcId)
    {
        // modify load balancer instance based on service settings
        final ServiceInstance serviceInstance = request.getServiceInstance();
        ServiceProvider provider = request.getProvider();
        if (serviceInstance == null || provider == null)
            return Promise.pure(new Exception("Invalid parameters"));

        QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_MODIFY_LOAD_BALANCER);
        params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(serviceInstance)));

        Map<String, AssetProperty> config = new HashMap<String, AssetProperty>();
        for (AssetProperty property : serviceInstance.getConfigurations())
            config.put(property.getName(), property);

        AssetProperty property = config.get(ELBEndpoint.CONFIG_ACCESS_LOG_ENABLED);
        if (property != null && property.isBooleanValue()) {
            params.add(new QueryParam(ELBEndpoint.ELB_ACCESS_LOG_ENABLED, "true"));
            if ((property = config.get(ELBEndpoint.CONFIG_ACCESS_LOG_INTERVAL)) != null)
                params.add(new QueryParam(ELBEndpoint.ELB_ACCESS_LOG_INTERVAL, "" + property.getIntValue()));
            if ((property = config.get(ELBEndpoint.CONFIG_ACCESS_LOG_S3_BUCKET)) != null)
                params.add(new QueryParam(ELBEndpoint.ELB_ACCESS_LOG_S3_BUCKET, "" + property.getStringValue()));
            if ((property = config.get(ELBEndpoint.CONFIG_ACCESS_LOG_S3_PREFIX)) != null)
                params.add(new QueryParam(ELBEndpoint.ELB_ACCESS_LOG_S3_PREFIX, "" + property.getStringValue()));
        }
        else {
            params.add(new QueryParam(ELBEndpoint.ELB_ACCESS_LOG_ENABLED, "false"));
        }

        if ((property = config.get(ELBEndpoint.CONFIG_CONNECTION_DRAINING_ENABLED)) != null && property.isBooleanValue()) {
            params.add(new QueryParam(ELBEndpoint.ELB_CONNECTION_DRAINING_ENABLED, "true"));
            if ((property = config.get(ELBEndpoint.CONFIG_CONNECTION_DRAINING_TIMEOUT)) != null)
                params.add(new QueryParam(ELBEndpoint.ELB_CONNECTION_DRAINING_TIMEOUT, "" + property.getIntValue()));
        }
        else {
            params.add(new QueryParam(ELBEndpoint.ELB_ACCESS_LOG_ENABLED, "false"));
        }
        if ((property = config.get(ELBEndpoint.CONFIG_CONNECTION_IDLE_TIMEOUT)) != null && property.getIntValue() > 0)
            params.add(new QueryParam(ELBEndpoint.ELB_CONNECTION_IDLE_TIMEOUT, "" + property.getIntValue()));
        if ((property = config.get(ELBEndpoint.CONFIG_CROSS_ZONE_LOAD_BALANCING)) != null && property.isBooleanValue())
            params.add(new QueryParam(ELBEndpoint.ELB_CROSS_ZONE_LOAD_BALANCING, "true"));
        else
            params.add(new QueryParam(ELBEndpoint.ELB_CROSS_ZONE_LOAD_BALANCING, "false"));

        Promise<ModifyLoadBalancerAttributesResponse> promise =
                connection.execute(params, ModifyLoadBalancerAttributesResponse.class);
        return promise.flatMap(new Function<ModifyLoadBalancerAttributesResponse, Promise<ServiceProviderResponse>>() {

            @Override
            public Promise<ServiceProviderResponse> invoke(ModifyLoadBalancerAttributesResponse lbResponse)
            {
                if (vpcId == null || serviceInstance.getFirewallRules().size() == 0) {
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.getModified().add(serviceInstance);
                    response.setStatus(Status.COMPLETE);
                    return Promise.pure(response);
                }
                return updateFirewallRules(request, vpcId);
            }
        });

    }

    private Promise<ServiceProviderResponse> updateFirewallRules(final ServiceInstanceRequest request, String vpcId)
    {
        final ServiceInstance serviceInstance = request.getServiceInstance();
        final AWSConnectionProxy securityGroups;
        try {
            securityGroups = _factory.getSecurityGroupsConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        EC2SecurityGroupOperations securityGroupOps = new EC2SecurityGroupOperations(securityGroups.getAWSConnection());
        Promise<List<CreateSecurityGroupResponseType>> promise =
                securityGroupOps.createSecurityGroups(serviceInstance.getFirewallRules(), vpcId);
        return promise.flatMap(new Function<List<CreateSecurityGroupResponseType>, Promise<ServiceProviderResponse>>() {

            @Override
            public Promise<ServiceProviderResponse> invoke(List<CreateSecurityGroupResponseType> securityGroups)
            {
                final AWSConnectionProxy elb;
                try {
                    elb = _factory.getELBConnection(request);
                }
                catch (Exception ex) {
                    return Promise.pure(ex);
                }
                QueryParams params = elb.newQueryParams(ELBEndpoint.ELB_APPLY_SECURITY_GROUPS);
                params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(serviceInstance)));
                int i = 1;
                for (CreateSecurityGroupResponseType item : securityGroups)
                    params.add(new QueryParam(String.format(ELBEndpoint.ELB_SECURITY_GROUPS, i++), item.getGroupId()));

                Promise<ApplySecurityGroupsToLoadBalancerResponse> promise =
                        elb.execute(params, ApplySecurityGroupsToLoadBalancerResponse.class);
                return promise.map(new Function<ApplySecurityGroupsToLoadBalancerResponse, ServiceProviderResponse>() {

                    @Override
                    public ServiceProviderResponse invoke(ApplySecurityGroupsToLoadBalancerResponse lbResponse)
                    {
                        StringBuilder ids = new StringBuilder();
                        for (String sg : lbResponse.getApplySecurityGroupsToLoadBalancerResult().getSecurityGroups().getMember()) {
                            if (ids.length() > 0)
                                ids.append(",");
                            ids.append(sg);
                        }

                        PropertyHelper.setString(serviceInstance.getConfigurations(), ELBEndpoint.CONFIG_SECURITY_GROUPS,
                                ids.toString());
                        ServiceProviderResponse response = new ServiceProviderResponse();
                        response.getModified().add(serviceInstance);
                        response.setStatus(Status.COMPLETE);
                        return response;
                    }
                });
            }
        });
    }

    @Override
    public Promise<ServiceProviderResponse> release(final ServiceInstanceReleaseRequest request)
    {
        // create load balancer instance based on template settings
        final AWSConnectionProxy connection;
        try {
            connection = _factory.getELBConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        final ServiceInstance serviceInstance = request.getServiceInstance();
        ServiceProvider provider = request.getProvider();
        if (serviceInstance == null || provider == null)
            return Promise.pure(new Exception("Invalid parameters"));

        QueryParams params = connection.newQueryParams(ELBEndpoint.ELB_DELETE_LOAD_BALANCER);
        params.add(new QueryParam(ELBEndpoint.ELB_NAME, ELBEndpoint.getName(serviceInstance)));
        Promise<DeleteLoadBalancerResponse> promise = connection.execute(params, DeleteLoadBalancerResponse.class);
        return promise.map(new Function<DeleteLoadBalancerResponse, ServiceProviderResponse>() {

            @Override
            public ServiceProviderResponse invoke(DeleteLoadBalancerResponse lbResponse)
            {
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.setStatus(Status.COMPLETE);
                return response;
            }
        });
    }

    private String getProperty(Network network, String name)
    {
        for (Property property : network.getProperties()) {
            if (property.getName().equals(name))
                return property.getValue();
        }
        return null;
    }
}
