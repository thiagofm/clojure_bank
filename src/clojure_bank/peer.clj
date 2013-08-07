(ns clojure_bank.peer
  (:require [datomic.api :as d :refer (q)]))

(def uri "datomic:mem://clojure_bank")

(def schema-tx (read-string (slurp "resources/clojure_bank/schema.edn")))
(def data-tx (read-string (slurp "resources/clojure_bank/seed-data.edn")))

; Loads up the current db files
(defn init-db []
  (when (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema-tx)
      @(d/transact conn data-tx))))

; Very useful method I got in a gist, it turns a datomic id into the full entity
(defn decorate 
  [id]
  (let [conn (d/connect uri)]
    (let [ db (d/db conn) e (d/entity db id)]
      (select-keys e (keys e)))))

(defn namespace-hash-map
  "adds a namespace to a hash-map"
  [nspace kword]
  (into {} (for [[k v] kword ][(keyword nspace (name k)) v])))

; Clojure doesn't have it in their stdlib or am I didn't researched well enough?
(defn in? 
  "true if seq contains elm"
  [seq elm]  
  (some #(= elm %) seq))

; Datomic should have a helper method to convert raw string
; parameters to the format of an entity(like, strings with numbers turn
; into floats. This is why I made this.
(defn convert-parameters-to-type
  "converts every hashmap's value to integer/float if it's in `keys`"
  [hmap keys-seq]
  (reduce merge
    (map
      ; this fn read-string's every key that contains any of the `key-seq`
      (fn[[k v]] {k (if (in? keys-seq k) (read-string v) v)})
      hmap)))

; Saves into datomic
(defn save-creditcard [creditcard-info]
  (init-db)
  (let
    [conn (d/connect uri) ci (namespace-hash-map "creditcard" creditcard-info)]
      ; the merge with id happens because datomic expects me to set up an id to every entity I save
      (d/transact conn [(convert-parameters-to-type (merge ci {:db/id #db/id[:db.part/db]})
                                                    [:creditcard/outstanding_balance :creditcard/available_balance])])))

; Finds a record in datomic where the creditcard/number is the passed
; number. It costed me a bunch of hours to get how to set up a
; datasource in this query. Yet still amazed but datomic's query design.
(defn find-creditcard-by-number [number]
  (init-db)
  (let [conn (d/connect uri)]
    (decorate
      (ffirst
        (q '[:find ?e :in $ ?number :where [?e :creditcard/number ?number]] (d/db conn) number)))))

; Balance
(defn get-balances
  "returns the outstanding and available balance given a creditcard"
  [creditcard-number]
  (select-keys (find-creditcard-by-number creditcard-number) [:creditcard/outstanding_balance :creditcard/available_balance]))

(defn compute-outstanding-balance
  "computes the outstanding balance"
  [old-outstanding-balance added-amount]
  (- old-outstanding-balance added-amount))

(defn compute-available-balance
  "computes the avaialable balance"
  [old-available-balance added-amount]
  (+ old-available-balance added-amount))

(defn update-balance
  [creditcard-number amount]
  (let [old-outstanding-balance (get-in (get-balances creditcard-number) [:creditcard/outstanding_balance]) old-available-balance (get-in (get-balances creditcard-number) [:creditcard/available_balance])]
    (let [outstanding-balance (compute-outstanding-balance old-outstanding-balance amount) avaialable-balance (compute-available-balance old-available-balance amount)]
      (init-db)
      (let
        [conn (d/connect uri)]
        (d/transact
         conn
         [{:creditcard/number creditcard-number ;; this finds the existing entity
           :db/id #db/id[:db.part/db]  ;; will be replaced by existing id
           :creditcard/outstanding_balance outstanding-balance
           :creditcard/available_balance avaialable-balance }])))))
