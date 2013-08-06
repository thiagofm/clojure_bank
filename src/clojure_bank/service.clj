(ns clojure-bank.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [clojure.data.json :as json]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [clojure_bank.peer :as peer :refer [find-creditcard-by-number save-creditcard update-balance]]
              [ring.util.response :as ring-resp]))

(defn show-creditcard
  [request]
  (let [number (get-in request [:path-params :number])]
    (ring-resp/response (json/write-str (find-creditcard-by-number number)))))

; Todo:
; I probably need a custom interceptor to be able to use the body as a query string parameter
; maybe I should propose a better way for pedestal.io to deal with parameters, more rails like
; or offer both alternatives just in case people want to work it separately
(defn create-creditcard
  [request]
  (let [creditcard (get-in request [:query-params])]
    (save-creditcard creditcard)
    {:status 201}))

(defn send-funds
  "send funds from outside the account"
  [request]
  (let
    [amount (get-in request [:query-params :amount]) creditcard-number (get-in request [:path-params :number]) ]
    (update-balance creditcard-number (- (read-string amount)))
    {:status 204}))

(defn receive-funds
  "receive funds to the account"
  [request]
  (let
    [amount (get-in request [:query-params :amount]) creditcard-number (get-in request [:path-params :number]) ]
    (update-balance creditcard-number (+ (read-string amount)))
    {:status 204}))

(defroutes routes
  [[["/creditcard" {:post create-creditcard}]]
   [["/creditcard/:number/send" {:put send-funds}]]
   [["/creditcard/:number/receive" {:put receive-funds}]]
   [["/creditcard/:number" {:get show-creditcard}
     ^:interceptors [(body-params/body-params) bootstrap/html-body]]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by clojure-bank.server/create-server
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::boostrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})
