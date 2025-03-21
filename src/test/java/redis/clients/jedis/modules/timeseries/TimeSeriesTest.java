package redis.clients.jedis.modules.timeseries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.modules.RedisModuleCommandsTestBase;
import redis.clients.jedis.timeseries.*;

public class TimeSeriesTest extends RedisModuleCommandsTestBase {

  @BeforeClass
  public static void prepare() {
    RedisModuleCommandsTestBase.prepare();
  }

  @AfterClass
  public static void tearDown() {
//    RedisModuleCommandsTestBase.tearDown();
  }

  @Test
  public void testCreate() {
    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");

    assertEquals("OK", client.tsCreate("series1", TSCreateParams.createParams().retention(10).labels(labels)));
    assertEquals("TSDB-TYPE", client.type("series1"));

    assertEquals("OK", client.tsCreate("series2", TSCreateParams.createParams().labels(labels)));
    assertEquals("TSDB-TYPE", client.type("series2"));

    assertEquals("OK", client.tsCreate("series3", TSCreateParams.createParams().retention(10)));
    assertEquals("TSDB-TYPE", client.type("series3"));

    assertEquals("OK", client.tsCreate("series4"));
    assertEquals("TSDB-TYPE", client.type("series4"));

    assertEquals("OK", client.tsCreate("series5", TSCreateParams.createParams().retention(0).uncompressed().labels(labels)));
    assertEquals("TSDB-TYPE", client.type("series5"));
    assertEquals("OK", client.tsCreate("series6", TSCreateParams.createParams().retention(7898)
        .uncompressed().duplicatePolicy(DuplicatePolicy.MAX).labels(labels)));
    assertEquals("TSDB-TYPE", client.type("series6"));

    try {
      assertEquals("OK", client.tsCreate("series1", TSCreateParams.createParams().retention(10).labels(labels)));
      fail();
    } catch (JedisDataException e) {
    }

    try {
      assertEquals("OK", client.tsCreate("series1", TSCreateParams.createParams().labels(labels)));
      fail();
    } catch (JedisDataException e) {
    }

    try {
      assertEquals("OK", client.tsCreate("series1", TSCreateParams.createParams().retention(10)));
      fail();
    } catch (JedisDataException e) {
    }

    try {
      assertEquals("OK", client.tsCreate("series1"));
      fail();
    } catch (JedisDataException e) {
    }

    try {
      assertEquals("OK", client.tsCreate("series1"));
      fail();
    } catch (JedisDataException e) {
    }

    try {
      assertEquals("OK", client.tsCreate("series7", TSCreateParams.createParams().retention(7898)
          .uncompressed().chunkSize(-10).duplicatePolicy(DuplicatePolicy.MAX).labels(labels)));
      fail();
    } catch (JedisDataException e) {
    }
  }

  @Test
  public void testRule() {
    assertEquals("OK", client.tsCreate("source"));
    assertEquals("OK", client.tsCreate("dest", TSCreateParams.createParams().retention(10)));

    assertEquals("OK", client.tsCreateRule("source", "dest", AggregationType.AVG, 100));

    try {
      client.tsCreateRule("source", "dest", AggregationType.COUNT, 100);
      fail();
    } catch (JedisDataException e) {
      // Error on creating same rule twice
    }

    assertEquals("OK", client.tsDeleteRule("source", "dest"));
    assertEquals("OK", client.tsCreateRule("source", "dest", AggregationType.COUNT, 100));

    try {
      assertEquals("OK", client.tsDeleteRule("source", "dest1"));
      fail();
    } catch (JedisDataException e) {
      // Error on creating same rule twice
    }
  }

