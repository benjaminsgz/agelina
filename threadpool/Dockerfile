FROM maven:3.9-eclipse-temurin-17
WORKDIR /app

# 复制整个 threadpool 目录进入镜像
COPY . .

# 执行 install 并启动项目
# -pl 指定运行 threadpool-dag-demo 模块
ENTRYPOINT ["sh", "-c", "mvn clean install -DskipTests && mvn -pl threadpool-dag-demo spring-boot:run"]
