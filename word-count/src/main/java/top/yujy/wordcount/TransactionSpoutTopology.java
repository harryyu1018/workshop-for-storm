package top.yujy.wordcount;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.topology.TopologyBuilder;
import top.yujy.wordcount.bolt.RandomFailureBolt;
import top.yujy.wordcount.spout.TransactionsSpout;

public class TransactionSpoutTopology {

    public static void main(String[] args) throws Exception {

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("t-spout", new TransactionsSpout());
        builder.setBolt("rand-failure-bolt", new RandomFailureBolt()).shuffleGrouping("t-spout");

        LocalCluster cluster = new LocalCluster();
        Config conf = new Config();
//        conf.setDebug(true);

        cluster.submitTopology("transaction-test", conf, builder.createTopology());

        while (true) {
            Thread.sleep(1000);
        }

    }
}
