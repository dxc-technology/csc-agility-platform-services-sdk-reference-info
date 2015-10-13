/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.operations;

import java.util.Collections;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.microsoft.schemas.azure.trafficmgr.Definition;
import com.microsoft.schemas.azure.trafficmgr.Endpoint;
import com.microsoft.schemas.azure.trafficmgr.Endpoints;
import com.microsoft.schemas.azure.trafficmgr.LoadBalancingMethod;
import com.microsoft.schemas.azure.trafficmgr.Policy;
import com.microsoft.schemas.azure.trafficmgr.Type;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter.ServiceCategory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerConfig;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.Constants;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.distributed.sync.AsyncLock;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceLifecycleRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.core.async.Callback;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class TrafficManagerServiceLifecycleOperations extends
        com.servicemesh.agility.sdk.service.operations.ServiceInstanceLifecycleOperations
{
    private static final Logger logger = Logger.getLogger(TrafficManagerServiceLifecycleOperations.class);

    private final TrafficManagerAdapter _adapter;
    private final ConnectionFactory _factory;

    public TrafficManagerServiceLifecycleOperations(TrafficManagerAdapter adapter, ConnectionFactory factory)
    {
        _adapter = adapter;
        _factory = factory;
    }

    @Override
    public Promise<ServiceProviderResponse> postProvision(final ServiceInstancePostProvisionRequest request)
    {
        if (request.getDependentServiceInstance() == null) {
            return Promise.pure(new Exception("Dependent Service Instance is not found"));
        }
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
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
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerServiceLifecycleOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceLifecycleOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                                            "Could not attach to load balancer.  Load balancer cannot be found."));
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return addEndpoint(definition, profileName, connection, request);
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
    public Promise<ServiceProviderResponse> preStop(ServiceInstancePreStopRequest request)
    {
        return doPreStop(request);
    }

    public Promise<ServiceProviderResponse> doPostStart(final ServiceInstanceLifecycleRequest request)
    {
        if (request.getDependentServiceInstance() == null) {
            return Promise.pure(new Exception("Dependent Service Instance is not found"));
        }
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                    "Could not enable on load balancer. Profile name is not found."));
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
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerServiceLifecycleOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceLifecycleOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                                            "Could not enable on load balancer.  Load balancer cannot be found."));
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return enableEndpoint(definition, profileName, connection, request);
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
    public Promise<ServiceProviderResponse> postStart(ServiceInstancePostStartRequest request)
    {
        return doPostStart(request);
    }

    @Override
    public Promise<ServiceProviderResponse> preRestart(ServiceInstancePreRestartRequest request)
    {
        return doPreStop(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postRestart(ServiceInstancePostRestartRequest request)
    {
        return doPostStart(request);
    }

    public Promise<ServiceProviderResponse> doPreStop(final ServiceInstanceLifecycleRequest request)
    {
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) { // ignore as we should not fail stop because of this 
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
        if (request.getDependentServiceInstance() == null) {
            return Promise.pure(new Exception("Dependent Service Instance is not found"));
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
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                // profile doesn't exist, ignore
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerServiceLifecycleOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceLifecycleOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.COMPLETE);
                                    return Promise.pure(response);
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return disableEndpoint(definition, profileName, connection, request);
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
    public Promise<ServiceProviderResponse> preRelease(final ServiceInstancePreReleaseRequest request)
    {
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) { // ignore as we should not fail release because of this 
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
        if (request.getDependentServiceInstance() == null) {
            return Promise.pure(new Exception("Dependent Service Instance is not found"));
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
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                // profile doesn't exist, ignore
                                if (arg.getStatusCode() != 200) {
                                    if (TrafficManagerServiceLifecycleOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceLifecycleOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.COMPLETE);
                                    return Promise.pure(response);
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return removeEndpoint(definition, profileName, connection, request);
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
    public Promise<ServiceProviderResponse> postRelease(ServiceInstancePostReleaseRequest request)
    {
        // TODO Auto-generated method stub
        return super.postRelease(request);
    }

    private ServiceProviderResponse degradeServiceInstance(ServiceInstance instance, String degradeReason)
    {
        ServiceProviderResponse response = new ServiceProviderResponse();
        response.setStatus(Status.FAILURE);
        response.setMessage(degradeReason);
        return response;
    }

    private String getDomainName(ServiceInstanceLifecycleRequest request)
    {
        return _adapter.getDomainName(request.getDependentServiceInstance());
    }

    private Promise<ServiceProviderResponse> addEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final ServiceInstanceLifecycleRequest request)
    {
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                    "Could not attach to load balancer. Load balancer policy cannot be found. "));
        }
        Endpoints endpoints = policy.getEndpoints();
        if (endpoints == null) {
            endpoints = new Endpoints();
            policy.setEndpoints(endpoints);
        }
        // domain name
        final String domainName = getDomainName(request);
        if (domainName == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                    "Could not attach to load balancer. Service Instance domain name cannot be found. "));
        }
        // for now, degrade if endpoint already exists, another option is replace of existing endpoint
        for (Endpoint endpoint : definition.getPolicy().getEndpoints().getEndpoints()) {
            if (endpoint.getDomainName().equals(domainName)) {
                return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                        "Could not attach to load balancer. Endpoint with the same domain name already exists. "));
            }
        }
        Connection conn =
                _adapter.findDependentConnection(request.getDestConnections(), request.getDependentServiceInstance(),
                        "application/com.servicemesh.agility.api.ServiceInstance+xml");
        if (conn == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                    "Could not attach to load balancer. Endpoint connection cannot be found. "));
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setDomainName(domainName);
        endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
        endpoint.setType(_adapter.getEndpointType(request.getDependentServiceInstance()));
        endpoint.setWeight(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_WEIGHT, conn.getAssetProperties(), TrafficManagerConfig.WEIGHT_DEFAULT));
        if (endpoint.getType() == Type.TRAFFIC_MANAGER) {
            endpoint.setMinChildEndpoints(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                    TrafficManagerConfig.CONFIG_MIN_CHILD_ENDPOINTS, conn.getAssetProperties(),
                    TrafficManagerConfig.MIN_CHILD_ENDPOINTS_DEFAULT));
        }
        // Location Required when LoadBalancingMethod is set to Performance and Type is set to Any or TrafficManager
        if (policy.getLoadBalancingMethod() == LoadBalancingMethod.PERFORMANCE
                && (endpoint.getType() == Type.ANY || endpoint.getType() == Type.TRAFFIC_MANAGER)) {
            ServiceCategory category = _adapter.getCategory(request.getDependentServiceInstance(), request.getServiceProviders());
            if (category == TrafficManagerAdapter.ServiceCategory.NON_AZURE) {
                return Promise
                        .pure(degradeServiceInstance(request.getDependentServiceInstance(),
                                "Could not attach to load balancer. Performance load balancing method can only be used with Azure services. "));
            }
            String location =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_LOCATION, conn.getAssetProperties(), null);
            if (location == null) {
                return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                        "Could not attach to load balancer. Endpoint Location is required. "));
            }
            endpoint.setLocation(location);
        }
        endpoints.getEndpoints().add(endpoint);
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
                    return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(), message));
                }
                ServiceProviderResponse response = new ServiceProviderResponse();
                ServiceInstance instance = request.getDependentServiceInstance();
                _adapter.updateServiceInstanceAdded(instance, request.getServiceInstance());
                response.getModified().add(instance);
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

    private Promise<ServiceProviderResponse> removeEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final ServiceInstanceLifecycleRequest request)
    {
        final ServiceProviderResponse ignoreResponse = new ServiceProviderResponse();
        ignoreResponse.setStatus(Status.COMPLETE);
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        // ignore if no policy
        if (policy == null) {
            return Promise.pure(ignoreResponse);
        }
        Endpoints endpoints = policy.getEndpoints();
        if (endpoints != null) {
            // domain name
            final String domainName = getDomainName(request);
            if (domainName == null) {
                return Promise.pure(ignoreResponse);
            }
            for (Iterator<Endpoint> iter = endpoints.getEndpoints().iterator(); iter.hasNext();) {
                Endpoint endpoint = iter.next();
                if (endpoint.getDomainName() != null && endpoint.getDomainName().equals(domainName)) {
                    iter.remove();
                    final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
                    Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
                    return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                        public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                        {
                            if (arg.getStatusCode() == 200) {
                                ServiceProviderResponse response = new ServiceProviderResponse();
                                ServiceInstance instance = request.getDependentServiceInstance();
                                _adapter.updateServiceInstanceRemoved(instance, request.getServiceInstance());
                                response.getModified().add(instance);
                                response.setStatus(Status.COMPLETE);
                                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                                if (definition.getStatus() != null
                                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                                    return _adapter.resetProfileStatus(profileName, connection,
                                            com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                                }
                                return Promise.pure(response);
                            }
                            else {
                                // ignore
                                if (TrafficManagerServiceLifecycleOperations.logger.isDebugEnabled()) {
                                    TrafficManagerServiceLifecycleOperations.logger.debug("POST " + uri + ": "
                                            + ConnectionUtil.getStatusInfo(connection, arg));
                                }
                                return Promise.pure(ignoreResponse);
                            }
                        }
                    });
                }
            }
        }
        return Promise.pure(ignoreResponse);
    }

    private Promise<ServiceProviderResponse> enableEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final ServiceInstanceLifecycleRequest request)
    {
        ServiceProviderResponse informResponse = new ServiceProviderResponse();
        informResponse.setStatus(Status.COMPLETE);
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                    "Could not enable on load balancer. Load balancer policy is not found."));
        }
        Endpoints endpoints = policy.getEndpoints();
        Endpoint endpointModified = null;
        if (endpoints != null) {
            // domain name
            final String domainName = getDomainName(request);
            if (domainName == null) {
                return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                        "Could not enable on load balancer. Instance domain name is not found."));
            }
            for (Endpoint endpoint : endpoints.getEndpoints()) {
                if (endpoint.getDomainName() != null && endpoint.getDomainName().equals(domainName)) {
                    // enable endpoint
                    endpointModified = endpoint;
                    endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
                    String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
                    Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
                    return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                        public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                        {
                            if (arg.getStatusCode() != 200) {
                                String message =
                                        "Could not enable on load balancer. " + ConnectionUtil.getStatusInfo(connection, arg);
                                return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(), message));
                            }
                            ServiceProviderResponse response = new ServiceProviderResponse();
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
            }
        }
        if (endpointModified == null) {
            return Promise.pure(degradeServiceInstance(request.getDependentServiceInstance(),
                    "Could not enable on load balancer. Coud not find endpoint."));
        }
        else {
            return Promise.pure(informResponse);
        }
    }

    private Promise<ServiceProviderResponse> disableEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final ServiceInstanceLifecycleRequest request)
    {
        // ignore if not able to disable
        final ServiceProviderResponse ignoreResponse = new ServiceProviderResponse();
        ignoreResponse.setStatus(Status.COMPLETE);
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        // ignore if no policy, should not happen
        if (policy == null) {
            return Promise.pure(ignoreResponse);
        }
        Endpoints endpoints = policy.getEndpoints();
        if (endpoints != null) {
            // domain name
            final String domainName = getDomainName(request);
            if (domainName == null) {
                return Promise.pure(ignoreResponse);
            }
            for (Endpoint endpoint : endpoints.getEndpoints()) {
                if (endpoint.getDomainName() != null && endpoint.getDomainName().equals(domainName)) {
                    // disable endpoint
                    endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.DISABLED);
                    final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
                    Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
                    return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                        public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                        {
                            if (arg.getStatusCode() != 200) {
                                if (TrafficManagerServiceLifecycleOperations.logger.isDebugEnabled()) {
                                    TrafficManagerServiceLifecycleOperations.logger.debug("POST " + uri + ": "
                                            + ConnectionUtil.getStatusInfo(connection, arg));
                                }
                                return Promise.pure(ignoreResponse);
                            }
                            ServiceProviderResponse response = new ServiceProviderResponse();
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
            }
        }
        return Promise.pure(ignoreResponse);
    }
}
