(defproject lens-workbook "0.1-SNAPSHOT"
  :description "A workbook storage service for Lens."

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [prismatic/plumbing "0.4.2"]
                 [slingshot "0.12.2"]
                 [com.datomic/datomic-pro "0.9.5078"
                  :exclusions [org.slf4j/slf4j-nop
                               org.slf4j/slf4j-log4j12
                               org.apache.httpcomponents/httpclient
                               commons-codec
                               joda-time]]
                 [com.basho.riak/riak-client "1.4.4"
                  :exclusions [com.fasterxml.jackson.core/jackson-annotations
                               com.fasterxml.jackson.core/jackson-core
                               com.fasterxml.jackson.core/jackson-databind]]
                 [org.apache.curator/curator-framework "2.6.0"
                  :exclusions [io.netty/netty log4j org.slf4j/slf4j-log4j12]]
                 [http-kit "2.1.18"]
                 [ring/ring-core "1.3.2"
                  :exclusions [ring/ring-core org.clojure/tools.reader]]
                 [bidi "1.18.10" :exclusions [ring/ring-core]]
                 [liberator "0.12.2"]
                 [io.clojure/liberator-transit "0.3.0"
                  :exclusions [com.cognitect/transit-clj]]
                 [ring-middleware-format "0.5.0"
                  :exclusions [org.clojure/tools.reader
                               com.fasterxml.jackson.core/jackson-core
                               com.fasterxml.jackson.core/jackson-annotations
                               com.fasterxml.jackson.core/jackson-databind
                               ring/ring-core ring
                               commons-codec]]
                 [org.clojars.akiel/shortid "0.1"]
                 [clj-time "0.6.0"]
                 [clj-stacktrace "0.2.7"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [ch.qos.logback/logback-classic "1.1.2"]]

  :profiles {:dev
             {:source-paths ["dev"]
              :dependencies [[org.clojure/tools.namespace "0.2.4"]
                             [criterium "0.4.3"]
                             [compojure "1.3.3"]
                             [cursive/datomic-stubs "0.9.5153" :scope "provided"]]
              :global-vars {*print-length* 20}
              :jvm-opts ["-Xmx4g"]}

             :production-run
             {:main lens.core
              :jvm-opts ["-Xmx4g"]}}

  :repl-options {:welcome (do
                            (println "   Docs: (doc function-name-here)")
                            (println "         (find-doc \"part-of-name-here\")")
                            (println "   Exit: Control+D or (exit) or (quit)")
                            (println "  Start: (startup)")
                            (println "Restart: (reset)"))})
