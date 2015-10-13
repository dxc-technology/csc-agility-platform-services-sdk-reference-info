package com.servicemesh.agility.adapters.service.mock.operations;

import java.util.List;
import java.util.Properties;

import com.servicemesh.agility.adapters.service.mock.MockServiceConfig;
import com.servicemesh.agility.sdk.service.msgs.ConnectionRequest;
import com.servicemesh.agility.sdk.service.msgs.InstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceLifecycleRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.ConnectionOperations;
import com.servicemesh.agility.sdk.service.operations.InstanceOperations;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceLifecycleOperations;
import com.servicemesh.agility.sdk.service.operations.ServiceProviderOperations;
import com.servicemesh.agility.api.Asset;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.Instance;
import com.servicemesh.agility.api.Link;
import com.servicemesh.agility.api.Package;
import com.servicemesh.agility.api.Script;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.api.State;
import com.servicemesh.agility.api.Template;
import com.servicemesh.core.async.Promise;
import com.servicemesh.core.messaging.Response;
import com.servicemesh.core.messaging.Status;

import org.apache.log4j.Logger;

public class MockOperationsManager
{
    private static final Logger logger =
        Logger.getLogger(MockOperationsManager.class);

    private static Package _mockPackage;
    private static Script _mockScript;

    public static void setMockPackage(Package pkg)
    {
        _mockPackage = pkg;
    }

    public static Package getMockPackage()
    {
        return _mockPackage;
    }

    public static void setMockScript(Script script)
    {
        _mockScript = script;
    }

    public static Script getMockScript()
    {
        return _mockScript;
    }

    public static <T> Promise<T> execute(Object operator, String operation,
                                         ServiceProviderRequest request,
                                         Class<T> responseClass,
                                         Properties testProperties)
    {
        StringBuilder operationKey = new StringBuilder();
        if (InstanceOperations.class.isInstance(operator)) {
            operationKey.append("InstanceOperations.");
        }
        else if (ServiceInstanceOperations.class.isInstance(operator)) {
            operationKey.append("ServiceInstanceOperations.");
        }
        else if (ServiceInstanceLifecycleOperations.class.isInstance(operator)) {
            operationKey.append("ServiceInstanceLifecycleOperations.");
        }
        else if (ServiceProviderOperations.class.isInstance(operator)) {
            operationKey.append("ServiceProviderOperations.");
        }
        else if (ConnectionOperations.class.isInstance(operator)) {
            operationKey.append("ConnectionOperations.");
        }
        operationKey.append(operation);

        MockServiceConfig.OperationOutcome outcome =
            MockServiceConfig.getInstance()
            .getOperationOutcome(operationKey.toString(), testProperties);

        if (outcome.getExecutionTimeMillis() > 0) {
            try {
                Thread.sleep(outcome.getExecutionTimeMillis());
            }
            catch (Exception e) {}
        }
        if (outcome.isSuccessful()) {
            return buildSucceededResponse(responseClass,
                                          operationKey.toString());
        }
        else if (outcome.isDegraded()) {
            return buildDegradedResponse(request, responseClass,
                                         operationKey.toString());
        }
        return buildFailedResponse(responseClass, operationKey.toString());

    }

    private static <T> Promise<T> buildSucceededResponse(Class<T> responseClass,
                                                         String operationKey)
    {
        try {
            T response = createResponse(responseClass, operationKey);
            Response r = (Response)response;
            r.setStatus(Status.COMPLETE);

            if (logger.isDebugEnabled()) {
                logger.debug("Mock " + operationKey + " succeeded");
            }
            return Promise.pure(response);
        }
        catch (Exception e) {
            return Promise.pure(e);
        }
    }

    private static <T> Promise<T> buildDegradedResponse(ServiceProviderRequest request,
                                                        Class<T> responseClass,
                                                        String operationKey)
    {
        try {
            T response = createResponse(responseClass, operationKey);
            ServiceProviderResponse r = (ServiceProviderResponse)response;
            r.setStatus(Status.COMPLETE);
            String msg = "Mock " + operationKey + " degraded per configuration";
            setDegraded(request, r, operationKey, msg);
            if (logger.isDebugEnabled()) {
                logger.debug("Mock " + operationKey + " degraded");
            }
            return Promise.pure(response);
        }
        catch (Exception e) {
            return Promise.pure(e);
        }
    }

