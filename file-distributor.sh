#!/bin/bash

source ~/.bash_profile
nohup java -jar photo-sorter.jar > /home/ec2-user/sorter.log &


# crontab -e
# 0 * * * * /home/ec2-user/file-distributor.sh