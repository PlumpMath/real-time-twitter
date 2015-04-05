(ns syncing.routes 
  (:require [compojure.core :refer :all]
            [clojure.data :as data]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace-web]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit 
             :refer (sente-web-server-adapter)]
            )
  (:use org.httpkit.server))

(defn uuid [req]
  (str (java.util.UUID/randomUUID)))

(let [{:keys [ch-recv send-fn ajax-post-fn
              ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn uuid})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids))

(defroutes init-routes 
  (route/resources "/")
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (route/not-found "<h1>Page not found</h1>"))

(def app (-> init-routes
             wrap-keyword-params
             wrap-params
             wrap-stacktrace-web))

;; Resources

;; usernames should also be unique
(def users (atom {}))

(defn stream-users []
  (mapv (fn [u] {:id (first u) :name (second u)}) @users))

(defn create-user! [{:keys [id name]}]
  (swap! users #(assoc % id name)))

(defn delete-user! [id]
  (swap! users #(dissoc % id)))

(defn listen-uids [a b old-u new-u]
  (println "new--uids"))

(add-watch connected-uids :ws listen-uids)

;; Helpers

(defn notify-all [event-tag event-data]
  (doseq [u (:ws @connected-uids)]
    (chsk-send! u [event-tag event-data])))
;; Event Handler

(defmulti event-handler :id)

(defmethod event-handler :default [msg]
  (println (:id msg) (:event msg) (:?data msg)))

(defmethod event-handler :user/create [msg]
  (create-user! {:id (:client-id msg)
                 :name (:?data msg)})
  (notify-all :user/stream (stream-users)))

(sente/start-chsk-router! ch-chsk event-handler)

;; Server Lifecycle

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main []
  (reset! server (run-server #'app {:port 8080})))

(defn reload []
  (stop-server)
  (-main))
