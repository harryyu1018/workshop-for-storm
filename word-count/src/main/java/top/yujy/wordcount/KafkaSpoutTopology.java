package top.yujy.wordcount;

import org.apache.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.kafka.*;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Properties;
import java.util.UUID;

/**
 * KafkaSpout Topology Demo.
 */
public class KafkaSpoutTopology {

    public static void main(String[] args) throws Exception {

        TopologyBuilder builder = new TopologyBuilder();

        KafkaSpout kafkaSpout = initKafkaSpout();
        KafkaBolt kafkaBolt = initKafkaBolt();

        builder.setSpout("kafka-spout", kafkaSpout, 2);
        builder.setBolt("seq-bolt", new SequenceBolt(), 2).shuffleGrouping("kafka-spout");
        builder.setBolt("kafka-bolt", kafkaBolt, 2).shuffleGrouping("seq-bolt");

        Config conf = new Config();
        conf.setDebug(true);

        if (args != null && args.length > 0) {
            conf.setNumWorkers(3);
            StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
        } else {
            conf.setMaxTaskParallelism(4);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("kafka-spout-demo", conf, builder.createTopology());

            Thread.sleep(500000);

            cluster.shutdown();
        }
    }

    private static final String BROKER_HOSTS_STR = "localhost:2181";

    private static final String TOPIC = "topic-storm";
    private static final String ZK_ROOT = "/kafkaspout-demo";

    private static final String TOPIC_RESULT = "topic-storm-result";

    /**
     * 初始化kafkaSpout.
     *
     * @return
     */
    protected static KafkaSpout initKafkaSpout() {

        BrokerHosts brokerHosts = new ZkHosts(BROKER_HOSTS_STR);
        SpoutConfig spoutConfig = new SpoutConfig(brokerHosts, TOPIC, ZK_ROOT, UUID.randomUUID().toString());
        spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());

        return new KafkaSpout(spoutConfig);
    }

    /**
     * 初始化KafkaBolt.
     */
    protected static KafkaBolt initKafkaBolt() {

        // 设置producer属性
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("acks", "1");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        KafkaBolt kafkaBolt = new KafkaBolt()
                .withProducerProperties(props)
                .withTopicSelector(new DefaultTopicSelector(TOPIC_RESULT))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper<>());

        return kafkaBolt;
    }

    public static class SequenceBolt extends BaseBasicBolt {

        private static final Logger LOGGER = Logger.getLogger(SequenceBolt.class);
        private static final String FIELD_NAME = "message";

        @Override
        public void execute(Tuple input, BasicOutputCollector collector) {
            String word = input.getString(0);
            String newWord = String.format("I'm %s !", word);

            LOGGER.info("out -> " + newWord);

            collector.emit(new Values(newWord));
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields(FIELD_NAME));
        }
    }
}
