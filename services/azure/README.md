# Microsoft Azure Service Adapter Reference

The com.servicemesh.agility.adapters.core.azure bundle is provided to aid in the development of an Agility Platform&trade; adapter for a Microsoft Azure&trade; service. This bundle provides communications and utility functions for interacting with Microsoft Azure.

## Reference Implementations
Two complete reference implementations that utilize the Services SDK and the core.azure bundle are provided under ./reference:

* com.servicemesh.agility.adapters.service.azure.sql: an Agility Platform service adapter to the Microsoft Azure SQL&trade; service
* com.servicemesh.agility.adapters.service.azure.trafficmanager is an Agility Platform service adapter to the Microsoft Azure Traffic Manager&trade; service

Both reference implementations follow the best practices for naming, versioning, and packaging recommended in the [Agility Platform Services SDK Guide](https://github.com/csc/agility-platform-services-sdk-reference-info/blob/master/doc/AgilityPlatformServicesSDK.pdf).

**DISCLAIMER**: The reference implementations are **NOT** production ready and should not be utilized in a customer setting. They are suitable for building and deploying for local experimentation with blueprints and service life cycle.

Both reference implementations share the same ant targets:
```
bundle     Generates the OSGi bundle
ci         Continuous Integration target - builds RPM from scratch
clean      Deletes all generated artifacts
clean_rpm  Deletes all generated RPM artifacts.
compile    Compile all Java source files
help       Help
rpm-build  Generates the RPM
```
