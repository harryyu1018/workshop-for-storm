package top.yujy.wordcount.kafka;

import org.apache.log4j.Logger;
import org.apache.storm.spout.Scheme;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;


public class MessageScheme implements Scheme {

    private static final Logger LOGGER = Logger.getLogger(MessageScheme.class);

    private static final String FIELD_NAME = "msg";

    private static final String MSG_ENCODING = "UTF-8";

    @Override
    public List<Object> deserialize(ByteBuffer ser) {

        try {
            return new Values(new String(ser.array(), MSG_ENCODING));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Fields getOutputFields() {
        return new Fields(FIELD_NAME);
    }
}
