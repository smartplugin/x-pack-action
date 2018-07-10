package org.elasticsearch.xpack.watcher.actions.wechat;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.xpack.common.text.TextTemplate;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.watcher.actions.Action;
import org.elasticsearch.xpack.watcher.actions.ExecutableAction;
import org.elasticsearch.xpack.watcher.execution.WatchExecutionContext;
import org.elasticsearch.xpack.watcher.support.Variables;
import org.elasticsearch.xpack.watcher.watch.Payload;

import java.util.Map;

/**
 * @author chendong
 * @date 2018-07-09
 * @description
 */

public class ExecutableWechatAction extends ExecutableAction<WechatAction> {

    private final TextTemplateEngine engine;

    public ExecutableWechatAction(final WechatAction action, final Logger logger, final TextTemplateEngine templateEngine) {
        super(action, logger);
        this.engine = templateEngine;
    }

    @Override
    public Action.Result execute(String actionId, WatchExecutionContext watchExecutionContext, Payload payload) throws Exception {
        final Map<String, Object> model = Variables.createCtxModel(watchExecutionContext, payload);
        final String loggedText = engine.render(action.text, model);
        if (watchExecutionContext.simulateAction(actionId)) {
            return new WechatAction.Result.Simulated(loggedText);
        }
        action.send(loggedText,logger);
        return new WechatAction.Result.Success(loggedText);
    }


}
