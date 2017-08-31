package top.yujy.wordcount;

import org.apache.storm.Config;

/**
 * @author yujingyi.
 */
public class PersistentWordCount {

    private static final String WORD_SPOUT = "WORD_SPOUT";
    private static final String COUNT_BOLT = "COUNT_BOLT";
    private static final String HBASE_BOLT = "HBASE_BOLT";

    public static void main(String[] args) {

        Config config = new Config();

    }

}
