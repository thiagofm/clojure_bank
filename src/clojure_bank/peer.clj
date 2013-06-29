(ns clojure_bank.peer
  (:require [datomic.api :as d :refer (q)]))

(def uri "datomic:mem://clojure_bank")

(def schema-tx (read-string (slurp "resources/clojure_bank/schema.edn")))
(def data-tx (read-string (slurp "resources/clojure_bank/seed-data.edn")))

(defn init-db []
  (when (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema-tx)
      @(d/transact conn data-tx))))

(defn results []
  (init-db)
  (let [conn (d/connect uri)]
    (q '[:find ?c :where [?e :creditcard/name ?c]] (d/db conn))))
