# ZooKeeper 进阶



http://www.cnblogs.com/likui360/p/5985588.html

## ZooKeeper配置

官方文档：[详见](https://zookeeper.apache.org/doc/r3.4.9/zookeeperAdmin.html#sc_configuration)

### 最小配置

| 参数名                       | 说明                                       | 类型      | 默认值     |
| ------------------------- | ---------------------------------------- | ------- | ------- |
| clientPort                | 客户端连接server的接口，即对外服务端口，一般设置为2181         | int     | 2181    |
| dataDir                   | 存储快照文件snapshot的目录，默认情况下，事务日志也会存储在这里。建议同时配置参数dataLogDIr. 事务日志的写性能直接影响zk性能 | String  |         |
| dataLogDir                | 事务日志输出目录。尽量给事务日志的输出配置单独的磁盘或是挂载点，这将极大的提升ZK性能 | String  |         |
| tickTime                  | ZK中的一个时间单元。ZK中所有时间都是以这个时间单元为基础，进行整数倍配置的。例如session的最小超时时间是 minSessionTimeout*tickTime。（单位：毫秒） | int     | 2000    |
| maxClientCnxns            | 单个客户端与单台服务器之间的连接数的限制，是**ip级别**的，默认是60，如果设置为0，那么表明不做任何限制。 请注意这个限制的使用范围，仅仅**是单台客户端机器与单台ZK服务器之间的连接数限制**。 注意：不是针对指定客户端IP，也不是ZK集群的连接数限制，也不是单台ZK对所有客户端的连接数限制。 | int     |         |
| minSessionTimeout         | Session超时时间限制，如果客户端设置的超时时间不在这个范围，那么会强制设置为最大或最小时间。默认的Session超时时间是在 min\*tickTime ~ max\*tickTime。（ new in 3.3.0） | int     |         |
| maxSessionTimeout         | Session超时时间设置的最大值（new in 3.3.0）          | int     |         |
|                           |                                          |         |         |
|                           |                                          |         |         |
|                           |                                          |         |         |
| quorumListenOnAllIPs      | 该参数设置为true，ZK服务器将监听所有可用IP地址的连接。他会影响ZAB协议和快速Leader选举协议。 | boolean | false   |
| globalOutstandingLimit    | 最大请求堆积数。默认是1000。ZK运行过程中，尽管Server没有空闲来处理更多的客户端请求了，但是还是允许客户端将请求提交到服务器上来，以提高吞吐性能。当然，为了防止Server内存溢出，这个请求堆积数还是需要限制下的。( Java system property: zookeeper.globalOutstandingLimit ) | int     | 1000    |
| preAllocSize              | 预先开辟磁盘空间，用于后续写入事务日志。默认大小是64M，每个事务日志大小就是64M。如果ZK的快照频率较大的话，建议适当减小这个参数。 |         | 64M     |
| snapCount                 | 没snapCount次事务日志输出后，触发一次快照。此时ZK会生成一个snapshot.\*文件，同时创建一个新的事务日志文件log.\*。 |         | 100,000 |
| clientPortAddress         | 对于多网卡的机器，可以为每个IP指定不同的监听端口。默认情况是所有IP都监听clientPort指定的端口。 |         |         |
| fsync.warningthresholdms  | 事务日志输出时，如果调用fsync方法超过指定的超时时间，那么会在日志中输出警告信息。 | int     | 1000    |
| autopurge.snapRetainCount | 参数指定了需要保留的事务日志和快照文件的数目。默认是保留3个。和autopurge.purgeInterval 搭配使用。 ( new in 3.4.0 ) |         | 3       |
| autopurge.purgeInterval   | 指定了清理频率，单位是小时，需要配置一个1或更大的整数。默认是0，表示不开启自动清理功能。 ( new in 3.4.0 ) |         | 0       |
| syncEnabled               | Observer写入日志和生成快照，这样可以减少Observer的恢复时间。true开启，false关闭。( new in 3.4.6, 3.5.0 ) |         | true    |



### 集群配置

| 参数                                     | 说明                                       | 类型   | 默认值  |
| -------------------------------------- | ---------------------------------------- | ---- | ---- |
| electionAlg                            | 在之前的版本中，这个参数配置是允许我们选择Leader选举算法。但是之后的版本中，只留下一种“TCP-based version of fast leader election”算法，所以这个参数目前看来没有用了。 | int  |      |
| initLimit                              | Follower在启动过程中，会从Leader同步所有最新数据，然后确定自己能够对外服务的起始状态。Leader允许Follower在initLimit时间内完成这个工作。通常情况下，使用时不用太在意这个参数的设置。如果ZK集群的数据量确实很大，Follower在启动的时候，从Leader上同步数据的时间也会相应变长。因此在这个情况下，有必要适当调大这个参数。 | int  | 10   |
| leaderServes                           | 默认情况下，Leader是会接受客户端连接，并提供正常的读写服务。但是，如果希望让Leader专注于集群中机器的协调，那么可以将这个参数设置为no，这样一样，会大大提高写操作的性能。一般机器数比较多的情况下可以设置为no，让Leader不接受客户端的连接，默认为yes。 |      | yes  |
| server.x=[hostname]:nnnnn:[nnnnn], etc | "x"是一个数字，与每个服务器的myid文件中的id是一样的。hostname是服务器的hostname，右边配置两个端口，第一个端口用于Follower和Leader之间的数据同步和其它通信，第二个端口用于Leader选举过程中投票通信。 |      |      |
| syncLimit                              | 在运行过程中，Leader负责与ZK集群中所有机器进行通信，例如通过一些心跳检测机制，来检测机器的存活状态。如果Leader发出心跳包在syncLimit之后，还没有从Follower那里收到响应，那么就认为这个Follower已经不在线了。 **注意：**不要把这个参数设置得过大，否则可能会掩盖一些问题。 | int  |      |
| group.x=nnnnn[:nnnnn]                  | "x" 是一个数字，是分组唯一标识(group identifier)。对机器进行分组，后面的参数是myid文件中的id。（例子：group.1=1:2:3） |      |      |
| weight.x=nnnnn                         | "x"是一个数字，和服务器的myid一致。设置服务器的权重，后面的参数是权重值。一部分ZK服务器需要投票，如：选主和原子广播协议(the broadcast protocol)。这个值就是服务器投票时所占权重。默认值都是1。 |      |      |
| cnxTimeout                             | 选举过程中打开一次连接的超时时间，默认为5s。                  |      | 5    |



group.x 和 weight.x 配置示例

```properties
group.1=1:2:3
group.2=4:5:6
group.3=7:8:9
   
weight.1=1
weight.2=1
weight.3=1
weight.4=1
weight.5=1
weight.6=1
weight.7=1
weight.8=1
weight.9=1
```





### 认证和授权选项  Authentication & Authorization

| 参数                                       | 说明                                       | 类型   | 默认值      |
| ---------------------------------------- | ---------------------------------------- | ---- | -------- |
| zookeeper.DigestAuthenticationProvider.superDigest | 启用超级管理员的用户去访问znode。可以使用org.apache.zookeeper.server.auth.DigestAuthenticationProvider来生成一个superDigest，参数格式为：super:<password>，一旦当前连接addAuthInfo超级用户验证通过，后续所有操作都不会checkACL。 |      | disabled |
| isro                                     | 测试服务器是否是read-only模式。如果是read-only，服务器响应"ro"；如果不是read-only模式，则响应为“rw”。( new in 3.4.0 ) |      |          |
| gtmk                                     | Gets the current trace mask as a 64-bit signed long value in decimal format. See stmk for an explanation of the possible values. |      |          |
| stmk                                     |                                          |      |          |