  @Test
  public void testAdd() {
    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");
    assertEquals("OK", client.tsCreate("seriesAdd", TSCreateParams.createParams().retention(10000).labels(labels)));
    assertEquals(0, client.tsRange("seriesAdd", TSRangeParams.rangeParams()).size());

    assertEquals(1000L, client.tsAdd("seriesAdd", 1000L, 1.1, TSCreateParams.createParams().retention(10000).labels(null)));
    assertEquals(2000L, client.tsAdd("seriesAdd", 2000L, 0.9, TSCreateParams.createParams().labels(null)));
    assertEquals(3200L, client.tsAdd("seriesAdd", 3200L, 1.1, TSCreateParams.createParams().retention(10000)));
    assertEquals(4500L, client.tsAdd("seriesAdd", 4500L, -1.1));

    TSElement[] rawValues = new TSElement[]{
      new TSElement(1000L, 1.1),
      new TSElement(2000L, 0.9),
      new TSElement(3200L, 1.1),
      new TSElement(4500L, -1.1)
    };
    List<TSElement> values = client.tsRange("seriesAdd", 800L, 3000L);
    assertEquals(2, values.size());
    assertEquals(Arrays.asList(rawValues[0], rawValues[1]), values);
    values = client.tsRange("seriesAdd", 800L, 5000L);
    assertEquals(4, values.size());
    assertEquals(Arrays.asList(rawValues), values);
    assertEquals(Arrays.asList(rawValues), client.tsRange("seriesAdd", TSRangeParams.rangeParams()));

    List<TSElement> expectedCountValues = Arrays.asList(
        new TSElement(2000L, 1), new TSElement(3200L, 1), new TSElement(4500L, 1));
    values = client.tsRange("seriesAdd", TSRangeParams.rangeParams(1200L, 4600L).aggregation(AggregationType.COUNT, 1));
    assertEquals(3, values.size());
    assertEquals(expectedCountValues, values);

    List<TSElement> expectedAvgValues = Arrays.asList(
        new TSElement(0L, 1.1), new TSElement(2000L, 1), new TSElement(4000L, -1.1));
    values = client.tsRange("seriesAdd", TSRangeParams.rangeParams(500L, 4600L).aggregation(AggregationType.AVG, 2000L));
    assertEquals(3, values.size());
    assertEquals(expectedAvgValues, values);

    // ensure zero-based index
    List<TSElement> valuesZeroBased = client.tsRange("seriesAdd",
        TSRangeParams.rangeParams(0L, 4600L).aggregation(AggregationType.AVG, 2000L));
    assertEquals(3, valuesZeroBased.size());
    assertEquals(values, valuesZeroBased);

    List<TSElement> expectedOverallSumValues = Arrays.asList(new TSElement(0L, 2.0));
    values = client.tsRange("seriesAdd", TSRangeParams.rangeParams(0L, 5000L).aggregation(AggregationType.SUM, 5000L));
    assertEquals(1, values.size());
    assertEquals(expectedOverallSumValues, values);

    List<TSElement> expectedOverallMinValues = Arrays.asList(new TSElement(0L, -1.1));
    values = client.tsRange("seriesAdd", TSRangeParams.rangeParams(0L, 5000L).aggregation(AggregationType.MIN, 5000L));
    assertEquals(1, values.size());
    assertEquals(expectedOverallMinValues, values);

    List<TSElement> expectedOverallMaxValues = Arrays.asList(new TSElement(0L, 1.1));
    values = client.tsRange("seriesAdd", TSRangeParams.rangeParams(0L, 5000L).aggregation(AggregationType.MAX, 5000L));
    assertEquals(1, values.size());
    assertEquals(expectedOverallMaxValues, values);

    // MRANGE
    assertEquals(Collections.emptyList(), client.tsMRange(TSMRangeParams.multiRangeParams().filter("l=v")));
    try {
      client.tsMRange(TSMRangeParams.multiRangeParams(500L, 4600L).aggregation(AggregationType.COUNT, 1));
      fail();
//    } catch (JedisDataException e) {
    } catch (IllegalArgumentException e) {
    }

    try {
      client.tsMRange(TSMRangeParams.multiRangeParams(500L, 4600L).aggregation(AggregationType.COUNT, 1).filter((String) null));
      fail();
//    } catch (JedisDataException e) {
    } catch (IllegalArgumentException e) {
    }

    List<TSKeyedElements> ranges = client.tsMRange(TSMRangeParams.multiRangeParams(500L, 4600L)
        .aggregation(AggregationType.COUNT, 1).filter("l1=v1"));
    assertEquals(1, ranges.size());

    TSKeyedElements range = ranges.get(0);
    assertEquals("seriesAdd", range.getKey());
    assertEquals(Collections.emptyMap(), range.getLabels());

    List<TSElement> rangeValues = range.getValue();
    assertEquals(4, rangeValues.size());
    assertEquals(new TSElement(1000, 1), rangeValues.get(0));
    assertNotEquals(new TSElement(1000, 1.1), rangeValues.get(0));
    assertEquals(2000L, rangeValues.get(1).getTimestamp());
    assertEquals("(2000:1.0)", rangeValues.get(1).toString());

    // Add with labels
    Map<String, String> labels2 = new HashMap<>();
    labels2.put("l3", "v3");
    labels2.put("l4", "v4");
    assertEquals(1000L, client.tsAdd("seriesAdd2", 1000L, 1.1, TSCreateParams.createParams().retention(10000).labels(labels2)));
    List<TSKeyedElements> ranges2 = client.tsMRange(TSMRangeParams.multiRangeParams(500L, 4600L)
        .aggregation(AggregationType.COUNT, 1).withLabels().filter("l4=v4"));
    assertEquals(1, ranges2.size());
    assertEquals(labels2, ranges2.get(0).getLabels());

    Map<String, String> labels3 = new HashMap<>();
    labels3.put("l3", "v33");
    labels3.put("l4", "v4");
    assertEquals(1000L, client.tsAdd("seriesAdd3", 1000L, 1.1, TSCreateParams.createParams().labels(labels3)));
    assertEquals(2000L, client.tsAdd("seriesAdd3", 2000L, 1.1, TSCreateParams.createParams().labels(labels3)));
    assertEquals(3000L, client.tsAdd("seriesAdd3", 3000L, 1.1, TSCreateParams.createParams().labels(labels3)));
    List<TSKeyedElements> ranges3 = client.tsMRange(TSMRangeParams.multiRangeParams(500L, 4600L)
        .aggregation(AggregationType.AVG, 1L).withLabels(true).count(2).filter("l4=v4"));
    assertEquals(2, ranges3.size());
    assertEquals(1, ranges3.get(0).getValue().size());
    assertEquals(labels2, ranges3.get(0).getLabels());
    assertEquals(2, ranges3.get(1).getValue().size());
    assertEquals(labels3, ranges3.get(1).getLabels());

    assertEquals(800L, client.tsAdd("seriesAdd", 800L, 1.1));
    assertEquals(700L, client.tsAdd("seriesAdd", 700L, 1.1, TSCreateParams.createParams().retention(10000)));
    assertEquals(600L, client.tsAdd("seriesAdd", 600L, 1.1, TSCreateParams.createParams().retention(10000).labels(null)));

    assertEquals(400L, client.tsAdd("seriesAdd4", 400L, 0.4, TSCreateParams.createParams()
        .retention(7898L).uncompressed().chunkSize(1000L).duplicatePolicy(DuplicatePolicy.SUM)
        .labels(labels)));
    assertEquals("TSDB-TYPE", client.type("seriesAdd4"));
    assertEquals(400L, client.tsAdd("seriesAdd4", 400L, 0.3, TSCreateParams.createParams()
        .retention(7898L).uncompressed().chunkSize(1000L).duplicatePolicy(DuplicatePolicy.SUM)
        .labels(labels)));
    assertEquals(Arrays.asList(new TSElement(400L, 0.7)), client.tsRange("seriesAdd4", 0L, Long.MAX_VALUE));

    // Range on none existing key
    try {
      client.tsRange("seriesAdd1", TSRangeParams.rangeParams(500L, 4000L).aggregation(AggregationType.COUNT, 1));
      fail();
    } catch (JedisDataException e) {
    }
  }

