# Coklu asama (multi-stage) derleme -- calisma zamani imaji Maven/JDK
# derleme araclarini icermez, sadece calistirilmis jar + kucuk bir JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Bagimliliklari kaynak kod degismeden ayri bir katmanda indir -- sadece
# pom.xml degistiginde bu katman yeniden calisir, her build'de degil.
RUN mvn -q dependency:go-offline -B
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render gibi kucuk bellekli (ornegin 512MB) ortamlarda JVM'in tum belle­gi
# tuketip konteyneri OOM ile durdurmasini onlemek icin heap sinirlanir.
ENV JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=128m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
