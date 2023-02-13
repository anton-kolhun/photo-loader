#!/bin/bash
sudo certbot renew

# crontab -e
# * * */10 * * /home/ec2-user/cert_renewal.sh