  @Test
  public void issue75() {
    client.tsMRange(TSMRangeParams.multiRangeParams().filter("id=1"));
  }

  @Test
  public void del() {
    try {
      client.tsDel("ts-del", 0, 1);
      fail();
    } catch (JedisDataException jde) {
      // expected
    }

    assertEquals("OK", client.tsCreate("ts-del", TSCreateParams.createParams().retention(10000L)));
    assertEquals(0, client.tsDel("ts-del", 0, 1));

    assertEquals(1000L, client.tsAdd("ts-del", 1000L, 1.1, TSCreateParams.createParams().retention(10000)));
    assertEquals(2000L, client.tsAdd("ts-del", 2000L, 0.9));
    assertEquals(3200L, client.tsAdd("ts-del", 3200L, 1.1, TSCreateParams.createParams().retention(10000)));
    assertEquals(4500L, client.tsAdd("ts-del", 4500L, -1.1));
    assertEquals(4, client.tsRange("ts-del", 0, 5000).size());

    assertEquals(2, client.tsDel("ts-del", 2000, 4000));
    assertEquals(2, client.tsRange("ts-del", 0, 5000).size());
    assertEquals(1, client.tsRange("ts-del", 0, 2500).size());
    assertEquals(1, client.tsRange("ts-del", 2500, 5000).size());
  }

  @Test
  public void testValue() {
    TSElement v = new TSElement(1234, 234.89634);
    assertEquals(1234, v.getTimestamp());
    assertEquals(234.89634, v.getValue(), 0);

    assertEquals(v, new TSElement(1234, 234.89634));
    assertNotEquals(v, new TSElement(1334, 234.89634));
    assertNotEquals(v, new TSElement(1234, 234.8934));
    assertNotEquals(1234, v.getValue());

    assertEquals("(1234:234.89634)", v.toString());
//    assertEquals(-1856758940, v.hashCode());
    assertEquals(-1856719580, v.hashCode());
  }

