# Trident API

Trident 的核心数据模型是“流”（Stream），不过与普通的拓扑不同的是，这里的流是作为一连串 batch 来处理的。流是分布在集群中的不同节点上运行的，并且对流的操作也是在流的各个 partition 上并行运行的。

Trident 中有 5 类操作：

1. 针对每个小分区（partition）的本地操作，这类操作不会产生网络数据传输；
2. 针对一个数据流的重新分区操作，这类操作不会改变数据流中的内容，但是会产生一定的网络传输；
3. 通过网络数据传输进行的聚合操作；
4. 针对数据流的分组操作；
5. 融合与联结操作。



## 本地分区操作

本地分区操作是在每个分区块上独立运行的操作，其中不涉及网络数据传输。

### 函数

函数负责接收一个输入域的集合并选择输出或者不输出 tuple。输出 tuple 的域会被添加到原始数据流的输入域中。如果一个函数不输出 tuple，那么原始的输入 tuple 就会被直接过滤掉。否则，每个输出 tuple 都会复制一份输入 tuple 。假设你有下面这样的函数：

```java
public class MyFunction extends BaseFunction {
    public void execute(TridentTuple tuple, TridentCollector collector) {
        for(int i=0; i < tuple.getInteger(0); i++) {
            collector.emit(new Values(i));
        }
    }
}
```

再假设你有一个名为 “mystream” 的数据流，这个流中包含下面几个 tuple，每个 tuple 中包含有 “a”、“b”、“c” 三个域：

```
// Fields: [a, b, c]
[1, 2, 3]
[4, 1, 6]
[3, 0, 8]
```

如果你运行这段代码：

```
mystream.each(new Fields("b"), new MyFunction(), new Fields("d")))

```

那么最终输出的结果 tuple 就会包含有 “a”、“b”、“c”、“d” 4 个域，就像下面这样：

```
// Fields: [a, b, c, d]
[1, 2, 3, 0]
[1, 2, 3, 1]
[4, 1, 6, 0]
```



### 过滤器

过滤器负责判断输入的 tuple 是否需要保留。以下面的过滤器为例：

```
public class MyFilter extends BaseFilter {
    public boolean isKeep(TridentTuple tuple) {
        return tuple.getInteger(0) == 1 && tuple.getInteger(1) == 2;
    }
}
```

通过使用这段代码：

```
mystream.each(new Fields("b", "a"), new MyFilter())
```

就可以将下面这样带有 “a”、“b”、“c” 三个域的 tuple

```
[1, 2, 3]
[2, 1, 1]
[2, 3, 4]

```

最终转化成这样的结果 tuple：

```
[2, 1, 1]
```



### partitionAggregate

`partitionAggregate` 会在一批 tuple 的每个分区上执行一个指定的功能操作。与上面的函数不同，由 `partitionAggregate`发送出的 tuple 会将输入 tuple 的域替换。以下面这段代码为例：

```
mystream.partitionAggregate(new Fields("b"), new Sum(), new Fields("sum"))

```

假如输入流中包含有 “a”、“b” 两个域并且有以下几个 tuple 块：

```
Partition 0:
["a", 1]
["b", 2]

Partition 1:
["a", 3]
["c", 8]

Partition 2:
["e", 1]
["d", 9]
["d", 10]

```

经过上面的代码之后，输出就会变成带有一个名为 “sum” 的域的数据流，其中的 tuple 就是这样的：

```
Partition 0:
[3]

Partition 1:
[11]

Partition 2:
[20]
```



**Storm有三个用于定义聚合器的接口：**

- CombinerAggregator
-  ReducerAggregator
- Aggregator



### CombinerAggregator

```
public interface CombinerAggregator<T> extends Serializable {
    T init(TridentTuple tuple);
    T combine(T val1, T val2);
    T zero();
}
```

`CombinerAggregator` 会将带有一个域的一个单独的 tuple 返回作为输出。`CombinerAggregator` 会在每个输入 tuple 上运行初始化函数，然后使用组合函数来组合所有输入的值。如果在某个分区中没有 tuple， `CombinerAggregator` 就会输出`zero` 方法的结果。例如，下面是 `Count` 的实现代码：

```
public class Count implements CombinerAggregator<Long> {
    public Long init(TridentTuple tuple) {
        return 1L;
    }

    public Long combine(Long val1, Long val2) {
        return val1 + val2;
    }

    public Long zero() {
        return 0L;
    }
}
```

如果你使用 aggregate 方法来代替 partitionAggregate 方法，你就会发现 `CombinerAggregator` 的好处了。在这种情况下，Trident 会在发送 tuple 之前通过分区聚合操作来优化计算过程。



### ReducerAggregator

```
public interface ReducerAggregator<T> extends Serializable {
    T init();
    T reduce(T curr, TridentTuple tuple);
}
```

`ReducerAggregator` 会使用 `init` 方法来产生一个初始化的值，然后使用该值对每个输入 tuple 进行遍历，并最终生成并输出一个单独的 tuple，这个 tuple 中就包含有我们需要的计算结果值。例如，下面是将 Count 定义为 `ReducerAggregator` 的代码：

```
public class Count implements ReducerAggregator<Long> {
    public Long init() {
        return 0L;
    }

    public Long reduce(Long curr, TridentTuple tuple) {
        return curr + 1;
    }
}
```

`ReducerAggregator` 同样可以用于 persistentAggregate，你会在后面看到这一点。



### Aggregator

```
public interface Aggregator<T> extends Operation {
    T init(Object batchId, TridentCollector collector);
    void aggregate(T state, TridentTuple tuple, TridentCollector collector);
    void complete(T state, TridentCollector collector);
}
```

`Aggregator` 聚合器可以生成任意数量的 tuple，这些 tuple 也可以带有任意数量的域。聚合器可以在执行过程中的任意一点输出tuple，他们的执行过程是这样的：

1. 在处理一批数据之前先调用 init 方法。init 方法的返回值是一个代表着聚合状态的对象，这个对象接下来会被传入 aggregate 方法和 complete 方法中。
2. 对于一个区块中的每个 tuple 都会调用 aggregate 方法。这个方法能够更新状态并且有选择地输出 tuple。
3. 在区块中的所有 tuple 都被 aggregate 方法处理之后就会调用 complete 方法。

下面是使用 Count 作为聚合器的代码：

```
public class CountAgg extends BaseAggregator<CountState> {
    static class CountState {
        long count = 0;
    }

    public CountState init(Object batchId, TridentCollector collector) {
        return new CountState();
    }

    public void aggregate(CountState state, TridentTuple tuple, TridentCollector collector) {
        state.count+=1;
    }

    public void complete(CountState state, TridentCollector collector) {
        collector.emit(new Values(state.count));
    }
}
```

有时你可能会需要同时执行多个聚合操作。这个过程叫做链式处理，可以使用下面这样的代码来实现：

```
mystream.chainedAgg()
        .partitionAggregate(new Count(), new Fields("count"))
        .partitionAggregate(new Fields("b"), new Sum(), new Fields("sum"))
        .chainEnd()
```

这段代码会在每个分区上分别执行 Count 和 Sum 聚合器，而输出中只会包含一个带有 [“count”, “sum”] 域的单独的 tuple。