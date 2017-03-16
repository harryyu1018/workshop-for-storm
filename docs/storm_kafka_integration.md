# Storm Kafka Integration



## KafkaConfig



```java
public KafkaConfig(BrokerHosts hosts, String topic);
public KafkaConfig(BrokerHosts hosts, String topic, String clientId);
```



### SpoutConfig

SpoutConfig是KafkaConfig的一个子类。它提供了额外的字段用于存储：zk的连接信息和 for controlling behavior specific to KafkaSpout。

```java
public SpoutConfig(BrokerHosts hosts, String topic, String zkRoot, String id);
public SpoutConfig(BrokerHosts hosts, String topic, String id);
```

设置多久将Kafka消费offset存储到ZK中

```java
// setting for how often to save the current Kafka offset to ZooKeeper
public long stateUpdateIntervalMs = 2000;
```



KafkaConfig 还有其他一组控制应用表现的变量

```java
public int fetchSizeBytes = 1024 * 1024;
public int socketTimeoutMs = 10000;
public int fetchMaxWait = 10000;
public int bufferSizeBytes = 1024 * 1024;
public MultiScheme scheme = new RawMultiScheme();
public boolean ignoreZkOffsets = false;
public long startOffsetTime = kafka.api.OffsetRequest.EarliestTime();
public long maxOffsetBehind = Long.MAX_VALUE;
public boolean useStartOffsetTimeIfOffsetOutOfRange = true;
public int metricsTimeBucketSizeInSecs = 60;
```



### MultiScheme

MultiScheme是一个接口，用于描述ByteBuffer如何从Kafka消费，再转换成Storm Tuple。它同时控制应用输出字段的命名(controls the naming of your output field)。

```java
public Iterable<List<Object>> deserialize(ByteBuffer ser);
public Fields getOutputFields();
```



**实现类**

- **RawMultiScheme**:	  获取一个ByteBuffer & 返回一个byte[] (ByteBuffer转换后) tuple。输出字段outputField的名字为"bytes"。

 **RawMultiScheme**:  获取一个ByteBuffer & 返回一个byte[] (ByteBuffer转换后) tuple。输出字段outputField的名字为"bytes"。

- **SchemeAsMultiScheme**
- **KeyValueSchemeAsMultiScheme**
- **SchemeAsMultiScheme**
- **MessageMetadataSchemeAsMultiScheme**

```java
public Iterable<List<Object>> deserializeMessageWithMetadata(ByteBuffer message, Partition partition, long offset);
```

PS: 保存每个消息的分区和消费点位替代持久化整个消息，这种方法对于从任意消费点位审计和重播Kafka topic中的消息是非常有用的。

This is useful for auditing/replaying messages from arbitrary points on a Kafka topic, saving the partition and offset of each message of a discrete stream instead of persisting the entire message.



### Failed message retry

FailedMsgRetryManager是一个定义失败消息重试策略的接口。默认实现是ExponentialBackoffMsgRetryManager，连续重试之间的间隔按照指数增长。如果需要自定义实现，那么设置`SpoutConfig.failedMsgRetryManagerClass = <自定义策略实现类的全名fullClassName>`。



> **版本不兼容**
>
> In Storm versions prior to 1.0, the MultiScheme methods accepted a `byte[]` instead of `ByteBuffer`. The `MultScheme` and the related Scheme apis were changed in version 1.0 to accept a ByteBuffer instead of a byte[].
>
> This means that pre 1.0 kafka spouts will not work with Storm versions 1.0 and higher. While running topologies in Storm version 1.0 and higher, it must be ensured that the storm-kafka version is at least 1.0. Pre 1.0 shaded topology jars that bundles storm-kafka classes must be rebuilt with storm-kafka version 1.0 for running in clusters with storm 1.0 and higher.



### Examples

**Core Spout**

```java
BrokerHosts hosts = new ZKHosts(zkConnString);
SpoutConfig spoutConfig = new SpoutConfig(hosts, topicName, "/" + topicName, UUID.randomUUID().toString());

spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
KafkaSpout kafkaSpout = new KafkaSpout(spoutConfig);
```

**Trident Spout**

```java
TridentTopology topology = new TridentTopology();

BrokerHosts zk = new ZKHosts("localhost");
TridentKafkaConfig spoutConf = new TridentKafkaConfig(zk, "test-topic");

spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
OpaqueTridentKafkaSpout spout = new OpaqueTridentKafkaSpout(spoutConf);
```



### KafkaSpout如何存储消费点位&在失败的情况下恢复？

可以通过设置`kafkaConfig.startOffsetTime`来控制Spout从Topic中开始读取的位置，有如下三种方法：

