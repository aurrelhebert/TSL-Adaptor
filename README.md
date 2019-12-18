# Agalon

The goal of Agalon is to be able to run [TSL](https://github.com/ovh/tsl) queries on multiple Time Series databases.

We choose to start with Influx, but Prometheus and OpenTSDB will come soon!

Agalon load in Memory the native raw data, then apply the TSL queries using WarpScript as run time analysis!

The JAVA [quarkus](https://quarkus.io/) project was used to quickly bootstrap te REST API

## Compile and test

You can build Agalon with

```sh
gradle quarkusBuild
```

And run it with

```sh
java -XX:TieredStopAtLevel=1 -Xverify:none -Djava.util.logging.manager=org.jboss.logmanager.LogManager -jar  build/agalon-0.0.1-SNAPSHOT-runner.jar 
```

Agalon is currently a PoC, improvement are coming! Any contribution will be welcomed

## 

