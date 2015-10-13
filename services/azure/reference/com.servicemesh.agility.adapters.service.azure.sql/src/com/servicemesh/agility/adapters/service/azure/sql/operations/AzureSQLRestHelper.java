/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.operations;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.microsoft.schemas.windowsazure.ServiceResource;
import com.microsoft.schemas.windowsazure.ServiceResources;
import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.agility.adapters.service.azure.sql.connection.AzureSQLConnectionFactory;
import com.servicemesh.agility.adapters.service.azure.sql.connection.AzureSQLEndpoint;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLConstants;
import com.servicemesh.agility.adapters.service.azure.sql.util.AzureSQLUtil;
import com.servicemesh.agility.api.Credential;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;
import com.servicemesh.azure.sql.models.AdministratorLoginPassword;
import com.servicemesh.azure.sql.models.Database;
import com.servicemesh.azure.sql.models.DatabaseList;
import com.servicemesh.azure.sql.models.FirewallRule;
import com.servicemesh.azure.sql.models.FirewallRuleList;
import com.servicemesh.azure.sql.models.Server;
import com.servicemesh.azure.sql.models.ServerList;
import com.servicemesh.azure.sql.models.ServerName;
import com.servicemesh.azure.sql.models.Servers;
import com.servicemesh.core.async.CompletablePromise;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.async.PromiseFactory;
import com.servicemesh.io.http.IHttpResponse;
import com.servicemesh.io.proxy.Proxy;

/**
 * This class will provide helper methods that can be used for create/access Azure SQL REST actions. These helpers will use the
 * proper endpoint and connection pair needed by the Azure schema associated with the call.
 */
public class AzureSQLRestHelper
{
    private static Logger logger = Logger.getLogger(AzureSQLRestHelper.class);

    // processing Azure REST calls requires schemas from multiple name spaces. there will be an
    // endpoint and connection pair associated with each name space
    private AzureSQLEndpoint _endpoint2010Context;
    private AzureSQLEndpoint _endpointGenericContext;
    private AzureConnection _conn2010Context;
    private AzureConnection _connGenericContext;

    /**
     * Constructor.
     *
     * @param ServiceProviderRequest
     *            request - a request object that contains information needed to configure the proper endpoint and connection pair
     * @throws AzureAdapterException
     *             - if any exception occurs during initiation
     */
    public AzureSQLRestHelper(ServiceProviderRequest request) throws AzureAdapterException
    {
        try {
            String subscription = AzureSQLConnectionFactory.getSubscription(request.getProvider(), request.getClouds());
            String version = AzureSQLConstants.AZURE_SQL_VERSION;
            String context201012 = AzureSQLConstants.AZURE_201012_CONTEXT;
            String contextGeneric = AzureSQLConstants.GENERIC_AZURE_CONTEXT;
            Class<?> errorClass201012 = AzureSQLUtil.getErrorClass(AzureSQLConstants.AZURE_201012_CONTEXT);
            Class<?> errorClassGeneric = AzureSQLUtil.getErrorClass(AzureSQLConstants.GENERIC_AZURE_CONTEXT);
            List<Property> settings = new ArrayList<Property>();
            Proxy proxy = null;
            Credential credential = AzureSQLConnectionFactory.getCredentials(request.getProvider(), request.getClouds());
            AzureSQLConnectionFactory connFactory = new AzureSQLConnectionFactory();

            _endpoint2010Context = new AzureSQLEndpoint(subscription, version, context201012, errorClass201012);
            _endpointGenericContext = new AzureSQLEndpoint(subscription, version, contextGeneric, errorClassGeneric);
            _conn2010Context = connFactory.getConnection(settings, credential, proxy, _endpoint2010Context);
            _connGenericContext = connFactory.getConnection(settings, credential, proxy, _endpointGenericContext);

            if (AzureSQLRestHelper.logger.isTraceEnabled()) {
                StringBuilder buf = new StringBuilder("\nAzureSQLRestHelper created:\n");

                buf.append("   Subscription: " + subscription + "\n");
                buf.append("   Version: " + version + "\n");
                buf.append("   201012 Context: " + context201012 + "\n");
                buf.append("   Generic Context: " + contextGeneric + "\n");
                buf.append("   201012 Error Class: " + errorClass201012.getName() + "\n");
                buf.append("   Generic Error Class: " + errorClassGeneric.getName() + "\n");

                AzureSQLRestHelper.logger.trace(buf.toString());
            }
        }
        catch (Exception e) {
            String msg = "An exception has occurred while initiating an AzureSQLRestHelper.";

            AzureSQLRestHelper.logger.error(msg, e);
            throw new AzureAdapterException(e);
        }
    }

