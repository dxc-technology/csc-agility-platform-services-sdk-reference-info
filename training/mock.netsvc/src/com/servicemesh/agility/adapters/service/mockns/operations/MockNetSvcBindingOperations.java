package com.servicemesh.agility.adapters.service.mockns.operations;

import com.servicemesh.agility.sdk.service.operations.ServiceInstanceOperations;

/*
 * A Service Instance for a service adapter implementing a pure Agility
 * Platform network service does not need to implement any methods. Instead,
 * a Service Instance will be the binding mechanism between a VM instance
 * and an instance of a Service Provider that is associated with the VM
 * instance's network
 */
public class MockNetSvcBindingOperations extends ServiceInstanceOperations
{
    public MockNetSvcBindingOperations() {}
}
