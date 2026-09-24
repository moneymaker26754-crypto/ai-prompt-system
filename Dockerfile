FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 多模块构建：先复制聚合 pom 与各模块 pom 以复用依赖缓存
COPY pom.xml .
COPY app/pom.xml app/pom.xml

RUN mvn -pl app -am dependency:go-offline -B

COPY app/src app/src

RUN mvn -pl app -am clean package \
    -DskipTests \
    -B


FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder \
    /build/app/target/*.jar \
    app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
