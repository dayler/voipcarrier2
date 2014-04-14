#!/bin/sh

# Path for SIPP
SIPP_PATH=/usr/local/Cellar/sipp/3.3/bin/sipp

# Path for descriptor
UAS_DESC_PATH=descriptor/callee.xml
UAC_DESC_PATH=descriptor/caller.xml

# UAS port
UAS_PORT=5066

# UAS Host
UAS_HOST_IP=127.0.0.1

# UAC port
UAC_PORT=5088

# UAC Host
UAC_HOST_IP=127.0.0.1

# PROXY
PROXY_HOST_IP=127.0.0.1
PROXY_PORT=5060

# Enable log = 1
ACTIVE_LOG=1

# External file with info
CSV_FILE=call.csv

# Quantity of simultaneous calls
L=5

# Quantity of calls to be executed
M=400

# Duration call, in milliseconds
CALL_DELAY=300000

if [ $ACTIVE_LOG  = 1 ];
then
    # Input UAC
    $SIPP_PATH -sf $UAC_DESC_PATH -p $UAC_PORT -i $UAC_HOST_IP -rsa $PROXY_HOST_IP:$PROXY_PORT $UAS_HOST_IP:$UAS_PORT -trace_err -trace_msg -inf $CSV_FILE -l $L -m $M -d $CALL_DELAY
else
    # Input UAC
    $SIPP_PATH -sf $UAC_DESC_PATH -p $UAC_PORT -i $UAC_HOST_IP -rsa $PROXY_HOST_IP:$PROXY_PORT $UAS_HOST_IP:$UAS_PORT -inf $CSV_FILE -l $L -m $M -d $CALL_DELAY
fi;
exit