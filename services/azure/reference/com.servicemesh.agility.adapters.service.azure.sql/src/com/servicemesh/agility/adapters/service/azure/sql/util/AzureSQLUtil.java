/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.microsoft.schemas.windowsazure.ServiceResource;
import com.microsoft.schemas.windowsazure.ServiceResources;
import com.servicemesh.agility.adapters.core.azure.AzureEndpoint;
import com.servicemesh.agility.adapters.core.azure.Config;
import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.agility.adapters.core.azure.util.AzureUtil;
import com.servicemesh.agility.adapters.core.azure.util.KeyValuePair;
import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLConfig;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.sdk.service.helper.IPHelper;
import com.servicemesh.agility.sdk.service.msgs.MethodRequest;
import com.servicemesh.agility.sdk.service.msgs.MethodResponse;
import com.servicemesh.agility.sdk.service.msgs.MethodVariable;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.azure.sql.models.Database;
import com.servicemesh.azure.sql.models.DatabaseList;
import com.servicemesh.azure.sql.models.FirewallRule;
import com.servicemesh.azure.sql.models.FirewallRuleList;
import com.servicemesh.azure.sql.models.Server;
import com.servicemesh.azure.sql.models.ServerList;
import com.servicemesh.azure.sql.models.ServerName;
import com.servicemesh.azure.sql.models.Servers;
import com.servicemesh.core.async.Function;
import com.servicemesh.core.messaging.Response;
import com.servicemesh.core.messaging.Status;
import com.servicemesh.io.http.IHttpResponse;

public class AzureSQLUtil extends AzureUtil
{
    public static Logger logger = Logger.getLogger(AzureUtil.class);
    private static final long serialVersionUID = 20150121;

    private static final String NO_ERROR_MESSAGE = "No error message available.";

    public static Class<?> getErrorClass(String context)
    {
        Class<?> retval = null;

        if (isValued(context)) {
            if (context.equals(AzureSQLConstants.GENERIC_AZURE_CONTEXT)) {
                retval = com.microsoft.schemas.windowsazure.Error.class;
            }
            else if (context.equals(AzureSQLConstants.AZURE_201012_CONTEXT)) {
                retval = com.servicemesh.azure.sql.models.Error.class;
            }
            else if (context.equals(AzureSQLConstants.SQLSERVER_CONTEXT)) {
                retval = services.web.framework.management.sqlserver.microsoft.Error.class;
            }
        }

        return retval;
    }

    /**
     * This was taken from the traffic manager reference code. It formats the XML into an easy-to-read format.
     * 
     * @param String
     *            xml - the XML code to be formatted
     * @return String - the XML code in an easy-to-read format
     */
    public static String prettyFormat(String xml)
    {
        String prettyVal = xml;

        if (isValued(xml)) {
            try {
                Source xmlInput = new StreamSource(new StringReader(xml));
                StreamResult xmlOutput = new StreamResult(new StringWriter());
                Transformer transformer = TransformerFactory.newInstance().newTransformer();

                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(xmlInput, xmlOutput);

                prettyVal = xmlOutput.getWriter().toString();
            }
            catch (Exception ex) {
                AzureSQLUtil.logger.error("An exception occurred while formatting XML string [" + xml + "]", ex);
            }
        }

        return prettyVal;
    }

    /**
     * This method will map a ServiceResources type to a DatabaseList type.
     *
     * @param ServiceResources
     *            serviceResources - Azure ServiceResource type as returned from REST call
     * @return DatabaseList - converted ServiceResources
     */
    public static DatabaseList mapResourcesToDatabaseList(ServiceResources serviceResources)
    {
        DatabaseList databaseList = new DatabaseList();

        if (serviceResources != null) {
            String expectedType = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_DATABASE;

            for (ServiceResource serviceResource : serviceResources.getServiceResources()) {
                if (expectedType.equals(serviceResource.getType())) {
                    databaseList.getDatabases().add(AzureSQLUtil.mapResourceToDatabase(serviceResource));
                }
                else {
                    AzureSQLUtil.logger.warn("Expected " + expectedType + " but found " + serviceResource.getType());
                }
            }
        }
        else {
            AzureSQLUtil.logger.trace("The ServiceResources object is null.  The DatabaseList object will be empty.");
        }

        return databaseList;
    }

    /**
     * This method will map a ServiceResources type to a FirewallRuleList type.
     *
     * @param ServiceResources
     *            serviceResources - Azure ServiceResource type as returned from REST call
     * @return FirewallRuleList - converted ServiceResources
     */
    public static FirewallRuleList mapResourcesToFirewallRuleList(ServiceResources serviceResources)
    {
        FirewallRuleList firewallRuleList = new FirewallRuleList();

        if (serviceResources != null) {
            String expectedType = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_FIREWALL_RULE;

            for (ServiceResource serviceResource : serviceResources.getServiceResources()) {
                if (expectedType.equals(serviceResource.getType())) {
                    firewallRuleList.getFirewallRules().add(AzureSQLUtil.mapResourceToFirewallRule(serviceResource));
                }
                else {
                    AzureSQLUtil.logger.warn("Expected " + expectedType + " but found " + serviceResource.getType());
                }
            }
        }
        else {
            AzureSQLUtil.logger.trace("The ServiceResources object is null.  The FirewallRuleList object will be empty.");
        }

        return firewallRuleList;
    }

    /**
     * This method will map a ServiceResources type to a ServerList type.
     *
     * @param ServiceResources
     *            serviceResources - Azure ServiceResource type as returned from REST call
     * @return ServerList - converted ServiceResources
     */
    public static ServerList mapResourcesToServerList(ServiceResources serviceResources)
    {
        ServerList serverList = new ServerList();

        if (serviceResources != null) {
            String expectedType = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_SERVER;

            for (ServiceResource serviceResource : serviceResources.getServiceResources()) {
                if (expectedType.equals(serviceResource.getType())) {
                    serverList.getServers().add(AzureSQLUtil.mapResourceToServer(serviceResource));
                }
                else {
                    AzureSQLUtil.logger.warn("Expected " + expectedType + " but found " + serviceResource.getType());
                }
            }
        }
        else {
            AzureSQLUtil.logger.trace("The ServiceResources object is null.  The ServerList object will be empty.");
        }

        return serverList;
    }

    /**
     * This method will map a ServiceResource type to a Database type.
     *
     * @param ServiceResource
     *            serviceResource - Azure ServiceResource type as returned from REST call
     * @return Database - converted ServiceResource
     */
    public static Database mapResourceToDatabase(ServiceResource serviceResource)
    {
        Database database = new Database();

        if (serviceResource != null) {
            database.setName(serviceResource.getName());
            database.setEdition(serviceResource.getEdition());
            database.setCollationName(serviceResource.getCollationName());
            database.setMaxSizeBytes(serviceResource.getMaxSizeBytes());
            database.setServiceObjectiveId(serviceResource.getServiceObjectiveId());
            database.setType(serviceResource.getType());
            database.setState(serviceResource.getState());
            database.setSelfLink(serviceResource.getSelfLink());
            database.setParentLink(serviceResource.getParentLink());
            database.setServiceObjectiveAssignmentErrorCode(serviceResource.getServiceObjectiveAssignmentErrorCode());
            database.setServiceObjectiveAssignmentErrorDescription(serviceResource
                    .getServiceObjectiveAssignmentErrorDescription());
            database.setServiceObjectiveAssignmentState(serviceResource.getServiceObjectiveAssignmentState());
            database.setServiceObjectiveAssignmentStateDescription(serviceResource
                    .getServiceObjectiveAssignmentStateDescription());
            database.setServiceObjectiveAssignmentSuccessDate(serviceResource.getServiceObjectiveAssignmentSuccessDate());
            database.setAssignedServiceObjectiveId(serviceResource.getAssignedServiceObjectiveId());
        }
        else {
            AzureSQLUtil.logger.trace("The ServiceResource object is null.  The Database object will be empty.");
        }

        return database;
    }

