package top.yujy.wordcount;

import org.apache.hadoop.hbase.client.Durability;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.hbase.bolt.mapper.HBaseProjectionCriteria;
import org.apache.storm.hbase.bolt.mapper.HBaseValueMapper;
import org.apache.storm.hbase.trident.mapper.SimpleTridentHBaseMapper;
import org.apache.storm.hbase.trident.mapper.TridentHBaseMapper;
import org.apache.storm.hbase.trident.state.HBaseQuery;
import org.apache.storm.hbase.trident.state.HBaseState;
import org.apache.storm.hbase.trident.state.HBaseStateFactory;
import org.apache.storm.hbase.trident.state.HBaseUpdater;
import org.apache.storm.trident.Stream;
import org.apache.storm.trident.TridentState;
import org.apache.storm.trident.TridentTopology;
import org.apache.storm.trident.state.StateFactory;
import org.apache.storm.trident.testing.FixedBatchSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import top.yujy.wordcount.hbase.PrintFunction;
import top.yujy.wordcount.hbase.WordCountValueMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yujingyi.
 */
public class TridentWordCountAndStoreIntoHBase {

    public static StormTopology buildTopology(String hbaseRoot) {

        Fields fields = new Fields("word", "count");
        FixedBatchSpout spout = new FixedBatchSpout(fields, 4,
                new Values("storm", 1),
                new Values("trident", 1),
                new Values("needs", 1),
                new Values("javadoc", 1)
        );
        spout.setCycle(true);

        TridentHBaseMapper tridentHBaseMapper = new SimpleTridentHBaseMapper()
                .withColumnFamily("cf")
                .withColumnFields(new Fields("word"))
                .withColumnFields(new Fields("count"))
                .withRowKeyField("word");

        HBaseValueMapper rowToStormValueMapper = new WordCountValueMapper();

        HBaseProjectionCriteria projectionCriteria = new HBaseProjectionCriteria();
        projectionCriteria.addColumn(new HBaseProjectionCriteria.ColumnMetaData("cf", "count"));

        HBaseState.Options options = new HBaseState.Options()
                .withConfigKey(hbaseRoot)
                .withDurability(Durability.SYNC_WAL)
                .withMapper(tridentHBaseMapper)
                .withProjectionCriteria(projectionCriteria)
                .withRowToStormValueMapper(rowToStormValueMapper)
                .withTableName("WordCount");

        StateFactory factory = new HBaseStateFactory(options);

        TridentTopology topology = new TridentTopology();
        Stream stream = topology.newStream("spout1", spout);

        stream.partitionPersist(factory, fields, new HBaseUpdater(), new Fields());

        TridentState state = topology.newStaticState(factory);
        stream = stream.stateQuery(state, new Fields("word"), new HBaseQuery(), new Fields("columnName", "columnValue"));
        stream.each(new Fields("word", "columnValue"), new PrintFunction(), new Fields());

        return topology.build();
    }

    public static void main(String[] args) throws Exception {

        Config config = new Config();
        config.setMaxSpoutPending(5);

        Map<String, String> hbaseConfigMap = new HashMap<>();
        hbaseConfigMap.put("hbase.zookeeper.quorum", "localhost");
        hbaseConfigMap.put("hbase.rootdir", "file:///Users/harry/app/hbase-1.1.12/data/hbase");
        hbaseConfigMap.put("hbase.zookeeper.property.dataDir", "/Users/harry/app/hbase-1.1.12/data/hbase-zk");

        config.put("config_key", hbaseConfigMap);

        args = new String[] { "config_key" };

        if (args.length == 1) {
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("wordCounter", config, buildTopology(args[0]));
            Thread.sleep(5 * 60 * 1000);
            cluster.killTopology("wordCounter");
            cluster.shutdown();
            System.exit(0);
        } else if (args.length == 2) {
            config.setNumWorkers(3);
            StormSubmitter.submitTopology(args[1], config, buildTopology(args[1]));
        } else {
            System.out.println("Usage: TridentFileTopology <hdfs url> [topology name]");
        }
    }
}
