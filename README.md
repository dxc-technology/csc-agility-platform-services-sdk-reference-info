# agility-platform-services-sdk-reference-info

The Services Software Development Kit (SDK) is a new capability offered in CSC Agility Platform&trade; 10.0 that provides an integration path to external platform and application cloud services. The Services SDK could be used, for example, to build service adapters for a cloud provider’s Database as a Service (DBaaS) or Load Balancer as a Service (LBaaS).

The SDK provides reference service adapters and public APIs that enable Java development of 3rd party cloud service adapters that can be delivered independently of core Agility Platform&trade; development. A service adapter defines properties that characterize a given service, allowing Agility to administer the service instance.

A service adapter, built from the Services SDK, is an OSGi bundle comprised of Java source files and provides the following functions within the Agility Platform:
* Creates the metadata that enables the supported service and service connections for use in a blueprint
* Creates the metadata that enables the definition of the service provider
* Creates service instances for deployed blueprints
* Manages lifecycle events for service provider, service instances, and the instances and service instances connected to a service instance.

## Subdirectories
* doc/: The Agility Platform Services SDK Guide
* services/: Examples of utilizing the Services SDK and its supporting libraries.
* services.training/: A Eclipse project including build files to go along with the SDK Training power point.

## Build Configuration

The examples in this repository are compatible with Java 8 and ant 1.9.3.

The examples are dependent on the agility-platform-sdk project.

If you want to edit core.azure using Eclipse you'll need to define Eclipse build path variables:

* IVY-LIB: Contains the path to the ivy-lib directory under agility-platform-sdk
* DIST: Contains the path to the dist directory under agility-platform-sdk

## Licensing
The Services SDK and the reference code provided in this repository are licensed under the Apache License, Version 2.0. See [LICENSE](https://github.com/ServiceMesh/agility-platform-services-sdk-reference-info/blob/master/LICENSE) for the full license text.
