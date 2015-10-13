/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.operations;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.microsoft.schemas.windowsazure.ServiceResource;
import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLConfig;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLConstants;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLUtil;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.sdk.service.msgs.InstancePostBootRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreBootRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceResponse;
import com.servicemesh.agility.sdk.service.operations.InstanceOperations;
import com.servicemesh.azure.sql.models.FirewallRule;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

/**
 * This class will provide operations for VM instances that are dependent on the Azure SQL service.
 */
public class AzureSQLInstanceOperations extends InstanceOperations
{
    private static Logger logger = Logger.getLogger(AzureSQLInstanceOperations.class);

    /**
     * Constructor.
     */
    public AzureSQLInstanceOperations()
    {
        super();
    }

    /**
     * This method will execute after the VM instance has been successfully provisioned or created.
     * 
     * @param InstancePostProvisionRequest
     *            request - the HTTP request object associated with the post provision action
     */
    @Override
    public Promise<InstanceResponse> postProvision(final InstancePostProvisionRequest request)
    {
        AzureSQLInstanceOperations.logger.trace("Calling Azure SQL Instance 'postProvision' action.");

        if (request != null) {
            // extract the SQL service credentials and add to instance variables
            InstanceResponse response = new InstanceResponse();
            Instance instance = request.getInstance();

            response.setReqId(request.getReqId());
            response.setTimestamp(Calendar.getInstance().getTimeInMillis());

            if (instance != null) {
                ServiceInstance serviceInstance = request.getServiceInstance();

                // pass the instance info forward
                response.setInstance(instance);

                if (serviceInstance != null) {
                    String msg = "Azure SQL Service has completed the post-provision action.";

                    AssetProperty serverName =
                            AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_SERVER_NAME, serviceInstance.getConfigurations());
                    AssetProperty serverDomainNameAp =
                            AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME,
                                    serviceInstance.getConfigurations());
                    AssetProperty dbNameAp =
                            AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_DB_NAME, serviceInstance.getAssetProperties());
                    AssetProperty usernameAp =
                            AzureSQLUtil
                                    .getAssetProperty(AzureSQLConfig.CONFIG_ADMIN_LOGIN, serviceInstance.getAssetProperties());
                    AssetProperty pwAp =
                            AzureSQLUtil.getAssetProperty(AzureSQLConfig.CONFIG_ADMIN_PASS, serviceInstance.getAssetProperties());

                    if (serverName != null && serverDomainNameAp != null && usernameAp != null && pwAp != null) {
                        AzureSQLUtil.addSQLProperty(response.getInstance().getVariables(), serverName);
                        AzureSQLUtil.addSQLProperty(response.getInstance().getVariables(), serverDomainNameAp);
                        AzureSQLUtil.addSQLProperty(response.getInstance().getVariables(), usernameAp);
                        AzureSQLUtil.addSQLProperty(response.getInstance().getVariables(), pwAp);
                        response.getInstance().setLastUpdate(Calendar.getInstance());
                        response.setStatus(Status.COMPLETE);

                        AzureSQLInstanceOperations.logger.trace("Adding serverDomainName: " + serverDomainNameAp.getStringValue()
                                + "  username: " + usernameAp.getStringValue() + "  password: "
                                + AzureSQLUtil.maskPrivateKey(pwAp.getStringValue()) + " to Instance: "
                                + instance.getInstanceId());

                        if (dbNameAp != null) {
                            AzureSQLInstanceOperations.logger.trace("Adding dbName " + dbNameAp.getStringValue()
                                    + " to Instance " + instance.getInstanceId());
                            AzureSQLUtil.addSQLProperty(response.getInstance().getVariables(), dbNameAp);
                        }
                        else {
                            AzureSQLInstanceOperations.logger
                                    .trace("The database name is null.  This implies the Azure SQL Service created a server only.");
                        }

                        // add the instance object to the modified list so it will get updated
                        response.getModified().add(response.getInstance());
                    }
                    else {
                        response.setStatus(Status.COMPLETE);

                        msg =
                                "The Azure SQL Service is missing one or more properties: " + "  serverName is null - "
                                        + (serverName == null) + "  serverDomainName is null - " + (serverDomainNameAp == null)
                                        + "  username is null - " + (usernameAp == null) + "  password is null - "
                                        + (pwAp == null);
                    }

                    response.setMessage(msg);

                    AzureSQLInstanceOperations.logger.trace(msg);

                    Promise<InstanceResponse> credentialSharingResponse = Promise.pure(response);
                    List<Promise<InstanceResponse>> promiseList = new LinkedList<Promise<InstanceResponse>>();
                    promiseList.add(credentialSharingResponse);

                    // Need to create a firewall rule for the Instance so it can reach the server.
                    String instanceIP = instance.getPublicAddress();
                    if (instanceIP != null) {
                        try {
                            final AzureSQLRestHelper helper = new AzureSQLRestHelper(request);
                            ServiceResource firewallRule = new ServiceResource();
                            firewallRule.setName(instance.getName() + AzureSQLConstants.INSTANCE_FIREWALL_SUFFIX);
                            firewallRule.setStartIPAddress(instanceIP);
                            firewallRule.setEndIPAddress(instanceIP);
                            Promise<FirewallRule> firewallCreate =
                                    helper.createFirewallRule(serverName.getStringValue(), firewallRule);
                            Promise<InstanceResponse> firewallResponse =
                                    firewallCreate.map(new Function<FirewallRule, InstanceResponse>() {
                                        public InstanceResponse invoke(FirewallRule firewall)
                                        {
                                            InstanceResponse out = new InstanceResponse();
                                            if (firewall != null) {
                                                out.setStatus(Status.COMPLETE);
                                                out.setMessage("Firewall rule " + firewall.getName() + " creation completed.");
                                            }
                                            else {
                                                out.setStatus(Status.FAILURE);
                                                out.setMessage("Failure during firewall rule create.");
                                            }
                                            return out;
                                        }
                                    });
                            promiseList.add(firewallResponse);
                        }
                        catch (Exception e) {
                            InstanceResponse errorResponse = new InstanceResponse();
                            e.printStackTrace();
                            errorResponse.setStatus(Status.FAILURE);
                            errorResponse.setMessage("Error occured during AzureSQL Post Provision.\n" + e.getMessage());
                            promiseList.add(Promise.pure(errorResponse));
                        }
                    }
                    else {
                        String errmsg = "The instance has no public IP.  This was unexpected.";

                        AzureSQLInstanceOperations.logger.error(errmsg);

                        InstanceResponse errorResponse = new InstanceResponse();
                        errorResponse.setReqId(request.getReqId());
                        errorResponse.setStatus(Status.FAILURE);
                        errorResponse.setTimestamp(Calendar.getInstance().getTimeInMillis());
                        errorResponse.setMessage(errmsg);
                        promiseList.add(Promise.pure(errorResponse));
                    }

                    // Compile all the responses into 1.
                    Promise<List<InstanceResponse>> sequence = Promise.sequence(promiseList);
                    Promise<InstanceResponse> result = sequence.map(new Function<List<InstanceResponse>, InstanceResponse>() {
                        public InstanceResponse invoke(List<InstanceResponse> responses)
                        {
                            InstanceResponse response = new InstanceResponse();
                            response.setMessage("");

                            for (InstanceResponse resp : responses) {
                                response.setMessage(response.getMessage() + "\n" + resp.getMessage());
                                if (resp.getStatus().equals(Status.FAILURE)) {
                                    response.setStatus(Status.FAILURE);
                                }
                                if (resp.getModified().size() > 0) {
                                    for (Asset a : resp.getModified()) {
                                        response.getModified().add(a);
                                    }
                                }
                            }

                            if (response.getStatus() == null) {
                                response.setStatus(Status.COMPLETE);
                            }

                            response.setReqId(request.getReqId());
                            response.setTimestamp(System.currentTimeMillis());
                            return response;
                        }
                    });
                    return result;
                }
                else {
                    AzureSQLInstanceOperations.logger.warn("There is no ServiceInstance object in the "
                            + request.getClass().getName() + " request object.");
                    response.setStatus(Status.COMPLETE);
                    response.setMessage("The Service Instance was null.  Azure SQL Service properties will not be added to the instance.");
                    return Promise.pure(response);
                }
            }
            else {
                String msg = "The instance object was null.  This was unexpected.";

                AzureSQLInstanceOperations.logger.error(msg);

                response.setReqId(request.getReqId());
                response.setStatus(Status.FAILURE);
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setMessage(msg);
                return Promise.pure(response);
            }
        }
        else {
            String msg = "The request object is null.  Marking the transaction FAILED.";
            InstanceResponse response = new InstanceResponse();
            response.setReqId(AzureSQLConstants.UNDEFINED);
            response.setTimestamp(Calendar.getInstance().getTimeInMillis());
            response.setMessage(msg);
            response.setStatus(Status.FAILURE);
            return Promise.pure(response);
        }
    }

    @Override
    public Promise<InstanceResponse> preProvision(InstancePreProvisionRequest request)
    {
        return super.preProvision(request);
    }

    @Override
    public Promise<InstanceResponse> preBoot(InstancePreBootRequest request)
    {
        return super.preBoot(request);
    }

    @Override
    public Promise<InstanceResponse> postBoot(InstancePostBootRequest request)
    {
        return super.postBoot(request);
    }

    @Override
    public Promise<InstanceResponse> preStop(InstancePreStopRequest request)
    {
        return super.preStop(request);
    }

    @Override
    public Promise<InstanceResponse> postStop(InstancePostStopRequest request)
    {
        return super.postStop(request);
    }

    @Override
    public Promise<InstanceResponse> preStart(InstancePreStartRequest request)
    {
        return super.preStart(request);
    }

    @Override
    public Promise<InstanceResponse> postStart(InstancePostStartRequest request)
    {
        return super.postStart(request);
    }

    @Override
    public Promise<InstanceResponse> preRestart(InstancePreRestartRequest request)
    {
        return super.preRestart(request);
    }

    @Override
    public Promise<InstanceResponse> postRestart(InstancePostRestartRequest request)
    {
        return super.postRestart(request);
    }

    @Override
    public Promise<InstanceResponse> preRelease(InstancePreReleaseRequest request)
    {
        return super.preRelease(request);
    }

    /**
     * This method will execute after the VM instance has been successfully released. We always return a COMPLETE response so we
     * don't prevent the instance from getting released. Any errors encountered will be dumped to the log.
     * 
     * @param InstancePostReleaseRequest
     *            request - the HTTP request object associated with the post release action
     */
    @Override
    public Promise<InstanceResponse> postRelease(InstancePostReleaseRequest request)
    {
        AzureSQLInstanceOperations.logger.debug("Calling Azure SQL Instance 'postRelease' action.");

        if (request != null) {
            InstanceResponse response = new InstanceResponse();
            final Instance instance = request.getInstance();
            ServiceInstance serviceInstance = request.getServiceInstance();
            response.setReqId(request.getReqId());
            response.setTimestamp(Calendar.getInstance().getTimeInMillis());

            if (serviceInstance == null) {
                String msg =
                        "The request object has no service instance. Marking the response COMPLETE and not attempting to clean up firewall rule.";
                AzureSQLInstanceOperations.logger.error(msg);
                response.setMessage(msg);
                response.setStatus(Status.COMPLETE);
                return Promise.pure(response);
            }

            if (instance != null && serviceInstance.getState() != null
                    && !serviceInstance.getState().equals(ServiceState.UNPROVISIONED)) {
                String firewallName = instance.getName() + AzureSQLConstants.INSTANCE_FIREWALL_SUFFIX;
                String serverName =
                        AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_SERVER_NAME,
                                serviceInstance.getConfigurations());
                if (!AzureSQLUtil.isValued(serverName)) {
                    String msg = "No server name found. Unable to clean up firewall rule. Marking the response COMPLETE";
                    AzureSQLInstanceOperations.logger.error(msg);
                    response.setStatus(Status.COMPLETE);
                    response.setMessage(msg);
                    return Promise.pure(response);
                }
                try {
                    final AzureSQLRestHelper helper = new AzureSQLRestHelper(request);
                    Promise<IHttpResponse> firewallDelete = helper.deleteFirewallRule(serverName, firewallName);
                    Promise<InstanceResponse> result = firewallDelete.map(new Function<IHttpResponse, InstanceResponse>() {
                        public InstanceResponse invoke(IHttpResponse httpResponse)
                        {
                            InstanceResponse response = new InstanceResponse();
                            if (httpResponse.getStatusCode() == 200) {
                                response.setStatus(Status.COMPLETE);
                                response.setMessage("Successfully deleted firewall rule for Instance " + instance.getName());
                            }
                            else if (httpResponse.getStatusCode() == 404) {
                                response.setStatus(Status.COMPLETE);
                                response.setMessage("No firewall rule for Instance " + instance.getName()
                                        + " was found. Either it was never created or it was deleted already.");
                            }
                            else {
                                response.setStatus(Status.COMPLETE);
                                String msg =
                                        "Failed to delete firewall rule for Instance " + instance.getName()
                                                + ". Status code was " + httpResponse.getStatusCode()
                                                + "\nMarking the response COMPLETE and not cleaning up firewall rule.";
                                AzureSQLInstanceOperations.logger.error(msg);
                                response.setMessage(msg);
                            }
                            return response;
                        }
                    });
                    return result;
                }
                catch (Exception e) {
                    InstanceResponse errorResponse = new InstanceResponse();
                    e.printStackTrace();
                    String msg =
                            "Error occured during AzureSQL Instance Post Release.\n" + e.getMessage()
                                    + "\nMarking the response COMPLETE and not cleaning up firewall rule.";
                    AzureSQLInstanceOperations.logger.error(msg);
                    errorResponse.setStatus(Status.COMPLETE);
                    errorResponse.setMessage(msg);
                    return Promise.pure(errorResponse);
                }

            }
            else {
                String msg =
                        "The instance object was null or the service is Unprovisioned or the service state is null. "
                                + "Marking the response COMPLETE and not attempting to clean up firewall rule.";

                AzureSQLInstanceOperations.logger.error(msg);

                response.setReqId(request.getReqId());
                response.setStatus(Status.COMPLETE);
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setMessage(msg);
                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object is null. Marking the response COMPLETE and not attempting to clean up firewall rule.";
            InstanceResponse response = new InstanceResponse();
            response.setReqId(AzureSQLConstants.UNDEFINED);
            response.setTimestamp(Calendar.getInstance().getTimeInMillis());
            response.setMessage(msg);
            response.setStatus(Status.COMPLETE);
            return Promise.pure(response);
        }
    }

    @Override
    public Promise<InstanceResponse> preReconfigure(InstancePreReconfigureRequest request)
    {
        return super.preReconfigure(request);
    }

    @Override
    public Promise<InstanceResponse> postReconfigure(InstancePostReconfigureRequest request)
    {
        return super.postReconfigure(request);
    }

}