  @Test
  public void testAddStar() throws InterruptedException {
    Map<String, String> labels = new HashMap<>();
    labels.put("l11", "v11");
    labels.put("l22", "v22");
    assertEquals("OK", client.tsCreate("seriesAdd2", TSCreateParams.createParams().retention(10000L).labels(labels)));

    long startTime = System.currentTimeMillis();
    Thread.sleep(1);
//    long add1 = client.tsAdd("seriesAdd2", 1.1, 10000);
    long add1 = client.tsAdd("seriesAdd2", 1.1);
    assertTrue(add1 > startTime);
    Thread.sleep(1);
    long add2 = client.tsAdd("seriesAdd2", 3.2);
    assertTrue(add2 > add1);
    Thread.sleep(1);
    long add3 = client.tsAdd("seriesAdd2", 3.2);
    assertTrue(add3 > add2);
    Thread.sleep(1);
    long add4 = client.tsAdd("seriesAdd2", -1.2);
    assertTrue(add4 > add3);
    Thread.sleep(1);
    long endTime = System.currentTimeMillis();
    assertTrue(endTime > add4);

    List<TSElement> values = client.tsRange("seriesAdd2", startTime, add3);
    assertEquals(3, values.size());
  }
//
//  @Test
//  public void testMadd() {
//    Map<String, String> labels = new HashMap<>();
//    labels.put("l1", "v1");
//    labels.put("l2", "v2");
//    assertEquals("OK", client.tsCreate("seriesAdd1", TSCreateParams.createParams().retention(10000L).labels(labels)));
//    assertEquals("OK", client.tsCreate("seriesAdd2", TSCreateParams.createParams().retention(10000L).labels(labels)));
//
//    long now = System.currentTimeMillis();
//    List<Object> result =
//        client.tsMadd(
//            new Measurement("seriesAdd1", 0L, 1.1), // System time
//            new Measurement("seriesAdd2", 2000L, 3.2),
//            new Measurement("seriesAdd1", 1500L, 2.67), // Should return an error
//            new Measurement("seriesAdd2", 3200L, 54.2),
//            new Measurement("seriesAdd2", 4300L, 21.2));
//
//    assertTrue(now <= (Long) result.get(0) && now + 5 > (Long) result.get(0));
//    assertEquals(2000L, result.get(1));
//    assertTrue(result.get(2) instanceof JedisDataException);
//    assertEquals(3200L, result.get(3));
//    assertEquals(4300L, result.get(4));
//
//    List<TSElement> values1 = client.tsRange("seriesAdd1", 0, Long.MAX_VALUE);
//    assertEquals(1, values1.size());
//    assertEquals(1.1, values1.get(0).getValue(), 0.001);
//
//    List<TSElement> values2 = client.tsRange("seriesAdd2", TSRangeParams.rangeParams(0, Long.MAX_VALUE).count(2));
//    assertEquals(2, values2.size());
//    assertEquals(3.2, values2.get(0).getValue(), 0.001);
//    assertEquals(54.2, values2.get(1).getValue(), 0.001);
//  }
//
//  @Test
//  public void testIncrByDecrBy() throws InterruptedException {
//    assertEquals("OK", client.tsCreate("seriesIncDec", 100 * 1000 /*100sec retentionTime*/));
//    assertEquals(1L, client.tsAdd("seriesIncDec", 1L, 1, 10000, null), 0);
//    assertEquals(2L, client.incrBy("seriesIncDec", 3, 2L), 0);
//    assertEquals(3L, client.decrBy("seriesIncDec", 2, 3L), 0);
//    List<TSElement> values = client.tsRange("seriesIncDec", 1L, 3L);
//    assertEquals(3, values.size());
//    assertEquals(2, values[2].getValue(), 0);
//    if (moduleVersion >= 10400) {
//      assertEquals(3L, client.decrBy("seriesIncDec", 2, 3L), 0);
//      values = client.tsRange("seriesIncDec", 1L, Long.MAX_VALUE);
//      assertEquals(3, values.size());
//    } else {
//      try {
//        client.incrBy("seriesIncDec", 3, 0L);
//        fail();
//      } catch (JedisDataException e) {
//        // Error on incrby in the past
//      }
//    }
//  }

  @Test
  public void align() {
    client.tsAdd("align", 1, 10d);
    client.tsAdd("align", 3, 5d);
    client.tsAdd("align", 11, 10d);
    client.tsAdd("align", 25, 11d);

    List<TSElement> values = client.tsRange("align", TSRangeParams.rangeParams(1L, 30L).aggregation(AggregationType.COUNT, 10));
    assertEquals(Arrays.asList(new TSElement(1, 2), new TSElement(11, 1), new TSElement(21, 1)), values);

    values = client.tsRange("align", TSRangeParams.rangeParams(1L, 30L).alignStart().aggregation(AggregationType.COUNT, 10));
    assertEquals(Arrays.asList(new TSElement(1, 2), new TSElement(11, 1), new TSElement(21, 1)), values);

    values = client.tsRange("align", TSRangeParams.rangeParams(1L, 30L).alignEnd().aggregation(AggregationType.COUNT, 10));
    assertEquals(Arrays.asList(new TSElement(1, 2), new TSElement(11, 1), new TSElement(21, 1)), values);

    values =
        client.tsRange("align", TSRangeParams.rangeParams(1L, 30L).align(5).aggregation(AggregationType.COUNT, 10));
    assertEquals(Arrays.asList(new TSElement(1, 2), new TSElement(11, 1), new TSElement(21, 1)), values);
  }

