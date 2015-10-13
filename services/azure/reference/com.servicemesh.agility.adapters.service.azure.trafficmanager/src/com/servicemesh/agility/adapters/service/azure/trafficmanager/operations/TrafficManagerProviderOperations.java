/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.microsoft.schemas.azure.trafficmgr.Definition;
import com.microsoft.schemas.azure.trafficmgr.Endpoint;
import com.microsoft.schemas.azure.trafficmgr.HttpOptions;
import com.microsoft.schemas.azure.trafficmgr.LoadBalancingMethod;
import com.microsoft.schemas.azure.trafficmgr.Monitor;
import com.microsoft.schemas.azure.trafficmgr.MonitorStatus;
import com.microsoft.schemas.azure.trafficmgr.Profile;
import com.microsoft.schemas.azure.trafficmgr.Profiles;
import com.microsoft.schemas.azure.trafficmgr.Protocol;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.core.azure.AzureConnectionFactory;
import com.servicemesh.agility.adapters.core.azure.Config;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerConfig;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.Constants;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.Credential;
import com.servicemesh.agility.api.ImportMode;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPingRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreCreateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderPreUpdateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderSyncRequest;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class TrafficManagerProviderOperations extends com.servicemesh.agility.sdk.service.operations.ServiceProviderOperations
{
    private final TrafficManagerAdapter _adapter;
    private final ConnectionFactory _factory;

    private static final Logger logger = Logger.getLogger(TrafficManagerProviderOperations.class);

    public TrafficManagerProviderOperations(TrafficManagerAdapter adapter, ConnectionFactory factory)
    {
        _adapter = adapter;
        _factory = factory;
    }

    public Promise<ServiceProviderResponse> sync(final ServiceProviderSyncRequest request)
    {
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles";
        Promise<Profiles> promise = connection.get(uri, null, Profiles.class);
        return promise.flatMap(new Function<Profiles, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(Profiles arg)
            {
                List<Promise<ServiceProviderResponse>> sequence = new ArrayList<Promise<ServiceProviderResponse>>();
                for (Profile profile : arg.getProfiles()) {
                    sequence.add(syncProfile(profile, connection, request));
                }

                Promise<List<ServiceProviderResponse>> promiseSequence = Promise.sequence(sequence);
                return promiseSequence.map(new Function<List<ServiceProviderResponse>, ServiceProviderResponse>() {
                    public ServiceProviderResponse invoke(List<ServiceProviderResponse> arg)
                    {
                        ServiceProviderResponse response = new ServiceProviderResponse();
                        response.setStatus(Status.COMPLETE);
                        for (ServiceProviderResponse res : arg) {
                            if (res.getStatus() == Status.COMPLETE) {
                                for (Asset asset : res.getModified()) {
                                    if (!response.getModified().contains(asset)) {
                                        response.getModified().add(asset);
                                    }
                                }
                            }
                        }
                        return response;
                    }
                });
            }
        });
    }

    private Promise<ServiceProviderResponse> syncProfile(final Profile profile, final AzureConnection connection,
            final ServiceProviderSyncRequest request)
    {
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profile.getName() + "/definitions/1";
        TrafficManagerProviderOperations.logger.debug("Synchronizing profile " + profile.getName());
        Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
        return promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() != 200) {
                    if (TrafficManagerProviderOperations.logger.isDebugEnabled()) {
                        TrafficManagerProviderOperations.logger.debug("GET " + uri + ": "
                                + ConnectionUtil.getStatusInfo(connection, arg));
                    }
                    ServiceProviderResponse ignore = new ServiceProviderResponse();
                    ignore.setStatus(Status.FAILURE);
                    return Promise.pure(ignore);
                }
                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                ServiceInstance serviceInstance = getServiceInstance(request, profile.getName());
                // not known to agility, ignore as we are not onboarding here
                if (serviceInstance == null) {
                    ServiceProviderResponse ignore = new ServiceProviderResponse();
                    ignore.setStatus(Status.COMPLETE);
                    return Promise.pure(ignore);
                }
                ServiceProviderResponse resp = new ServiceProviderResponse();
                resp.setStatus(Status.COMPLETE);
                // sync service instance
                if (syncServiceInstance(profile, definition, serviceInstance)) {
                    resp.getModified().add(serviceInstance);
                }
                // sync connections
                List<Asset> dependents = getDependentsForServiceInstance(request, serviceInstance);
                Map<Endpoint, Connection> endpointMap = new HashMap<Endpoint, Connection>();
                if (definition.getPolicy() != null && definition.getPolicy().getEndpoints() != null) {
                    for (Endpoint endpoint : definition.getPolicy().getEndpoints().getEndpoints()) {
                        // match up by domain name
                        Connection connection = _adapter.findDependentConnection(endpoint, dependents, request.getConnections());
                        // ignore, possibly not known to agility
                        if (connection == null) {
                            continue;
                        }
                        endpointMap.put(endpoint, connection);
                        if (syncConnection(endpoint, connection)) {
                            resp.getModified().add(connection);
                        }
                    }
                }
                if (syncEndpointsOrder(definition, endpointMap)) {
                    for (Connection connection : endpointMap.values()) {
                        if (!resp.getModified().contains(connection)) {
                            resp.getModified().add(connection);
                        }
                    }
                }
                return Promise.pure(resp);
            }
        });
    }

    private boolean syncEndpointsOrder(Definition definition, Map<Endpoint, Connection> endpointMap)
    {
        boolean changed = false;
        if (definition.getPolicy().getLoadBalancingMethod() == LoadBalancingMethod.FAILOVER
                && definition.getPolicy().getEndpoints() != null
                && definition.getPolicy().getEndpoints().getEndpoints().size() > 1 && endpointMap.values().size() > 1) {
            List<Connection> connectionOrder = new ArrayList<Connection>(endpointMap.values());
            // skip ordering if not the same size
            if (connectionOrder.size() != definition.getPolicy().getEndpoints().getEndpoints().size()) {
                return changed;
            }
            Collections.sort(connectionOrder, new TrafficManagerAdapter.ConnectionOrderComparator());
            // compare connection and endpoint order and reorder if necessary
            boolean needsReorder = false;
            for (int index = 0; index < definition.getPolicy().getEndpoints().getEndpoints().size(); index++) {
                Endpoint endpoint = definition.getPolicy().getEndpoints().getEndpoints().get(index);
                Connection connection = connectionOrder.get(index);
                if (connection != endpointMap.get(endpoint)) {
                    needsReorder = true;
                    break;
                }
            }
            if (needsReorder) {
                for (int index = 0; index < definition.getPolicy().getEndpoints().getEndpoints().size(); index++) {
                    Endpoint endpoint = definition.getPolicy().getEndpoints().getEndpoints().get(index);
                    Connection connection = endpointMap.get(endpoint);
                    _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_ORDER, index + 1,
                            connection.getAssetProperties());
                }
                changed = true;
            }
        }
        return changed;
    }

    private boolean syncServiceInstance(Profile profile, Definition definition, ServiceInstance serviceInstance)
    {
        boolean changed = false;
        MonitorStatus monitorStatus =
                definition.getPolicy() != null && definition.getPolicy().getMonitorStatus() != null ? definition.getPolicy()
                        .getMonitorStatus() : null;
        ServiceState serviceState = null;
        if (profile.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
            serviceState = ServiceState.STOPPED;
        }
        else if (profile.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.ENABLED) {
            serviceState = ServiceState.RUNNING;
        }
        if (monitorStatus != null) {
            AssetProperty propExisting =
                    Config.getAssetProperty(TrafficManagerConfig.POLICY_PROP_MONITOR_STATUS_NAME,
                            serviceInstance.getConfigurations());
            if (propExisting == null || !propExisting.getStringValue().equals(monitorStatus.value())) {
                _adapter.addOrUpdateAssetProperty(TrafficManagerConfig.POLICY_PROP_MONITOR_STATUS_NAME, monitorStatus.value(),
                        serviceInstance.getConfigurations());
                changed = true;
            }
        }
        if (serviceState != serviceInstance.getState()) {
            serviceInstance.setState(serviceState);
            changed = true;
        }
        int ttlSeconds =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                        TrafficManagerConfig.CONFIG_TTL_NAME, serviceInstance.getAssetProperties(), -1);
        if (definition.getDnsOptions() != null
                && (ttlSeconds == -1 || definition.getDnsOptions().getTimeToLiveInSeconds() != ttlSeconds)) {
            _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_TTL_NAME, definition.getDnsOptions()
                    .getTimeToLiveInSeconds(), serviceInstance.getAssetProperties());
            changed = true;
        }
        if (definition.getMonitors() != null && definition.getMonitors().getMonitor() != null) {
            Monitor monitor = definition.getMonitors().getMonitor();
            int monitorIntervalSeconds =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                            TrafficManagerConfig.CONFIG_MONITOR_INTERVAL, serviceInstance.getAssetProperties(), -1);
            if (monitorIntervalSeconds == -1 || monitor.getIntervalInSeconds() != monitorIntervalSeconds) {
                _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_MONITOR_INTERVAL,
                        monitor.getIntervalInSeconds(), serviceInstance.getAssetProperties());
                changed = true;
            }
            int monitorTimeoutSeconds =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                            TrafficManagerConfig.CONFIG_MONITOR_TIMEOUT, serviceInstance.getAssetProperties(), -1);
            if (monitorTimeoutSeconds == -1 || monitor.getTimeoutInSeconds() != monitorTimeoutSeconds) {
                _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_MONITOR_TIMEOUT,
                        monitor.getTimeoutInSeconds(), serviceInstance.getAssetProperties());
                changed = true;
            }
            int toleratedNumberOfFailures =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                            TrafficManagerConfig.CONFIG_MONITOR_RETRIES, serviceInstance.getAssetProperties(), -1);
            if (toleratedNumberOfFailures == -1 || monitor.getToleratedNumberOfFailures() != toleratedNumberOfFailures) {
                _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_MONITOR_RETRIES,
                        monitor.getToleratedNumberOfFailures(), serviceInstance.getAssetProperties());
                changed = true;
            }
            String monitorProtocol =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_MONITOR_PROTOCOL, serviceInstance.getAssetProperties());
            if (monitorProtocol == null || Protocol.fromValue(monitorProtocol) != monitor.getProtocol()) {
                _adapter.addOrUpdateAssetProperty(TrafficManagerConfig.CONFIG_MONITOR_PROTOCOL, monitor.getProtocol().value(),
                        serviceInstance.getAssetProperties());
                changed = true;
            }
            int monitorPort =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                            TrafficManagerConfig.CONFIG_MONITOR_PORT, serviceInstance.getAssetProperties(), -1);
            if (monitorPort == -1 || monitor.getPort() != monitorPort) {
                _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_MONITOR_PORT, monitor.getPort(),
                        serviceInstance.getAssetProperties());
                changed = true;
            }
            HttpOptions httpOptions = monitor.getHttpOptions();
            if (httpOptions != null) {
                int statusCode =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                                TrafficManagerConfig.CONFIG_MONITOR_STATUS_CODE, serviceInstance.getAssetProperties(), -1);
                if (statusCode == -1 || httpOptions.getExpectedStatusCode() != statusCode) {
                    _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_MONITOR_STATUS_CODE,
                            httpOptions.getExpectedStatusCode(), serviceInstance.getAssetProperties());
                    changed = true;
                }
            }
            String relativePath =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_MONITOR_RELATIVE_PATH, serviceInstance.getAssetProperties());
            if (relativePath == null || !relativePath.equals(httpOptions.getRelativePath())) {
                _adapter.addOrUpdateAssetProperty(TrafficManagerConfig.CONFIG_MONITOR_RELATIVE_PATH,
                        httpOptions.getRelativePath(), serviceInstance.getAssetProperties());
                changed = true;
            }
            String verb =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_MONITOR_VERB, serviceInstance.getAssetProperties());
            if (verb == null || !verb.equals(httpOptions.getVerb())) {
                _adapter.addOrUpdateAssetProperty(TrafficManagerConfig.CONFIG_MONITOR_VERB, httpOptions.getVerb(),
                        serviceInstance.getAssetProperties());
                changed = true;
            }
        }
        String loadBalancing =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_LBM_NAME,
                        serviceInstance.getAssetProperties());
        if (loadBalancing == null
                || LoadBalancingMethod.fromValue(loadBalancing) != definition.getPolicy().getLoadBalancingMethod()) {
            _adapter.addOrUpdateAssetProperty(TrafficManagerConfig.CONFIG_LBM_NAME, definition.getPolicy()
                    .getLoadBalancingMethod().value(), serviceInstance.getAssetProperties());
            changed = true;
        }
        return changed;
    }

    private boolean syncConnection(Endpoint endpoint, Connection connection)
    {
        boolean changed = false;
        int weight =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_WEIGHT,
                        connection.getAssetProperties(), -1);
        if (weight == -1 && endpoint.getWeight() != null || endpoint.getWeight() != null
                && endpoint.getWeight().intValue() != weight) {
            _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_WEIGHT, endpoint.getWeight().intValue(),
                    connection.getAssetProperties());
            changed = true;
        }
        String location =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_LOCATION,
                        connection.getAssetProperties());
        if (location == null && endpoint.getLocation() != null || location != null && !location.equals(endpoint.getLocation())) {
            _adapter.addOrUpdateAssetProperty(TrafficManagerConfig.CONFIG_LOCATION, endpoint.getLocation(),
                    connection.getAssetProperties());
            changed = true;
        }
        int minChildEndpoints =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                        TrafficManagerConfig.CONFIG_MIN_CHILD_ENDPOINTS, connection.getAssetProperties(), -1);
        if (minChildEndpoints == -1 && endpoint.getMinChildEndpoints() != null || endpoint.getMinChildEndpoints() != null
                && endpoint.getMinChildEndpoints().intValue() != minChildEndpoints) {
            _adapter.addOrUpdateAssetPropertyAsInteger(TrafficManagerConfig.CONFIG_MIN_CHILD_ENDPOINTS, endpoint
                    .getMinChildEndpoints().intValue(), connection.getAssetProperties());
            changed = true;
        }
        return changed;
    }

    private ServiceInstance getServiceInstance(ServiceProviderSyncRequest request, String profileName)
    {
        for (ServiceInstance service : request.getServiceInstances()) {
            String profileNameService =
                    com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                            TrafficManagerConfig.CONFIG_PROFILE_NAME, service.getAssetProperties());
            if (profileNameService != null && profileNameService.equals(profileName)) {
                return service;
            }
        }
        return null;
    }

    private List<Asset> getDependentsForServiceInstance(ServiceProviderSyncRequest request, ServiceInstance serviceInstance)
    {
        List<Asset> dependents = new ArrayList<Asset>();
        for (Link depLink : serviceInstance.getDependents()) {
            Asset dependent = _adapter.findAsset(request.getConnectedAssets(), depLink);
            if (dependent != null) {
                dependents.add(dependent);
            }
            // include instances
            if (dependent instanceof Template) {
                dependents.addAll(_adapter.findInstanceAssets(request.getConnectedAssets(), (Template) dependent));
            }
        }
        return dependents;
    }

    public Promise<ServiceProviderResponse> ping(ServiceProviderPingRequest request)
    {
        AzureConnection connection = null;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles";
        Promise<Profiles> promise = connection.get(uri, null, Profiles.class);
        return promise.map(new Function<Profiles, ServiceProviderResponse>() {
            public ServiceProviderResponse invoke(Profiles arg)
            {
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.setStatus(Status.COMPLETE);
                return response;
            }
        });
    }

    public Promise<ServiceProviderResponse> preCreate(ServiceProviderPreCreateRequest request)
    {
        // credentials and subscription are required if cloud not specified
        ServiceProvider provider = request.getProvider();
        Credential cred = AzureConnectionFactory.getCredentials(provider, request.getClouds());
        if (cred == null) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.FAILURE);
            response.setMessage("Not able to create service provider. Service provider requires credentials");
            return Promise.pure(response);
        }
        // validate subscription
        String subscription = AzureConnectionFactory.getSubscription(provider, request.getClouds());
        if (subscription == null) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.FAILURE);
            response.setMessage("Not able to create service provider. Service provider requires subscription");
            return Promise.pure(response);
        }
        ServiceProviderResponse response = new ServiceProviderResponse();
        response.setStatus(Status.COMPLETE);
        return Promise.pure(response);
    }

    public Promise<ServiceProviderResponse> preUpdate(ServiceProviderPreUpdateRequest request)
    {
        // credentials and subscription are required if cloud not specified
        ServiceProvider provider = request.getProvider();
        Credential cred = AzureConnectionFactory.getCredentials(provider, request.getClouds());
        if (cred == null) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.FAILURE);
            response.setMessage("Not able to update service provider. Service provider requires credentials");
            return Promise.pure(response);
        }
        // validate subscription
        String subscription = AzureConnectionFactory.getSubscription(provider, request.getClouds());
        if (subscription == null) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.FAILURE);
            response.setMessage("Not able to update service provider. Service provider requires subscription");
            return Promise.pure(response);
        }
        ServiceProviderResponse response = new ServiceProviderResponse();
        response.setStatus(Status.COMPLETE);
        return Promise.pure(response);
    }
}
