FROM gfredericks/leiningen:java-8-lein-2.5.1

COPY resources /app/resources
COPY src /app/src
COPY project.clj /app/
COPY datomic-pro-0.9.5173.* /root/.m2/repository/com/datomic/datomic-pro/0.9.5173/
COPY docker/start.sh /app/

WORKDIR /app

RUN lein with-profile production,datomic-pro deps
RUN chmod +x start.sh

EXPOSE 80

CMD ["./start.sh"]
