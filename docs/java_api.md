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



### ISpout



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



