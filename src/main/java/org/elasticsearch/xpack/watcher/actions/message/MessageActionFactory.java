package org.elasticsearch.xpack.watcher.actions.message;

import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.watcher.actions.ActionFactory;
import org.elasticsearch.xpack.watcher.actions.ExecutableAction;
import org.elasticsearch.xpack.watcher.actions.wechat.ExecutableWechatAction;
import org.elasticsearch.xpack.watcher.actions.wechat.WechatAction;

import java.io.IOException;

/**
 * @author chendong
 * @date 2018-07-09
 * @description
 */

public class MessageActionFactory  extends ActionFactory {

    private final TextTemplateEngine templateEngine;

    public MessageActionFactory(final Settings settings, final TextTemplateEngine templateEngine) {
        super(Loggers.getLogger(ExecutableMessageAction.class, settings, new String[0]));
        this.templateEngine = templateEngine;
    }

    @Override
    public ExecutableAction parseExecutable(final String watchId,final String actionId, final XContentParser parser) throws IOException {
        return new ExecutableMessageAction(MessageAction.parse(watchId, actionId, parser), this.actionLogger, this.templateEngine);
    }
}
