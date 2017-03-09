package top.yujy.wordcount.bolt;


import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.util.Map;
import java.util.Random;

/**
 * 随机失败的Bolt, 用于验证<code>TransactionsSpouts</code>
 */
public class RandomFailureBolt extends BaseRichBolt {

    private static final int MAX_PERCENT_FAIL = 80;

    private Random random = new Random();
    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {

        int rand = random.nextInt(100);

        if (rand > MAX_PERCENT_FAIL) {
            collector.ack(input);
        } else {
            collector.fail(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }
}
