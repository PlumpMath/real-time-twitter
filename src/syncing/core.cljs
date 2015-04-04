(ns ^:figwheel-always syncing.core
    (:require [cljs.core.async :as async :refer (<! >! put! chan)]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true])
    (:require-macros [cljs.core.async.macros :as async :refer (go go-loop)]))

(enable-console-print!)

(defonce app-state 
  (atom {:tweet ""
         :auth {:name ""
                :id nil}
         :users [{:id 1 :name "Arya" :following? true}
                 {:id 2 :name "Tyron" :following? false}]
         :feed [{:id 1 :user 1 :text "Valar Morghulis"}
                {:id 2 :user 2 :text "I have a lots of gold"}]}))

(defn twitter [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:tweet-ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [tweet-ch (om/get-state owner :tweet-ch)]
        (go-loop []
          (let [msg (<! tweet-ch)]
            (println msg)))))
    om/IRenderState
    (render-state [_ {:keys [tweet-ch]}]
      (dom/div nil
               (dom/input 
                #js {:onChange #(om/update! data :tweet (.. % -target -value))
                     :value (:tweet data)})
               (dom/button #js {:onClick (fn [_] 
                                           (put! tweet-ch (:tweet data)) 
                                           nil)}
                           "Tweet!")))))

(defn user-component [user owner]
  (om/component
   (dom/div nil
            (dom/p nil (:name user))
            (dom/button 
             #js {:onClick (fn [_] (om/transact! user :following? not))}
             (if (:following? user) "Unfollow" "Follow")))))

(defn follow [users owner]
  (om/component
   (dom/div nil
            (dom/h3 nil "Users")
            (apply dom/div nil
                   (om/build-all user-component users)))))

(defn tweet [data owner]
  (om/component
   (dom/div nil
            (dom/p nil (:text data))
            (dom/p nil (str "by " (:user data))))))

(defn feed [data owner]
  (om/component
   (dom/div nil
        (dom/h3 nil "Feed")
        (apply dom/div nil
               (om/build-all tweet data)))))

(defn register [auth owner]
  (om/component
   (dom/div nil
            (dom/input 
             #js {:onChange #(om/update! auth :name (.. % -target -value))
                  :value (:name auth)})
            (dom/button 
             #js {:onClick (fn [_] (println (:name auth)))}
             "Register"))))

(defn skeleton [data owner]
  (reify om/IRender
    (render [_]
      (dom/div nil
               (dom/h1 nil "Twitter Client")
               (om/build register (:auth data))
               (om/build follow (:users data))
               (om/build feed (:feed data))
               (om/build twitter data)))))

(om/root skeleton app-state {:target (. js/document (getElementById "app"))})
