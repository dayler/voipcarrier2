#!/bin/sh

#Path for SIPP
SIPP_PATH=/Users/lesly/development/sipp/3.3/bin/sipp

#Path for descriptor
UAS_DESC_PATH=descriptor/callee.xml
UAC_DESC_PATH=descriptor/caller.xml

#UAS port
UAS_PORT=5066
#UAS Host
UAS_HOST_IP=127.0.0.1

#UAC port
UAC_PORT=5088
#UAC Host
UAC_HOST_IP=127.0.0.1

#PROXY
PROXY_HOST_IP=127.0.0.1
PROXY_PORT=5060

#enable log = 1
ACTIVE_LOG=1

#External file with info
CSV_FILE=call.csv

#Quantity of simultaneous calls
L=10

#Quantity of calls to be executed
M=200

if [ $ACTIVE_LOG  = 1 ];
then
    #input UAS
    $SIPP_PATH -sf $UAS_DESC_PATH -p $UAS_PORT -i $UAS_HOST_IP  -rsa $PROXY_HOST_IP:$PROXY_PORT $UAC_HOST_IP:$UAC_PORT -trace_err -trace_msg -m $M
else
    #input UAS
    $SIPP_PATH -sf $UAS_DESC_PATH -p $UAS_PORT -i $UAS_HOST_IP  -rsa $PROXY_HOST_IP:$PROXY_PORT $UAC_HOST_IP:$UAC_PORT -m $M
fi;
exit