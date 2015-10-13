/**
 *              Copyright (c) 2008-2013 ServiceMesh, Incorporated; All Rights Reserved
 *              Copyright (c) 2013-Present Computer Sciences Corporation
 */

package com.servicemesh.agility.adapters.service.azure.sql.util;

import java.util.regex.Pattern;

import com.servicemesh.agility.adapters.core.azure.util.AzureConstants;
import com.servicemesh.agility.adapters.core.azure.util.KeyValuePair;
import com.servicemesh.agility.adapters.service.azure.sql.AzureSQLConfig;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.azure.sql.models.Database;
import com.servicemesh.azure.sql.models.DatabaseList;
import com.servicemesh.azure.sql.models.FirewallRule;
import com.servicemesh.azure.sql.models.FirewallRuleList;
import com.servicemesh.azure.sql.models.Server;
import com.servicemesh.azure.sql.models.ServerList;
import com.servicemesh.azure.sql.models.ServerName;
import com.servicemesh.azure.sql.models.Servers;

public class AzureSQLConstants extends AzureConstants
{
    private static final long serialVersionUID = 20150121;

    /** Per Microsoft, Azure SQL version should be set to 2012-03-01 */
    public static final String AZURE_SQL_VERSION = "2012-03-01";
    public static final String AZURE_SQL_BASE_URI = "services/sqlservers/servers";
    public static final String AZURE_DOMAIN_SUFFIX = ".database.windows.net";

    public static final String INSTANCE_FIREWALL_SUFFIX = "-FirewallRule";
    public static final String SQL_PROP_PREFIX = "azure-sql-";

    public static final String URI_GENERIC_TAG = "?contentview=generic";

    public static final long UNDEFINED = -1;

    // this is required by Microsoft so we have marked our schemas with this namespace
    public static final String GENERIC_AZURE_NS = "http://schemas.microsoft.com/windowsazure";
    public static final String AZURE_201012_NS = "http://schemas.microsoft.com/sqlazure/2010/12/";
    public static final String SQLSERVER_NS = "Microsoft.SqlServer.Management.Framework.Web.Services";
    public static final String SCHEMA_NAMESPACE_2010_12 = "xmlns=\"" + AzureSQLConstants.AZURE_201012_NS + "\"";
    public static final String SCHEMA_GENERIC_AZURE_NAMESPACE = "xmlns=\"" + AzureSQLConstants.GENERIC_AZURE_NS + "\"";
    public static final String GENERIC_AZURE_CONTEXT = "com.microsoft.schemas.windowsazure"; // @see GENERIC_AZURE_NS
    public static final String AZURE_201012_CONTEXT = "com.servicemesh.azure.sql.models"; // @see AZURE_201012_NS
    public static final String SQLSERVER_CONTEXT = "services.web.framework.management.sqlserver.microsoft";
    public static final String GENERIC_ERROR_RESPONSE = "<Error xmlns=\"http://schemas.microsoft.com/windowsazure";
    public static final String SQLSERVER_ERROR_RESPONSE =
            "<Error xmlns=\"Microsoft.SqlServer.Management.Framework.Web.Services\"";

    public static final String MICROSOFT_RESOURCE_TYPE_BASE = "Microsoft.SqlAzure.";
    public static final String MICROSOFT_RESOURCE_TYPE_DATABASE = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE + "Database";
    public static final String MICROSOFT_RESOURCE_TYPE_SERVER = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE + "Server";
    public static final String MICROSOFT_RESOURCE_TYPE_QUOTA = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE + "ServerQuota";
    public static final String MICROSOFT_RESOURCE_TYPE_FIREWALL_RULE = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE
            + "FirewallRule";
    public static final String MICROSOFT_RESOURCE_TYPE_SERVICE_OBJECTIVE = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE
            + "ServiceObjective";
    public static final String MICROSOFT_RESOURCE_TYPE_DIMENSION_SETTING = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE
            + "DimensionSetting";
    public static final String MICROSOFT_RESOURCE_TYPE_RESTORE_DB_OPERATION = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE
            + "RestoreDatabaseOperation";
    public static final String MICROSOFT_RESOURCE_TYPE_DATABASE_COPY = AzureSQLConstants.MICROSOFT_RESOURCE_TYPE_BASE
            + "DatabaseCopy";

    public static final String DATABASE_LIST_NAME = DatabaseList.class.getName();
    public static final String SERVER_LIST_NAME = ServerList.class.getName();
    public static final String FIREWALL_RULE_LIST_NAME = FirewallRuleList.class.getName();
    public static final String SERVERS_NAME = Servers.class.getName();

    public static final long MB = 1048576;
    public static final long GB = 1073741824;

