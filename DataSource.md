# TSL-Adaptor Data Sources

## Influx

Influx data source is now supported by TSL-Adaptor, with TSL-Adaptor as a proxy in front of Influx.

## Create an TSL-Adaptor Data Source

Your custom Data-Source should extends the Abstract class of LOADSOURCERAW.
This require implementing the "LoadGTS" method containing the query basic auth information or soon a token, the select selectors, the selected tags and the start and end timestamp of the user query.
You can add some relative properties of your data-source, theyr should all be prefixed by the "source" namespace and the data source name.

## Configuration

Check the resources/application.properties file. The only requirement is the [tsl.so library](https://github.com/ovh/tsl/releases).
