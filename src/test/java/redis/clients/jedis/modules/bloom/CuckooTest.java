package redis.clients.jedis.modules.bloom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import redis.clients.jedis.bloom.CFInsertParams;
import redis.clients.jedis.bloom.CFReserveParams;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.modules.RedisModuleCommandsTestBase;

/**
 * Tests for the Cuckoo Filter Implementation
 */
public class CuckooTest extends RedisModuleCommandsTestBase {

  @BeforeClass
  public static void prepare() {
    RedisModuleCommandsTestBase.prepare();
  }

  @AfterClass
  public static void tearDown() {
//    RedisModuleCommandsTestBase.tearDown();
  }

  @Test
  public void testReservationCapacityOnly() {
    client.cfReserve("cuckoo1", 10);

    Map<String, Object> info = client.cfInfo("cuckoo1");
    assertEquals(8L, info.get("Number of buckets"));
    assertEquals(0L, info.get("Number of items inserted"));
    assertEquals(72L, info.get("Size"));
    assertEquals(1L, info.get("Expansion rate"));
    assertEquals(1L, info.get("Number of filters"));
    assertEquals(2L, info.get("Bucket size"));
    assertEquals(20L, info.get("Max iterations"));
    assertEquals(0L, info.get("Number of items deleted"));
  }

  @Test
  public void testReservationCapacityAndBucketSize() {
    client.cfReserve("cuckoo2", 200, CFReserveParams.reserveParams().bucketSize(10));

    Map<String, Object> info = client.cfInfo("cuckoo2");

    assertEquals(32L, info.get("Number of buckets"));
    assertEquals(0L, info.get("Number of items inserted"));
    assertEquals(376L, info.get("Size"));
    assertEquals(1L, info.get("Expansion rate"));
    assertEquals(1L, info.get("Number of filters"));
    assertEquals(10L, info.get("Bucket size"));
    assertEquals(20L, info.get("Max iterations"));
    assertEquals(0L, info.get("Number of items deleted"));
  }

  @Test
  public void testReservationCapacityAndBucketSizeAndMaxIterations() {
    client.cfReserve("cuckoo3", 200, CFReserveParams.reserveParams()
        .bucketSize(10).maxIterations(20));

    Map<String, Object> info = client.cfInfo("cuckoo3");

    assertEquals(32L, info.get("Number of buckets"));
    assertEquals(0L, info.get("Number of items inserted"));
    assertEquals(376L, info.get("Size"));
    assertEquals(1L, info.get("Expansion rate"));
    assertEquals(1L, info.get("Number of filters"));
    assertEquals(10L, info.get("Bucket size"));
    assertEquals(20L, info.get("Max iterations"));
    assertEquals(0L, info.get("Number of items deleted"));
  }

  @Test
  public void testReservationAllParams() {
    client.cfReserve("cuckoo4", 200, CFReserveParams.reserveParams()
        .bucketSize(10).expansion(4).maxIterations(20));

    Map<String, Object> info = client.cfInfo("cuckoo4");

    assertEquals(32L, info.get("Number of buckets"));
    assertEquals(0L, info.get("Number of items inserted"));
    assertEquals(376L, info.get("Size"));
    assertEquals(4L, info.get("Expansion rate"));
    assertEquals(1L, info.get("Number of filters"));
    assertEquals(10L, info.get("Bucket size"));
    assertEquals(20L, info.get("Max iterations"));
    assertEquals(0L, info.get("Number of items deleted"));
  }

  @Test
  public void testAdd() {
    client.cfReserve("cuckoo5", 64000);
    assertTrue(client.cfAdd("cuckoo5", "test"));

    Map<String, Object> info = client.cfInfo("cuckoo5");
    assertEquals(1L, info.get("Number of items inserted"));
  }

  @Test
  public void testAddNxItemDoesExist() {
    client.cfReserve("cuckoo6", 64000);
    assertTrue(client.cfAddNx("cuckoo6", "filter"));
  }

  @Test
  public void testAddNxItemExists() {
    client.cfReserve("cuckoo7", 64000);
    client.cfAdd("cuckoo7", "filter");
    assertFalse(client.cfAddNx("cuckoo7", "filter"));
  }

  @Test
  public void testInsert() {
    assertEquals(Arrays.asList(true), client.cfInsert("cuckoo8", "foo"));
  }

  @Test
  public void testInsertWithCapacity() {
    assertEquals(Arrays.asList(true), client.cfInsert("cuckoo9",
        CFInsertParams.insertParams().capacity(1000), "foo"));
  }

  @Test
  public void testInsertNoCreateFilterDoesNotExist() {
    try {
      client.cfInsert("cuckoo10", CFInsertParams.insertParams().noCreate(), "foo", "bar");
      fail();
    } catch (JedisDataException e) {
      assertEquals("ERR not found", e.getMessage());
    }
  }

  @Test
  public void testInsertNoCreateFilterExists() {
    client.cfInsert("cuckoo11", "bar");
    assertEquals(Arrays.asList(true, true), client.cfInsert("cuckoo11",
        CFInsertParams.insertParams().noCreate(), "foo", "bar"));
  }

  @Test
  public void testInsertNx() {
    assertEquals(Arrays.asList(true), client.cfInsertNx("cuckoo12", "bar"));
  }

  @Test
  public void testInsertNxWithCapacity() {
    client.cfInsertNx("cuckoo13", "bar");
    assertEquals(Arrays.asList(false), client.cfInsertNx("cuckoo13",
        CFInsertParams.insertParams().capacity(1000), "bar"));
  }

