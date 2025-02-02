/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.serde.connect;

import static io.confluent.ksql.serde.connect.ConnectKsqlSchemaTranslator.OPTIONAL_DATE_SCHEMA;
import static io.confluent.ksql.serde.connect.ConnectKsqlSchemaTranslator.OPTIONAL_TIMESTAMP_SCHEMA;
import static io.confluent.ksql.serde.connect.ConnectKsqlSchemaTranslator.OPTIONAL_TIME_SCHEMA;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.junit.Test;


public class ConnectKsqlSchemaTranslatorTest {

  @Test
  public void shouldTranslatePrimitives() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field("intField", Schema.INT32_SCHEMA)
        .field("longField", Schema.INT64_SCHEMA)
        .field("doubleField", Schema.FLOAT64_SCHEMA)
        .field("stringField", Schema.STRING_SCHEMA)
        .field("booleanField", Schema.BOOLEAN_SCHEMA)
        .field("bytesField", Schema.BYTES_SCHEMA)
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);
    assertThat(ksqlSchema.schema().type(), equalTo(Schema.Type.STRUCT));
    assertThat(ksqlSchema.fields().size(), equalTo(connectSchema.fields().size()));
    for (int i = 0; i < ksqlSchema.fields().size(); i++) {
      assertThat(
          ksqlSchema.fields().get(i).name(),
          equalTo(connectSchema.fields().get(i).name().toUpperCase()));
      assertThat(
          ksqlSchema.fields().get(i).schema().type(),
          equalTo(connectSchema.fields().get(i).schema().type()));
      assertThat(ksqlSchema.fields().get(i).schema().isOptional(), is(true));
    }
    // Make sure that regular int32/int64 fields do not get converted to date/time/timestamp
    assertThat(ksqlSchema.field("LONGFIELD").schema(), is(Schema.OPTIONAL_INT64_SCHEMA));
    assertThat(ksqlSchema.field("INTFIELD").schema(), is(Schema.OPTIONAL_INT32_SCHEMA));
  }

  @Test
  public void shouldTranslateMaps() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field("mapField", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA))
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);
    assertThat(ksqlSchema.field("MAPFIELD"), notNullValue());
    final Schema mapSchema = ksqlSchema.field("MAPFIELD").schema();
    assertThat(mapSchema.type(), equalTo(Schema.Type.MAP));
    assertThat(mapSchema.keySchema(), equalTo(Schema.OPTIONAL_STRING_SCHEMA));
    assertThat(mapSchema.valueSchema(), equalTo(Schema.OPTIONAL_INT32_SCHEMA));
  }

  @Test
  public void shouldTranslateStructInsideMap() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field(
            "mapField",
            SchemaBuilder.map(
                Schema.STRING_SCHEMA,
                SchemaBuilder.struct()
                    .field("innerIntField", Schema.INT32_SCHEMA)
                    .build()))
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);
    assertThat(ksqlSchema.field("MAPFIELD"), notNullValue());
    final Schema mapSchema = ksqlSchema.field("MAPFIELD").schema();
    assertThat(mapSchema.type(), equalTo(Schema.Type.MAP));
    assertThat(mapSchema.isOptional(), is(true));
    assertThat(mapSchema.keySchema(), equalTo(Schema.OPTIONAL_STRING_SCHEMA));
    assertThat(mapSchema.valueSchema().type(), equalTo(Schema.Type.STRUCT));
    assertThat(mapSchema.valueSchema().fields().size(), equalTo(1));
    assertThat(mapSchema.valueSchema().fields().get(0).name(), equalTo("INNERINTFIELD"));
    assertThat(mapSchema.valueSchema().fields().get(0).schema(), equalTo(Schema.OPTIONAL_INT32_SCHEMA));
  }

  @Test
  public void shouldTranslateArray() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field("arrayField", SchemaBuilder.array(Schema.INT32_SCHEMA))
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);
    assertThat(ksqlSchema.field("ARRAYFIELD"), notNullValue());
    final Schema arraySchema = ksqlSchema.field("ARRAYFIELD").schema();
    assertThat(arraySchema.type(), equalTo(Schema.Type.ARRAY));
    assertThat(arraySchema.isOptional(), is(true));
    assertThat(arraySchema.valueSchema(), equalTo(Schema.OPTIONAL_INT32_SCHEMA));
  }

  @Test
  public void shouldTranslateStructInsideArray() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field(
            "arrayField",
            SchemaBuilder.array(
                SchemaBuilder.struct()
                    .field("innerIntField", Schema.OPTIONAL_INT32_SCHEMA)
                    .build()))
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);
    assertThat(ksqlSchema.field("ARRAYFIELD"), notNullValue());
    final Schema arraySchema = ksqlSchema.field("ARRAYFIELD").schema();
    assertThat(arraySchema.type(), equalTo(Schema.Type.ARRAY));
    assertThat(arraySchema.valueSchema().type(), equalTo(Schema.Type.STRUCT));
    assertThat(arraySchema.valueSchema().fields().size(), equalTo(1));
    assertThat(arraySchema.valueSchema().fields().get(0).name(), equalTo("INNERINTFIELD"));
    assertThat(arraySchema.valueSchema().fields().get(0).schema(), equalTo(Schema.OPTIONAL_INT32_SCHEMA));
  }

  @Test
  public void shouldTranslateNested() {
    final Schema connectInnerSchema = SchemaBuilder
        .struct()
        .field("intField", Schema.INT32_SCHEMA)
        .build();
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field("structField", connectInnerSchema)
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);
    assertThat(ksqlSchema.field("STRUCTFIELD"), notNullValue());
    final Schema innerSchema = ksqlSchema.field("STRUCTFIELD").schema();
    assertThat(innerSchema.fields().size(), equalTo(connectInnerSchema.fields().size()));
    for (int i = 0; i < connectInnerSchema.fields().size(); i++) {
      assertThat(
          innerSchema.fields().get(i).name().toUpperCase(),
          equalTo(connectInnerSchema.fields().get(i).name().toUpperCase()));
      assertThat(
          innerSchema.fields().get(i).schema().type(),
          equalTo(connectInnerSchema.fields().get(i).schema().type()));
      assertThat(innerSchema.fields().get(i).schema().isOptional(), is(true));
    }
  }

  @Test
  public void shouldTranslateMapWithNonStringKey() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field("mapfield", SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.INT32_SCHEMA))
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);

    assertThat(ksqlSchema.field("MAPFIELD"), notNullValue());
    final Schema mapSchema = ksqlSchema.field("MAPFIELD").schema();
    assertThat(mapSchema.type(), equalTo(Schema.Type.MAP));
    assertThat(mapSchema.keySchema(), equalTo(Schema.OPTIONAL_STRING_SCHEMA));
    assertThat(mapSchema.valueSchema(), equalTo(Schema.OPTIONAL_INT32_SCHEMA));
  }

  @Test
  public void shouldTranslateTimeTypes() {
    final Schema connectSchema = SchemaBuilder
        .struct()
        .field("timefield", Time.SCHEMA)
        .field("datefield", Date.SCHEMA)
        .field("timestampfield", Timestamp.SCHEMA)
        .build();

    final Schema ksqlSchema = new ConnectKsqlSchemaTranslator().toKsqlSchema(connectSchema);

    assertThat(ksqlSchema.field("TIMEFIELD").schema(), equalTo(OPTIONAL_TIME_SCHEMA));
    assertThat(ksqlSchema.field("DATEFIELD").schema(), equalTo(OPTIONAL_DATE_SCHEMA));
    assertThat(ksqlSchema.field("TIMESTAMPFIELD").schema(), equalTo(OPTIONAL_TIMESTAMP_SCHEMA));
  }
}
