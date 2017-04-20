(ns jepsen.kafka
  (:require  [clojure.tools.logging :refer :all]
             [clojure.java.io :as io]
             [clojure.string     :as str]
             [gregor.core :as gregor]
             ;[franzy.clients.consumer.protocols :refer :all]
             ;[franzy.clients.producer.protocols :refer :all]
             ;[franzy.clients.consumer.client :as consumer]
             ;[franzy.clients.producer.client :as producer]
             ;[franzy.clients.producer.defaults :as pd]
             ;[franzy.clients.consumer.defaults :as cd]
             ;[franzy.serialization.serializers :as serializers]
             ;[franzy.serialization.nippy.deserializers :as nippy-deserializers]
             ;[franzy.serialization.deserializers :as deserializers]
             ;[clj-kafka.consumer.zk  :as consumer]
             ;[clj-kafka.producer     :as producer]
             ;[clj-kafka.new.producer :as nproducer]
             ;[clj-kafka.zk           :as czk]
             ;[clj-kafka.core         :as ckafka]
             [jepsen  [db    :as db]
                      [core  :as jepsen]
                      [client  :as client]
                      [control :as c]
                      [tests :as tests]
                      [checker   :as checker]
                      [model     :as model]
                      [generator :as gen]
                      [store     :as store]
                      [nemesis   :as nemesis]
                      [report    :as report]
                      [codec     :as codec]
                      [util      :as util :refer  [meh
                                                   timeout
                                                   relative-time-nanos]]
                      ]
             [jepsen.control :as c :refer  [|]]
             [jepsen.control.util :as cu]
             [jepsen.zookeeper :as zk]
             [jepsen.os.debian :as debian])
  )

(def topic "jepsen")

(defn create-topic
  []
  ;(Thread/sleep 20)
  (info "creating topic")
  (info (c/exec (c/lit (str "/opt/kafka/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 3 --partitions 1 --topic " topic ;" --config unclean.leader.election.enable=false --config min.insync.replicas=3"
                            ))))
  (info (c/exec (c/lit "/opt/kafka/bin/kafka-topics.sh --list --zookeeper localhost:2181")))
  (info "creating topic done")
)

(defn start!
  [id]
  ;(zk/start)
  (Thread/sleep (* 3 id))
  (c/su
    (info "start!  begins" id)
    (c/cd "/opt/kafka"
      (info (c/exec (c/lit "/opt/kafka/bin/kafka-server-start.sh -daemon config/server.properties"))))
      ;(c/exec (c/lit "/opt/kafka/bin/zookeeper-server-start.sh -daemon config/zookeeper.properties")))
    (info "start!  ends" id)
  )
)

(defn stop!
  []
  (c/su
     ;(c/exec :ps :aux "|" :grep :kafka "|" :grep :-v :grep "|" :awk "{print $2 }" "|" :xargs :kill :-s :kill)
     (c/exec (c/lit  "ps aux | grep kafka | grep -v grep | awk '{ print $2 }' | xargs kill -s kill"))))

(defn restart!
  [id]
  (stop! )
  (start! id))

(defn status!
  []
  (c/exec (c/lit "/opt/kafka/bin/kafka-list-topic.sh --zookeeper localhost:2181")))

(defn nuke!
  []
  (stop! )
  (c/su
    (stop!)
    (c/exec :rm :-rf "/opt/kafka")
    (c/exec :rm :-rf "/tmp/kafka-logs")))

(defn set-broker-id! [filename id]
   (c/exec  (c/lit  (format "sed -i.bak '/^broker\\.id/s/^.*$/broker.id=%s/' %s"     id filename))))

(defn deploy [id node version]
  (let [filename "/opt/kafka/config/server.properties"]
    ; (info "deploy calls set-broker-id!" filename node id )
    (set-broker-id! filename id)
    ; set advertised host name, otherwise it is canonical name
    ;(info "setting advertised host name to" (name node))
    ;(c/exec :echo (str "advertised.host.name=" (name node)) :> filename)
    ; (info "deplpoy set-broker-id done calls start!!" id )
    (info "deploy start! begins" id )
    (start! id)
    (info "deploy start! ends!" id )
    ; Create topic asynchronously
    (when (= id 1)
       (future  (create-topic)))
  ))

