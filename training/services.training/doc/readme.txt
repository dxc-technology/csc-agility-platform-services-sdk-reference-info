This is a jar based way to deploy and update OSGI bundle.

To build, from services.training folder run: ant clean bundle
copy jar (demo.adapter-1.0.0.jar) to deploy directory 
example copy using scp command: scp demo.adapter-1.0.0.jar smadmin@192.168.71.239:/opt/agility-platform/deploy

Option #1 - redeploying bundle
If issues are seen after re-deploying an adapter with major changes, try restarting the service framework bundle:
   $ ssh -p 8022 karaf@127.0.0.1
   karaf@root()> list -s | grep framework
       149 | Active | 60 | 1.0.0 |      
         com.servicemesh.agility.adapters.service.framework
   karaf@root()> restart 149

Option #2 - redeploying bundle
Restart agility cleaning karaf cache (there are other options to update agility live)
1. stop agility "sudo /etc/init.d/agility-platform stop"
2. delete data directory "rm -rf /opt/agility-platform/data"
3. start agility "sudo /etc/init.d/agility-platform start" (make sure agility fully starts before continuing)

command to see agility log: tail -f /opt/agility-platform/log/agility.log

How to examine bundles in karaf
command > ssh -l karaf -p 8022 127.0.0.1
password: karaf	
command > list | grep Demo