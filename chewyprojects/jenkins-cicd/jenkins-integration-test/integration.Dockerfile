FROM openjdk:25-jdk

WORKDIR /app

RUN mkdir -p /output

ENV JOB_NAME=default-integration-test

CMD ["sh", "-c", "curl -o jenkins-cli.jar http://jenkins:8080/jnlpJars/jenkins-cli.jar && java -jar jenkins-cli.jar -s http://jenkins:8080 build $JOB_NAME -s -v > /output/$JOB_NAME.txt 2>&1"]
