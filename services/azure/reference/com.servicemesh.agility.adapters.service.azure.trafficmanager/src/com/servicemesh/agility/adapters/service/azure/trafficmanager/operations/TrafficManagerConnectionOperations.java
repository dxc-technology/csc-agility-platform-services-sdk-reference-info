/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.microsoft.schemas.azure.trafficmgr.Definition;
import com.microsoft.schemas.azure.trafficmgr.Endpoint;
import com.microsoft.schemas.azure.trafficmgr.Endpoints;
import com.microsoft.schemas.azure.trafficmgr.LoadBalancingMethod;
import com.microsoft.schemas.azure.trafficmgr.Policy;
import com.microsoft.schemas.azure.trafficmgr.Type;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter.InstanceCategory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter.ServiceCategory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerConfig;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.Constants;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.State;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.distributed.sync.AsyncLock;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPostCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPostUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPreCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPreDeleteRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionPreUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ConnectionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.core.async.Callback;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class TrafficManagerConnectionOperations extends com.servicemesh.agility.sdk.service.operations.ConnectionOperations
{
    private static final Logger logger = Logger.getLogger(TrafficManagerConnectionOperations.class);

    private final TrafficManagerAdapter _adapter;
    private final ConnectionFactory _factory;

    public TrafficManagerConnectionOperations(TrafficManagerAdapter adapter, ConnectionFactory factory)
    {
        _adapter = adapter;
        _factory = factory;
    }

    private Promise<ServiceProviderResponse> doValidate(ConnectionRequest request)
    {
        AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        // location is required for traffic manager or any type endpoints for performance load balancing
        if (request.getDestination() != null && request.getDestination() instanceof ServiceInstance) {
            ServiceInstance service = (ServiceInstance) request.getDestination();
            Asset sourceEndpoint = request.getSource();
            try {
                _adapter.validateLocation(connection, service, sourceEndpoint, request.getConnection(), request.getClouds(),
                        request.getServiceProviders());
            }
            catch (Exception ex) {
                return Promise.pure(ex);
            }
        }
        ServiceProviderResponse response = new ServiceProviderResponse();
        response.setStatus(Status.COMPLETE);
        return Promise.pure(response);
    }

    @Override
    public Promise<ServiceProviderResponse> preCreate(ConnectionPreCreateRequest request)
    {
        return doValidate(request);
    }

    private boolean isValidState(ServiceInstance service)
    {
        if (service.getState() == ServiceState.UNPROVISIONED || service.getState() == ServiceState.FAILED) {
            return false;
        }
        return true;
    }

    @Override
    public Promise<ServiceProviderResponse> postCreate(final ConnectionPostCreateRequest request)
    {
        if (request.getSource() == null) {
            return Promise.pure(new Exception("Source is not found"));
        }
        if (request.getDestination() == null) {
            return Promise.pure(new Exception("Destination is not found"));
        }
        if (request.getConnection() == null) {
            return Promise.pure(new Exception("Connection is not found"));
        }
        // don't care about not service instance destination
        if (!(request.getDestination() instanceof ServiceInstance)) {
            return super.postCreate(request);
        }
        ServiceInstance service = (ServiceInstance) request.getDestination();
        // check service state
        if (!isValidState(service)) {
            return super.postCreate(request);
        }
        final Asset sourceEndpoint = request.getSource();
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, service.getAssetProperties());
        if (profileName == null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                    "Could not attach to load balancer. Profile name is not found."));
        }
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions/1";
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                // degrade if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerConnectionOperations.logger.isDebugEnabled()) {
                                        TrafficManagerConnectionOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                                            "Could not attach to load balancer.  Load balancer cannot be found."));
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                if (sourceEndpoint instanceof Template) {
                                    return addInstanceEndpoints(definition, profileName, connection, request);
                                }
                                else {
                                    return addServiceEndpoint(definition, profileName, connection, request);
                                }
                            }
                        });
                flatPromise.onFailure(new Callback<Throwable>() {
                    public void invoke(Throwable t)
                    {
                        arg.unlock();
                    }
                });
                flatPromise.onComplete(new Callback<ServiceProviderResponse>() {
                    public void invoke(ServiceProviderResponse t)
                    {
                        arg.unlock();
                    }
                });
                flatPromise.onCancel(new Callback<Void>() {
                    public void invoke(Void t)
                    {
                        arg.unlock();
                    }
                });
                return flatPromise;
            }
        });
    }

    private Endpoint lookupEndpoint(List<Endpoint> endpoints, String domainName)
    {
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getDomainName().equals(domainName)) {
                return endpoint;
            }
        }
        return null;
    }

    private Promise<ServiceProviderResponse> addInstanceEndpoints(final Definition definition, final String profileName,
            final AzureConnection connection, final ConnectionRequest request)
    {
        final Asset sourceEndpoint = request.getSource();
        Template template = (Template) sourceEndpoint;
        final ServiceInstance service = (ServiceInstance) request.getDestination();
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                    "Could not attach to load balancer. Load balancer policy cannot be found. "));
        }
        Endpoints endpoints = policy.getEndpoints();
        if (endpoints == null) {
            endpoints = new Endpoints();
            policy.setEndpoints(endpoints);
        }
        final List<Instance> instancesAdded = new ArrayList<Instance>();
        final List<Asset> degradedAssets = new ArrayList<Asset>();
        for (Link instanceLink : template.getInstances()) {
            Instance instance = (Instance) _adapter.lookupAsset(request.getDependents(), instanceLink.getId(), Instance.class);
            if (instance == null) {
                continue;
            }
            // for now, we don't care about instance state, we may improve on that later on
            // domain name
            try {
                _adapter.certifyCanonicalName(instance);
            }
            catch (Exception e) {
                degradeEndpoint(instance, "Could not attach to load balancer. Instance domain name cannot be found. ");
                degradedAssets.add(instance);
                continue;
            }
            String domainName = _adapter.getDomainName(instance);
            if (lookupEndpoint(policy.getEndpoints().getEndpoints(), domainName) != null) {
                degradeEndpoint(instance,
                        "Could not attach to load balancer. Endpoint with the same domain name already exists. ");
                degradedAssets.add(instance);
                continue;
            }
            Endpoint endpoint = new Endpoint();
            endpoint.setDomainName(domainName);
            endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
            InstanceCategory category =
                    _adapter.getCategory(connection.getEndpoint().getSubscription(), domainName, instance.getCloud(),
                            request.getClouds());
            if (category == InstanceCategory.SHARED_SUBSCRIPTION) {
                endpoint.setType(Type.CLOUD_SERVICE);
            }
            else {
                endpoint.setType(Type.ANY);
            }
            endpoint.setWeight(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                    TrafficManagerConfig.CONFIG_WEIGHT, request.getConnection().getAssetProperties(),
                    TrafficManagerConfig.WEIGHT_DEFAULT));
            if (policy.getLoadBalancingMethod() == LoadBalancingMethod.PERFORMANCE && endpoint.getType() == Type.ANY) {
                if (category == TrafficManagerAdapter.InstanceCategory.NON_AZURE) {
                    degradeEndpoint(instance,
                            "Could not attach to load balancer. Performance load balancing method can only be used with Azure instances. ");
                    degradedAssets.add(instance);
                    continue;
                }
                endpoint.setLocation(_adapter.getLocation(category, request.getConnection(), instance));
                if (endpoint.getLocation() == null) {
                    degradeEndpoint(instance,
                            "Could not attach to load balancer. Location is required for Performance load balancing method. ");
                    degradedAssets.add(instance);
                    continue;
                }
            }
            policy.getEndpoints().getEndpoints().add(endpoint);
            instancesAdded.add(instance);
        }
        // nothing to do
        if (template.getInstances().size() == degradedAssets.size()) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.getModified().addAll(degradedAssets);
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }

        // order if failover loadbalancing method
        if (policy.getLoadBalancingMethod() == LoadBalancingMethod.FAILOVER) {
            Collections.sort(endpoints.getEndpoints(), new TrafficManagerAdapter.EndpointOrderComparator(request.getDependents(),
                    request.getDestConnections(), _adapter));
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
        Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
        return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() != 200) {
                    String message = "Could not attach to load balancer. " + ConnectionUtil.getStatusInfo(connection, arg);
                    return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint, message));
                }
                ServiceProviderResponse response = new ServiceProviderResponse();

                for (Instance instance : instancesAdded) {
                    _adapter.updateInstanceAdded(instance, service);
                    response.getModified().add(instance);
                }
                response.getModified().addAll(degradedAssets);
                response.setStatus(Status.COMPLETE);
                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                if (definition.getStatus() != null
                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                    return _adapter.resetProfileStatus(profileName, connection,
                            com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                }
                return Promise.pure(response);
            }
        });
    }

    private Promise<ServiceProviderResponse> addServiceEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final ConnectionRequest request)
    {
        final Asset sourceEndpoint = request.getSource();
        final ServiceInstance sourceService = (ServiceInstance) sourceEndpoint;
        final ServiceInstance service = (ServiceInstance) request.getDestination();
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                    "Could not attach to load balancer. Load balancer policy cannot be found. "));
        }
        Endpoints endpoints = policy.getEndpoints();
        if (endpoints == null) {
            endpoints = new Endpoints();
            policy.setEndpoints(endpoints);
        }
        // domain name
        String domainName = _adapter.getDomainName(sourceService);
        if (domainName == null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceService,
                    "Could not attach to load balancer. Service Instance domain name cannot be found. "));
        }
        if (lookupEndpoint(policy.getEndpoints().getEndpoints(), domainName) != null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceService,
                    "Could not attach to load balancer. Endpoint with the same domain name already exists. "));
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setDomainName(domainName);
        endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
        endpoint.setType(_adapter.getEndpointType(sourceService));
        endpoint.setWeight(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_WEIGHT, request.getConnection().getAssetProperties(),
                TrafficManagerConfig.WEIGHT_DEFAULT));
        if (endpoint.getType() == Type.TRAFFIC_MANAGER) {
            endpoint.setMinChildEndpoints(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                    TrafficManagerConfig.CONFIG_MIN_CHILD_ENDPOINTS, request.getConnection().getAssetProperties(),
                    TrafficManagerConfig.MIN_CHILD_ENDPOINTS_DEFAULT));
        }
        // Location Required when LoadBalancingMethod is set to Performance and Type is set to Any or TrafficManager
        if (policy.getLoadBalancingMethod() == LoadBalancingMethod.PERFORMANCE
                && (endpoint.getType() == Type.ANY || endpoint.getType() == Type.TRAFFIC_MANAGER)) {
            ServiceCategory category = _adapter.getCategory(sourceService, request.getServiceProviders());
            if (category == TrafficManagerAdapter.ServiceCategory.NON_AZURE) {
                return Promise
                        .pure(degradeEndpointAndReturn(request, sourceService,
                                "Could not attach to load balancer. Performance load balancing method can only be used with Azure services. "));
            }
            String location =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_LOCATION, request.getConnection().getAssetProperties(), null);
            if (location == null) {
                return Promise.pure(degradeEndpointAndReturn(request, sourceService,
                        "Could not attach to load balancer. Endpoint Location is required. "));
            }
            endpoint.setLocation(location);
        }
        policy.getEndpoints().getEndpoints().add(endpoint);

        // order if failover loadbalancing method
        if (policy.getLoadBalancingMethod() == LoadBalancingMethod.FAILOVER) {
            Collections.sort(endpoints.getEndpoints(), new TrafficManagerAdapter.EndpointOrderComparator(request.getDependents(),
                    request.getDestConnections(), _adapter));
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
        Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
        return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() != 200) {
                    String message = "Could not attach to load balancer. " + ConnectionUtil.getStatusInfo(connection, arg);
                    return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint, message));
                }
                ServiceProviderResponse response = new ServiceProviderResponse();
                // clean up properties
                _adapter.updateServiceInstanceAdded(sourceService, service);
                response.getModified().add(sourceService);
                response.setStatus(Status.COMPLETE);
                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                if (definition.getStatus() != null
                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                    return _adapter.resetProfileStatus(profileName, connection,
                            com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                }
                return Promise.pure(response);
            }
        });
    }

    private Promise<ServiceProviderResponse> updateInstanceEndpoints(final Definition definition, final String profileName,
            final AzureConnection connection, final ConnectionRequest request, List<Endpoint> endpointsToUpdate,
            final List<Asset> degradedAssets, Map<String, Asset> assetMap)
    {
        final Asset sourceEndpoint = request.getSource();
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                    "Could not update on load balancer. Load balancer policy cannot be found. "));
        }

        boolean updated = false;
        for (Endpoint endpointToUpdate : endpointsToUpdate) {
            Endpoint endpoint =
                    policy.getEndpoints() != null ? lookupEndpoint(policy.getEndpoints().getEndpoints(),
                            endpointToUpdate.getDomainName()) : null;
            if (endpoint == null) {
                degradeEndpoint(assetMap.get(endpointToUpdate.getDomainName()),
                        "Could not update on load balancer. Endpoint with the same domain name could not be found. ");
                degradedAssets.add(assetMap.get(endpointToUpdate.getDomainName()));
                continue;
            }
            endpoint.setLocation(endpointToUpdate.getLocation());
            endpoint.setMinChildEndpoints(endpointToUpdate.getMinChildEndpoints());
            endpoint.setWeight(endpointToUpdate.getWeight());
            updated = true;
        }
        // nothing to do
        if (!updated) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.getModified().addAll(degradedAssets);
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
        // order if failover loadbalancing method
        if (policy.getLoadBalancingMethod() == LoadBalancingMethod.FAILOVER) {
            Collections.sort(policy.getEndpoints().getEndpoints(),
                    new TrafficManagerAdapter.EndpointOrderComparator(request.getDependents(), request.getDestConnections(),
                            _adapter));
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
        Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
        return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() != 200) {
                    String message = "Could not update on load balancer. " + ConnectionUtil.getStatusInfo(connection, arg);
                    return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint, message));
                }
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.getModified().addAll(degradedAssets);
                response.setStatus(Status.COMPLETE);
                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                if (definition.getStatus() != null
                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                    return _adapter.resetProfileStatus(profileName, connection,
                            com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                }
                return Promise.pure(response);
            }
        });
    }

    private Promise<ServiceProviderResponse> removeInstanceEndpoints(final Definition definition, final String profileName,
            final AzureConnection connection, final ConnectionRequest request, List<Endpoint> endpointsToDelete,
            final Map<String, Asset> assetMap)
    {
        final ServiceProviderResponse ignore = new ServiceProviderResponse();
        final ServiceInstance service = (ServiceInstance) request.getDestination();
        ignore.setStatus(Status.COMPLETE);
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(ignore);
        }

        boolean deleted = false;
        if (policy.getEndpoints() != null) {
            for (Iterator<Endpoint> iter = policy.getEndpoints().getEndpoints().iterator(); iter.hasNext();) {
                Endpoint endpoint = iter.next();
                Endpoint endpointToDelete = lookupEndpoint(endpointsToDelete, endpoint.getDomainName());
                if (endpointToDelete != null) {
                    deleted = true;
                    iter.remove();
                }
            }
        }
        // nothing to do
        if (!deleted) {
            return Promise.pure(ignore);
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
        Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
        return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() != 200) {
                    return Promise.pure(ignore);
                }
                ServiceProviderResponse response = new ServiceProviderResponse();
                // clean up properties
                for (Asset asset : assetMap.values()) {
                    if (asset instanceof Instance) {
                        _adapter.updateInstanceRemoved((Instance) asset, service);
                    }
                    else if (asset instanceof ServiceInstance) {
                        _adapter.updateServiceInstanceRemoved((ServiceInstance) asset, service);
                    }
                    response.getModified().add(asset);
                }
                response.setStatus(Status.COMPLETE);
                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                if (definition.getStatus() != null
                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                    return _adapter.resetProfileStatus(profileName, connection,
                            com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                }
                return Promise.pure(response);
            }
        });
    }

    @Override
    public Promise<ServiceProviderResponse> preUpdate(ConnectionPreUpdateRequest request)
    {
        return doValidate(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postUpdate(final ConnectionPostUpdateRequest request)
    {
        if (request.getSource() == null) {
            return Promise.pure(new Exception("Source is not found"));
        }
        if (request.getDestination() == null) {
            return Promise.pure(new Exception("Destination is not found"));
        }
        if (request.getConnection() == null) {
            return Promise.pure(new Exception("Connection is not found"));
        }
        // don't care about not service instance destination
        if (!(request.getDestination() instanceof ServiceInstance)) {
            return super.postUpdate(request);
        }
        ServiceInstance service = (ServiceInstance) request.getDestination();
        // check service state
        if (!isValidState(service)) {
            return super.postUpdate(request);
        }
        final Asset sourceEndpoint = request.getSource();
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, service.getAssetProperties());
        if (profileName == null) {
            return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                    "Could not update on load balancer. Profile name is not found."));
        }
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions/1";
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }

        final List<Asset> degradedAssets = new ArrayList<Asset>();
        final List<Endpoint> endpointsToUpdate = new ArrayList<Endpoint>();
        final Map<String, Asset> assetMap = new HashMap<String, Asset>();
        if (sourceEndpoint instanceof Template) {
            Template template = (Template) sourceEndpoint;
            for (Link instanceLink : template.getInstances()) {
                Instance instance =
                        (Instance) _adapter.lookupAsset(request.getDependents(), instanceLink.getId(), Instance.class);
                // for now, we don't care about instance state, we may improve on that later on
                // domain name
                if (instance == null) {
                    continue;
                }
                String domainName = _adapter.getDomainName(instance);
                if (domainName == null) {
                    degradeEndpoint(instance, "Could not update on load balancer. Instance domain name is not found. ");
                    degradedAssets.add(instance);
                    continue;
                }
                assetMap.put(domainName, instance);
                Endpoint endpoint = new Endpoint();
                endpoint.setDomainName(domainName);
                InstanceCategory category =
                        _adapter.getCategory(connection.getEndpoint().getSubscription(), domainName, instance.getCloud(),
                                request.getClouds());
                if (category == InstanceCategory.SHARED_SUBSCRIPTION) {
                    endpoint.setType(Type.CLOUD_SERVICE);
                }
                else {
                    endpoint.setType(Type.ANY);
                }
                endpoint.setWeight(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                        TrafficManagerConfig.CONFIG_WEIGHT, request.getConnection().getAssetProperties(),
                        TrafficManagerConfig.WEIGHT_DEFAULT));
                LoadBalancingMethod lbm =
                        LoadBalancingMethod.fromValue(com.servicemesh.agility.adapters.core.azure.Config
                                .getAssetPropertyAsString(TrafficManagerConfig.CONFIG_LBM_NAME, service.getAssetProperties()));
                if (lbm == LoadBalancingMethod.PERFORMANCE && endpoint.getType() == Type.ANY) {
                    endpoint.setLocation(_adapter.getLocation(category, request.getConnection(), instance));
                    if (endpoint.getLocation() == null) {
                        degradeEndpoint(instance,
                                "Could not update on load balancer. Location is required for Performance load balancing method. ");
                        degradedAssets.add(instance);
                        continue;
                    }
                }
                endpointsToUpdate.add(endpoint);
            }
            // nothing to do
            if (template.getInstances().size() == degradedAssets.size()) {
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.getModified().addAll(degradedAssets);
                response.setStatus(Status.COMPLETE);
                return Promise.pure(response);
            }
        }
        else if (sourceEndpoint instanceof ServiceInstance) {
            ServiceInstance sourceService = (ServiceInstance) sourceEndpoint;
            // domain name
            String domainName = _adapter.getDomainName(sourceService);
            if (domainName == null) {
                return Promise.pure(degradeEndpointAndReturn(request, sourceService,
                        "Could not update to load balancer. Service Instance domain name cannot be found. "));
            }
            assetMap.put(domainName, sourceService);
            Endpoint endpoint = new Endpoint();
            endpoint.setDomainName(domainName);
            endpoint.setType(_adapter.getEndpointType(sourceService));
            endpoint.setWeight(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                    TrafficManagerConfig.CONFIG_WEIGHT, request.getConnection().getAssetProperties(),
                    TrafficManagerConfig.WEIGHT_DEFAULT));
            if (endpoint.getType() == Type.TRAFFIC_MANAGER) {
                endpoint.setMinChildEndpoints(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                        TrafficManagerConfig.CONFIG_MIN_CHILD_ENDPOINTS, request.getConnection().getAssetProperties(),
                        TrafficManagerConfig.MIN_CHILD_ENDPOINTS_DEFAULT));
            }
            // Location Required when LoadBalancingMethod is set to Performance and Type is set to Any or TrafficManager
            LoadBalancingMethod lbm =
                    LoadBalancingMethod.fromValue(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_LBM_NAME, service.getAssetProperties()));
            if (lbm == LoadBalancingMethod.PERFORMANCE
                    && (endpoint.getType() == Type.ANY || endpoint.getType() == Type.TRAFFIC_MANAGER)) {
                String location =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                TrafficManagerConfig.CONFIG_LOCATION, request.getConnection().getAssetProperties(), null);
                if (location == null) {
                    return Promise.pure(degradeEndpointAndReturn(request, sourceService,
                            "Could not update to load balancer. Endpoint Location is required. "));
                }
                endpoint.setLocation(location);
            }
            endpointsToUpdate.add(endpoint);
        }
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerConnectionOperations.logger.isDebugEnabled()) {
                                        TrafficManagerConnectionOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    return Promise.pure(degradeEndpointAndReturn(request, sourceEndpoint,
                                            "Could not update on load balancer.  Load balancer cannot be found."));
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return updateInstanceEndpoints(definition, profileName, connection, request, endpointsToUpdate,
                                        degradedAssets, assetMap);
                            }
                        });
                flatPromise.onFailure(new Callback<Throwable>() {
                    public void invoke(Throwable t)
                    {
                        arg.unlock();
                    }
                });
                flatPromise.onComplete(new Callback<ServiceProviderResponse>() {
                    public void invoke(ServiceProviderResponse t)
                    {
                        arg.unlock();
                    }
                });
                flatPromise.onCancel(new Callback<Void>() {
                    public void invoke(Void t)
                    {
                        arg.unlock();
                    }
                });
                return flatPromise;
            }
        });
    }

    @Override
    public Promise<ServiceProviderResponse> preDelete(final ConnectionPreDeleteRequest request)
    {
        return doDelete(request);
    }

    private Promise<ServiceProviderResponse> doDelete(final ConnectionRequest request)
    {
        final ServiceProviderResponse ignore = new ServiceProviderResponse();
        ignore.setStatus(Status.COMPLETE);
        if (request.getSource() == null) {
            return Promise.pure(new Exception("Source is not found"));
        }
        if (request.getDestination() == null) {
            return Promise.pure(new Exception("Destination is not found"));
        }
        if (request.getConnection() == null) {
            return Promise.pure(new Exception("Connection is not found"));
        }
        // don't care about not service instance destination
        if (!(request.getDestination() instanceof ServiceInstance)) {
            return Promise.pure(ignore);
        }

        ServiceInstance service = (ServiceInstance) request.getDestination();
        final Asset sourceEndpoint = request.getSource();
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, service.getAssetProperties());
        if (profileName == null) {
            return Promise.pure(ignore);
        }
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions/1";
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        final List<Endpoint> endpointsToDelete = new ArrayList<Endpoint>();
        final Map<String, Asset> assetMap = new HashMap<String, Asset>();
        if (sourceEndpoint instanceof Template) {
            Template template = (Template) sourceEndpoint;
            for (Link instanceLink : template.getInstances()) {
                Instance instance =
                        (Instance) _adapter.lookupAsset(request.getDependents(), instanceLink.getId(), Instance.class);
                if (instance == null) {
                    continue;
                }
                String domainName = _adapter.getDomainName(instance);
                if (domainName == null) {
                    continue;
                }
                assetMap.put(domainName, instance);
                Endpoint endpoint = new Endpoint();
                endpoint.setDomainName(domainName);
                endpointsToDelete.add(endpoint);
            }
            // nothing to do
            if (endpointsToDelete.size() == 0) {
                return Promise.pure(ignore);
            }
        }
        else if (sourceEndpoint instanceof ServiceInstance) {
            ServiceInstance sourceService = (ServiceInstance) sourceEndpoint;
            // domain name
            String domainName = _adapter.getDomainName(sourceService);
            if (domainName == null) {
                return Promise.pure(ignore);
            }
            assetMap.put(domainName, sourceService);
            Endpoint endpoint = new Endpoint();
            endpoint.setDomainName(domainName);
            endpointsToDelete.add(endpoint);
        }
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerConnectionOperations.logger.isDebugEnabled()) {
                                        TrafficManagerConnectionOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    return Promise.pure(ignore);
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return removeInstanceEndpoints(definition, profileName, connection, request, endpointsToDelete,
                                        assetMap);
                            }
                        });
                flatPromise.onFailure(new Callback<Throwable>() {
                    public void invoke(Throwable t)
                    {
                        arg.unlock();
                    }
                });
                flatPromise.onComplete(new Callback<ServiceProviderResponse>() {
                    public void invoke(ServiceProviderResponse t)
                    {
                        arg.unlock();
                    }
                });
                flatPromise.onCancel(new Callback<Void>() {
                    public void invoke(Void t)
                    {
                        arg.unlock();
                    }
                });
                return flatPromise;
            }
        });
    }

    private void degradeEndpoint(Asset asset, String degradeReason)
    {
        // degrade only if running
        // TODO: revisit this to better report errors once event framework in place
        if (asset instanceof Instance) {
            if (((Instance) asset).getState() != null && ((Instance) asset).getState() == State.RUNNING) {
                ((Instance) asset).setState(State.DEGRADED);
                _adapter.addOrUpdateAssetProperty("degraded-reason", degradeReason, asset.getAssetProperties());
            }
        }
        else if (asset instanceof ServiceInstance) {
            if (((ServiceInstance) asset).getState() != null && ((ServiceInstance) asset).getState() == ServiceState.RUNNING) {
                ((ServiceInstance) asset).setState(ServiceState.DEGRADED);
                _adapter.addOrUpdateAssetProperty("degraded-reason", degradeReason, ((ServiceInstance) asset).getConfigurations());
            }
        }
    }

    private ServiceProviderResponse degradeEndpointAndReturn(ConnectionRequest request, Asset asset, String degradeReason)
    {
        ServiceProviderResponse response = new ServiceProviderResponse();
        if (asset instanceof Template) {
            // degrade all template instances
            Template template = (Template) asset;
            for (Link instanceLink : template.getInstances()) {
                Instance instance =
                        (Instance) _adapter.lookupAsset(request.getDependents(), instanceLink.getId(), Instance.class);
                if (instance != null && instance.getState() != null && instance.getState() == State.RUNNING) {
                    instance.setState(State.DEGRADED);
                    _adapter.addOrUpdateAssetProperty("degraded-reason", degradeReason, instance.getAssetProperties());
                    response.getModified().add(instance);
                }
            }
        }
        else if (asset instanceof ServiceInstance) {
            ServiceInstance service = (ServiceInstance) asset;
            if (service.getState() != null && service.getState() == ServiceState.RUNNING) {
                service.setState(ServiceState.DEGRADED);
                _adapter.addOrUpdateAssetProperty("degraded-reason", degradeReason, service.getConfigurations());
                response.getModified().add(service);
            }
        }
        response.setStatus(Status.COMPLETE);
        response.setMessage(degradeReason);
        return response;
    }

}
