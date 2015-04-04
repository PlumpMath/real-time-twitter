(ns syncing.routes 
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.stacktrace :refer [wrap-stacktrace-web]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit 
             :refer (sente-web-server-adapter)]
            )
  (:use org.httpkit.server))

(let [{:keys [ch-recv send-fn ajax-post-fn
              ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
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

(def app (wrap-stacktrace-web init-routes))

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