1. `kafka.api.OffsetRequest.EarliestTime` : 从topic的开头开始读取（最老的一条信息）
2. `kafka.api.OffsetRequest.LatestTime()` : 从topic的最后一条开始读取（任何写入topic的新消息）
3. Unix时间戳( System.currentTimeMills() )，see [How do I accurately get offsets of messages for a certain timestamp using OffsetRequest? ](https://cwiki.apache.org/confluence/display/KAFKA/FAQ#FAQ-HowdoIaccuratelygetoffsetsofmessagesforacertaintimestampusingOffsetRequest?) in Kafka FAQ



Topology运行中Kafka spout会记录其已经读取和发出（emit）的消息点位（offset）, 并存储这些状态信息在ZK的 `SpoutConfig.zkRoot + "/" + SpoutConfig.id`节点中。当出现失败，Topology会读取上次写入offset进行恢复。

> PS: 当重新部署Topology时需要保证SpoutConfig.zkRoot & SpoutConfig.id两个值没有被修改，否则spout将不能从ZK读取上次消费状态信息(offset)。这有可能导致未知的情况，造成



This means that when a topology has run once the setting `KafkaConfig.startOffsetTime` will not have an effect for subsequent runs of the topology because now the topology will rely on the consumer state information (offsets) in ZooKeeper to determine from where it should begin (more precisely: resume) reading.

 If you want to force the spout to ignore any consumer state information stored in ZooKeeper, then you should set the parameter `KafkaConfig.ignoreZkOffsets` to `true`. If `true`, the spout will always begin reading from the offset defined by `KafkaConfig.startOffsetTime` as described above.





## KafkaSpout分析

**原理**

- kafka topic的partition均匀分配给spout task
- 每个spout task负责消费1个或多个partition
- nextTuple()从kafka取数据，emit tuple
- ack()标记已处理的tuple，用来更新消费的offset，消费过的offset就不再重复了
- fail()标记未处理完的tuple，用来重新从kafka取到未处理完的数据进行消费

 



```java
@Override
    public void nextTuple() {
        List<PartitionManager> managers = _coordinator.getMyManagedPartitions();
        for (int i = 0; i < managers.size(); i++) {

            try {
                // in case the number of managers decreased
                _currPartitionIndex = _currPartitionIndex % managers.size();
                EmitState state = managers.get(_currPartitionIndex).next(_collector);
                if (state != EmitState.EMITTED_MORE_LEFT) {
                    _currPartitionIndex = (_currPartitionIndex + 1) % managers.size();
                }
                if (state != EmitState.NO_EMITTED) {
                    break;
                }
            } catch (FailedFetchException e) {
                LOG.warn("Fetch failed", e);
                _coordinator.refresh();
            }
        }

        long diffWithNow = System.currentTimeMillis() - _lastUpdateMs;

        /*
             As far as the System.currentTimeMillis() is dependent on System clock,
             additional check on negative value of diffWithNow in case of external changes.
         */
        if (diffWithNow > _spoutConfig.stateUpdateIntervalMs || diffWithNow < 0) {
            commit();
        }
    }
```



```java
// ack
@Override
public void ack(Object msgId) {
  KafkaMessageId id = (KafkaMessageId) msgId;
  PartitionManager m = _coordinator.getManager(id.partition);
  if (m != null) {
    m.ack(id.offset);
  }
}

// fail
@Override
public void fail(Object msgId) {
  KafkaMessageId id = (KafkaMessageId) msgId;
  PartitionManager m = _coordinator.getManager(id.partition);
  if (m != null) {
    m.fail(id.offset);
  }
}


// commit
private void commit() {
  _lastUpdateMs = System.currentTimeMillis();
  for (PartitionManager manager : _coordinator.getMyManagedPartitions()) {
    manager.commit();
  }
}

```



**KafkaMessageId**

```java
static class KafkaMessageId implements Serializable {
  
  // org.apache.storm.kafka.Partition (注意不是Kafka里面的Partition)
  public Partition partition;
  public long offset;
  
  // here is constructor
}
```





### PartitionManager分析

**Emit的三种状态**

```java
static enum EmitState {
  EMITTED_MORE_LEFT, EMITTED_END, NO_EMITTED
}
```

**next()函数**

```java
public EmitState next(SpoutOutputCollector collector) {
        if (_waitingToEmit.isEmpty()) {
            fill();
        }
        while (true) {
            MessageAndOffset toEmit = _waitingToEmit.pollFirst();
            if (toEmit == null) {
                return EmitState.NO_EMITTED;
            }

            Iterable<List<Object>> tups;
            if (_spoutConfig.scheme instanceof MessageMetadataSchemeAsMultiScheme) {
                tups = KafkaUtils.generateTuples((MessageMetadataSchemeAsMultiScheme) _spoutConfig.scheme, toEmit.message(), _partition, toEmit.offset());
            } else {
                tups = KafkaUtils.generateTuples(_spoutConfig, toEmit.message(), _partition.topic);
            }
            
            if ((tups != null) && tups.iterator().hasNext()) {
               if (!Strings.isNullOrEmpty(_spoutConfig.outputStreamId)) {
                    for (List<Object> tup : tups) {
                        collector.emit(_spoutConfig.topic, tup, new KafkaMessageId(_partition, toEmit.offset()));
                    }
                } else {
                    for (List<Object> tup : tups) {
                        collector.emit(tup, new KafkaMessageId(_partition, toEmit.offset()));
                    }
                }
                break;
            } else {
                ack(toEmit.offset());
            }
        }
        if (!_waitingToEmit.isEmpty()) {
            return EmitState.EMITTED_MORE_LEFT;
        } else {
            return EmitState.EMITTED_END;
        }
    }
```



### KafkaSpout流程示意

 

- emittedToOffset: 已经从kafka读到的最大offset
- committedTo: 已经确认处理完的最大offset
- pending: 已经从kafka读到，但是还没有ack的offset队列
- waitingToEmit: 已从kafka读到，但是还没有emit出去的数据





## KafkaBolt分析

关键参数：

- TopicSelector: 数据写到哪个topic
- TupleToKafkaMapper: 从Storm的tuple选择哪些字段写Kafka
- serializer class: 把选择的字段转成kafka的byte[]
- broker list: broker列表



原理：

- TopicSelector决定写入哪个topic
  - DefaultTopicSelector可以指定固定的topic
- TupleToKafkaMapper决定写入tuple中的哪些数据
  - FieldNameBased 

 























