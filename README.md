# Agalon

The goal of Agalon is to be able to run [TSL](https://github.com/ovh/tsl) queries on multiple Time Series databases.

We choose to start with Influx, but Prometheus and OpenTSDB will come soon!

Agalon load in Memory the native raw data, then apply the TSL queries using [WarpScript™](https://warp10.io/doc/reference) as run time analysis!

The JAVA [quarkus](https://quarkus.io/) project was used to quickly bootstrap a REST API.

## Compile and test

You can build Agalon with

```sh
gradle quarkusBuild
```

And run it with

```sh
java -XX:TieredStopAtLevel=1 -Xverify:none -Djava.util.logging.manager=org.jboss.logmanager.LogManager -jar  build/agalon-0.0.1-SNAPSHOT-runner.jar 
```

Agalon is currently a PoC, improvement are coming! Any feedback or contribution are welcomed!

## TSL properties

Agalon require only a path to a TSL.so library and optionally the time units of the TSL query results.
To configure it, edit the `src/main/resources/application.properties` file.

```properties
#
# TSL SO library path
#
tsl.libso.path = /Path/to/tsl.so

#
# TSL time units of the query result
# ns means we store nanoseconds
# us means we store microseconds
# ms means we store milliseconds
#
# tsl.result.timeunits = us
```

## Influx properties

TSL is fully supported on an Influx DataBase. The Influx URL, the influx database as well as the output separtor format (between measurements and fields) are configurable. 

To configure an Influx source you can edit the `src/main/resources/application.properties` file.

```properties
#
# Influx Query URL
#
source.influx.query.url = http://127.0.0.1:8086

#
# Influx Query database
#
source.influx.query.database = telegraf

#
# Influx results separator (series names)
#
source.influx.result.separator = .
```

## TSL queries

Once TSL is well configured and running, it's now time to do some TSL queries. Let's start with one which will create series and sample them.

```cURL
curl --request POST \
  --url http://0.0.0.0:8080/api/v0/tsl \
  --data '
create(series('\''1'\'').setLabels(["l0=42","l1=42"]).setValues(1575914640000000, [-5m, 2], [0, 1]).setValues(1575914640000000,[2m, 3]),series("test2").setLabels(["l0=40","l2=41"]).setValues(1575914640000000, [-5m, 2], [0, 1]))
	 .sampleBy(2m, max)'
```

Then, as example let's run a find and a fetch queries on the Influx backend specified in the configuration.
At the moment a TSL basic authentication is used to connect to Influx (user, password). Even if no user are set on a local Influx for example, *a basic authentication* is currently *required*.
```cURL
# TSL Find on an Influx backend
curl --request POST \
  --url http://u:p@0.0.0.0:8080/api/v0/tsl \
  --data 'select("disk").where("mode=rw")'

# TSL Fetch on an Influx backend
curl --request POST \
  --url http://u:p@0.0.0.0:8080/api/v0/tsl \
  --data 'select("disk").where("mode=rw").last(20m).sampleBy(5m,max)'
```

If you want to learn more of the TSL syntax, you can check the [query doc](https://github.com/ovh/tsl/blob/master/spec/doc.md).

## License

Agalon is released under a [3-BSD clause license](./LICENSE).

## Get in touch

If you have any questions or want to share about TSL or Agalon, we will be happy to answer on our [gitter](https://gitter.im/ovh/tsl).
