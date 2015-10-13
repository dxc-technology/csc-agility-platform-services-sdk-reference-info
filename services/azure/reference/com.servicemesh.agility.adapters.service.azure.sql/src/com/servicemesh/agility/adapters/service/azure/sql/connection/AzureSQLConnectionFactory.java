/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.connection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.agility.adapters.core.azure.AzureConnectionFactory;
import com.servicemesh.agility.adapters.core.azure.AzureEndpoint;
import com.servicemesh.agility.adapters.core.azure.AzureEndpointFactory;
import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.agility.adapters.core.azure.util.AzureUtil;
import com.servicemesh.agility.api.Cloud;
import com.servicemesh.agility.api.Credential;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.io.proxy.Proxy;

public class AzureSQLConnectionFactory implements Serializable
{
    private static Logger logger = Logger.getLogger(AzureSQLConnectionFactory.class);
    private static final long serialVersionUID = 20150123;

    private final AzureConnectionFactory factory;

    public AzureSQLConnectionFactory() throws AzureAdapterException
    {
        factory = AzureConnectionFactory.getInstance();
    }

    /**
     * Gets an Azure connection.
     *
     * @param List
     *            <Property> settings - The configuration settings for the connection. Optional - may be empty or null.
     * @param Credential
     *            credentials - Must be a credential that contains a certificate and a private key.
     * @param Proxy
     *            proxy - The proxy to be utilized. Optional - may be null.
     * @param AzureEndpoint
     *            endpoint - Provides data specific to an Azure service.
     * @see com.servicemesh.agility.adapters.core.azure.Config
     * @return An Azure connection
     */
    public AzureConnection getConnection(List<Property> settings, Credential certificate, Proxy proxy, AzureSQLEndpoint endpoint)
            throws AzureAdapterException
    {
        return getConnection(settings, new ArrayList<Credential>(Arrays.asList(certificate)), proxy, endpoint);
    }

    /**
     * Gets an Azure connection.
     *
     * @param List
     *            <Property> settings - The configuration settings for the connection. Optional - may be empty or null.
     * @param List
     *            <Credential> credentials - All available connections - the first credential with a certificate will be utilized.
     *            The credential must also contain a private key for the certificate.
     * @param Proxy
     *            proxy - The proxy to be utilized. Optional - may be null.
     * @param AzureEndpoint
     *            endpoint - Provides data specific to an Azure service.
     * @see com.servicemesh.agility.adapters.core.azure.Config
     * @return An Azure connection
     */
    public AzureConnection getConnection(List<Property> settings, List<Credential> credentials, Proxy proxy,
            AzureSQLEndpoint endpoint) throws AzureAdapterException
    {
        AzureSQLConnectionFactory.logger.debug("Requesting an AzureSQLConnection - " + Calendar.getInstance().getTimeInMillis());

        AzureSQLConnectionFactory.logger.debug("The settings parameter has a value - " + (settings != null));

        if (AzureSQLConnectionFactory.logger.isTraceEnabled()) {
            AzureSQLConnectionFactory.logger.trace(AzureSQLConnectionFactory.settingsToString(settings));
        }

        AzureSQLConnectionFactory.logger.debug("The credentials parameter has a value - " + (credentials != null));

        if (AzureSQLConnectionFactory.logger.isTraceEnabled()) {
            AzureSQLConnectionFactory.logger.trace(AzureSQLConnectionFactory.credentialsToString(credentials));
        }

        AzureSQLConnectionFactory.logger.debug("The proxy parameter has a value - " + (proxy != null));
        AzureUtil.logObject(proxy, AzureSQLConnectionFactory.logger);

        AzureSQLConnectionFactory.logger.debug("The endpoint parameter has a value - " + (endpoint != null));
        AzureUtil.logObject(endpoint, AzureSQLConnectionFactory.logger);

        try {
            return factory.getConnection(settings, credentials, proxy, (AzureEndpoint) endpoint);
        }
        catch (Exception e) {
            String msg = "An exception occurred while creating an AzureSQLConnection.";

            AzureSQLConnectionFactory.logger.error(msg, e);

            throw new AzureAdapterException(e);
        }
    }

    /**
     * This method will convert a list of settings to a string for logging purposes.
     * 
     * @param List
     *            <Property> settings - list of Property objects
     * @return String - value to be used for logging
     */
    private static String settingsToString(List<Property> settings)
    {
        if (settings != null && !settings.isEmpty()) {
            StringBuilder buf = new StringBuilder("\nThe settings parameter contains:\n");
            String sep = "";

            for (Property p : settings) {
                buf.append(sep + "[" + p.getName() + ":" + p.getValue() + "]");
                sep = ",";
            }

            return buf.toString();
        }
        else {
            return "No settings were defined.";
        }
    }

    /**
     * This method will convert a list of Credentials to a string for logging purposes.
     * 
     * @param List
     *            <Credential> credentials - list of Credential objects
     * @return String - value to be used for logging
     */
    private static String credentialsToString(List<Credential> credentials)
    {
        if (credentials != null && !credentials.isEmpty()) {
            StringBuilder buf = new StringBuilder("\nThe credentials parameter contains:\n");
            String sep = "";

            for (Credential p : credentials) {
                buf.append(sep + "[certificateName:" + p.getCertificateName() + ", credentialId:" + p.getCredentialId()
                        + ", credentialType:" + p.getCredentialType() + ", adminUser:" + p.getAdminUser() + ", applicationType:"
                        + p.getApplicationType() + "]");
                sep = ",";
            }

            return buf.toString();
        }
        else {
            return "No credentials were defined.";
        }
    }

    /**
     * This method will get the subscription of the cloud provider. If a subscription was provided when setting up the service
     * provider that subscription will be used. Otherwise it defaults to the cloud's subscription.
     * 
     * @param provider
     * @param clouds
     * @return
     * @throws Exception
     */
    public static String getSubscription(ServiceProvider provider, List<Cloud> clouds) throws Exception
    {
        return AzureConnectionFactory.getSubscription(provider, clouds);
    }

    /**
     * This method will get the credentials for the cloud provider. If the certificate and private key were provided when setting
     * up the service provider then those values will be used. Otherwise it defualts to using the values from the cloud provider.
     * 
     * @param provider
     * @param clouds
     * @return
     * @throws Exception
     */
    public static Credential getCredentials(ServiceProvider provider, List<Cloud> clouds) throws Exception
    {
        return AzureConnectionFactory.getCredentials(provider, clouds);
    }

    /**
     * Unregisters the contexts for the Azure SQL schemas Any contexts that were used to create an endpoint need to be
     * unregistered here
     */
    public void unregisterContext()
    {
        AzureEndpointFactory.getInstance().unregisterContext(com.servicemesh.azure.sql.models.Error.class.getPackage().getName());
        AzureEndpointFactory.getInstance().unregisterContext(
                com.microsoft.schemas.windowsazure.Error.class.getPackage().getName());
    }

}
