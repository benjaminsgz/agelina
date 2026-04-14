#!/bin/sh
set -eu

cd /workspace/ThreadPool/threadpool
mvn -B -DskipTests install

cd threadpool-auth-demo
exec mvn -B spring-boot:run