    private static <T> Promise<T> buildFailedResponse(Class<T> responseClass,
                                                      String operationKey)
    {
        String msg = "Mock " + operationKey + " failed per configuration";
        logger.debug(msg);
        return Promise.pure(new Exception(msg));
    }

    private static <T> T createResponse(Class<T> responseClass,
                                        String operationKey)
        throws Exception
    {
        StringBuilder failMsg = new StringBuilder();
        try {
            T response = responseClass.newInstance();
            if (! Response.class.isInstance(response)) {
                failMsg.append("Mock ").append(operationKey)
                    .append(": Invalid response class=")
                    .append(responseClass.getName());
            }
            return response;
        }
        catch (Exception e) {
            failMsg.append("Mock ").append(operationKey)
                .append("; Unable to instantiate ")
                .append(responseClass.getName())
                .append(" exc=").append(e.getMessage());
        }
        logger.error(failMsg.toString());
        throw new Exception(failMsg.toString());
    }

    private static void setDegraded(ServiceProviderRequest request,
                                    ServiceProviderResponse response,
                                    String operationKey,
                                    String degradeMsg)
        throws Exception
    {
        Asset asset = null;
        List<Asset> subAssets = null;
        boolean conditionalDegrade = false;
        if (request instanceof InstanceRequest)
            asset = ((InstanceRequest)request).getInstance();
        else if (request instanceof ServiceInstanceLifecycleRequest)
            asset = ((ServiceInstanceRequest)request).getDependentServiceInstance();
        else if (request instanceof ServiceInstanceRequest)
            asset = ((ServiceInstanceRequest)request).getServiceInstance();
        else if (request instanceof ConnectionRequest) {
            ConnectionRequest cr = (ConnectionRequest)request;
            asset = cr.getSource();
            subAssets = cr.getDependents();
            conditionalDegrade = true;
        }

        if (asset == null) {
            throw new Exception("Mock " + operationKey + " degrading failed." +
                                request.getClass().getName() +
                                " has no degradable asset");
        }
        if (asset instanceof Template) { // Degrade every instance
            if (subAssets != null) {
                Template template = (Template)asset;
                for (Link instanceLink : template.getInstances()) {
                    for (Asset subAsset : subAssets) {
                        if ((subAsset.getId() == instanceLink.getId()) &&
                            (subAsset instanceof Instance))
                            setDegraded(response, (Instance)subAsset,
                                        conditionalDegrade, degradeMsg);
                    }
                }
            }
        }
        else
            setDegraded(response, asset, conditionalDegrade, degradeMsg);
    }

    private static void setDegraded(ServiceProviderResponse response,
                                    Asset asset,
                                    boolean conditionalDegrade,
                                    String degradeMsg)
    {
        List<AssetProperty> properties = null;

        if (asset instanceof ServiceInstance) {
            ServiceInstance svcInstance = (ServiceInstance)asset;
            if ((! conditionalDegrade) || ((svcInstance.getState() != null) &&
                (svcInstance.getState() == ServiceState.RUNNING))) {
                svcInstance.setState(ServiceState.DEGRADED);
                properties = svcInstance.getConfigurations();
            }
        }
        else if (asset instanceof Instance) {
            Instance instance = (Instance)asset;
            if ((! conditionalDegrade) || ((instance.getState() != null) &&
               (instance.getState() == State.RUNNING))) {
                instance.setState(State.DEGRADED);
                properties = instance.getAssetProperties();
            }
        }

        if (properties != null) {
            AssetProperty prop = null;

            for (AssetProperty p : properties) {
                if ("degraded-reason".equals(p.getName())) {
                    prop = p;
                    break;
                }
            }
            if (prop == null) {
                prop = new AssetProperty();
                prop.setName("degraded-reason");
                properties.add(prop);
            }
            prop.setStringValue(degradeMsg);
            response.getModified().add(asset);
        }
    }
}
