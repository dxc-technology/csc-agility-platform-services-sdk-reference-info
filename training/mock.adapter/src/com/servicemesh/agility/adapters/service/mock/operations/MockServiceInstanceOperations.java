package com.servicemesh.agility.adapters.service.mock.operations;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.servicemesh.agility.adapters.service.mock.MockServiceConfig;
import com.servicemesh.agility.api.AssetProperty;
import com.servicemesh.agility.api.LaunchItemDeployment;
import com.servicemesh.agility.api.ServiceInstance;
import com.servicemesh.agility.api.ServiceState;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceProvisionRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReconfigureRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceReleaseRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStartRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceStopRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceInstanceValidateRequest;
import com.servicemesh.agility.sdk.service.msgs.ServiceProviderResponse;
import com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations;
import com.servicemesh.agility.sdk.service.spi.Constants;
import com.servicemesh.core.async.Promise;

import org.apache.log4j.Logger;

public class MockServiceInstanceOperations extends ServiceInstanceOperations
{
    private static final Logger logger =
        Logger.getLogger(MockServiceInstanceOperations.class);
    private static int provisionCount = 0;

    public MockServiceInstanceOperations()
    {
    }

    @Override
    public Promise<ServiceProviderResponse> validate(final ServiceInstanceValidateRequest request)
    {
        return execute("validate", request);
    }

    @Override
    public Promise<ServiceProviderResponse> provision(final ServiceInstanceProvisionRequest request)
    {
        Promise<ServiceProviderResponse> response =
            execute("provision", request);

        if (! response.isFailed()) {
            provisionCount++;
            AssetProperty prop = new AssetProperty();
            prop.setName(Constants.FQ_DOMAIN_NAME);
            prop.setStringValue("mock" + provisionCount + ".service.com");
            ServiceInstance svcInstance = request.getServiceInstance();
            svcInstance.getConfigurations().add(prop);
            svcInstance.setInstanceId("Mock Service Instance ID");

            applyLaunchItem(svcInstance.getAssetProperties(),
                            request.getLaunchItemDeployment());

            try {
                response.get().getModified().add(svcInstance);
            }
            catch (Throwable t) {}
        }
        return response;
    }

    private void applyLaunchItem(List<AssetProperty> assetProps,
                                 LaunchItemDeployment lid)
    {
        AssetProperty liap = null;
        if (lid != null) {
            // Launch item property takes precedence if defined
            liap = findProperty(lid.getAssetProperties(),
                                MockServiceConfig.CONFIG_SERVICE_TEST_PROPS);
        }
        if (liap != null) {
            AssetProperty ap = findProperty(assetProps, liap.getName());
            if (ap == null) {
                ap = new AssetProperty();
                ap.setName(liap.getName());
                assetProps.add(ap);
            }
            ap.setStringValue(liap.getStringValue());
        }
    }

    private AssetProperty findProperty(List<AssetProperty> assetProps,
                                       String propertyName)
    {
        for (AssetProperty ap : assetProps) {
            if (propertyName.equals(ap.getName()))
                return ap;
        }
        return null;
    }

    @Override
    public Promise<ServiceProviderResponse> reconfigure(final ServiceInstanceReconfigureRequest request)
    {
        return execute("reconfigure", request);
    }

    @Override
    public Promise<ServiceProviderResponse> start(ServiceInstanceStartRequest request)
    {
        return execute("start", request);
    }

    @Override
    public Promise<ServiceProviderResponse> stop(ServiceInstanceStopRequest request)
    {
        return execute("stop", request);
    }

    @Override
    public Promise<ServiceProviderResponse> release(final ServiceInstanceReleaseRequest request)
    {
        Promise<ServiceProviderResponse> response =
            execute("release", request);

        if (! response.isFailed()) {
            ServiceInstance svcInstance = request.getServiceInstance();
            Iterator<AssetProperty> iter =
                svcInstance.getConfigurations().listIterator();

            while (iter.hasNext()) {
                AssetProperty prop = iter.next();
                if (Constants.FQ_DOMAIN_NAME.equals(prop.getName()))
                    iter.remove();
            }
            try {
                response.get().getModified().add(svcInstance);
            }
            catch (Throwable t) {}
        }
        return response;
    }

    private Promise<ServiceProviderResponse> execute(String operation,
                                                     ServiceInstanceRequest request)
    {
        Properties props = null;
        ServiceInstance svcInstance = request.getServiceInstance();
        if (svcInstance != null) {
            props = MockServiceConfig.getTestProperties(svcInstance);

            if (logger.isDebugEnabled()) {
                ServiceState state = svcInstance.getState();
                String strState = (state != null) ? state.value() : "N/A";
                logger.debug(operation + " ServiceInstance id=" +
                             svcInstance.getId() + ", name=" +
                             svcInstance.getName() + ", state=" + strState);
            }
        }
        return MockOperationsManager.execute(this, operation, request,
                                             ServiceProviderResponse.class,
                                             props);
    }
}