  @Test
  public void rangeFilterBy() {

    TSElement[] rawValues =
        new TSElement[] {
          new TSElement(1000L, 1.0),
          new TSElement(2000L, 0.9),
          new TSElement(3200L, 1.1),
          new TSElement(4500L, -1.1)
        };

    for (TSElement value : rawValues) {
      client.tsAdd("filterBy", value.getTimestamp(), value.getValue());
    }

    // RANGE
    List<TSElement> values = client.tsRange("filterBy", 0L, 5000L);
    assertEquals(Arrays.asList(rawValues), values);

    values = client.tsRange("filterBy", TSRangeParams.rangeParams(0L, 5000L).filterByTS(1000L, 2000L));
    assertEquals(Arrays.asList(rawValues[0], rawValues[1]), values);

    values = client.tsRange("filterBy", TSRangeParams.rangeParams(0L, 5000L).filterByValues(1.0, 1.2));
    assertEquals(Arrays.asList(rawValues[0], rawValues[2]), values);

    values = client.tsRange("filterBy", TSRangeParams.rangeParams(0L, 5000L).filterByTS(1000L, 2000L).filterByValues(1.0, 1.2));
    assertEquals(Arrays.asList(rawValues[0]), values);

    // REVRANGE
    values = client.tsRevRange("filterBy", 0L, 5000L);
    assertEquals(Arrays.asList(rawValues[3], rawValues[2], rawValues[1], rawValues[0]), values);

    values =
        client.tsRevRange("filterBy", TSRangeParams.rangeParams(0L, 5000L).filterByTS(1000L, 2000L));
    assertEquals(Arrays.asList(rawValues[1], rawValues[0]), values);

    values =
        client.tsRevRange("filterBy", TSRangeParams.rangeParams(0L, 5000L).filterByValues(1.0, 1.2));
    assertEquals(Arrays.asList(rawValues[2], rawValues[0]), values);

    values =
        client.tsRevRange("filterBy", TSRangeParams.rangeParams(0L, 5000L).filterByTS(1000L, 2000L).filterByValues(1.0, 1.2));
    assertEquals(Arrays.asList(rawValues[0]), values);
  }

  @Test
  public void mrangeFilterBy() {

    Map<String, String> labels = Collections.singletonMap("label", "multi");
    client.tsCreate("ts1", TSCreateParams.createParams().labels(labels));
    client.tsCreate("ts2", TSCreateParams.createParams().labels(labels));
    String filter = "label=multi";

    TSElement[] rawValues = new TSElement[]{
      new TSElement(1000L, 1.0),
      new TSElement(2000L, 0.9),
      new TSElement(3200L, 1.1),
      new TSElement(4500L, -1.1)
    };

    client.tsAdd("ts1", rawValues[0].getTimestamp(), rawValues[0].getValue());
    client.tsAdd("ts2", rawValues[1].getTimestamp(), rawValues[1].getValue());
    client.tsAdd("ts2", rawValues[2].getTimestamp(), rawValues[2].getValue());
    client.tsAdd("ts1", rawValues[3].getTimestamp(), rawValues[3].getValue());

    // MRANGE
    List<TSKeyedElements> range = client.tsMRange(0L, 5000L, filter);
    assertEquals("ts1", range.get(0).getKey());
    assertEquals(Arrays.asList(rawValues[0], rawValues[3]), range.get(0).getValue());
    assertEquals("ts2", range.get(1).getKey());
    assertEquals(Arrays.asList(rawValues[1], rawValues[2]), range.get(1).getValue());

    range = client.tsMRange(TSMRangeParams.multiRangeParams(0L, 5000L).filterByTS(1000L, 2000L).filter(filter));
    assertEquals("ts1", range.get(0).getKey());
    assertEquals(Arrays.asList(rawValues[0]), range.get(0).getValue());
    assertEquals("ts2", range.get(1).getKey());
    assertEquals(Arrays.asList(rawValues[1]), range.get(1).getValue());

    range = client.tsMRange(TSMRangeParams.multiRangeParams(0L, 5000L).filterByValues(1.0, 1.2).filter(filter));
    assertEquals("ts1", range.get(0).getKey());
    assertEquals(Arrays.asList(rawValues[0]), range.get(0).getValue());
    assertEquals("ts2", range.get(1).getKey());
    assertEquals(Arrays.asList(rawValues[2]), range.get(1).getValue());

    range = client.tsMRange(TSMRangeParams.multiRangeParams(0L, 5000L)
        .filterByTS(1000L, 2000L).filterByValues(1.0, 1.2).filter(filter));
    assertEquals(Arrays.asList(rawValues[0]), range.get(0).getValue());

    // MREVRANGE
    range = client.tsMRevRange(0L, 5000L,  filter);
    assertEquals("ts1", range.get(0).getKey());
    assertEquals(Arrays.asList(rawValues[3], rawValues[0]), range.get(0).getValue());
    assertEquals("ts2", range.get(1).getKey());
    assertEquals(Arrays.asList(rawValues[2], rawValues[1]), range.get(1).getValue());

    range = client.tsMRevRange(TSMRangeParams.multiRangeParams(0L, 5000L).filterByTS(1000L, 2000L).filter(filter));
    assertEquals("ts1", range.get(0).getKey());
    assertEquals(Arrays.asList(rawValues[0]), range.get(0).getValue());
    assertEquals("ts2", range.get(1).getKey());
    assertEquals(Arrays.asList(rawValues[1]), range.get(1).getValue());

    range = client.tsMRevRange(TSMRangeParams.multiRangeParams(0L, 5000L).filterByValues(1.0, 1.2).filter(filter));
    assertEquals("ts1", range.get(0).getKey());
    assertEquals(Arrays.asList(rawValues[0]), range.get(0).getValue());
    assertEquals("ts2", range.get(1).getKey());
    assertEquals(Arrays.asList(rawValues[2]), range.get(1).getValue());

    range = client.tsMRevRange(TSMRangeParams.multiRangeParams(0L, 5000L)
        .filterByTS(1000L, 2000L).filterByValues(1.0, 1.2).filter(filter));
    assertEquals(Arrays.asList(rawValues[0]), range.get(0).getValue());
  }

