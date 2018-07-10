package org.elasticsearch.xpack.watcher.actions.message;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.watcher.actions.Action;
import org.elasticsearch.xpack.watcher.actions.ExecutableAction;
import org.elasticsearch.xpack.watcher.execution.WatchExecutionContext;
import org.elasticsearch.xpack.watcher.watch.Payload;

/**
 * @author chendong
 * @date 2018-07-09
 * @description
 */

public class ExecutableMessageAction extends ExecutableAction<MessageAction> {
    private final TextTemplateEngine engine;

    public ExecutableMessageAction(final MessageAction action, final Logger logger, final TextTemplateEngine templateEngine) {
        super(action, logger);
        this.engine = templateEngine;
    }

    @Override
    public Action.Result execute(String s, WatchExecutionContext watchExecutionContext, Payload payload) throws Exception {
        return null;
    }
}
