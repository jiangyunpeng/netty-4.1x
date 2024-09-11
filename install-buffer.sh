mvn clean package -Dmaven.test.skip=true -pl buffer,common

cp buffer/target/netty-buffer-4.1.49.Final.jar  /data/program/es/elasticsearch-7.11.2/modules/transport-netty4/netty-buffer-4.1.49.Final.jar
cp buffer/target/netty-buffer-4.1.49.Final.jar /data/program/es/elasticsearch-7.11.2/modules/x-pack-core/netty-buffer-4.1.49.Final.jar 

cp common/target/netty-common-4.1.49.Final.jar /data/program/es/elasticsearch-7.11.2/modules/transport-netty4/netty-common-4.1.49.Final.jar
cp common/target/netty-common-4.1.49.Final.jar  /data/program/es/elasticsearch-7.11.2/modules/x-pack-core/netty-common-4.1.49.Final.jar


cp transport/target/netty-transport-4.1.49.Final.jar /data/program/es/elasticsearch-7.11.2/modules/transport-netty4/netty-transport-4.1.49.Final.jar
cp transport/target/netty-transport-4.1.49.Final.jar  /data/program/es/elasticsearch-7.11.2/modules/x-pack-core/netty-transport-4.1.49.Final.jar


cp /System/Volumes/Data/work/dist/branch/my/netty-4.1.49/buffer/target/netty-buffer-4.1.49.Final.jar /Users/bairen/.gradle/caches/modules-2/files-2.1/io.netty/netty-buffer/4.1.49.Final/8e819a81bca88d1e88137336f64531a53db0a4ad/netty-buffer-4.1.49.Final.jar

