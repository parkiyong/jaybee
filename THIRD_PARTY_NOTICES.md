# Third-Party Notices

Jaybee (`jaybee-api`, `jaybee-core`, `jaybee-jpdl`, and `jaybee-jbpm4-adapter`) is a clean-room implementation licensed under the MIT License (see `LICENSE`).

## Clean-Room Boundary & Native API

- Core modules (`jaybee-api`, `jaybee-core`, and `jaybee-jpdl`) have **zero runtime third-party dependencies** and expose Jaybee's own idiomatic Java 17 API (`io.github.parkiyong.jaybee.api.*`).
- `jaybee-jpdl` parses the jPDL 4.4 XML workflow schema (`http://jbpm.org/4.4/jpdl`) solely for data-format interoperability with existing workflow definitions.

## Optional External Compatibility Dependency (`jaybee-jbpm4-adapter`)

The optional `jaybee-jbpm4-adapter` module is the sole compatibility boundary implementing `org.jbpm.api.*` interfaces and compiles against the unmodified public API artifact:

- **Artifact**: `org.jbpm.jbpm4:jbpm-api:4.4`
- **License**: GNU Lesser General Public License, Version 2.1 (LGPL-2.1)
- **Dependency Scope**: `compileOnly` (and `testImplementation` for adapter verification tests)
- **Repository Content Filter**: Strictly restricted to `org.jbpm.jbpm4:jbpm-api` (no implementation modules are permitted).

Jaybee does **not** include, modify, copy source code from, or redistribute `jbpm-api` or any other LGPL-licensed library. Applications choosing to use `jaybee-jbpm4-adapter` supply the unmodified `jbpm-api-4.4.jar` at runtime under the terms of LGPL-2.1 Section 6 (dynamically linked work that uses the Library).