  @Test
  public void testInsertNxMultiple() {
    client.cfInsertNx("cuckoo14", "foo");
    client.cfInsertNx("cuckoo14", "bar");
    assertEquals(Arrays.asList(false, false, true),
        client.cfInsertNx("cuckoo14", "foo", "bar", "baz"));
  }

  @Test
  public void testInsertNxNoCreate() {
    try {
      client.cfInsertNx("cuckoo15", CFInsertParams.insertParams().noCreate(), "foo", "bar");
      fail();
    } catch (JedisDataException e) {
      assertEquals("ERR not found", e.getMessage());
    }
  }

  @Test
  public void testExistsItemDoesntExist() {
    assertFalse(client.cfExists("cuckoo16", "foo"));
  }

  @Test
  public void testExistsItemExists() {
    client.cfInsert("cuckoo17", "foo");
    assertTrue(client.cfExists("cuckoo17", "foo"));
  }

  @Test
  public void testDeleteItemDoesntExist() {
    client.cfInsert("cuckoo8", "bar");
    assertFalse(client.cfDel("cuckoo8", "foo"));
  }

  @Test
  public void testDeleteItemExists() {
    client.cfInsert("cuckoo18", "foo");
    assertTrue(client.cfDel("cuckoo18", "foo"));
  }

  @Test
  public void testDeleteFilterDoesNotExist() {
    Exception ex = assertThrows(JedisDataException.class, () -> {
      client.cfDel("cuckoo19", "foo");
    });
    assertTrue(ex.getMessage().contains("Not found"));
  }

  @Test
  public void testCountFilterDoesNotExist() {
    assertEquals(0L, client.cfCount("cuckoo20", "filter"));
  }

  @Test
  public void testCountFilterExist() {
    client.cfInsert("cuckoo21", "foo");
    assertEquals(0L, client.cfCount("cuckoo21", "filter"));
  }

  @Test
  public void testCountItemExists() {
    client.cfInsert("cuckoo22", "foo");
    assertEquals(1L, client.cfCount("cuckoo22", "foo"));
  }

  @Test
  public void testInfoFilterDoesNotExists() {
    Exception ex = assertThrows(JedisDataException.class, () -> {
      client.cfInfo("cuckoo23");
    });
    assertTrue(ex.getMessage().contains("ERR not found"));
  }

//  @Test
//  public void testScanDump() {
//    client.cfReserve("cuckoo24", CFReserveParams.reserveParams()//
//        .withCapacity(100)  //
//        .bucketSize(50)  //
//        .build());
//    
//    client.cfAdd("cuckoo24", "a");
//
//    long iter = 0;
//    List<Map.Entry<Long, byte[]>> chunks = new ArrayList<>();
//    while (true) {
//      Map.Entry<Long, byte[]> chunksData = client.cfScanDump("cuckoo24", iter);
//      iter = chunksData.getKey();
//      if (iter == 0) {
//        break;
//      }
//      chunks.add(chunksData);
//    }
//
//    client.delete("cuckoo24");
//
//    // Load it back
//    chunks.forEach(chunksData -> client.cfLoadChunk("cuckoo24", chunksData));
//
//    // check the bucket size
//    Map<String, Object> info = client.cfInfo("cuckoo24");
//
//    // check the retrieved filter properties
//    assertEquals(1L, info.get("Number of items inserted"));
//    assertEquals(50L, info.get("Bucket size"));
//    assertEquals(0L, info.get("Number of items deleted"));
//
//    // check for existing items
//    assertTrue(client.cfExists("cuckoo24", "a"));
//  }
//  
//  @Test
//  public void testScanDumpWithIterator() {
//    client.cfReserve("cuckoo25", CFReserveParams.reserveParams()//
//        .withCapacity(200)  //
//        .bucketSize(50)  //
//        .build());
//    
//    client.cfAdd("cuckoo25", "b");
//    
//    List<Map.Entry<Long, byte[]>> chunks = new ArrayList<>();
//    Iterator<Map.Entry<Long, byte[]>> i = client.cfScanDumpIterator("cuckoo25");
//    while (i.hasNext()) {
//      chunks.add(i.next());
//    }
//
//    client.delete("cuckoo25");
//
//    // Load it back
//    chunks.forEach(chunksData -> {
//      client.cfLoadChunk("cuckoo25", chunksData);
//    });
//
//    // check the bucket size
//    Map<String, Object> info = client.cfInfo("cuckoo25");
//
//    // check the retrieved filter properties
//    assertEquals(1L, info.get("Number of items inserted"));
//    assertEquals(50L, info.get("Bucket size"));
//    assertEquals(0L, info.get("Number of items deleted"));
//
//    // check for existing items
//    assertTrue(client.cfExists("cuckoo25", "b"));
//  }
//  
//  @Test
//  public void testScanDumpWithStream() {
//    client.cfReserve("cuckoo26", CFReserveParams.reserveParams()//
//        .withCapacity(100)  //
//        .bucketSize(50)  //
//        .build());
//    
//    client.cfAdd("cuckoo26", "c");
//    
//    List<Map.Entry<Long, byte[]>> chunks = client.cfScanDumpStream("cuckoo26").collect(Collectors.toList());
//    
//    client.delete("cuckoo26");
//
//    // Load it back
//    chunks.forEach(chunksData -> {
//      client.cfLoadChunk("cuckoo26", chunksData);
//    });
//
//    // check the bucket size
//    Map<String, Object> info = client.cfInfo("cuckoo26");
//
//    // check the retrieved filter properties
//    assertEquals(1L, info.get("Number of items inserted"));
//    assertEquals(50L, info.get("Bucket size"));
//    assertEquals(0L, info.get("Number of items deleted"));
//
//    // check for existing items
//    assertTrue(client.cfExists("cuckoo26", "c"));
//  }
}
