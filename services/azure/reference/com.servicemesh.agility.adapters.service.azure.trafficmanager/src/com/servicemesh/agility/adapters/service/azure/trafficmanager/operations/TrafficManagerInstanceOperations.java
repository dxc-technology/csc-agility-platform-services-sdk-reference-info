/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.operations;

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
import com.microsoft.schemas.azure.trafficmgr.Profile;
import com.microsoft.schemas.azure.trafficmgr.StatusDetails;
import com.microsoft.schemas.azure.trafficmgr.Type;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter.InstanceCategory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerConfig;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.Constants;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.distributed.sync.AsyncLock;
import com.servicemesh.agility.sdk.service.msgs.InstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceResponse;
import com.servicemesh.core.async.Callback;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class TrafficManagerInstanceOperations extends com.servicemesh.agility.sdk.service.operations.InstanceOperations
{
    private static final Logger logger = Logger.getLogger(TrafficManagerInstanceOperations.class);

    private final TrafficManagerAdapter _adapter;
    private final ConnectionFactory _factory;

    public TrafficManagerInstanceOperations(TrafficManagerAdapter adapter, ConnectionFactory factory)
    {
        _adapter = adapter;
        _factory = factory;
    }

    @Override
    public Promise<InstanceResponse> preRestart(InstancePreRestartRequest request)
    {
        return doPreStop(request);
    }

    @Override
    public Promise<InstanceResponse> postRestart(InstancePostRestartRequest request)
    {
        return doPostStart(request);
    }

    public Promise<InstanceResponse> postProvision(final InstancePostProvisionRequest request)
    {
        if (request.getInstance() == null) {
            return Promise.pure(new Exception("Instance is not found"));
        }
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            return Promise.pure(degradeInstance(request.getInstance(),
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
        return lock.flatMap(new Function<AsyncLock, Promise<InstanceResponse>>() {
            public Promise<InstanceResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<InstanceResponse> flatPromise = promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                    public Promise<InstanceResponse> invoke(IHttpResponse arg)
                    {
                        if (arg.getStatusCode() != 200) {
                            if (TrafficManagerInstanceOperations.logger.isDebugEnabled()) {
                                TrafficManagerInstanceOperations.logger.debug("GET " + uri + ": "
                                        + ConnectionUtil.getStatusInfo(connection, arg));
                            }
                            return Promise.pure(degradeInstance(request.getInstance(),
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
                flatPromise.onComplete(new Callback<InstanceResponse>() {
                    public void invoke(InstanceResponse t)
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

    public Promise<InstanceResponse> preRelease(final InstancePreReleaseRequest request)
    {
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) { // ignore as we should not fail release because of this 
            InstanceResponse response = new InstanceResponse();
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
        if (request.getInstance() == null) {
            return Promise.pure(new Exception("Instance is not found"));
        }
        if (request.getTemplate() == null) {
            return Promise.pure(new Exception("Template is not found"));
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
        return lock.flatMap(new Function<AsyncLock, Promise<InstanceResponse>>() {
            public Promise<InstanceResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<InstanceResponse> flatPromise = promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                    public Promise<InstanceResponse> invoke(IHttpResponse arg)
                    {
                        // profile doesn't exist, ignore
                        if (arg.getStatusCode() != 200) {
                            if (TrafficManagerInstanceOperations.logger.isDebugEnabled()) {
                                TrafficManagerInstanceOperations.logger.debug("GET " + uri + ": "
                                        + ConnectionUtil.getStatusInfo(connection, arg));
                            }
                            InstanceResponse response = new InstanceResponse();
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
                flatPromise.onComplete(new Callback<InstanceResponse>() {
                    public void invoke(InstanceResponse t)
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

    public Promise<InstanceResponse> preStop(final InstancePreStopRequest request)
    {
        return doPreStop(request);
    }

    private Promise<InstanceResponse> doPreStop(final InstanceRequest request)
    {
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (request.getInstance() == null) {
            return Promise.pure(new Exception("Instance is not found"));
        }
        if (request.getTemplate() == null) {
            return Promise.pure(new Exception("Template is not found"));
        }
        if (profileName == null) { // ignore as we should not fail stop because of this 
            InstanceResponse response = new InstanceResponse();
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
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
        return lock.flatMap(new Function<AsyncLock, Promise<InstanceResponse>>() {
            public Promise<InstanceResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<InstanceResponse> flatPromise = promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                    public Promise<InstanceResponse> invoke(IHttpResponse arg)
                    {
                        // profile doesn't exist, ignore
                        if (arg.getStatusCode() != 200) {
                            if (TrafficManagerInstanceOperations.logger.isDebugEnabled()) {
                                TrafficManagerInstanceOperations.logger.debug("GET " + uri + ": "
                                        + ConnectionUtil.getStatusInfo(connection, arg));
                            }
                            InstanceResponse response = new InstanceResponse();
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
                flatPromise.onComplete(new Callback<InstanceResponse>() {
                    public void invoke(InstanceResponse t)
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

    public Promise<InstanceResponse> postStart(final InstancePostStartRequest request)
    {
        return doPostStart(request);
    }

    private Promise<InstanceResponse> doPostStart(final InstanceRequest request)
    {
        if (request.getInstance() == null) {
            return Promise.pure(new Exception("Instance is not found"));
        }
        if (request.getTemplate() == null) {
            return Promise.pure(new Exception("Template is not found"));
        }
        final String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            return Promise.pure(degradeInstance(request.getInstance(),
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
        return lock.flatMap(new Function<AsyncLock, Promise<InstanceResponse>>() {
            public Promise<InstanceResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<InstanceResponse> flatPromise = promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                    public Promise<InstanceResponse> invoke(IHttpResponse arg)
                    {
                        if (arg.getStatusCode() != 200) {
                            if (TrafficManagerInstanceOperations.logger.isDebugEnabled()) {
                                TrafficManagerInstanceOperations.logger.debug("GET " + uri + ": "
                                        + ConnectionUtil.getStatusInfo(connection, arg));
                            }
                            return Promise.pure(degradeInstance(request.getInstance(),
                                    "Could not enable on load balancer. Load balancer cannot be found. "));
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
                flatPromise.onComplete(new Callback<InstanceResponse>() {
                    public void invoke(InstanceResponse t)
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

    private Promise<InstanceResponse> addEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final InstanceRequest request)
    {
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeInstance(request.getInstance(),
                    "Could not attach to load balancer. Load balancer policy cannot be found. "));
        }
        Endpoints endpoints = policy.getEndpoints();
        if (endpoints == null) {
            endpoints = new Endpoints();
            policy.setEndpoints(endpoints);
        }
        // domain name
        try {
            _adapter.certifyCanonicalName(request.getInstance());
        }
        catch (Exception e) {
            return Promise.pure(degradeInstance(request.getInstance(),
                    "Could not attach to load balancer. Instance domain name cannot be found. "));
        }
        final String domainName = getDomainName(request);
        // update dependent with fqdn as well
        Instance instance =
                (Instance) _adapter.lookupAsset(request.getDependents(), request.getInstance().getId(), Instance.class);
        if (instance != null) {
            _adapter.saveCanonicalName(" via hostname", instance, domainName);
        }
        // for now, degrade if endpoint already exists, another option is replace of existing endpoint
        for (Endpoint endpoint : definition.getPolicy().getEndpoints().getEndpoints()) {
            if (endpoint.getDomainName().equals(domainName)) {
                return Promise.pure(degradeInstance(request.getInstance(),
                        "Could not attach to load balancer. Endpoint with the same domain name already exists. "));
            }
        }
        Connection conn =
                _adapter.findDependentConnection(request.getDestConnections(), request.getTemplate(),
                        "application/com.servicemesh.agility.api.Template+xml");
        if (conn == null) {
            return Promise.pure(degradeInstance(request.getInstance(),
                    "Could not attach to load balancer. Endpoint connection cannot be found. "));
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setDomainName(domainName);
        endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
        InstanceCategory category =
                _adapter.getCategory(connection.getEndpoint().getSubscription(), domainName, request.getInstance().getCloud(),
                        request.getClouds());
        if (category == InstanceCategory.SHARED_SUBSCRIPTION) {
            endpoint.setType(Type.CLOUD_SERVICE);
        }
        else {
            endpoint.setType(Type.ANY);
        }

        endpoint.setWeight(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_WEIGHT, conn.getAssetProperties(), TrafficManagerConfig.WEIGHT_DEFAULT));
        endpoints.getEndpoints().add(endpoint);
        // order if failover loadbalancing method
        if (policy.getLoadBalancingMethod() == LoadBalancingMethod.FAILOVER) {
            Collections.sort(endpoints.getEndpoints(), new TrafficManagerAdapter.EndpointOrderComparator(request.getDependents(),
                    request.getDestConnections(), _adapter));
        }
        else if (policy.getLoadBalancingMethod() == LoadBalancingMethod.PERFORMANCE && endpoint.getType() == Type.ANY) {
            // Traffic Manager requires the name of the Azure region. We will
            // only be able to identify a valid one for an instance running in
            // an alternate Azure subscription from the Traffic Manager. A
            // non-Azure instance will always fail here.
            if (category == TrafficManagerAdapter.InstanceCategory.NON_AZURE) {
                return Promise
                        .pure(degradeInstance(request.getInstance(),
                                "Could not attach to load balancer. Performance load balancing method can only be used with Azure instances. "));
            }
            endpoint.setLocation(_adapter.getLocation(category, conn, request.getInstance()));
            if (endpoint.getLocation() == null) {
                return Promise.pure(degradeInstance(request.getInstance(),
                        "Could not attach to load balancer. Location is required for Performance load balancing method. "));
            }
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
        Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
        return promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
            public Promise<InstanceResponse> invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() != 200) {
                    String message = "Could not attach to load balancer. " + ConnectionUtil.getStatusInfo(connection, arg);
                    return Promise.pure(degradeInstance(request.getInstance(), message));
                }
                InstanceResponse response = new InstanceResponse();
                Instance instance = request.getInstance();
                _adapter.updateInstanceAdded(instance, request.getServiceInstance());
                response.getModified().add(instance);
                response.setStatus(Status.COMPLETE);
                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                if (definition.getStatus() != null
                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                    return resetProfileStatus(profileName, connection, com.microsoft.schemas.azure.trafficmgr.Status.DISABLED,
                            response);
                }
                return Promise.pure(response);
            }
        });
    }

    private Promise<InstanceResponse> removeEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final InstanceRequest request)
    {
        final InstanceResponse ignoreResponse = new InstanceResponse();
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
                    return promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                        public Promise<InstanceResponse> invoke(IHttpResponse arg)
                        {
                            if (arg.getStatusCode() == 200) {
                                InstanceResponse response = new InstanceResponse();
                                Instance instance = request.getInstance();
                                _adapter.updateInstanceRemoved(instance, request.getServiceInstance());
                                response.getModified().add(instance);
                                response.setStatus(Status.COMPLETE);
                                // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                                if (definition.getStatus() != null
                                        && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                                    return resetProfileStatus(profileName, connection,
                                            com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                                }
                                return Promise.pure(response);
                            }
                            else {
                                // ignore
                                if (TrafficManagerInstanceOperations.logger.isDebugEnabled()) {
                                    TrafficManagerInstanceOperations.logger.debug("POST " + uri + ": "
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

    private Promise<InstanceResponse> enableEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final InstanceRequest request)
    {
        InstanceResponse informResponse = new InstanceResponse();
        informResponse.setStatus(Status.COMPLETE);
        // assumption that policy exists
        Policy policy = definition.getPolicy();
        if (policy == null) {
            return Promise.pure(degradeInstance(request.getInstance(),
                    "Could not enable on load balancer. Load balancer policy is not found."));
        }
        Endpoints endpoints = policy.getEndpoints();
        Endpoint endpointModified = null;
        if (endpoints != null) {
            // domain name
            final String domainName = getDomainName(request);
            if (domainName == null) {
                return Promise.pure(degradeInstance(request.getInstance(),
                        "Could not enable on load balancer. Instance domain name is not found."));
            }
            for (Endpoint endpoint : endpoints.getEndpoints()) {
                if (endpoint.getDomainName() != null && endpoint.getDomainName().equals(domainName)) {
                    // enable endpoint
                    endpointModified = endpoint;
                    endpoint.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
                    String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions";
                    Promise<IHttpResponse> promise = connection.post(uri, definition, IHttpResponse.class);
                    return promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                        public Promise<InstanceResponse> invoke(IHttpResponse arg)
                        {
                            if (arg.getStatusCode() != 200) {
                                String message =
                                        "Could not enable on load balancer. " + ConnectionUtil.getStatusInfo(connection, arg);
                                return Promise.pure(degradeInstance(request.getInstance(), message));
                            }
                            InstanceResponse response = new InstanceResponse();
                            response.setStatus(Status.COMPLETE);
                            // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                            if (definition.getStatus() != null
                                    && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                                return resetProfileStatus(profileName, connection,
                                        com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                            }
                            return Promise.pure(response);
                        }
                    });
                }
            }
        }
        if (endpointModified == null) {
            return Promise.pure(degradeInstance(request.getInstance(),
                    "Could not enable on load balancer. Coud not find endpoint."));
        }
        return Promise.pure(informResponse);
    }

    private Promise<InstanceResponse> disableEndpoint(final Definition definition, final String profileName,
            final AzureConnection connection, final InstanceRequest request)
    {
        // ignore if not able to disable
        final InstanceResponse ignoreResponse = new InstanceResponse();
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
                    return promise.flatMap(new Function<IHttpResponse, Promise<InstanceResponse>>() {
                        public Promise<InstanceResponse> invoke(IHttpResponse arg)
                        {
                            if (arg.getStatusCode() != 200) {
                                if (TrafficManagerInstanceOperations.logger.isDebugEnabled()) {
                                    TrafficManagerInstanceOperations.logger.debug("POST " + uri + ": "
                                            + ConnectionUtil.getStatusInfo(connection, arg));
                                }
                                return Promise.pure(ignoreResponse);
                            }
                            InstanceResponse response = new InstanceResponse();
                            response.setStatus(Status.COMPLETE);
                            // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                            if (definition.getStatus() != null
                                    && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                                return resetProfileStatus(profileName, connection,
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

    private String getDomainName(InstanceRequest request)
    {
        return _adapter.getDomainName(request.getInstance());
    }

    protected Promise<InstanceResponse> resetProfileStatus(final String profileName, final AzureConnection connection,
            final com.microsoft.schemas.azure.trafficmgr.Status status, final InstanceResponse delegateResponse)
    {
        final Profile profile = new Profile();
        profile.setStatus(status);
        StatusDetails details = new StatusDetails();
        details.setEnabledVersion(1);
        profile.setStatusDetails(details);
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName;
        Promise<IHttpResponse> promise = connection.put(uri, profile, IHttpResponse.class);
        return promise.map(new Function<IHttpResponse, InstanceResponse>() {
            public InstanceResponse invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() == 200) {
                    return delegateResponse;
                }
                else {
                    String message = "Not able to reset profile status. " + ConnectionUtil.getStatusInfo(connection, arg);
                    InstanceResponse response = new InstanceResponse();
                    response.setStatus(Status.FAILURE);
                    response.setMessage(message);
                    return response;
                }
            }
        });
    }

    private InstanceResponse degradeInstance(Instance instance, String degradeReason)
    {
        InstanceResponse response = new InstanceResponse();
        response.setStatus(Status.FAILURE);
        response.setMessage(degradeReason);
        return response;
    }
}
