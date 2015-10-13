/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql;

import com.servicemesh.agility.adapters.core.azure.Config;
import com.servicemesh.agility.sdk.service.spi.Constants;

public class AzureSQLConfig extends Config
{
    public static final String CONFIG_ADMIN_LOGIN = "admin-login";
    public static final String CONFIG_ADMIN_PASS = "admin-pass";
    public static final String CONFIG_AZURE_SQL_SERVER_LOCATION = "location";
    public static final String CONFIG_LOCATION_EAST_ASIA = "East Asia";
    public static final String CONFIG_LOCATION_SOUTHEAST_ASIA = "Southeast Asia";
    public static final String CONFIG_LOCATION_JAPAN_WEST = "Japan West";
    public static final String CONFIG_LOCATION_JAPAN_EAST = "Japan East";
    public static final String CONFIG_LOCATION_CENTRAL_US = "Central US";
    public static final String CONFIG_LOCATION_NORTH_CENTRAL_US = "North Central US";
    public static final String CONFIG_LOCATION_SOUTH_CENTRAL_US = "South Central US";
    public static final String CONFIG_LOCATION_WEST_US = "West US";
    public static final String CONFIG_LOCATION_EAST_US = "East US";
    public static final String CONFIG_LOCATION_EAST_US_2 = "East US 2";
    public static final String CONFIG_LOCATION_NORTH_EUROPE = "North Europe";
    public static final String CONFIG_LOCATION_WEST_EUROPE = "West Europe";
    public static final String CONFIG_LOCATION_BRAZIL_SOUTH = "Brazil South";
    public static final String CONFIG_FIREWALL_RULE_NAME = "firewall-rule-name";
    public static final String CONFIG_STARTING_ALLOWED_IP = "starting-allowed-ip";
    public static final String CONFIG_ENDING_ALLOWED_IP = "ending-allowed-ip";
    public static final String CONFIG_DB_NAME = "db-name";
    public static final String CONFIG_EDITION = "edition";
    public static final String CONFIG_EDITION_BASIC = "Basic";
    public static final String CONFIG_EDITION_STANDARD = "Standard";
    public static final String CONFIG_EDITION_PREMIUM = "Premium";
    public static final String CONFIG_COLLATION = "collation";
    public static final String CONFIG_MAX_SIZE = "max-size";
    public static final String CONFIG_100_MB = "100 MB";
    public static final String CONFIG_500_MB = "500 MB";
    public static final String CONFIG_1_GB = "1 GB";
    public static final String CONFIG_2_GB = "2 GB";
    public static final String CONFIG_5_GB = "5 GB";
    public static final String CONFIG_10_GB = "10 GB";
    public static final String CONFIG_20_GB = "20 GB";
    public static final String CONFIG_30_GB = "30 GB";
    public static final String CONFIG_40_GB = "40 GB";
    public static final String CONFIG_50_GB = "50 GB";
    public static final String CONFIG_100_GB = "100 GB";
    public static final String CONFIG_150_GB = "150 GB";
    public static final String CONFIG_200_GB = "200 GB";
    public static final String CONFIG_250_GB = "250 GB";
    public static final String CONFIG_300_GB = "300 GB";
    public static final String CONFIG_400_GB = "400 GB";
    public static final String CONFIG_500_GB = "500 GB";
    public static final String CONFIG_SERVICE_OBJECTIVE_ID = "service-objective-id";
    public static final String CONFIG_SERVER_NAME = "server-name";
    public static final String CONFIG_SERVER_DOMAIN_NAME = Constants.FQ_DOMAIN_NAME;
    public static final String CONFIG_WORKLOAD_CONN = "workload-azure-sql";
    public static final String CONFIG_SERVICE_SQL_CONN = "service-azure-sql";
    public static final String CONFIG_SQL_SERVICE_CONN = "azure-sql-service";
}