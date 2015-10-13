/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */
package com.servicemesh.agility.adapters.service.azure.trafficmanager;

public class TrafficManagerConfig
{
    public static final String POLL_RETRIES = "AgilityManager.TrafficManager.PollRetries";
    public static final String POLL_INTERVAL = "AgilityManager.TrafficManager.PollInterval";

    public static final String HTTP_RETRIES = "AgilityManager.TrafficManager.HttpRetries";
    public static final String HTTP_TIMEOUT = "AgilityManager.TrafficManager.HttpTimeout";
    public static final String SOCKET_TIMEOUT = "AgilityManager.TrafficManager.SocketTimeout";

    public static final int POLL_RETRIES_DEFAULT = 30;
    public static final int POLL_INTERVAL_DEFAULT = 30;
    public static final int DNS_TTL_DEFAULT = 30;

    public static final int HTTP_RETRIES_DEFAULT = 2;
    public static final int HTTP_TIMEOUT_DEFAULT = 240;
    public static final int SOCKET_TIMEOUT_DEFAULT = 20;

    public static final int MONITOR_POLL_INTERVAL_DEFAULT = 30;
    public static final int MONITOR_POLL_TIMEOUT_DEFAULT = 10;
    public static final int MONITOR_POLL_RETRY_DEFAULT = 3;
    public static final String CONFIG_PROTOCOL_HTTP = "HTTP";
    public static final String CONFIG_PROTOCOL_HTTPS = "HTTPS";
    public static final String MONITOR_VERB_DEFAULT = "GET";
    public static final String MONITOR_RELATIVE_PATH_DEFAULT = "/";
    public static final int MONITOR_STATUS_CODE_DEFAULT = 200;
    public static final int MIN_CHILD_ENDPOINTS_DEFAULT = 1;
    public static final int WEIGHT_DEFAULT = 1;

    public static final String CONFIG_DNS_NAME = "dns";
    public static final String CONFIG_TTL_NAME = "time-to-live";
    public static final String CONFIG_LBM_NAME = "load-balancing-method";
    public static final String CONFIG_PROFILE_NAME = "profile-name";
    public static final String CONFIG_MONITOR_INTERVAL = "monitor-interval";
    public static final String CONFIG_MONITOR_TIMEOUT = "monitor-timeout";
    public static final String CONFIG_MONITOR_RETRIES = "monitor-retry";
    public static final String CONFIG_MONITOR_PROTOCOL = "monitor-protocol";
    public static final String CONFIG_MONITOR_PORT = "monitor-port";
    public static final String CONFIG_MONITOR_VERB = "monitor-verb";
    public static final String CONFIG_MONITOR_RELATIVE_PATH = "monitor-relative-path";
    public static final String CONFIG_MONITOR_STATUS_CODE = "monitor-status-code";
    public static final String CONFIG_MONITOR_STATUS = "monitor-status";
    public static final String CONFIG_MIN_CHILD_ENDPOINTS = "min-child-endpoint";
    public static final String CONFIG_WEIGHT = "weight";
    public static final String CONFIG_LOCATION = "location";
    public static final String CONFIG_ORDER = "order";

    public static final String ENDPOINT_PROP_LB_NAME = "Load Balancer";
    public static final String ENDPOINT_PROP_PROFILE_NAME = "Profile Name";
    public static final String POLICY_PROP_MONITOR_STATUS_NAME = "Monitor Status";
}
