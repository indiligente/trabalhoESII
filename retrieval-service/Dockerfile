#imagem para o processo de build
FROM eclipse-temurin:25-jdk-alpine AS build
#diretório de trabalho
WORKDIR /workspace/app
#copiar arquivos do maven
COPY mvnw .
COPY .mvn .mvn
#copiar o arquivo com as dependências
COPY pom.xml .
#fazer download de todas dependências sem gerar o JAR final
RUN ./mvnw dependency:go-offline
#copiar os arquivos-fonte
COPY src src
#realizar o build da aplicação sem executar os testes
RUN ./mvnw clean install -DskipTests

#imagem para implantação em produção
FROM eclipse-temurin:21-jre-alpine AS final
#diretório de trabalho
WORKDIR /workspace/app
#copiar os arquivos para execução no conteiner
COPY --from=build /workspace/app/target/*.jar /workspace/app/app.jar
#expor a porta utilizada na aplicação
EXPOSE 8766
#executar o JAR
ENTRYPOINT ["java", "-jar", "/workspace/app/app.jar"]