    /**
     * This method will return the endpoint associated with the "http://schemas.microsoft.com/sqlazure/2010/12/" schema.
     *
     * @return AzureSQLEndpoint - endpoint used to access Azure SQL REST calls
     */
    public AzureSQLEndpoint getEndpoint2010Context()
    {
        return _endpoint2010Context;
    }

    /**
     * This method will return the endpoint associated with the "http://schemas.microsoft.com/windowsazure" schema.
     *
     * @return AzureSQLEndpoint - endpoint used to access Azure SQL REST calls
     */
    public AzureSQLEndpoint getEndpointGenericContext()
    {
        return _endpointGenericContext;
    }

    /**
     * This method will make an Azure SQL REST call to create an SQL server.
     *
     * @param Server
     *            server - workload object that contains the server configuration properties
     * @return Promise<ServerName> - promise for an object identifying the server that was created
     * @return Promise<AzureAdapterException> - promise for if any exception occurs from the call or the response content mapping
     */
    public Promise<ServerName> createServer(Server server)
    {
        String uri = AzureSQLConstants.AZURE_SQL_BASE_URI;
        String serverStr =
                "  Admin Login = " + (server != null ? server.getAdministratorLogin() : null) + "\n" + "  Admin Password = "
                        + (server != null ? AzureSQLUtil.maskPrivateKey(server.getAdministratorLoginPassword()) : null) + "\n"
                        + "  Location = " + (server != null ? server.getLocation() : null) + "\n";

        AzureSQLRestHelper.logger.trace("Creating Azure SQL Server using URI = " + uri + "\n" + serverStr);

        try {
            return _conn2010Context.post(uri, server, IHttpResponse.class).map(
                    AzureSQLUtil.createServerNameMap(_endpoint2010Context));
        }
        catch (Exception e) {
            String msg = "An exception occurred while creating an Azure SQL server with the following properties:\n" + serverStr;

            AzureSQLRestHelper.logger.error(msg, e);
            return Promise.pure(new AzureAdapterException(msg + "\n" + e));
        }
    }

    /**
     * This method will identify all the servers associated with a given Azure subscription.
     *
     * @return Promise<Servers> - promise of a list of Server objects for a given subscription
     * @return Promise<AzureAdapterException> - promise for any exception or mapping errors
     */
    public Promise<Servers> listServers()
    {
        String uri = AzureSQLConstants.AZURE_SQL_BASE_URI;

        AzureSQLRestHelper.logger.trace("Listing servers using URI = " + uri);

        try {
            return _conn2010Context.get(uri, null, IHttpResponse.class).map(AzureSQLUtil.createServersMap(_endpoint2010Context));
        }
        catch (Exception e) {
            String msg = "An exception occurred while generating a list of servers.";

            AzureSQLRestHelper.logger.error(msg, e);
            return Promise.pure(new AzureAdapterException(msg + "\n" + e));
        }
    }

