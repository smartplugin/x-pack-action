# x-pack-action
x-pack 自定义actions

Extract X-Pack-6.0.0.jar from X-Pack-6.0.0.zip;
Add this jar as dependency in IntelliJ IDEA;
Build the project;
Open X-Pack-6.0.0.jar with ArchieveManager like WinRAR, 7zip;
Copy the generated *.class files into X-Pack-6.0.0.jar according to the path, overwrite the existing ones if needed;
Replace the original jar in ES_HOME/plugins/x-pack with the new X-Pack-5.2.0.jar;
Download java-syslog-client-1.2.1.jar and copy it to ES_HOME/plugins/x-pack;
Add the following lien to ES_HOME/plugins/x-pack/plugin-security.policy: permission java.net.SocketPermission "localhost:0", "listen,resolve";
All the master nodes need to do these steps, data nodes don't need to;
Restart your cluseter and enjoy it!
Here is a simple example:

 PUT _xpack/watcher/watch/syslog_example
{
  "trigger" : {
    "schedule" : { "interval" : "10s" } 
  },
  "actions" : {
    "hello_syslog" : {  
      "syslog" : {
        "app" : "elastic"
        "host" : "127.0.0.1",
        "port" : 514,
        "facility" : "local7",
        "level" : "warning",
        "text" : "executed at {{ctx.execution_time}}" 
      }
    }
  }
P.S:

The params of "syslog" used here are right the default value;
"level" can be: EMERGENCY/ALERT/CRITICAL/ERROR/NOTICE/INFORMATIONAL/DEBUG;
"text" is necessary.
If you reads Chinese, please visit http://blog.csdn.net/mvpboss1004/article/details/70158864