;        kafka "kafka_2.11-0.8.2.2"

;        kafka "kafka_2.10-0.8.2.1"
;2.10-0.8.2.1

(defn install! [node version]
   ; Install specific versions
  (info "install! Kafka begins" node )
  ; https://www.apache.org/dyn/closer.cgi?path=/kafka/0.10.2.0/kafka_2.12-0.10.2.0.tgz
  (let [id  (Integer.  (re-find #"\d+", (name node)))
        ;kafka "kafka_2.11-0.10.0.1"
        kafka "kafka_2.12-0.10.2.0"
        ]
     (info (c/exec :apt-get :update))
     (info (debian/install-jdk8!))
    ;(c/exec :apt-get :install :-y :--force-yes "default-jre")
     (info (c/exec :apt-get :install :-y :--force-yes "wget"))
     (info (c/exec :rm :-rf "/opt/"))
     (info (c/exec :mkdir :-p "/opt/"))
    (c/cd "/opt/"
          ; http://apache.claz.org/kafka/0.10.0.1/kafka_2.11-0.10.0.1.tgz
          (info (c/exec :wget (format "http://apache.claz.org/kafka/0.10.2.0/%s.tgz" kafka)))
          (info (c/exec :gzip :-d (format "%s.tgz" kafka)))
          (info (c/exec :tar :xf (format "%s.tar" kafka)))
          (info (c/exec :mv kafka "kafka"))
          (info (c/exec :rm (format "%s.tar" kafka))))
    (info "install! Kafka before call deploy" node ))
  (info "install! Kafka ends call deploy" node )
  (info "install! Kafka ends" node )
)

(defn db
    "Kafka DB for a particular version."
    [version]
    (let [zk (zk/db "3.4.5+dfsg-2+deb8u1")]
      (reify db/DB
        (setup!  [_ test node]
          (let [id (Integer.  (re-find #"\d+", (name node)))]
            (info "setup! zk " node)
            ;(db/setup! zk test node)
            (info "setup! kafka" node)
            (install! node version)
            ; need to start zk right before kafka deploy
            (db/setup! zk test node)
            (deploy id node version)
            (info "setup! kafka done"  node)
        ))
        (teardown!  [_ test node]
          ;(info "tearing down Kafka NUKE!!!" node)
          ;(nuke!)
          ;(info "Kafka NUKED!!!" node)
          ;(info "tearing down Zookeeper")
          ;(db/teardown! zk test node)
          ))))

(defn test-setup-all []
      (let [db (db "3.4.5+dfsg-2")
            test tests/noop-test]
           (doall (map #(c/on % (db/setup! db test %)) [:n1 :n2 :n3 :n4 :n5]))))

(defn consumer [node queue]
      (gregor/consumer (str (name node) ":9092")
                       "jepsen.consumer"
                       [queue]
                       {"auto.offset.reset" "earliest"
                        "enable.auto.commit" "false"}))

(defn get-cr [node queue]
      (let [c (consumer node queue)]
           (try
             (println "subscription:" (gregor/subscription c))
             (gregor/poll c)
             (catch Exception e
               (println "Exception:" e)
               nil
               )
             (finally (gregor/close c)))))

(defn dequeue-only! [op node queue]
  (let [c (consumer node queue)]
    (try
      (let [cr (gregor/poll c 5000)
            message (first cr)
            value (:value message)]
           (if (nil? message)
             (assoc op :type :fail :value :exhausted)
             (do
               ;(println "message:" message)
               (gregor/commit-offsets! c [{:topic queue :partition (:partition message) :offset (+ 1 (:offset message))}])
               ; If this fails, we will throw an exception and return timeout.  That way we don't consume it.
               (assoc op :type :ok :value (codec/decode value)))))
     (catch Exception e
       ;(pst e 25)
       ; Exception is probably timeout variant
       (info (str "Dequeue exception: " (.getMessage e) e))
       (assoc op :type :fail :value :timeout))
     (finally (gregor/close c)))))

(defn dequeue!
  "Given a channel and an operation, dequeues a value and returns the
  corresponding operation."
  [client queue op]
  (timeout 60000
           (assoc op :type :fail :value :timeout)
           (dequeue-only! op (:node client) queue)))

(defn enqueue-only! [node queue value]
  (let [p (gregor/producer (str (name node) ":9092") {"acks" "all"
                                                      "retry.backoff.ms" "1000"
                                                      "batch.size" "1"})]
    (try
      (deref (gregor/send p queue (str value)))
      (gregor/flush p)
     (catch Exception e
       nil)
     (finally (gregor/close p)))))

(defn enqueue! [client queue op]
  (try
    (timeout 10000  (assoc op :type :info, :value :timeout)
             (enqueue-only! (:node client) queue (:value op))
             (assoc op :type :ok))
    (catch Exception e
      (assoc op :type :info, :value :timeout))))

(defrecord Client [client queue]
  client/Client
  (setup!  [this test node]
           (info "setup! client called" node)
           (let [;brokers (->> (brokers node)
                 ;            (filter #(= (:host %) (name node))))
                 ;            first)
                 ;a0 (info "brokers:" brokers)
                 ;a1 (info "starting client producer." node)
                 ;producer (producer node)
                 ;a2 (info "starting client consumer" node)
                 ;consumer (consumer node)
                 ;messages (consumer/messages consumer queue)
                 client {:producer nil :consumer nil :node node :messages nil}]
            (info "done client setup..." node)
            (assoc this :client client)))

  (teardown!  [_ test]
    ;(consumer/shutdown (:consumer client))
    )

  (invoke!  [this test op]
     (case  (:f op)
         :enqueue (enqueue! client queue op)
         :dequeue  (dequeue! client queue op)
         :drain  (timeout 60000 (assoc op :type :info :value :timeout)
                                           (loop []
                                             (let [op' (->> (assoc op
                                                                   :f    :dequeue
                                                                   :time (relative-time-nanos))
                                                            util/log-op
                                                            (jepsen/conj-op! test)
                                                            (dequeue! client queue))]
                                               ; Log completion
                                               (->> (assoc op' :time (relative-time-nanos))
                                                    util/log-op
                                                    (jepsen/conj-op! test))

                                               (if (= :fail (:type op'))
                                                 ; Done
                                                 (assoc op :type :ok, :value :exhausted)

                                                 ; Keep going.
                                                 (recur)))))
         ))
  )

(defn client [] (Client. nil topic))

; Generators

(defn std-gen
    "Takes a client generator and wraps it in a typical schedule and nemesis
      causing failover."
    [gen]
    (gen/phases
       (->> gen
            (gen/nemesis
               (gen/seq  (cycle  [(gen/sleep 10)
                                   {:type :info :f :start}
                                   (gen/sleep 10)
                                   {:type :info :f :stop}])))
            (gen/time-limit 100))
       ; Recover
       (gen/nemesis  (gen/once  {:type :info :f :stop}))
       ; Wait for resumption of normal ops
       (gen/clients  (gen/time-limit 10 gen))
       ; Drain
       (info "draining " )
       (gen/log "Draining")
       (gen/clients  (gen/each  (gen/once  {:type :invoke
                                            :f    :drain})))))

(def gen1
  (->>  (gen/queue)
        (gen/delay 1)
        std-gen))

(def gen2
  (gen/phases
    (->> (gen/queue)
         (gen/delay 1/10)
         (gen/nemesis
           (gen/seq
             (cycle [(gen/sleep 60)
                     {:type :info :f :start}
                     (gen/sleep 60)
                     {:type :info :f :stop}])))
         (gen/time-limit 360))
    (gen/nemesis
      (gen/once {:type :info, :f :stop}))
    (gen/log "waiting for recovery")
    (gen/sleep 60)
    (gen/clients
      (gen/each
        (gen/once {:type :invoke
                   :f    :drain})))))


(defn kafka-test
    [version]
      (assoc  tests/noop-test
             :os debian/os
             :db  (db version)
             :client  (client)
             :model   (model/unordered-queue)
             :nemesis (nemesis/partition-random-halves)
             :checker    (checker/compose
                            {:queue       checker/queue
                            :total-queue checker/total-queue})
             :generator  gen1
      ))
