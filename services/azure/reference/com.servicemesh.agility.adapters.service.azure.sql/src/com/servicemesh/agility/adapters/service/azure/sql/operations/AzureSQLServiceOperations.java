/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.microsoft.schemas.windowsazure.ServiceResource;
import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.agility.adapters.core.azure.util.KeyValuePair;
import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLAdapter;
import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLConfig;
import com.servicemesh.agility.adapters.service.azure.sql.connection.AzureSQLConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.sql.connection.AzureSQLEndpoint;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLConstants;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLUtil;
import com.servicemesh.agility.adapters.service.azure.sql.util.DatabasePoller;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.State;
import com.servicemesh.agility.sdk.service.msgs.MethodRequest;
import com.servicemesh.agility.sdk.service.msgs.MethodResponse;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceValidateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.msgs.ValidateMode;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations;
import com.servicemesh.azure.sql.models.AdministratorLoginPassword;
import com.servicemesh.azure.sql.models.Database;
import com.servicemesh.azure.sql.models.DatabaseList;
import com.servicemesh.azure.sql.models.FirewallRule;
import com.servicemesh.azure.sql.models.FirewallRuleList;
import com.servicemesh.azure.sql.models.Server;
import com.servicemesh.azure.sql.models.ServerList;
import com.servicemesh.azure.sql.models.ServerName;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.async.CompletablePromise;
import com.servicemesh.core.async.PromiseFactory;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

/**
 * This class supports the operations associated with an Agility Adapter Service. These operations apply to an instance of a given
 * service.
 */
public class AzureSQLServiceOperations extends ServiceInstanceOperations
{
    private static Logger logger = Logger.getLogger(AzureSQLProviderOperations.class);

    private AzureSQLAdapter adapter;
    private AzureSQLConnectionFactory factory;

    public AzureSQLServiceOperations(AzureSQLAdapter adapter, AzureSQLConnectionFactory factory)
    {
        if (adapter != null && factory != null) {
            this.adapter = adapter;
            this.factory = factory;

            AzureSQLServiceOperations.logger.trace("AzureSQLProviderOperations has been created.");
            AzureSQLServiceOperations.logger.trace("Adapter has a value - " + AzureSQLUtil.isValued(this.adapter));
            AzureSQLServiceOperations.logger.trace("Factory has a value - " + AzureSQLUtil.isValued(this.factory));
        }
        else {
            StringBuilder errMsg =
                    new StringBuilder("AzureSQLProviderOperations requires both an adapter and a factory to be set.");

            if (adapter == null) {
                errMsg.append("  The adapter value is null.");
            }

            if (factory == null) {
                errMsg.append("  The factory value is null.");
            }

            throw new AzureAdapterException(errMsg.toString());
        }
    }

