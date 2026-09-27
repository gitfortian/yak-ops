# Built-in MySQL JDBC Drivers

This directory contains the fixed MySQL JDBC Driver binaries used by the Datasource isolated-driver runtime.

Layout:

```text
mysql/
├── 5/
│   └── mysql-connector-java-5.1.49.jar
└── 8/
    └── mysql-connector-j-8.4.0.jar
```

Runtime mapping:

- `MYSQL_5` → MySQL Connector/J `5.1.49`
- `MYSQL_8` → MySQL Connector/J `8.4.0`
- `AUTO` → `MYSQL_8`

Integrity:

```text
cf76d2e4c9c3782a85c15c87bec5772b34ffd0e5  mysql/5/mysql-connector-java-5.1.49.jar
b1bc0f47bcad26ad5f9bceefb63fcb920d868fca  mysql/8/mysql-connector-j-8.4.0.jar
```

The hashes above are the published Maven Central SHA-1 values for the exact artifacts.

Sources:

- `mysql:mysql-connector-java:5.1.49`
- `com.mysql:mysql-connector-j:8.4.0`

Licensing follows the license distributed with the corresponding MySQL Connector/J release. Keep version changes, checksums, runtime mapping and the bundled binary in the same PR.

The MySQL 8 JDBC path used by Yak Ops does not use X DevAPI, so `protobuf-java` is intentionally not bundled.
