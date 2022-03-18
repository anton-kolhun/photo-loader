#!/bin/bash
kill -9 $(ps -aux  | grep -w 'jar photo-webapp-1.0-exec.jar' | grep -v grep | awk {'print $2'})
source ~/.bash_profile
nohup java -Xmx200m -jar photo-webapp-1.0-exec.jar >> /home/ec2-user/logs.log &


# crontab -e
# 0 0 * * * /home/ec2-user/process_restart.sh