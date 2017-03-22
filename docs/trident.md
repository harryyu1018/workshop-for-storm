# Trident



## Trident 基础



### Trident是什么

1. Trident是Storm基础上封装的高级框架

- 集成常用操作如filter, function, group, aggregation, join
- 支持不丢不重（exactly once）
- 支持**有状态的流式处理**，可以对接各种存储系统
- 集成DRPC查询状态



2. 比原生Storm API更简单应用

- 提供了高层操作，如filter, group, join等
- 封装了复杂的有状态，不丢不重流式处理
- 封装了复杂的流式处理



3. 兼顾流式处理的低延迟和批处理的高吞吐

- 默认做了微批处理，批量读写存储优化
- 分批大小可以灵活控制，延迟仍然可控
- 自动对操作进行组合优化，避免不必要的网络传输



4. 是一个可扩展的框架

- 可以增加自己的高层处理函数实现业务逻辑
- 可以实现与已有存储系统对接的有状态处理
- 可以根据需要选择不同界别的容错：at most once / at least once / exactly once



### Trident 用法



#### 基本概念

> TridentStream

- Stream由一系列TridentTuple batch组成
- Stream分partition，一个task一个Partition
- 在Stream上进行操作产生新的Stream/State，就构成了一个TridentTopology
  - Partition Local Operation: Project, Function, Filter, partitionAggregate, partitionPersist
  - Aggregation Operation: aggregate, partitionAggregate
  - Group Operation: groupBy
  - Join: merge, join



> TridentState

- 封装了状态处理，对接不同存储系统
- 幂等的状态更新操作，支持exactly once语义



流式数据处理

- 调用newStream基于spout创建初始Stream
- 调用各种操作如filter, group进行Stream交换
- 调用persist*接口存储要保存的状态



调用DRPC查询状态

- 调用newDRPCStream创建DRPC请求Stream
- 调用stateQuery读取之前保存的状态



Trident WordCount Demo

```java

```





#### 对比裸写Storm程序

- 代码更简洁直观、易懂、看到的是操作而不是接口
- 堆积木式的编程方式，像函数式语言
- 复杂操作调用接口就可以了
- 生成结果和DRPC查询结果很好结合





## Trident 原理

- 怎么做到不丢不重
- 怎么维护状态
- 怎么集成DRPC查询状态
- 怎么封装常用操作




### 如何实现不丢不重

- 什么时候会丢？
  - tuple fail后，如果不replay就可能会丢
  - 因为有些bolt还没有处理这个tuple
- 什么时候会重？
  - tuple fail后，如果replay就可能重复
  - 因为有些bolt已经处理过这个tuple



> 思考

- 不丢很好解决，spout replay就可以
- 不丢且不重不容易做到
  - 每个tuple/batch给个ID
  - 处理tuple/batch时判断这个ID是否处理过，忽略已经处理过的tuple/batch
  - 隐含条件
    - spout replay tuple/batch时ID要保持和之前fail的一致
    - 有地方存处理过的ID，还要有清理机制



> Trident的方案

- 对tuple进行小批batch处理
- 每个batch一个唯一txid，replay时txid保持不变（TridentSpout自动做好上面两点）
- 区分无状态操作和状态更新操作，对状态更新操作按照batch txid严格有序执行
  - 因此只用保存最后处理过的txid即可做去重



![](img/Trident例子.png)



###  怎么维护状态

- 状态值和txid组合成一个JSON串，原子更新
- State框架内部做好根据txid去重的逻辑
- State框架调用对应用存储系统具体实现的接口如multiGet/multiPut更新状态
- State框架内部提供cache、批处理等性能优化



### 怎么集成DRPC查询状态

从DRPCSpout生成一个Stream + 普通操作

- 在这个Stream上进行各种操作实现逻辑
- 调用stateQuery查询State中的状态数据
- 对返回的状态Stream继续做后续操作



### 怎么封装常用操作

- 每个操作对应一个TridentOperator和TridentTopology中的一个Node
  - ProjectedProcessor, EachProcessor, AggregateProcessor, PartitionPersistProcessor, StateQueryProcessor
  - SpoutNode, ProcessorNode, PartitionNode
- partition local的一个或多个TridentOperator组成一个Bolt
- 每个repartition操作对应一次StreamGrouping，产生新的Bolt
- 最终生成一个普通的StormTopology








