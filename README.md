![Travis CI Status](https://travis-ci.org/cloudendpoints/endpoints-management-java.svg?branch=master)
![codecov.io](http://codecov.io/github/cloudendpoints/endpoints-management-java/coverage.svg?branch=master)

Google Endpoints API Management
===============================

Google Endpoints API Management manages the 'control plane' of an API by providing support for authentication, billing, monitoring and quota control.

It achieves this by

- allowing HTTP servers to control access to their APIs using the Google Service Management and Google Service Control APIs
- providing built-in, standards-compliant support for third-party authentication
- doing this with minimal performance impact via the use of advanced caching and aggregation algorithms
- making this easy to integrate via a servlet filters

The main documents for consuming Endpoints can be found at
https://cloud.google.com/endpoints/docs/frameworks/java

## Build package

    git submodule init && git pull --recurse-submodules=yes
    ./gradlew build

## Installing to local maven

To install test versions to Maven for easier dependency management, simply run:

    gradle install
    
Java Versions
-------------

Java 7 or above is required for using this library.

Contributing
------------

Contributions to this library are always welcome and highly encouraged.

See the [CONTRIBUTING] documentation for more information on how to get started.

Versioning
----------

This library follows [Semantic Versioning](http://semver.org/).


Repository Structure
--------------------

This repository provides several artifacts, all in the `com.google.endpoints` group:

1.  `endpoints-management-auth`: Enables authentication by multiple authentication providers
2.  `endpoints-control-api-client`: A basic client library for accessing the service control API
3.  `endpoints-control-appengine`: Provides a servlet filter that simplifies integrating service management on Google App Engine
4.  `endpoints-control`: Provide access control for managed services
5.  `endpoints-framework-auth`: Enables use of endpoints-management-auth with endpoints-framework
6.  `endpoints-service-config`: Handles service configuration via the service management API

License
-------

Apache - See [LICENSE] for more information.

[CONTRIBUTING]:https://github.com/googleapis/endpoints-service-control-java/blob/master/CONTRIBUTING.md
[LICENSE]: https://github.com/googleapis/endpoints-service-control-java/blob/master/LICENSE
