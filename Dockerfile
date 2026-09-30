FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac Server.java
CMD ["java","Server"]
