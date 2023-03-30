#!/bin/bash

SCRIPT_ABS_PATH=$(readlink -f "$0")
SCRIPT_ABS_DIR=$(dirname $SCRIPT_ABS_PATH)

JAVA_OPTS="-Xmx10g  -Xss100m" $SCRIPT_ABS_DIR/target/universal/stage/bin/appanalyzer -- $@