    /**
     * This method will identify all the servers associated with a given Azure subscription. The 'generic' suffix is used which
     * will generate ServiceResources XML content. This will be mapped to a ServerList object.
     *
     * @return Promise<ServerList> - promise of a list of Server objects for a given subscription
     * @return Promise<AzureAdapterException> - promise for any exception or mapping errors
     */
    public Promise<ServerList> getServerList()
    {
        String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + AzureSQLConstants.URI_GENERIC_TAG;

        AzureSQLRestHelper.logger.trace("Get server list using URI = " + uri);

        try {
            return _connGenericContext.get(uri, null, IHttpResponse.class).map(
                    AzureSQLUtil.createServerListMap(_endpointGenericContext));
        }
        catch (Exception e) {
            String msg = "An exception occurred while generating a list of servers.";

            AzureSQLRestHelper.logger.error(msg, e);
            return Promise.pure(new AzureAdapterException(msg + "\n" + e));
        }
    }

    /**
     * This method will get a specific server from a list of servers for a given subscription. If the server is not found, null
     * will be returned.
     *
     * @param String
     *            serverName - name of server for which to search
     * @return Promise<Server> - promise for a Server object that matches the given name
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception or mapping errors
     */
    public Promise<Server> getServer(final String serverName)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + AzureSQLConstants.URI_GENERIC_TAG;

