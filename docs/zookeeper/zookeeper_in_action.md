# ZooKeeper 进阶



http://www.cnblogs.com/likui360/p/5985588.html

## ZooKeeper配置

| 参数名                  | 说明                                       | 类型      |
| -------------------- | ---------------------------------------- | ------- |
| clientPort           | 客户端连接server的接口，即对外服务端口，一般设置为2181         | int     |
| dataDir              | 存储快照文件snapshot的目录，默认情况下，事务日志也会存储在这里。建议同时配置参数dataLogDIr. 事务日志的写性能直接影响zk性能 | String  |
| dataLogDir           | 事务日志输出目录。尽量给事务日志的输出配置单独的磁盘或是挂载点，这将极大的提升ZK性能 | String  |
| tickTime             | ZK中的一个时间单元。ZK中所有时间都是以这个时间单元为基础，进行整数倍配置的。例如session的最小超时时间是 minSessionTimeout*tickTime | int     |
| maxClientCnxns       | 单个客户端与单台服务器之间的连接数的限制，是**ip级别**的，默认是60，如果设置为0，那么表明不做任何限制。 请注意这个限制的使用范围，仅仅**是单台客户端机器与单台ZK服务器之间的连接数限制**。 注意：不是针对指定客户端IP，也不是ZK集群的连接数限制，也不是单台ZK对所有客户端的连接数限制。 | int     |
| minSessionTimeout    | Session超时时间限制，如果客户端设置的超时时间不在这个范围，那么会强制设置为最大或最小时间。默认的Session超时时间是在 min\*tickTime ~ max\*tickTime。（ new in 3.3.0） | int     |
| maxSessionTimeout    | Session超时时间设置的最大值（new in 3.3.0）          | int     |
| initLimit            |                                          | int     |
| syncLimit            |                                          | int     |
| electionAlg          |                                          | int     |
| electionPort         |                                          | int     |
| quorumListenOnAllIPs |                                          | boolean |
|                      |                                          |         |
|                      |                                          |         |
|                      |                                          |         |
|                      |                                          |         |
|                      |                                          |         |
|                      |                                          |         |
|                      |                                          |         |



