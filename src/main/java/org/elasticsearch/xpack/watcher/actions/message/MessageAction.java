package org.elasticsearch.xpack.watcher.actions.message;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.watcher.actions.Action;
import org.elasticsearch.xpack.watcher.actions.wechat.WechatAction;

import java.io.IOException;

/**
 * @author chendong
 * @date 2018-07-09
 * @description
 */

public class MessageAction implements Action {

    @Override
    public String type() {
        return "message";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return null;
    }

    public static MessageAction parse(final String watchId, final String actionId, final XContentParser parser) throws IOException{
        return null;
    }
}
