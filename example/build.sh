#!/bin/bash
docker image rm -f infinispan-example:0.0.1
docker build -t infinispan-example:0.0.1 .