  @Test
  public void groupByReduce() {
    client.tsCreate("ts1", TSCreateParams.createParams().labels(convertMap("metric", "cpu", "metric_name", "system")));
    client.tsCreate("ts2", TSCreateParams.createParams().labels(convertMap("metric", "cpu", "metric_name", "user")));

    client.tsAdd("ts1", 1L, 90.0);
    client.tsAdd("ts1", 2L, 45.0);
    client.tsAdd("ts2", 2L, 99.0);

//    List<TSElements> range = client.tsMRange(TSMRangeParams.multiGetParams(0L, 100L).withLabels()
//        .groupByReduce("metric_name", "max"), "metric=cpu");
    List<TSKeyedElements> range = client.tsMRange(TSMRangeParams.multiRangeParams(0L, 100L).withLabels()
        .filter("metric=cpu").groupBy("metric_name", "max"));
    assertEquals(2, range.size());

    assertEquals("metric_name=system", range.get(0).getKey());
    assertEquals("system", range.get(0).getLabels().get("metric_name"));
    assertEquals("max", range.get(0).getLabels().get("__reducer__"));
    assertEquals("ts1", range.get(0).getLabels().get("__source__"));
    assertEquals(Arrays.asList(new TSElement(1, 90), new TSElement(2, 45)), range.get(0).getValue());

    assertEquals("metric_name=user", range.get(1).getKey());
    assertEquals("user", range.get(1).getLabels().get("metric_name"));
    assertEquals("max", range.get(1).getLabels().get("__reducer__"));
    assertEquals("ts2", range.get(1).getLabels().get("__source__"));
    assertEquals(Arrays.asList(new TSElement(2, 99)), range.get(1).getValue());
  }

  private Map<String, String> convertMap(String... array) {
    Map<String, String> map = new HashMap<>(array.length / 2);
    for (int i = 0; i < array.length; i += 2) {
      map.put(array[i], array[i + 1]);
    }
    return map;
  }

  @Test
  public void testGet() {

    // Test for empty result none existing series
    try {
      client.tsGet("seriesGet");
      fail();
    } catch (JedisDataException e) {
    }

    assertEquals("OK", client.tsCreate("seriesGet", TSCreateParams.createParams()
        .retention(100 * 1000 /*100sec retentionTime*/)));

    // Test for empty result
    assertNull(client.tsGet("seriesGet"));

    // Test returned last Value
    client.tsAdd("seriesGet", 2558, 8.7);
    assertEquals(new TSElement(2558, 8.7), client.tsGet("seriesGet"));

    client.tsAdd("seriesGet", 3458, 1.117);
    assertEquals(new TSElement(3458, 1.117), client.tsGet("seriesGet"));
  }

  @Test
  public void testMGet() {
    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");
    assertEquals("OK", client.tsCreate("seriesMGet1", TSCreateParams.createParams()
        .retention(100 * 1000 /*100sec retentionTime*/).labels(labels)));
    assertEquals("OK", client.tsCreate("seriesMGet2", TSCreateParams.createParams()
        .retention(100 * 1000 /*100sec retentionTime*/).labels(labels)));

    // Test for empty result
    List<TSKeyValue<TSElement>> ranges1 = client.tsMGet(TSMGetParams.multiGetParams().withLabels(false), "l1=v2");
    assertEquals(0, ranges1.size());

    // Test for empty ranges
    List<TSKeyValue<TSElement>> ranges2 = client.tsMGet(TSMGetParams.multiGetParams().withLabels(true), "l1=v1");
    assertEquals(2, ranges2.size());
    assertEquals(labels, ranges2.get(0).getLabels());
    assertEquals(labels, ranges2.get(1).getLabels());
    assertNull(ranges2.get(0).getValue());

    // Test for returned result on MGet
    client.tsAdd("seriesMGet1", 1500, 1.3);
    List<TSKeyValue<TSElement>> ranges3 = client.tsMGet(TSMGetParams.multiGetParams().withLabels(false), "l1=v1");
    assertEquals(2, ranges3.size());
    assertEquals(Collections.emptyMap(), ranges3.get(0).getLabels());
    assertEquals(Collections.emptyMap(), ranges3.get(1).getLabels());
    assertEquals(new TSElement(1500, 1.3), ranges3.get(0).getValue());
    assertNull(ranges3.get(1).getValue());
  }

