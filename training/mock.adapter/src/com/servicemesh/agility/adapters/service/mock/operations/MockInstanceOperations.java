package com.servicemesh.agility.adapters.service.mock.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.servicemesh.agility.adapters.service.mock.MockServiceConfig;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Connection;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.Package;
import com.servicemesh.agility.api.PropertyDefinition;
import com.servicemesh.agility.api.Script;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.State;
import com.servicemesh.agility.api.Template;
import com.servicemesh.agility.sdk.service.msgs.InstancePostBootRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePostStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreBootRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreRestartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStartRequest;
import com.servicemesh.agility.sdk.service.msgs.InstancePreStopRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceResponse;
import com.servicemesh.agility.sdk.service.operations.InstanceOperations;
import com.servicemesh.core.async.Promise;

import org.apache.log4j.Logger;

public class MockInstanceOperations extends InstanceOperations
{
     private static final Logger logger =
        Logger.getLogger(MockInstanceOperations.class);

    public MockInstanceOperations()
    {
    }

    @Override
    public Promise<InstanceResponse> preProvision(InstancePreProvisionRequest request)
    {
        return execute("preProvision", request);
    }

    @Override
    public Promise<InstanceResponse> postProvision(InstancePostProvisionRequest request)
    {
        Promise<InstanceResponse> response = execute("postProvision", request);

        if (! response.isFailed()) {
            Instance instance = request.getInstance();
            if (instance != null) {
                List<Asset> modified = new ArrayList<Asset>();

                if (MockServiceConfig.getInstance().isPackageAssignEnabled()) {
                    // Assign the package now so that its startup script gets
                    // executed
                    Template template = request.getTemplate();
                    if (assignPackage(template))
                        modified.add(template);
                }

                Script mockScript = MockOperationsManager.getMockScript();
                if (mockScript != null) {
                    if (setInstanceVariables(instance,
                                             request.getServiceInstance(),
                                             mockScript.getVariables()))
                        modified.add(instance);
                }
                if (! modified.isEmpty()) {
                    try {
                        InstanceResponse ir = response.get();
                        for (Asset asset : modified)
                            ir.getModified().add(asset);
                    }
                    catch (Throwable t) {}
                }
            }
        }
        return response;
    }

    @Override
    public Promise<InstanceResponse> preBoot(InstancePreBootRequest request)
    {
        return execute("preBoot", request);
    }

    @Override
    public Promise<InstanceResponse> postBoot(InstancePostBootRequest request)
    {
        return execute("postBoot", request);
    }

    @Override
    public Promise<InstanceResponse> preStop(final InstancePreStopRequest request)
    {
        return execute("preStop", request);
    }

    @Override
    public Promise<InstanceResponse> postStop(final InstancePostStopRequest request)
    {
        return execute("postStop", request);
    }

    @Override
    public Promise<InstanceResponse> preStart(final InstancePreStartRequest request)
    {
        return execute("preStart", request);
    }

    @Override
    public Promise<InstanceResponse> postStart(final InstancePostStartRequest request)
    {
        return execute("postStart", request);
    }

    @Override
    public Promise<InstanceResponse> preRestart(InstancePreRestartRequest request)
    {
        return execute("preRestart", request);
    }

    @Override
    public Promise<InstanceResponse> postRestart(InstancePostRestartRequest request)
    {
        return execute("postRestart", request);
    }

    @Override
    public Promise<InstanceResponse> preRelease(InstancePreReleaseRequest request)
    {
        return execute("preRelease", request);
    }

    @Override
    public Promise<InstanceResponse> postRelease(InstancePostReleaseRequest request)
    {
        return execute("postRelease", request);
    }

    @Override
    public Promise<InstanceResponse> preReconfigure(final InstancePreReconfigureRequest request)
    {
        return execute("preReconfigure", request);
    }

    @Override
    public Promise<InstanceResponse> postReconfigure(final InstancePostReconfigureRequest request)
    {
        return execute("postReconfigure", request);
    }

    private Promise<InstanceResponse> execute(String operation,
                                              InstanceRequest request)
    {
        ServiceInstance dstInstance = request.getServiceInstance();
        Instance srcInstance = request.getInstance();
        Properties props = null;
        StringBuilder sb = (logger.isDebugEnabled()) ?
            new StringBuilder(operation) : null;

        if (dstInstance != null) {
            if (sb != null) {
                ServiceState state = dstInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                sb.append(" { ServiceInstanceId=").append(dstInstance.getId())
                    .append(", name=").append(dstInstance.getName())
                    .append(", state=").append(strState).append(" }");
            }
        }

        if (srcInstance != null) {
            if (sb != null) {
                State state = srcInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                sb.append(" { Instance id=").append(srcInstance.getId())
                    .append(", name=").append(srcInstance.getName())
                    .append(", state=").append(strState).append(" }");
            }

            Link template = srcInstance.getTemplate();
            if (template != null) {
                for (Connection conn : request.getDestConnections()) {
                    Link src = conn.getSource();
                    if ((src != null) && (src.getId() == template.getId()) &&
                    (src.getHref() != null) &&
                    (src.getHref().startsWith("template"))) {
                        props = MockServiceConfig.getTestProperties(conn);
                        break;
                    }
                }
            }
        }
        if (sb != null) {
            logger.debug(sb.toString());
        }
        return MockOperationsManager.execute(this, operation, request,
                                             InstanceResponse.class, props);
    }

    private boolean assignPackage(Template template)
    {
        boolean modified = false;
        Package mockPackage = MockOperationsManager.getMockPackage();

        if ((template != null) && (mockPackage != null)) {
            Link mockPkg = null;

            for (Link pkg : template.getPackages()) {
                if (mockPackage.getName().equals(pkg.getName())) {
                    mockPkg = pkg;
                    break;
                }
            }
            if (mockPkg == null) {
                mockPkg = new Link();
                mockPkg.setName(mockPackage.getName());
                mockPkg.setType("application/" + Template.class.getName() + "+xml");
                mockPkg.setId(mockPackage.getId());
                template.getPackages().add(mockPkg);
                modified = true;
            }
            else if (mockPackage.getId() != mockPkg.getId()) {
                mockPkg.setId(mockPackage.getId());
                modified = true;
            }
        }
        return modified;
    }

    private boolean setInstanceVariables(Instance instance,
                                         ServiceInstance svcInstance,
                                         List<PropertyDefinition> variableDefs)
    {
        boolean modified = false;
        String svcHostname = getServiceHostname(svcInstance);

        if (svcHostname != null) {
            for (PropertyDefinition variableDef : variableDefs) {
                AssetProperty variable = null;

                for (AssetProperty iVar : instance.getVariables()) {
                    if (variableDef.getName().equals(iVar.getName())) {
                        variable = iVar;
                        break;
                    }
                }
                if (variable == null) {
                    variable = new AssetProperty();
                    variable.setName(variableDef.getName());
                    variable.setStringValue(svcHostname);
                    instance.getVariables().add(variable);
                    modified = true;
                }
                else if (! svcHostname.equals(variable.getStringValue())) {
                    variable.setStringValue(svcHostname);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private String getServiceHostname(ServiceInstance svcInstance)
    {
        String hostName = null;
        if (svcInstance != null) {
            List<AssetProperty> configs = svcInstance.getConfigurations();
            if (! configs.isEmpty()) {
                hostName = configs.get(0).getStringValue();
            }
        }
        return hostName;
    }
}
