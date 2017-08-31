package top.yujy.wordcount.hbase;

import org.apache.storm.trident.operation.BaseFunction;
import org.apache.storm.trident.operation.TridentCollector;
import org.apache.storm.trident.tuple.TridentTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * @author yujingyi.
 */
public class PrintFunction extends BaseFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintFunction.class);

    private static final Random RANDOM = new Random();

    @Override
    public void execute(TridentTuple tuple, TridentCollector collector) {
        if (RANDOM.nextInt(1000) > 999) {
            LOGGER.info(tuple.toString());
        }
    }
}