  @Test
  public void testAlter() {

    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");
    assertEquals("OK", client.tsCreate("seriesAlter", TSCreateParams.createParams()
        .retention(57 * 1000 /*57sec retentionTime*/).labels(labels)));
    assertEquals(Collections.emptyList(), client.tsQueryIndex("l2=v22"));

    // Test alter labels
    labels.remove("l1");
    labels.put("l2", "v22");
    labels.put("l3", "v33");
    assertEquals("OK", client.tsAlter("seriesAlter", TSAlterParams.alterParams().labels(labels)));
    assertEquals(Collections.singletonList("seriesAlter"), client.tsQueryIndex("l2=v22", "l3=v33"));
    assertEquals(Collections.emptyList(), client.tsQueryIndex("l1=v1"));

    // Test alter labels and retention time
    labels.put("l1", "v11");
    labels.remove("l2");
    assertEquals("OK", client.tsAlter("seriesAlter", TSAlterParams.alterParams()
        .retentionTime(324 /*324ms retentionTime*/).labels(labels)));
//    Info info = client.info("seriesAlter");
//    assertEquals((Long) 324L, info.getProperty("retentionTime"));
//    assertEquals("v11", info.getLabel("l1"));
//    assertNull(info.getLabel("l2"));
//    assertEquals("v33", info.getLabel("l3"));
  }

  @Test
  public void testQueryIndex() {

    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");
    assertEquals("OK", client.tsCreate("seriesQueryIndex1", TSCreateParams.createParams()
        .retention(100 * 1000 /*100sec retentionTime*/).labels(labels)));

    labels.put("l2", "v22");
    labels.put("l3", "v33");
    assertEquals("OK", client.tsCreate("seriesQueryIndex2", TSCreateParams.createParams()
        .retention(100 * 1000 /*100sec retentionTime*/).labels(labels)));

    assertEquals(Arrays.<String>asList(), client.tsQueryIndex("l1=v2"));
    assertEquals(Arrays.asList("seriesQueryIndex1", "seriesQueryIndex2"), client.tsQueryIndex("l1=v1"));
    assertEquals(Arrays.asList("seriesQueryIndex2"), client.tsQueryIndex("l2=v22"));
  }
//
//  @Test
//  public void testInfo() {
//    Map<String, String> labels = new HashMap<>();
//    labels.put("l1", "v1");
//    labels.put("l2", "v2");
//    assertEquals("OK", client.tsCreate("source", 10000L /*retentionTime*/, labels));
//    assertEquals("OK", client.tsCreate("dest", 20000L /*retentionTime*/));
//    assertEquals("OK", client.tsCreateRule("source", "dest", AggregationType.AVG, 100));
//
//    Info info = client.info("source");
//    assertEquals((Long) 10000L, info.getProperty("retentionTime"));
//    if (moduleVersion >= 10400) {
//      assertEquals((Long) 4096L, info.getProperty("chunkSize"));
//    } else {
//      assertEquals((Long) 256L, info.getProperty("maxSamplesPerChunk"));
//    }
//    assertEquals("v1", info.getLabel("l1"));
//    assertEquals("v2", info.getLabel("l2"));
//    assertNull(info.getLabel("l3"));
//
//    Rule rule = info.getRule("dest");
//    assertEquals("dest", rule.getTarget());
//    assertEquals(100L, rule.getValue());
//    assertEquals(AggregationType.AVG, rule.getAggregation());
//    try {
//      client.info("seriesInfo1");
//      fail();
//    } catch (JedisDataException e) {
//      // Error on info on none existing series
//    }
//  }

