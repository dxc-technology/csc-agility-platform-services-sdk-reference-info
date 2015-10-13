/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.connection;

import java.io.Serializable;
import java.util.Calendar;

import javax.xml.bind.JAXBContext;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.core.azure.AzureEndpoint;
import com.servicemesh.agility.adapters.core.azure.AzureEndpointFactory;
import com.servicemesh.agility.adapters.core.azure.exception.AzureAdapterException;
import com.servicemesh.io.http.IHttpResponse;

public class AzureSQLEndpoint implements Serializable, AzureEndpoint
{
    private static Logger logger = Logger.getLogger(AzureSQLEndpoint.class);
    private static final long serialVersionUID = 20150123;

    private AzureEndpoint endpoint;

    public <E> AzureSQLEndpoint(String subscription, String msVersion, String msContextPath, Class<E> msErrorClass)
            throws AzureAdapterException
    {
        AzureSQLEndpoint.logger.debug("Requesting an AzureSQLEndpoint - " + Calendar.getInstance().getTimeInMillis());
        AzureSQLEndpoint.logger.debug("Subscription = " + subscription);
        AzureSQLEndpoint.logger.debug("msVersion = " + msVersion);
        AzureSQLEndpoint.logger.debug("msContextpath = " + msContextPath);
        AzureSQLEndpoint.logger.debug("Error class[" + (msErrorClass != null ? msErrorClass.getName() : "null")
                + "] has a value - " + (msErrorClass != null));

        try {
            endpoint = AzureEndpointFactory.getInstance().getEndpoint(subscription, msVersion, msContextPath, msErrorClass);
        }
        catch (Exception e) {
            String msg = "An exception occurred while creating an AzureSQLEndpoint.";

            AzureSQLEndpoint.logger.error(msg, e);

            throw new AzureAdapterException(e);
        }
    }

    @Override
    public <T> T decode(IHttpResponse response, Class<T> responseClass)
    {
        return endpoint.decode(response, responseClass);
    }

    @Override
    public <T> T decode(IHttpResponse response, String responseContextPath, Class<T> responseClass)
    {
        return endpoint.decode(response, responseContextPath, responseClass);
    }

    @Override
    public String encode(Object obj)
    {
        return endpoint.encode(obj);
    }

    @Override
    public String encode(String contextPath, Object obj)
    {
        return endpoint.encode(contextPath, obj);
    }

    @Override
    public String getAddress()
    {
        return endpoint.getAddress();
    }

    @Override
    public String getContentType()
    {
        return endpoint.getContentType();
    }

    @Override
    public JAXBContext getContext()
    {
        return endpoint.getContext();
    }

    @Override
    public JAXBContext getContext(String contextPath)
    {
        return endpoint.getContext(contextPath);
    }

    @Override
    public String getMsVersion()
    {
        return endpoint.getMsVersion();
    }

    @Override
    public String getSubscription()
    {
        return endpoint.getSubscription();
    }
}
