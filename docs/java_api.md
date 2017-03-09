# Java API



- Spout
  - ISpout	// 最基本的Spout接口
  - IRichSpout    // 带有output定义、用的最多的接口
  - 核心是`nextTuple()`回调函数
- Bolt
  - IBolt	// 最基本的Bolt接口
  - IRichBolt    // 带output定义、用的最多的接口
  - IBasicBolt    // 针对常用场景简化的Bolt接口
- Base*
  - 实现对应接口的基类（具体实现为空）
  - BaseRichSpout
  - BaseRichBolt
- Tuple





## Spout分析





![Spout类图](img/Spout类图1.png)

![Spout类图带方法版本](img/Spout类图2.png)

<center>Spout结构</center>



### ISpout.java

```java
public interface ISpout extends Serializable {
    /**
     * 在集群中的Work中被初始化调用
     * 
     */
    void open(Map conf, TopologyContext context, SpoutOutputCollector collector);

    /**
     * 当ISpout准备关闭是被调用. 
     * 但是没法保证该方法一定被调用，因为supervisor上面会使用 kill -9 来杀死进程
     * 唯一能保证被调用的场景是：LocalCluster.
     */
    void close();
    
    /**
     * 从“暂停状态”到“重新激活”. 其spout上的nextTuple会重新被调用.
     */
    void activate();
    
    /**
     * 从“重新激活”到“暂停状态”. 其spout上的nextTuple将不会被调用.
     */
    void deactivate();

    /**
     * 主要用来生成tuple，内部调用collector.emit输出.
     * 循环回调，没有输出或者max.pending达到时触发sleep.
     * 
     * 当没有tuple可以输出时，比较合适的友好的方式是让其sleep短暂的一会（比如：1ms），以免浪费太多的CPU
     */
    void nextTuple();

    /**
     * tuple处理完成时回调
     */
    void ack(Object msgId);

    /**
     * tuple超时或者提前失败时回调
     */
    void fail(Object msgId);
  
    /**
     * 注意：一个spout的nextTuple/ack/fail这3个回调函数任意时刻只有一个会被调用
     */
}
```



**IComponent**

```java
public interface IComponent extends Serializable {

    /**
     * 定义输出的字段.
     */
    void declareOutputFields(OutputFieldsDeclarer declarer);

    /**
     * 返回对应Component特有的Config.
     */
    Map<String, Object> getComponentConfiguration();

}
```





**ISpoutOutputCollector**

```java
public interface ISpoutOutputCollector extends IErrorReporter{
    /**
     * 返回收到这些Tuple的taskId.
     */
    List<Integer> emit(String streamId, List<Object> tuple, Object messageId);
  
    void emitDirect(int taskId, String streamId, List<Object> tuple, Object messageId);
    long getPendingCount();
    
   /**
    * 用于汇报Spout中的错误. 这个是从IErrorReporter继承得到的方法.
    */
    void reportError(Throwable error);
}
```



## Bolt分析



![Bolt类图](img/Bolt类图结构1.png)



![Bolt类图](img/Bolt类图结构2.png)

<center>Spout结构</center>



### IBolt



```java
public interface IBolt extends Serializable {
    /**
     * 初始化回调函数.
     * 
     * @param stormConf 提供Storm集群的相关信息.
     * @param context 可以获取当前对象在topology中的task的位置, 包括：task id & component id, 输入&输出信息等.
     * @param collector 用于emit tuple. tuple可以在任意时刻被emit, prepare&cleanup都是可以的.
     */
    void prepare(Map stormConf, TopologyContext context, OutputCollector collector);

    /**
     * 处理函数. Tuple包含了元数据(来自于哪个component, stream, 和task).
     * IBolt不需要立刻处理tuple, 它可以保留tuple, 然后在之后处理(针对aggregation或者join的场景)
     *
     * 使用prepare中的collector emit tuple. 需要注意的是, 所有的tuple必须被ack或者fail, 否则的话, storm没法告知产生tuple的spout是否执行成功.
     *
     * 注意：大部分场景都是在execute方法的底部进行ack. IBasicBolt会自动ack.
     * see IBasicBolt which automates this.
     */
    void execute(Tuple input);

    /**
     * 销毁Bolt时的回调函数(销毁时不能保证一定被调用).
     */
    void cleanup();
}
```