            AzureSQLRestHelper.logger.trace("Find server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.get(uri, null, IHttpResponse.class)
                        .map(AzureSQLUtil.createServerListMap(_endpointGenericContext)).map(new Function<ServerList, Server>() {
                            @Override
                            public Server invoke(ServerList list)
                            {
                                Server retval = null;

                                if (list != null) {
                                    // see if the server is in the list
                                    for (Server server : list.getServers()) {
                                        if (serverName.equals(server.getName())) {
                                            retval = server;
                                            break;
                                        }
                                    }
                                }

                                if (retval == null) {
                                    AzureSQLRestHelper.logger.debug("No server with name " + serverName + " was found.");
                                }

                                return retval;
                            }
                        });
            }
            catch (Exception e) {
                String msg = "An exception occurred while searching for server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            AzureSQLRestHelper.logger.debug("The name of the server does not have a value.  No work to do.");
            CompletablePromise<Server> p = PromiseFactory.create();
            p.complete(null);
            return p;
        }
    }

    /**
     * This method will delete an Azure SQL server given a server name.
     *
     * @param String
     *            serverName - name of server to delete
     * @return Promise<IHttpResponse> - promise for a raw HTTP response
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception. Missing serverName value will also cause
     *         an exception.
     */
    public Promise<IHttpResponse> deleteServer(String serverName)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName;

            AzureSQLRestHelper.logger.trace("Deleting server with name " + serverName + " using URI = " + uri);

            try {
                return _conn2010Context.delete(uri);
            }
            catch (Exception e) {
                String msg = "An exception occurred while deleting server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            String msg = "The name of the server is required.";

            AzureSQLRestHelper.logger.error(msg);
            return Promise.pure(new AzureAdapterException(msg));
        }
    }

    /**
     * This method will change the administrator login password for a given server.
     *
     * @param String
     *            serverName - name of server for which the password is to be changed.
     * @param String
     *            newPassword - new password value
     * @return Promise<IHttpResponse> - promise of a raw HTTP response
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception. Missing server name and/or password will
     *         also throw exception
     */
    public Promise<IHttpResponse> changeServerPassword(String serverName, AdministratorLoginPassword newPassword)
    {
        boolean isServerNameValued = AzureSQLUtil.isValued(serverName);
        boolean isNewPwValued = newPassword != null && AzureSQLUtil.isValued(newPassword.getValue());

        if (isServerNameValued && isNewPwValued) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "?op=ResetPassword";

            AzureSQLRestHelper.logger.trace("Changing password for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.post(uri,
                        _endpointGenericContext.encode(AzureSQLConstants.AZURE_201012_CONTEXT, newPassword), IHttpResponse.class);
            }
            catch (Exception e) {
                String msg = "An exception occurred while changing the password for server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            StringBuilder msg = new StringBuilder("Missing required parameter value(s):");

            if (!isServerNameValued) {
                msg.append("\nServer Name is missing");
            }

            if (!isNewPwValued) {
                msg.append("\nNew password value is missing");
            }

            return Promise.pure(new AzureAdapterException(msg.toString()));
        }
    }

    /**
     * This method will add a new firewall rule to an Azure SQL server.
     *
     * @param String
     *            serverName - name of the server to which the rule will be added
     * @param ServiceResource
     *            firewallRule - request payload that contains the firewall rule properties
     * @return Promise<FirewallRule> - promise for the created FirewallRule object
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception. A missing server name will also cause an
     *         exception
     */
    public Promise<FirewallRule> createFirewallRule(String serverName, final ServiceResource firewallRule)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/firewallrules";

            AzureSQLRestHelper.logger
                    .trace("Creating a firewall rule for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.post(uri,
                        _endpointGenericContext.encode(AzureSQLConstants.GENERIC_AZURE_CONTEXT, firewallRule),
                        IHttpResponse.class).map(AzureSQLUtil.createFirewallRuleMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while creating a firewall rule for server with name " + serverName;

                AzureSQLRestHelper.logger.error(msg);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            String msg = "The server name has no value.  No firewall rule can be created.";

            AzureSQLRestHelper.logger.debug(msg);
            return Promise.pure(new AzureAdapterException(msg));
        }
    }

    /**
     * This method will update a firewall rule for a given server.
     *
     * @param String
     *            serverName - name of the server that contains the rule to be updated
     * @param ServiceResource
     *            firewallRule - request payload that contains the properties of the rule
     * @return Promise<FirewallRule> - promise of the updated firewall rule object
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception. A missing server name will also cause an
     *         exception
     */
    public Promise<FirewallRule> updateFirewallRule(String serverName, ServiceResource firewallRule)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/firewallrules/"
                            + AzureSQLUtil.escapeSpaces(firewallRule.getName());

            AzureSQLRestHelper.logger
                    .trace("Creating a firewall rule for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.put(uri,
                        _endpointGenericContext.encode(AzureSQLConstants.GENERIC_AZURE_CONTEXT, firewallRule),
                        IHttpResponse.class).map(AzureSQLUtil.createFirewallRuleMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while updating a firewall rule for server with name " + serverName;

                AzureSQLRestHelper.logger.error(msg);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            String msg = "The server name has no value.  Firewall rule cannot be updated.";

            AzureSQLRestHelper.logger.debug(msg);
            return Promise.pure(new AzureAdapterException(msg));
        }
    }

    /**
     * This method will generate a list of all firewall rules for a given Azure SQL server.
     *
     * @param String
     *            serverName - name of server for which to get rules
     * @return Promise<FirewallRuleList> - promise for a list of FirewallRule objects. An empty list will be returned if the
     *         server name is missing
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception
     */
    public Promise<FirewallRuleList> listFirewallRules(String serverName)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/firewallrules";

            AzureSQLRestHelper.logger
                    .trace("Creating a firewall rule for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.get(uri, null, IHttpResponse.class).map(
                        AzureSQLUtil.createFirewallRuleListMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while listing firewall rules for server with name " + serverName;

                AzureSQLRestHelper.logger.error(msg);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            AzureSQLRestHelper.logger.debug("Server name not provided.  No work to do.");
            return Promise.pure(new FirewallRuleList());
        }
    }

    /**
     * This method will delete an existing firewall rule from an Azure SQL server.
     *
     * @param String
     *            serverName - name of server from which to delete the rule
     * @param String
     *            firewallRuleName - name of firewall rule that is to be deleted
     * @return Promise<IHttpResponse> - promise of raw HTTP response
     * @return Promise<AzureAdapterException> - promise of any wrapped Azure exception. If the server or rule name is missing an
     *         exception will be sent.
     */
    public Promise<IHttpResponse> deleteFirewallRule(String serverName, String firewallRuleName)
    {
        boolean isServerNameValued = AzureSQLUtil.isValued(serverName);
        boolean isRuleNameValued = AzureSQLUtil.isValued(firewallRuleName);

        if (isServerNameValued && isRuleNameValued) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/firewallrules/"
                            + AzureSQLUtil.escapeSpaces(firewallRuleName);

            AzureSQLRestHelper.logger.trace("Deleting firewall rule firwallRuleName " + firewallRuleName
                    + " from server with name " + serverName + " using URI = " + uri);

            try {
                return _conn2010Context.delete(uri);
            }
            catch (Exception e) {
                String msg =
                        "An exception occurred while deleting firewall rule " + firewallRuleName + " from server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            StringBuilder msg = new StringBuilder("Missing required parameter value(s):");

            if (!isServerNameValued) {
                msg.append("\nServer Name is missing");
            }

            if (!isRuleNameValued) {
                msg.append("\nFirewallRule Name is missing");
            }

            return Promise.pure(new AzureAdapterException(msg.toString()));
        }
    }

    /**
     * This method will add a new database to an Azure SQL server.
     *
     * @param String
     *            serverName - name of the server to which the database is to be added
     * @param ServiceResource
     *            db - request payload that contains the database properties
     * @return Promise<Database> - promise of the new Database object
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception. A missing server name will cause an
     *         exception promise.
     */
    public Promise<Database> createDatabase(String serverName, ServiceResource db)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/databases";

            AzureSQLRestHelper.logger.trace("Creating database for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.post(uri, _endpointGenericContext.encode(AzureSQLConstants.GENERIC_AZURE_CONTEXT, db),
                        IHttpResponse.class).map(AzureSQLUtil.createDatabaseMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while creating a database on server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            String msg = "No server name provided.  Database cannot be created.";

            AzureSQLRestHelper.logger.error(msg);
            return Promise.pure(new AzureAdapterException(msg));
        }
    }

    /**
     * This method will execute a database update REST call. The server name and the current database name must be provided as
     * well as the update object which contains the desired changes. If the name of the database is to be changed, that must be
     * the only property in the change object. If other fields are to be changed, the current database name must be specified,
     * i.e. the 'Name' element is required.
     *
     * @param String
     *            serverName - name of SQL server host
     * @param String
     *            curDbName - current name of the database to be updated. If the name is to be changed, the new name will be set
     *            in the update object.
     * @param ServiceResource
     *            db - update object which contains the changes to be made to the database
     * @return Promise<Database> - future reference to a Database object
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception. Missing server name will also cause
     *         exception.
     */
    public Promise<Database> updateDatabase(String serverName, String curDbName, ServiceResource db)
    {
        boolean isServerNameValued = AzureSQLUtil.isValued(serverName);
        boolean isCurDbNameValued = AzureSQLUtil.isValued(curDbName);

        if (isServerNameValued && isCurDbNameValued) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/databases/"
                            + AzureSQLUtil.escapeSpaces(curDbName);

            AzureSQLRestHelper.logger.trace("Updating database for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.put(uri, _endpointGenericContext.encode(AzureSQLConstants.GENERIC_AZURE_CONTEXT, db),
                        IHttpResponse.class).map(AzureSQLUtil.createDatabaseMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while updating a database on server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            StringBuilder msg = new StringBuilder("Missing required parameter value(s):");

            if (!isServerNameValued) {
                msg.append("\nServer Name is missing");
            }

            if (!isCurDbNameValued) {
                msg.append("\nCurrent Database Name is missing");
            }

            AzureSQLRestHelper.logger.error(msg.toString());
            return Promise.pure(new AzureAdapterException(msg.toString()));
        }
    }

    /**
     * This method will generate a list of all databases that have been created for the given server.
     *
     * @param String
     *            serverName - name of the server for which to get database list
     * @return Promise<DatabaseList> - promise for a list of Database objects found on the server. If the server name is null, an
     *         empty list will be returned
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception
     */
    public Promise<DatabaseList> listDatabases(String serverName)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/databases" + AzureSQLConstants.URI_GENERIC_TAG;

            AzureSQLRestHelper.logger.trace("Listing databases for server with name " + serverName + " using URI = " + uri);

            try {
                return _connGenericContext.get(uri, null, IHttpResponse.class).map(
                        AzureSQLUtil.createDatabaseListMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while generating a list of databases for server with name " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            AzureSQLRestHelper.logger.debug("Server name not provided.  No work to do.");
            return Promise.pure(new DatabaseList());
        }
    }

    /**
     * This method will delete an existing database from an Azure SQL server.
     *
     * @param String
     *            serverName - name of server from which to delete the rule
     * @param String
     *            dbName - name of database that is to be deleted
     * @return Promise<IHttpResponse> - promise of raw HTTP response
     * @return Promise<AzureAdapterException> - promise of any wrapped Azure exception. If the server or database name is missing
     *         an exception will be sent.
     */
    public Promise<IHttpResponse> deleteDatabase(String serverName, String dbName)
    {
        boolean isServerNameValued = AzureSQLUtil.isValued(serverName);
        boolean isDbNameValued = AzureSQLUtil.isValued(dbName);

        if (isServerNameValued && isDbNameValued) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/databases/" + AzureSQLUtil.escapeSpaces(dbName);

            AzureSQLRestHelper.logger.trace("Deleting database " + dbName + " from server with name " + serverName
                    + " using URI = " + uri);

            try {
                return _conn2010Context.delete(uri);
            }
            catch (Exception e) {
                String msg = "An exception occurred while deleting database " + dbName + " from server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            StringBuilder msg = new StringBuilder("Missing required parameter value(s):");

            if (!isServerNameValued) {
                msg.append("\nServer Name is missing");
            }

            if (!isDbNameValued) {
                msg.append("\nDatabase Name is missing");
            }

            return Promise.pure(new AzureAdapterException(msg.toString()));
        }
    }

    /**
     * This method will find a specified database on a given Azure SQL server.
     *
     * @param String
     *            serverName - name of server on which to search for the database
     * @param String
     *            dbName - name of the database for which to search
     * @return Promise<Database> - promise for a Database object. A null value will be returned if not found or either of the
     *         parameters are null.
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception
     */
    public Promise<Database> getDatabase(String serverName, String dbName)
    {
        boolean isServerNameValued = AzureSQLUtil.isValued(serverName);
        boolean isDbNameValued = AzureSQLUtil.isValued(dbName);

        if (isServerNameValued && isDbNameValued) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/databases/" + AzureSQLUtil.escapeSpaces(dbName);

            AzureSQLRestHelper.logger.trace("Find database " + dbName + " on server with name " + serverName + " using URI = "
                    + uri);

            try {
                return _connGenericContext.get(uri, null, IHttpResponse.class).map(
                        AzureSQLUtil.createDatabaseMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while finding database " + dbName + " on server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            StringBuilder msg = new StringBuilder("Missing required parameter value(s):");

            if (!isServerNameValued) {
                msg.append("\nServer Name is missing");
            }

            if (!isDbNameValued) {
                msg.append("\nDatabase Name is missing");
            }

            AzureSQLRestHelper.logger.debug(msg.toString());
            CompletablePromise<Database> p = PromiseFactory.create();
            p.complete(null);
            return p;
        }
    }

    /**
     * This method will find a specified firewall rule on a given Azure SQL server.
     *
     * @param String
     *            serverName - name of server on which to search for the rule
     * @param String
     *            ruleName - name of the firewall rule for which to search
     * @return Promise<FirewallRule> - promise for a FirewallRule object. A null value will be returned if not found or either of
     *         the parameters are null.
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception
     */
    public Promise<FirewallRule> getFirewallRule(String serverName, String ruleName)
    {
        boolean isServerNameValued = AzureSQLUtil.isValued(serverName);
        boolean isRuleNameValued = AzureSQLUtil.isValued(ruleName);

        if (isServerNameValued && isRuleNameValued) {
            String uri =
                    AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/firewallrules/"
                            + AzureSQLUtil.escapeSpaces(ruleName);

            AzureSQLRestHelper.logger.trace("Find firewall rule " + ruleName + " on server with name " + serverName
                    + " using URI = " + uri);

            try {
                return _connGenericContext.get(uri, null, IHttpResponse.class).map(
                        AzureSQLUtil.createFirewallRuleMap(_endpointGenericContext));
            }
            catch (Exception e) {
                String msg = "An exception occurred while finding firewall rule " + ruleName + " on server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            StringBuilder msg = new StringBuilder("Missing required parameter value(s):");

            if (!isServerNameValued) {
                msg.append("\nServer Name is missing");
            }

            if (!isRuleNameValued) {
                msg.append("\nFirewallRule Name is missing");
            }

            AzureSQLRestHelper.logger.debug(msg.toString());
            CompletablePromise<FirewallRule> p = PromiseFactory.create();
            p.complete(null);
            return p;
        }
    }

    /**
     * This method will return the list of Service Level Objectives available on the given server.
     *
     * @param String
     *            serverName - name of the Azure SQL server for which to get the service level objectives
     * @return Promise<ServiceResources> - promise of list of service level objectives as a ServiceResources object. if the server
     *         name is null, an empty list will be returned.
     * @return Promise<AzureAdapterException> - promise for any wrapped Azure exception
     */
    public Promise<ServiceResources> getServiceLevelObjectives(String serverName)
    {
        if (AzureSQLUtil.isValued(serverName)) {
            String uri = AzureSQLConstants.AZURE_SQL_BASE_URI + "/" + serverName + "/serviceobjectives";

            AzureSQLRestHelper.logger.trace("Get service level objectives for server with name " + serverName + " using URI = "
                    + uri);

            try {
                return _connGenericContext.get(uri, null, IHttpResponse.class).map(
                        new Function<IHttpResponse, ServiceResources>() {
                            @Override
                            public ServiceResources invoke(IHttpResponse httpResponse)
                            {
                                AzureSQLUtil.checkForSQLServerError(httpResponse, _endpointGenericContext);
                                return _endpointGenericContext.decode(httpResponse, AzureSQLConstants.GENERIC_AZURE_CONTEXT,
                                        ServiceResources.class);
                            }
                        });
            }
            catch (Exception e) {
                String msg = "An exception occurred while retrieving ServiceLevelObjects for server " + serverName;

                AzureSQLRestHelper.logger.error(msg, e);
                return Promise.pure(new AzureAdapterException(msg + "\n" + e));
            }
        }
        else {
            String msg = "Server name has no value.  No work to do.";

            AzureSQLRestHelper.logger.debug(msg);
            return Promise.pure(new ServiceResources());
        }
    }

    public static AzureConnection getConnectionGeneric(ServiceProviderRequest request)
    {
        if (request != null) {
            String subscription = null;
            Credential credential = null;
            try {
                subscription = AzureSQLConnectionFactory.getSubscription(request.getProvider(), request.getClouds());
                credential = AzureSQLConnectionFactory.getCredentials(request.getProvider(), request.getClouds());
                AzureSQLConnectionFactory factory = new AzureSQLConnectionFactory();
                AzureSQLEndpoint _endpointGenericContext =
                        new AzureSQLEndpoint(subscription, AzureSQLConstants.AZURE_SQL_VERSION,
                                AzureSQLConstants.GENERIC_AZURE_CONTEXT,
                                AzureSQLUtil.getErrorClass(AzureSQLConstants.GENERIC_AZURE_CONTEXT));
                return factory.getConnection(new ArrayList<Property>(), credential, null, _endpointGenericContext);
            }
            catch (Exception e) {
                AzureSQLRestHelper.logger.error(e);
                return null;
            }
        }
        else {
            return null;
        }
    }

}
