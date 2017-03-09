package top.yujy.wordcount.spout;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

import java.util.Map;
import java.util.Random;

/**
 * Created by harry on 2017/2/21.
 */
public class RandomSentenceSpout extends BaseRichSpout {

    SpoutOutputCollector collector;
    Random rand;

    private static final String[] sentences = {
            "the cow jumped over the moon",
            "an apple a day keeps the doctor away",
            "four score and seven years ago",
            "snow white and the seven dwarfs",
            "i am at two with nature"
    };

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("word"));
    }

    @Override
    public void open(Map map, TopologyContext topologyContext, SpoutOutputCollector spoutOutputCollector) {
        this.collector = spoutOutputCollector;
        rand = new Random();
    }

    @Override
    public void nextTuple() {
        Utils.sleep(100);

        // 生成随机数, 得到产生的句子
        String sentence = sentences[rand.nextInt(sentences.length)];
        collector.emit(new Values(sentence));
    }

    @Override
    public void ack(Object msgId) {
        System.out.println("ack: " + msgId);
    }

    @Override
    public void fail(Object msgId) {
        System.out.println("fail: " + msgId);
    }
}
