# CSC Agility Platform Services SDK Reference Info

The Services Software Development Kit (SDK) is a capability offered in the CSC Agility Platform&trade; that provides an integration path to external platform and application cloud services. The Services SDK could be used, for example, to build service adapters for a cloud provider’s Database as a Service (DBaaS) or Load Balancer as a Service (LBaaS).

The SDK provides reference service adapters and public APIs that enable Java development of 3rd party cloud service adapters that can be delivered independently of core CSC Agility Platform&trade; development. A service adapter defines properties that characterize a given service, allowing Agility to administer the service instance.

A service adapter, built from the Services SDK, is an OSGi bundle comprised of Java source files and provides the following functions within the CSC Agility Platform:
* Creates the metadata that enables the supported service and service connections for use in a blueprint
* Creates the metadata that enables the definition of the service provider
* Creates service instances for deployed blueprints
* Manages lifecycle events for service provider, service instances, and the instances and service instances connected to a service instance.

## Subdirectories
* doc/: The CSC Agility Platform Services SDK Guide
* services/: Examples of utilizing the Services SDK and its supporting libraries.
* training/: Material from the Services SDK class, including a power point presentation and Eclipse projects for training exercises.

## Build Configuration

The examples in this repository are compatible with Java 8, Apache ant 1.9.3 and Apache Ivy.

The examples are dependent on the [csc-agility-platform-sdk project](https://github.com/csc/csc-agility-platform-sdk) and will download compiled versions of those projects using Ivy.

If you want to utilize Eclipse you'll need to install the IvyDE plugin for eclipse. 
Apache Ivy update site - http://www.apache.org/dist/ant/ivyde/updatesite


## License
The Services SDK and the reference code provided in this repository are licensed under the Apache License, Version 2.0. See [LICENSE](https://github.com/csc/csc-agility-platform-services-sdk-reference-info/blob/master/LICENSE) for the full license text.
