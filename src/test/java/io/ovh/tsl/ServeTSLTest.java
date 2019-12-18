package io.ovh.tsl;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

public class ServeTSLTest {


    @Test
    public void testEmptyTSL() {
        given()
                .when().body("")
                .post("/api/v0/tsl" )
                .then()
                .statusCode(200)
                .body(is("[]"));
    }

    @Test
    public void testCreateTSL() {
        given()
                .when().body("create(series('1').setLabels([\"l0=42\",\"l1=42\"]).setValues(1575914640000000, [-5m, 2], [0, 1])"
                             + ".setValues(1575914640000000,[2m, 3]),series(\"test2\").setLabels([\"l0=40\",\"l2=41\"])"
                             + ".setValues(1575914640000000, [-5m, 2], [0, 1]))\n" +
                             "\t .sampleBy(2m, max)")
                .post("/api/v0/tsl" )
                .then()
                .statusCode(200)
                .body(is("[[" +
                         "{" +
                            "\"c\":\"1\",\"" +
                            "l\":{\"l0\":\"42\",\"l1\":\"42\"}," +
                            "\"a\":{},\"la\":0," +
                            "\"v\":[[1575914760000000,3],[1575914640000000,1],[1575914520000000,1]]}," +
                         "{" +
                            "\"c\":\"test2\"," +
                            "\"l\":{\"l0\":\"40\",\"l2\":\"41\"}," +
                            "\"a\":{},\"la\":0," +
                            "\"v\":[[1575914760000000,1],[1575914640000000,1],[1575914520000000,1]]" +
                         "}]]"));
    }
}
