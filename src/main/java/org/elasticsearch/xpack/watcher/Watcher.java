
package org.elasticsearch.xpack.watcher;

import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.bootstrap.BootstrapCheck;
import org.elasticsearch.cluster.NamedDiff;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MetaData.Custom;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Booleans;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.util.Providers;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry.Entry;
import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.common.logging.LoggerMessageFormat;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.SecureSetting;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.ActionPlugin.ActionHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.SearchScript.Factory;
import org.elasticsearch.threadpool.ExecutorBuilder;
import org.elasticsearch.threadpool.FixedExecutorBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.XPackPlugin;
import org.elasticsearch.xpack.XPackSettings;
import org.elasticsearch.xpack.common.http.HttpClient;
import org.elasticsearch.xpack.common.http.HttpRequestTemplate.Parser;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.notification.email.EmailService;
import org.elasticsearch.xpack.notification.email.attachment.EmailAttachmentsParser;
import org.elasticsearch.xpack.notification.hipchat.HipChatService;
import org.elasticsearch.xpack.notification.jira.JiraService;
import org.elasticsearch.xpack.notification.pagerduty.PagerDutyService;
import org.elasticsearch.xpack.notification.slack.SlackService;
import org.elasticsearch.xpack.security.InternalClient;
import org.elasticsearch.xpack.security.crypto.CryptoService;
import org.elasticsearch.xpack.watcher.actions.ActionFactory;
import org.elasticsearch.xpack.watcher.actions.ActionRegistry;
import org.elasticsearch.xpack.watcher.actions.email.EmailActionFactory;
import org.elasticsearch.xpack.watcher.actions.hipchat.HipChatActionFactory;
import org.elasticsearch.xpack.watcher.actions.index.IndexActionFactory;
import org.elasticsearch.xpack.watcher.actions.jira.JiraActionFactory;
import org.elasticsearch.xpack.watcher.actions.logging.LoggingActionFactory;
import org.elasticsearch.xpack.watcher.actions.message.MessageActionFactory;
import org.elasticsearch.xpack.watcher.actions.pagerduty.PagerDutyActionFactory;
import org.elasticsearch.xpack.watcher.actions.slack.SlackActionFactory;
import org.elasticsearch.xpack.watcher.actions.syslog.SyslogActionFactory;
import org.elasticsearch.xpack.watcher.actions.webhook.WebhookActionFactory;
import org.elasticsearch.xpack.watcher.actions.wechat.WechatActionFactory;
import org.elasticsearch.xpack.watcher.client.WatcherClient;
import org.elasticsearch.xpack.watcher.condition.AlwaysCondition;
import org.elasticsearch.xpack.watcher.condition.ArrayCompareCondition;
import org.elasticsearch.xpack.watcher.condition.CompareCondition;
import org.elasticsearch.xpack.watcher.condition.ConditionFactory;
import org.elasticsearch.xpack.watcher.condition.ConditionRegistry;
import org.elasticsearch.xpack.watcher.condition.NeverCondition;
import org.elasticsearch.xpack.watcher.condition.ScriptCondition;
import org.elasticsearch.xpack.watcher.execution.AsyncTriggerEventConsumer;
import org.elasticsearch.xpack.watcher.execution.ExecutionService;
import org.elasticsearch.xpack.watcher.execution.InternalWatchExecutor;
import org.elasticsearch.xpack.watcher.execution.TriggeredWatchStore;
import org.elasticsearch.xpack.watcher.execution.WatchExecutor;
import org.elasticsearch.xpack.watcher.history.HistoryStore;
import org.elasticsearch.xpack.watcher.input.InputFactory;
import org.elasticsearch.xpack.watcher.input.InputRegistry;
import org.elasticsearch.xpack.watcher.input.chain.ChainInputFactory;
import org.elasticsearch.xpack.watcher.input.http.HttpInputFactory;
import org.elasticsearch.xpack.watcher.input.none.NoneInputFactory;
import org.elasticsearch.xpack.watcher.input.search.SearchInputFactory;
import org.elasticsearch.xpack.watcher.input.simple.SimpleInputFactory;
import org.elasticsearch.xpack.watcher.rest.action.RestAckWatchAction;
import org.elasticsearch.xpack.watcher.rest.action.RestActivateWatchAction;
import org.elasticsearch.xpack.watcher.rest.action.RestDeleteWatchAction;
import org.elasticsearch.xpack.watcher.rest.action.RestExecuteWatchAction;
import org.elasticsearch.xpack.watcher.rest.action.RestGetWatchAction;
import org.elasticsearch.xpack.watcher.rest.action.RestHijackOperationAction;
import org.elasticsearch.xpack.watcher.rest.action.RestPutWatchAction;
import org.elasticsearch.xpack.watcher.rest.action.RestWatchServiceAction;
import org.elasticsearch.xpack.watcher.rest.action.RestWatcherStatsAction;
import org.elasticsearch.xpack.watcher.support.WatcherIndexTemplateRegistry;
import org.elasticsearch.xpack.watcher.support.search.WatcherSearchTemplateService;
import org.elasticsearch.xpack.watcher.transform.TransformFactory;
import org.elasticsearch.xpack.watcher.transform.TransformRegistry;
import org.elasticsearch.xpack.watcher.transform.script.ScriptTransformFactory;
import org.elasticsearch.xpack.watcher.transform.search.SearchTransformFactory;
import org.elasticsearch.xpack.watcher.transport.actions.ack.AckWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.ack.TransportAckWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.activate.ActivateWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.activate.TransportActivateWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.delete.DeleteWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.delete.TransportDeleteWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.execute.ExecuteWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.execute.TransportExecuteWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.get.GetWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.get.TransportGetWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.put.PutWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.put.TransportPutWatchAction;
import org.elasticsearch.xpack.watcher.transport.actions.service.TransportWatcherServiceAction;
import org.elasticsearch.xpack.watcher.transport.actions.service.WatcherServiceAction;
import org.elasticsearch.xpack.watcher.transport.actions.stats.OldTransportWatcherStatsAction;
import org.elasticsearch.xpack.watcher.transport.actions.stats.OldWatcherStatsAction;
import org.elasticsearch.xpack.watcher.transport.actions.stats.TransportWatcherStatsAction;
import org.elasticsearch.xpack.watcher.transport.actions.stats.WatcherStatsAction;
import org.elasticsearch.xpack.watcher.trigger.TriggerEngine;
import org.elasticsearch.xpack.watcher.trigger.TriggerEvent;
import org.elasticsearch.xpack.watcher.trigger.TriggerService;
import org.elasticsearch.xpack.watcher.trigger.manual.ManualTriggerEngine;
import org.elasticsearch.xpack.watcher.trigger.schedule.ScheduleRegistry;
import org.elasticsearch.xpack.watcher.trigger.schedule.engine.TickerScheduleTriggerEngine;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class Watcher implements ActionPlugin {
    public static final Setting<String> INDEX_WATCHER_TEMPLATE_VERSION_SETTING;
    public static final Setting<Boolean> ENCRYPT_SENSITIVE_DATA_SETTING;
    public static final Setting<InputStream> ENCRYPTION_KEY_SETTING;
    public static final Setting<TimeValue> MAX_STOP_TIMEOUT_SETTING;
    public static final ScriptContext<Factory> SCRIPT_SEARCH_CONTEXT;
    public static final ScriptContext<org.elasticsearch.script.ExecutableScript.Factory> SCRIPT_EXECUTABLE_CONTEXT;
    public static final ScriptContext<org.elasticsearch.script.TemplateScript.Factory> SCRIPT_TEMPLATE_CONTEXT;
    private static final Logger logger;
    private WatcherIndexingListener listener;
    protected final Settings settings;
    protected final boolean transportClient;
    protected final boolean enabled;

    public List<Entry> getNamedWriteables() {
        List<Entry> entries = new ArrayList();
        entries.add(new Entry(Custom.class, "watcher", WatcherMetaData::new));
        entries.add(new Entry(NamedDiff.class, "watcher", WatcherMetaData::readDiffFrom));
        return entries;
    }

    public List<org.elasticsearch.common.xcontent.NamedXContentRegistry.Entry> getNamedXContent() {
        List<org.elasticsearch.common.xcontent.NamedXContentRegistry.Entry> entries = new ArrayList();
        entries.add(new org.elasticsearch.common.xcontent.NamedXContentRegistry.Entry(Custom.class, new ParseField("watcher", new String[0]), WatcherMetaData::fromXContent));
        return entries;
    }

    public Watcher(Settings settings) {
        this.settings = settings;
        this.enabled = (Boolean)XPackSettings.WATCHER_ENABLED.get(settings);
        this.transportClient = XPackPlugin.transportClientMode(settings);
        if (this.enabled && !this.transportClient) {
            validAutoCreateIndex(settings, logger);
        }

    }

    public Collection<Object> createComponents(Clock clock, ScriptService scriptService, InternalClient internalClient, XPackLicenseState licenseState, HttpClient httpClient, Parser httpTemplateParser, ThreadPool threadPool, ClusterService clusterService, CryptoService cryptoService, NamedXContentRegistry xContentRegistry, Collection<Object> components) {
        if (!this.enabled) {
            return Collections.emptyList();
        } else {
            Map<String, ConditionFactory> parsers = new HashMap();
            parsers.put("always", (c, id, p) -> {
                return AlwaysCondition.parse(id, p);
            });
            parsers.put("never", (c, id, p) -> {
                return NeverCondition.parse(id, p);
            });
            parsers.put("array_compare", (c, id, p) -> {
                return ArrayCompareCondition.parse(c, id, p);
            });
            parsers.put("compare", (c, id, p) -> {
                return CompareCondition.parse(c, id, p);
            });
            parsers.put("script", (c, id, p) -> {
                return ScriptCondition.parse(scriptService, id, p);
            });
            ConditionRegistry conditionRegistry = new ConditionRegistry(Collections.unmodifiableMap(parsers), clock);
            Map<String, TransformFactory> transformFactories = new HashMap();
            transformFactories.put("script", new ScriptTransformFactory(this.settings, scriptService));
            transformFactories.put("search", new SearchTransformFactory(this.settings, internalClient, xContentRegistry, scriptService));
            TransformRegistry transformRegistry = new TransformRegistry(this.settings, Collections.unmodifiableMap(transformFactories));
            Map<String, ActionFactory> actionFactoryMap = new HashMap();
            TextTemplateEngine templateEngine = (TextTemplateEngine)this.getService(TextTemplateEngine.class, components);
            actionFactoryMap.put("email", new EmailActionFactory(this.settings, (EmailService)this.getService(EmailService.class, components), templateEngine, (EmailAttachmentsParser)this.getService(EmailAttachmentsParser.class, components)));
            actionFactoryMap.put("webhook", new WebhookActionFactory(this.settings, httpClient, (Parser)this.getService(Parser.class, components), templateEngine));
            actionFactoryMap.put("index", new IndexActionFactory(this.settings, internalClient));
            actionFactoryMap.put("logging", new LoggingActionFactory(this.settings, templateEngine));
            actionFactoryMap.put("hipchat", new HipChatActionFactory(this.settings, templateEngine, (HipChatService)this.getService(HipChatService.class, components)));
            actionFactoryMap.put("jira", new JiraActionFactory(this.settings, templateEngine, (JiraService)this.getService(JiraService.class, components)));
            actionFactoryMap.put("slack", new SlackActionFactory(this.settings, templateEngine, (SlackService)this.getService(SlackService.class, components)));
            actionFactoryMap.put("pagerduty", new PagerDutyActionFactory(this.settings, templateEngine, (PagerDutyService)this.getService(PagerDutyService.class, components)));

            actionFactoryMap.put("syslog", new SyslogActionFactory(this.settings, templateEngine));
            actionFactoryMap.put("wechat", new WechatActionFactory(this.settings, templateEngine));
            //TODO message
//            actionFactoryMap.put("message", new MessageActionFactory(this.settings, templateEngine));

            ActionRegistry registry = new ActionRegistry(actionFactoryMap, conditionRegistry, transformRegistry, clock, licenseState);
            Map<String, InputFactory> inputFactories = new HashMap();
            inputFactories.put("search", new SearchInputFactory(this.settings, internalClient, xContentRegistry, scriptService));
            inputFactories.put("simple", new SimpleInputFactory(this.settings));
            inputFactories.put("http", new HttpInputFactory(this.settings, httpClient, templateEngine, httpTemplateParser));
            inputFactories.put("none", new NoneInputFactory(this.settings));
            InputRegistry inputRegistry = new InputRegistry(this.settings, inputFactories);
            inputFactories.put("chain", new ChainInputFactory(this.settings, inputRegistry));
            WatcherClient watcherClient = new WatcherClient(internalClient);
            HistoryStore historyStore = new HistoryStore(this.settings, internalClient);
            Set<org.elasticsearch.xpack.watcher.trigger.schedule.Schedule.Parser> scheduleParsers = new HashSet();
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.CronSchedule.Parser());
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.DailySchedule.Parser());
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.HourlySchedule.Parser());
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.IntervalSchedule.Parser());
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.MonthlySchedule.Parser());
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.WeeklySchedule.Parser());
            scheduleParsers.add(new org.elasticsearch.xpack.watcher.trigger.schedule.YearlySchedule.Parser());
            ScheduleRegistry scheduleRegistry = new ScheduleRegistry(scheduleParsers);
            TriggerEngine manualTriggerEngine = new ManualTriggerEngine();
            TriggerEngine configuredTriggerEngine = this.getTriggerEngine(clock, scheduleRegistry);
            Set<TriggerEngine> triggerEngines = new HashSet();
            triggerEngines.add(manualTriggerEngine);
            triggerEngines.add(configuredTriggerEngine);
            TriggerService triggerService = new TriggerService(this.settings, triggerEngines);
            org.elasticsearch.xpack.watcher.execution.TriggeredWatch.Parser triggeredWatchParser = new org.elasticsearch.xpack.watcher.execution.TriggeredWatch.Parser(this.settings, triggerService);
            TriggeredWatchStore triggeredWatchStore = new TriggeredWatchStore(this.settings, internalClient, triggeredWatchParser);
            WatcherSearchTemplateService watcherSearchTemplateService = new WatcherSearchTemplateService(this.settings, scriptService, xContentRegistry);
            WatchExecutor watchExecutor = this.getWatchExecutor(threadPool);
            org.elasticsearch.xpack.watcher.watch.Watch.Parser watchParser = new org.elasticsearch.xpack.watcher.watch.Watch.Parser(this.settings, triggerService, registry, inputRegistry, cryptoService, clock);
            ExecutionService executionService = new ExecutionService(this.settings, historyStore, triggeredWatchStore, watchExecutor, clock, threadPool, watchParser, clusterService, internalClient);
            Consumer<Iterable<TriggerEvent>> triggerEngineListener = this.getTriggerEngineListener(executionService);
            triggerService.register(triggerEngineListener);
            WatcherIndexTemplateRegistry watcherIndexTemplateRegistry = new WatcherIndexTemplateRegistry(this.settings, clusterService, threadPool, internalClient);
            WatcherService watcherService = new WatcherService(this.settings, triggerService, triggeredWatchStore, executionService, watchParser, internalClient);
            WatcherLifeCycleService watcherLifeCycleService = new WatcherLifeCycleService(this.settings, threadPool, clusterService, watcherService);
            this.listener = new WatcherIndexingListener(this.settings, watchParser, clock, triggerService);
            clusterService.addListener(this.listener);
            return Arrays.asList(registry, watcherClient, inputRegistry, historyStore, triggerService, triggeredWatchParser, watcherLifeCycleService, executionService, triggerEngineListener, watcherService, watchParser, configuredTriggerEngine, triggeredWatchStore, watcherSearchTemplateService, watcherIndexTemplateRegistry);
        }
    }

    protected TriggerEngine getTriggerEngine(Clock clock, ScheduleRegistry scheduleRegistry) {
        return new TickerScheduleTriggerEngine(this.settings, scheduleRegistry, clock);
    }

    protected WatchExecutor getWatchExecutor(ThreadPool threadPool) {
        return new InternalWatchExecutor(threadPool);
    }

    protected Consumer<Iterable<TriggerEvent>> getTriggerEngineListener(ExecutionService executionService) {
        return new AsyncTriggerEventConsumer(this.settings, executionService);
    }

    private <T> T getService(Class<T> serviceClass, Collection<Object> services) {
        List<Object> collect = (List)services.stream().filter((o) -> {
            return o.getClass() == serviceClass;
        }).collect(Collectors.toList());
        if (collect.isEmpty()) {
            throw new IllegalArgumentException("no service for class " + serviceClass.getName());
        } else if (collect.size() > 1) {
            throw new IllegalArgumentException("more than one service for class " + serviceClass.getName());
        } else {
            return (T)collect.get(0);
        }
    }

    public Collection<Module> nodeModules() {
        List<Module> modules = new ArrayList();
        modules.add((b) -> {
            XPackPlugin.bindFeatureSet(b, WatcherFeatureSet.class);
            if (this.transportClient || !this.enabled) {
                b.bind(WatcherService.class).toProvider(Providers.of((WatcherService)null));
            }

        });
        return modules;
    }

    public Settings additionalSettings() {
        return Settings.EMPTY;
    }

    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = new ArrayList();
        settings.add(INDEX_WATCHER_TEMPLATE_VERSION_SETTING);
        settings.add(MAX_STOP_TIMEOUT_SETTING);
        settings.add(ExecutionService.DEFAULT_THROTTLE_PERIOD_SETTING);
        settings.add(TickerScheduleTriggerEngine.TICKER_INTERVAL_SETTING);
        settings.add(Setting.intSetting("xpack.watcher.execution.scroll.size", 0, new Property[]{Property.NodeScope}));
        settings.add(Setting.intSetting("xpack.watcher.watch.scroll.size", 0, new Property[]{Property.NodeScope}));
        settings.add(ENCRYPT_SENSITIVE_DATA_SETTING);
        settings.add(ENCRYPTION_KEY_SETTING);
        settings.add(Setting.simpleString("xpack.watcher.internal.ops.search.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.internal.ops.bulk.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.internal.ops.index.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.actions.index.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.actions.bulk.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.index.rest.direct_access", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.input.search.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.transform.search.default_timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.execution.scroll.timeout", new Property[]{Property.NodeScope}));
        settings.add(Setting.simpleString("xpack.watcher.start_immediately", new Property[]{Property.NodeScope}));
        CryptoService.addSettings(settings);
        return settings;
    }

    public List<ExecutorBuilder<?>> getExecutorBuilders(Settings settings) {
        if (this.enabled) {
            FixedExecutorBuilder builder = new FixedExecutorBuilder(settings, "watcher", 5 * EsExecutors.numberOfProcessors(settings), 1000, "xpack.watcher.thread_pool");
            return Collections.singletonList(builder);
        } else {
            return Collections.emptyList();
        }
    }

    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return !this.enabled ? Collections.emptyList() : Arrays.asList(new ActionHandler(PutWatchAction.INSTANCE, TransportPutWatchAction.class, new Class[0]), new ActionHandler(DeleteWatchAction.INSTANCE, TransportDeleteWatchAction.class, new Class[0]), new ActionHandler(GetWatchAction.INSTANCE, TransportGetWatchAction.class, new Class[0]), new ActionHandler(WatcherStatsAction.INSTANCE, TransportWatcherStatsAction.class, new Class[0]), new ActionHandler(OldWatcherStatsAction.INSTANCE, OldTransportWatcherStatsAction.class, new Class[0]), new ActionHandler(AckWatchAction.INSTANCE, TransportAckWatchAction.class, new Class[0]), new ActionHandler(ActivateWatchAction.INSTANCE, TransportActivateWatchAction.class, new Class[0]), new ActionHandler(WatcherServiceAction.INSTANCE, TransportWatcherServiceAction.class, new Class[0]), new ActionHandler(ExecuteWatchAction.INSTANCE, TransportExecuteWatchAction.class, new Class[0]));
    }

    public List<RestHandler> getRestHandlers(Settings settings, RestController restController, ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings, SettingsFilter settingsFilter, IndexNameExpressionResolver indexNameExpressionResolver, Supplier<DiscoveryNodes> nodesInCluster) {
        return !this.enabled ? Collections.emptyList() : Arrays.asList(new RestPutWatchAction(settings, restController), new RestDeleteWatchAction(settings, restController), new RestWatcherStatsAction(settings, restController), new RestGetWatchAction(settings, restController), new RestWatchServiceAction(settings, restController), new RestAckWatchAction(settings, restController), new RestActivateWatchAction(settings, restController), new RestExecuteWatchAction(settings, restController), new RestHijackOperationAction(settings, restController));
    }

    public void onIndexModule(IndexModule module) {
        if (this.enabled && !this.transportClient) {
            assert this.listener != null;

            if (module.getIndex().getName().startsWith(".watches")) {
                module.addIndexOperationListener(this.listener);
            }

        }
    }

    static void validAutoCreateIndex(Settings settings, Logger logger) {
        String value = settings.get("action.auto_create_index");
        if (value != null) {
            String errorMessage = LoggerMessageFormat.format("the [action.auto_create_index] setting value [{}] is too restrictive. disable [action.auto_create_index] or set it to [{}, {}, {}*]", new Object[]{value, ".watches", ".triggered_watches", ".watcher-history-"});
            if (Booleans.isFalse(value)) {
                throw new IllegalArgumentException(errorMessage);
            } else if (!Booleans.isTrue(value)) {
                String[] matches = Strings.commaDelimitedListToStringArray(value);
                List<String> indices = new ArrayList();
                indices.add(".watches");
                indices.add(".triggered_watches");
                DateTime now = new DateTime(DateTimeZone.UTC);
                indices.add(HistoryStore.getHistoryIndexNameForTime(now));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusDays(1)));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(1)));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(2)));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(3)));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(4)));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(5)));
                indices.add(HistoryStore.getHistoryIndexNameForTime(now.plusMonths(6)));
                Iterator var7 = indices.iterator();

                boolean matched;
                do {
                    if (!var7.hasNext()) {
                        logger.warn("the [action.auto_create_index] setting is configured to be restrictive [{}].  for the next 6 months daily history indices are allowed to be created, but please make sure that any future history indices after 6 months with the pattern [.watcher-history-YYYY.MM.dd] are allowed to be created", value);
                        return;
                    }

                    String index = (String)var7.next();
                    matched = false;
                    String[] var10 = matches;
                    int var11 = matches.length;

                    for(int var12 = 0; var12 < var11; ++var12) {
                        String match = var10[var12];
                        char c = match.charAt(0);
                        if (c == '-') {
                            if (Regex.simpleMatch(match.substring(1), index)) {
                                throw new IllegalArgumentException(errorMessage);
                            }
                        } else if (c == '+') {
                            if (Regex.simpleMatch(match.substring(1), index)) {
                                matched = true;
                                break;
                            }
                        } else if (Regex.simpleMatch(match, index)) {
                            matched = true;
                            break;
                        }
                    }
                } while(matched);

                throw new IllegalArgumentException(errorMessage);
            }
        }
    }

    public UnaryOperator<Map<String, IndexTemplateMetaData>> getIndexTemplateMetaDataUpgrader() {
        return (map) -> {
            map.keySet().removeIf((name) -> {
                return name.startsWith("watch_history_");
            });
            return map;
        };
    }

    public List<BootstrapCheck> getBootstrapChecks() {
        return Collections.singletonList(new EncryptSensitiveDataBootstrapCheck(new Environment(this.settings)));
    }

    static {
        INDEX_WATCHER_TEMPLATE_VERSION_SETTING = new Setting("index.xpack.watcher.template.version", "", Function.identity(), new Property[]{Property.IndexScope});
        ENCRYPT_SENSITIVE_DATA_SETTING = Setting.boolSetting("xpack.watcher.encrypt_sensitive_data", false, new Property[]{Property.NodeScope});
        ENCRYPTION_KEY_SETTING = SecureSetting.secureFile("xpack.watcher.encryption_key", (Setting)null, new Property[0]);
        MAX_STOP_TIMEOUT_SETTING = Setting.timeSetting("xpack.watcher.stop.timeout", TimeValue.timeValueSeconds(30L), new Property[]{Property.NodeScope});
        SCRIPT_SEARCH_CONTEXT = new ScriptContext("xpack", Factory.class);
        SCRIPT_EXECUTABLE_CONTEXT = new ScriptContext("xpack_executable", org.elasticsearch.script.ExecutableScript.Factory.class);
        SCRIPT_TEMPLATE_CONTEXT = new ScriptContext("xpack_template", org.elasticsearch.script.TemplateScript.Factory.class);
        logger = Loggers.getLogger(Watcher.class);
    }
}