  @Test
  public void testRevRange() {

    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");
    assertEquals("OK", client.tsCreate("seriesAdd", TSCreateParams.createParams().retention(10000L).labels(labels)));
    assertEquals(Collections.emptyList(), client.tsRevRange("seriesAdd", TSRangeParams.rangeParams()));

    assertEquals(1000L, client.tsAdd("seriesRevRange", 1000L, 1.1, TSCreateParams.createParams().retention(10000)));
    assertEquals(2000L, client.tsAdd("seriesRevRange", 2000L, 0.9, TSCreateParams.createParams().labels(null)));
    assertEquals(3200L, client.tsAdd("seriesRevRange", 3200L, 1.1, TSCreateParams.createParams().retention(10000)));
    assertEquals(4500L, client.tsAdd("seriesRevRange", 4500L, -1.1));

    TSElement[] rawValues = new TSElement[]{
      new TSElement(4500L, -1.1),
      new TSElement(3200L, 1.1),
      new TSElement(2000L, 0.9),
      new TSElement(1000L, 1.1)
    };
    List<TSElement> values = client.tsRevRange("seriesRevRange", 800L, 3000L);
    assertEquals(2, values.size());
    assertEquals(Arrays.asList(Arrays.copyOfRange(rawValues, 2, 4)), values);
    values = client.tsRevRange("seriesRevRange", 800L, 5000L);
    assertEquals(4, values.size());
    assertEquals(Arrays.asList(rawValues), values);
    assertEquals(Arrays.asList(rawValues), client.tsRevRange("seriesRevRange", TSRangeParams.rangeParams()));

    List<TSElement> expectedCountValues = Arrays.asList(
        new TSElement(4500L, 1), new TSElement(3200L, 1), new TSElement(2000L, 1));
    values = client.tsRevRange("seriesRevRange", TSRangeParams.rangeParams(1200L, 4600L)
        .aggregation(AggregationType.COUNT, 1));
    assertEquals(3, values.size());
    assertEquals(expectedCountValues, values);

    List<TSElement> expectedAvgValues = Arrays.asList(
        new TSElement(4000L, -1.1), new TSElement(2000L, 1), new TSElement(0L, 1.1));
    values = client.tsRevRange("seriesRevRange", TSRangeParams.rangeParams(500L, 4600L)
        .aggregation(AggregationType.AVG, 2000L));
    assertEquals(3, values.size());
    assertEquals(expectedAvgValues, values);
  }

  @Test
  public void testMRevRange() {

    assertEquals(Collections.emptyList(), client.tsMRevRange(TSMRangeParams.multiRangeParams().filter("l=v")));

    Map<String, String> labels1 = new HashMap<>();
    labels1.put("l3", "v3");
    labels1.put("l4", "v4");
    assertEquals(1000L, client.tsAdd("seriesMRevRange1", 1000L, 1.1,
        TSCreateParams.createParams().retention(10000).labels(labels1)));
    assertEquals(2222L, client.tsAdd("seriesMRevRange1", 2222L, 3.1,
        TSCreateParams.createParams().retention(10000).labels(labels1)));
    List<TSKeyedElements> ranges1 = client.tsMRevRange(TSMRangeParams.multiRangeParams(500L, 4600L)
        .aggregation(AggregationType.COUNT, 1).withLabels().filter("l4=v4"));
    assertEquals(1, ranges1.size());
    assertEquals(labels1, ranges1.get(0).getLabels());
    assertEquals(Arrays.asList(new TSElement(2222L, 1.0), new TSElement(1000L, 1.0)), ranges1.get(0).getValue());

    Map<String, String> labels2 = new HashMap<>();
    labels2.put("l3", "v3");
    labels2.put("l4", "v44");
    assertEquals(1000L, client.tsAdd("seriesMRevRange2", 1000L, 8.88,
        TSCreateParams.createParams().retention(10000).labels(labels2)));
    assertEquals(1111L, client.tsAdd("seriesMRevRange2", 1111L, 99.99,
        TSCreateParams.createParams().retention(10000).labels(labels2)));
    List<TSKeyedElements> ranges2 = client.tsMRevRange(500L, 4600L, "l3=v3");
    assertEquals(2, ranges2.size());
    assertEquals(Collections.emptyMap(), ranges2.get(0).getLabels());
    assertEquals(Arrays.asList(new TSElement(2222L, 3.1), new TSElement(1000L, 1.1)), ranges2.get(0).getValue());
    assertEquals(Collections.emptyMap(), ranges2.get(0).getLabels());
    assertEquals(Arrays.asList(new TSElement(1111L, 99.99), new TSElement(1000L, 8.88)), ranges2.get(1).getValue());

    Map<String, String> labels3 = new HashMap<>();
    labels3.put("l3", "v33");
    labels3.put("l4", "v4");
    assertEquals(2200L, client.tsAdd("seriesMRevRange3", 2200L, -1.1, TSCreateParams.createParams().labels(labels3)));
    assertEquals(2400L, client.tsAdd("seriesMRevRange3", 2400L, 1.1, TSCreateParams.createParams().labels(labels3)));
    assertEquals(3300L, client.tsAdd("seriesMRevRange3", 3300L, -33, TSCreateParams.createParams().labels(labels3)));
    List<TSKeyedElements> ranges3 = client.tsMRevRange(TSMRangeParams.multiRangeParams(500L, 4600L)
        .aggregation(AggregationType.AVG, 500).withLabels().count(5).filter("l4=v4"));
    assertEquals(2, ranges3.size());
    assertEquals(labels1, ranges3.get(0).getLabels());
    assertEquals(Arrays.asList(new TSElement(2000L, 3.1), new TSElement(1000L, 1.1)), ranges3.get(0).getValue());
    assertEquals(labels3, ranges3.get(1).getLabels());
    assertEquals(Arrays.asList(new TSElement(3000L, -33.0), new TSElement(2000L, 0.0)), ranges3.get(1).getValue());
  }
}
