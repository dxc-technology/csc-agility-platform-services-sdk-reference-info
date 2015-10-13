/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.operations;

import java.util.Calendar;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLConfig;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLConstants;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLUtil;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePostStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;

public class AzureSQLServiceLifecycleOperations extends
        com.servicemesh.agility.sdk.service.operations.ServiceInstanceLifecycleOperations
{
    private static Logger logger = Logger.getLogger(AzureSQLServiceLifecycleOperations.class);

    /**
     * Constructor.
     */
    public AzureSQLServiceLifecycleOperations()
    {
        super();
    }

    @Override
    public Promise<ServiceProviderResponse> preProvision(ServiceInstancePreProvisionRequest request)
    {
        return super.preProvision(request);
    }

    /**
     * This method will execute after the service has been successfully provisioned or created.
     *
     * @param ServiceInstancePostProvisionRequest
     *            request - the HTTP request object associated with the post provision action
     */
    @Override
    public Promise<ServiceProviderResponse> postProvision(ServiceInstancePostProvisionRequest request)
    {
        if (request != null) {
            ServiceProviderResponse response = new ServiceProviderResponse();
            ServiceInstance dependentService = request.getDependentServiceInstance();
            ServiceInstance service = request.getServiceInstance();
            if (service != null && dependentService != null) {
                AssetProperty serverName =
                        AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_SERVER_NAME, service.getConfigurations());
                AssetProperty serverDomainNameAp =
                        AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME, service.getConfigurations());
                AssetProperty dbNameAp =
                        AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_DB_NAME, service.getAssetProperties());
                AssetProperty usernameAp =
                        AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_ADMIN_LOGIN, service.getAssetProperties());
                AssetProperty pwAp =
                        AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_ADMIN_PASS, service.getAssetProperties());

                if (serverName != null && serverDomainNameAp != null && usernameAp != null && pwAp != null) {
                    AzureSQLUtil.addSQLProperty(dependentService.getVariables(), serverName);
                    AzureSQLUtil.addSQLProperty(dependentService.getVariables(), serverDomainNameAp);
                    AzureSQLUtil.addSQLProperty(dependentService.getVariables(), usernameAp);
                    AzureSQLUtil.addSQLProperty(dependentService.getVariables(), pwAp);
                    response.setStatus(Status.COMPLETE);

                    if (dbNameAp != null) {
                        AzureSQLUtil.addSQLProperty(dependentService.getVariables(), dbNameAp);
                    }

                    // add the dependent service object to the modified list so it will get updated
                    response.getModified().add(dependentService);
                    response.setMessage("Azure SQL Service has completed the post-provision action.");
                    return Promise.pure(response);
                }
                else {
                    response.setStatus(Status.COMPLETE);
                    response.setReqId(request.getReqId());
                    response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                    String msg =
                            "The Azure SQL Service is missing one or more properties: " + "  serverName is null - "
                                    + (serverName == null) + "  serverDomainName is null - " + (serverDomainNameAp == null)
                                    + "  username is null - " + (usernameAp == null) + "  password is null - " + (pwAp == null);
                    AzureSQLServiceLifecycleOperations.logger.warn(msg);
                    response.setMessage(msg);
                    return Promise.pure(response);
                }

            }
            else {
                String msg =
                        "Service Instance and or Dependent Service Instance is not found. AzureSQL properties will not be copied to the dependent service instance.";
                AzureSQLServiceLifecycleOperations.logger.warn(msg);
                ServiceProviderResponse errResponse = new ServiceProviderResponse();
                errResponse.setReqId(request.getReqId());
                errResponse.setTimestamp(Calendar.getInstance().getTimeInMillis());
                errResponse.setMessage(msg);
                errResponse.setStatus(Status.COMPLETE);
                return Promise.pure(errResponse);
            }
        }
        else {
            String msg = "The request object is null.  Marking the transaction FAILED.";
            ServiceProviderResponse response = new ServiceProviderResponse();
            response.setReqId(AzureSQLConstants.UNDEFINED);
            response.setTimestamp(Calendar.getInstance().getTimeInMillis());
            response.setMessage(msg);
            response.setStatus(Status.FAILURE);
            return Promise.pure(response);
        }
    }

    @Override
    public Promise<ServiceProviderResponse> preStop(ServiceInstancePreStopRequest request)
    {
        return super.preStop(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postStop(ServiceInstancePostStopRequest request)
    {
        return super.postStop(request);
    }

    @Override
    public Promise<ServiceProviderResponse> preStart(ServiceInstancePreStartRequest request)
    {
        return super.preStart(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postStart(ServiceInstancePostStartRequest request)
    {
        return super.postStart(request);
    }

    @Override
    public Promise<ServiceProviderResponse> preRestart(ServiceInstancePreRestartRequest request)
    {
        return super.preRestart(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postRestart(ServiceInstancePostRestartRequest request)
    {
        return super.postRestart(request);
    }

    @Override
    public Promise<ServiceProviderResponse> preRelease(ServiceInstancePreReleaseRequest request)
    {
        return super.preRelease(request);
    }

    @Override
    public Promise<ServiceProviderResponse> postRelease(ServiceInstancePostReleaseRequest request)
    {
        return super.postRelease(request);
    }
}