    /**
     * This method will map a ServiceResource type to a Server type.
     *
     * @param ServiceResource
     *            serviceResource - Azure ServiceResource type as returned from REST call
     * @return Server - converted ServiceResource
     */
    public static Server mapResourceToServer(ServiceResource serviceResource)
    {
        Server server = new Server();

        if (serviceResource != null) {
            server.setName(serviceResource.getName());
            server.setType(serviceResource.getType());
            server.setState(serviceResource.getState());
            server.setSelfLink(serviceResource.getSelfLink());
            server.setParentLink(serviceResource.getParentLink());
            server.setAdministratorLogin(serviceResource.getAdministratorLogin());
            server.setAdministratorLoginPassword(serviceResource.getAdministratorLoginPassword());
            server.setFullyQualifiedDomainName(serviceResource.getFullyQualifiedDomainName());
            server.setGeoPairedRegion(serviceResource.getGeoPairedRegion());
            server.setLocation(serviceResource.getLocation());
            server.setVersion(serviceResource.getVersion());
        }
        else {
            AzureSQLUtil.logger.trace("The ServiceResource object is null.  The Server object will be empty.");
        }

        return server;
    }

    /**
     * This method will map a ServiceResource type to a FirewallRule type.
     *
     * @param ServiceResource
     *            serviceResource - Azure ServiceResource type as returned from REST call
     * @return FirewallRule - converted ServiceResource
     */
    public static FirewallRule mapResourceToFirewallRule(ServiceResource serviceResource)
    {
        FirewallRule firewallRule = new FirewallRule();

        if (serviceResource != null) {
            firewallRule.setName(serviceResource.getName());
            firewallRule.setStartIPAddress(serviceResource.getStartIPAddress());
            firewallRule.setEndIPAddress(serviceResource.getEndIPAddress());
            firewallRule.setType(serviceResource.getType());
            firewallRule.setState(serviceResource.getState());
            firewallRule.setSelfLink(serviceResource.getSelfLink());
            firewallRule.setParentLink(serviceResource.getParentLink());
        }
        else {
            AzureSQLUtil.logger.trace("The ServiceResource object is null.  The FirewallRule object will be empty.");
        }

        return firewallRule;
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * ServiceResource type to a more specific Database type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, Database> - returns a mapping function from ServiceResource to Database
     */
    public static Function<IHttpResponse, Database> createDatabaseMap(final AzureEndpoint endpoint)
    {
        return new Function<IHttpResponse, Database>() {
            @Override
            public Database invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    return AzureSQLUtil.mapResourceToDatabase(endpoint.decode(response, AzureSQLConstants.GENERIC_AZURE_CONTEXT,
                            ServiceResource.class));
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a Database mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * ServiceResources type to a more specific DatabaseList type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, DatabaseList> - returns a mapping function from ServiceResources to DatabaseList
     */
    public static Function<IHttpResponse, DatabaseList> createDatabaseListMap(final AzureEndpoint endpoint)
            throws AzureAdapterException
    {
        return new Function<IHttpResponse, DatabaseList>() {
            @Override
            public DatabaseList invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    ServiceResources resources =
                            endpoint.decode(response, AzureSQLConstants.GENERIC_AZURE_CONTEXT, ServiceResources.class);
                    DatabaseList dbList = new DatabaseList();

                    for (ServiceResource serviceResource : resources.getServiceResources()) {
                        dbList.getDatabases().add(AzureSQLUtil.mapResourceToDatabase(serviceResource));
                    }

                    return dbList;
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a DatabaseList mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * ServiceResources type to a more specific ServerList type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, ServerList> - returns a mapping function from ServiceResources to ServerList
     */
    public static Function<IHttpResponse, ServerList> createServerListMap(final AzureEndpoint endpoint)
            throws AzureAdapterException
    {
        return new Function<IHttpResponse, ServerList>() {
            @Override
            public ServerList invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    ServiceResources resources =
                            endpoint.decode(response, AzureSQLConstants.GENERIC_AZURE_CONTEXT, ServiceResources.class);
                    ServerList svrList = new ServerList();

                    for (ServiceResource serviceResource : resources.getServiceResources()) {
                        svrList.getServers().add(AzureSQLUtil.mapResourceToServer(serviceResource));
                    }

                    return svrList;
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a ServerList mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * IHttpResponse type to a more specific Servers type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, Servers> - returns a mapping function from IHttpResponse to Servers
     */
    public static Function<IHttpResponse, Servers> createServersMap(final AzureEndpoint endpoint) throws AzureAdapterException
    {
        return new Function<IHttpResponse, Servers>() {
            @Override
            public Servers invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    // Check if the Error is a Generic Context error. 
                    AzureSQLUtil.checkForGenericError(response, endpoint);
                    Servers servers = endpoint.decode(response, AzureSQLConstants.AZURE_201012_CONTEXT, Servers.class);
                    return servers;
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a Servers mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * IHttpResponse type to a more specific ServerName type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, ServerName> - returns a mapping function from IHttpResponse to ServerName
     */
    public static Function<IHttpResponse, ServerName> createServerNameMap(final AzureEndpoint endpoint)
            throws AzureAdapterException
    {
        return new Function<IHttpResponse, ServerName>() {
            @Override
            public ServerName invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    // Check if the Error is a Generic Context error.
                    AzureSQLUtil.checkForGenericError(response, endpoint);
                    ServerName serverName = endpoint.decode(response, AzureSQLConstants.AZURE_201012_CONTEXT, ServerName.class);
                    return serverName;
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a ServerName mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * ServiceResource type to a more specific FirewallRule type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, FirewallRule> - returns a mapping function from ServiceResource to FirewallRule
     */
    public static Function<IHttpResponse, FirewallRule> createFirewallRuleMap(final AzureEndpoint endpoint)
            throws AzureAdapterException
    {
        return new Function<IHttpResponse, FirewallRule>() {
            @Override
            public FirewallRule invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    return AzureSQLUtil.mapResourceToFirewallRule(endpoint.decode(response,
                            AzureSQLConstants.GENERIC_AZURE_CONTEXT, ServiceResource.class));
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a FirewallRule mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will create a mapping function that can be used by the Promise.map(...) method. It will map a more general
     * ServiceResources type to a more specific FirewallRuleList type.
     *
     * @param AzureEndpoint
     *            endpoint - the REST endpoint used by connection
     * @return Function<IHttpResponse, FirewallRuleList> - returns a mapping function from ServiceResources to FirewallRuleList
     */
    public static Function<IHttpResponse, FirewallRuleList> createFirewallRuleListMap(final AzureEndpoint endpoint)
            throws AzureAdapterException
    {
        return new Function<IHttpResponse, FirewallRuleList>() {
            @Override
            public FirewallRuleList invoke(IHttpResponse response) throws RuntimeException
            {
                try {
                    AzureSQLUtil.checkForSQLServerError(response, endpoint);
                    ServiceResources resources =
                            endpoint.decode(response, AzureSQLConstants.GENERIC_AZURE_CONTEXT, ServiceResources.class);
                    FirewallRuleList fwrList = new FirewallRuleList();

                    for (ServiceResource serviceResource : resources.getServiceResources()) {
                        fwrList.getFirewallRules().add(AzureSQLUtil.mapResourceToFirewallRule(serviceResource));
                    }

                    return fwrList;
                }
                catch (AzureAdapterException ex) {
                    AzureSQLUtil.logger.error("An exception occurred while creating a FirewallRuleList mapping function.", ex);
                    throw ex;
                }
            }
        };
    }

    /**
     * This method will extract a specific MethodVariable object from the list. The name will identify which variable should be
     * extracted.
     *
     * @param String
     *            variableName - name of variable to extract
     * @param List
     *            <MethodVariable> mvs - method variables to be searched
     * @return MethodVariable - the first matching variable; null will be returned if no match found
     */
    public static MethodVariable getMethodVariable(String variableName, List<MethodVariable> mvs)
    {
        MethodVariable retval = null;

        if (mvs != null && !mvs.isEmpty() && isValued(variableName)) {
            for (MethodVariable mv : mvs) {
                if (variableName.equals(mv.getName())) {
                    retval = mv;
                    break;
                }
            }
        }
        else {
            AzureSQLUtil.logger.trace("One or more parameters did not have values - is list null: "
                    + (mvs == null || mvs.isEmpty()) + "  is variableName null: " + !isValued(variableName));
        }

        return retval;
    }

    /**
     * This method will extract a specific AssetProperty from a list of asset properties. The name will identify which
     * AssetProperty should be extracted.
     *
     * @param String
     *            variableName - name of variable to extract
     * @param List
     *            <AssetProperty> props - list of asset properties to search
     * @return AssetProperty - the first matching asset property; null will be returned if no match found
     */
    public static AssetProperty getAssetProperty(String variableName, List<AssetProperty> props)
    {
        return Config.getAssetProperty(variableName, props);
    }

    /**
     * This method will extract a specific AssetProperty object from a list of MethodVariable properties. The name will identify
     * which AssetProperty should be extracted.
     *
     * @param String
     *            variableName - name of variable to extract
     * @param MethodVariable
     *            mv - method variable containing the list of asset properties to search
     * @return AssetProperty - the first matching asset property; null will be returned if no match found
     */
    public static AssetProperty getAssetProperty(String variableName, MethodVariable mv)
    {
        return AzureSQLUtil.getAssetProperty(variableName, mv != null ? mv.getProperties() : null);
    }

    /**
     * This method will extract the string value from an asset property object.
     *
     * @param String
     *            variableName - name of variable to be extracted
     * @param MethodVariable
     *            mv - variable containing the string value
     * @return String - the string value from the method variable; null will be returned if the name is not found
     */
    public static String getStringVariableValue(String variableName, MethodVariable mv)
    {
        AssetProperty ap = AzureSQLUtil.getAssetProperty(variableName, mv != null ? mv.getProperties() : null);

        return ap != null ? ap.getStringValue() : null;
    }

    /**
     * This method will extract the value representing a named variable from a list of method variables. If the variable does not
     * exist with the provide key, null will be returned.
     *
     * @param String
     *            name - name of variable for which to search
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value of the variable or null if not found
     */
    public static String getStringVariableValue(String name, List<MethodVariable> mvs)
    {
        AssetProperty ap = AzureSQLUtil.getAssetProperty(name, AzureSQLUtil.getMethodVariable(name, mvs));

        return ap != null ? ap.getStringValue() : null;
    }

    /**
     * This method will extract the value representing a named variable from a list of method variables. If the variable does not
     * exist with the provide key, null will be returned.
     *
     * @param String
     *            name - name of variable for which to search
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return byte[] - value of the variable or null if not found
     */
    public static byte[] getByteVariableValue(String name, List<MethodVariable> mvs)
    {
        AssetProperty ap = AzureSQLUtil.getAssetProperty(name, AzureSQLUtil.getMethodVariable(name, mvs));

        return ap != null ? ap.getByteValue() : null;
    }

    /**
     * This method will extract the value representing a server name from a list of method variables. If variable does not exist
     * with the provided key (for server name), null will be returned. Key Name = @see AzureSQLConstants.PROP_SERVER_NAME_STR
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value associated with the named property; null will be returned if not found
     */
    public static String getServerName(List<MethodVariable> mvs)
    {
        return AzureSQLUtil.getStringVariableValue(AzureSQLConstants.PROP_SERVER_NAME_STR, mvs);
    }

    /**
     * This method will extract the value representing a database name from a list of method variables. If variable does not exist
     * with the provided key (for database name), null will be returned. Key Name = @see
     * AzureSQLConstants.PROP_NEW_DATABASE_NAME_STR
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value associated with the named property; null will be returned if not found
     */
    public static String getNewDatabaseName(List<MethodVariable> mvs)
    {
        return AzureSQLUtil.getStringVariableValue(AzureSQLConstants.PROP_NEW_DATABASE_NAME_STR, mvs);
    }

    /**
     * This method will extract the value representing a database name from a list of method variables. If variable does not exist
     * with the provided key (for database name), null will be returned. Key Name = @see AzureSQLConstants.PROP_DATABASE_NAME_STR
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value associated with the named property; null will be returned if not found
     */
    public static String getDatabaseName(List<MethodVariable> mvs)
    {
        return AzureSQLUtil.getStringVariableValue(AzureSQLConstants.PROP_DATABASE_NAME_STR, mvs);
    }

    /**
     * This method will extract the value representing a server 'new' password from a list of method variables. If variable does
     * not exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_NEW_PASSWORD_STR
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value associated with the named property; null will be returned if not found
     */
    public static String getNewPassword(List<MethodVariable> mvs)
    {
        return AzureSQLUtil.getStringVariableValue(AzureSQLConstants.PROP_NEW_PASSWORD_STR, mvs);
    }

    /**
     * This method will extract the value representing a server password from a list of method variables. If variable does not
     * exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_PASSWORD_STR
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value associated with the named property; null will be returned if not found
     */
    public static String getPassword(List<MethodVariable> mvs)
    {
        return AzureSQLUtil.getStringVariableValue(AzureSQLConstants.PROP_PASSWORD_STR, mvs);
    }

    /**
     * This method will extract the value representing a firewall rule name from a list of method variables. If variable does not
     * exist with the provided key (for firewall rule name), null will be returned. Key Name = @see
     * AzureSQLConstants.PROP_FIREWALL_RULE_NAME_STR
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return String - value associated with the named property; null will be returned if not found
     */
    public static String getFirewallRuleName(List<MethodVariable> mvs)
    {
        return AzureSQLUtil.getStringVariableValue(AzureSQLConstants.PROP_FIREWALL_RULE_NAME_STR, mvs);
    }

    /**
     * This method will extract the value representing a database object from a list of method variables. If variable does not
     * exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_DATABASE
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return Database - value associated with the named property as a Database object; null will be returned if not found
     */
    public static Database getDatabase(List<MethodVariable> mvs)
    {
        Database retval = null;
        String propKey = AzureSQLConstants.PROP_DATABASE;

        byte[] data = AzureSQLUtil.getByteVariableValue(propKey, mvs);

        if (data != null && data.length > 0) {
            retval = AzureSQLUtil.deserialize(data);
        }
        else {
            AzureSQLUtil.logger.trace("No data found for property " + propKey);
        }

        return retval;
    }

    /**
     * This method will extract the value representing a databaseList object from a list of method variables. If variable does not
     * exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_DATABASE_LIST
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return DatabaseList - value associated with the named property as a DatabaseList object; null will be returned if not found
     */
    public static DatabaseList getDatabaseList(List<MethodVariable> mvs)
    {
        DatabaseList retval = null;
        String propKey = AzureSQLConstants.PROP_DATABASE_LIST;

        byte[] data = AzureSQLUtil.getByteVariableValue(propKey, mvs);

        if (data != null && data.length > 0) {
            retval = AzureSQLUtil.deserialize(data);
        }
        else {
            AzureSQLUtil.logger.trace("No data found for property " + propKey);
        }

        return retval;
    }

    /**
     * This method will extract the value representing a firewallRuleList object from a list of method variables. If variable does
     * not exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_FIREWALL_RULE_LIST
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return FirewallRuleList - value associated with the named property as a FirewallRuleList object; null will be returned if
     *         not found
     */
    public static FirewallRuleList getFirewallRuleList(List<MethodVariable> mvs)
    {
        FirewallRuleList retval = null;
        String propKey = AzureSQLConstants.PROP_FIREWALL_RULE_LIST;

        byte[] data = AzureSQLUtil.getByteVariableValue(propKey, mvs);

        if (data != null && data.length > 0) {
            retval = AzureSQLUtil.deserialize(data);
        }
        else {
            AzureSQLUtil.logger.trace("No data found for property " + propKey);
        }

        return retval;
    }

    /**
     * This method will extract the value representing a ServerName object from a list of method variables. If variable does not
     * exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_SERVER_NAME
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return ServerName - value associated with the named property as a ServerName object; null will be returned if not found
     */
    public static ServerName getServerNameObject(List<MethodVariable> mvs)
    {
        ServerName retval = null;
        String propKey = AzureSQLConstants.PROP_SERVER_NAME;

        byte[] data = AzureSQLUtil.getByteVariableValue(propKey, mvs);

        if (data != null && data.length > 0) {
            retval = AzureSQLUtil.deserialize(data);
        }
        else {
            AzureSQLUtil.logger.trace("No data found for property " + propKey);
        }

        return retval;
    }

    /**
     * This method will extract the value representing a ServiceInstance object from a list of method variables. If variable does
     * not exist with the provided key, null will be returned. Key Name = @see AzureSQLConstants.PROP_SERVICE_INSTANCE
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return ServiceInsance - value associated with the named property as a ServiceInstance object; null will be returned if not
     *         found
     */
    public static ServiceInstance getServiceInstance(List<MethodVariable> mvs)
    {
        ServiceInstance retval = null;
        String propKey = AzureSQLConstants.PROP_SERVICE_INSTANCE;

        byte[] data = AzureSQLUtil.getByteVariableValue(propKey, mvs);

        if (data != null && data.length > 0) {
            retval = AzureSQLUtil.deserialize(data);
        }
        else {
            AzureSQLUtil.logger.trace("No data found for property " + propKey);
        }

        return retval;
    }

    /**
     * This method will extract the value representing a FirewallRule list from a list of method variables. If variable does not
     * exist with the provided key, null will be returned.
     *
     * @param String
     *            listName - name of property that has the list
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return List<FirewallRule> - value associated with the named property; null will be returned if not found or an exception
     *         occurs.
     */
    @SuppressWarnings("unchecked")
    public static List<FirewallRule> getFirewallRuleList(String listName, List<MethodVariable> mvs)
    {
        List<FirewallRule> retval = null;

        if (isValued(listName) && mvs != null && !mvs.isEmpty()) {
            byte[] rawList = AzureSQLUtil.getByteVariableValue(listName, mvs);

            retval = (List<FirewallRule>) (rawList != null && rawList.length > 0 ? AzureSQLUtil.deserialize(rawList) : null);
        }

        return retval;
    }

    public static long convertMaxSizeToBytes(String maxSize) throws Exception
    {
        if (AzureSQLUtil.isValued(maxSize)) {
            String[] maxSizeParts = maxSize.split(" ");
            if (maxSizeParts.length != 2) {
                throw new Exception("Unexcpected MaxSize format. Should be formatted like '100 GB'");
            }
            int quantity = Integer.parseInt(maxSizeParts[0]);
            String units = maxSizeParts[1];
            long multiplier;
            if (units.equals("MB")) {
                multiplier = AzureSQLConstants.MB;
            }
            else if (units.equals("GB")) {
                multiplier = AzureSQLConstants.GB;
            }
            else {
                throw new Exception("Unexpected Units value '" + units + "'. Expected MB or GB.");
            }
            return quantity * multiplier;
        }
        else {
            return 0;
        }
    }

    public static String convertBytesToMaxSize(long bytes)
    {
        if (bytes != 0) {
            long quantity = bytes / AzureSQLConstants.MB;
            if (quantity > 999) {
                quantity = bytes / AzureSQLConstants.GB;
                return quantity + " GB";
            }
            return quantity + " MB";
        }
        else {
            return null;
        }
    }

    /**
     * This method will extract the value representing a Server object from a list of method variables. If variable does not exist
     * with the provided key (for server object), null will be returned. The data will be in the byte array. Key Name = @see
     * AzureSQLConstants.PROP_SERVER
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return Server - deserialized Server object from the request; null will be returned if not found
     */
    public static Server getServerObject(List<MethodVariable> mvs)
    {
        Server retval = null;
        String name = AzureSQLConstants.PROP_SERVER;
        AssetProperty ap = AzureSQLUtil.getAssetProperty(name, AzureSQLUtil.getMethodVariable(name, mvs));

        if (ap != null && ap.getByteValue() != null) {
            retval = AzureSQLUtil.deserialize(ap.getByteValue());
        }

        return retval;
    }

    /**
     * This method will extract the value representing a FirewallRule object from a list of method variables. If variable does not
     * exist with the provided key (for FirewallRule object), null will be returned. The data will be in the byte array. Key Name
     * = @see AzureSQLConstants.PROP_FIREWALL_RULE
     *
     * @param List
     *            <MethodVariable> mvs - list of method variables that will be searched.
     * @return FirewallRule - deserialized FirewallRule object from the request; null will be returned if not found
     */
    public static FirewallRule getFirewallRule(List<MethodVariable> mvs)
    {
        FirewallRule retval = null;
        String name = AzureSQLConstants.PROP_FIREWALL_RULE;
        AssetProperty ap = AzureSQLUtil.getAssetProperty(name, AzureSQLUtil.getMethodVariable(name, mvs));

        if (ap != null && ap.getByteValue() != null) {
            retval = AzureSQLUtil.deserialize(ap.getByteValue());
        }

        return retval;
    }

    public static long ipToLong(String ip)
    {
        return IPHelper.ipToLong(ip);
    }

    public static String longToIP(long ipAsLong)
    {
        return IPHelper.longToIP(ipAsLong);
    }

    /**
     * @see List<MethodVariable> createStringArgument(KeyValuePair kvp) Takes individual values for the variable name and variable
     *      value and calls another method for processing.
     * @param String
     *            varName - name of variable
     * @param String
     *            varValue - value of the variable
     * @return List<MethodVariable> - properly formatted argument list for a MethodRequest. If no key value pairs exist, the return
     *         value will be an empty list.
     */
    public static List<MethodVariable> createStringArgument(String varName, String varValue)
    {
        return AzureSQLUtil.createStringArgument(new KeyValuePair(varName, varValue));
    }

    /**
     * @see List<MethodVariable> createStringArguments(List<KeyValuePair> kvps) Takes the single key/value pair and formats for a
     *      call the another method.
     * @param KeyValuePair
     *            kvp - a single key/value pair representing a variable name/variable value
     * @return List<MethodVariable> - properly formatted argument list for a MethodRequest. If no key value pairs exist, the return
     *         value will be an empty list.
     */
    public static List<MethodVariable> createStringArgument(KeyValuePair kvp)
    {
        return AzureSQLUtil.createStringArguments(Arrays.asList(kvp));
    }

    /**
     * This method will convert a list of key/value pairs to a list of method variables that can be used in a MethodRequest
     * action.
     *
     * @param List
     *            <KeyValuePair> kvps - key/value pairs that represent a variable name/variable value
     * @return List<MethodVariable> - properly formatted argument list for a MethodRequest. If no key value pairs exist, the return
     *         value will be an empty list.
     */
    public static List<MethodVariable> createStringArguments(List<KeyValuePair> kvps)
    {
        List<MethodVariable> retval = new ArrayList<MethodVariable>();

        if (kvps != null && !kvps.isEmpty()) {
            for (KeyValuePair kvp : kvps) {
                if (isValued(kvp.getKey()) && isValued(kvp.getValue())) {
                    MethodVariable mv = new MethodVariable();
                    AssetProperty ap = new AssetProperty();

                    ap.setName(kvp.getKey());
                    ap.setStringValue(kvp.getValue());

                    mv.setName(kvp.getKey());
                    mv.getProperties().add(ap);

                    retval.add(mv);
                }
                else {
                    AzureSQLUtil.logger.warn("The variable name [" + kvp.getKey() + "] and the variable value [" + kvp.getValue()
                            + "] must have values in order to create a valid method argument.");
                }
            }
        }
        else {
            AzureSQLUtil.logger.warn("There is no variable infomation to transform to arguments.");
        }

        return retval;
    }

    /**
     * This method will create a method variable that can be used in a MethodRequest action.
     *
     * @param String
     *            key - key that represents a variable name/variable value
     * @param byte[] data - the data that represents the object
     * @return MethodVariable - properly formatted argument for a MethodRequest. If no key or data is provided, the return value
     *         will be null.
     */
    public static MethodVariable createByteArgument(String key, byte[] data)
    {
        MethodVariable retval = null;

        if (isValued(key) && data != null && data.length > 0) {
            AssetProperty ap = new AssetProperty();

            retval = new MethodVariable();
            retval.setName(key);
            ap.setName(key);
            ap.setByteValue(data);
            retval.getProperties().add(ap);
        }
        else {
            AzureSQLUtil.logger.warn("Key and data parameters are required.  Key is null - "
                    + (!isValued(key) + "  data is null - " + (data == null || data.length == 0)));
        }

        return retval;
    }

    /**
     * This method will create a MethodVariable for the provided Server object. A method argument is used by a MethodRequest. A
     * method argument is a List of MethodVariable objects.
     *
     * @param Server
     *            server - object that is to be transformed into MethodVariable
     * @return List<MethodVariable>
     */
    public static List<MethodVariable> createServerArgument(Server server)
    {
        List<MethodVariable> retval = new ArrayList<MethodVariable>();

        if (server != null) {
            MethodVariable mv = new MethodVariable();
            AssetProperty ap = new AssetProperty();
            String propName = AzureSQLConstants.PROP_SERVER;

            ap.setName(propName);
            ap.setByteValue(AzureSQLUtil.serialize(server));

            mv.setName(propName);
            mv.getProperties().add(ap);

            retval.add(mv);
        }
        else {
            AzureSQLUtil.logger.warn("The Server object must have a value.  Nothing to serialize.");
        }

        return retval;
    }

    /**
     * This method will extract the first occurrence of an Asset that matches the provided class.
     *
     * @param List
     *            <Asset> props - list from which to look for the object
     * @param Class
     *            <T> clazz - type of object for which to search
     * @return T - an Asset of type Class<T>; null if not found
     */
    public static <T extends Asset> T getAsset(List<Asset> props, Class<T> clazz)
    {
        return AzureSQLUtil.getAsset(null, props, clazz);
    }

    /**
     * This method will search a list of Asset object and find the first object that matches the specified type and name. If name
     * does not have a value, the first occurrence of the type will be returned. The requested name will be compared to the
     * Asset.getName() value.
     *
     * @param String
     *            propName - name of the desired property; a null value implies the name should be ignored
     * @param List
     *            <Asset> assetList - list from which to search
     * @param Class
     *            <T> clazz - the desired type of Asset to return
     * @return T - Asset that matches the type Class<T> and optionally the name; null if not found
     */
    public static <T extends Asset> T getAsset(String propName, List<Asset> assetList, Class<T> clazz)
    {
        T retval = null;

        if (assetList != null && !assetList.isEmpty() && isValued(clazz)) {
            for (Asset a : assetList) {
                if (a.getClass().isAssignableFrom(clazz)) {
                    T p = clazz.cast(a);

                    if (!isValued(propName)) { // return first of a given type regardless of name
                        retval = p;
                        break;
                    }
                    else if (propName.equals(p.getName())) {
                        retval = p;
                        break;
                    }
                }
            }
        }
        else {
            AzureSQLUtil.logger.trace("On or more parameters did not have values - list null: " + (assetList == null)
                    + "  is class null: " + (clazz == null));
        }

        return retval;
    }

    /**
     * This method will search a list of Asset objects for a particular Property type with the provided name.
     *
     * @param String
     *            propName - name of the desired property
     * @param List
     *            <Asset> assetList - list of Asset objects from which to search
     * @return Property - object that matches the name and is of type Property; null if not found
     */
    public static Property getProperty(String propName, List<Asset> assetList)
    {
        return AzureSQLUtil.getAsset(propName, assetList, Property.class);
    }

    /**
     * This method will search the properties of a ServiceInstanceProvisionRequest object for a particular name. If found, the
     * value associated with the name is returned.
     *
     * @param String
     *            name - desired property name from the request properties
     * @param ServiceInstanceRequest
     *            request - request object to search
     * @return String - the value associated with the name in the request properties; null if not found
     */
    public static String getServiceInstanceRequestProperty(String name, ServiceInstanceRequest request)
    {
        String retval = null;

        if (isValued(name) && request != null && request.getServiceInstance() != null
                && !request.getServiceInstance().getAssetProperties().isEmpty()) {
            AssetProperty ap = AzureSQLUtil.getAssetProperty(name, request.getServiceInstance().getAssetProperties());

            retval = ap != null ? ap.getStringValue() : null;
        }
        else {
            AzureSQLUtil.logger.trace("Missing parameter of data -  name is null: " + isValued(name) + "  request is null: "
                    + (request == null) + "  serviceInstance is null: "
                    + (request != null ? request.getServiceInstance() == null : true));
        }

        return retval;
    }

    /**
     * This method will search the properties of a ServiceInstanceProvisionRequest object for a particular name. If found, the
     * value associated with the name is returned.
     *
     * @param String
     *            name - desired property name from the request properties
     * @param ServiceInstanceRequest
     *            request - request object to search
     * @return String - the value associated with the name in the request properties; null if not found
     */
    public static String getServiceInstanceRequestConfigProperty(String name, ServiceInstanceRequest request)
    {
        String retval = null;

        if (isValued(name) && request != null && request.getServiceInstance() != null
                && !request.getServiceInstance().getConfigurations().isEmpty()) {
            AssetProperty ap = AzureSQLUtil.getAssetProperty(name, request.getServiceInstance().getConfigurations());

            retval = ap != null ? ap.getStringValue() : null;
        }
        else {
            AzureSQLUtil.logger.trace("Missing parameter of data -  name is null: " + isValued(name) + "  request is null: "
                    + (request == null) + "  serviceInstance is null: "
                    + (request != null ? request.getServiceInstance() == null : true));
        }

        return retval;
    }

    /**
     * This method will extract the value associated with the server name property in a request.
     * 
     * @see AzureSQLConfig.CONFIG_SERVER_NAME
     * @param ServiceInstanceRequest
     *            request - request object to search
     * @return String - value of the server name property; null if not found
     */
    public static String getServerName(ServiceInstanceRequest request)
    {
        return AzureSQLUtil.getServiceInstanceRequestConfigProperty(AzureSQLConfig.CONFIG_SERVER_NAME, request);
    }

    /**
     * This method will extract the value associated with the database name property in a request.
     * 
     * @see AzureSQLConfig.CONFIG_DB_NAME
     * @param ServiceInstanceRequest
     *            request - request object to search
     * @return String - value of the database name property; null if not found
     */
    public static String getDatabaseName(ServiceInstanceRequest request)
    {
        return AzureSQLUtil.getServiceInstanceRequestProperty(AzureSQLConfig.CONFIG_DB_NAME, request);
    }

    /**
     * This method will create a String AssetProperty object given a name and value pair. The name parameter cannot be null.
     *
     * @param String
     *            name - value to which the name property of the AssetProperty will be set
     * @param String
     *            value - value to which the string value property of the AssetProperty will be set
     * @return AssetProperty - initialized AssetProperty object; null if the name parameter is missing
     */
    public static AssetProperty makeAssetProperty(String name, String value)
    {
        AssetProperty retval = null;

        if (isValued(name)) {
            retval = new AssetProperty();

            retval.setName(name);
            retval.setStringValue(value);
        }
        else {
            AzureSQLUtil.logger.trace("The name parameter is null.  No AssetProperty will be created.");
        }

        return retval;
    }

    /**
     * This method will create an Integer AssetProperty object given a name and value pair. The name parameter cannot be null.
     *
     * @param String
     *            name - value to which the name property of the AssetProperty will be set
     * @param Integer
     *            value - value to which the integer value property of the AssetProperty will be set
     * @return AssetProperty - initialized AssetProperty object; null if the name parameter is missing
     */
    public static AssetProperty makeAssetProperty(String name, Integer value)
    {
        AssetProperty retval = null;

        if (isValued(name)) {
            retval = new AssetProperty();

            retval.setName(name);
            retval.setIntValue(value);
        }
        else {
            AzureSQLUtil.logger.trace("The name parameter is null.  No AssetProperty will be created.");
        }

        return retval;
    }

    /**
     * This method will create a byte[] AssetProperty object given a name and value pair. The name parameter cannot be null. If
     * the data is null or not serializable, the byte array will be null.
     *
     * @param String
     *            name - value to which the name property of the AssetProperty will be set
     * @param Object
     *            value - serialized value to which the byte array value property of the AssetProperty will be set
     * @return AssetProperty - initialized AssetProperty object; null if the name parameter is missing.
     */
    public static AssetProperty makeAssetProperty(String name, Object data)
    {
        AssetProperty retval = null;

        if (isValued(name)) {
            retval = new AssetProperty();

            retval.setName(name);
            retval.setByteValue(AzureSQLUtil.serialize(data)); // this could be null
        }
        else {
            AzureSQLUtil.logger.trace("The name parameter is null.  No AssetProperty will be created.");
        }

        return retval;
    }

    /**
     * This method will create a byte[] MethodVariable object given a name and value pair. The name parameter cannot be null.
     *
     * @param String
     *            name - value to which the name property of the AssetProperty will be set
     * @param Object
     *            value - serialized value to which the byte array value property of the AssetProperty will be set
     * @return AssetProperty - initialized AssetProperty object; null if the name parameter is missing.
     */
    public static MethodVariable makeMethodVariable(String name, Object data)
    {
        MethodVariable retval = null;
        AssetProperty ap = AzureSQLUtil.makeAssetProperty(name, data);

        if (isValued(name)) {
            retval = new MethodVariable();

            retval.setName(name);
            retval.getProperties().add(ap);
        }

        return retval;
    }

    /**
     * This method will create a byte[] MethodVariable object given a name and value pair. The name parameter cannot be null.
     *
     * @param String
     *            name - value to which the name property of the AssetProperty will be set
     * @param String
     *            value - value to which the string property of the AssetProperty will be set
     * @return AssetProperty - initialized AssetProperty object; null if the name parameter is missing.
     */
    public static MethodVariable makeMethodVariable(String name, String value)
    {
        MethodVariable retval = null;
        AssetProperty ap = AzureSQLUtil.makeAssetProperty(name, value);

        if (isValued(name)) {
            retval = new MethodVariable();

            retval.setName(name);
            retval.getProperties().add(ap);
        }

        return retval;
    }

    /**
     * This method will check to see if a given IP address falls between two other IP address values.
     *
     * @param String
     *            ipAddress - IP address to check
     * @param String
     *            minIpAddress - minimum IP address of a range
     * @param String
     *            maxIpAddress - maximum IP address of a range
     * @return boolean - true if the IP address falls within the range
     */
    public static boolean isIpInRange(String ipAddress, String minIpAddress, String maxIpAddress)
    {
        boolean retval = false;

        if (isValued(ipAddress) && isValued(minIpAddress) && isValued(maxIpAddress)) {
            long ip = AzureSQLUtil.ipToLong(ipAddress);
            long min = AzureSQLUtil.ipToLong(minIpAddress);
            long max = AzureSQLUtil.ipToLong(maxIpAddress);

            retval = min <= max && ip >= min && ip <= max;
        }

        return retval;
    }

    /**
     * This method will create a failure MethodResponse object and populate the common fields. Any message will be logged to the
     * specified logger. The status will be FAILURE and the log LEVEL will be ERROR. This is used for a failure that was not
     * caused by an exception.
     *
     * @see public static MethodResponse buildFailedMethodResponse(long, String, Logger, Exception)
     * @param long reqId - request ID value
     * @param String
     *            msg - message to be assigned to the response as well as logged
     * @param log
     * @return MethodResponse - a response object with the common properties populated
     */
    public static MethodResponse buildFailedMethodResponse(long reqId, String msg, Logger log)
    {
        return AzureSQLUtil.buildFailedMethodResponse(reqId, msg, log, null);
    }

    /**
     * This method will create a failure MethodResponse object and populate the common fields. Any message and/or response
     * exception will be logged to the specified logger. The status will be FAILURE and the log LEVEL will be ERROR.
     *
     * @see public static <T extends Response> T buildResponse(Class<T>, long, Status, String, Level, Logger, Exception)
     * @param long reqId - request ID value
     * @param String
     *            msg - message to be assigned to the response as well as logged
     * @param log
     * @param Exception
     *            e - exception that caused the failure - the exception message will be written to the log
     * @return MethodResponse - a response object with the common properties populated
     */
    public static MethodResponse buildFailedMethodResponse(long reqId, String msg, Logger log, Exception e)
    {
        return AzureSQLUtil.buildResponse(MethodResponse.class, reqId, Status.FAILURE, msg, Level.ERROR, log, e);
    }

    /**
     * This method will create a failure ServiceProviderResponse object and populate the common fields. Any message will be logged
     * to the specified logger. The status will be FAILURE and the log LEVEL will be ERROR. This is used for a failure that was
     * not caused by an exception.
     *
     * @see public static ServiceProviderResponse buildFailedServiceProviderResponse(long, String, Logger, Exception)
     * @param long reqId - request ID value
     * @param String
     *            msg - message to be assigned to the response as well as logged
     * @param log
     * @return ServiceProviderResponse - a response object with the common properties populated
     */
    public static ServiceProviderResponse buildFailedServiceProviderResponse(long reqId, String msg, Logger log)
    {
        return AzureSQLUtil.buildFailedServiceProviderResponse(reqId, msg, log, null);
    }

    /**
     * This method will create a failure ServiceProviderResponse object and populate the common fields. Any message and/or
     * response exception will be logged to the specified logger. The status will be FAILURE and the log LEVEL will be ERROR.
     *
     * @see public static <T extends Response> T buildResponse(Class<T>, long, Status, String, Level, Logger, Exception)
     * @param long reqId - request ID value
     * @param String
     *            msg - message to be assigned to the response as well as logged
     * @param log
     * @param Exception
     *            e - exception that caused the failure - the exception message will be written to the log
     * @return ServiceProviderResponse - a response object with the common properties populated
     */
    public static ServiceProviderResponse buildFailedServiceProviderResponse(long reqId, String msg, Logger log, Exception e)
    {
        return AzureSQLUtil.buildResponse(ServiceProviderResponse.class, reqId, Status.FAILURE, msg, Level.ERROR, log, e);
    }

    /**
     * This method will build a general response object and populate the common fields to all subclass of Response. The type of
     * response will be identified by the respType parameter. If the respType parameter is null or an exception occurs, the result
     * of this method call will be null.
     *
     * @param Class
     *            <T> respType - class identifying the type of response to create
     * @param long reqId - request ID value
     * @param Status
     *            status - desired status of the response; default is FAILURE
     * @param String
     *            msg - message to be assigned to the response as well as logged
     * @param Level
     *            logLevel - log4j logging level used to write the message to the logger; default is DEBUG
     * @param Logger
     *            log - log file to be used for logging any messages and response exception parameters; default is
     *            AzureSQLUtil.logger
     * @param Exception
     *            e - exception that caused the failure - the exception message will be written to the log
     * @return <T extends Response> - a subclass of the Response class as identified by the respType parameter; null will be
     *         returned if the respType is null or an exception occurs
     */
    public static <T extends Response> T buildResponse(Class<T> respType, long reqId, Status status, String msg, Level logLevel,
            Logger log, Exception e)
    {

        if (respType != null) {
            AzureSQLUtil.logger.trace("Creating " + status + " response for type " + respType.getName());

            try {
                T response = respType.newInstance();
                Logger localLogger = log != null ? log : AzureSQLUtil.logger;
                Level localLevel = logLevel != null ? logLevel : Level.DEBUG;
                Status localStatus = status != null ? status : Status.FAILURE;

                localLogger.log(localLevel, msg, e);

                response.setReqId(reqId);
                response.setTimestamp(Calendar.getInstance().getTimeInMillis());
                response.setStatus(localStatus);
                response.setMessage(msg + (e != null ? "\n" + e.getMessage() : ""));

                return response;
            }
            catch (Exception ex) {
                String errMsg = "An exception occurred while creating a new instance of " + respType.getName();
                AzureSQLUtil.logger.warn(errMsg, e);

                return null;
            }
        }
        else {
            AzureSQLUtil.logger.warn("The response type parameter is null so no response object can be created.");
            return null;
        }
    }

    public static void checkForSQLServerError(IHttpResponse response, AzureEndpoint endpoint) throws AzureAdapterException
    {
        if (response.getContent().contains(AzureSQLConstants.SQLSERVER_ERROR_RESPONSE)) {
            services.web.framework.management.sqlserver.microsoft.Error error =
                    endpoint.decode(response, AzureSQLConstants.SQLSERVER_CONTEXT,
                            services.web.framework.management.sqlserver.microsoft.Error.class);
            String errorMsg = AzureSQLUtil.NO_ERROR_MESSAGE;

            if (error != null) {
                errorMsg = isValued(error.getCode()) ? error.getCode() + ":" + error.getMessage() : error.getMessage();
            }

            throw new AzureAdapterException(response.getStatusCode(), errorMsg);
        }
    }

    public static void checkForGenericError(IHttpResponse response, AzureEndpoint endpoint) throws AzureAdapterException
    {
        if (response.getContent().contains(AzureSQLConstants.GENERIC_ERROR_RESPONSE)) {
            com.microsoft.schemas.windowsazure.Error error =
                    endpoint.decode(response, AzureSQLConstants.GENERIC_AZURE_CONTEXT,
                            com.microsoft.schemas.windowsazure.Error.class);
            String errorMsg = AzureSQLUtil.NO_ERROR_MESSAGE;

            if (error != null) {
                errorMsg = isValued(error.getCode()) ? error.getCode() + ":" + error.getMessage() : error.getMessage();
            }

            throw new AzureAdapterException(response.getStatusCode(), errorMsg);
        }
    }

    public static void updateStringProperty(List<AssetProperty> props, String propertyName, String newValue)
    {
        for (int i = 0; i < props.size(); i++) {
            if (props.get(i).getName().equals(propertyName)) {
                props.get(i).setStringValue(newValue);
                break;
            }
        }
    }

    public static void clearProperty(List<AssetProperty> props, String propertyName)
    {
        for (int i = 0; i < props.size(); i++) {
            if (props.get(i).getName().equals(propertyName)) {
                props.remove(i);
            }
        }
    }

    /**
     * When adding a property from AzureSQL onto another asset we strip the property definition off and prepend "AzureSQL" on the
     * property name so that it is clear what the property is referencing.
     * 
     * @param list
     * @param variable
     */
    public static void addSQLProperty(List<AssetProperty> list, AssetProperty variable)
    {
        // Need to strip property definition information off of instance variables so they will show in the UI
        variable.setPropertyDefinition(null);
        variable.setName(AzureSQLConstants.SQL_PROP_PREFIX + variable.getName());
        list.add(variable);
    }

    public static String escapeSpaces(String input)
    {
        return input.replaceAll(" ", "%20");
    }

    /**
     * This method will extract Database properties from the AssetProperty list of a ServiceInstance object. If the database size
     * cannot be properly converted, given size and units, the max size will be set to null.
     *
     * @param ServiceInstance
     *            serviceInstance - definition object that describes the service
     * @return Database - extracted properties that relate to the database component of the service; null will be returned if no
     *         properties are defined.
     */
    public static Database getDatabase(ServiceInstance serviceInstance)
    {
        Database retval = null;

        if (serviceInstance != null) {
            List<AssetProperty> props = serviceInstance.getAssetProperties();

            retval = new Database();

            retval.setCollationName(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_COLLATION, props, null));
            retval.setEdition(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_EDITION, props, null));

            String maxSize = AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_MAX_SIZE, props, null);

            try {
                if (isValued(maxSize)) {
                    retval.setMaxSizeBytes(AzureSQLUtil.convertMaxSizeToBytes(maxSize));
                }
                else {
                    retval.setMaxSizeBytes(null);
                }
            }
            catch (Exception ex) {
                // if the size is not configured properly, do not set and let it default based on Edition type
                retval.setMaxSizeBytes(null);
                AzureSQLUtil.logger.warn("The database size '" + maxSize
                        + "' is not configured properly.  The default value for edition '" + retval.getEdition()
                        + " will be used.");
            }

            retval.setName(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_DB_NAME, props, null));
            retval.setServiceObjectiveId(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_SERVICE_OBJECTIVE_ID,
                    props, null));

            // if everything is empty, return null - this implies no properties were included in the serviceInstance
            if (!isValued(retval.getCollationName()) && !isValued(retval.getEdition()) && !isValued(maxSize)
                    && !isValued(retval.getName()) && !isValued(retval.getServiceObjectiveId())) {
                retval = null;
            }
        }

        return retval;
    }

    /**
     * This method will extract Server properties from the Configurations list of a ServiceInstance object.
     *
     * @param ServiceInstance
     *            serviceInstance - definition object that describes the service
     * @return Server - extracted properties that relate to the server component of the service; null will be returned if no
     *         properties are defined.
     */
    public static Server getServer(ServiceInstance serviceInstance)
    {
        Server retval = null;

        if (serviceInstance != null) {
            List<AssetProperty> config = serviceInstance.getConfigurations();
            List<AssetProperty> props = serviceInstance.getAssetProperties();

            retval = new Server();

            retval.setName(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_SERVER_NAME, config, null));
            retval.setAdministratorLogin(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_ADMIN_LOGIN, props, null));
            retval.setAdministratorLoginPassword(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_ADMIN_PASS, props,
                    null));
            retval.setLocation(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_AZURE_SQL_SERVER_LOCATION, props,
                    null));

            // if everything is empty, return null - this implies no properties were included in the serviceInstance
            if (!isValued(retval.getName()) && !isValued(retval.getAdministratorLogin())
                    && !isValued(retval.getAdministratorLoginPassword()) && !isValued(retval.getLocation())) {
                retval = null;
            }
        }

        return retval;
    }

    /**
     * This method will extract FirewallRule properties from the AssetProperty list of a ServiceInstance object.
     *
     * @param ServiceInstance
     *            serviceInstance - definition object that describes the service
     * @return FirewallRule - extracted properties that relate to the firewall rule component of the service; null will be returned
     *         if no properties are defined.
     */
    public static FirewallRule getFirewallRule(ServiceInstance serviceInstance)
    {
        FirewallRule retval = null;

        if (serviceInstance != null) {
            List<AssetProperty> props = serviceInstance.getAssetProperties();

            retval = new FirewallRule();

            retval.setName(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_FIREWALL_RULE_NAME, props, null));
            retval.setStartIPAddress(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_STARTING_ALLOWED_IP, props,
                    null));
            retval.setEndIPAddress(AzureSQLConfig.getAssetPropertyAsString(AzureSQLConfig.CONFIG_ENDING_ALLOWED_IP, props, null));

            // if everything is empty, return null - this implies no properties were included in the serviceInstance
            if (!isValued(retval.getName()) && !isValued(retval.getStartIPAddress()) && !isValued(retval.getEndIPAddress())) {
                retval = null;
            }
        }

        return retval;
    }

    /**
     * This method will replace FirewallRule properties for the AssetProperty list of a ServiceInstance object.
     *
     * @param ServiceInstance
     *            serviceInstance - definition object that describes the service
     * @param FirewallRule
     *            fwRule - new FirewallRule properties
     * @return ServiceInstance - updated ServiceInstance object
     */
    public static ServiceInstance updateFirewallRule(final ServiceInstance serviceInstance, FirewallRule fwRule)
    {
        ServiceInstance retval = serviceInstance;

        if (retval != null && fwRule != null) {
            List<AssetProperty> props = serviceInstance.getAssetProperties();

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_FIREWALL_RULE_NAME);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_FIREWALL_RULE_NAME, fwRule.getName()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_STARTING_ALLOWED_IP);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_STARTING_ALLOWED_IP, fwRule.getStartIPAddress()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_ENDING_ALLOWED_IP);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_ENDING_ALLOWED_IP, fwRule.getEndIPAddress()));
        }

        return retval;
    }

    /**
     * This method will replace Server properties for the Configurations list of a ServiceInstance object.
     *
     * @param ServiceInstance
     *            serviceInstance - definition object that describes the service
     * @param Server
     *            server - new Server properties
     * @return ServiceInstance - updated ServiceInstance object
     */
    public static ServiceInstance updateServer(final ServiceInstance serviceInstance, Server server)
    {
        ServiceInstance retval = serviceInstance;

        if (retval != null && server != null) {
            List<AssetProperty> config = serviceInstance.getConfigurations();
            List<AssetProperty> props = serviceInstance.getAssetProperties();

            AzureSQLUtil.clearProperty(config, AzureSQLConfig.CONFIG_SERVER_NAME);
            config.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_SERVER_NAME, server.getName()));

            AzureSQLUtil.clearProperty(config, AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME);
            config.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_SERVER_DOMAIN_NAME, server.getName()
                    + AzureSQLConstants.AZURE_DOMAIN_SUFFIX));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_ADMIN_LOGIN);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_ADMIN_LOGIN, server.getAdministratorLogin()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_ADMIN_PASS);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_ADMIN_PASS, server.getAdministratorLoginPassword()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_AZURE_SQL_SERVER_LOCATION);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_AZURE_SQL_SERVER_LOCATION, server.getLocation()));
        }

        return retval;
    }

    /**
     * This method will replace Database properties for the AssetProperty list of a ServiceInstance object.
     *
     * @param ServiceInstance
     *            serviceInstance - definition object that describes the service
     * @param Database
     *            db - new Database properties
     * @return ServiceInstance - updated ServiceInstance object
     */
    public static ServiceInstance updateDatabase(final ServiceInstance serviceInstance, Database db)
    {
        ServiceInstance retval = serviceInstance;

        if (retval != null && db != null) {
            List<AssetProperty> props = serviceInstance.getAssetProperties();

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_DB_NAME);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_DB_NAME, db.getName()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_COLLATION);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_COLLATION, db.getCollationName()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_EDITION);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_EDITION, db.getEdition()));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_MAX_SIZE);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_MAX_SIZE,
                    db.getMaxSizeBytes() != null ? AzureSQLUtil.convertBytesToMaxSize(db.getMaxSizeBytes()) : null));

            AzureSQLUtil.clearProperty(props, AzureSQLConfig.CONFIG_SERVICE_OBJECTIVE_ID);
            props.add(AzureSQLUtil.makeAssetProperty(AzureSQLConfig.CONFIG_SERVICE_OBJECTIVE_ID, db.getServiceObjectiveId()));
        }

        return retval;
    }

    /**
     * This method will stub out a MethodRequest object from a ServiceProviderRequest.
     *
     * @param ServiceProviderRequest
     *            request - original request
     * @return MethodRequest - new request object built from the original request.
     */
    public static MethodRequest makeMethodRequest(ServiceProviderRequest request)
    {
        MethodRequest retval = new MethodRequest();

        if (request != null) {
            retval.setProvider(request.getProvider());
            retval.setReqId(request.getReqId());
            retval.setUser(request.getUser());
            retval.getClouds().addAll(request.getClouds());
            retval.setTimestamp(System.currentTimeMillis());
            retval.getArguments().clear();
        }

        return retval;
    }

    /**
     * This method will convert an object into an array of bytes. Note - this was used instead of
     * org.apache.commons.lang.SerializationUtils methods because there is a bug that causes non-compliance with OSGi.
     *
     * @param Object
     *            obj - the object to be serialized. This object must implement java.io.Serializable.
     * @return byte[] - serialized form of the object. The array will be null if the input object is null, not serializable, or an
     *         exception occurs
     */
    public static byte[] serialize(Object obj)
    {
        byte[] retval = null;

        if (obj != null) {
            if (obj instanceof java.io.Serializable) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput output = null;

                try {
                    output = new ObjectOutputStream(bos);
                    output.writeObject(obj);
                    retval = bos.toByteArray();
                }
                catch (Exception e) {
                    AzureSQLUtil.logger.error("Unable to serialize object " + obj.getClass().getName() + " due to exception.", e);
                }
                finally {
                    try {
                        if (output != null) {
                            output.close();
                        }
                    }
                    catch (Exception ex) {
                        AzureSQLUtil.logger.debug(
                                "An exception occurred while closing the output object during the serialization of object."
                                        + obj.getClass().getName(), ex);
                    }

                    try {
                        bos.close();
                    }
                    catch (Exception ex) {
                        AzureSQLUtil.logger
                                .debug("An exception occurred while closing the byte output stream object during the serialization of object.",
                                        ex);
                    }
                }
            }
            else {
                AzureSQLUtil.logger.warn("The object " + obj.getClass().getName()
                        + " cannot be serialized because it is not Serializable.");
            }
        }
        else {
            AzureSQLUtil.logger.warn("The object cannot be serialized because it is null.");
        }

        return retval;
    }

    /**
     * This method will convert a serialized object back into the original object. Note - this was used instead of
     * org.apache.commons.lang.SerializationUtils methods because there is a bug that causes non-compliance with OSGi.
     *
     * @param byte[] array - array of bytes representing serialized object. This must represent an object that implements
     *        java.io.Serializable.
     * @return T - new object created from the byte array. The object will be null if the array is null/empty, or an exception
     *         occurs.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] array)
    {
        T retval = null;

        if (array != null && array.length > 0) {
            ByteArrayInputStream bis = new ByteArrayInputStream(array);
            ObjectInput input = null;

            try {
                input = new ObjectInputStream(bis);

                try {
                    retval = (T) input.readObject();
                }
                catch (Exception ce) {
                    AzureSQLUtil.logger.error("An exception occurred while casting deserialized object.", ce);
                }
            }
            catch (Exception e) {
                AzureSQLUtil.logger.error("Unable to deserialize object due to exception.", e);
            }
            finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                }
                catch (Exception ex) {
                    AzureSQLUtil.logger.debug("An exception occurred while closing the output object.", ex);
                }

                try {
                    bis.close();
                }
                catch (Exception ex) {
                    AzureSQLUtil.logger.debug("An exception occurred while closing the byte input stream object.", ex);
                }
            }
        }
        else {
            AzureSQLUtil.logger.warn("The object cannot be deserialized because the contents are null.");
        }

        return retval;
    }

    /**
     * This method will create an exact copy of a given object. This is accomplished by serializing and deserializing the object.
     * This implies that the object must be serializable. If it is not, null will be returned. Note - this was used instead of
     * org.apache.commons.lang.SerializationUtils methods because there is a bug that causes non-compliance with OSGi.
     *
     * @param T
     *            obj - object to be cloned
     * @return T - new object created from provided object; null will be returned if the provided object is null or not
     *         serializable
     */
    public static <T> T clone(T obj)
    {
        T retval = null;

        if (obj != null) {
            if (obj instanceof java.io.Serializable) {
                retval = AzureSQLUtil.deserialize(AzureSQLUtil.serialize(obj));
            }
            else {
                AzureSQLUtil.logger.warn("Only serializable objects can be cloned.  Object " + obj.getClass().getName()
                        + " is not serializable.");
            }
        }
        else {
            AzureSQLUtil.logger.warn("The object cannot be cloned because it is null.");
        }

        return retval;
    }

    /**
     * This method will mark a serviceInstance as being degraded. This can be caused by an operation partially working.
     *
     * @param ServiceInstance
     *            serviceInstance - the service to degrade
     * @param String
     *            reason - reason text for the degradation. If null, a default value will be set.
     * @return ServiceInstance - the updated serviceInstance
     */
    public static ServiceInstance degradeService(ServiceInstance serviceInstance, String reason)
    {
        ServiceInstance si = serviceInstance;

        if (si != null) {
            si.setState(ServiceState.DEGRADED);
            AssetProperty degradedProp =
                    AzureSQLUtil.getAssetProperty(AzureSQLConstants.PROP_DEGRADED_REASON, si.getConfigurations());
            if (degradedProp != null) { // if the service has degraded reason already then append the new issue to the existing degraded reason
                String degradedReason =
                        degradedProp.getStringValue() + "\n"
                                + (isValued(reason) ? reason : AzureSQLConstants.DEFAULT_DEGRADED_REASON);
                AzureSQLUtil.updateStringProperty(si.getConfigurations(), AzureSQLConstants.PROP_DEGRADED_REASON, degradedReason);
            }
            else {
                si.getConfigurations().add(
                        AzureSQLUtil.makeAssetProperty(AzureSQLConstants.PROP_DEGRADED_REASON, isValued(reason) ? reason
                                : AzureSQLConstants.DEFAULT_DEGRADED_REASON));
            }
        }

        return si;
    }

}