    // property key names
    public static final String PROP_DATABASE_NAME_STR = "databaseName";
    public static final String PROP_NEW_DATABASE_NAME_STR = "newDatabaseName";
    public static final String PROP_SERVER_NAME_STR = "serverName";
    public static final String PROP_FIREWALL_RULE_NAME_STR = "firewallRuleName";
    public static final String PROP_ADMIN_LOGIN_STR = "administratorLogin";
    public static final String PROP_ADMIN_PASSWORD_STR = "administratorLoginPassword";
    public static final String PROP_LOCATION_STR = "location";
    public static final String PROP_SELF_LINK_STR = "selfLink";
    public static final String PROP_PARENT_LINK_STR = "parentLink";
    public static final String PROP_PASSWORD_STR = "password";
    public static final String PROP_NEW_PASSWORD_STR = "newPassord";

    // property key names for object data
    public static final String PROP_DATABASE_LIST = DatabaseList.class.getName();
    public static final String PROP_DATABASE = Database.class.getName();
    public static final String PROP_FIREWALL_RULE_LIST = FirewallRuleList.class.getName();
    public static final String PROP_FIREWALL_RULE = FirewallRule.class.getName();
    public static final String PROP_SERVER_LIST = ServerList.class.getName();
    public static final String PROP_SERVER_NAME = ServerName.class.getName();
    public static final String PROP_SERVER = Server.class.getName();
    public static final String PROP_SERVICE_INSTANCE = ServiceInstance.class.getName();
    public static final String PROP_FIREWALL_RULE_ADD = "firewallRuleAddList"; // list of FirewallRule objects
    public static final String PROP_FIREWALL_RULE_DELETE = "firewallRuleDeleteList"; // list of FirewallRule objects
    public static final String PROP_FIREWALL_RULE_CHANGE = "firewallRuleChangeList"; // list of FirewallRule objects

    // method names used in MethodRequest calls
    public static final String METHOD_LIST_DATABASES = "listDatabases";
    public static final String METHOD_GET_DATABASE = "getDatabase";
    public static final String METHOD_LIST_FIREWALL_RULES = "listFirewallRules";
    public static final String METHOD_GET_FIREWALL_RULE = "getFirewallRule";
    public static final String METHOD_LIST_SERVERS = "listServers";
    public static final String METHOD_CREATE_SERVER = "createServer";
    public static final String METHOD_GET_SERVER = "getServer";
    public static final String METHOD_CHANGE_DATABASE_NAME = "changeDatabaseName";
    public static final String METHOD_PROCESS_DATABASE_CHANGE = "processDatabaseChange";
    public static final String METHOD_PROCESS_FIREWALL_RULE_CHANGE = "processFirewallRuleChange";
    public static final String METHOD_PROCESS_SERVER_CHANGE = "processServerChange";
    public static final String METHOD_PROCESS_HEALTH_CHECK = "processHealthCheck";
    public static final String METHOD_SERVER_SYNC = "serverSync";

    // Azure Server location constants
    public static final String LOCATION_EAST_US = "East US";

    // Password Validation Patterns
    public static final Pattern HAS_UPPER_CASE = Pattern.compile("[A-Z]");
    public static final Pattern HAS_LOWER_CASE = Pattern.compile("[a-z]");
    public static final Pattern HAS_NUMBER = Pattern.compile("\\d");
    public static final Pattern HAS_SPECIAL_CHAR = Pattern.compile("[^a-zA-Z0-9 ]");

    public static final String PROP_DEGRADED_REASON = "degraded-reason";
    public static final String DEFAULT_DEGRADED_REASON =
            "Something occurred during the operation that has caused the service to be marked as DEGRADED.  Check the log for details.";

    // Azure ServiceLevelObjectiveId values - https://msdn.microsoft.com/en-us/library/azure/dn505718.aspx
    // Azure Service Level Properties - https://msdn.microsoft.com/en-us/library/azure/dn741336.aspx
    // The key represents the edition and the value represents the service level objective ID
    public static final KeyValuePair SLO_BASIC = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_BASIC,
            "dd6d99bb-f193-4ec1-86f2-43d3bccbc49c");
    public static final KeyValuePair SLO_S0 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_STANDARD,
            "f1173c43-91bd-4aaa-973c-54e79e15235b");
    public static final KeyValuePair SLO_S1 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_STANDARD,
            "1b1ebd4d-d903-4baa-97f9-4ea675f5e928");
    public static final KeyValuePair SLO_S2 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_STANDARD,
            "455330e1-00cd-488b-b5fa-177c226f28b7");
    public static final KeyValuePair SLO_S3 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_STANDARD,
            "789681b8-ca10-4eb0-bdf2-e0b050601b40");
    public static final KeyValuePair SLO_P1 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_PREMIUM,
            "7203483a-c4fb-4304-9e9f-17c71c904f5d");
    public static final KeyValuePair SLO_P2 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_PREMIUM,
            "a7d1b92d-c987-4375-b54d-2b1d0e0f5bb0");
    public static final KeyValuePair SLO_P3 = new KeyValuePair(AzureSQLConfig.CONFIG_EDITION_PREMIUM,
            "a7c4c615-cfb1-464b-b252-925be0a19446");

    public static final long POLL_INTERVAL = 5000;
    public static final long POLL_RETRIES = 12;
    public static final String DB_EXPECTED_STATUS = "Normal";
}