### IBasicBolt

- 针对常用场景简化的bolt接口
- 什么场景: emit一个新tuple, ack输入tuple
- 简单什么: 1. 不用保存collector, 2. 不用显示调用ack

```java
public interface IBasicBolt extends IComponent {
    void prepare(Map stormConf, TopologyContext context);
    /**
     * Process the input tuple and optionally emit new tuples based on the input tuple.
     * 
     * All acking is managed for you. Throw a FailedException if you want to fail the tuple.
     */
    void execute(Tuple input, BasicOutputCollector collector);
    void cleanup();
}
```



![](img/BaseBasicBolt类图.png)



提供空实现的**BaseBasicBolt**， 方便使用，只用实现自己关系的接口即可

```java
public abstract class BaseBasicBolt extends BaseComponent implements IBasicBolt {

    @Override
    public void prepare(Map stormConf, TopologyContext context) {
    }

    @Override
    public void cleanup() {
    }    
}


public abstract class BaseComponent implements IComponent {
    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }    
}
```





## ISpoutOutputCollector VS IOutputCollector 



- Spout通过调用ISpoutOutputCollector的emit函数进行tuple的发射，当然实际上emit函数并未完成实际的发送，它主要是根据用户提供的streamId，计算出该tuple需要发送到的目标taskID。emitDirect函数。更加直白一点的解释是: 直接指定目标taskID。它们都只是将<tasked,tuple>组成的序列对放到一个队列中，然后会有另一个线程负责将tuple从队列中取出并发送到目标task。
- IOutputCollector是会被Bolt调用的，与ISpoutOutputCollector功能类似。但是区别也很明显，首先我们可以看到它的emit系列函数，多了一个参数Collection<Tuple> anchors，增加这样一个anchors原因在于，对于spout来说，它产生的tuple就是root tuple，但是对于bolt来说，它是通过一个或多个输入tuple，进而产生输出tuple的，这样tuple之间是有一个父子关系的，anchors就是用于指定当前要emit的这个tuple的所有父亲，正是通过它，才建立起tuple树，如果用户给了一个空的anchors，那么这个要emit的tuple将不会被加入tuple树，也就不会被追踪，即使后面它丢失了，也不会被spout感知。



### Tuple分析

获取Tuple对应值的方法

```java
public interface ITuple {
  
  // 根据下标获取字段值
  public Object getValue(int i);
  public String getString(int i);
  
  // 根据名称获取字段值
  public Object getValueByField(String field);
  public String getStringByField(String field);
  
  // 获取所有字段值列表
  public List<Object> getValues();
}
```



![ITuple](img/ITuple方法.png)



```java
public interface Tuple extends ITuple{

    /**
     * Returns the global stream id (component + stream) of this tuple.
     * 
     * @deprecated replaced by {@link #getSourceGlobalStreamId()} due to broken naming convention
     */
    @Deprecated
    public GlobalStreamId getSourceGlobalStreamid();
    
    /**
     * Returns the global stream id (component + stream) of this tuple.
     */
    public GlobalStreamId getSourceGlobalStreamId();

    /**
     * Gets the id of the component that created this tuple.
     */
    public String getSourceComponent();
    
    /**
     * Gets the id of the task that created this tuple.
     */
    public int getSourceTask();
    
    /**
     * Gets the id of the stream that this tuple was emitted to.
     */
    public String getSourceStreamId();
    
    /**
     * Gets the message id that associated with this tuple.
     */
    public MessageId getMessageId();
}

```



### Storm的运行拓扑

![](img/Storm中worker进程，executor(线程)和任务的关系.png)



**小例子**

```java
Config conf = new Config();
conf.setNumWorkers(2); // use two worker processes

topologyBuilder.setSpout("blue-spout", new BlueSpout(), 2);
topologyBuilder.setBolt("green-bolt", new GreenBolt(), 2).setNumTasks(4).shuffleGrouping("blue-spout");
topologyBuilder.setBolt("yellow-bolt", new YellowBolt(), 6).shuffleGrouping("green-bolt");

StormSubmitter.submitToplogy("mytopology", conf, topologyBuilder.createTopology());
```





![](img/Storm中worker进程，executor(线程)和任务的关系-例子.png)