    /**
     * This method will validate the properties of a service instance. If any validation errors occur, an exception promise will
     * be returned. If all is good, a response with a status of COMPLETE will be returned.
     * 
     * @param ServiceInstanceValidateRequest
     *            request - the request object that contains the ServiceInstance object
     * @return Promise<ServiceProviderResponse> - promise of a response object if no validation errors
     * @return Promise<AzureAdapterException> - promise of exception if validation errors exist
     */
    public Promise<ServiceProviderResponse> validate(final ServiceInstanceValidateRequest request)
    {
        if (request == null) {
            return Promise.pure(new Exception("No request was provided to the validate method."));
        }
        ServiceInstance serviceInstance = request.getServiceInstance();
        if (serviceInstance == null) {
            return Promise.pure(new Exception("Service Instance not provided."));
        }
        if (request.getMode() != null
                && (request.getMode() == ValidateMode.CREATE || request.getMode() == ValidateMode.UPDATE
                        && request.getServiceInstance().getState().equals(ServiceState.UNPROVISIONED))) {
            List<AssetProperty> properties = serviceInstance.getAssetProperties();
            String maxSize = AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_MAX_SIZE, properties);
            Server server = AzureSQLUtil.getServer(serviceInstance);
            Database database = AzureSQLUtil.getDatabase(serviceInstance);
            FirewallRule firewall = AzureSQLUtil.getFirewallRule(serviceInstance);

            StringBuilder errMsg = new StringBuilder();
            if (server == null) {
                errMsg.append("No server info was provided. AdminLogin, AdminPass, Location are required\n");
            }
            else {
                errMsg.append(AzureSQLServiceOperations.validateServerInfo(server));
            }
            if (firewall != null) {
                errMsg.append(AzureSQLServiceOperations.validateFirewallRuleInfo(firewall));
            }
            if (database != null || maxSize != null) {
                if (database == null) {
                    database = new Database();
                }
                errMsg.append(AzureSQLServiceOperations.validateDatabaseInfo(database, maxSize));
            }
            if (AzureSQLUtil.isValued(errMsg.toString())) {
                return Promise.pure(new Exception("\n" + errMsg.toString()));
            }

        }
        else if (request.getMode() != null && request.getMode() == ValidateMode.UPDATE) {
            ServiceInstance serviceInstanceOrig = request.getOriginalServiceInstance();
            if (serviceInstanceOrig == null) {
                return Promise.pure(new Exception("Original Service Instance not provided."));
            }
            String maxSize =
                    AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_MAX_SIZE, serviceInstance.getAssetProperties());
            Server serverOld = AzureSQLUtil.getServer(serviceInstanceOrig);
            Server serverNew = AzureSQLUtil.getServer(serviceInstance);
            Database databaseOld = AzureSQLUtil.getDatabase(serviceInstanceOrig);
            Database databaseNew = AzureSQLUtil.getDatabase(serviceInstance);
            FirewallRule firewallOld = AzureSQLUtil.getFirewallRule(serviceInstanceOrig);
            FirewallRule firewallNew = AzureSQLUtil.getFirewallRule(serviceInstance);
            StringBuilder errors = new StringBuilder();
            //server checks
            if (serverNew == null) {
                errors.append("Deleting a server is not supported. Releasing the service instance will delete the server.\n");
            }
            else if (!serverOld.getLocation().equals(serverNew.getLocation())
                    || !serverOld.getAdministratorLogin().equals(serverNew.getAdministratorLogin())
                    || !serverOld.getAdministratorLoginPassword().equals(serverNew.getAdministratorLoginPassword())) {
                errors.append("Changing server information is not supported.\n");
            }
            // db checks
            if (databaseOld == null && databaseNew == null) {
                // do nothing since nothing changed
            }
            else if (databaseOld == null && databaseNew != null) {
                errors.append("Creating database is not supported.\n");
            }
            else if (databaseOld != null && databaseNew == null) {
                errors.append("Deleting a database is not supported.\n");
            }
            else if (!databaseOld.getName().equals(databaseNew.getName())) {
                errors.append("Changing database name is not supported.\n");
            }
            else { // validate the input values that are being updated
                String errMsg = AzureSQLServiceOperations.validateDatabaseInfo(databaseNew, maxSize);
                if (AzureSQLUtil.isValued(errMsg)) {
                    errors.append(errMsg);
                }
            }
            // firewall checks
            if (firewallOld == null && firewallNew == null) {
                // do nothing since nothing changed
            }
            else if (firewallOld == null && firewallNew != null) {
                errors.append("Creating firewall rule is not supported.\n");
            }
            else if (firewallOld != null && firewallNew == null) {
                errors.append("Deleting firewall rule is not supported.\n");
            }
            else if (!firewallOld.getName().equals(firewallNew.getName())
                    || !firewallOld.getStartIPAddress().equals(firewallNew.getStartIPAddress())
                    || !firewallOld.getEndIPAddress().equals(firewallNew.getEndIPAddress())) {
                errors.append("Changing firewall rule information is not supported.\n");
            }
            if (AzureSQLUtil.isValued(errors.toString())) {
                return Promise.pure(new Exception("\n" + errors.toString()));
            }
        }
        return super.validate(request);
    }

    /**
     * This method will create a new instance of a service. The properties that define the service will be contained in the
     * ServiceInstance object found in the request.
     *
     * @param ServiceInstanceProvisionRequest
     *            request - request object that contains the service properties
     * @return Promise<ServiceProviderResponse> - promise for a response object with any updated service properties. The
     *         serviceInstance object will be returned in the response modified list. A status of COMPLETE implies a successful
     *         completion. A status of FAILURE implies there was a problem creating the service and will be marked as degraded.
     */
    @Override
    public Promise<ServiceProviderResponse> provision(final ServiceInstanceProvisionRequest request)
    {
        AzureSQLServiceOperations.logger.debug("Calling Azure SQL Service 'provision' action.");

        if (request != null) {
            ServiceInstance sqlInstance = request.getServiceInstance();

            if (sqlInstance != null) {
                final Server server = AzureSQLUtil.getServer(sqlInstance);
                final Database database = AzureSQLUtil.getDatabase(sqlInstance);
                final FirewallRule fwRule = AzureSQLUtil.getFirewallRule(sqlInstance);

                if (database != null) {
                    AzureSQLServiceOperations.logger.trace("Creating Database with Name = " + database.getName() + "  Edition = "
                            + database.getEdition() + "  Collation Name = " + database.getCollationName() + "  Size = "
                            + database.getMaxSizeBytes() + " bytes" + "  Service Objective ID = "
                            + database.getServiceObjectiveId());
                }
                else {
                    AzureSQLServiceOperations.logger.debug("No Database defined in the service instance object.");
                }

                if (fwRule != null) {
                    AzureSQLServiceOperations.logger.trace("Creating FirewallRule with Name = " + fwRule.getName()
                            + "  IP Range = " + fwRule.getStartIPAddress() + ":" + fwRule.getEndIPAddress());
                }
                else {
                    AzureSQLServiceOperations.logger.debug("No FirewallRule defined in the service instance object.");
                }

                if (server != null) {
                    // make sure server name is null
                    server.setName(null);

                    AzureSQLServiceOperations.logger.trace("Creating SQL Server with Name = " + server.getName() + "  Login = "
                            + server.getAdministratorLogin() + "  Password = "
                            + AzureSQLUtil.maskPrivateKey(server.getAdministratorLoginPassword()) + "  Location = "
                            + server.getLocation());
                }
                else {
                    // no need to continue without a server
                    return Promise.pure(AzureSQLUtil.buildFailedServiceProviderResponse(request.getReqId(),
                            "No SQL Server defined in the service instance object.", AzureSQLServiceOperations.logger));
                }

                MethodRequest methodReq = AzureSQLUtil.makeMethodRequest(request);

                methodReq.setName(AzureSQLConstants.METHOD_CREATE_SERVER);
                methodReq.setOriginalServiceInstance(sqlInstance);
                methodReq.setServiceInstance(sqlInstance);
                methodReq.getArguments().add(
                        AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_SERVER, AzureSQLUtil.serialize(server)));

                return AzureSQLServiceOperations.createServer(methodReq)
                        .flatMap(new Function<MethodResponse, Promise<ServiceProviderResponse>>() {
                            @Override
                            public Promise<ServiceProviderResponse> invoke(MethodResponse createServerResp)
                            {
                                if (createServerResp.getStatus() == Status.COMPLETE) {
                                    // the server now exists - now process Database and FirewallRule objects
                                    MethodRequest dbReq = AzureSQLUtil.makeMethodRequest(request);
                                    MethodRequest fwReq = AzureSQLUtil.makeMethodRequest(request);
                                    List<Promise<MethodResponse>> seqList = new ArrayList<Promise<MethodResponse>>();
                                    ServerName serverName = AzureSQLUtil.getServerNameObject(createServerResp.getResults());

                                    if (serverName != null && AzureSQLUtil.isValued(serverName.getValue())) {
                                        // update the server and the service instance objects with the new name
                                        server.setName(serverName.getValue());
                                        request.setServiceInstance(AzureSQLUtil.updateServer(request.getServiceInstance(), server));
                                        AzureSQLUtil.clearProperty(request.getServiceInstance().getConfigurations(),
                                                AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME);
                                        request.getServiceInstance()
                                                .getConfigurations()
                                                .add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME,
                                                        serverName.getValue() + AzureSQLConstants.AZURE_DOMAIN_SUFFIX));

                                        if (database != null) {
                                            dbReq.setName(AzureSQLConstants.METHOD_PROCESS_DATABASE_CHANGE);
                                            dbReq.getArguments().clear();
                                            dbReq.getArguments().addAll(
                                                    AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                                            AzureSQLConstants.PROP_DATABASE_NAME_STR, null), new KeyValuePair(
                                                            AzureSQLConstants.PROP_SERVER_NAME_STR, serverName.getValue())))); // will cause a create
                                            dbReq.getArguments().add(
                                                    AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_DATABASE,
                                                            AzureSQLUtil.serialize(database)));
                                            seqList.add(AzureSQLServiceOperations.processDatabaseChange(dbReq));
                                            CompletablePromise<MethodResponse> dbPoller = PromiseFactory.create();
                                            adapter.getReactor().timerCreateRel(
                                                    AzureSQLConstants.POLL_INTERVAL,
                                                    new DatabasePoller(request, dbPoller, AzureSQLConstants.POLL_INTERVAL,
                                                            AzureSQLConstants.POLL_RETRIES, AzureSQLConstants.DB_EXPECTED_STATUS,
                                                            AzureSQLRestHelper.getConnectionGeneric(request), true));
                                            seqList.add(dbPoller);
                                        }

                                        if (fwRule != null) {
                                            fwReq.setName(AzureSQLConstants.METHOD_PROCESS_FIREWALL_RULE_CHANGE);
                                            fwReq.getArguments().clear();
                                            fwReq.getArguments().addAll(
                                                    AzureSQLUtil.createStringArgument(AzureSQLConstants.PROP_SERVER_NAME_STR,
                                                            serverName.getValue()));
                                            fwReq.getArguments().add(
                                                    AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_FIREWALL_RULE_ADD,
                                                            AzureSQLUtil.serialize(new ArrayList<FirewallRule>(Arrays
                                                                    .asList(fwRule)))));

                                            seqList.add(AzureSQLServiceOperations.processFirewallRuleChange(fwReq));
                                        }

                                        if (!seqList.isEmpty()) {
                                            return Promise.sequence(seqList).map(
                                                    new Function<List<MethodResponse>, ServiceProviderResponse>() {
                                                        @Override
                                                        public ServiceProviderResponse invoke(List<MethodResponse> results)
                                                        {
                                                            ServiceProviderResponse resp = new ServiceProviderResponse();
                                                            List<String> errMsgs = new ArrayList<String>();
                                                            Server newServer =
                                                                    AzureSQLUtil.getServer(request.getServiceInstance());

                                                            for (MethodResponse result : results) {
                                                                if (result.getStatus() == Status.FAILURE) {
                                                                    errMsgs.add(result.getMessage());
                                                                }
                                                            }

                                                            if (errMsgs.isEmpty()) {
                                                                resp.setStatus(Status.COMPLETE);
                                                            }
                                                            else {
                                                                StringBuilder buf =
                                                                        new StringBuilder(
                                                                                "\nSQL Server '"
                                                                                        + (newServer != null ? newServer
                                                                                                .getName() : null)
                                                                                        + "' was created but the SQL service was not properly provisioned.");

                                                                for (String s : errMsgs) {
                                                                    buf.append("\n" + s);
                                                                }

                                                                buf.append("\n");

                                                                resp.setMessage(buf.toString());
                                                                resp.setStatus(Status.COMPLETE);

                                                                // degrade the service - something when wrong; however, the server was created
                                                                request.setServiceInstance(AzureSQLUtil.degradeService(
                                                                        request.getServiceInstance(), resp.getMessage()));

                                                                AzureSQLServiceOperations.logger
                                                                        .error("The Azure SQL Service failed to be completely provisioned.  It is in a degraded state.\n"
                                                                                + resp.getMessage());
                                                            }

                                                            resp.setReqId(request.getReqId());
                                                            resp.setTimestamp(System.currentTimeMillis());
                                                            resp.getModified().add(request.getServiceInstance()); // make sure the service instance is up-to-date

                                                            return resp;
                                                        }
                                                    });
                                        }
                                        else {
                                            ServiceProviderResponse resp = new ServiceProviderResponse();

                                            resp.setMessage(createServerResp.getMessage());
                                            resp.setReqId(createServerResp.getReqId());
                                            resp.setStatus(createServerResp.getStatus());
                                            resp.setTimestamp(createServerResp.getTimestamp());
                                            resp.getModified().add(request.getServiceInstance()); // make sure the service instance is up-to-date

                                            return Promise.pure(resp);
                                        }
                                    }
                                    else {
                                        return Promise.pure(AzureSQLUtil.buildFailedServiceProviderResponse(request.getReqId(),
                                                "The ServerName object from the create server process was null.",
                                                AzureSQLServiceOperations.logger));
                                    }
                                }
                                else {
                                    return Promise.pure(AzureSQLUtil.buildResponse(ServiceProviderResponse.class,
                                            createServerResp.getReqId(), createServerResp.getStatus(),
                                            "Creation of server with name '" + server.getName() + "' failed.\n"
                                                    + createServerResp.getMessage(), Level.ERROR,
                                            AzureSQLServiceOperations.logger, null));
                                }
                            }
                        }).recover(AzureSQLServiceOperations.getRecoverFunction(request));
            }
            else {
                return Promise.pure(AzureSQLUtil.buildFailedServiceProviderResponse(request.getReqId(),
                        "The SQL Service Instance is missing from the request.  Provision cannot continue.",
                        AzureSQLServiceOperations.logger));
            }
        }
        else {
            return Promise.pure(AzureSQLUtil.buildFailedServiceProviderResponse(AzureSQLConstants.UNDEFINED,
                    "The request object is null.  Provision cannot continue.", AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This method will delete a provisioned service.
     * 
     * @param ServiceInstanceReleaseRequest
     *            request - the request object that will contain the service properties which will identify the service to be
     *            removed.
     * @return Promise<ServiceProviderResponse> - promise of a response where a status of COMPLETE implies the service was
     *         successfully removed and FAILURE implies the service was not removed.
     */
    @Override
    public Promise<ServiceProviderResponse> release(final ServiceInstanceReleaseRequest request)
    {
        if (request != null) {
            List<Asset> dependents = request.getDependents();
            for (Asset dep : dependents) {
                if (dep instanceof Instance) {
                    Instance inst = (Instance) dep;
                    if (!inst.getState().equals(State.DESTROYED)) {
                        AzureSQLServiceOperations.logger.warn("Instance " + inst.getName() + " is dependent on this service.");
                    }
                }
                else if (dep instanceof ServiceInstance) {
                    ServiceInstance service = (ServiceInstance) dep;
                    if (!service.getState().equals(ServiceState.UNPROVISIONED)) {
                        AzureSQLServiceOperations.logger.warn("Service Instance " + service.getName()
                                + " is dependent on this service.");
                    }
                }
            }
            final String serverName = AzureSQLUtil.getServerName(request);
            if (serverName == null) {
                String msg =
                        "The server name is null. Assuming the server was never provisioned and marking the transaction COMPLETE.";
                AzureSQLServiceOperations.logger.debug(msg);
                ServiceProviderResponse response = new ServiceProviderResponse();
                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.COMPLETE);
                response.setMessage(msg);
                return Promise.pure(response);
            }
            try {
                final AzureSQLRestHelper helper = new AzureSQLRestHelper(request);
                Promise<Server> getServerPromise = helper.getServer(serverName);
                Promise<ServiceProviderResponse> result =
                        getServerPromise.flatMap(new Function<Server, Promise<ServiceProviderResponse>>() {
                            public Promise<ServiceProviderResponse> invoke(Server server)
                            {
                                if (server == null) {
                                    String msg =
                                            "The server "
                                                    + serverName
                                                    + " was not found. Assuming the server was deleted outside of Agility and marking the transaction COMPLETE.";
                                    AzureSQLServiceOperations.logger.debug(msg);
                                    AzureSQLUtil.clearProperty(request.getServiceInstance().getConfigurations(),
                                            AzureSQLConfig.CONFIG_SERVER_NAME);
                                    AzureSQLUtil.clearProperty(request.getServiceInstance().getConfigurations(),
                                            AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME);
                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                    response.setReqId(request.getReqId());
                                    response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                                    response.setStatus(Status.COMPLETE);
                                    response.setMessage(msg);
                                    response.getModified().add(request.getServiceInstance());
                                    return Promise.pure(response);
                                }
                                try {
                                    Promise<IHttpResponse> deleteServerPromise = helper.deleteServer(serverName);
                                    Promise<ServiceProviderResponse> deleteServerResponse =
                                            deleteServerPromise.map(new Function<IHttpResponse, ServiceProviderResponse>() {
                                                public ServiceProviderResponse invoke(IHttpResponse httpResponse)
                                                {
                                                    ServiceProviderResponse response = new ServiceProviderResponse();
                                                    AzureSQLEndpoint endpoint = helper.getEndpoint2010Context();
                                                    if (httpResponse.getContent() != null
                                                            && httpResponse.getContent().contains("<Error")) {
                                                        response.setStatus(Status.FAILURE);
                                                        String errorMsg = "";
                                                        if (httpResponse.getContent().contains(
                                                                AzureSQLConstants.GENERIC_ERROR_RESPONSE)) {
                                                            com.microsoft.schemas.windowsazure.Error error =
                                                                    endpoint.decode(httpResponse,
                                                                            AzureSQLConstants.GENERIC_AZURE_CONTEXT,
                                                                            com.microsoft.schemas.windowsazure.Error.class);
                                                            errorMsg =
                                                                    endpoint.encode(AzureSQLConstants.GENERIC_AZURE_CONTEXT,
                                                                            error);
                                                        }
                                                        else {
                                                            com.servicemesh.azure.sql.models.Error error =
                                                                    endpoint.decode(httpResponse,
                                                                            com.servicemesh.azure.sql.models.Error.class);
                                                            errorMsg = endpoint.encode(error);
                                                        }
                                                        response.setMessage("Error occured while releasing server.\n" + errorMsg);
                                                    }
                                                    else {
                                                        if (httpResponse.getStatusCode() == 200) {
                                                            AzureSQLUtil.clearProperty(request.getServiceInstance()
                                                                    .getConfigurations(), AzureSQLConfig.CONFIG_SERVER_NAME);
                                                            AzureSQLUtil.clearProperty(request.getServiceInstance()
                                                                    .getConfigurations(),
                                                                    AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME);
                                                            response.getModified().add(request.getServiceInstance());
                                                            response.setStatus(Status.COMPLETE);
                                                            response.setMessage("Server " + serverName
                                                                    + " was successfully released");
                                                        }
                                                        else {
                                                            response.setStatus(Status.FAILURE);
                                                            response.setMessage("Marking release as failed. Expected Http Status of 200. Http Status Code was: "
                                                                    + httpResponse.getStatusCode());
                                                        }
                                                    }
                                                    response.setReqId(request.getReqId());
                                                    response.setTimestamp(System.currentTimeMillis());

                                                    return response;
                                                }
                                            });
                                    return deleteServerResponse;
                                }
                                catch (Exception e) {
                                    return Promise.pure(AzureSQLUtil.buildFailedServiceProviderResponse(request.getReqId(),
                                            e.getMessage(), AzureSQLServiceOperations.logger, e));
                                }
                            }
                        });
                return result;
            }
            catch (Exception e) {
                return Promise.pure(AzureSQLUtil.buildFailedServiceProviderResponse(request.getReqId(), e.getMessage(),
                        AzureSQLServiceOperations.logger, e));
            }
        }
        else {
            String msg = "The request object is null.  Marking the transaction FAILED.";
            ServiceProviderResponse response = new ServiceProviderResponse();

            response.setReqId(AzureSQLConstants.UNDEFINED);
            response.setTimestamp(Calendar.getInstance().getTimeInMillis());
            response.setStatus(Status.FAILURE);
            response.setMessage(msg);

            return Promise.pure(response);
        }
    }

    // TODO - whc - this version will reconfigure everything but it needs the originalServiceInstance and
    //        the serviceInstance objects to be populated.  Currently, this is not possible.  Save this
    //        work for now
    /**
     * This method will verify the service and reconfigure items that are changeable. It is assumed that the current service state
     * is provided in the OriginalServiceInstance object and the desired changes will be set in the ServiceInstance. The server
     * will be checked for existence - failure would imply the server was deleted outside the service. Reconfigure supports the
     * following changes: 1. Database properties update - changeable properties (as allowed by the updateDatabase REST call) are
     * edition, maxSizeBytes, serviceObjectiveId 2. Database rename 3. Server password update 4. Replace FirewallRule 5. Update
     * FirewallRule IP values Database property update and database rename are mutually exclusive, i.e. if the name changes,
     * property changes are ignored. Also, FirewallRule replacement and IP value updates cannot be process concurrently since on
     * one rule is currently supported by the service.
     */
    //	@Override
    //	public Promise<ServiceProviderResponse> reconfigureEpic(final ServiceInstanceReconfigureRequest request) {
    protected Promise<ServiceProviderResponse> reconfigureEpic(final ServiceInstanceReconfigureRequest request)
    {
        ServiceProviderResponse response = new ServiceProviderResponse();
        String msg = null;

        if (request != null) {
            final ServiceInstance serviceInstanceOrig = request.getOriginalServiceInstance(); // current state of service
            ServiceInstance serviceInstance = request.getServiceInstance(); // desired state of service

            AzureSQLServiceOperations.logger.trace("Original ServiceInstance object null - " + (serviceInstanceOrig == null)
                    + "  ServiceInstance object null - " + (serviceInstance == null));

            response.setReqId(request.getReqId());

            if (serviceInstance != null && serviceInstanceOrig != null) {
                if (serviceInstanceOrig.getState() == ServiceState.UNPROVISIONED) {
                    msg = "Service has not been provisioned.  No work to do.";
                    AzureSQLServiceOperations.logger.trace(msg);
                    response.setStatus(Status.COMPLETE);
                }
                else {
                    AzureSQLServiceOperations.logger.trace("Reconfiguring Azure SQL service.");

                    List<String> propertyErrors = AzureSQLServiceOperations.validateProperties(serviceInstanceOrig);

                    AzureSQLServiceOperations.logger.trace(propertyErrors.isEmpty() ? "All properties are valid."
                            : "Some properties failed validation.");

                    // it is expected to have a server and credentials
                    if (propertyErrors.isEmpty()) {
                        // TODO - whc - we will need to revisit how to handle dependents - I will just log any differences for now
                        if (serviceInstanceOrig.getDependents().size() != serviceInstance.getDependents().size()) {
                            AzureSQLServiceOperations.logger
                                    .warn("The original list of service dependents does not match the new set of service dependents.");
                        }

                        try {
                            Server server = AzureSQLUtil.getServer(serviceInstanceOrig);
                            final AzureSQLRestHelper helper = new AzureSQLRestHelper(request);
                            Promise<Server> serverPromise = helper.getServer(server != null ? server.getName() : ""); // server should not be null here

                            return serverPromise.flatMap(
                                    AzureSQLServiceOperations.getServerPromiseFunction(request, serviceInstanceOrig,
                                            serviceInstance, helper)).recover(
                                    AzureSQLServiceOperations.getRecoverFunction(request));
                        }
                        catch (Exception e) {
                            msg = "An exception occurred during the 'reconfigure' operation.";
                            AzureSQLServiceOperations.logger.error(msg, e);
                            response.setStatus(Status.FAILURE);
                        }
                    }
                    else {
                        StringBuilder buf = new StringBuilder("The Azure SQL service has the following configuration problems:");

                        for (String s : propertyErrors) {
                            buf.append("\n" + s);
                        }

                        msg = buf.toString();
                        AzureSQLServiceOperations.logger.error(msg);
                        response.setStatus(Status.FAILURE);
                    }
                }
            }
            else {
                msg = null;

                if (serviceInstanceOrig == null) {
                    msg =
                            "The OriginalServiceInstance object (current state) was not provided for Azure SQL service operation 'reconfigure'";
                }

                if (serviceInstance == null) {
                    String msg2 =
                            "The ServiceInstance object (desired changes) was not provided for Azure SQL service operation 'reconfigure'";
                    msg = AzureSQLUtil.isValued(msg) ? msg + "\n" + msg2 : msg2;
                }

                AzureSQLServiceOperations.logger.error(msg);
                response.setStatus(Status.FAILURE);
            }
        }
        else {
            msg = "No request object was provided for Azure SQL service operation 'reconfigure'.";
            AzureSQLServiceOperations.logger.error(msg);
            response.setStatus(Status.FAILURE);
            response.setReqId(System.currentTimeMillis());
        }

        response.setTimestamp(System.currentTimeMillis());
        return Promise.pure(response);
    }

    /**
     * This method will verify the service and reconfigure database items that are changeable. The server will be checked for
     * existence - failure would imply the server was deleted outside the service. Reconfigure supports the following changes: 1.
     * Database properties update - changeable properties (as allowed by the updateDatabase REST call) are edition, maxSizeBytes,
     * serviceObjectiveId
     * 
     * @param ServiceInstanceReconfigureRequest
     *            request - request object that contains the service properties
     * @return Promise<ServiceProviderResponse> - a promise of a response object. The status will be COMPLETE if the update was
     *         successful. If the update fails, the status will be FAILURE and will be marked as degraded.
     */
    @Override
    public Promise<ServiceProviderResponse> reconfigure(final ServiceInstanceReconfigureRequest request)
    {
        ServiceProviderResponse response = new ServiceProviderResponse();
        String msg = null;

        if (request != null) {
            ServiceInstance serviceInstance = request.getServiceInstance(); // desired state of service

            response.setReqId(request.getReqId());

            if (serviceInstance != null) {
                if (serviceInstance.getState() == ServiceState.UNPROVISIONED) {
                    msg = "Service has not been provisioned. No work to do.";
                    AzureSQLServiceOperations.logger.trace(msg);
                    response.setMessage(msg);
                    response.setStatus(Status.COMPLETE);
                }
                else {
                    AzureSQLServiceOperations.logger.trace("Reconfiguring Azure SQL service.");

                    List<String> propertyErrors = AzureSQLServiceOperations.validatePropertiesBasic(serviceInstance);

                    AzureSQLServiceOperations.logger.trace(propertyErrors.isEmpty() ? "All properties are valid."
                            : "Some properties failed validation.");

                    // it is expected to have a server and credentials
                    if (propertyErrors.isEmpty()) {
                        try {
                            Server server = AzureSQLUtil.getServer(serviceInstance);
                            Database database = AzureSQLUtil.getDatabase(serviceInstance);
                            AzureSQLRestHelper helper = new AzureSQLRestHelper(request);
                            ServiceResource dbResource = new ServiceResource();

                            dbResource.setName(database.getName());
                            dbResource.setEdition(database.getEdition());
                            dbResource.setMaxSizeBytes(database.getMaxSizeBytes());
                            dbResource.setServiceObjectiveId(database.getServiceObjectiveId());

                            Promise<Database> dbPromise = helper.updateDatabase(server.getName(), database.getName(), dbResource);

                            return dbPromise.map(new Function<Database, ServiceProviderResponse>() {

                                @Override
                                public ServiceProviderResponse invoke(Database db)
                                {
                                    AzureSQLServiceOperations.logger.trace("Database '" + db.getName()
                                            + "' successfully updated.");
                                    return AzureSQLUtil.buildResponse(ServiceProviderResponse.class, request.getReqId(),
                                            Status.COMPLETE, "SUCCESS", Level.TRACE, AzureSQLServiceOperations.logger, null);
                                }

                            }).recover(AzureSQLServiceOperations.getRecoverFunction(request));
                        }
                        catch (Exception e) {
                            msg = "An exception occurred during the 'reconfigure' operation.";
                            AzureSQLServiceOperations.logger.error(msg, e);
                            response.setMessage(msg + "\n" + e.getMessage());
                            response.setStatus(Status.FAILURE);
                        }
                    }
                    else {
                        StringBuilder buf = new StringBuilder("The Azure SQL service has the following configuration problems:");

                        for (String s : propertyErrors) {
                            buf.append("\n" + s);
                        }

                        msg = buf.toString();
                        AzureSQLServiceOperations.logger.error(msg);
                        response.setMessage(msg);
                        response.setStatus(Status.FAILURE);
                    }
                }
            }
            else {
                String errMsg =
                        "The ServiceInstance object (desired changes) was not provided for Azure SQL service operation 'reconfigure'";

                AzureSQLServiceOperations.logger.error(errMsg);
                response.setMessage(errMsg);
                response.setStatus(Status.FAILURE);
            }
        }
        else {
            msg = "No request object was provided for Azure SQL service operation 'reconfigure'.";
            AzureSQLServiceOperations.logger.error(msg);
            response.setMessage(msg);
            response.setStatus(Status.FAILURE);
            response.setReqId(AzureSQLConstants.UNDEFINED);
        }

        response.setTimestamp(System.currentTimeMillis());
        return Promise.pure(response);
    }

    @Override
    public Promise<ServiceProviderResponse> start(ServiceInstanceStartRequest request)
    {
        return super.start(request);
    }

    @Override
    public Promise<ServiceProviderResponse> stop(ServiceInstanceStopRequest request)
    {
        return super.stop(request);
    }

    /**
     * This operation will change the name of a database for a given SQL server. The input method argument must be configured with
     * the proper method name (@see AzureSQLConstants.METHOD_CHANGE_DATABASE_NAME). The name of the server, current database name,
     * and new database name must be assigned to the arguments list using the property names
     * 
     * @see AzureSQLConstants.PROP_SERVER_NAME_STR
     * @see AzureSQLConstants.PROP_DATABASE_NAME_STR
     * @see AzureSQLConstants.PROP_NEW_DATABASE_NAME_STR This method will extract the name of the server and DB names from the
     *      arguments list and make a call to the helper.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - promise of a response. If the status is COMPLETE, the rename was successful and the
     *         results will have a serialized version of the Database object. If the rename fails, the status will be FAILURE.
     */
    public static Promise<MethodResponse> changeDatabaseName(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_CHANGE_DATABASE_NAME;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            String serverName = AzureSQLUtil.getServerName(request.getArguments());
            String dbName = AzureSQLUtil.getDatabaseName(request.getArguments());
            String newDbName = AzureSQLUtil.getNewDatabaseName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName) && AzureSQLUtil.isValued(dbName) && AzureSQLUtil.isValued(newDbName)) {
                if (!dbName.equals(newDbName)) {
                    ServiceResource dbResource = new ServiceResource();

                    dbResource.setName(newDbName);

                    try {
                        return new AzureSQLRestHelper(request).updateDatabase(serverName, dbName, dbResource).map(
                                new Function<Database, MethodResponse>() {
                                    @Override
                                    public MethodResponse invoke(Database database)
                                    {
                                        String variableName = AzureSQLConstants.PROP_DATABASE;
                                        MethodResponse response = new MethodResponse();

                                        response.setReqId(request.getReqId());

                                        if (database != null) {
                                            response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, database));
                                            response.setStatus(Status.COMPLETE);
                                        }
                                        else {
                                            response.setStatus(Status.FAILURE);
                                            response.setMessage("The update call returned a null database object.");
                                        }

                                        response.setTimestamp(System.currentTimeMillis());

                                        return response;
                                    }
                                });
                    }
                    catch (Exception ex) {
                        String msg = "An exception occurred while performing the 'changeDatabaseName' operation.";
                        AzureSQLServiceOperations.logger.error(msg, ex);
                        return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                                AzureSQLServiceOperations.logger, ex));
                    }
                }
                else {
                    String msg = "The current database name matches the new database name.  No work to do.";

                    MethodResponse response = new MethodResponse();

                    AzureSQLServiceOperations.logger.trace(msg);

                    response.setReqId(request.getReqId());
                    response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                    response.setStatus(Status.COMPLETE);
                    response.setMessage(msg);

                    return Promise.pure(response);
                }
            }
            else {
                StringBuilder msg = new StringBuilder("Missing request arguments:");
                MethodResponse response = new MethodResponse();

                if (!AzureSQLUtil.isValued(serverName)) {
                    msg.append("\nServerName");
                }

                if (!AzureSQLUtil.isValued(dbName)) {
                    msg.append("\ndbName");
                }

                if (!AzureSQLUtil.isValued(newDbName)) {
                    msg.append("\nnewDbName");
                }

                msg.append("\n");

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg.toString());

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will process a database create, update, or name change for a given SQL server. The input method argument
     * must be configured with the proper method name (@see AzureSQLConstants.METHOD_PROCESS_DATABASE_CHANGE). The name of the
     * server, current database name, and new database object must be assigned to the arguments list using the property names
     * 
     * @see AzureSQLConstants.PROP_SERVER_NAME_STR
     * @see AzureSQLConstants.PROP_DATABASE_NAME_STR
     * @see AzureSQLConstants.PROP_DATABASE This method will extract the name of the server and DB info from the arguments list
     *      and make a call to the helper.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the Database object result.
     */
    public static Promise<MethodResponse> processDatabaseChange(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_PROCESS_DATABASE_CHANGE;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            String serverName = AzureSQLUtil.getServerName(request.getArguments());
            String dbName = AzureSQLUtil.getDatabaseName(request.getArguments());
            Database database = AzureSQLUtil.getDatabase(request.getArguments());

            if (AzureSQLUtil.isValued(serverName) && database != null) {
                try {
                    String newDbName = database.getName();
                    boolean doCreate = false;
                    ServiceResource dbResource = new ServiceResource();

                    Function<Database, MethodResponse> mapFunction = new Function<Database, MethodResponse>() {
                        @Override
                        public MethodResponse invoke(Database database)
                        {
                            String variableName = AzureSQLConstants.PROP_DATABASE;
                            MethodResponse response = new MethodResponse();

                            response.setReqId(request.getReqId());

                            if (database != null) {
                                response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, database));
                                response.setStatus(Status.COMPLETE);
                            }
                            else {
                                response.setStatus(Status.FAILURE);
                                response.setMessage("The Azure REST call returned a null database object.");
                            }

                            response.setTimestamp(System.currentTimeMillis());

                            return response;
                        }
                    };

                    // now figure out what needs to happen
                    if (!AzureSQLUtil.isValued(dbName)) {
                        doCreate = true;

                        dbResource.setName(newDbName);
                        dbResource.setEdition(database.getEdition());
                        dbResource.setCollationName(database.getCollationName());
                        dbResource.setMaxSizeBytes(database.getMaxSizeBytes());
                        dbResource.setServiceObjectiveId(database.getServiceObjectiveId());

                        AzureSQLServiceOperations.logger
                                .debug("Creating a new database '" + newDbName + "' on server '" + serverName
                                        + "' with the following properties: " + "Edition = " + dbResource.getEdition()
                                        + "  Collation Name = " + dbResource.getCollationName() + "  Size in Bytes = "
                                        + dbResource.getMaxSizeBytes() + "  Service Objective ID = "
                                        + dbResource.getServiceObjectiveId());
                    }
                    else if (AzureSQLUtil.isValued(newDbName) && !dbName.equals(newDbName)) {
                        dbResource.setName(newDbName);

                        AzureSQLServiceOperations.logger.debug("Renaming database '" + dbName + "' to '" + dbResource.getName()
                                + "'");
                    }
                    else {
                        dbResource.setName(dbName);
                        dbResource.setEdition(AzureSQLUtil.isValued(database.getEdition()) ? database.getEdition() : null);
                        dbResource.setCollationName(AzureSQLUtil.isValued(database.getCollationName()) ? database
                                .getCollationName() : null);
                        dbResource.setMaxSizeBytes(AzureSQLUtil.isValued(database.getMaxSizeBytes())
                                && database.getMaxSizeBytes() > 0 ? database.getMaxSizeBytes() : null);
                        dbResource.setServiceObjectiveId(AzureSQLUtil.isValued(database.getServiceObjectiveId()) ? database
                                .getServiceObjectiveId() : null);

                        AzureSQLServiceOperations.logger
                                .debug("Updating a database '" + dbName + "' on server '" + serverName
                                        + "' with the following properties: " + "Edition = " + dbResource.getEdition()
                                        + "  Collation Name = " + dbResource.getCollationName() + "  Size in Bytes = "
                                        + dbResource.getMaxSizeBytes() + "  Service Objective ID = "
                                        + dbResource.getServiceObjectiveId());
                    }

                    if (doCreate) {
                        return new AzureSQLRestHelper(request).createDatabase(serverName, dbResource).map(mapFunction)
                                .recover(AzureSQLServiceOperations.getRecoverFunction(request));
                    }
                    else {
                        return new AzureSQLRestHelper(request).updateDatabase(serverName, dbName, dbResource).map(mapFunction)
                                .recover(AzureSQLServiceOperations.getRecoverFunction(request));
                    }
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'processDatabaseChange' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                StringBuilder msg = new StringBuilder("Missing request arguments:");
                MethodResponse response = new MethodResponse();

                if (!AzureSQLUtil.isValued(serverName)) {
                    msg.append("\nServer Name");
                }

                if (database == null) {
                    msg.append("\nDatabase Object");
                }

                msg.append("\n");

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg.toString());

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will process a firewall rule create, update, or delete for a given SQL server. The input method argument
     * must be configured with the proper method name (@see AzureSQLConstants.METHOD_PROCESS_FIREWALL_RULE_CHANGE). The name of
     * the server, rule delete, rule add, rule change objects must be assigned to the arguments list using the property names
     * 
     * @see AzureSQLConstants.PROP_SERVER_NAME_STR
     * @see AzureSQLConstants.PROP_FIREWALL_RULE_ADD
     * @see AzureSQLConstants.PROP_FIREWALL_RULE_DELETE
     * @see AzureSQLConstants.PROP_FIREWALL_RULE_CHANGE This method will extract the name of the server info and firewall rule
     *      lists from the arguments list and make a call to the helper using a Promise sequence. Note, deletes may take five
     *      minutes to become effective.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a details of the processing in the response message.
     */
    public static Promise<MethodResponse> processFirewallRuleChange(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_PROCESS_FIREWALL_RULE_CHANGE;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            final String serverName = AzureSQLUtil.getServerName(request.getArguments());
            List<FirewallRule> adds =
                    AzureSQLUtil.getFirewallRuleList(AzureSQLConstants.PROP_FIREWALL_RULE_ADD, request.getArguments());
            List<FirewallRule> deletes =
                    AzureSQLUtil.getFirewallRuleList(AzureSQLConstants.PROP_FIREWALL_RULE_DELETE, request.getArguments());
            List<FirewallRule> changes =
                    AzureSQLUtil.getFirewallRuleList(AzureSQLConstants.PROP_FIREWALL_RULE_CHANGE, request.getArguments());

            if (AzureSQLServiceOperations.logger.isTraceEnabled()) {
                StringBuilder buf = new StringBuilder("\nProcessing FirewallRule changes for server '" + serverName + "'");

                if (AzureSQLUtil.isValued(deletes)) {
                    buf.append("\n     deleting the following rules:");
                    for (FirewallRule fwr : deletes) {
                        buf.append("\n        " + fwr.getName() + "  " + fwr.getStartIPAddress() + ":" + fwr.getEndIPAddress());
                    }
                }
                else {
                    buf.append("\n     no deletes required");
                }

                if (AzureSQLUtil.isValued(adds)) {
                    buf.append("\n     adding the following rules:");
                    for (FirewallRule fwr : adds) {
                        buf.append("\n        " + fwr.getName() + "  " + fwr.getStartIPAddress() + ":" + fwr.getEndIPAddress());
                    }
                }
                else {
                    buf.append("\n     no adds required");
                }

                if (AzureSQLUtil.isValued(changes)) {
                    buf.append("\n     changing the following rules:");
                    for (FirewallRule fwr : changes) {
                        buf.append("\n        " + fwr.getName() + "  " + fwr.getStartIPAddress() + ":" + fwr.getEndIPAddress());
                    }
                }
                else {
                    buf.append("\n     no changes required");
                }

                buf.append("\n");
                AzureSQLServiceOperations.logger.trace(buf.toString());
            }

            if (AzureSQLUtil.isValued(serverName)) {
                try {
                    AzureSQLRestHelper helper = new AzureSQLRestHelper(request);
                    List<Promise<MethodResponse>> seqList = new ArrayList<Promise<MethodResponse>>();

                    if (AzureSQLUtil.isValued(deletes)) {
                        for (final FirewallRule fwr : deletes) {
                            seqList.add(helper.deleteFirewallRule(serverName, fwr.getName())
                                    .map(new Function<IHttpResponse, MethodResponse>() {
                                        @Override
                                        public MethodResponse invoke(IHttpResponse httpResp)
                                        {
                                            MethodResponse resp = new MethodResponse();

                                            resp.setStatus(Status.COMPLETE);
                                            resp.setReqId(request.getReqId());
                                            resp.setTimestamp(System.currentTimeMillis());

                                            if (httpResp.getStatusCode() == 200) {
                                                resp.setMessage(serverName + ":" + fwr.getName() + ":SUCCESS-DELETED");
                                            }
                                            else if (httpResp.getStatusCode() == 404) {
                                                resp.setMessage(serverName + ":" + fwr.getName() + ":SUCCESS-NOTFOUND");
                                            }
                                            else {
                                                resp.setStatus(Status.FAILURE);
                                                resp.setMessage(serverName
                                                        + ":"
                                                        + fwr.getName()
                                                        + ":FAILED["
                                                        + httpResp.getStatusCode()
                                                        + (httpResp.getStatus() != null ? ":" + httpResp.getStatus().getReason()
                                                                : ""));
                                            }

                                            return resp;
                                        }
                                    }).recover(AzureSQLServiceOperations.getRecoverFunction(request)));
                        }
                    }

                    if (AzureSQLUtil.isValued(adds)) {
                        for (final FirewallRule fwr : adds) {
                            ServiceResource fwResource = new ServiceResource();

                            fwResource.setName(fwr.getName());
                            fwResource.setStartIPAddress(fwr.getStartIPAddress());
                            fwResource.setEndIPAddress(fwr.getEndIPAddress());

                            seqList.add(helper.createFirewallRule(serverName, fwResource)
                                    .map(new Function<FirewallRule, MethodResponse>() {
                                        @Override
                                        public MethodResponse invoke(FirewallRule firewallRule)
                                        {
                                            MethodResponse resp = new MethodResponse();

                                            resp.setReqId(request.getReqId());
                                            resp.setTimestamp(System.currentTimeMillis());

                                            if (firewallRule != null) {
                                                resp.setStatus(Status.COMPLETE);
                                                resp.setMessage(serverName + ":" + firewallRule.getName() + ":SUCCESS-ADDED");
                                            }
                                            else {
                                                resp.setStatus(Status.FAILURE);
                                                resp.setMessage(serverName + ":null:FAILED");
                                            }

                                            return resp;
                                        }
                                    }).recover(AzureSQLServiceOperations.getRecoverFunction(request)));
                        }
                    }

                    if (AzureSQLUtil.isValued(changes)) {
                        for (final FirewallRule fwr : changes) {
                            ServiceResource fwResource = new ServiceResource();

                            fwResource.setName(fwr.getName());
                            fwResource.setStartIPAddress(fwr.getStartIPAddress());
                            fwResource.setEndIPAddress(fwr.getEndIPAddress());

                            seqList.add(helper.updateFirewallRule(serverName, fwResource)
                                    .map(new Function<FirewallRule, MethodResponse>() {
                                        @Override
                                        public MethodResponse invoke(FirewallRule firewallRule)
                                        {
                                            MethodResponse resp = new MethodResponse();

                                            resp.setReqId(request.getReqId());
                                            resp.setTimestamp(System.currentTimeMillis());

                                            if (firewallRule != null) {
                                                resp.setStatus(Status.COMPLETE);
                                                resp.setMessage(serverName + ":" + firewallRule.getName() + ":SUCCESS-CHANGED");
                                            }
                                            else {
                                                resp.setStatus(Status.FAILURE);
                                                resp.setMessage(serverName + ":null:FAILED");
                                            }

                                            return resp;
                                        }
                                    }).recover(AzureSQLServiceOperations.getRecoverFunction(request)));
                        }
                    }

                    if (!seqList.isEmpty()) {
                        return Promise.sequence(seqList).map(new Function<List<MethodResponse>, MethodResponse>() {
                            @Override
                            public MethodResponse invoke(List<MethodResponse> results)
                            {
                                StringBuilder buf = new StringBuilder();
                                int failures = 0;
                                int totResults = results.size();

                                for (MethodResponse result : results) {
                                    failures += result.getStatus() == Status.FAILURE ? 1 : 0;
                                    buf.append("\n" + result.getMessage());
                                }

                                // if all failures, return FAILURE else return COMPLETE with a caveat message
                                if (failures == 0) {
                                    return AzureSQLUtil.buildResponse(MethodResponse.class, request.getReqId(), Status.COMPLETE,
                                            "SUCCESS\n" + buf.toString(), Level.TRACE, AzureSQLServiceOperations.logger, null);
                                }
                                else if (failures < totResults) {
                                    return AzureSQLUtil.buildResponse(MethodResponse.class, request.getReqId(), Status.COMPLETE,
                                            "Not all processFirewallChange calls were successful.\n" + buf.toString(),
                                            Level.WARN, AzureSQLServiceOperations.logger, null);
                                }
                                else {
                                    return AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                                            "All processFirewallChange calls resulted in failures.\n" + buf.toString(),
                                            AzureSQLServiceOperations.logger);
                                }
                            }
                        });
                    }
                    else {
                        return Promise.pure(AzureSQLUtil.buildResponse(MethodResponse.class, request.getReqId(), Status.COMPLETE,
                                "No FirewallRule sequence to process.  No work to do.", Level.DEBUG,
                                AzureSQLServiceOperations.logger, null));
                    }
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'processFirewallRuleChange' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                        "No serverName value provided.  FirewallRule changes cannot be processed.",
                        AzureSQLServiceOperations.logger));
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will process a server password change for a given SQL server. The input method argument must be configured
     * with the proper method name (@see AzureSQLConstants.METHOD_PROCESS_SERVER_CHANGE). The name of the server, current password
     * value, and new password value must be assigned to the arguments list using the property names
     * 
     * @see AzureSQLConstants.PROP_SERVER_NAME_STR
     * @see AzureSQLConstants.PROP_PASSWORD_STR
     * @see AzureSQLConstants.PROP_NEW_PASSWORD_STR This method will extract the name of the server and password info from the
     *      arguments list and make a call to the helper.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the Server object result.
     */
    public static Promise<MethodResponse> processServerChange(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_PROCESS_SERVER_CHANGE;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            String serverName = AzureSQLUtil.getServerName(request.getArguments());
            String password = AzureSQLUtil.getPassword(request.getArguments());
            String newPassword = AzureSQLUtil.getNewPassword(request.getArguments());

            // TODO - whc - the password is an argument in case we want to require the current password before a new
            //              password can be set.  The value of password is not checked yet.

            if (AzureSQLUtil.isValued(serverName) && AzureSQLUtil.isValued(newPassword)) {
                AzureSQLServiceOperations.logger.trace("Changing password for server '" + serverName + "' from '"
                        + AzureSQLUtil.maskPrivateKey(password) + "' to '" + AzureSQLUtil.maskPrivateKey(newPassword) + "'");

                try {
                    AdministratorLoginPassword newPw = new AdministratorLoginPassword();

                    newPw.setValue(newPassword);

                    return new AzureSQLRestHelper(request).changeServerPassword(serverName, newPw).map(
                            new Function<IHttpResponse, MethodResponse>() {
                                @Override
                                public MethodResponse invoke(IHttpResponse httpResp)
                                {
                                    MethodResponse resp = new MethodResponse();

                                    resp.setReqId(request.getReqId());
                                    resp.setTimestamp(System.currentTimeMillis());

                                    if (httpResp != null && httpResp.getStatusCode() == 200) {
                                        resp.setStatus(Status.COMPLETE);
                                    }
                                    else {
                                        StringBuilder msg = new StringBuilder();

                                        if (httpResp != null) {
                                            msg.append("Password change failed with code " + httpResp.getStatusCode());

                                            if (httpResp.getStatus() != null) {
                                                msg.append("\n" + httpResp.getStatus().getReason());
                                            }
                                        }
                                        else {
                                            msg.append("Unknown reason for failure");
                                        }

                                        resp.setStatus(Status.FAILURE);
                                        resp.setMessage(msg.toString());
                                    }

                                    return resp;
                                }
                            });
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'processServerChange' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                StringBuilder msg = new StringBuilder("Missing request arguments:");
                MethodResponse response = new MethodResponse();

                if (!AzureSQLUtil.isValued(serverName)) {
                    msg.append("\nServer Name");
                }

                if (!AzureSQLUtil.isValued(newPassword)) {
                    msg.append("\nNew Password");
                }

                msg.append("\n");

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg.toString());

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will check the health status of a SQL service. The input method argument must be configured with the proper
     * method name (@see AzureSQLConstants.METHOD_PROCESS_HEALTH_CHECK). The ServiceInstance object value must be assigned to the
     * arguments list using the property name
     * 
     * @see AzureSQLConstants.PROP_SERVICE This method will extract the ServiceInstance object from the arguments list and checks
     *      that the server, database, and firewall rule exists. The health check method will return COMPLETE unless an exception
     *      occurs. The details of the health check will be stored in the request.message. Three SUCCESS messages imply all is
     *      good. The message may have FAILED messages and/or exceptions. If the server does not exist, exceptions will be thrown.
     *      The response results list will contain all the objects found, i.e. Server, Database, FirewallRule. The variable name
     *      will match the property names from the constants file:
     * @see AzureSQLConstants.PROP_SERVER
     * @see AzureSQLConstants.PROP_DATABASE
     * @see AzureSQLConstants.PROP_FIREWALL_RULE
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a result of COMPLETE unless an exception is thrown, the server name
     *         is null, or the serviceInstance object is null in which case FAILURE will be returned.
     */
    public static Promise<MethodResponse> processHealthCheck(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_PROCESS_HEALTH_CHECK;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            ServiceInstance serviceInstance = AzureSQLUtil.getServiceInstance(request.getArguments());

            if (serviceInstance != null) {
                Server server = AzureSQLUtil.getServer(serviceInstance);
                Database database = AzureSQLUtil.getDatabase(serviceInstance);
                FirewallRule fwRule = AzureSQLUtil.getFirewallRule(serviceInstance);

                AzureSQLServiceOperations.logger.trace("\nChecking the health status for:\n" + "   Server Name = "
                        + (server != null ? server.getName() : null) + "\n" + "   Database Name = "
                        + (database != null ? database.getName() : null) + "\n" + "   FirewallRule Name = "
                        + (fwRule != null ? fwRule.getName() : null) + "\n");

                if (server != null) {
                    List<String> errMsgs = new ArrayList<String>();
                    List<Promise<MethodResponse>> seqList = new ArrayList<Promise<MethodResponse>>();

                    try {
                        MethodRequest svrReq = AzureSQLUtil.makeMethodRequest(request);

                        svrReq.setName(AzureSQLConstants.METHOD_GET_SERVER);
                        svrReq.getArguments().addAll(
                                AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                        AzureSQLConstants.PROP_SERVER_NAME_STR, server.getName()))));
                        seqList.add(AzureSQLServiceOperations.getServer(svrReq));

                        if (database != null) {
                            MethodRequest dbReq = AzureSQLUtil.makeMethodRequest(request);

                            dbReq.setName(AzureSQLConstants.METHOD_GET_DATABASE);
                            dbReq.getArguments().addAll(
                                    AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                            AzureSQLConstants.PROP_SERVER_NAME_STR, server.getName()), new KeyValuePair(
                                            AzureSQLConstants.PROP_DATABASE_NAME_STR, database.getName()))));
                            seqList.add(AzureSQLServiceOperations.getDatabase(dbReq));
                        }
                        else {
                            errMsgs.add("WARNING: The Database object is null.  This is expected only if the server was created without a database.");
                        }

                        if (fwRule != null) {
                            MethodRequest ruleReq = AzureSQLUtil.makeMethodRequest(request);

                            ruleReq.setName(AzureSQLConstants.METHOD_GET_FIREWALL_RULE);
                            ruleReq.getArguments().addAll(
                                    AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                            AzureSQLConstants.PROP_SERVER_NAME_STR, server.getName()), new KeyValuePair(
                                            AzureSQLConstants.PROP_FIREWALL_RULE_NAME_STR, fwRule.getName()))));
                            seqList.add(AzureSQLServiceOperations.getFirewallRule(ruleReq));
                        }
                        else {
                            errMsgs.add("WARNING: The FirewallRule object is null.  This is expected only if the server was created without a rule.");
                        }

                        AzureSQLServiceOperations.logger.trace("The health check sequence has " + seqList.size()
                                + " task(s) to run.");

                        return Promise.sequence(seqList).map(new Function<List<MethodResponse>, MethodResponse>() {

                            @Override
                            public MethodResponse invoke(List<MethodResponse> results)
                            {
                                if (results != null && !results.isEmpty()) {
                                    MethodResponse resp =
                                            AzureSQLUtil.buildResponse(MethodResponse.class, request.getReqId(), Status.COMPLETE,
                                                    "", Level.TRACE, AzureSQLServiceOperations.logger, null);
                                    StringBuilder buf = new StringBuilder("\nHealth Check Results:");
                                    ServiceInstance serviceInstance = new ServiceInstance();

                                    for (MethodResponse result : results) {
                                        // since we are looking for specific names, there should only be one item in the results - Database, Server, or FirewallRule
                                        String entityType =
                                                !result.getResults().isEmpty() ? result.getResults().get(0).getName() : "";

                                        if (result.getStatus() == Status.COMPLETE) {
                                            buf.append("\nSUCCESS - " + entityType);

                                            // assign entity to serviceInstance
                                            if (entityType.equals(AzureSQLConstants.PROP_DATABASE)) {
                                                serviceInstance =
                                                        AzureSQLUtil.updateDatabase(serviceInstance,
                                                                AzureSQLUtil.getDatabase(result.getResults()));
                                            }
                                            else if (entityType.equals(AzureSQLConstants.PROP_SERVER)) {
                                                serviceInstance =
                                                        AzureSQLUtil.updateServer(serviceInstance,
                                                                AzureSQLUtil.getServerObject(result.getResults()));
                                            }
                                            else if (entityType.equals(AzureSQLConstants.PROP_FIREWALL_RULE)) {
                                                serviceInstance =
                                                        AzureSQLUtil.updateFirewallRule(serviceInstance,
                                                                AzureSQLUtil.getFirewallRule(result.getResults()));
                                            }
                                            else {
                                                AzureSQLServiceOperations.logger
                                                        .warn("The results array contained an entity type of " + entityType
                                                                + " which was unexpected.  Unable to process this entity type.");
                                            }
                                        }
                                        else {
                                            buf.append("\nFAILED - " + result.getMessage());
                                        }
                                    }

                                    buf.append("\n");

                                    resp.setMessage(buf.toString());
                                    resp.getResults().add(
                                            AzureSQLUtil.makeMethodVariable(AzureSQLConstants.PROP_SERVICE_INSTANCE,
                                                    serviceInstance));

                                    return resp;
                                }
                                else {
                                    return AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                                            "The results object from the health check sequence was null.",
                                            AzureSQLServiceOperations.logger);
                                }
                            }
                        });
                    }
                    catch (Exception ex) {
                        String msg = "An exception occurred while performing the 'processHealthCheck' operation.";
                        AzureSQLServiceOperations.logger.error(msg, ex);
                        return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                                AzureSQLServiceOperations.logger, ex));
                    }
                }
                else {
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                            "The server object is null.  This is unexpected and a health check cannot be performed.",
                            AzureSQLServiceOperations.logger));
                }
            }
            else {
                return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                        "The ServiceInstance object is missing.  A health check cannot be assessed.",
                        AzureSQLServiceOperations.logger));
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will gather the components of a SQL service. The input method argument must be configured with the proper
     * method name (@see AzureSQLConstants.METHOD_SERVER_SYNC). The name of the server to be sync'd must be assigned to the
     * arguments list using the property name @see AzureSQLConstants.PROP_SERVER_NAME_STR This method will extract the Server name
     * string from the arguments list and checks that the server exists. If the server exists, all databases and firewall rules
     * will be gathered for the server. The method will return COMPLETE unless an exception occurs. The details of the health
     * check will be stored in the request.message. SUCCESS implies all is good. The message may have FAILED messages and/or
     * exceptions. If the server does not exist, exceptions will be thrown. The response results list will contain all the objects
     * found, i.e. Server, DatabaseList, FirewallRuleList. The variable name will match the property names from the constants
     * file:
     * 
     * @see AzureSQLConstants.PROP_SERVER
     * @see AzureSQLConstants.PROP_DATABASE_LIST
     * @see AzureSQLConstants.PROP_FIREWALL_RULE_LIST Unless an exception is thrown or the serverName is null, the lists returned
     *      will not be null - an empty list will be returned if no objects exist for that entity type.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a result of COMPLETE unless the serverName is null in which case,
     *         FAILURE will be returned.
     */
    public static Promise<MethodResponse> serverSync(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_SERVER_SYNC;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            String serverName = AzureSQLUtil.getServerName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName)) {
                AzureSQLServiceOperations.logger.trace("\nSync'ing Azure SQL Server '" + serverName + "'");

                List<Promise<MethodResponse>> seqList = new ArrayList<Promise<MethodResponse>>();

                try {
                    MethodRequest svrReq = AzureSQLUtil.makeMethodRequest(request);

                    svrReq.setName(AzureSQLConstants.METHOD_GET_SERVER);
                    svrReq.getArguments().addAll(
                            AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                    AzureSQLConstants.PROP_SERVER_NAME_STR, serverName))));
                    seqList.add(AzureSQLServiceOperations.getServer(svrReq));

                    MethodRequest dbReq = AzureSQLUtil.makeMethodRequest(request);

                    dbReq.setName(AzureSQLConstants.METHOD_LIST_DATABASES);
                    dbReq.getArguments().addAll(
                            AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                    AzureSQLConstants.PROP_SERVER_NAME_STR, serverName))));
                    seqList.add(AzureSQLServiceOperations.listDatabases(dbReq));

                    MethodRequest ruleReq = AzureSQLUtil.makeMethodRequest(request);

                    ruleReq.setName(AzureSQLConstants.METHOD_LIST_FIREWALL_RULES);
                    ruleReq.getArguments().addAll(
                            AzureSQLUtil.createStringArguments(Arrays.asList(new KeyValuePair(
                                    AzureSQLConstants.PROP_SERVER_NAME_STR, serverName))));
                    seqList.add(AzureSQLServiceOperations.listFirewallRules(ruleReq));

                    AzureSQLServiceOperations.logger.trace("The server sync sequence has " + seqList.size() + " task(s) to run.");

                    return Promise.sequence(seqList).map(new Function<List<MethodResponse>, MethodResponse>() {

                        @Override
                        public MethodResponse invoke(List<MethodResponse> results)
                        {
                            if (results != null && !results.isEmpty()) {
                                MethodResponse resp =
                                        AzureSQLUtil.buildResponse(MethodResponse.class, request.getReqId(), Status.COMPLETE, "",
                                                Level.TRACE, AzureSQLServiceOperations.logger, null);
                                StringBuilder buf = new StringBuilder("\nServer Sync Results:");

                                for (MethodResponse result : results) {
                                    String entityType =
                                            !result.getResults().isEmpty() ? result.getResults().get(0).getName() : "";

                                    if (result.getStatus() == Status.COMPLETE) {
                                        buf.append("\nSUCCESS - " + entityType);
                                        resp.getResults().addAll(result.getResults());
                                    }
                                    else {
                                        buf.append("\nFAILED - " + result.getMessage());
                                    }
                                }

                                buf.append("\n");

                                resp.setMessage(buf.toString());

                                return resp;
                            }
                            else {
                                return AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                                        "The results object from the Server Sync sequence was null.",
                                        AzureSQLServiceOperations.logger);
                            }
                        }
                    });
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'serverSync' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                        "The Server name is missing.  A server sync cannot be performed.", AzureSQLServiceOperations.logger));
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will get a list of databases for a given SQL server. The input method argument must be configured with the
     * proper method name (@see AzureSQLConstants.METHOD_LIST_DATABASES). The name of the server must be assigned to the arguments
     * list using the property name @see AzureSQLConstants.PROP_SERVER_NAME_STR. This method will extract the name of the server
     * from the arguments list and make a call to the helper.
     * 
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the DatabaseList object result.
     */
    public static Promise<MethodResponse> listDatabases(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_LIST_DATABASES;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            String serverName = AzureSQLUtil.getServerName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName)) {
                try {
                    return new AzureSQLRestHelper(request).listDatabases(serverName).map(
                            new Function<DatabaseList, MethodResponse>() {
                                @Override
                                public MethodResponse invoke(DatabaseList databaseList)
                                {
                                    String variableName = AzureSQLConstants.PROP_DATABASE_LIST;
                                    MethodResponse response = new MethodResponse();

                                    response.setReqId(request.getReqId());

                                    // send back and empty list if null
                                    if (databaseList == null) {
                                        databaseList = new DatabaseList();
                                    }

                                    response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, databaseList));
                                    response.setStatus(Status.COMPLETE);
                                    response.setTimestamp(System.currentTimeMillis());

                                    return response;
                                }
                            });
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'listDatabases' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                String msg = "No value found for the server name in the request arguments.";
                MethodResponse response = new MethodResponse();

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg);

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will get a specific database for a given SQL server. The input method argument must be configured with the
     * proper method name (@see AzureSQLConstants.METHOD_GET_DATABASE). The name of the server and the name of the database must
     * be assigned to the arguments list using the property names
     * 
     * @see AzureSQLConstants.PROP_DATABASE_NAME_STR and AzureSQLConstants.PROP_SERVER_NAME_STR. This method will extract the name
     *      of the server and database from the arguments list and make a call to the helper.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the Database object or null if not found.
     */
    public static Promise<MethodResponse> getDatabase(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_GET_DATABASE;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            final String serverName = AzureSQLUtil.getServerName(request.getArguments());
            final String databaseName = AzureSQLUtil.getDatabaseName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName) && AzureSQLUtil.isValued(databaseName)) {
                try {
                    return new AzureSQLRestHelper(request).getDatabase(serverName, databaseName)
                            .map(new Function<Database, MethodResponse>() {
                                @Override
                                public MethodResponse invoke(Database database)
                                {
                                    String variableName = AzureSQLConstants.PROP_DATABASE;
                                    MethodResponse response = new MethodResponse();

                                    response.setReqId(request.getReqId());
                                    response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, database));
                                    response.setStatus(Status.COMPLETE);
                                    response.setTimestamp(System.currentTimeMillis());

                                    return response;
                                }
                            }).recover(AzureSQLServiceOperations.getRecoverFunction(request));
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'getDatabase' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                String msg =
                        "No value found for the server name [" + serverName + "] and/or database name [" + databaseName
                                + " in the request arguments.";
                MethodResponse response = new MethodResponse();

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg);

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will get a list of firewall rules for a given SQL server. The input method argument must be configured with
     * the proper method name (@see AzureSQLConstants.METHOD_LIST_FIREWALL_RULES). The name of the server must be assigned to the
     * arguments list using the property name @see AzureSQLConstants.PROP_SERVER_NAME_STR. This method will extract the name of
     * the server from the arguments list and make a call to the helper.
     * 
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the FirewallRuleList object result.
     */
    public static Promise<MethodResponse> listFirewallRules(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_LIST_FIREWALL_RULES;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            String serverName = AzureSQLUtil.getServerName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName)) {
                try {
                    return new AzureSQLRestHelper(request).listFirewallRules(serverName).map(
                            new Function<FirewallRuleList, MethodResponse>() {
                                @Override
                                public MethodResponse invoke(FirewallRuleList firewallRuleList)
                                {
                                    String variableName = AzureSQLConstants.PROP_FIREWALL_RULE_LIST;
                                    MethodResponse response = new MethodResponse();

                                    response.setReqId(request.getReqId());

                                    // send back and empty list if null
                                    if (firewallRuleList == null) {
                                        firewallRuleList = new FirewallRuleList();
                                    }

                                    response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, firewallRuleList));
                                    response.setStatus(Status.COMPLETE);
                                    response.setTimestamp(System.currentTimeMillis());

                                    return response;
                                }
                            });
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'listFirewallRules' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                String msg = "No value found for the server name in the request arguments.";
                MethodResponse response = new MethodResponse();

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg);

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will get a specific firewall rule for a given SQL server. The input method argument must be configured with
     * the proper method name (@see AzureSQLConstants.METHOD_GET_FIREWALL_RULE). The name of the server and the name of the
     * firewall rule must be assigned to the arguments list using the property names
     * 
     * @see AzureSQLConstants.PROP_FIREWALL_RULE_NAME_STR and AzureSQLConstants.PROP_SERVER_NAME_STR. This method will extract the
     *      name of the server and firewall rule from the arguments list and make a call to the helper.
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the FirewallRule object or null if not
     *         found.
     */
    public static Promise<MethodResponse> getFirewallRule(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_GET_FIREWALL_RULE;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            final String serverName = AzureSQLUtil.getServerName(request.getArguments());
            final String ruleName = AzureSQLUtil.getFirewallRuleName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName) && AzureSQLUtil.isValued(ruleName)) {
                try {
                    return new AzureSQLRestHelper(request).getFirewallRule(serverName, ruleName)
                            .map(new Function<FirewallRule, MethodResponse>() {
                                @Override
                                public MethodResponse invoke(FirewallRule firewallRule)
                                {
                                    String variableName = AzureSQLConstants.PROP_FIREWALL_RULE;
                                    MethodResponse response = new MethodResponse();

                                    response.setReqId(request.getReqId());
                                    response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, firewallRule));
                                    response.setStatus(Status.COMPLETE);
                                    response.setTimestamp(System.currentTimeMillis());

                                    return response;
                                }
                            }).recover(AzureSQLServiceOperations.getRecoverFunction(request));
                }
                catch (Exception ex) {
                    String msg = "An exception occurred while performing the 'getFirewallRule' operation.";
                    AzureSQLServiceOperations.logger.error(msg, ex);
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(), msg,
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                String msg =
                        "No value found for the server name [" + serverName + "] and/or firewall rule name [" + ruleName
                                + " in the request arguments.";
                MethodResponse response = new MethodResponse();

                AzureSQLServiceOperations.logger.warn(msg);

                response.setReqId(request.getReqId());
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(Status.FAILURE);
                response.setMessage(msg);

                return Promise.pure(response);
            }
        }
        else {
            String msg =
                    "The request object cannot be null and the method name [" + (request != null ? request.getName() : null)
                            + "] must be " + methodName;
            AzureSQLServiceOperations.logger.error(msg);
            return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                    : AzureSQLConstants.UNDEFINED, msg, AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will get a generic list of servers (ServiceResources). The input method argument must be configured with the
     * proper method name (@see AzureSQLConstants.METHOD_LIST_SERVERS). There are no parameters for this call. This method will
     * make a call to the helper.
     * 
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the ServerList object result.
     */
    public static Promise<MethodResponse> listServers(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_LIST_SERVERS;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            try {
                return new AzureSQLRestHelper(request).getServerList().map(new Function<ServerList, MethodResponse>() {
                    @Override
                    public MethodResponse invoke(ServerList serverList)
                    {
                        String variableName = AzureSQLConstants.PROP_SERVER_LIST;
                        MethodResponse response = new MethodResponse();

                        response.setReqId(request.getReqId());

                        // send back an empty list if null
                        if (serverList == null) {
                            serverList = new ServerList();
                        }

                        response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, serverList));
                        response.setStatus(Status.COMPLETE);
                        response.setTimestamp(System.currentTimeMillis());

                        return response;
                    }
                });
            }
            catch (Exception ex) {
                return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                        "An exception occurred while performing the 'listServers' operation.", AzureSQLServiceOperations.logger,
                        ex));
            }
        }
        else {
            return Promise
                    .pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                            : AzureSQLConstants.UNDEFINED, "The request object cannot be null and the method name ["
                            + (request != null ? request.getName() : null) + "] must be " + methodName,
                            AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will get a specific server from a server list (ServiceResources). The input method argument must be
     * configured with the proper method name (@see AzureSQLConstants.METHOD_GET_SERVER). The name of the server will be found in
     * the argument list. @see AzureSQLConstants.PROP_SERVER_NAME_STR This method will make a call to the helper.
     * 
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the Server object result.
     */
    public static Promise<MethodResponse> getServer(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_GET_SERVER;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            final String serverName = AzureSQLUtil.getServerName(request.getArguments());

            if (AzureSQLUtil.isValued(serverName)) {
                try {
                    return new AzureSQLRestHelper(request).getServer(serverName).map(new Function<Server, MethodResponse>() {
                        @Override
                        public MethodResponse invoke(Server server)
                        {
                            String variableName = AzureSQLConstants.PROP_SERVER;
                            MethodResponse response = new MethodResponse();

                            response.setReqId(request.getReqId());

                            if (server != null) {
                                response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, server));
                                response.setStatus(Status.COMPLETE);
                            }
                            else {
                                String msg = "No Server with name " + serverName + " exists.";

                                AzureSQLServiceOperations.logger.error(msg);

                                response.setStatus(Status.FAILURE);
                                response.setMessage(msg);
                            }

                            response.setTimestamp(System.currentTimeMillis());

                            return response;
                        }
                    }).recover(AzureSQLServiceOperations.getRecoverFunction(request));
                }
                catch (Exception ex) {
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                            "An exception occurred while performing the 'listServers' operation.",
                            AzureSQLServiceOperations.logger, ex));
                }
            }
            else {
                return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                        "No value found for the server name in the request arguments.", AzureSQLServiceOperations.logger));
            }
        }
        else {
            return Promise
                    .pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                            : AzureSQLConstants.UNDEFINED, "The request object cannot be null and the method name ["
                            + (request != null ? request.getName() : null) + "] must be " + methodName,
                            AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This operation will create an SQL server that will be used for database creations. The input method argument must be
     * configured with the proper method name (@see AzureSQLConstants.METHOD_CREATE_SERVER). The arguments will be mapped to a
     * Server object. This method will make a call to the helper.
     * 
     * @param MethodRequest
     *            request - the configuration of the operation request. It will identify the method and any required method
     *            parameters.
     * @return Promise<MethodResponse> - the response will have a serialized version of the ServerName object result.
     */
    public static Promise<MethodResponse> createServer(final MethodRequest request)
    {
        String methodName = AzureSQLConstants.METHOD_CREATE_SERVER;

        if (AzureSQLUtil.isValued(request) && methodName.equals(request.getName())) {
            try {
                // get the server input object from the request - it will be serialized
                Server server = AzureSQLUtil.getServerObject(request.getArguments());

                if (server != null) {
                    return new AzureSQLRestHelper(request).createServer(server).map(new Function<ServerName, MethodResponse>() {
                        @Override
                        public MethodResponse invoke(ServerName serverName)
                        {
                            String variableName = AzureSQLConstants.PROP_SERVER_NAME;
                            MethodResponse response = new MethodResponse();

                            response.setReqId(request.getReqId());
                            response.getResults().add(AzureSQLUtil.makeMethodVariable(variableName, serverName));
                            response.setStatus(Status.COMPLETE);
                            response.setTimestamp(System.currentTimeMillis());

                            return response;
                        }
                    });
                }
                else {
                    return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                            "The 'createServer' request is missing the required Server parameter.  Server creation failed.",
                            AzureSQLServiceOperations.logger));
                }
            }
            catch (Exception ex) {
                return Promise.pure(AzureSQLUtil.buildFailedMethodResponse(request.getReqId(),
                        "An exception occurred while performing the 'createServer' operation.", AzureSQLServiceOperations.logger,
                        ex));
            }
        }
        else {
            return Promise
                    .pure(AzureSQLUtil.buildFailedMethodResponse(request != null ? request.getReqId()
                            : AzureSQLConstants.UNDEFINED, "The request object cannot be null and the method name ["
                            + (request != null ? request.getName() : null) + "] must be " + methodName,
                            AzureSQLServiceOperations.logger));
        }
    }

    /**
     * This method will validate the properties attributed to a Server object.
     * 
     * @param Server
     *            server - server object from service
     * @return String - any server validation error messages
     */
    private static String validateServerInfo(Server server)
    {
        StringBuilder buf = new StringBuilder();
        //Server validations
        String adminLogin = server.getAdministratorLogin();
        if (!AzureSQLUtil.isValued(adminLogin)) {
            buf.append("Required field Admin Login was not provided\n");
        }
        String adminPass = server.getAdministratorLoginPassword();
        if (!AzureSQLUtil.isValued(adminPass)) {
            buf.append("Required field Admin Password was not provided\n");
        }
        else { // validate password
            StringBuilder passwordError = new StringBuilder();
            passwordError.append("Invalid Admin Password. \n");
            passwordError.append("   Admin Password can not be the same as the Admin Login\n");
            passwordError.append("   Admin Password must be at least 8 characters long.\n");
            passwordError.append("   Admin Password can not be more than 128 characters long.\n");
            passwordError.append("   Admin Password must contain characters from 3 of the following 4 categories:\n");
            passwordError.append("    - Latin uppercase letters (A through Z)\n");
            passwordError.append("    - Latin lowercase letters (a through z)\n");
            passwordError.append("    - Base 10 digits (0 through 9)\n");
            passwordError
                    .append("    - Non-alphanumeric characters such as: exclamation point (!), dollar sign ($), number sign (#), or percent (%)\n");
            if (adminPass.equals(adminLogin) || adminPass.length() < 8 || adminPass.length() > 128) {
                buf.append(passwordError.toString());
            }
            else {
                int validPatternsMatched = 0;
                if (AzureSQLConstants.HAS_LOWER_CASE.matcher(adminPass).find()) {
                    validPatternsMatched++;
                }
                if (AzureSQLConstants.HAS_UPPER_CASE.matcher(adminPass).find()) {
                    validPatternsMatched++;
                }
                if (AzureSQLConstants.HAS_NUMBER.matcher(adminPass).find()) {
                    validPatternsMatched++;
                }
                if (AzureSQLConstants.HAS_SPECIAL_CHAR.matcher(adminPass).find()) {
                    validPatternsMatched++;
                }
                if (validPatternsMatched < 3) {
                    buf.append(passwordError.toString());
                }
            }
        }
        String location = server.getLocation();
        if (!AzureSQLUtil.isValued(location)) {
            buf.append("Required field Location was not provided\n");
        }
        return buf.toString();
    }

    /**
     * This method will validate the properties attributed to a Database object.
     * 
     * @param Database
     *            db - database object from service
     * @param String
     *            maxSize - desired maximum size of the database
     * @return String - any database validation error messages
     */
    private static String validateDatabaseInfo(Database db, String maxSize)
    {
        StringBuilder buf = new StringBuilder();

        String dbName = db.getName();
        String edition = db.getEdition();
        String collation = db.getCollationName();
        String serviceObjectiveID = db.getServiceObjectiveId();
        if (!AzureSQLUtil.isValued(dbName)) {
            if (AzureSQLUtil.isValued(edition)) {
                buf.append("Cannot create a Database without a Database Name. ");
                buf.append("Either clear the Edition field or provide a Database Name.\n");
            }
            if (AzureSQLUtil.isValued(collation)) {
                buf.append("Cannot create a Database without a Database Name. ");
                buf.append("Either clear the Collation field or provide a Database Name.\n");
            }
            if (AzureSQLUtil.isValued(maxSize)) {
                buf.append("Cannot create a Database without a Database Name. ");
                buf.append("Either clear the Max Size field or provide a Database Name.\n");
            }
            if (AzureSQLUtil.isValued(serviceObjectiveID)) {
                buf.append("Cannot create a Database without a Database Name. ");
                buf.append("Either clear the Service Objective ID field or provide a Database Name.\n");
            }
        }
        else {
            if (!AzureSQLUtil.isValued(edition)) {
                buf.append("When a Database Name is provided the Edition field is required.\n");
            }
        }
        return buf.toString();
    }

    /**
     * This method will validate the properties attributed to a FirewallRule object.
     * 
     * @param FirewallRule
     *            firewall - firewall rule object from service
     * @return String - any firewall rule validation error messages
     */
    private static String validateFirewallRuleInfo(FirewallRule firewall)
    {
        StringBuilder buf = new StringBuilder();

        String firewallName = firewall.getName();
        String startIP = firewall.getStartIPAddress();
        String endIP = firewall.getEndIPAddress();
        if (AzureSQLUtil.isValued(firewallName)) {
            boolean validStart = true;
            boolean validEnd = true;
            if (!AzureSQLUtil.isValued(startIP)) {
                buf.append("When a Firewall Rule Name is provided the Starting Allowed IP field is required.\n");
                validStart = false;
            }
            else {
                // make sure the IP is valid. i.e. 0.0.0.0 <= IP <= 255.255.255.255
                String[] ipParts = startIP.split("\\.");
                boolean printTooBig = true;
                boolean printTooSmall = true;
                for (String part : ipParts) {
                    if (Integer.parseInt(part) > 255 && printTooBig) {
                        buf.append("Invalid IP value for Starting Allowed IP. No number in the IP address can be larger than 255\n");
                        validStart = false;
                        printTooBig = false;
                    }
                    else if (Integer.parseInt(part) < 0 && printTooSmall) {
                        buf.append("Invalid IP value for Starting Allowed IP. No number in the IP address can be smaller than 0\n");
                        validStart = false;
                        printTooSmall = false;
                    }
                }
            }
            if (!AzureSQLUtil.isValued(endIP)) {
                buf.append("When a Firewall Rule Name is provided the Ending Allowed IP field is required.\n");
                validEnd = false;
            }
            else {
                String[] ipParts = endIP.split("\\.");
                boolean printTooBig = true;
                boolean printTooSmall = true;
                for (String part : ipParts) {
                    if (Integer.parseInt(part) > 255 && printTooBig) {
                        buf.append("Invalid IP value for Ending Allowed IP. No number in the IP address can be larger than 255\n");
                        validEnd = false;
                        printTooBig = false;
                    }
                    else if (Integer.parseInt(part) < 0 && printTooSmall) {
                        buf.append("Invalid IP value for Ending Allowed IP. No number in the IP address can be smaller than 0\n");
                        validEnd = false;
                        printTooSmall = false;
                    }
                }
            }
            if (validStart && validEnd && AzureSQLUtil.ipToLong(endIP) < AzureSQLUtil.ipToLong(startIP)) {
                buf.append("The Ending Allowed IP cannot be smaller than the Starting Allowed IP\n");
            }
        }
        else {
            if (AzureSQLUtil.isValued(startIP)) {
                buf.append("Cannot create a Firewall Rule without a Firewall Rule Name. ");
                buf.append("Either clear the Starting Allowed IP Field or provide a Firewall Rule Name.\n");
            }
            if (AzureSQLUtil.isValued(endIP)) {
                buf.append("Cannot create a Firewall Rule without a Firewall Rule Name. ");
                buf.append("Either clear the Ending Allowed IP Field or provide a Firewall Rule Name.\n");
            }
        }
        return buf.toString();
    }

    /**
     * This method will log and validate objects found in the SQL ServiceInstance. If all required properties are defined, the
     * return value will be an empty list. If missing properties are found, a message will be added for each issue.
     * 
     * @param ServiceInstance
     *            serviceInstance - SQL service that contains the objects to be verified
     * @return List<String> - list of error messages. The list will be empty if no issues were found
     */
    private static List<String> validateProperties(ServiceInstance serviceInstance)
    {
        List<String> retval = new ArrayList<String>();
        Database database = AzureSQLUtil.getDatabase(serviceInstance);
        Server server = AzureSQLUtil.getServer(serviceInstance);
        FirewallRule fwRule = AzureSQLUtil.getFirewallRule(serviceInstance);
        String serverName = null;
        String adminLogin = null;
        String adminPassword = null;

        if (AzureSQLServiceOperations.logger.isTraceEnabled()) {
            StringBuilder buf = new StringBuilder("\nAzure SQL Properties:");

            if (server != null) {
                serverName = server.getName();
                adminLogin = server.getAdministratorLogin();
                adminPassword = server.getAdministratorLoginPassword();

                buf.append("\nServer Name: " + serverName);
                buf.append("\nAdmin Login: " + adminLogin);
                buf.append("\nAdmin Password: " + AzureSQLUtil.maskPrivateKey(adminPassword));
                buf.append("\nLocation: " + server.getLocation());
            }
            else {
                buf.append("\nNo Server properties defined");
            }

            if (database != null) {
                buf.append("\nDatabase Name: " + database.getName());
                buf.append("\nEdition: " + database.getEdition());
                buf.append("\nCollation: " + database.getCollationName());
                buf.append("\nMax Size Bytes: " + database.getMaxSizeBytes());
                buf.append("\nService Objective ID: " + database.getServiceObjectiveId());
            }
            else {
                buf.append("\nNo Database properties defined");
            }

            if (fwRule != null) {
                buf.append("\nFirewall Rule Name: " + fwRule.getName());
                buf.append("\nFirewall Rule Min IP: " + fwRule.getStartIPAddress());
                buf.append("\nFirewall Rule Max IP: " + fwRule.getEndIPAddress());
            }
            else {
                buf.append("\nNo Firewall Rule properties defined");
            }

            buf.append("\n");

            AzureSQLServiceOperations.logger.trace(buf.toString());
        }

        // check for required fields - serverName, login, password
        if (!AzureSQLUtil.isValued(serverName)) {
            retval.add("The server name value is missing.");
        }

        if (!AzureSQLUtil.isValued(adminLogin)) {
            retval.add("The administrator login value is missing.");
        }

        if (!AzureSQLUtil.isValued(adminPassword)) {
            retval.add("The administrator password value is missing.");
        }

        return retval;
    }

    /**
     * This method will log and validate objects found in the SQL ServiceInstance. If all required properties are defined, the
     * return value will be an empty list. If missing properties are found, a message will be added for each issue. This is used
     * by the "basic" reconfigure. The server and database names are required.
     * 
     * @param ServiceInstance
     *            serviceInstance - SQL service that contains the objects to be verified
     * @return List<String> - list of error messages. The list will be empty if no issues were found
     */
    private static List<String> validatePropertiesBasic(ServiceInstance serviceInstance)
    {
        List<String> retval = new ArrayList<String>();
        Database database = AzureSQLUtil.getDatabase(serviceInstance);
        Server server = AzureSQLUtil.getServer(serviceInstance);
        FirewallRule fwRule = AzureSQLUtil.getFirewallRule(serviceInstance);
        String serverName = null;
        String adminLogin = null;
        String adminPassword = null;

        StringBuilder buf = new StringBuilder("\nAzure SQL Properties:");

        if (server != null) {
            serverName = server.getName();
            adminLogin = server.getAdministratorLogin();
            adminPassword = server.getAdministratorLoginPassword();

            buf.append("\nServer Name: " + serverName);
            buf.append("\nAdmin Login: " + adminLogin);
            buf.append("\nAdmin Password: " + AzureSQLUtil.maskPrivateKey(adminPassword));
            buf.append("\nLocation: " + server.getLocation());
        }
        else {
            buf.append("\nNo Server properties defined");
        }

        if (database != null) {
            buf.append("\nDatabase Name: " + database.getName());
            buf.append("\nEdition: " + database.getEdition());
            buf.append("\nCollation: " + database.getCollationName());
            buf.append("\nMax Size Bytes: " + database.getMaxSizeBytes());
            buf.append("\nService Objective ID: " + database.getServiceObjectiveId());
        }
        else {
            buf.append("\nNo Database properties defined");
        }

        if (fwRule != null) {
            buf.append("\nFirewall Rule Name: " + fwRule.getName());
            buf.append("\nFirewall Rule Min IP: " + fwRule.getStartIPAddress());
            buf.append("\nFirewall Rule Max IP: " + fwRule.getEndIPAddress());
        }
        else {
            buf.append("\nNo Firewall Rule properties defined");
        }

        buf.append("\n");
        if (AzureSQLServiceOperations.logger.isTraceEnabled()) {
            AzureSQLServiceOperations.logger.trace(buf.toString());
        }

        // check for required fields - serverName, login, password
        if (!AzureSQLUtil.isValued(serverName)) {
            retval.add("The server name value is missing.");
        }

        if (!AzureSQLUtil.isValued(database) || !AzureSQLUtil.isValued(database.getName())) {
            retval.add("The database name value is missing.");
        }

        return retval;
    }

    /**
     * This method will return a new Function that can be used in the 'recover' option of a Promise. The recover operation will
     * properly handle an exception.
     * 
     * @param ServiceInstanceRequest
     *            request - request object that initiated the process
     * @return Function<Throwable,ServiceProviderResponse> - function object to be used by the Promise recover operation
     */
    private static Function<Throwable, ServiceProviderResponse> getRecoverFunction(final ServiceInstanceRequest request)
    {
        return new Function<Throwable, ServiceProviderResponse>() {
            @Override
            public ServiceProviderResponse invoke(Throwable arg)
            {
                ServiceProviderResponse resp = new ServiceProviderResponse();
                resp.getModified().add(AzureSQLUtil.degradeService(request.getServiceInstance(), arg.getMessage()));
                resp.setReqId(request.getReqId());
                resp.setMessage(arg.getMessage());
                resp.setStatus(Status.FAILURE);
                resp.setTimestamp(System.currentTimeMillis());

                return resp;
            }
        };
    }

    /**
     * This method will return a new Function that can be used in the 'recover' option of a Promise. The recover operation will
     * properly handle an exception.
     * 
     * @param MethodRequest
     *            request - request object that initiated the process
     * @return Function<Throwable,MethodResponse> - function object to be used by the Promise recover operation
     */
    private static Function<Throwable, MethodResponse> getRecoverFunction(final MethodRequest request)
    {
        return new Function<Throwable, MethodResponse>() {
            @Override
            public MethodResponse invoke(Throwable arg)
            {
                MethodResponse resp = new MethodResponse();

                resp.setReqId(request.getReqId());
                resp.setMessage(arg.getMessage());
                resp.setStatus(Status.FAILURE);
                resp.setTimestamp(System.currentTimeMillis());

                return resp;
            }
        };
    }

    /**
     * This method will return a new Function to be used by flatMap operation of a Promise to find a server. If the server is
     * found, the reconfigure work can proceed. If the server is not found, an exception is returned via the Promise.
     * 
     * @param ServiceInstanceReconfigureRequest
     *            request - reconfigure request that initiated the process
     * @param ServiceInstance
     *            serviceInstanceOrig - current service information
     * @param ServiceInstance
     *            serviceInstance - new service information
     * @param AzureSQLRestHelper
     *            helper - object that has helper methods for accessing Azure REST calls
     * @return Function<Server,Promise<ServiceProviderResponse>> - function that can be used in the flatMap operation
     */
    private static Function<Server, Promise<ServiceProviderResponse>> getServerPromiseFunction(
            final ServiceInstanceReconfigureRequest request, final ServiceInstance serviceInstanceOrig,
            final ServiceInstance serviceInstance, final AzureSQLRestHelper helper)
    {
        final Server serverOrig = AzureSQLUtil.getServer(serviceInstanceOrig);
        final Database databaseOrig = AzureSQLUtil.getDatabase(serviceInstanceOrig);
        final FirewallRule fwRuleOrig = AzureSQLUtil.getFirewallRule(serviceInstanceOrig);
        final Server serverNew = AzureSQLUtil.getServer(serviceInstance);
        final Database databaseNew = AzureSQLUtil.getDatabase(serviceInstance);
        final FirewallRule fwRuleNew = AzureSQLUtil.getFirewallRule(serviceInstance);

        return new Function<Server, Promise<ServiceProviderResponse>>() {
            String curServerName = serverOrig != null ? serverOrig.getName() : null;
            String curServerPw = serverOrig != null ? serverOrig.getAdministratorLoginPassword() : null;
            String curDbName = databaseOrig != null ? databaseOrig.getName() : null;
            String curDbEdition = databaseOrig != null ? databaseOrig.getEdition() : null;
            String curDbObjId = databaseOrig != null ? databaseOrig.getServiceObjectiveId() : null;
            Long curDbMaxBytes = databaseOrig != null && databaseOrig.getMaxSizeBytes() != null ? databaseOrig.getMaxSizeBytes()
                    : 0L;
            String curFwRuleName = fwRuleOrig != null ? fwRuleOrig.getName() : null;
            String curFwStartIp = fwRuleOrig != null ? fwRuleOrig.getStartIPAddress() : null;
            String curFwEndIp = fwRuleOrig != null ? fwRuleOrig.getEndIPAddress() : null;
            String newServerPw = serverNew != null ? serverNew.getAdministratorLoginPassword() : null;
            String newDbName = databaseNew != null ? databaseNew.getName() : null;
            String newDbEdition = databaseNew != null ? databaseNew.getEdition() : null;
            String newDbObjId = databaseNew != null ? databaseNew.getServiceObjectiveId() : null;
            Long newDbMaxBytes = databaseNew != null && databaseNew.getMaxSizeBytes() != null ? databaseNew.getMaxSizeBytes()
                    : 0L;
            String newFwRuleName = fwRuleNew != null ? fwRuleNew.getName() : null;
            String newFwStartIp = fwRuleNew != null ? fwRuleNew.getStartIPAddress() : null;
            String newFwEndIp = fwRuleNew != null ? fwRuleNew.getEndIPAddress() : null;
            boolean isNewDatabase = !AzureSQLUtil.isValued(curDbName) && AzureSQLUtil.isValued(newDbName);
            boolean isDbNameChange = AzureSQLUtil.isValued(newDbName) && AzureSQLUtil.isValued(curDbName)
                    && !curDbName.equals(newDbName);
            boolean isNewDbEdition = AzureSQLUtil.isValued(newDbEdition) && AzureSQLUtil.isValued(curDbEdition)
                    && !curDbEdition.equals(newDbEdition);
            boolean isNewDbObjId = AzureSQLUtil.isValued(newDbObjId) && AzureSQLUtil.isValued(curDbObjId)
                    && !curDbObjId.equals(newDbObjId);
            boolean isNewDbSize = AzureSQLUtil.isValued(newDbMaxBytes) && AzureSQLUtil.isValued(curDbMaxBytes)
                    && newDbMaxBytes.longValue() > 0 && curDbMaxBytes.longValue() != newDbMaxBytes.longValue();
            boolean isNewServerPw = AzureSQLUtil.isValued(newServerPw) && AzureSQLUtil.isValued(curServerPw)
                    && !curServerPw.equals(newServerPw);
            boolean isDeleteFwRule = AzureSQLUtil.isValued(newFwRuleName) && AzureSQLUtil.isValued(curFwRuleName)
                    && !curFwRuleName.equals(newFwRuleName);
            boolean isNewStartIp = AzureSQLUtil.isValued(newFwStartIp) && AzureSQLUtil.isValued(curFwStartIp)
                    && !curFwStartIp.equals(newFwStartIp);
            boolean isNewEndIp = AzureSQLUtil.isValued(newFwEndIp) && AzureSQLUtil.isValued(curFwEndIp)
                    && !curFwEndIp.equals(newFwEndIp);

            @Override
            public Promise<ServiceProviderResponse> invoke(Server server) throws RuntimeException
            {
                ServiceProviderResponse resp = new ServiceProviderResponse();
                List<Promise<MethodResponse>> seqList = new ArrayList<Promise<MethodResponse>>();
                ServiceInstance siResp = AzureSQLUtil.clone(serviceInstanceOrig); // start with the original object

                resp.setReqId(request.getReqId());

                if (server != null) {
                    AzureSQLServiceOperations.logger.trace("Found server '" + curServerName + "'");

                    // start packaging Server changes
                    if (isNewServerPw) {
                        MethodRequest svrRequest = new MethodRequest();
                        List<KeyValuePair> kvps = new ArrayList<KeyValuePair>();
                        Server svr = AzureSQLUtil.getServer(serviceInstanceOrig);

                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_SERVER_NAME_STR, svr.getName()));
                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_PASSWORD_STR, svr.getAdministratorLoginPassword()));
                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_NEW_PASSWORD_STR, newServerPw));

                        svr.setAdministratorLoginPassword(newServerPw);

                        svrRequest.setReqId(request.getReqId());
                        svrRequest.setName(AzureSQLConstants.METHOD_PROCESS_SERVER_CHANGE);
                        svrRequest.setProvider(request.getProvider());
                        svrRequest.setOriginalServiceInstance(serviceInstanceOrig);
                        svrRequest.setServiceInstance(serviceInstance);
                        svrRequest.setUser(request.getUser());
                        svrRequest.getClouds().addAll(request.getClouds());
                        svrRequest.getArguments().clear();
                        svrRequest.getArguments().addAll(AzureSQLUtil.createStringArguments(kvps));

                        AzureSQLServiceOperations.logger.trace("Changing the password for server '" + server.getName() + "' to "
                                + AzureSQLUtil.maskPrivateKey(newServerPw));

                        siResp = AzureSQLUtil.updateServer(siResp, svr); // update object for response
                        seqList.add(AzureSQLServiceOperations.processServerChange(svrRequest));
                    }
                    // end packaging Server changes

                    // start packaging FirewallRule changes
                    ArrayList<FirewallRule> adds = new ArrayList<FirewallRule>();
                    ArrayList<FirewallRule> deletes = new ArrayList<FirewallRule>();
                    ArrayList<FirewallRule> changes = new ArrayList<FirewallRule>();

                    if (isDeleteFwRule) {
                        FirewallRule delete = new FirewallRule();
                        FirewallRule add = new FirewallRule();

                        delete.setName(curFwRuleName);
                        delete.setStartIPAddress(curFwStartIp);
                        delete.setEndIPAddress(curFwEndIp);

                        add.setName(newFwRuleName);
                        add.setStartIPAddress(newFwStartIp);
                        add.setEndIPAddress(newFwEndIp);

                        AzureSQLServiceOperations.logger.trace("Adding FirewallRule: " + add.getName() + ":"
                                + add.getStartIPAddress() + ":" + add.getEndIPAddress());
                        AzureSQLServiceOperations.logger.trace("Deleting FirewallRule: " + delete.getName() + ":"
                                + delete.getStartIPAddress() + ":" + delete.getEndIPAddress());

                        siResp = AzureSQLUtil.updateFirewallRule(siResp, add); // update the response object
                        adds.add(add);
                        deletes.add(delete);
                    }
                    else if (isNewStartIp || isNewEndIp) { // check to see if the range changed
                        FirewallRule fwRule = new FirewallRule();

                        fwRule.setName(curFwRuleName);
                        fwRule.setStartIPAddress(isNewStartIp ? newFwStartIp : curFwStartIp);
                        fwRule.setEndIPAddress(isNewEndIp ? newFwEndIp : curFwEndIp);

                        AzureSQLServiceOperations.logger.trace("Changing FirewallRule '" + fwRule.getName() + "' from "
                                + curFwStartIp + ":" + curFwEndIp + " to " + fwRule.getStartIPAddress() + ":"
                                + fwRule.getEndIPAddress());

                        siResp = AzureSQLUtil.updateFirewallRule(siResp, fwRule); // update the response object
                        changes.add(fwRule);
                    }

                    if (!adds.isEmpty() || !deletes.isEmpty() || !changes.isEmpty()) {
                        MethodRequest fwRequest = new MethodRequest();

                        fwRequest.setReqId(request.getReqId());
                        fwRequest.setName(AzureSQLConstants.METHOD_PROCESS_FIREWALL_RULE_CHANGE);
                        fwRequest.setProvider(request.getProvider());
                        fwRequest.setOriginalServiceInstance(serviceInstanceOrig);
                        fwRequest.setServiceInstance(serviceInstance);
                        fwRequest.setUser(request.getUser());
                        fwRequest.getClouds().addAll(request.getClouds());
                        fwRequest.getArguments().clear();
                        fwRequest.getArguments().addAll(
                                AzureSQLUtil.createStringArgument(new KeyValuePair(AzureSQLConstants.PROP_SERVER_NAME_STR, server
                                        .getName())));
                        fwRequest.getArguments().add(
                                AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_FIREWALL_RULE_DELETE,
                                        AzureSQLUtil.serialize(deletes)));
                        fwRequest.getArguments().add(
                                AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_FIREWALL_RULE_ADD,
                                        AzureSQLUtil.serialize(adds)));
                        fwRequest.getArguments().add(
                                AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_FIREWALL_RULE_CHANGE,
                                        AzureSQLUtil.serialize(changes)));

                        seqList.add(AzureSQLServiceOperations.processFirewallRuleChange(fwRequest));
                    }
                    // end packaging FirewallRule changes

                    // start packaging Database changes
                    if (isDbNameChange) {
                        MethodRequest dbRequest = new MethodRequest();
                        Database database = new Database();
                        List<KeyValuePair> kvps = new ArrayList<KeyValuePair>();

                        AzureSQLServiceOperations.logger.trace("Changing database name from '" + curDbName + "' to '" + newDbName
                                + "'");

                        database.setName(newDbName);
                        database.setEdition(curDbEdition);
                        database.setServiceObjectiveId(curDbObjId);
                        database.setMaxSizeBytes(curDbMaxBytes);

                        dbRequest.setReqId(request.getReqId());
                        dbRequest.setName(AzureSQLConstants.METHOD_PROCESS_DATABASE_CHANGE);
                        dbRequest.setProvider(request.getProvider());
                        dbRequest.setOriginalServiceInstance(serviceInstanceOrig);
                        dbRequest.setServiceInstance(serviceInstance);
                        dbRequest.getClouds().addAll(request.getClouds());
                        dbRequest.setUser(request.getUser());

                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_SERVER_NAME_STR, server.getName()));
                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_DATABASE_NAME_STR, curDbName));

                        dbRequest.getArguments().clear();
                        dbRequest.getArguments().addAll(AzureSQLUtil.createStringArguments(kvps));
                        dbRequest.getArguments()
                                .add(AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_DATABASE,
                                        AzureSQLUtil.serialize(database)));

                        siResp = AzureSQLUtil.updateDatabase(siResp, database); // update the response object
                        seqList.add(AzureSQLServiceOperations.processDatabaseChange(dbRequest));
                    }
                    else if (isNewDatabase) { // not originally created so need to create a new database
                        MethodRequest dbRequest = new MethodRequest();
                        Database database = new Database();
                        List<KeyValuePair> kvps = new ArrayList<KeyValuePair>();

                        database.setName(newDbName);
                        database.setEdition(newDbEdition);
                        database.setServiceObjectiveId(newDbObjId);
                        database.setMaxSizeBytes(newDbMaxBytes);

                        AzureSQLServiceOperations.logger.trace("Creating new database with name = " + database.getName()
                                + "  edition = " + database.getEdition() + "  service objective ID = "
                                + database.getServiceObjectiveId() + "  size = " + database.getMaxSizeBytes() + " bytes");

                        dbRequest.setReqId(request.getReqId());
                        dbRequest.setName(AzureSQLConstants.METHOD_PROCESS_DATABASE_CHANGE);
                        dbRequest.setProvider(request.getProvider());
                        dbRequest.setOriginalServiceInstance(serviceInstanceOrig);
                        dbRequest.setServiceInstance(serviceInstance);
                        dbRequest.getClouds().addAll(request.getClouds());
                        dbRequest.setUser(request.getUser());

                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_SERVER_NAME_STR, server.getName()));

                        dbRequest.getArguments().clear();
                        dbRequest.getArguments().addAll(AzureSQLUtil.createStringArguments(kvps));
                        dbRequest.getArguments()
                                .add(AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_DATABASE,
                                        AzureSQLUtil.serialize(database)));

                        siResp = AzureSQLUtil.updateDatabase(siResp, database);
                        seqList.add(AzureSQLServiceOperations.processDatabaseChange(dbRequest));
                    }
                    else if (isNewDbEdition || isNewDbSize || isNewDbObjId) { // may have database property changes
                        MethodRequest dbRequest = new MethodRequest();
                        Database database = new Database();
                        List<KeyValuePair> kvps = new ArrayList<KeyValuePair>();

                        database.setName(curDbName);
                        database.setEdition(AzureSQLUtil.isValued(newDbEdition) && !curDbEdition.equals(newDbEdition) ? newDbEdition
                                : curDbEdition);
                        database.setServiceObjectiveId(AzureSQLUtil.isValued(newDbObjId) && !curDbObjId.equals(newDbObjId) ? newDbObjId
                                : curDbObjId);
                        database.setMaxSizeBytes(newDbMaxBytes > 0 && curDbMaxBytes != newDbMaxBytes ? newDbMaxBytes
                                : curDbMaxBytes);

                        AzureSQLServiceOperations.logger.trace("Updating database with name = " + database.getName()
                                + "  edition = " + database.getEdition() + "  service objective ID = "
                                + database.getServiceObjectiveId() + "  size = " + database.getMaxSizeBytes() + " bytes");

                        dbRequest.setReqId(request.getReqId());
                        dbRequest.setName(AzureSQLConstants.METHOD_PROCESS_DATABASE_CHANGE);
                        dbRequest.setProvider(request.getProvider());
                        dbRequest.setOriginalServiceInstance(serviceInstanceOrig);
                        dbRequest.setServiceInstance(serviceInstance);
                        dbRequest.getClouds().addAll(request.getClouds());
                        dbRequest.setUser(request.getUser());

                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_SERVER_NAME_STR, server.getName()));
                        kvps.add(new KeyValuePair(AzureSQLConstants.PROP_DATABASE_NAME_STR, curDbName));

                        dbRequest.getArguments().clear();
                        dbRequest.getArguments().addAll(AzureSQLUtil.createStringArguments(kvps));
                        dbRequest.getArguments()
                                .add(AzureSQLUtil.createByteArgument(AzureSQLConstants.PROP_DATABASE,
                                        AzureSQLUtil.serialize(database)));

                        siResp = AzureSQLUtil.updateDatabase(siResp, database);
                        seqList.add(AzureSQLServiceOperations.processDatabaseChange(dbRequest));
                    }
                    // end packaging Database changes

                    if (seqList.isEmpty()) {
                        AzureSQLServiceOperations.logger.trace("The sequence list is null - no work to do.");
                        resp.setStatus(Status.COMPLETE);
                    }
                    else {
                        final ServiceInstance si = siResp; // need this as final so it can be sent into the function
                        int seqCnt = seqList.size();

                        AzureSQLServiceOperations.logger.trace("There " + (seqCnt == 1 ? "is " : "are ") + seqCnt
                                + " sequence task" + (seqCnt == 1 ? "" : "s") + " to run.");

                        return Promise.sequence(seqList).map(new Function<List<MethodResponse>, ServiceProviderResponse>() {
                            @Override
                            public ServiceProviderResponse invoke(List<MethodResponse> results)
                            {
                                int failureCnt = 0;
                                int totCnt = results != null ? results.size() : 0;
                                ServiceProviderResponse resp = new ServiceProviderResponse();

                                resp.setReqId(request.getReqId());
                                resp.setTimestamp(System.currentTimeMillis());
                                resp.setStatus(Status.COMPLETE);
                                resp.getModified().add(si);

                                if (results != null && !results.isEmpty()) {
                                    for (MethodResponse result : results) {
                                        resp.setMessage(AzureSQLUtil.isValued(resp.getMessage()) ? resp.getMessage() + "\n"
                                                + result.getMessage() : result.getMessage());
                                        failureCnt += result.getStatus() == Status.FAILURE ? 1 : 0;
                                    }

                                    if (failureCnt == totCnt) { // all failed so issue FAILURE
                                        resp.setStatus(Status.FAILURE);
                                    }
                                    else if (failureCnt > 0) { // some failed so mark COMPLETE
                                        resp.setMessage("Some tasks failed to complete successfully.\n" + resp.getMessage());
                                    }
                                }

                                return resp;
                            }
                        });
                    }
                }
                else {
                    String localMsg = "Azure has no reference to Server '" + curServerName + "'.  The service is misconfigured.";

                    AzureSQLServiceOperations.logger.error(localMsg);
                    resp.setMessage(localMsg);
                    resp.setStatus(Status.FAILURE);
                }

                resp.setTimestamp(System.currentTimeMillis());

                return Promise.pure(resp);
            }
        };
    }

}
