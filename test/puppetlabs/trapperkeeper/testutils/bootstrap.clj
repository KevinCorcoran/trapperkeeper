(ns puppetlabs.trapperkeeper.testutils.bootstrap
  (:require [me.raynes.fs :as fs]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.kitchensink.testutils :as ks-testutils]
            [puppetlabs.trapperkeeper.bootstrap :as bootstrap]
            [puppetlabs.trapperkeeper.config :as config]
            [puppetlabs.trapperkeeper.internal :as internal]))

(defmacro with-app-with-config
  [app services config & body]
  `(ks-testutils/with-no-jvm-shutdown-hooks
     (let [~app (internal/throw-app-error-if-exists!
                  (tk/boot-services-with-config ~services ~config))]
       (try
         ~@body
         (finally
           (tk-app/stop ~app))))))

(defn bootstrap-services-with-cli-data
  [services cli-data]
  (internal/throw-app-error-if-exists!
    (tk/boot-services-with-config services
                                  (config/parse-config-data cli-data))))

(defmacro with-app-with-cli-data
  [app services cli-data & body]
  `(ks-testutils/with-no-jvm-shutdown-hooks
     (let [~app (bootstrap-services-with-cli-data ~services ~cli-data)]
       (try
         ~@body
         (finally
           (tk-app/stop ~app))))))

(defn bootstrap-services-with-cli-args
  [services cli-args]
  (bootstrap-services-with-cli-data services
                                    (internal/parse-cli-args! cli-args)))

(defmacro with-app-with-cli-args
  [app services cli-args & body]
  `(ks-testutils/with-no-jvm-shutdown-hooks
     (let [~app (bootstrap-services-with-cli-args ~services ~cli-args)]
       (try
         ~@body
         (finally
           (tk-app/stop ~app))))))

(defn bootstrap-services
  [services]
  (bootstrap-services-with-cli-data services {}))

(defmacro with-app
  [app services & body]
  `(ks-testutils/with-no-jvm-shutdown-hooks
     (let [~app (bootstrap-services ~services)]
       (try
         ~@body
         (finally
           (tk-app/stop ~app))))))

(defn bootstrap
  ([]
   (bootstrap []))
  ([other-args]
   (-> other-args
       (internal/parse-cli-args!)
       (tk/boot-with-cli-data)
       (internal/throw-app-error-if-exists!))))

(defn parse-and-bootstrap
  ([bootstrap-config]
   (parse-and-bootstrap bootstrap-config {}))
  ([bootstrap-config cli-data]
   (-> bootstrap-config
       (bootstrap/parse-bootstrap-config!)
       (bootstrap-services-with-cli-data cli-data))))
