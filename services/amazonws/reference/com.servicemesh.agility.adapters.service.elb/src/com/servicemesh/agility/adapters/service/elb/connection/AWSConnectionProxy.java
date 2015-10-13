/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.elb.connection;

import java.util.List;

import org.apache.log4j.Logger;

import com.servicemesh.agility.adapters.core.aws.AWSConnection;
import com.servicemesh.agility.adapters.core.aws.AWSConnectionFactory;
import com.servicemesh.agility.adapters.core.aws.AWSCredentialFactory;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Cloud;
import com.servicemesh.agility.api.Credential;
import com.servicemesh.agility.api.Property;
import com.servicemesh.agility.api.ServiceProvider;
import com.servicemesh.agility.sdk.service.exception.ServiceProviderException;
import com.servicemesh.core.async.Promise;
import com.servicemesh.io.http.IHttpResponse;
import com.servicemesh.io.http.QueryParams;
import com.servicemesh.io.proxy.Proxy;

/**
 * This class provides methods to create connections for communicating with AWS.
 */
public class AWSConnectionProxy
{
    private static final Logger logger = Logger.getLogger(AWSConnectionProxy.class);

    private AWSConnection _connection;
    private AWSEndpointProxy _endpointProxy;

    /**
     * Constructor.
     * 
     * @param connection
     *            object representing the connection to AWS
     * @param proxy
     *            The AWSEndpointProxy that allows access to the endpoint used by the connection.
     */
    private AWSConnectionProxy(AWSConnection connection, AWSEndpointProxy proxy)
    {
        _connection = connection;
        _endpointProxy = proxy;
    }

    public static AWSConnectionProxy create(ServiceProvider provider, List<Cloud> clouds, AWSEndpointProxy endpoint,
            List<Proxy> proxies, List<Property> settings) throws Exception
    {
        if (provider == null) {
            throw new ServiceProviderException("The cloud parameter cannot be null.");
        }
        if (endpoint == null) {
            throw new ServiceProviderException("The endpoint parameter cannot be null.");
        }
        Credential cred = AWSCredentialFactory.getInstance().getCredentials(provider, clouds);

        Proxy proxy = null;
        if (proxies != null && (!proxies.isEmpty())) {
            proxy = proxies.get(0);
        }

        AWSConnection connection =
                AWSConnectionFactory.getInstance().getConnection(settings, cred, proxy, endpoint.getEndpoint());
        return new AWSConnectionProxy(connection, endpoint);
    }

    /**
     * Creates an AWSConnectionProxy with an AWSConnection that can be used for EC2SecurityGroupOperations.
     * 
     * @param provider
     *            The service provider
     * @param clouds
     *            Associated clouds
     * @param proxies
     *            The proxy to be utilized. Optional - may be null.
     * @param settings
     *            The configuration settings for the connection. Optional - may be empty or null.
     * @return An AWSConnectionProxy with a connection that can be used for EC2SecurityGroupOperations.
     * @throws Exception
     */
    public static AWSConnectionProxy createSecurityGroupConnection(ServiceProvider provider, List<Cloud> clouds,
            List<Proxy> proxies, List<Property> settings) throws Exception
    {
        if (provider == null) {
            throw new Exception("The service provider cannot be null");
        }
        String AmazonURI = null;
        for (AssetProperty property : provider.getProperties()) {
            if (property.getName().equals(EC2Endpoint.CONFIG_ENDPOINT)) {
                AmazonURI = property.getStringValue();
                break;
            }
        }

        Proxy proxy = null;
        if (proxies != null && (!proxies.isEmpty())) {
            proxy = proxies.get(0);
        }
        Credential cred = AWSCredentialFactory.getInstance().getCredentials(provider, clouds);
        AWSConnection connection =
                AWSConnectionFactory.getInstance().getSecurityGroupConnection(settings, cred, proxy, AmazonURI);
        SecurityGroupEndpoint endpoint = new SecurityGroupEndpoint(connection.getEndpoint());
        return new AWSConnectionProxy(connection, endpoint);
    }

    /**
     * This method will build a common set of parameters used by all AWS calls
     * 
     * @param action
     *            - Amazon web service action to be performed
     * @return QueryParams - parameters required by all AWS calls
     */
    public QueryParams newQueryParams(String action)
    {
        QueryParams params = _connection.initQueryParams(action);
        return params;
    }

    /**
     * This method will make an asynchronous HTTP call using the provide callback.
     * 
     * @param params
     *            parameters required by the HTTP call
     * @param responseClass
     *            The type returned in the HTTP response
     * @return Promise<?> - future object that can be used to access the response
     */

    public <T> Promise<T> execute(final QueryParams params, final Class<T> responseClass)
    {
        if (logger.isTraceEnabled()) {
            logger.trace(params.toString());
        }
        return _connection.execute(params, responseClass);
    }

    /**
     * Returns the underlying AWSConnection
     */
    public AWSConnection getAWSConnection()
    {
        return _connection;
    }

    /**
     * Returns the associated AWSEndpointProxy
     */
    public AWSEndpointProxy getAWSEndpointProxy()
    {
        return _endpointProxy;
    }


    /**
     * Provides a ping operation for a connection
     */
    public Promise<IHttpResponse> ping()
    {
        QueryParams params = newQueryParams(ELBEndpoint.DESCRIBE_LOAD_BALANCERS);
        return execute(params, IHttpResponse.class);
    }
}
