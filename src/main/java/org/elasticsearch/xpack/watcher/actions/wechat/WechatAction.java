package org.elasticsearch.xpack.watcher.actions.wechat;


import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.common.text.TextTemplate;
import org.elasticsearch.xpack.watcher.actions.Action;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author chendong
 * @date 2018-07-09
 * @description
 */

public class WechatAction implements Action {

    final String secret;
    final String token;
    final String key;
    final String title;
    final String content;
    final String remark;
    final TextTemplate text;

    public WechatAction(final String secret, final String token, final String key, final String title, final String content, final String remark,final TextTemplate text) {
        this.secret = secret;
        this.token = token;
        this.key = key;
        this.title = title;
        this.content = content;
        this.remark=remark;
        this.text = text;
    }

    @Override
    public String type() {
        return "wechat";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WechatAction that = (WechatAction) o;
        return Objects.equals(secret, that.secret) &&
                Objects.equals(token, that.token) &&
                Objects.equals(key, that.key) &&
                Objects.equals(title, that.title) &&
                Objects.equals(content, that.content) &&
                Objects.equals(remark, that.remark) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {

        return Objects.hash(secret, token, key, title, content, remark, text);
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
        builder.startObject();
        builder.field(Field.SECRET.getPreferredName(), secret);
        builder.field(Field.TOKEN.getPreferredName(), token);
        builder.field(Field.KEY.getPreferredName(), key);
        builder.field(Field.TITLE.getPreferredName(), title);
        builder.field(Field.CONTENT.getPreferredName(), content);
        builder.field(Field.REMARK.getPreferredName(), remark);
        builder.field(Field.TEXT.getPreferredName(), text, params);
        return builder.endObject();
    }

    public static Builder builder(final String secret, final String token, final String key, final String title, final String content, final String remark,final TextTemplate text) {
        return new Builder(new WechatAction(secret, token, key ,title, content, remark,text));
    }

    public static class Builder implements Action.Builder<WechatAction> {
        final WechatAction action;
        public Builder(final WechatAction action) {
            this.action = action;
        }

        @Override
        public WechatAction build() {
            return action;
        }
    }



    public static WechatAction parse(final String watchId, final String actionId, final XContentParser parser) throws IOException{
        String secret = "xxxx-xxxx-xxx";
        String token = "xxx-xxx-xxx-xxx-xxx";
        String key = "notic";
        String title = "test";
        String content = "this is a test wechat message for you ";
        String remark ="please ensure that you put the right info ";
        String currentFieldName = null;
        XContentParser.Token sysToken;
        TextTemplate text = null;
        while ((sysToken = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (sysToken == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (Field.SECRET.match(currentFieldName)) {
                if (sysToken != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "wechat", watchId, actionId, WechatAction.Field.SECRET.getPreferredName(), sysToken });
                }
                secret = parser.text();
            } else if (Field.TOKEN.match(currentFieldName)) {
                if (sysToken != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "wechat", watchId, actionId, WechatAction.Field.TOKEN.getPreferredName(), sysToken });
                }
                token = parser.text();
            } else if (Field.KEY.match(currentFieldName)) {
                if (sysToken != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type int, but found [{}] instead", new Object[] { "wechat", watchId, actionId, WechatAction.Field.KEY.getPreferredName(), sysToken });
                }
                key = parser.text();
            } else if (Field.TITLE.match(currentFieldName)) {
                if (sysToken != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "wechat", watchId, actionId, WechatAction.Field.TITLE.getPreferredName(), sysToken });
                }
                title = parser.text();
            } else if (Field.CONTENT.match(currentFieldName)) {
                if (sysToken != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "wechat", watchId, actionId, WechatAction.Field.CONTENT.getPreferredName(), sysToken });
                }
                content = parser.text();
            }else if (Field.REMARK.match(currentFieldName)) {
                if (sysToken != XContentParser.Token.VALUE_STRING) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. expected [{}] to be of type string, but found [{}] instead", new Object[] { "wechat", watchId, actionId, WechatAction.Field.REMARK.getPreferredName(), sysToken });
                }
                remark = parser.text();
            }  else if (WechatAction.Field.TEXT.match(currentFieldName)) {
                try {
                    text = TextTemplate.parse(parser);
                } catch (ElasticsearchParseException pe) {
                    throw new ElasticsearchParseException("failed to parse [{}] action [{}/{}]. failed to parse [{}] field", pe, new Object[]{"wechat", watchId, actionId, WechatAction.Field.TEXT.getPreferredName()});
                }
            }
        }
        return new WechatAction(secret, token, key, title, content,remark, text);
    }



    public void send(final String loggedText,final Logger logger) throws Exception {
        CloseableHttpClient client = AccessController.doPrivileged((PrivilegedExceptionAction<CloseableHttpClient>) () -> HttpClients.createDefault());
        HttpPost post = AccessController.doPrivileged((PrivilegedExceptionAction<HttpPost>) () -> new HttpPost("http://u.ifeige.cn/api/send_message"));
        List<NameValuePair> formList = new ArrayList<>();
        formList.add(new BasicNameValuePair(Field.SECRET.getPreferredName(), secret));
        formList.add(new BasicNameValuePair(Field.TOKEN.getPreferredName(), token));
        formList.add(new BasicNameValuePair(Field.KEY.getPreferredName(), key));
        formList.add(new BasicNameValuePair(Field.TITLE.getPreferredName(), title));
        formList.add(new BasicNameValuePair(Field.CONTENT.getPreferredName(), content+loggedText));
        formList.add(new BasicNameValuePair(Field.REMARK.getPreferredName(), remark));
        formList.add(new BasicNameValuePair("time", "time()"));
        logger.info("@@we get the params: secret:{} , token:{} ,key:{} , title: {}, content:{} ,text:{} ,remark: {}",secret,token,key,title,content,text,remark);

        StringEntity entity = new UrlEncodedFormEntity(formList, "utf-8");
        post.setEntity(entity);
        CloseableHttpResponse response = client.execute(post);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            System.out.println("statusCode = " + statusCode);
            String resStr = EntityUtils.toString(response.getEntity());
            logger.info("Success! the result with back is :{}",resStr);
        } else {
            logger.warn("Bad request,did not connect the server of feiGeKuaiXin,please check your account info or network health !:{}",statusCode);
        }


    }



    interface Field extends Action.Field {
        ParseField SECRET = new ParseField("secret", new String[0]);
        ParseField TOKEN = new ParseField("token", new String[0]);
        ParseField KEY = new ParseField("key", new String[0]);
        ParseField TITLE = new ParseField("title", new String[0]);
        ParseField CONTENT = new ParseField("content", new String[0]);
        ParseField REMARK = new ParseField("remark", new String[0]);
        ParseField TEXT = new ParseField("text", new String[0]);
        ParseField LOGGED_TEXT = new ParseField("logged_text", new String[0]);
    }


    public interface Result {
        class Success extends Action.Result implements WechatAction.Result {
            private final String loggedText;

            public Success(final String loggedText) {
                super("wechat", Status.SUCCESS);
                this.loggedText = loggedText;
            }

            public String getLoggedText() {
                return loggedText;
            }

            public XContentBuilder toXContent(final XContentBuilder builder, final ToXContent.Params params) throws IOException {
                return builder.startObject(type).field(WechatAction.Field.LOGGED_TEXT.getPreferredName(), loggedText).endObject();
            }
        }

        class Simulated extends Action.Result implements WechatAction.Result {
            private final String loggedText;

            public Simulated(final String loggedText) {
                super("wechat", Status.SUCCESS);
                this.loggedText = loggedText;
            }

            public String getLoggedText() {
                return loggedText;
            }

            public XContentBuilder toXContent(final XContentBuilder builder, final ToXContent.Params params) throws IOException {
                return builder.startObject(type).field(WechatAction.Field.LOGGED_TEXT.getPreferredName(), loggedText).endObject();
            }
        }
    }

}
