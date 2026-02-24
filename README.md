# KNIME® Product

[![Jenkins](https://jenkins.knime.com/buildStatus/icon?job=knime-product%2Fmaster)](https://jenkins.knime.com/job/knime-product/job/master/)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=KNIME_knime-product&metric=alert_status&token=55129ac721eacd76417f57921368ed587ad8339d)](https://sonarcloud.io/summary/new_code?id=KNIME_knime-product)
This repository is maintained by the [KNIME Core Development Team](mailto:ap-core@knime.com).

### Content
This repository contains the source code of [KNIME Analytics Platform](http://www.knime.org). The code is organized as follows:

* _org.knime.product.*_: RCP (Rich Client Platform) definition

### Development
Instructions for how to develop extensions for KNIME Analytics Platform can be found in the _knime-sdk-setup_ repository on [GitHub](http://github.com/knime/knime-sdk-setup).

### Build a local minimal KNIME AP installation locally:

Run maven with the *local-build* profile enabled: `mvn verify -P local-build`.

### Join the Community!
* [KNIME Forum](https://tech.knime.org/forum)