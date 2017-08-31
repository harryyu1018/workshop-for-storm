> 参考 Oracle JDK 1.7.0_80



## 源码分析

HashMap的构造方法遵循了Java集合框架的约束，提供了一个参数为空的构造方法与有一个参数且参数类型为Map的构造方法。除此之外，还提供了两个构造函数，用于设置HashMap的容量(Capacity)与平衡因子(loadFactor)。

```java
public HashMap(int initialCapacity, float loadFactor) {
  if (initialCapacity < 0)
    throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
  if (initialCapacity > MAXIMUM_CAPACITY)
    initialCapacity = MAXIMUM_CAPACITY;
  if (loadFactor <= 0 || Float.isNaN(loadFactor))
    throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);

  this.loadFactor = loadFactor;
  threshold = initialCapacity;
  init();
}

public HashMap(int initialCapacity) {
  this(initialCapacity, DEFAULT_LOAD_FACTOR);
}

public HashMap() {
  this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
}

public HashMap(Map<? extends K, ? extends V> m) {
  this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, 
                DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
  inflateTable(threshold);
  putAllForCreate(m);
}
```

从代码上可以看到，容量和平衡因子都有默认值，并且容量有最大值(2^30)

```java
/**
 * 默认初始化大小，必须是2的倍数
 */
static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

/**
 * HashMap容量的最大值
 */
static final int MAXIMUM_CAPACITY = 1 << 30;

/**
 * 平衡因子默认值
 */
static final float DEFAULT_LOAD_FACTOR = 0.75f;
```

可以看到，默认的平衡因子为0.75，这是权衡了时间复杂度与空间复杂度之后的最好取值(JDK说的)，过高的因子会降低存储空间，但是查找和存储(lookUp, put, get)的时间就会增加。

### 哈希函数的设计原理

















