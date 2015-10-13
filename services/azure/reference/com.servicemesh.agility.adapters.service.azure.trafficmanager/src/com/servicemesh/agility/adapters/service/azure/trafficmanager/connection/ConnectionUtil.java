/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager.connection;

import org.apache.log4j.Logger;

import com.microsoft.schemas.azure.trafficmgr.Error;

import com.servicemesh.agility.adapters.core.azure.AzureConnection;
import com.servicemesh.io.http.HttpStatus;
import com.servicemesh.io.http.IHttpResponse;

public class ConnectionUtil
{
    private static final Logger logger = Logger.getLogger(ConnectionUtil.class);

    /**
     * Derives textual status information from a HtttpResponse
     */
    public static String getStatusInfo(AzureConnection conn, IHttpResponse response)
    {
        StringBuilder sb = new StringBuilder();
        if (response.getStatusCode() != 200) {
            HttpStatus status = response.getStatus();
            if (status != null) {
                sb.append(status.getReason());
            }
            else {
                sb.append(response.getStatusCode());
            }
            try {
                Error error = conn.getEndpoint().decode(response, Error.class);
                sb.append(": ").append(error.getMessage());
            }
            catch (Exception e) {
                ConnectionUtil.logger.error("Unable to decode response: " + response.getContent());
            }
        }
        return sb.toString();
    }
}
