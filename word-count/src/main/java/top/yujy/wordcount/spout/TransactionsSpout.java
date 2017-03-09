package top.yujy.wordcount.spout;

import org.apache.log4j.Logger;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 事务Spout.
 */
public class TransactionsSpout extends BaseRichSpout {

    private static Logger LOG = Logger.getLogger(TransactionsSpout.class);

    private static final Integer MAX_FAILS = 2;
    Map<Integer, String> messages;
    Map<Integer, Integer> transactionFailureCount;
    Map<Integer, String> toSend;

    private SpoutOutputCollector collector;

    @Override
    public void nextTuple() {

        if (!toSend.isEmpty()) {
            toSend.forEach(
                    (k, v) -> collector.emit(new Values(v), k)
            );
        }

        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ack(Object msgId) {
        messages.remove(msgId);
        LOG.info("Processed message [" + msgId + "]");
    }

    @Override
    public void fail(Object msgId) {
        if (!transactionFailureCount.containsKey(msgId)) {
            throw new RuntimeException("Error, transaction id not found [" + msgId + "]");
        }

        Integer transactionId = (Integer) msgId;

        Integer failures = transactionFailureCount.get(transactionId) + 1;
        if (failures >= MAX_FAILS) {
            LOG.error("Error, transaction id [" + transactionId + "] has had many errors [" + failures + "]");

            toSend.remove(transactionId);
//            transactionFailureCount.remove(transactionId);
            return;
            // throw new RuntimeException("Error, transaction id [" + transactionId + "] has had many errors [" + failures + "]");
        }

        transactionFailureCount.put(transactionId, failures);
        toSend.put(transactionId, messages.get(transactionId));

        LOG.info("Re-send messages [" + msgId + "]");
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("transactionMessage"));
    }

    @Override
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {

        this.collector = collector;

        Random random = new Random();

        messages = new HashMap<>();
        toSend = new HashMap<>();
        transactionFailureCount = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            messages.put(i, "transaction_" + random.nextInt());
            transactionFailureCount.put(i, 0);
        }

        toSend.putAll(messages);
    }

}
