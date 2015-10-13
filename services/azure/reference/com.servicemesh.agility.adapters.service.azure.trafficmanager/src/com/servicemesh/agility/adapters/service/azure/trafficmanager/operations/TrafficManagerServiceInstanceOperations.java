/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.microsoft.schemas.azure.trafficmgr.AvailabilityResponse;
import com.microsoft.schemas.azure.trafficmgr.Definition;
import com.microsoft.schemas.azure.trafficmgr.DnsOptions;
import com.microsoft.schemas.azure.trafficmgr.Endpoints;
import com.microsoft.schemas.azure.trafficmgr.HttpOptions;
import com.microsoft.schemas.azure.trafficmgr.LoadBalancingMethod;
import com.microsoft.schemas.azure.trafficmgr.Monitor;
import com.microsoft.schemas.azure.trafficmgr.Monitors;
import com.microsoft.schemas.azure.trafficmgr.Policy;
import com.microsoft.schemas.azure.trafficmgr.Profile;
import com.microsoft.schemas.azure.trafficmgr.Profiles;
import com.microsoft.schemas.azure.trafficmgr.Protocol;
import com.microsoft.schemas.azure.trafficmgr.Result;
import com.microsoft.schemas.azure.trafficmgr.StatusDetails;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerAdapter;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.TrafficManagerConfig;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.ConnectionUtil;
import com.servicemesh.agility.adapters.service.azure.trafficmanager.connection.Constants;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.distributed.sync.AsyncLock;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceValidateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ValidateMode;
import com.servicemesh.core.async.Callback;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class TrafficManagerServiceInstanceOperations extends
        com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations
{
    private static final Logger logger = Logger.getLogger(TrafficManagerServiceInstanceOperations.class);

    private final TrafficManagerAdapter _adapter;
    private final ConnectionFactory _factory;

    public TrafficManagerServiceInstanceOperations(TrafficManagerAdapter adapter, ConnectionFactory factory)
    {
        _adapter = adapter;
        _factory = factory;
    }

    public Promise<ServiceProviderResponse> validate(final ServiceInstanceValidateRequest request)
    {
        if (request.getServiceInstance() == null) {
            return Promise.pure(new Exception("Service Instance not provided."));
        }
        // optimization: nothing to validate for dependent service change
        if (request.getDependentServiceInstance() != null) {
            return super.validate(request);
        }
        // validate on update when unprovisioned
        if (request.getMode() != null && request.getMode() == ValidateMode.CREATE) {
            return doValidate(request);
        }
        else if (request.getMode() != null && request.getMode() == ValidateMode.UPDATE) {
            // profile name or domain name cannot be changed once provisioned
            if (request.getOriginalServiceInstance() == null) {
                return Promise.pure(new Exception("Original Service Instance not provided."));
            }
            if (request.getOriginalServiceInstance().getState() != ServiceState.UNPROVISIONED) {
                String origProfileName =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getOriginalServiceInstance()
                                        .getAssetProperties());
                String origDomainName =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                TrafficManagerConfig.CONFIG_DNS_NAME, request.getOriginalServiceInstance().getAssetProperties());
                String domainName =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                TrafficManagerConfig.CONFIG_DNS_NAME, request.getServiceInstance().getAssetProperties());
                String profileName =
                        com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                                TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
                if (profileName == null) {
                    profileName = profileNameFromDomainName(domainName);
                }
                if (origProfileName == null) {
                    origProfileName = profileNameFromDomainName(origDomainName);
                }
                if (!origDomainName.equals(domainName)) {
                    return Promise.pure(new Exception("Provisioned Service Instance domain name cannot be changed."));
                }
                if (!origProfileName.equals(profileName)) {
                    return Promise.pure(new Exception("Provisioned Service Instance profile name cannot be changed."));
                }
            }
            else {
                // regular validation when updating unprovisioned service instance
                return doValidate(request);
            }
        }
        return super.validate(request);
    }

    public Promise<ServiceProviderResponse> provision(final ServiceInstanceProvisionRequest request)
    {
        // create profile
        String domainName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME,
                        request.getServiceInstance().getAssetProperties());
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        //  if not specified, use Domain Name replacing dots with hyphens.
        ServiceInstance serviceInstance = request.getServiceInstance();
        boolean profileModified = false;
        if (profileName == null) {
            profileName = profileNameFromDomainName(domainName);
            AssetProperty prop = new AssetProperty();
            prop.setName(TrafficManagerConfig.CONFIG_PROFILE_NAME);
            prop.setStringValue(profileName);
            serviceInstance.getAssetProperties().add(prop);
            profileModified = true;
        }
        final boolean serviceModified = profileModified;
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles";
        final Profile profile = new Profile();
        profile.setDomainName(domainName);
        profile.setName(profileName);
        final String profileNameFinal = profileName;
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return getFailed(request, "Unable to get connection. " + ex.getMessage());
        }
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileNameFinal + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.post(uri, profile, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() == 200) {
                                    return createDefinition(new Definition(), profileNameFinal, connection, request,
                                            serviceModified);
                                }
                                else {
                                    String message =
                                            "Not able to create profile. " + ConnectionUtil.getStatusInfo(connection, arg);
                                    return getFailed(request, message);
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

    public Promise<ServiceProviderResponse> release(final ServiceInstanceReleaseRequest request)
    {
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        String domainName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME,
                        request.getServiceInstance().getAssetProperties());
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            profileName = profileNameFromDomainName(domainName);
        }
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName;
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                // ignore if definition not there
                Promise<IHttpResponse> promise = connection.delete(uri);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.map(new Function<IHttpResponse, ServiceProviderResponse>() {
                            public ServiceProviderResponse invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() == 200) {
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    // clean up any dependents
                                    for (Asset asset : request.getDependents()) {
                                        if (asset instanceof Instance) {
                                            _adapter.updateInstanceRemoved((Instance) asset, request.getServiceInstance());
                                            response.getModified().add(asset);
                                        }
                                        else if (asset instanceof ServiceInstance) {
                                            _adapter.updateServiceInstanceRemoved((ServiceInstance) asset,
                                                    request.getServiceInstance());
                                            response.getModified().add(asset);
                                        }
                                    }
                                    response.setStatus(Status.COMPLETE);
                                    return response;
                                }
                                else {
                                    // mark as destroyed if delete failed
                                    if (TrafficManagerServiceInstanceOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceInstanceOperations.logger.debug("DELETE " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.COMPLETE);
                                    return response;
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

    @Override
    public Promise<ServiceProviderResponse> start(ServiceInstanceStartRequest request)
    {
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            return Promise.pure(new Exception("Profile name is not found"));
        }
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName;
        final Profile profile = new Profile();
        profile.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.ENABLED);
        StatusDetails details = new StatusDetails();
        details.setEnabledVersion(1);
        profile.setStatusDetails(details);
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                Promise<IHttpResponse> promise = connection.put(uri, profile, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.map(new Function<IHttpResponse, ServiceProviderResponse>() {
                            public ServiceProviderResponse invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() == 200) {
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.COMPLETE);
                                    return response;
                                }
                                else {
                                    // mark as destroyed if update failed
                                    if (TrafficManagerServiceInstanceOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceInstanceOperations.logger.debug("PUT " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.FAILURE);
                                    String message =
                                            "Not able to enable profile. "
                                                    + (arg.getStatus() != null ? arg.getStatus().getReason() : "");
                                    response.setMessage(message);
                                    return response;
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

    @Override
    public Promise<ServiceProviderResponse> stop(ServiceInstanceStopRequest request)
    {
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName == null) {
            return Promise.pure(new Exception("Profile name is not found"));
        }
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName;
        final Profile profile = new Profile();
        profile.setStatus(com.microsoft.schemas.azure.trafficmgr.Status.DISABLED);
        StatusDetails details = new StatusDetails();
        details.setEnabledVersion(1);
        profile.setStatusDetails(details);
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                Promise<IHttpResponse> promise = connection.put(uri, profile, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.map(new Function<IHttpResponse, ServiceProviderResponse>() {
                            public ServiceProviderResponse invoke(IHttpResponse arg)
                            {
                                if (arg.getStatusCode() == 200) {
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.COMPLETE);
                                    return response;
                                }
                                else {
                                    // mark as destroyed if update failed
                                    if (TrafficManagerServiceInstanceOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceInstanceOperations.logger.debug("PUT " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.FAILURE);
                                    String message =
                                            "Not able to disable profile. "
                                                    + (arg.getStatus() != null ? arg.getStatus().getReason() : "");
                                    response.setMessage(message);
                                    return response;
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

    @Override
    public Promise<ServiceProviderResponse> reconfigure(final ServiceInstanceReconfigureRequest request)
    {
        // optimization: nothing to reconfigure for dependent service change
        if (request.getDependentServiceInstance() != null) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
        // if not provisioned, we are done
        if (request.getServiceInstance().getState() == ServiceState.UNPROVISIONED) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
        final AzureConnection connection;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        String domainName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME,
                        request.getServiceInstance().getAssetProperties());
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        //  if not specified, use Domain Name replacing dots with hyphens.
        final ServiceInstance serviceInstance = request.getServiceInstance();
        boolean profileModified = false;
        if (profileName == null) {
            profileName = profileNameFromDomainName(domainName);
        }
        final boolean serviceModified = profileModified;
        final String profileNameFinal = profileName;
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName + "/definitions/1";
        // lock at profile name so multiple requests don't modify service at the same time to avoid discrepancies 
        Promise<AsyncLock> lock = AsyncLock.lock("/agility/trafficmanager/profile/" + profileName + "/lock");
        return lock.flatMap(new Function<AsyncLock, Promise<ServiceProviderResponse>>() {
            public Promise<ServiceProviderResponse> invoke(final AsyncLock arg)
            {
                Promise<IHttpResponse> promise = connection.get(uri, null, IHttpResponse.class);
                Promise<ServiceProviderResponse> flatPromise =
                        promise.flatMap(new Function<IHttpResponse, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(IHttpResponse arg)
                            {
                                // we might be out of sync and service is no longer provisioned, just ignore
                                if (arg.getStatus().getStatusCode() != 200) {
                                    if (TrafficManagerServiceInstanceOperations.logger.isDebugEnabled()) {
                                        TrafficManagerServiceInstanceOperations.logger.debug("GET " + uri + ": "
                                                + ConnectionUtil.getStatusInfo(connection, arg));
                                    }
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setStatus(Status.COMPLETE);
                                    return Promise.pure(response);
                                }
                                Definition definition = connection.getEndpoint().decode(arg, Definition.class);
                                return createDefinition(definition, profileNameFinal, connection, request, serviceModified);
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

    private Promise<ServiceProviderResponse> createDefinition(final Definition definition, final String profileName,
            final AzureConnection connection, final ServiceInstanceRequest request, final boolean serviceModified)
    {
        DnsOptions dnsOpts = new DnsOptions();
        dnsOpts.setTimeToLiveInSeconds(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_TTL_NAME, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.DNS_TTL_DEFAULT));
        definition.setDnsOptions(dnsOpts);

        Monitor monitor = new Monitor();
        monitor.setIntervalInSeconds(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_MONITOR_INTERVAL, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.MONITOR_POLL_INTERVAL_DEFAULT));
        monitor.setTimeoutInSeconds(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_MONITOR_TIMEOUT, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.MONITOR_POLL_TIMEOUT_DEFAULT));
        monitor.setToleratedNumberOfFailures(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_MONITOR_RETRIES, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.MONITOR_POLL_RETRY_DEFAULT));
        monitor.setProtocol(Protocol.fromValue(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                TrafficManagerConfig.CONFIG_MONITOR_PROTOCOL, request.getServiceInstance().getAssetProperties())));
        monitor.setPort(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_MONITOR_PORT, request.getServiceInstance().getAssetProperties()));

        HttpOptions httpOpts = new HttpOptions();
        httpOpts.setExpectedStatusCode(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsInteger(
                TrafficManagerConfig.CONFIG_MONITOR_STATUS_CODE, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.MONITOR_STATUS_CODE_DEFAULT));
        httpOpts.setRelativePath(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                TrafficManagerConfig.CONFIG_MONITOR_RELATIVE_PATH, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.MONITOR_RELATIVE_PATH_DEFAULT));
        httpOpts.setVerb(com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                TrafficManagerConfig.CONFIG_MONITOR_VERB, request.getServiceInstance().getAssetProperties(),
                TrafficManagerConfig.MONITOR_VERB_DEFAULT));
        monitor.setHttpOptions(httpOpts);

        Monitors monitors = new Monitors();
        monitors.setMonitor(monitor);
        definition.setMonitors(monitors);

        Policy policy = definition.getPolicy();
        if (policy == null) {
            policy = new Policy();
            policy.setEndpoints(new Endpoints());
        }
        policy.setLoadBalancingMethod(LoadBalancingMethod.fromValue(com.servicemesh.agility.adapters.core.azure.Config
                .getAssetPropertyAsString(TrafficManagerConfig.CONFIG_LBM_NAME, request.getServiceInstance().getAssetProperties())));
        definition.setPolicy(policy);
        // order for failover
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
                if (arg.getStatusCode() == 200) {
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.COMPLETE);
                    if (serviceModified) {
                        response.getModified().add(request.getServiceInstance());
                    }
                    // azure will auto enable profile with any change to definition, this is to make sure to reset profile back to disabled if it is disabled
                    if (definition.getStatus() != null
                            && definition.getStatus() == com.microsoft.schemas.azure.trafficmgr.Status.DISABLED) {
                        return resetProfileStatus(profileName, connection,
                                com.microsoft.schemas.azure.trafficmgr.Status.DISABLED, response);
                    }
                    return Promise.pure(response);
                }
                else {
                    String message = "Not able to create definition. " + ConnectionUtil.getStatusInfo(connection, arg);
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.FAILURE);
                    response.setMessage(message);
                    request.getServiceInstance().setState(ServiceState.FAILED);
                    _adapter.addOrUpdateAssetProperty("degraded-reason", message, request.getServiceInstance()
                            .getConfigurations());
                    response.getModified().add(request.getServiceInstance());
                    return Promise.pure(response);
                }
            }
        });
    }

    private Promise<ServiceProviderResponse> validateProfileNameAvailable(final String profileName,
            final AzureConnection connection, final ServiceInstanceRequest request)
    {
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles";
        Promise<Profiles> promise = connection.get(uri, null, Profiles.class);
        return promise.map(new Function<Profiles, ServiceProviderResponse>() {
            public ServiceProviderResponse invoke(Profiles arg)
            {
                Profile found = null;
                for (Profile profile : arg.getProfiles()) {
                    if (profile.getName().equals(profileName)) {
                        found = profile;
                        break;
                    }
                }
                if (found == null) {
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.COMPLETE);
                    return response;
                }
                else {
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.FAILURE);
                    response.setMessage("Profile name " + profileName + " already exists.");
                    return response;
                }
            }
        });
    }

    private Promise<ServiceProviderResponse> validateDomainNameAvailable(final String domainName,
            final AzureConnection connection, final ServiceInstanceRequest request)
    {
        String uri = Constants.TRAFFIC_MGR_BASE_URI + "/operations/isavailable/" + domainName;
        Promise<AvailabilityResponse> promise = connection.get(uri, null, AvailabilityResponse.class);
        return promise.map(new Function<AvailabilityResponse, ServiceProviderResponse>() {
            public ServiceProviderResponse invoke(AvailabilityResponse arg)
            {
                if (arg.getResult() == Result.TRUE) {
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.COMPLETE);
                    return response;
                }
                else {
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.FAILURE);
                    response.setMessage("Domain name invalid. Domain name is not available.");
                    return response;
                }
            }
        });
    }

    private Promise<ServiceProviderResponse> getFailed(ServiceInstanceRequest request, String message)
    {
        ServiceProviderResponse response = new ServiceProviderResponse();
        response.setStatus(Status.FAILURE);
        response.setMessage(message);
        return Promise.pure(response);
    }

    private String profileNameFromDomainName(String domainName)
    {
        return domainName.replace('.', '-');
    }

    private Promise<ServiceProviderResponse> doValidate(final ServiceInstanceValidateRequest request)
    {
        // on create, must not begin or end with hyphens
        String profileName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(
                        TrafficManagerConfig.CONFIG_PROFILE_NAME, request.getServiceInstance().getAssetProperties());
        if (profileName != null && (profileName.endsWith("-") || profileName.startsWith("-"))) {
            return Promise.pure(new Exception("Profile name invalid. Hyphens cannot be the first or last character "));
        }
        // A valid DNS name of the form <subdomain name>.trafficmanager.net
        String domainName =
                com.servicemesh.agility.adapters.core.azure.Config.getAssetPropertyAsString(TrafficManagerConfig.CONFIG_DNS_NAME,
                        request.getServiceInstance().getAssetProperties());
        if (!domainName.endsWith("trafficmanager.net")) {
            return Promise.pure(new Exception("Domain name invalid. Domain name must end with trafficmanager.net "));
        }
        AzureConnection connection = null;
        try {
            connection = _factory.getConnection(request);
        }
        catch (Exception ex) {
            return Promise.pure(ex);
        }
        // location is required for traffic manager or any type endpoints for performance load balancing
        for (Asset dep : request.getDependents()) {
            Connection conn = null;
            if (dep instanceof ServiceInstance) {
                conn =
                        _adapter.findDependentConnection(request.getDestConnections(), dep,
                                "application/com.servicemesh.agility.api.ServiceInstance+xml");
            }
            else if (dep instanceof Template) {
                conn =
                        _adapter.findDependentConnection(request.getDestConnections(), dep,
                                "application/com.servicemesh.agility.api.Template+xml");
            }
            try {
                _adapter.validateLocation(connection, request.getServiceInstance(), dep, conn, request.getClouds(),
                        request.getServiceProviders());
            }
            catch (Exception ex) {
                return Promise.pure(ex);
            }
        }
        // DNS name availability
        List<Promise<ServiceProviderResponse>> sequence = new ArrayList<Promise<ServiceProviderResponse>>();
        sequence.add(validateDomainNameAvailable(domainName, connection, request));
        sequence.add(validateProfileNameAvailable(profileName, connection, request));
        Promise<List<ServiceProviderResponse>> promise = Promise.sequence(sequence);
        return promise.map(new Function<List<ServiceProviderResponse>, ServiceProviderResponse>() {
            public ServiceProviderResponse invoke(List<ServiceProviderResponse> arg)
            {
                for (ServiceProviderResponse res : arg) {
                    if (res.getStatus() == Status.FAILURE) {
                        ServiceProviderResponse response = new ServiceProviderResponse();
                        response.setStatus(Status.FAILURE);
                        response.setMessage(res.getMessage());
                        return response;
                    }
                }
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.setStatus(Status.COMPLETE);
                return response;
            }
        });
    }

    protected Promise<ServiceProviderResponse> resetProfileStatus(final String profileName, final AzureConnection connection,
            final com.microsoft.schemas.azure.trafficmgr.Status status, final ServiceProviderResponse delegateResponse)
    {
        final Profile profile = new Profile();
        profile.setStatus(status);
        StatusDetails details = new StatusDetails();
        details.setEnabledVersion(1);
        profile.setStatusDetails(details);
        final String uri = Constants.TRAFFIC_MGR_BASE_URI + "/profiles/" + profileName;
        Promise<IHttpResponse> promise = connection.put(uri, profile, IHttpResponse.class);
        return promise.map(new Function<IHttpResponse, ServiceProviderResponse>() {
            public ServiceProviderResponse invoke(IHttpResponse arg)
            {
                if (arg.getStatusCode() == 200) {
                    return delegateResponse;
                }
                else {
                    String message = "Not able to reset profile status. " + ConnectionUtil.getStatusInfo(connection, arg);
                    ServiceProviderResponse response = new ServiceProviderResponse();
                    response.setStatus(Status.FAILURE);
                    response.setMessage(message);
                    return response;
                }
            }
        });
    }
}
