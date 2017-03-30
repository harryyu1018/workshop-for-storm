package top.yujy.wordcount;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.LocalDRPC;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.redis.common.config.JedisPoolConfig;
import org.apache.storm.redis.common.mapper.RedisDataTypeDescription;
import org.apache.storm.redis.common.mapper.RedisStoreMapper;
import org.apache.storm.redis.trident.state.Options;
import org.apache.storm.redis.trident.state.RedisMapState;
import org.apache.storm.redis.trident.state.RedisState;
import org.apache.storm.trident.TridentState;
import org.apache.storm.trident.TridentTopology;
import org.apache.storm.trident.operation.BaseFunction;
import org.apache.storm.trident.operation.TridentCollector;
import org.apache.storm.trident.operation.builtin.Count;
import org.apache.storm.trident.operation.builtin.FilterNull;
import org.apache.storm.trident.operation.builtin.MapGet;
import org.apache.storm.trident.operation.builtin.Sum;
import org.apache.storm.trident.state.TransactionalValue;
import org.apache.storm.trident.testing.FixedBatchSpout;
import org.apache.storm.trident.testing.MemoryMapState;
import org.apache.storm.trident.tuple.TridentTuple;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.ITuple;
import org.apache.storm.tuple.Values;

/**
 * Trident实现Word Count.
 */
public class TridentWordCountAndStoreIntoRedis {

    public static class Split extends BaseFunction {

        @Override
        public void execute(TridentTuple tuple, TridentCollector collector) {
            String sentence = tuple.getString(0);
            for (String word : sentence.split(" ")) {
                collector.emit(new Values(word));
            }
        }
    }

    private static final int MAX_BATCH_SIZE = 3;
    private static final Values[] dataset = {
            new Values("the cow jumped over the moon"),
            new Values("the man went to the store and bought some candy"),
            new Values("four score and seven years ago"),
            new Values("how many apples can you eat"),
            new Values("to be or not to be the person")
    };

    public static StormTopology buildTopology() {
        FixedBatchSpout spout = new FixedBatchSpout(new Fields("sentence"), MAX_BATCH_SIZE, dataset);
        spout.setCycle(true);

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig.Builder()
                .setHost("localhost").setPort(6379).build();
        RedisDataTypeDescription typeDescription = new RedisDataTypeDescription(RedisDataTypeDescription.RedisDataType.HASH, "wc");

        TridentTopology topology = new TridentTopology();
        TridentState state = topology.newStream("spout1", spout).parallelismHint(16)
                .each(new Fields("sentence"), new Split(), new Fields("word"))
                .groupBy(new Fields("word"))
                .persistentAggregate(RedisMapState.transactional(jedisPoolConfig, typeDescription), new Count(), new Fields("count")).parallelismHint(16);

        return topology.build();
    }

    public static void main(String[] args) throws Exception {
        Config config = new Config();
        config.setMaxSpoutPending(20);
        if (args.length <= 0) {
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("wordCounter", config, buildTopology());

        } else {
            config.setNumWorkers(3);
            StormSubmitter.submitTopology(args[0], config, buildTopology());
        }
    }
}
