package redis.clients.jedis;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.json.JSONArray;

import redis.clients.jedis.args.*;
import redis.clients.jedis.bloom.BFInsertParams;
import redis.clients.jedis.bloom.BFReserveParams;
import redis.clients.jedis.bloom.CFInsertParams;
import redis.clients.jedis.bloom.CFReserveParams;
import redis.clients.jedis.commands.PipelineBinaryCommands;
import redis.clients.jedis.commands.PipelineCommands;
import redis.clients.jedis.commands.RedisModulePipelineCommands;
import redis.clients.jedis.json.JsonSetParams;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.params.*;
import redis.clients.jedis.resps.*;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.Schema;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.timeseries.*;
import redis.clients.jedis.util.KeyValue;

public abstract class MultiNodePipelineBase implements PipelineCommands, PipelineBinaryCommands,
    RedisModulePipelineCommands, Closeable {

  private final Map<HostAndPort, Queue<Response<?>>> pipelinedResponses;
  private final Map<HostAndPort, Connection> connections;
  private volatile boolean synced;

  private final CommandObjects commandObjects;

  public MultiNodePipelineBase(CommandObjects commandObjects) {
    pipelinedResponses = new LinkedHashMap<>();
    connections = new LinkedHashMap<>();
    synced = false;
    this.commandObjects = commandObjects;
  }

  protected abstract HostAndPort getNodeKey(CommandArguments args);

  protected abstract Connection getConnection(HostAndPort nodeKey);

  protected final <T> Response<T> appendCommand(CommandObject<T> commandObject) {
    HostAndPort nodeKey = getNodeKey(commandObject.getArguments());

    Queue<Response<?>> queue;
    Connection connection;
    if (pipelinedResponses.containsKey(nodeKey)) {
      queue = pipelinedResponses.get(nodeKey);
      connection = connections.get(nodeKey);
    } else {
      queue = new LinkedList<>();
      connection = getConnection(nodeKey);
      pipelinedResponses.put(nodeKey, queue);
      connections.put(nodeKey, connection);
    }

    connection.sendCommand(commandObject.getArguments());
    Response<T> response = new Response<>(commandObject.getBuilder());
    queue.add(response);
    return response;
  }

  @Override
  public void close() {
    sync();
    for (Connection connection : connections.values()) {
      connection.close();
    }
  }

  public final void sync() {
    if (synced) {
      return;
    }
    for (Map.Entry<HostAndPort, Queue<Response<?>>> entry : pipelinedResponses.entrySet()) {
      HostAndPort nodeKey = entry.getKey();
      Queue<Response<?>> queue = entry.getValue();
      List<Object> unformatted = connections.get(nodeKey).getMany(queue.size());
      for (Object o : unformatted) {
        queue.poll().set(o);
      }
    }
    synced = true;
  }

  @Override
  public Response<Boolean> exists(String key) {
    return appendCommand(commandObjects.exists(key));
  }

  @Override
  public Response<Long> exists(String... keys) {
    return appendCommand(commandObjects.exists(keys));
  }

  @Override
  public Response<Long> persist(String key) {
    return appendCommand(commandObjects.persist(key));
  }

  @Override
  public Response<String> type(String key) {
    return appendCommand(commandObjects.type(key));
  }

  @Override
  public Response<byte[]> dump(String key) {
    return appendCommand(commandObjects.dump(key));
  }

  @Override
  public Response<String> restore(String key, long ttl, byte[] serializedValue) {
    return appendCommand(commandObjects.restore(key, ttl, serializedValue));
  }

  @Override
  public Response<String> restore(String key, long ttl, byte[] serializedValue, RestoreParams params) {
    return appendCommand(commandObjects.restore(key, ttl, serializedValue, params));
  }

  @Override
  public Response<Long> expire(String key, long seconds) {
    return appendCommand(commandObjects.expire(key, seconds));
  }

  @Override
  public Response<Long> expire(String key, long seconds, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.expire(key, seconds, expiryOption));
  }

  @Override
  public Response<Long> pexpire(String key, long milliseconds) {
    return appendCommand(commandObjects.pexpire(key, milliseconds));
  }

  @Override
  public Response<Long> pexpire(String key, long milliseconds, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.pexpire(key, milliseconds, expiryOption));
  }

  @Override
  public Response<Long> expireTime(String key) {
    return appendCommand(commandObjects.expireTime(key));
  }

  @Override
  public Response<Long> pexpireTime(String key) {
    return appendCommand(commandObjects.pexpireTime(key));
  }

  @Override
  public Response<Long> expireAt(String key, long unixTime) {
    return appendCommand(commandObjects.expireAt(key, unixTime));
  }

  @Override
  public Response<Long> expireAt(String key, long unixTime, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.expireAt(key, unixTime, expiryOption));
  }

  @Override
  public Response<Long> pexpireAt(String key, long millisecondsTimestamp) {
    return appendCommand(commandObjects.pexpireAt(key, millisecondsTimestamp));
  }

  @Override
  public Response<Long> pexpireAt(String key, long millisecondsTimestamp, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.pexpireAt(key, millisecondsTimestamp, expiryOption));
  }

  @Override
  public Response<Long> ttl(String key) {
    return appendCommand(commandObjects.ttl(key));
  }

  @Override
  public Response<Long> pttl(String key) {
    return appendCommand(commandObjects.pttl(key));
  }

  @Override
  public Response<Long> touch(String key) {
    return appendCommand(commandObjects.touch(key));
  }

  @Override
  public Response<Long> touch(String... keys) {
    return appendCommand(commandObjects.touch(keys));
  }

  @Override
  public Response<List<String>> sort(String key) {
    return appendCommand(commandObjects.sort(key));
  }

  @Override
  public Response<Long> sort(String key, String dstKey) {
    return appendCommand(commandObjects.sort(key, dstKey));
  }

  @Override
  public Response<List<String>> sort(String key, SortingParams sortingParams) {
    return appendCommand(commandObjects.sort(key, sortingParams));
  }

  @Override
  public Response<Long> sort(String key, SortingParams sortingParams, String dstKey) {
    return appendCommand(commandObjects.sort(key, sortingParams, dstKey));
  }

  @Override
  public Response<List<String>> sortReadonly(String key, SortingParams sortingParams) {
    return appendCommand(commandObjects.sortReadonly(key, sortingParams));
  }

  @Override
  public Response<Long> del(String key) {
    return appendCommand(commandObjects.del(key));
  }

  @Override
  public Response<Long> del(String... keys) {
    return appendCommand(commandObjects.del(keys));
  }

  @Override
  public Response<Long> unlink(String key) {
    return appendCommand(commandObjects.unlink(key));
  }

  @Override
  public Response<Long> unlink(String... keys) {
    return appendCommand(commandObjects.unlink(keys));
  }

  @Override
  public Response<Boolean> copy(String srcKey, String dstKey, boolean replace) {
    return appendCommand(commandObjects.copy(srcKey, dstKey, replace));
  }

  @Override
  public Response<String> rename(String oldKey, String newKey) {
    return appendCommand(commandObjects.rename(oldKey, newKey));
  }

  @Override
  public Response<Long> renamenx(String oldKey, String newKey) {
    return appendCommand(commandObjects.renamenx(oldKey, newKey));
  }

  @Override
  public Response<Long> memoryUsage(String key) {
    return appendCommand(commandObjects.memoryUsage(key));
  }

  @Override
  public Response<Long> memoryUsage(String key, int samples) {
    return appendCommand(commandObjects.memoryUsage(key, samples));
  }

  @Override
  public Response<Long> objectRefcount(String key) {
    return appendCommand(commandObjects.objectRefcount(key));
  }

  @Override
  public Response<String> objectEncoding(String key) {
    return appendCommand(commandObjects.objectEncoding(key));
  }

  @Override
  public Response<Long> objectIdletime(String key) {
    return appendCommand(commandObjects.objectIdletime(key));
  }

  @Override
  public Response<Long> objectFreq(String key) {
    return appendCommand(commandObjects.objectFreq(key));
  }

  @Override
  public Response<String> migrate(String host, int port, String key, int timeout) {
    return appendCommand(commandObjects.migrate(host, port, key, timeout));
  }

  @Override
  public Response<String> migrate(String host, int port, int timeout, MigrateParams params, String... keys) {
    return appendCommand(commandObjects.migrate(host, port, timeout, params, keys));
  }

  @Override
  public Response<Set<String>> keys(String pattern) {
    return appendCommand(commandObjects.keys(pattern));
  }

  @Override
  public Response<ScanResult<String>> scan(String cursor) {
    return appendCommand(commandObjects.scan(cursor));
  }

  @Override
  public Response<ScanResult<String>> scan(String cursor, ScanParams params) {
    return appendCommand(commandObjects.scan(cursor, params));
  }

  @Override
  public Response<ScanResult<String>> scan(String cursor, ScanParams params, String type) {
    return appendCommand(commandObjects.scan(cursor, params, type));
  }

  @Override
  public Response<String> randomKey() {
    return appendCommand(commandObjects.randomKey());
  }

  @Override
  public Response<String> get(String key) {
    return appendCommand(commandObjects.get(key));
  }

  @Override
  public Response<String> getDel(String key) {
    return appendCommand(commandObjects.getDel(key));
  }

  @Override
  public Response<String> getEx(String key, GetExParams params) {
    return appendCommand(commandObjects.getEx(key, params));
  }

  @Override
  public Response<Boolean> setbit(String key, long offset, boolean value) {
    return appendCommand(commandObjects.setbit(key, offset, value));
  }

  @Override
  public Response<Boolean> getbit(String key, long offset) {
    return appendCommand(commandObjects.getbit(key, offset));
  }

  @Override
  public Response<Long> setrange(String key, long offset, String value) {
    return appendCommand(commandObjects.setrange(key, offset, value));
  }

  @Override
  public Response<String> getrange(String key, long startOffset, long endOffset) {
    return appendCommand(commandObjects.getrange(key, startOffset, endOffset));
  }

  @Override
  public Response<String> getSet(String key, String value) {
    return appendCommand(commandObjects.getSet(key, value));
  }

  @Override
  public Response<Long> setnx(String key, String value) {
    return appendCommand(commandObjects.setnx(key, value));
  }

  @Override
  public Response<String> setex(String key, long seconds, String value) {
    return appendCommand(commandObjects.setex(key, seconds, value));
  }

  @Override
  public Response<String> psetex(String key, long milliseconds, String value) {
    return appendCommand(commandObjects.psetex(key, milliseconds, value));
  }

  @Override
  public Response<List<String>> mget(String... keys) {
    return appendCommand(commandObjects.mget(keys));
  }

  @Override
  public Response<String> mset(String... keysvalues) {
    return appendCommand(commandObjects.mset(keysvalues));
  }

  @Override
  public Response<Long> msetnx(String... keysvalues) {
    return appendCommand(commandObjects.msetnx(keysvalues));
  }

  @Override
  public Response<Long> incr(String key) {
    return appendCommand(commandObjects.incr(key));
  }

  @Override
  public Response<Long> incrBy(String key, long increment) {
    return appendCommand(commandObjects.incrBy(key, increment));
  }

  @Override
  public Response<Double> incrByFloat(String key, double increment) {
    return appendCommand(commandObjects.incrByFloat(key, increment));
  }

  @Override
  public Response<Long> decr(String key) {
    return appendCommand(commandObjects.decr(key));
  }

  @Override
  public Response<Long> decrBy(String key, long decrement) {
    return appendCommand(commandObjects.decrBy(key, decrement));
  }

  @Override
  public Response<Long> append(String key, String value) {
    return appendCommand(commandObjects.append(key, value));
  }

  @Override
  public Response<String> substr(String key, int start, int end) {
    return appendCommand(commandObjects.substr(key, start, end));
  }

  @Override
  public Response<Long> strlen(String key) {
    return appendCommand(commandObjects.strlen(key));
  }

  @Override
  public Response<Long> bitcount(String key) {
    return appendCommand(commandObjects.bitcount(key));
  }

  @Override
  public Response<Long> bitcount(String key, long start, long end) {
    return appendCommand(commandObjects.bitcount(key, start, end));
  }

  @Override
  public Response<Long> bitcount(String key, long start, long end, BitCountOption option) {
    return appendCommand(commandObjects.bitcount(key, start, end, option));
  }

  @Override
  public Response<Long> bitpos(String key, boolean value) {
    return appendCommand(commandObjects.bitpos(key, value));
  }

  @Override
  public Response<Long> bitpos(String key, boolean value, BitPosParams params) {
    return appendCommand(commandObjects.bitpos(key, value, params));
  }

  @Override
  public Response<List<Long>> bitfield(String key, String... arguments) {
    return appendCommand(commandObjects.bitfield(key, arguments));
  }

  @Override
  public Response<List<Long>> bitfieldReadonly(String key, String... arguments) {
    return appendCommand(commandObjects.bitfieldReadonly(key, arguments));
  }

  @Override
  public Response<Long> bitop(BitOP op, String destKey, String... srcKeys) {
    return appendCommand(commandObjects.bitop(op, destKey, srcKeys));
  }

  @Override
  public Response<LCSMatchResult> strAlgoLCSKeys(String keyA, String keyB, StrAlgoLCSParams params) {
    return appendCommand(commandObjects.strAlgoLCSKeys(keyA, keyB, params));
  }

  @Override
  public Response<LCSMatchResult> lcs(String keyA, String keyB, LCSParams params) {
    return appendCommand(commandObjects.lcs(keyA, keyB, params));
  }

  @Override
  public Response<String> set(String key, String value) {
    return appendCommand(commandObjects.set(key, value));
  }

  @Override
  public Response<String> set(String key, String value, SetParams params) {
    return appendCommand(commandObjects.set(key, value, params));
  }

  @Override
  public Response<Long> rpush(String key, String... string) {
    return appendCommand(commandObjects.rpush(key, string));
  }

  @Override
  public Response<Long> lpush(String key, String... string) {
    return appendCommand(commandObjects.lpush(key, string));
  }

  @Override
  public Response<Long> llen(String key) {
    return appendCommand(commandObjects.llen(key));
  }

  @Override
  public Response<List<String>> lrange(String key, long start, long stop) {
    return appendCommand(commandObjects.lrange(key, start, stop));
  }

  @Override
  public Response<String> ltrim(String key, long start, long stop) {
    return appendCommand(commandObjects.ltrim(key, start, stop));
  }

  @Override
  public Response<String> lindex(String key, long index) {
    return appendCommand(commandObjects.lindex(key, index));
  }

  @Override
  public Response<String> lset(String key, long index, String value) {
    return appendCommand(commandObjects.lset(key, index, value));
  }

  @Override
  public Response<Long> lrem(String key, long count, String value) {
    return appendCommand(commandObjects.lrem(key, count, value));
  }

  @Override
  public Response<String> lpop(String key) {
    return appendCommand(commandObjects.lpop(key));
  }

  @Override
  public Response<List<String>> lpop(String key, int count) {
    return appendCommand(commandObjects.lpop(key, count));
  }

  @Override
  public Response<Long> lpos(String key, String element) {
    return appendCommand(commandObjects.lpos(key, element));
  }

  @Override
  public Response<Long> lpos(String key, String element, LPosParams params) {
    return appendCommand(commandObjects.lpos(key, element, params));
  }

  @Override
  public Response<List<Long>> lpos(String key, String element, LPosParams params, long count) {
    return appendCommand(commandObjects.lpos(key, element, params, count));
  }

  @Override
  public Response<String> rpop(String key) {
    return appendCommand(commandObjects.rpop(key));
  }

  @Override
  public Response<List<String>> rpop(String key, int count) {
    return appendCommand(commandObjects.rpop(key, count));
  }

  @Override
  public Response<Long> linsert(String key, ListPosition where, String pivot, String value) {
    return appendCommand(commandObjects.linsert(key, where, pivot, value));
  }

  @Override
  public Response<Long> lpushx(String key, String... strings) {
    return appendCommand(commandObjects.lpushx(key, strings));
  }

  @Override
  public Response<Long> rpushx(String key, String... strings) {
    return appendCommand(commandObjects.rpushx(key, strings));
  }

  @Override
  public Response<List<String>> blpop(int timeout, String key) {
    return appendCommand(commandObjects.blpop(timeout, key));
  }

  @Override
  public Response<KeyedListElement> blpop(double timeout, String key) {
    return appendCommand(commandObjects.blpop(timeout, key));
  }

  @Override
  public Response<List<String>> brpop(int timeout, String key) {
    return appendCommand(commandObjects.brpop(timeout, key));
  }

  @Override
  public Response<KeyedListElement> brpop(double timeout, String key) {
    return appendCommand(commandObjects.brpop(timeout, key));
  }

  @Override
  public Response<List<String>> blpop(int timeout, String... keys) {
    return appendCommand(commandObjects.blpop(timeout, keys));
  }

  @Override
  public Response<KeyedListElement> blpop(double timeout, String... keys) {
    return appendCommand(commandObjects.blpop(timeout, keys));
  }

  @Override
  public Response<List<String>> brpop(int timeout, String... keys) {
    return appendCommand(commandObjects.brpop(timeout, keys));
  }

  @Override
  public Response<KeyedListElement> brpop(double timeout, String... keys) {
    return appendCommand(commandObjects.brpop(timeout, keys));
  }

  @Override
  public Response<String> rpoplpush(String srcKey, String dstKey) {
    return appendCommand(commandObjects.rpoplpush(srcKey, dstKey));
  }

  @Override
  public Response<String> brpoplpush(String source, String destination, int timeout) {
    return appendCommand(commandObjects.brpoplpush(source, destination, timeout));
  }

  @Override
  public Response<String> lmove(String srcKey, String dstKey, ListDirection from, ListDirection to) {
    return appendCommand(commandObjects.lmove(srcKey, dstKey, from, to));
  }

  @Override
  public Response<String> blmove(String srcKey, String dstKey, ListDirection from, ListDirection to, double timeout) {
    return appendCommand(commandObjects.blmove(srcKey, dstKey, from, to, timeout));
  }

  @Override
  public Response<KeyValue<String, List<String>>> lmpop(ListDirection direction, String... keys) {
    return appendCommand(commandObjects.lmpop(direction, keys));
  }

  @Override
  public Response<KeyValue<String, List<String>>> lmpop(ListDirection direction, int count, String... keys) {
    return appendCommand(commandObjects.lmpop(direction, count, keys));
  }

  @Override
  public Response<KeyValue<String, List<String>>> blmpop(long timeout, ListDirection direction, String... keys) {
    return appendCommand(commandObjects.blmpop(timeout, direction, keys));
  }

  @Override
  public Response<KeyValue<String, List<String>>> blmpop(long timeout, ListDirection direction, int count, String... keys) {
    return appendCommand(commandObjects.blmpop(timeout, direction, count, keys));
  }

  @Override
  public Response<Long> hset(String key, String field, String value) {
    return appendCommand(commandObjects.hset(key, field, value));
  }

  @Override
  public Response<Long> hset(String key, Map<String, String> hash) {
    return appendCommand(commandObjects.hset(key, hash));
  }

  @Override
  public Response<String> hget(String key, String field) {
    return appendCommand(commandObjects.hget(key, field));
  }

  @Override
  public Response<Long> hsetnx(String key, String field, String value) {
    return appendCommand(commandObjects.hsetnx(key, field, value));
  }

  @Override
  public Response<String> hmset(String key, Map<String, String> hash) {
    return appendCommand(commandObjects.hmset(key, hash));
  }

  @Override
  public Response<List<String>> hmget(String key, String... fields) {
    return appendCommand(commandObjects.hmget(key, fields));
  }

  @Override
  public Response<Long> hincrBy(String key, String field, long value) {
    return appendCommand(commandObjects.hincrBy(key, field, value));
  }

  @Override
  public Response<Double> hincrByFloat(String key, String field, double value) {
    return appendCommand(commandObjects.hincrByFloat(key, field, value));
  }

  @Override
  public Response<Boolean> hexists(String key, String field) {
    return appendCommand(commandObjects.hexists(key, field));
  }

  @Override
  public Response<Long> hdel(String key, String... field) {
    return appendCommand(commandObjects.hdel(key, field));
  }

  @Override
  public Response<Long> hlen(String key) {
    return appendCommand(commandObjects.hlen(key));
  }

  @Override
  public Response<Set<String>> hkeys(String key) {
    return appendCommand(commandObjects.hkeys(key));
  }

  @Override
  public Response<List<String>> hvals(String key) {
    return appendCommand(commandObjects.hvals(key));
  }

  @Override
  public Response<Map<String, String>> hgetAll(String key) {
    return appendCommand(commandObjects.hgetAll(key));
  }

  @Override
  public Response<String> hrandfield(String key) {
    return appendCommand(commandObjects.hrandfield(key));
  }

  @Override
  public Response<List<String>> hrandfield(String key, long count) {
    return appendCommand(commandObjects.hrandfield(key, count));
  }

  @Override
  public Response<Map<String, String>> hrandfieldWithValues(String key, long count) {
    return appendCommand(commandObjects.hrandfieldWithValues(key, count));
  }

  @Override
  public Response<ScanResult<Map.Entry<String, String>>> hscan(String key, String cursor, ScanParams params) {
    return appendCommand(commandObjects.hscan(key, cursor, params));
  }

  @Override
  public Response<Long> hstrlen(String key, String field) {
    return appendCommand(commandObjects.hstrlen(key, field));
  }

  @Override
  public Response<Long> sadd(String key, String... members) {
    return appendCommand(commandObjects.sadd(key, members));
  }

  @Override
  public Response<Set<String>> smembers(String key) {
    return appendCommand(commandObjects.smembers(key));
  }

  @Override
  public Response<Long> srem(String key, String... members) {
    return appendCommand(commandObjects.srem(key, members));
  }

  @Override
  public Response<String> spop(String key) {
    return appendCommand(commandObjects.spop(key));
  }

  @Override
  public Response<Set<String>> spop(String key, long count) {
    return appendCommand(commandObjects.spop(key, count));
  }

  @Override
  public Response<Long> scard(String key) {
    return appendCommand(commandObjects.scard(key));
  }

  @Override
  public Response<Boolean> sismember(String key, String member) {
    return appendCommand(commandObjects.sismember(key, member));
  }

  @Override
  public Response<List<Boolean>> smismember(String key, String... members) {
    return appendCommand(commandObjects.smismember(key, members));
  }

  @Override
  public Response<String> srandmember(String key) {
    return appendCommand(commandObjects.srandmember(key));
  }

  @Override
  public Response<List<String>> srandmember(String key, int count) {
    return appendCommand(commandObjects.srandmember(key, count));
  }

  @Override
  public Response<ScanResult<String>> sscan(String key, String cursor, ScanParams params) {
    return appendCommand(commandObjects.sscan(key, cursor, params));
  }

  @Override
  public Response<Set<String>> sdiff(String... keys) {
    return appendCommand(commandObjects.sdiff(keys));
  }

  @Override
  public Response<Long> sdiffstore(String dstKey, String... keys) {
    return appendCommand(commandObjects.sdiffstore(dstKey, keys));
  }

  @Override
  public Response<Set<String>> sinter(String... keys) {
    return appendCommand(commandObjects.sinter(keys));
  }

  @Override
  public Response<Long> sinterstore(String dstKey, String... keys) {
    return appendCommand(commandObjects.sinterstore(dstKey, keys));
  }

  @Override
  public Response<Long> sintercard(String... keys){
    return appendCommand(commandObjects.sintercard(keys));
  }

  @Override
  public Response<Long> sintercard(int limit, String... keys){
    return appendCommand(commandObjects.sintercard(limit, keys));
  }

  @Override
  public Response<Set<String>> sunion(String... keys) {
    return appendCommand(commandObjects.sunion(keys));
  }

  @Override
  public Response<Long> sunionstore(String dstKey, String... keys) {
    return appendCommand(commandObjects.sunionstore(dstKey, keys));
  }

  @Override
  public Response<Long> smove(String srcKey, String dstKey, String member) {
    return appendCommand(commandObjects.smove(srcKey, dstKey, member));
  }

  @Override
  public Response<Long> zadd(String key, double score, String member) {
    return appendCommand(commandObjects.zadd(key, score, member));
  }

  @Override
  public Response<Long> zadd(String key, double score, String member, ZAddParams params) {
    return appendCommand(commandObjects.zadd(key, score, member, params));
  }

  @Override
  public Response<Long> zadd(String key, Map<String, Double> scoreMembers) {
    return appendCommand(commandObjects.zadd(key, scoreMembers));
  }

  @Override
  public Response<Long> zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
    return appendCommand(commandObjects.zadd(key, scoreMembers, params));
  }

  @Override
  public Response<Double> zaddIncr(String key, double score, String member, ZAddParams params) {
    return appendCommand(commandObjects.zaddIncr(key, score, member, params));
  }

  @Override
  public Response<Long> zrem(String key, String... members) {
    return appendCommand(commandObjects.zrem(key, members));
  }

  @Override
  public Response<Double> zincrby(String key, double increment, String member) {
    return appendCommand(commandObjects.zincrby(key, increment, member));
  }

  @Override
  public Response<Double> zincrby(String key, double increment, String member, ZIncrByParams params) {
    return appendCommand(commandObjects.zincrby(key, increment, member, params));
  }

  @Override
  public Response<Long> zrank(String key, String member) {
    return appendCommand(commandObjects.zrank(key, member));
  }

  @Override
  public Response<Long> zrevrank(String key, String member) {
    return appendCommand(commandObjects.zrevrank(key, member));
  }

  @Override
  public Response<List<String>> zrange(String key, long start, long stop) {
    return appendCommand(commandObjects.zrange(key, start, stop));
  }

  @Override
  public Response<List<String>> zrevrange(String key, long start, long stop) {
    return appendCommand(commandObjects.zrevrange(key, start, stop));
  }

  @Override
  public Response<List<Tuple>> zrangeWithScores(String key, long start, long stop) {
    return appendCommand(commandObjects.zrangeWithScores(key, start, stop));
  }

  @Override
  public Response<List<Tuple>> zrevrangeWithScores(String key, long start, long stop) {
    return appendCommand(commandObjects.zrevrangeWithScores(key, start, stop));
  }

  @Override
  public Response<List<String>> zrange(String key, ZRangeParams zRangeParams) {
    return appendCommand(commandObjects.zrange(key, zRangeParams));
  }

  @Override
  public Response<List<Tuple>> zrangeWithScores(String key, ZRangeParams zRangeParams) {
    return appendCommand(commandObjects.zrangeWithScores(key, zRangeParams));
  }

  @Override
  public Response<Long> zrangestore(String dest, String src, ZRangeParams zRangeParams) {
    return appendCommand(commandObjects.zrangestore(dest, src, zRangeParams));
  }

  @Override
  public Response<String> zrandmember(String key) {
    return appendCommand(commandObjects.zrandmember(key));
  }

  @Override
  public Response<List<String>> zrandmember(String key, long count) {
    return appendCommand(commandObjects.zrandmember(key, count));
  }

  @Override
  public Response<List<Tuple>> zrandmemberWithScores(String key, long count) {
    return appendCommand(commandObjects.zrandmemberWithScores(key, count));
  }

  @Override
  public Response<Long> zcard(String key) {
    return appendCommand(commandObjects.zcard(key));
  }

  @Override
  public Response<Double> zscore(String key, String member) {
    return appendCommand(commandObjects.zscore(key, member));
  }

  @Override
  public Response<List<Double>> zmscore(String key, String... members) {    
    return appendCommand(commandObjects.zmscore(key, members));
  }

  @Override
  public Response<Tuple> zpopmax(String key) {
    return appendCommand(commandObjects.zpopmax(key));
  }

  @Override
  public Response<List<Tuple>> zpopmax(String key, int count) {
    return appendCommand(commandObjects.zpopmax(key, count));
  }

  @Override
  public Response<Tuple> zpopmin(String key) {
    return appendCommand(commandObjects.zpopmin(key));
  }

  @Override
  public Response<List<Tuple>> zpopmin(String key, int count) {
    return appendCommand(commandObjects.zpopmin(key, count));
  }

  @Override
  public Response<Long> zcount(String key, double min, double max) {
    return appendCommand(commandObjects.zcount(key, min, max));
  }

  @Override
  public Response<Long> zcount(String key, String min, String max) {
    return appendCommand(commandObjects.zcount(key, min, max));
  }

  @Override
  public Response<List<String>> zrangeByScore(String key, double min, double max) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max));
  }

  @Override
  public Response<List<String>> zrangeByScore(String key, String min, String max) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max));
  }

  @Override
  public Response<List<String>> zrevrangeByScore(String key, double max, double min) {
    return appendCommand(commandObjects.zrevrangeByScore(key, max, min));

  }

  @Override
  public Response<List<String>> zrangeByScore(String key, double min, double max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<List<String>> zrevrangeByScore(String key, String max, String min) {
    return appendCommand(commandObjects.zrevrangeByScore(key, max, min));
  }

  @Override
  public Response<List<String>> zrangeByScore(String key, String min, String max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<List<String>> zrevrangeByScore(String key, double max, double min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(String key, double min, double max) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<List<String>> zrevrangeByScore(String key, String max, String min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(String key, String min, String max) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<Long> zremrangeByRank(String key, long start, long stop) {
    return appendCommand(commandObjects.zremrangeByRank(key, start, stop));
  }

  @Override
  public Response<Long> zremrangeByScore(String key, double min, double max) {
    return appendCommand(commandObjects.zremrangeByScore(key, min, max));
  }

  @Override
  public Response<Long> zremrangeByScore(String key, String min, String max) {
    return appendCommand(commandObjects.zremrangeByScore(key, min, max));
  }

  @Override
  public Response<Long> zlexcount(String key, String min, String max) {
    return appendCommand(commandObjects.zlexcount(key, min, max));
  }

  @Override
  public Response<List<String>> zrangeByLex(String key, String min, String max) {
    return appendCommand(commandObjects.zrangeByLex(key, min, max));
  }

  @Override
  public Response<List<String>> zrangeByLex(String key, String min, String max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Response<List<String>> zrevrangeByLex(String key, String max, String min) {
    return appendCommand(commandObjects.zrevrangeByLex(key, max, min));
  }

  @Override
  public Response<List<String>> zrevrangeByLex(String key, String max, String min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByLex(key, max, min, offset, count));
  }

  @Override
  public Response<Long> zremrangeByLex(String key, String min, String max) {
    return appendCommand(commandObjects.zremrangeByLex(key, min, max));
  }

  @Override
  public Response<ScanResult<Tuple>> zscan(String key, String cursor, ScanParams params) {
    return appendCommand(commandObjects.zscan(key, cursor, params));
  }

  @Override
  public Response<KeyedZSetElement> bzpopmax(double timeout, String... keys) {
    return appendCommand(commandObjects.bzpopmax(timeout, keys));
  }

  @Override
  public Response<KeyedZSetElement> bzpopmin(double timeout, String... keys) {
    return appendCommand(commandObjects.bzpopmin(timeout, keys));
  }

  @Override
  public Response<KeyValue<String, List<Tuple>>> zmpop(SortedSetOption option, String... keys) {
    return appendCommand(commandObjects.zmpop(option, keys));
  }

  @Override
  public Response<KeyValue<String, List<Tuple>>> zmpop(SortedSetOption option, int count, String... keys) {
    return appendCommand(commandObjects.zmpop(option, count, keys));
  }

  @Override
  public Response<KeyValue<String, List<Tuple>>> bzmpop(long timeout, SortedSetOption option, String... keys) {
    return appendCommand(commandObjects.bzmpop(timeout, option, keys));
  }

  @Override
  public Response<KeyValue<String, List<Tuple>>> bzmpop(long timeout, SortedSetOption option, int count, String... keys) {
    return appendCommand(commandObjects.bzmpop(timeout, option, count, keys));
  }

  @Override
  public Response<Set<String>> zdiff(String... keys) {
    return appendCommand(commandObjects.zdiff(keys));
  }

  @Override
  public Response<Set<Tuple>> zdiffWithScores(String... keys) {
    return appendCommand(commandObjects.zdiffWithScores(keys));
  }

  @Override
  public Response<Long> zdiffStore(String dstKey, String... keys) {
    return appendCommand(commandObjects.zdiffStore(dstKey, keys));
  }

  @Override
  public Response<Long> zinterstore(String dstKey, String... sets) {
    return appendCommand(commandObjects.zinterstore(dstKey, sets));
  }

  @Override
  public Response<Long> zinterstore(String dstKey, ZParams params, String... sets) {
    return appendCommand(commandObjects.zinterstore(dstKey, params, sets));
  }

  @Override
  public Response<Set<String>> zinter(ZParams params, String... keys) {
    return appendCommand(commandObjects.zinter(params, keys));
  }

  @Override
  public Response<Set<Tuple>> zinterWithScores(ZParams params, String... keys) {
    return appendCommand(commandObjects.zinterWithScores(params, keys));
  }

  @Override
  public Response<Long> zintercard(String... keys) {
    return appendCommand(commandObjects.zintercard(keys));
  }

  @Override
  public Response<Long> zintercard(long limit, String... keys) {
    return appendCommand(commandObjects.zintercard(limit, keys));
  }

  @Override
  public Response<Set<String>> zunion(ZParams params, String... keys) {
    return appendCommand(commandObjects.zunion(params, keys));
  }

  @Override
  public Response<Set<Tuple>> zunionWithScores(ZParams params, String... keys) {
    return appendCommand(commandObjects.zunionWithScores(params, keys));
  }

  @Override
  public Response<Long> zunionstore(String dstKey, String... sets) {
    return appendCommand(commandObjects.zunionstore(dstKey, sets));
  }

  @Override
  public Response<Long> zunionstore(String dstKey, ZParams params, String... sets) {
    return appendCommand(commandObjects.zunionstore(dstKey, params, sets));
  }

  @Override
  public Response<Long> geoadd(String key, double longitude, double latitude, String member) {
    return appendCommand(commandObjects.geoadd(key, longitude, latitude, member));
  }

  @Override
  public Response<Long> geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
    return appendCommand(commandObjects.geoadd(key, memberCoordinateMap));
  }

  @Override
  public Response<Long> geoadd(String key, GeoAddParams params, Map<String, GeoCoordinate> memberCoordinateMap) {
    return appendCommand(commandObjects.geoadd(key, params, memberCoordinateMap));
  }

  @Override
  public Response<Double> geodist(String key, String member1, String member2) {
    return appendCommand(commandObjects.geodist(key, member1, member2));
  }

  @Override
  public Response<Double> geodist(String key, String member1, String member2, GeoUnit unit) {
    return appendCommand(commandObjects.geodist(key, member1, member2, unit));
  }

  @Override
  public Response<List<String>> geohash(String key, String... members) {
    return appendCommand(commandObjects.geohash(key, members));
  }

  @Override
  public Response<List<GeoCoordinate>> geopos(String key, String... members) {
    return appendCommand(commandObjects.geopos(key, members));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadius(key, longitude, latitude, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusReadonly(String key, double longitude, double latitude, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadiusReadonly(key, longitude, latitude, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadius(key, longitude, latitude, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusReadonly(String key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadiusReadonly(key, longitude, latitude, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(String key, String member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadiusByMember(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMemberReadonly(String key, String member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadiusByMemberReadonly(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadiusByMember(key, member, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMemberReadonly(String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadiusByMemberReadonly(key, member, radius, unit, param));
  }

  @Override
  public Response<Long> georadiusStore(String key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return appendCommand(commandObjects.georadiusStore(key, longitude, latitude, radius, unit, param, storeParam));
  }

  @Override
  public Response<Long> georadiusByMemberStore(String key, String member, double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return appendCommand(commandObjects.georadiusByMemberStore(key, member, radius, unit, param, storeParam));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(String key, String member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(String key, GeoCoordinate coord, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, coord, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(String key, String member, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, member, width, height, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(String key, GeoCoordinate coord, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, coord, width, height, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(String key, GeoSearchParam params) {
    return appendCommand(commandObjects.geosearch(key, params));
  }

  @Override
  public Response<Long> geosearchStore(String dest, String src, String member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, member, radius, unit));
  }

  @Override
  public Response<Long> geosearchStore(String dest, String src, GeoCoordinate coord, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, coord, radius, unit));
  }

  @Override
  public Response<Long> geosearchStore(String dest, String src, String member, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, member, width, height, unit));
  }

  @Override
  public Response<Long> geosearchStore(String dest, String src, GeoCoordinate coord, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, coord, width, height, unit));
  }

  @Override
  public Response<Long> geosearchStore(String dest, String src, GeoSearchParam params) {
    return appendCommand(commandObjects.geosearchStore(dest, src, params));
  }

  @Override
  public Response<Long> geosearchStoreStoreDist(String dest, String src, GeoSearchParam params) {
    return appendCommand(commandObjects.geosearchStoreStoreDist(dest, src, params));
  }

  @Override
  public Response<Long> pfadd(String key, String... elements) {
    return appendCommand(commandObjects.pfadd(key, elements));
  }

  @Override
  public Response<String> pfmerge(String destkey, String... sourcekeys) {
    return appendCommand(commandObjects.pfmerge(destkey, sourcekeys));
  }

  @Override
  public Response<Long> pfcount(String key) {
    return appendCommand(commandObjects.pfcount(key));
  }

  @Override
  public Response<Long> pfcount(String... keys) {
    return appendCommand(commandObjects.pfcount(keys));
  }

  @Override
  public Response<StreamEntryID> xadd(String key, StreamEntryID id, Map<String, String> hash) {
    return appendCommand(commandObjects.xadd(key, id, hash));
  }

  @Override
  public Response<StreamEntryID> xadd(String key, XAddParams params, Map<String, String> hash) {
    return appendCommand(commandObjects.xadd(key, params, hash));
  }

  @Override
  public Response<Long> xlen(String key) {
    return appendCommand(commandObjects.xlen(key));
  }

  @Override
  public Response<List<StreamEntry>> xrange(String key, StreamEntryID start, StreamEntryID end) {
    return appendCommand(commandObjects.xrange(key, start, end));
  }

  @Override
  public Response<List<StreamEntry>> xrange(String key, StreamEntryID start, StreamEntryID end, int count) {
    return appendCommand(commandObjects.xrange(key, start, end, count));
  }

  @Override
  public Response<List<StreamEntry>> xrevrange(String key, StreamEntryID end, StreamEntryID start) {
    return appendCommand(commandObjects.xrevrange(key, start, end));
  }

  @Override
  public Response<List<StreamEntry>> xrevrange(String key, StreamEntryID end, StreamEntryID start, int count) {
    return appendCommand(commandObjects.xrevrange(key, start, end, count));
  }

  @Override
  public Response<List<StreamEntry>> xrange(String key, String start, String end) {
    return appendCommand(commandObjects.xrange(key, start, end));
  }

  @Override
  public Response<List<StreamEntry>> xrange(String key, String start, String end, int count) {
    return appendCommand(commandObjects.xrange(key, start, end, count));
  }

  @Override
  public Response<List<StreamEntry>> xrevrange(String key, String end, String start) {
    return appendCommand(commandObjects.xrevrange(key, start, end));
  }

  @Override
  public Response<List<StreamEntry>> xrevrange(String key, String end, String start, int count) {
    return appendCommand(commandObjects.xrevrange(key, start, end, count));
  }

  @Override
  public Response<Long> xack(String key, String group, StreamEntryID... ids) {
    return appendCommand(commandObjects.xack(key, group, ids));
  }

  @Override
  public Response<String> xgroupCreate(String key, String groupName, StreamEntryID id, boolean makeStream) {
    return appendCommand(commandObjects.xgroupCreate(key, groupName, id, makeStream));
  }

  @Override
  public Response<String> xgroupSetID(String key, String groupName, StreamEntryID id) {
    return appendCommand(commandObjects.xgroupSetID(key, groupName, id));
  }

  @Override
  public Response<Long> xgroupDestroy(String key, String groupName) {
    return appendCommand(commandObjects.xgroupDestroy(key, groupName));
  }

  @Override
  public Response<Boolean> xgroupCreateConsumer(String key, String groupName, String consumerName) {
    return appendCommand(commandObjects.xgroupCreateConsumer(key, groupName, consumerName));
  }

  @Override
  public Response<Long> xgroupDelConsumer(String key, String groupName, String consumerName) {
    return appendCommand(commandObjects.xgroupDelConsumer(key, groupName, consumerName));
  }

  @Override
  public Response<StreamPendingSummary> xpending(String key, String groupName) {
    return appendCommand(commandObjects.xpending(key, groupName));
  }

  @Override
  public Response<List<StreamPendingEntry>> xpending(String key, String groupName, StreamEntryID start, StreamEntryID end, int count, String consumerName) {
    return appendCommand(commandObjects.xpending(key, groupName, start, end, count, consumerName));
  }

  @Override
  public Response<List<StreamPendingEntry>> xpending(String key, String groupName, XPendingParams params) {
    return appendCommand(commandObjects.xpending(key, groupName, params));
  }

  @Override
  public Response<Long> xdel(String key, StreamEntryID... ids) {
    return appendCommand(commandObjects.xdel(key, ids));
  }

  @Override
  public Response<Long> xtrim(String key, long maxLen, boolean approximate) {
    return appendCommand(commandObjects.xtrim(key, maxLen, approximate));
  }

  @Override
  public Response<Long> xtrim(String key, XTrimParams params) {
    return appendCommand(commandObjects.xtrim(key, params));
  }

  @Override
  public Response<List<StreamEntry>> xclaim(String key, String group, String consumerName, long minIdleTime, XClaimParams params, StreamEntryID... ids) {
    return appendCommand(commandObjects.xclaim(key, group, consumerName, minIdleTime, params, ids));
  }

  @Override
  public Response<List<StreamEntryID>> xclaimJustId(String key, String group, String consumerName, long minIdleTime, XClaimParams params, StreamEntryID... ids) {
    return appendCommand(commandObjects.xclaimJustId(key, group, consumerName, minIdleTime, params, ids));
  }

  @Override
  public Response<Map.Entry<StreamEntryID, List<StreamEntry>>> xautoclaim(String key, String group, String consumerName, long minIdleTime, StreamEntryID start, XAutoClaimParams params) {
    return appendCommand(commandObjects.xautoclaim(key, group, consumerName, minIdleTime, start, params));
  }

  @Override
  public Response<Map.Entry<StreamEntryID, List<StreamEntryID>>> xautoclaimJustId(String key, String group, String consumerName, long minIdleTime, StreamEntryID start, XAutoClaimParams params) {
    return appendCommand(commandObjects.xautoclaimJustId(key, group, consumerName, minIdleTime, start, params));
  }

  @Override
  public Response<StreamInfo> xinfoStream(String key) {
    return appendCommand(commandObjects.xinfoStream(key));
  }

  @Override
  public Response<StreamFullInfo> xinfoStreamFull(String key) {
    return appendCommand(commandObjects.xinfoStreamFull(key));
  }

  @Override
  public Response<StreamFullInfo> xinfoStreamFull(String key, int count) {
    return appendCommand(commandObjects.xinfoStreamFull(key, count));
  }

  @Override
  @Deprecated
  public Response<List<StreamGroupInfo>> xinfoGroup(String key) {
    return appendCommand(commandObjects.xinfoGroup(key));
  }

  @Override
  public Response<List<StreamGroupInfo>> xinfoGroups(String key) {
    return appendCommand(commandObjects.xinfoGroups(key));
  }

  @Override
  public Response<List<StreamConsumersInfo>> xinfoConsumers(String key, String group) {
    return appendCommand(commandObjects.xinfoConsumers(key, group));
  }

  @Override
  public Response<List<Map.Entry<String, List<StreamEntry>>>> xread(XReadParams xReadParams, Map<String, StreamEntryID> streams) {
    return appendCommand(commandObjects.xread(xReadParams, streams));
  }

  @Override
  public Response<List<Map.Entry<String, List<StreamEntry>>>> xreadGroup(String groupName, String consumer, XReadGroupParams xReadGroupParams, Map<String, StreamEntryID> streams) {
    return appendCommand(commandObjects.xreadGroup(groupName, consumer, xReadGroupParams, streams));
  }

  @Override
  public Response<Object> eval(String script) {
    return appendCommand(commandObjects.eval(script));
  }

  @Override
  public Response<Object> eval(String script, int keyCount, String... params) {
    return appendCommand(commandObjects.eval(script, keyCount, params));
  }

  @Override
  public Response<Object> eval(String script, List<String> keys, List<String> args) {
    return appendCommand(commandObjects.eval(script, keys, args));
  }

  @Override
  public Response<Object> evalReadonly(String script, List<String> keys, List<String> args) {
    return appendCommand(commandObjects.evalReadonly(script, keys, args));
  }

  @Override
  public Response<Object> evalsha(String sha1) {
    return appendCommand(commandObjects.evalsha(sha1));
  }

  @Override
  public Response<Object> evalsha(String sha1, int keyCount, String... params) {
    return appendCommand(commandObjects.evalsha(sha1, keyCount, params));
  }

  @Override
  public Response<Object> evalsha(String sha1, List<String> keys, List<String> args) {
    return appendCommand(commandObjects.evalsha(sha1, keys, args));
  }

  @Override
  public Response<Object> evalshaReadonly(String sha1, List<String> keys, List<String> args) {
    return appendCommand(commandObjects.evalshaReadonly(sha1, keys, args));
  }

  @Override
  public Response<Long> waitReplicas(String sampleKey, int replicas, long timeout) {
    return appendCommand(commandObjects.waitReplicas(sampleKey, replicas, timeout));
  }

  @Override
  public Response<Object> eval(String script, String sampleKey) {
    return appendCommand(commandObjects.eval(script, sampleKey));
  }

  @Override
  public Response<Object> evalsha(String sha1, String sampleKey) {
    return appendCommand(commandObjects.evalsha(sha1, sampleKey));
  }

  @Override
  public Response<List<Boolean>> scriptExists(String sampleKey, String... sha1) {
    return appendCommand(commandObjects.scriptExists(sampleKey, sha1));
  }

  @Override
  public Response<String> scriptLoad(String script, String sampleKey) {
    return appendCommand(commandObjects.scriptLoad(script, sampleKey));
  }

  @Override
  public Response<String> scriptFlush(String sampleKey) {
    return appendCommand(commandObjects.scriptFlush(sampleKey));
  }

  @Override
  public Response<String> scriptFlush(String sampleKey, FlushMode flushMode) {
    return appendCommand(commandObjects.scriptFlush(sampleKey, flushMode));
  }

  @Override
  public Response<String> scriptKill(String sampleKey) {
    return appendCommand(commandObjects.scriptKill(sampleKey));
  }

  @Override
  public Response<Object> fcall(byte[] name, List<byte[]> keys, List<byte[]> args) {
    return appendCommand(commandObjects.fcall(name, keys, args));
  }

  @Override
  public Response<Object> fcall(String name, List<String> keys, List<String> args) {
    return appendCommand(commandObjects.fcall(name, keys, args));
  }

  @Override
  public Response<Object> fcallReadonly(byte[] name, List<byte[]> keys, List<byte[]> args) {
    return appendCommand(commandObjects.fcallReadonly(name, keys, args));
  }

  @Override
  public Response<Object> fcallReadonly(String name, List<String> keys, List<String> args) {
    return appendCommand(commandObjects.fcallReadonly(name, keys, args));
  }

  @Override
  public Response<String> functionDelete(byte[] libraryName) {
    return appendCommand(commandObjects.functionDelete(libraryName));
  }

  @Override
  public Response<String> functionDelete(String libraryName) {
    return appendCommand(commandObjects.functionDelete(libraryName));
  }

  @Override
  public Response<byte[]> functionDump() {
    return appendCommand(commandObjects.functionDump());
  }

  @Override
  public Response<List<LibraryInfo>> functionList(String libraryNamePattern) {
    return appendCommand(commandObjects.functionList(libraryNamePattern));
  }

  @Override
  public Response<List<LibraryInfo>> functionList() {
    return appendCommand(commandObjects.functionList());
  }

  @Override
  public Response<List<LibraryInfo>> functionListWithCode(String libraryNamePattern) {
    return appendCommand(commandObjects.functionListWithCode(libraryNamePattern));
  }

  @Override
  public Response<List<LibraryInfo>> functionListWithCode() {
    return appendCommand(commandObjects.functionListWithCode());
  }

  @Override
  public Response<List<Object>> functionListBinary() {
    return appendCommand(commandObjects.functionListBinary());
  }

  @Override
  public Response<List<Object>> functionList(final byte[] libraryNamePattern) {
    return appendCommand(commandObjects.functionList(libraryNamePattern));
  }

  @Override
  public Response<List<Object>> functionListWithCodeBinary() {
    return appendCommand(commandObjects.functionListWithCodeBinary());
  }

  @Override
  public Response<List<Object>> functionListWithCode(final byte[] libraryNamePattern) {
    return appendCommand(commandObjects.functionListWithCode(libraryNamePattern));
  }

  @Override
  public Response<String> functionLoad(byte[] engineName, byte[] libraryName, byte[] functionCode) {
    return appendCommand(commandObjects.functionLoad(engineName, libraryName, functionCode));
  }

  @Override
  public Response<String> functionLoad(String engineName, String libraryName, String functionCode) {
    return appendCommand(commandObjects.functionLoad(engineName, libraryName, functionCode));
  }

  @Override
  public Response<String> functionLoad(byte[] engineName, byte[] libraryName, FunctionLoadParams params, byte[] functionCode) {
    return appendCommand(commandObjects.functionLoad(engineName, libraryName, params, functionCode));
  }

  @Override
  public Response<String> functionLoad(String engineName, String libraryName, FunctionLoadParams params, String functionCode) {
    return appendCommand(commandObjects.functionLoad(engineName, libraryName, params, functionCode));
  }

  @Override
  public Response<String> functionRestore(byte[] serializedValue) {
    return appendCommand(commandObjects.functionRestore(serializedValue));
  }

  @Override
  public Response<String> functionRestore(byte[] serializedValue, FunctionRestorePolicy policy) {
    return appendCommand(commandObjects.functionRestore(serializedValue, policy));
  }

  @Override
  public Response<String> functionFlush() {
    return appendCommand(commandObjects.functionFlush());
  }

  @Override
  public Response<String> functionFlush(FlushMode mode) {
    return appendCommand(commandObjects.functionFlush(mode));
  }

  @Override
  public Response<String> functionKill() {
    return appendCommand(commandObjects.functionKill());
  }

  @Override
  public Response<FunctionStats> functionStats() {
    return appendCommand(commandObjects.functionStats());
  }

  @Override
  public Response<Object> functionStatsBinary() {
    return appendCommand(commandObjects.functionStatsBinary());
  }

  public Response<Long> publish(String channel, String message) {
    return appendCommand(commandObjects.publish(channel, message));
  }

  public Response<LCSMatchResult> strAlgoLCSStrings(String strA, String strB, StrAlgoLCSParams params) {
    return appendCommand(commandObjects.strAlgoLCSStrings(strA, strB, params));
  }

  @Override
  public Response<Long> geoadd(byte[] key, double longitude, double latitude, byte[] member) {
    return appendCommand(commandObjects.geoadd(key, longitude, latitude, member));
  }

  @Override
  public Response<Long> geoadd(byte[] key, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    return appendCommand(commandObjects.geoadd(key, memberCoordinateMap));
  }

  @Override
  public Response<Long> geoadd(byte[] key, GeoAddParams params, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    return appendCommand(commandObjects.geoadd(key, params, memberCoordinateMap));
  }

  @Override
  public Response<Double> geodist(byte[] key, byte[] member1, byte[] member2) {
    return appendCommand(commandObjects.geodist(key, member1, member2));
  }

  @Override
  public Response<Double> geodist(byte[] key, byte[] member1, byte[] member2, GeoUnit unit) {
    return appendCommand(commandObjects.geodist(key, member1, member2, unit));
  }

  @Override
  public Response<List<byte[]>> geohash(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.geohash(key, members));
  }

  @Override
  public Response<List<GeoCoordinate>> geopos(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.geopos(key, members));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(byte[] key, double longitude, double latitude, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadius(key, longitude, latitude, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusReadonly(byte[] key, double longitude, double latitude, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadiusReadonly(key, longitude, latitude, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(byte[] key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadius(key, longitude, latitude, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusReadonly(byte[] key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadiusReadonly(key, longitude, latitude, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(byte[] key, byte[] member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadiusByMember(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMemberReadonly(byte[] key, byte[] member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.georadiusByMemberReadonly(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadiusByMember(key, member, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMemberReadonly(byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return appendCommand(commandObjects.georadiusByMemberReadonly(key, member, radius, unit, param));
  }

  @Override
  public Response<Long> georadiusStore(byte[] key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return appendCommand(commandObjects.georadiusStore(key, longitude, latitude, radius, unit, param, storeParam));
  }

  @Override
  public Response<Long> georadiusByMemberStore(byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return appendCommand(commandObjects.georadiusByMemberStore(key, member, radius, unit, param, storeParam));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(byte[] key, byte[] member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(byte[] key, GeoCoordinate coord, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, coord, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(byte[] key, byte[] member, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, member, width, height, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(byte[] key, GeoCoordinate coord, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearch(key, coord, width, height, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> geosearch(byte[] key, GeoSearchParam params) {
    return appendCommand(commandObjects.geosearch(key, params));
  }

  @Override
  public Response<Long> geosearchStore(byte[] dest, byte[] src, byte[] member, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, member, radius, unit));
  }

  @Override
  public Response<Long> geosearchStore(byte[] dest, byte[] src, GeoCoordinate coord, double radius, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, coord, radius, unit));
  }

  @Override
  public Response<Long> geosearchStore(byte[] dest, byte[] src, byte[] member, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, member, width, height, unit));
  }

  @Override
  public Response<Long> geosearchStore(byte[] dest, byte[] src, GeoCoordinate coord, double width, double height, GeoUnit unit) {
    return appendCommand(commandObjects.geosearchStore(dest, src, coord, width, height, unit));
  }

  @Override
  public Response<Long> geosearchStore(byte[] dest, byte[] src, GeoSearchParam params) {
    return appendCommand(commandObjects.geosearchStore(dest, src, params));
  }

  @Override
  public Response<Long> geosearchStoreStoreDist(byte[] dest, byte[] src, GeoSearchParam params) {
    return appendCommand(commandObjects.geosearchStoreStoreDist(dest, src, params));
  }

  @Override
  public Response<Long> hset(byte[] key, byte[] field, byte[] value) {
    return appendCommand(commandObjects.hset(key, field, value));
  }

  @Override
  public Response<Long> hset(byte[] key, Map<byte[], byte[]> hash) {
    return appendCommand(commandObjects.hset(key, hash));
  }

  @Override
  public Response<byte[]> hget(byte[] key, byte[] field) {
    return appendCommand(commandObjects.hget(key, field));
  }

  @Override
  public Response<Long> hsetnx(byte[] key, byte[] field, byte[] value) {
    return appendCommand(commandObjects.hsetnx(key, field, value));
  }

  @Override
  public Response<String> hmset(byte[] key, Map<byte[], byte[]> hash) {
    return appendCommand(commandObjects.hmset(key, hash));
  }

  @Override
  public Response<List<byte[]>> hmget(byte[] key, byte[]... fields) {
    return appendCommand(commandObjects.hmget(key, fields));
  }

  @Override
  public Response<Long> hincrBy(byte[] key, byte[] field, long value) {
    return appendCommand(commandObjects.hincrBy(key, field, value));
  }

  @Override
  public Response<Double> hincrByFloat(byte[] key, byte[] field, double value) {
    return appendCommand(commandObjects.hincrByFloat(key, field, value));
  }

  @Override
  public Response<Boolean> hexists(byte[] key, byte[] field) {
    return appendCommand(commandObjects.hexists(key, field));
  }

  @Override
  public Response<Long> hdel(byte[] key, byte[]... field) {
    return appendCommand(commandObjects.hdel(key, field));
  }

  @Override
  public Response<Long> hlen(byte[] key) {
    return appendCommand(commandObjects.hlen(key));
  }

  @Override
  public Response<Set<byte[]>> hkeys(byte[] key) {
    return appendCommand(commandObjects.hkeys(key));
  }

  @Override
  public Response<List<byte[]>> hvals(byte[] key) {
    return appendCommand(commandObjects.hvals(key));
  }

  @Override
  public Response<Map<byte[], byte[]>> hgetAll(byte[] key) {
    return appendCommand(commandObjects.hgetAll(key));
  }

  @Override
  public Response<byte[]> hrandfield(byte[] key) {
    return appendCommand(commandObjects.hrandfield(key));
  }

  @Override
  public Response<List<byte[]>> hrandfield(byte[] key, long count) {
    return appendCommand(commandObjects.hrandfield(key, count));
  }

  @Override
  public Response<Map<byte[], byte[]>> hrandfieldWithValues(byte[] key, long count) {
    return appendCommand(commandObjects.hrandfieldWithValues(key, count));
  }

  @Override
  public Response<ScanResult<Map.Entry<byte[], byte[]>>> hscan(byte[] key, byte[] cursor, ScanParams params) {
    return appendCommand(commandObjects.hscan(key, cursor, params));
  }

  @Override
  public Response<Long> hstrlen(byte[] key, byte[] field) {
    return appendCommand(commandObjects.hstrlen(key, field));
  }

  @Override
  public Response<Long> pfadd(byte[] key, byte[]... elements) {
    return appendCommand(commandObjects.pfadd(key, elements));
  }

  @Override
  public Response<String> pfmerge(byte[] destkey, byte[]... sourcekeys) {
    return appendCommand(commandObjects.pfmerge(destkey, sourcekeys));
  }

  @Override
  public Response<Long> pfcount(byte[] key) {
    return appendCommand(commandObjects.pfcount(key));
  }

  @Override
  public Response<Long> pfcount(byte[]... keys) {
    return appendCommand(commandObjects.pfcount(keys));
  }

  @Override
  public Response<Boolean> exists(byte[] key) {
    return appendCommand(commandObjects.exists(key));
  }

  @Override
  public Response<Long> exists(byte[]... keys) {
    return appendCommand(commandObjects.exists(keys));
  }

  @Override
  public Response<Long> persist(byte[] key) {
    return appendCommand(commandObjects.persist(key));
  }

  @Override
  public Response<String> type(byte[] key) {
    return appendCommand(commandObjects.type(key));
  }

  @Override
  public Response<byte[]> dump(byte[] key) {
    return appendCommand(commandObjects.dump(key));
  }

  @Override
  public Response<String> restore(byte[] key, long ttl, byte[] serializedValue) {
    return appendCommand(commandObjects.restore(key, ttl, serializedValue));
  }

  @Override
  public Response<String> restore(byte[] key, long ttl, byte[] serializedValue, RestoreParams params) {
    return appendCommand(commandObjects.restore(key, ttl, serializedValue, params));
  }

  @Override
  public Response<Long> expire(byte[] key, long seconds) {
    return appendCommand(commandObjects.expire(key, seconds));
  }

  @Override
  public Response<Long> expire(byte[] key, long seconds, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.expire(key, seconds, expiryOption));
  }

  @Override
  public Response<Long> pexpire(byte[] key, long milliseconds) {
    return appendCommand(commandObjects.pexpire(key, milliseconds));
  }

  @Override
  public Response<Long> pexpire(byte[] key, long milliseconds, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.pexpire(key, milliseconds, expiryOption));
  }

  @Override
  public Response<Long> expireTime(byte[] key) {
    return appendCommand(commandObjects.expireTime(key));
  }

  @Override
  public Response<Long> pexpireTime(byte[] key) {
    return appendCommand(commandObjects.pexpireTime(key));
  }

  @Override
  public Response<Long> expireAt(byte[] key, long unixTime) {
    return appendCommand(commandObjects.expireAt(key, unixTime));
  }

  @Override
  public Response<Long> expireAt(byte[] key, long unixTime, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.expireAt(key, unixTime, expiryOption));
  }

  @Override
  public Response<Long> pexpireAt(byte[] key, long millisecondsTimestamp) {
    return appendCommand(commandObjects.pexpireAt(key, millisecondsTimestamp));
  }

  @Override
  public Response<Long> pexpireAt(byte[] key, long millisecondsTimestamp, ExpiryOption expiryOption) {
    return appendCommand(commandObjects.pexpireAt(key, millisecondsTimestamp, expiryOption));
  }

  @Override
  public Response<Long> ttl(byte[] key) {
    return appendCommand(commandObjects.ttl(key));
  }

  @Override
  public Response<Long> pttl(byte[] key) {
    return appendCommand(commandObjects.pttl(key));
  }

  @Override
  public Response<Long> touch(byte[] key) {
    return appendCommand(commandObjects.touch(key));
  }

  @Override
  public Response<Long> touch(byte[]... keys) {
    return appendCommand(commandObjects.touch(keys));
  }

  @Override
  public Response<List<byte[]>> sort(byte[] key) {
    return appendCommand(commandObjects.sort(key));
  }

  @Override
  public Response<List<byte[]>> sort(byte[] key, SortingParams sortingParams) {
    return appendCommand(commandObjects.sort(key, sortingParams));
  }

  @Override
  public Response<List<byte[]>> sortReadonly(byte[] key, SortingParams sortingParams) {
    return appendCommand(commandObjects.sortReadonly(key, sortingParams));
  }

  @Override
  public Response<Long> del(byte[] key) {
    return appendCommand(commandObjects.del(key));
  }

  @Override
  public Response<Long> del(byte[]... keys) {
    return appendCommand(commandObjects.del(keys));
  }

  @Override
  public Response<Long> unlink(byte[] key) {
    return appendCommand(commandObjects.unlink(key));
  }

  @Override
  public Response<Long> unlink(byte[]... keys) {
    return appendCommand(commandObjects.unlink(keys));
  }

  @Override
  public Response<Boolean> copy(byte[] srcKey, byte[] dstKey, boolean replace) {
    return appendCommand(commandObjects.copy(srcKey, dstKey, replace));
  }

  @Override
  public Response<String> rename(byte[] oldkey, byte[] newkey) {
    return appendCommand(commandObjects.rename(oldkey, newkey));
  }

  @Override
  public Response<Long> renamenx(byte[] oldkey, byte[] newkey) {
    return appendCommand(commandObjects.renamenx(oldkey, newkey));
  }

  @Override
  public Response<Long> sort(byte[] key, SortingParams sortingParams, byte[] dstkey) {
    return appendCommand(commandObjects.sort(key, sortingParams, dstkey));
  }

  @Override
  public Response<Long> sort(byte[] key, byte[] dstkey) {
    return appendCommand(commandObjects.sort(key, dstkey));
  }

  @Override
  public Response<Long> memoryUsage(byte[] key) {
    return appendCommand(commandObjects.memoryUsage(key));
  }

  @Override
  public Response<Long> memoryUsage(byte[] key, int samples) {
    return appendCommand(commandObjects.memoryUsage(key, samples));
  }

  @Override
  public Response<Long> objectRefcount(byte[] key) {
    return appendCommand(commandObjects.objectRefcount(key));
  }

  @Override
  public Response<byte[]> objectEncoding(byte[] key) {
    return appendCommand(commandObjects.objectEncoding(key));
  }

  @Override
  public Response<Long> objectIdletime(byte[] key) {
    return appendCommand(commandObjects.objectIdletime(key));
  }

  @Override
  public Response<Long> objectFreq(byte[] key) {
    return appendCommand(commandObjects.objectFreq(key));
  }

  @Override
  public Response<String> migrate(String host, int port, byte[] key, int timeout) {
    return appendCommand(commandObjects.migrate(host, port, key, timeout));
  }

  @Override
  public Response<String> migrate(String host, int port, int timeout, MigrateParams params, byte[]... keys) {
    return appendCommand(commandObjects.migrate(host, port, timeout, params, keys));
  }

  @Override
  public Response<Set<byte[]>> keys(byte[] pattern) {
    return appendCommand(commandObjects.keys(pattern));
  }

  @Override
  public Response<ScanResult<byte[]>> scan(byte[] cursor) {
    return appendCommand(commandObjects.scan(cursor));
  }

  @Override
  public Response<ScanResult<byte[]>> scan(byte[] cursor, ScanParams params) {
    return appendCommand(commandObjects.scan(cursor, params));
  }

  @Override
  public Response<ScanResult<byte[]>> scan(byte[] cursor, ScanParams params, byte[] type) {
    return appendCommand(commandObjects.scan(cursor, params, type));
  }

  @Override
  public Response<byte[]> randomBinaryKey() {
    return appendCommand(commandObjects.randomBinaryKey());
  }

  @Override
  public Response<Long> rpush(byte[] key, byte[]... args) {
    return appendCommand(commandObjects.rpush(key, args));
  }

  @Override
  public Response<Long> lpush(byte[] key, byte[]... args) {
    return appendCommand(commandObjects.lpush(key, args));
  }

  @Override
  public Response<Long> llen(byte[] key) {
    return appendCommand(commandObjects.llen(key));
  }

  @Override
  public Response<List<byte[]>> lrange(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.lrange(key, start, stop));
  }

  @Override
  public Response<String> ltrim(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.ltrim(key, start, stop));
  }

  @Override
  public Response<byte[]> lindex(byte[] key, long index) {
    return appendCommand(commandObjects.lindex(key, index));
  }

  @Override
  public Response<String> lset(byte[] key, long index, byte[] value) {
    return appendCommand(commandObjects.lset(key, index, value));
  }

  @Override
  public Response<Long> lrem(byte[] key, long count, byte[] value) {
    return appendCommand(commandObjects.lrem(key, count, value));
  }

  @Override
  public Response<byte[]> lpop(byte[] key) {
    return appendCommand(commandObjects.lpop(key));
  }

  @Override
  public Response<List<byte[]>> lpop(byte[] key, int count) {
    return appendCommand(commandObjects.lpop(key, count));
  }

  @Override
  public Response<Long> lpos(byte[] key, byte[] element) {
    return appendCommand(commandObjects.lpos(key, element));
  }

  @Override
  public Response<Long> lpos(byte[] key, byte[] element, LPosParams params) {
    return appendCommand(commandObjects.lpos(key, element, params));
  }

  @Override
  public Response<List<Long>> lpos(byte[] key, byte[] element, LPosParams params, long count) {
    return appendCommand(commandObjects.lpos(key, element, params, count));
  }

  @Override
  public Response<byte[]> rpop(byte[] key) {
    return appendCommand(commandObjects.rpop(key));
  }

  @Override
  public Response<List<byte[]>> rpop(byte[] key, int count) {
    return appendCommand(commandObjects.rpop(key, count));
  }

  @Override
  public Response<Long> linsert(byte[] key, ListPosition where, byte[] pivot, byte[] value) {
    return appendCommand(commandObjects.linsert(key, where, pivot, value));
  }

  @Override
  public Response<Long> lpushx(byte[] key, byte[]... args) {
    return appendCommand(commandObjects.lpushx(key, args));
  }

  @Override
  public Response<Long> rpushx(byte[] key, byte[]... args) {
    return appendCommand(commandObjects.rpushx(key, args));
  }

  @Override
  public Response<List<byte[]>> blpop(int timeout, byte[]... keys) {
    return appendCommand(commandObjects.blpop(timeout, keys));
  }

  @Override
  public Response<List<byte[]>> blpop(double timeout, byte[]... keys) {
    return appendCommand(commandObjects.blpop(timeout, keys));
  }

  @Override
  public Response<List<byte[]>> brpop(int timeout, byte[]... keys) {
    return appendCommand(commandObjects.brpop(timeout, keys));
  }

  @Override
  public Response<List<byte[]>> brpop(double timeout, byte[]... keys) {
    return appendCommand(commandObjects.brpop(timeout, keys));
  }

  @Override
  public Response<byte[]> rpoplpush(byte[] srckey, byte[] dstkey) {
    return appendCommand(commandObjects.rpoplpush(srckey, dstkey));
  }

  @Override
  public Response<byte[]> brpoplpush(byte[] source, byte[] destination, int timeout) {
    return appendCommand(commandObjects.brpoplpush(source, destination, timeout));
  }

  @Override
  public Response<byte[]> lmove(byte[] srcKey, byte[] dstKey, ListDirection from, ListDirection to) {
    return appendCommand(commandObjects.lmove(srcKey, dstKey, from, to));
  }

  @Override
  public Response<byte[]> blmove(byte[] srcKey, byte[] dstKey, ListDirection from, ListDirection to, double timeout) {
    return appendCommand(commandObjects.blmove(srcKey, dstKey, from, to, timeout));
  }

  @Override
  public Response<KeyValue<byte[], List<byte[]>>> lmpop(ListDirection direction, byte[]... keys) {
    return appendCommand(commandObjects.lmpop(direction, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<byte[]>>> lmpop(ListDirection direction, int count, byte[]... keys) {
    return appendCommand(commandObjects.lmpop(direction, count, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<byte[]>>> blmpop(long timeout, ListDirection direction, byte[]... keys) {
    return appendCommand(commandObjects.blmpop(timeout, direction, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<byte[]>>> blmpop(long timeout, ListDirection direction, int count, byte[]... keys) {
    return appendCommand(commandObjects.blmpop(timeout, direction, count, keys));
  }

  public Response<Long> publish(byte[] channel, byte[] message) {
    return appendCommand(commandObjects.publish(channel, message));
  }

  public Response<LCSMatchResult> strAlgoLCSStrings(byte[] strA, byte[] strB, StrAlgoLCSParams params) {
    return appendCommand(commandObjects.strAlgoLCSStrings(strA, strB, params));
  }

  @Override
  public Response<Long> waitReplicas(byte[] sampleKey, int replicas, long timeout) {
    return appendCommand(commandObjects.waitReplicas(sampleKey, replicas, timeout));
  }

  @Override
  public Response<Object> eval(byte[] script, byte[] sampleKey) {
    return appendCommand(commandObjects.eval(script, sampleKey));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1, byte[] sampleKey) {
    return appendCommand(commandObjects.evalsha(sha1, sampleKey));
  }

  @Override
  public Response<List<Boolean>> scriptExists(byte[] sampleKey, byte[]... sha1s) {
    return appendCommand(commandObjects.scriptExists(sampleKey, sha1s));
  }

  @Override
  public Response<byte[]> scriptLoad(byte[] script, byte[] sampleKey) {
    return appendCommand(commandObjects.scriptLoad(script, sampleKey));
  }

  @Override
  public Response<String> scriptFlush(byte[] sampleKey) {
    return appendCommand(commandObjects.scriptFlush(sampleKey));
  }

  @Override
  public Response<String> scriptFlush(byte[] sampleKey, FlushMode flushMode) {
    return appendCommand(commandObjects.scriptFlush(sampleKey, flushMode));
  }

  @Override
  public Response<String> scriptKill(byte[] sampleKey) {
    return appendCommand(commandObjects.scriptKill(sampleKey));
  }

  @Override
  public Response<Object> eval(byte[] script) {
    return appendCommand(commandObjects.eval(script));
  }

  @Override
  public Response<Object> eval(byte[] script, int keyCount, byte[]... params) {
    return appendCommand(commandObjects.eval(script, keyCount, params));
  }

  @Override
  public Response<Object> eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
    return appendCommand(commandObjects.eval(script, keys, args));
  }

  @Override
  public Response<Object> evalReadonly(byte[] script, List<byte[]> keys, List<byte[]> args) {
    return appendCommand(commandObjects.evalReadonly(script, keys, args));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1) {
    return appendCommand(commandObjects.evalsha(sha1));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1, int keyCount, byte[]... params) {
    return appendCommand(commandObjects.evalsha(sha1, keyCount, params));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    return appendCommand(commandObjects.evalsha(sha1, keys, args));
  }

  @Override
  public Response<Object> evalshaReadonly(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    return appendCommand(commandObjects.evalshaReadonly(sha1, keys, args));
  }

  @Override
  public Response<Long> sadd(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.sadd(key, members));
  }

  @Override
  public Response<Set<byte[]>> smembers(byte[] key) {
    return appendCommand(commandObjects.smembers(key));
  }

  @Override
  public Response<Long> srem(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.srem(key, members));
  }

  @Override
  public Response<byte[]> spop(byte[] key) {
    return appendCommand(commandObjects.spop(key));
  }

  @Override
  public Response<Set<byte[]>> spop(byte[] key, long count) {
    return appendCommand(commandObjects.spop(key, count));
  }

  @Override
  public Response<Long> scard(byte[] key) {
    return appendCommand(commandObjects.scard(key));
  }

  @Override
  public Response<Boolean> sismember(byte[] key, byte[] member) {
    return appendCommand(commandObjects.sismember(key, member));
  }

  @Override
  public Response<List<Boolean>> smismember(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.smismember(key, members));
  }

  @Override
  public Response<byte[]> srandmember(byte[] key) {
    return appendCommand(commandObjects.srandmember(key));
  }

  @Override
  public Response<List<byte[]>> srandmember(byte[] key, int count) {
    return appendCommand(commandObjects.srandmember(key, count));
  }

  @Override
  public Response<ScanResult<byte[]>> sscan(byte[] key, byte[] cursor, ScanParams params) {
    return appendCommand(commandObjects.sscan(key, cursor, params));
  }

  @Override
  public Response<Set<byte[]>> sdiff(byte[]... keys) {
    return appendCommand(commandObjects.sdiff(keys));
  }

  @Override
  public Response<Long> sdiffstore(byte[] dstkey, byte[]... keys) {
    return appendCommand(commandObjects.sdiffstore(dstkey, keys));
  }

  @Override
  public Response<Set<byte[]>> sinter(byte[]... keys) {
    return appendCommand(commandObjects.sinter(keys));
  }

  @Override
  public Response<Long> sinterstore(byte[] dstkey, byte[]... keys) {
    return appendCommand(commandObjects.sinterstore(dstkey, keys));
  }

  @Override
  public Response<Long> sintercard(byte[]... keys){
    return appendCommand(commandObjects.sintercard(keys));
  }

  @Override
  public Response<Long> sintercard(int limit, byte[]... keys){
    return appendCommand(commandObjects.sintercard(limit, keys));
  }

  @Override
  public Response<Set<byte[]>> sunion(byte[]... keys) {
    return appendCommand(commandObjects.sunion(keys));
  }

  @Override
  public Response<Long> sunionstore(byte[] dstkey, byte[]... keys) {
    return appendCommand(commandObjects.sunionstore(dstkey, keys));
  }

  @Override
  public Response<Long> smove(byte[] srckey, byte[] dstkey, byte[] member) {
    return appendCommand(commandObjects.smove(srckey, dstkey, member));
  }

  @Override
  public Response<Long> zadd(byte[] key, double score, byte[] member) {
    return appendCommand(commandObjects.zadd(key, score, member));
  }

  @Override
  public Response<Long> zadd(byte[] key, double score, byte[] member, ZAddParams params) {
    return appendCommand(commandObjects.zadd(key, score, member, params));
  }

  @Override
  public Response<Long> zadd(byte[] key, Map<byte[], Double> scoreMembers) {
    return appendCommand(commandObjects.zadd(key, scoreMembers));
  }

  @Override
  public Response<Long> zadd(byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
    return appendCommand(commandObjects.zadd(key, scoreMembers, params));
  }

  @Override
  public Response<Double> zaddIncr(byte[] key, double score, byte[] member, ZAddParams params) {
    return appendCommand(commandObjects.zaddIncr(key, score, member, params));
  }

  @Override
  public Response<Long> zrem(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.zrem(key, members));
  }

  @Override
  public Response<Double> zincrby(byte[] key, double increment, byte[] member) {
    return appendCommand(commandObjects.zincrby(key, increment, member));
  }

  @Override
  public Response<Double> zincrby(byte[] key, double increment, byte[] member, ZIncrByParams params) {
    return appendCommand(commandObjects.zincrby(key, increment, member, params));
  }

  @Override
  public Response<Long> zrank(byte[] key, byte[] member) {
    return appendCommand(commandObjects.zrank(key, member));
  }

  @Override
  public Response<Long> zrevrank(byte[] key, byte[] member) {
    return appendCommand(commandObjects.zrevrank(key, member));
  }

  @Override
  public Response<List<byte[]>> zrange(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.zrange(key, start, stop));
  }

  @Override
  public Response<List<byte[]>> zrevrange(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.zrevrange(key, start, stop));
  }

  @Override
  public Response<List<Tuple>> zrangeWithScores(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.zrangeWithScores(key, start, stop));
  }

  @Override
  public Response<List<Tuple>> zrevrangeWithScores(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.zrevrangeWithScores(key, start, stop));
  }

  @Override
  public Response<List<byte[]>> zrange(byte[] key, ZRangeParams zRangeParams) {
    return appendCommand(commandObjects.zrange(key, zRangeParams));
  }

  @Override
  public Response<List<Tuple>> zrangeWithScores(byte[] key, ZRangeParams zRangeParams) {
    return appendCommand(commandObjects.zrangeWithScores(key, zRangeParams));
  }

  @Override
  public Response<Long> zrangestore(byte[] dest, byte[] src, ZRangeParams zRangeParams) {
    return appendCommand(commandObjects.zrangestore(dest, src, zRangeParams));
  }

  @Override
  public Response<byte[]> zrandmember(byte[] key) {
    return appendCommand(commandObjects.zrandmember(key));
  }

  @Override
  public Response<List<byte[]>> zrandmember(byte[] key, long count) {
    return appendCommand(commandObjects.zrandmember(key, count));
  }

  @Override
  public Response<List<Tuple>> zrandmemberWithScores(byte[] key, long count) {
    return appendCommand(commandObjects.zrandmemberWithScores(key, count));
  }

  @Override
  public Response<Long> zcard(byte[] key) {
    return appendCommand(commandObjects.zcard(key));
  }

  @Override
  public Response<Double> zscore(byte[] key, byte[] member) {
    return appendCommand(commandObjects.zscore(key, member));
  }

  @Override
  public Response<List<Double>> zmscore(byte[] key, byte[]... members) {
    return appendCommand(commandObjects.zmscore(key, members));
  }

  @Override
  public Response<Tuple> zpopmax(byte[] key) {
    return appendCommand(commandObjects.zpopmax(key));
  }

  @Override
  public Response<List<Tuple>> zpopmax(byte[] key, int count) {
    return appendCommand(commandObjects.zpopmax(key, count));
  }

  @Override
  public Response<Tuple> zpopmin(byte[] key) {
    return appendCommand(commandObjects.zpopmin(key));
  }

  @Override
  public Response<List<Tuple>> zpopmin(byte[] key, int count) {
    return appendCommand(commandObjects.zpopmin(key, count));
  }

  @Override
  public Response<Long> zcount(byte[] key, double min, double max) {
    return appendCommand(commandObjects.zcount(key, min, max));
  }

  @Override
  public Response<Long> zcount(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zcount(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrangeByScore(byte[] key, double min, double max) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrevrangeByScore(byte[] key, double max, double min) {
    return appendCommand(commandObjects.zrevrangeByScore(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrangeByScore(byte[] key, double min, double max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<List<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
    return appendCommand(commandObjects.zrevrangeByScore(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<List<byte[]>> zrevrangeByScore(byte[] key, double max, double min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<List<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<List<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<Long> zremrangeByRank(byte[] key, long start, long stop) {
    return appendCommand(commandObjects.zremrangeByRank(key, start, stop));
  }

  @Override
  public Response<Long> zremrangeByScore(byte[] key, double min, double max) {
    return appendCommand(commandObjects.zremrangeByScore(key, min, max));
  }

  @Override
  public Response<Long> zremrangeByScore(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zremrangeByScore(key, min, max));
  }

  @Override
  public Response<Long> zlexcount(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zlexcount(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zrangeByLex(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max, int offset, int count) {
    return appendCommand(commandObjects.zrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Response<List<byte[]>> zrevrangeByLex(byte[] key, byte[] max, byte[] min) {
    return appendCommand(commandObjects.zrevrangeByLex(key, min, max));
  }

  @Override
  public Response<List<byte[]>> zrevrangeByLex(byte[] key, byte[] max, byte[] min, int offset, int count) {
    return appendCommand(commandObjects.zrevrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Response<Long> zremrangeByLex(byte[] key, byte[] min, byte[] max) {
    return appendCommand(commandObjects.zremrangeByLex(key, min, max));
  }

  @Override
  public Response<ScanResult<Tuple>> zscan(byte[] key, byte[] cursor, ScanParams params) {
    return appendCommand(commandObjects.zscan(key, cursor, params));
  }

  @Override
  public Response<List<byte[]>> bzpopmax(double timeout, byte[]... keys) {
    return appendCommand(commandObjects.bzpopmax(timeout, keys));
  }

  @Override
  public Response<List<byte[]>> bzpopmin(double timeout, byte[]... keys) {
    return appendCommand(commandObjects.bzpopmin(timeout, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<Tuple>>> zmpop(SortedSetOption option, byte[]... keys) {
    return appendCommand(commandObjects.zmpop(option, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<Tuple>>> zmpop(SortedSetOption option, int count, byte[]... keys) {
    return appendCommand(commandObjects.zmpop(option, count, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<Tuple>>> bzmpop(long timeout, SortedSetOption option, byte[]... keys) {
    return appendCommand(commandObjects.bzmpop(timeout, option, keys));
  }

  @Override
  public Response<KeyValue<byte[], List<Tuple>>> bzmpop(long timeout, SortedSetOption option, int count, byte[]... keys) {
    return appendCommand(commandObjects.bzmpop(timeout, option, count, keys));
  }

  @Override
  public Response<Set<byte[]>> zdiff(byte[]... keys) {
    return appendCommand(commandObjects.zdiff(keys));
  }

  @Override
  public Response<Set<Tuple>> zdiffWithScores(byte[]... keys) {
    return appendCommand(commandObjects.zdiffWithScores(keys));
  }

  @Override
  public Response<Long> zdiffStore(byte[] dstkey, byte[]... keys) {
    return appendCommand(commandObjects.zdiffStore(dstkey, keys));
  }

  @Override
  public Response<Set<byte[]>> zinter(ZParams params, byte[]... keys) {
    return appendCommand(commandObjects.zinter(params, keys));
  }

  @Override
  public Response<Set<Tuple>> zinterWithScores(ZParams params, byte[]... keys) {
    return appendCommand(commandObjects.zinterWithScores(params, keys));
  }

  @Override
  public Response<Long> zinterstore(byte[] dstkey, byte[]... sets) {
    return appendCommand(commandObjects.zinterstore(dstkey, sets));
  }

  @Override
  public Response<Long> zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
    return appendCommand(commandObjects.zinterstore(dstkey, params, sets));
  }

  @Override
  public Response<Long> zintercard(byte[]... keys) {
    return appendCommand(commandObjects.zintercard(keys));
  }

  @Override
  public Response<Long> zintercard(long limit, byte[]... keys) {
    return appendCommand(commandObjects.zintercard(limit, keys));
  }

  @Override
  public Response<Set<byte[]>> zunion(ZParams params, byte[]... keys) {
    return appendCommand(commandObjects.zunion(params, keys));
  }

  @Override
  public Response<Set<Tuple>> zunionWithScores(ZParams params, byte[]... keys) {
    return appendCommand(commandObjects.zunionWithScores(params, keys));
  }

  @Override
  public Response<Long> zunionstore(byte[] dstkey, byte[]... sets) {
    return appendCommand(commandObjects.zunionstore(dstkey, sets));
  }

  @Override
  public Response<Long> zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
    return appendCommand(commandObjects.zunionstore(dstkey, params, sets));
  }

  @Override
  public Response<byte[]> xadd(byte[] key, XAddParams params, Map<byte[], byte[]> hash) {
    return appendCommand(commandObjects.xadd(key, params, hash));
  }

  @Override
  public Response<Long> xlen(byte[] key) {
    return appendCommand(commandObjects.xlen(key));
  }

  @Override
  public Response<List<byte[]>> xrange(byte[] key, byte[] start, byte[] end) {
    return appendCommand(commandObjects.xrange(key, start, end));
  }

  @Override
  public Response<List<byte[]>> xrange(byte[] key, byte[] start, byte[] end, int count) {
    return appendCommand(commandObjects.xrange(key, start, end, count));
  }

  @Override
  public Response<List<byte[]>> xrevrange(byte[] key, byte[] end, byte[] start) {
    return appendCommand(commandObjects.xrevrange(key, end, start));
  }

  @Override
  public Response<List<byte[]>> xrevrange(byte[] key, byte[] end, byte[] start, int count) {
    return appendCommand(commandObjects.xrevrange(key, end, start, count));
  }

  @Override
  public Response<Long> xack(byte[] key, byte[] group, byte[]... ids) {
    return appendCommand(commandObjects.xack(key, group, ids));
  }

  @Override
  public Response<String> xgroupCreate(byte[] key, byte[] groupName, byte[] id, boolean makeStream) {
    return appendCommand(commandObjects.xgroupCreate(key, groupName, id, makeStream));
  }

  @Override
  public Response<String> xgroupSetID(byte[] key, byte[] groupName, byte[] id) {
    return appendCommand(commandObjects.xgroupSetID(key, groupName, id));
  }

  @Override
  public Response<Long> xgroupDestroy(byte[] key, byte[] groupName) {
    return appendCommand(commandObjects.xgroupDestroy(key, groupName));
  }

  @Override
  public Response<Boolean> xgroupCreateConsumer(byte[] key, byte[] groupName, byte[] consumerName) {
    return appendCommand(commandObjects.xgroupCreateConsumer(key, groupName, consumerName));
  }

  @Override
  public Response<Long> xgroupDelConsumer(byte[] key, byte[] groupName, byte[] consumerName) {
    return appendCommand(commandObjects.xgroupDelConsumer(key, groupName, consumerName));
  }

  @Override
  public Response<Long> xdel(byte[] key, byte[]... ids) {
    return appendCommand(commandObjects.xdel(key, ids));
  }

  @Override
  public Response<Long> xtrim(byte[] key, long maxLen, boolean approximateLength) {
    return appendCommand(commandObjects.xtrim(key, maxLen, approximateLength));
  }

  @Override
  public Response<Long> xtrim(byte[] key, XTrimParams params) {
    return appendCommand(commandObjects.xtrim(key, params));
  }

  @Override
  public Response<Object> xpending(byte[] key, byte[] groupName) {
    return appendCommand(commandObjects.xpending(key, groupName));
  }

  @Override
  public Response<List<Object>> xpending(byte[] key, byte[] groupName, byte[] start, byte[] end, int count, byte[] consumerName) {
    return appendCommand(commandObjects.xpending(key, groupName, start, end, count, consumerName));
  }

  @Override
  public Response<List<Object>> xpending(byte[] key, byte[] groupName, XPendingParams params) {
    return appendCommand(commandObjects.xpending(key, groupName, params));
  }

  @Override
  public Response<List<byte[]>> xclaim(byte[] key, byte[] group, byte[] consumerName, long minIdleTime, XClaimParams params, byte[]... ids) {
    return appendCommand(commandObjects.xclaim(key, group, consumerName, minIdleTime, params, ids));
  }

  @Override
  public Response<List<byte[]>> xclaimJustId(byte[] key, byte[] group, byte[] consumerName, long minIdleTime, XClaimParams params, byte[]... ids) {
    return appendCommand(commandObjects.xclaimJustId(key, group, consumerName, minIdleTime, params, ids));
  }

  @Override
  public Response<List<Object>> xautoclaim(byte[] key, byte[] groupName, byte[] consumerName, long minIdleTime, byte[] start, XAutoClaimParams params) {
    return appendCommand(commandObjects.xautoclaim(key, groupName, consumerName, minIdleTime, start, params));
  }

  @Override
  public Response<List<Object>> xautoclaimJustId(byte[] key, byte[] groupName, byte[] consumerName, long minIdleTime, byte[] start, XAutoClaimParams params) {
    return appendCommand(commandObjects.xautoclaimJustId(key, groupName, consumerName, minIdleTime, start, params));
  }

  @Override
  public Response<Object> xinfoStream(byte[] key) {
    return appendCommand(commandObjects.xinfoStream(key));
  }

  @Override
  public Response<Object> xinfoStreamFull(byte[] key) {
    return appendCommand(commandObjects.xinfoStreamFull(key));
  }

  @Override
  public Response<Object> xinfoStreamFull(byte[] key, int count) {
    return appendCommand(commandObjects.xinfoStreamFull(key, count));
  }

  @Override
  @Deprecated
  public Response<List<Object>> xinfoGroup(byte[] key) {
    return appendCommand(commandObjects.xinfoGroup(key));
  }

  @Override
  public Response<List<Object>> xinfoGroups(byte[] key) {
    return appendCommand(commandObjects.xinfoGroups(key));
  }

  @Override
  public Response<List<Object>> xinfoConsumers(byte[] key, byte[] group) {
    return appendCommand(commandObjects.xinfoConsumers(key, group));
  }

  @Override
  public Response<List<byte[]>> xread(XReadParams xReadParams, Map.Entry<byte[], byte[]>... streams) {
    return appendCommand(commandObjects.xread(xReadParams, streams));
  }

  @Override
  public Response<List<byte[]>> xreadGroup(byte[] groupName, byte[] consumer, XReadGroupParams xReadGroupParams, Map.Entry<byte[], byte[]>... streams) {
    return appendCommand(commandObjects.xreadGroup(groupName, consumer, xReadGroupParams, streams));
  }

  @Override
  public Response<String> set(byte[] key, byte[] value) {
    return appendCommand(commandObjects.set(key, value));
  }

  @Override
  public Response<String> set(byte[] key, byte[] value, SetParams params) {
    return appendCommand(commandObjects.set(key, value, params));
  }

  @Override
  public Response<byte[]> get(byte[] key) {
    return appendCommand(commandObjects.get(key));
  }

  @Override
  public Response<byte[]> getDel(byte[] key) {
    return appendCommand(commandObjects.getDel(key));
  }

  @Override
  public Response<byte[]> getEx(byte[] key, GetExParams params) {
    return appendCommand(commandObjects.getEx(key, params));
  }

  @Override
  public Response<Boolean> setbit(byte[] key, long offset, boolean value) {
    return appendCommand(commandObjects.setbit(key, offset, value));
  }

  @Override
  public Response<Boolean> getbit(byte[] key, long offset) {
    return appendCommand(commandObjects.getbit(key, offset));
  }

  @Override
  public Response<Long> setrange(byte[] key, long offset, byte[] value) {
    return appendCommand(commandObjects.setrange(key, offset, value));
  }

  @Override
  public Response<byte[]> getrange(byte[] key, long startOffset, long endOffset) {
    return appendCommand(commandObjects.getrange(key, startOffset, endOffset));
  }

  @Override
  public Response<byte[]> getSet(byte[] key, byte[] value) {
    return appendCommand(commandObjects.getSet(key, value));
  }

  @Override
  public Response<Long> setnx(byte[] key, byte[] value) {
    return appendCommand(commandObjects.setnx(key, value));
  }

  @Override
  public Response<String> setex(byte[] key, long seconds, byte[] value) {
    return appendCommand(commandObjects.setex(key, seconds, value));
  }

  @Override
  public Response<String> psetex(byte[] key, long milliseconds, byte[] value) {
    return appendCommand(commandObjects.psetex(key, milliseconds, value));
  }

  @Override
  public Response<List<byte[]>> mget(byte[]... keys) {
    return appendCommand(commandObjects.mget(keys));
  }

  @Override
  public Response<String> mset(byte[]... keysvalues) {
    return appendCommand(commandObjects.mset(keysvalues));
  }

  @Override
  public Response<Long> msetnx(byte[]... keysvalues) {
    return appendCommand(commandObjects.msetnx(keysvalues));
  }

  @Override
  public Response<Long> incr(byte[] key) {
    return appendCommand(commandObjects.incr(key));
  }

  @Override
  public Response<Long> incrBy(byte[] key, long increment) {
    return appendCommand(commandObjects.incrBy(key, increment));
  }

  @Override
  public Response<Double> incrByFloat(byte[] key, double increment) {
    return appendCommand(commandObjects.incrByFloat(key, increment));
  }

  @Override
  public Response<Long> decr(byte[] key) {
    return appendCommand(commandObjects.decr(key));
  }

  @Override
  public Response<Long> decrBy(byte[] key, long decrement) {
    return appendCommand(commandObjects.decrBy(key, decrement));
  }

  @Override
  public Response<Long> append(byte[] key, byte[] value) {
    return appendCommand(commandObjects.append(key, value));
  }

  @Override
  public Response<byte[]> substr(byte[] key, int start, int end) {
    return appendCommand(commandObjects.substr(key, start, end));
  }

  @Override
  public Response<Long> strlen(byte[] key) {
    return appendCommand(commandObjects.strlen(key));
  }

  @Override
  public Response<Long> bitcount(byte[] key) {
    return appendCommand(commandObjects.bitcount(key));
  }

  @Override
  public Response<Long> bitcount(byte[] key, long start, long end) {
    return appendCommand(commandObjects.bitcount(key, start, end));
  }

  @Override
  public Response<Long> bitcount(byte[] key, long start, long end, BitCountOption option) {
    return appendCommand(commandObjects.bitcount(key, start, end, option));
  }

  @Override
  public Response<Long> bitpos(byte[] key, boolean value) {
    return appendCommand(commandObjects.bitpos(key, value));
  }

  @Override
  public Response<Long> bitpos(byte[] key, boolean value, BitPosParams params) {
    return appendCommand(commandObjects.bitpos(key, value, params));
  }

  @Override
  public Response<List<Long>> bitfield(byte[] key, byte[]... arguments) {
    return appendCommand(commandObjects.bitfield(key, arguments));
  }

  @Override
  public Response<List<Long>> bitfieldReadonly(byte[] key, byte[]... arguments) {
    return appendCommand(commandObjects.bitfieldReadonly(key, arguments));
  }

  @Override
  public Response<Long> bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
    return appendCommand(commandObjects.bitop(op, destKey, srcKeys));
  }

  @Override
  public Response<LCSMatchResult> strAlgoLCSKeys(byte[] keyA, byte[] keyB, StrAlgoLCSParams params) {
    return appendCommand(commandObjects.strAlgoLCSKeys(keyA, keyB, params));
  }

  // RediSearch commands
  @Override
  public Response<String> ftAlter(String indexName, Schema schema) {
    return appendCommand(commandObjects.ftAlter(indexName, schema));
  }

  @Override
  public Response<SearchResult> ftSearch(String indexName, Query query) {
    return appendCommand(commandObjects.ftSearch(indexName, query));
  }

  @Override
  public Response<SearchResult> ftSearch(byte[] indexName, Query query) {
    return appendCommand(commandObjects.ftSearch(indexName, query));
  }

  @Override
  public Response<String> ftExplain(String indexName, Query query) {
    return appendCommand(commandObjects.ftExplain(indexName, query));
  }

  @Override
  public Response<List<String>> ftExplainCLI(String indexName, Query query) {
    return appendCommand(commandObjects.ftExplainCLI(indexName, query));
  }

  @Override
  public Response<AggregationResult> ftAggregate(String indexName, AggregationBuilder aggr) {
    return appendCommand(commandObjects.ftAggregate(indexName, aggr));
  }

  @Override
  public Response<AggregationResult> ftCursorRead(String indexName, long cursorId, int count) {
    return appendCommand(commandObjects.ftCursorRead(indexName, cursorId, count));
  }

  @Override
  public Response<String> ftCursorDel(String indexName, long cursorId) {
    return appendCommand(commandObjects.ftCursorDel(indexName, cursorId));
  }

  @Override
  public Response<String> ftDropIndex(String indexName) {
    return appendCommand(commandObjects.ftDropIndex(indexName));
  }

  @Override
  public Response<String> ftDropIndexDD(String indexName) {
    return appendCommand(commandObjects.ftDropIndexDD(indexName));
  }

  @Override
  public Response<String> ftSynUpdate(String indexName, String synonymGroupId, String... terms) {
    return appendCommand(commandObjects.ftSynUpdate(indexName, synonymGroupId, terms));
  }

  @Override
  public Response<Map<String, List<String>>> ftSynDump(String indexName) {
    return appendCommand(commandObjects.ftSynDump(indexName));
  }

  @Override
  public Response<Map<String, Object>> ftInfo(String indexName) {
    return appendCommand(commandObjects.ftInfo(indexName));
  }

  @Override
  public Response<String> ftAliasAdd(String aliasName, String indexName) {
    return appendCommand(commandObjects.ftAliasAdd(aliasName, indexName));
  }

  @Override
  public Response<String> ftAliasUpdate(String aliasName, String indexName) {
    return appendCommand(commandObjects.ftAliasUpdate(aliasName, indexName));
  }

  @Override
  public Response<String> ftAliasDel(String aliasName) {
    return appendCommand(commandObjects.ftAliasDel(aliasName));
  }

  @Override
  public Response<Map<String, String>> ftConfigGet(String option) {
    return appendCommand(commandObjects.ftConfigGet(option));
  }

  @Override
  public Response<Map<String, String>> ftConfigGet(String indexName, String option) {
    return appendCommand(commandObjects.ftConfigGet(indexName, option));
  }

  @Override
  public Response<String> ftConfigSet(String option, String value) {
    return appendCommand(commandObjects.ftConfigSet(option, value));
  }

  @Override
  public Response<String> ftConfigSet(String indexName, String option, String value) {
    return appendCommand(commandObjects.ftConfigSet(indexName, option, value));
  }
  // RediSearch commands

  // RedisJSON commands
  @Override
  public Response<LCSMatchResult> lcs(byte[] keyA, byte[] keyB, LCSParams params) {
    return appendCommand(commandObjects.lcs(keyA, keyB, params));
  }

  @Override
  public Response<String> jsonSet(String key, Path2 path, Object object) {
    return appendCommand(commandObjects.jsonSet(key, path, object));
  }

  @Override
  public Response<String> jsonSetWithEscape(String key, Path2 path, Object object) {
    return appendCommand(commandObjects.jsonSetWithEscape(key, path, object));
  }

  @Override
  public Response<String> jsonSet(String key, Path path, Object object) {
    return appendCommand(commandObjects.jsonSet(key, path, object));
  }

  @Override
  public Response<String> jsonSet(String key, Path2 path, Object object, JsonSetParams params) {
    return appendCommand(commandObjects.jsonSet(key, path, object, params));
  }

  @Override
  public Response<String> jsonSetWithEscape(String key, Path2 path, Object object, JsonSetParams params) {
    return appendCommand(commandObjects.jsonSetWithEscape(key, path, object, params));
  }

  @Override
  public Response<String> jsonSet(String key, Path path, Object object, JsonSetParams params) {
    return appendCommand(commandObjects.jsonSet(key, path, object, params));
  }

  @Override
  public Response<Object> jsonGet(String key) {
    return appendCommand(commandObjects.jsonGet(key));
  }

  @Override
  public <T> Response<T> jsonGet(String key, Class<T> clazz) {
    return appendCommand(commandObjects.jsonGet(key, clazz));
  }

  @Override
  public Response<Object> jsonGet(String key, Path2... paths) {
    return appendCommand(commandObjects.jsonGet(key, paths));
  }

  @Override
  public Response<Object> jsonGet(String key, Path... paths) {
    return appendCommand(commandObjects.jsonGet(key, paths));
  }

  @Override
  public <T> Response<T> jsonGet(String key, Class<T> clazz, Path... paths) {
    return appendCommand(commandObjects.jsonGet(key, clazz, paths));
  }

  @Override
  public Response<List<JSONArray>> jsonMGet(Path2 path, String... keys) {
    return appendCommand(commandObjects.jsonMGet(path, keys));
  }

  @Override
  public <T> Response<List<T>> jsonMGet(Path path, Class<T> clazz, String... keys) {
    return appendCommand(commandObjects.jsonMGet(path, clazz, keys));
  }

  @Override
  public Response<Long> jsonDel(String key) {
    return appendCommand(commandObjects.jsonDel(key));
  }

  @Override
  public Response<Long> jsonDel(String key, Path2 path) {
    return appendCommand(commandObjects.jsonDel(key, path));
  }

  @Override
  public Response<Long> jsonDel(String key, Path path) {
    return appendCommand(commandObjects.jsonDel(key, path));
  }

  @Override
  public Response<Long> jsonClear(String key) {
    return appendCommand(commandObjects.jsonClear(key));
  }

  @Override
  public Response<Long> jsonClear(String key, Path2 path) {
    return appendCommand(commandObjects.jsonClear(key, path));
  }

  @Override
  public Response<Long> jsonClear(String key, Path path) {
    return appendCommand(commandObjects.jsonClear(key, path));
  }

  @Override
  public Response<List<Boolean>> jsonToggle(String key, Path2 path) {
    return appendCommand(commandObjects.jsonToggle(key, path));
  }

  @Override
  public Response<String> jsonToggle(String key, Path path) {
    return appendCommand(commandObjects.jsonToggle(key, path));
  }

  @Override
  public Response<Class<?>> jsonType(String key) {
    return appendCommand(commandObjects.jsonType(key));
  }

  @Override
  public Response<List<Class<?>>> jsonType(String key, Path2 path) {
    return appendCommand(commandObjects.jsonType(key, path));
  }

  @Override
  public Response<Class<?>> jsonType(String key, Path path) {
    return appendCommand(commandObjects.jsonType(key, path));
  }

  @Override
  public Response<Long> jsonStrAppend(String key, Object string) {
    return appendCommand(commandObjects.jsonStrAppend(key, string));
  }

  @Override
  public Response<List<Long>> jsonStrAppend(String key, Path2 path, Object string) {
    return appendCommand(commandObjects.jsonStrAppend(key, path, string));
  }

  @Override
  public Response<Long> jsonStrAppend(String key, Path path, Object string) {
    return appendCommand(commandObjects.jsonStrAppend(key, path, string));
  }

  @Override
  public Response<Long> jsonStrLen(String key) {
    return appendCommand(commandObjects.jsonStrLen(key));
  }

  @Override
  public Response<List<Long>> jsonStrLen(String key, Path2 path) {
    return appendCommand(commandObjects.jsonStrLen(key, path));
  }

  @Override
  public Response<Long> jsonStrLen(String key, Path path) {
    return appendCommand(commandObjects.jsonStrLen(key, path));
  }

  @Override
  public Response<List<Long>> jsonArrAppend(String key, Path2 path, Object... objects) {
    return appendCommand(commandObjects.jsonArrAppend(key, path, objects));
  }

  @Override
  public Response<List<Long>> jsonArrAppendWithEscape(String key, Path2 path, Object... objects) {
    return appendCommand(commandObjects.jsonArrAppendWithEscape(key, path, objects));
  }

  @Override
  public Response<Long> jsonArrAppend(String key, Path path, Object... objects) {
    return appendCommand(commandObjects.jsonArrAppend(key, path, objects));
  }

  @Override
  public Response<List<Long>> jsonArrIndex(String key, Path2 path, Object scalar) {
    return appendCommand(commandObjects.jsonArrIndex(key, path, scalar));
  }

  @Override
  public Response<List<Long>> jsonArrIndexWithEscape(String key, Path2 path, Object scalar) {
    return appendCommand(commandObjects.jsonArrIndexWithEscape(key, path, scalar));
  }

  @Override
  public Response<Long> jsonArrIndex(String key, Path path, Object scalar) {
    return appendCommand(commandObjects.jsonArrIndex(key, path, scalar));
  }

  @Override
  public Response<List<Long>> jsonArrInsert(String key, Path2 path, int index, Object... objects) {
    return appendCommand(commandObjects.jsonArrInsert(key, path, index, objects));
  }

  @Override
  public Response<List<Long>> jsonArrInsertWithEscape(String key, Path2 path, int index, Object... objects) {
    return appendCommand(commandObjects.jsonArrInsertWithEscape(key, path, index, objects));
  }

  @Override
  public Response<Long> jsonArrInsert(String key, Path path, int index, Object... pojos) {
    return appendCommand(commandObjects.jsonArrInsert(key, path, index, pojos));
  }

  @Override
  public Response<Object> jsonArrPop(String key) {
    return appendCommand(commandObjects.jsonArrPop(key));
  }

  @Override
  public Response<Long> jsonArrLen(String key, Path path) {
    return appendCommand(commandObjects.jsonArrLen(key, path));
  }

  @Override
  public Response<List<Long>> jsonArrTrim(String key, Path2 path, int start, int stop) {
    return appendCommand(commandObjects.jsonArrTrim(key, path, start, stop));
  }

  @Override
  public Response<Long> jsonArrTrim(String key, Path path, int start, int stop) {
    return appendCommand(commandObjects.jsonArrTrim(key, path, start, stop));
  }

  @Override
  public <T> Response<T> jsonArrPop(String key, Class<T> clazz, Path path) {
    return appendCommand(commandObjects.jsonArrPop(key, clazz, path));
  }

  @Override
  public Response<List<Object>> jsonArrPop(String key, Path2 path, int index) {
    return appendCommand(commandObjects.jsonArrPop(key, path, index));
  }

  @Override
  public Response<Object> jsonArrPop(String key, Path path, int index) {
    return appendCommand(commandObjects.jsonArrPop(key, path, index));
  }

  @Override
  public <T> Response<T> jsonArrPop(String key, Class<T> clazz, Path path, int index) {
    return appendCommand(commandObjects.jsonArrPop(key, clazz, path, index));
  }

  @Override
  public Response<Long> jsonArrLen(String key) {
    return appendCommand(commandObjects.jsonArrLen(key));
  }

  @Override
  public Response<List<Long>> jsonArrLen(String key, Path2 path) {
    return appendCommand(commandObjects.jsonArrLen(key, path));
  }

  @Override
  public <T> Response<T> jsonArrPop(String key, Class<T> clazz) {
    return appendCommand(commandObjects.jsonArrPop(key, clazz));
  }

  @Override
  public Response<List<Object>> jsonArrPop(String key, Path2 path) {
    return appendCommand(commandObjects.jsonArrPop(key, path));
  }

  @Override
  public Response<Object> jsonArrPop(String key, Path path) {
    return appendCommand(commandObjects.jsonArrPop(key, path));
  }

  @Override
  public Response<String> ftCreate(String indexName, IndexOptions indexOptions, Schema schema) {
    return appendCommand(commandObjects.ftCreate(indexName, indexOptions, schema));
  }
  // RedisJSON commands

  // RedisTimeSeries commands
  @Override
  public Response<String> tsCreate(String key) {
    return appendCommand(commandObjects.tsCreate(key));
  }

  @Override
  public Response<String> tsCreate(String key, TSCreateParams createParams) {
    return appendCommand(commandObjects.tsCreate(key, createParams));
  }

  @Override
  public Response<Long> tsDel(String key, long fromTimestamp, long toTimestamp) {
    return appendCommand(commandObjects.tsDel(key, fromTimestamp, toTimestamp));
  }

  @Override
  public Response<String> tsAlter(String key, TSAlterParams alterParams) {
    return appendCommand(commandObjects.tsAlter(key, alterParams));
  }

  @Override
  public Response<Long> tsAdd(String key, double value) {
    return appendCommand(commandObjects.tsAdd(key, value));
  }

  @Override
  public Response<Long> tsAdd(String key, long timestamp, double value) {
    return appendCommand(commandObjects.tsAdd(key, timestamp, value));
  }

  @Override
  public Response<Long> tsAdd(String key, long timestamp, double value, TSCreateParams createParams) {
    return appendCommand(commandObjects.tsAdd(key, timestamp, value, createParams));
  }

  @Override
  public Response<List<TSElement>> tsRange(String key, long fromTimestamp, long toTimestamp) {
    return appendCommand(commandObjects.tsRange(key, fromTimestamp, toTimestamp));
  }

  @Override
  public Response<List<TSElement>> tsRange(String key, TSRangeParams rangeParams) {
    return appendCommand(commandObjects.tsRange(key, rangeParams));
  }

  @Override
  public Response<List<TSElement>> tsRevRange(String key, long fromTimestamp, long toTimestamp) {
    return appendCommand(commandObjects.tsRevRange(key, fromTimestamp, toTimestamp));
  }

  @Override
  public Response<List<TSElement>> tsRevRange(String key, TSRangeParams rangeParams) {
    return appendCommand(commandObjects.tsRevRange(key, rangeParams));
  }

  @Override
  public Response<List<TSKeyedElements>> tsMRange(long fromTimestamp, long toTimestamp, String... filters) {
    return appendCommand(commandObjects.tsMRange(fromTimestamp, toTimestamp, filters));
  }

  @Override
  public Response<List<TSKeyedElements>> tsMRange(TSMRangeParams multiRangeParams) {
    return appendCommand(commandObjects.tsMRange(multiRangeParams));
  }

  @Override
  public Response<List<TSKeyedElements>> tsMRevRange(long fromTimestamp, long toTimestamp, String... filters) {
    return appendCommand(commandObjects.tsMRevRange(fromTimestamp, toTimestamp, filters));
  }

  @Override
  public Response<List<TSKeyedElements>> tsMRevRange(TSMRangeParams multiRangeParams) {
    return appendCommand(commandObjects.tsMRevRange(multiRangeParams));
  }

  @Override
  public Response<TSElement> tsGet(String key) {
    return appendCommand(commandObjects.tsGet(key));
  }

  @Override
  public Response<List<TSKeyValue<TSElement>>> tsMGet(TSMGetParams multiGetParams, String... filters) {
    return appendCommand(commandObjects.tsMGet(multiGetParams, filters));
  }

  @Override
  public Response<String> tsCreateRule(String sourceKey, String destKey, AggregationType aggregationType, long timeBucket) {
    return appendCommand(commandObjects.tsCreateRule(sourceKey, destKey, aggregationType, timeBucket));
  }

  @Override
  public Response<String> tsDeleteRule(String sourceKey, String destKey) {
    return appendCommand(commandObjects.tsDeleteRule(sourceKey, destKey));
  }

  @Override
  public Response<List<String>> tsQueryIndex(String... filters) {
    return appendCommand(commandObjects.tsQueryIndex(filters));
  }
  // RedisTimeSeries commands

  // RedisBloom commands
  @Override
  public Response<String> bfReserve(String key, double errorRate, long capacity) {
    return appendCommand(commandObjects.bfReserve(key, errorRate, capacity));
  }

  @Override
  public Response<String> bfReserve(String key, double errorRate, long capacity, BFReserveParams reserveParams) {
    return appendCommand(commandObjects.bfReserve(key, errorRate, capacity, reserveParams));
  }

  @Override
  public Response<Boolean> bfAdd(String key, String item) {
    return appendCommand(commandObjects.bfAdd(key, item));
  }

  @Override
  public Response<List<Boolean>> bfMAdd(String key, String... items) {
    return appendCommand(commandObjects.bfMAdd(key, items));
  }

  @Override
  public Response<List<Boolean>> bfInsert(String key, String... items) {
    return appendCommand(commandObjects.bfInsert(key, items));
  }

  @Override
  public Response<List<Boolean>> bfInsert(String key, BFInsertParams insertParams, String... items) {
    return appendCommand(commandObjects.bfInsert(key, insertParams, items));
  }

  @Override
  public Response<Boolean> bfExists(String key, String item) {
    return appendCommand(commandObjects.bfExists(key, item));
  }

  @Override
  public Response<List<Boolean>> bfMExists(String key, String... items) {
    return appendCommand(commandObjects.bfMExists(key, items));
  }

  @Override
  public Response<Map<String, Object>> bfInfo(String key) {
    return appendCommand(commandObjects.bfInfo(key));
  }

  @Override
  public Response<String> cfReserve(String key, long capacity) {
    return appendCommand(commandObjects.cfReserve(key, capacity));
  }

  @Override
  public Response<String> cfReserve(String key, long capacity, CFReserveParams reserveParams) {
    return appendCommand(commandObjects.cfReserve(key, capacity, reserveParams));
  }

  @Override
  public Response<Boolean> cfAdd(String key, String item) {
    return appendCommand(commandObjects.cfAdd(key, item));
  }

  @Override
  public Response<Boolean> cfAddNx(String key, String item) {
    return appendCommand(commandObjects.cfAddNx(key, item));
  }

  @Override
  public Response<List<Boolean>> cfInsert(String key, String... items) {
    return appendCommand(commandObjects.cfInsert(key, items));
  }

  @Override
  public Response<List<Boolean>> cfInsert(String key, CFInsertParams insertParams, String... items) {
    return appendCommand(commandObjects.cfInsert(key, insertParams, items));
  }

  @Override
  public Response<List<Boolean>> cfInsertNx(String key, String... items) {
    return appendCommand(commandObjects.cfInsertNx(key, items));
  }

  @Override
  public Response<List<Boolean>> cfInsertNx(String key, CFInsertParams insertParams, String... items) {
    return appendCommand(commandObjects.cfInsertNx(key, insertParams, items));
  }

  @Override
  public Response<Boolean> cfExists(String key, String item) {
    return appendCommand(commandObjects.cfExists(key, item));
  }

  @Override
  public Response<Boolean> cfDel(String key, String item) {
    return appendCommand(commandObjects.cfDel(key, item));
  }

  @Override
  public Response<Long> cfCount(String key, String item) {
    return appendCommand(commandObjects.cfCount(key, item));
  }

  @Override
  public Response<Map<String, Object>> cfInfo(String key) {
    return appendCommand(commandObjects.cfInfo(key));
  }

  @Override
  public Response<String> cmsInitByDim(String key, long width, long depth) {
    return appendCommand(commandObjects.cmsInitByDim(key, width, depth));
  }

  @Override
  public Response<String> cmsInitByProb(String key, double error, double probability) {
    return appendCommand(commandObjects.cmsInitByProb(key, error, probability));
  }

  @Override
  public Response<List<Long>> cmsIncrBy(String key, Map<String, Long> itemIncrements) {
    return appendCommand(commandObjects.cmsIncrBy(key, itemIncrements));
  }

  @Override
  public Response<List<Long>> cmsQuery(String key, String... items) {
    return appendCommand(commandObjects.cmsQuery(key, items));
  }

  @Override
  public Response<String> cmsMerge(String destKey, String... keys) {
    return appendCommand(commandObjects.cmsMerge(destKey, keys));
  }

  @Override
  public Response<String> cmsMerge(String destKey, Map<String, Long> keysAndWeights) {
    return appendCommand(commandObjects.cmsMerge(destKey, keysAndWeights));
  }

  @Override
  public Response<Map<String, Object>> cmsInfo(String key) {
    return appendCommand(commandObjects.cmsInfo(key));
  }

  @Override
  public Response<String> topkReserve(String key, long topk) {
    return appendCommand(commandObjects.topkReserve(key, topk));
  }

  @Override
  public Response<String> topkReserve(String key, long topk, long width, long depth, double decay) {
    return appendCommand(commandObjects.topkReserve(key, topk, width, depth, decay));
  }

  @Override
  public Response<List<String>> topkAdd(String key, String... items) {
    return appendCommand(commandObjects.topkAdd(key, items));
  }

  @Override
  public Response<List<String>> topkIncrBy(String key, Map<String, Long> itemIncrements) {
    return appendCommand(commandObjects.topkIncrBy(key, itemIncrements));
  }

  @Override
  public Response<List<Boolean>> topkQuery(String key, String... items) {
    return appendCommand(commandObjects.topkQuery(key, items));
  }

  @Override
  public Response<List<Long>> topkCount(String key, String... items) {
    return appendCommand(commandObjects.topkCount(key, items));
  }

  @Override
  public Response<List<String>> topkList(String key) {
    return appendCommand(commandObjects.topkList(key));
  }

  @Override
  public Response<Map<String, Object>> topkInfo(String key) {
    return appendCommand(commandObjects.topkInfo(key));
  }
  // RedisBloom commands

  public Response<Long> waitReplicas(int replicas, long timeout) {
    return appendCommand(commandObjects.waitReplicas(replicas, timeout));
  }
}
