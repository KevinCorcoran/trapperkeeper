(ns puppetlabs.trapperkeeper.services-test
  (:import (clojure.lang ExceptionInfo))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services :refer
             [defservice service] :as svcs]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.internal :as internal]
            [puppetlabs.trapperkeeper.testutils.bootstrap :as bootstrap-testutils]
            [puppetlabs.kitchensink.testutils.fixtures :refer [with-no-jvm-shutdown-hooks]]
            [schema.test :as schema-test]
            [slingshot.slingshot :refer [try+]]
            [schema.core :as schema]))

(use-fixtures :once schema-test/validate-schemas with-no-jvm-shutdown-hooks)

(defprotocol EmptyService)

(defprotocol HelloService
  (hello [this msg]))

(defservice hello-service
  HelloService
  []
  (init [this context] context)
  (start [this context] context)
  (hello [this msg] (str "HELLO!: " msg)))

(deftest test-satisfies-protocols
  (testing "creates a service definition"
    (is (satisfies? svcs/ServiceDefinition hello-service)))

  (let [app (bootstrap-testutils/bootstrap-services [hello-service])]
    (testing "app satisfies protocol"
      (is (satisfies? app/TrapperkeeperApp app)))

    (let [h-s (app/get-service app :HelloService)]
      (testing "service satisfies all protocols"
        (is (satisfies? svcs/Lifecycle h-s))
        (is (satisfies? svcs/Service h-s))
        (is (satisfies? HelloService h-s)))

      (testing "service functions behave as expected"
        (is (= "HELLO!: yo" (hello h-s "yo")))))))

(defprotocol Service1
  (service1-fn [this]))

(defprotocol Service2
  (service2-fn [this]))

(defprotocol Service3
  (service3-fn [this]))

(deftest lifecycle-test
  (testing "services are not required to define lifecycle functions"
    (let [service1  (service Service1
                      []
                      (service1-fn [this] "hi"))
          app       (bootstrap-testutils/bootstrap-services [service1])]
      (is (not (nil? app)))))

  (testing "life cycle functions are called in the correct order"
    (let [call-seq  (atom [])
          lc-fn     (fn [context action] (swap! call-seq conj action) context)
          service1  (service Service1
                      []
                      (init [this context] (lc-fn context :init-service1))
                      (start [this context] (lc-fn context :start-service1))
                      (service1-fn [this] (lc-fn nil :service1-fn)))
          service2  (service Service2
                      [[:Service1 service1-fn]]
                      (init [this context] (lc-fn context :init-service2))
                      (start [this context] (lc-fn context :start-service2))
                      (service2-fn [this] (lc-fn nil :service2-fn)))
          service3  (service Service3
                       [[:Service2 service2-fn]]
                       (init [this context] (lc-fn context :init-service3))
                       (start [this context] (lc-fn context :start-service3))
                       (service3-fn [this] (lc-fn nil :service3-fn)))]
      (bootstrap-testutils/bootstrap-services [service1 service3 service2])
      (is (= [:init-service1 :init-service2 :init-service3
              :start-service1 :start-service2 :start-service3]
             @call-seq))
      (reset! call-seq [])
      (bootstrap-testutils/bootstrap-services [service3 service2 service1])
      (is (= [:init-service1 :init-service2 :init-service3
              :start-service1 :start-service2 :start-service3]
             @call-seq))))

  (testing "service-id should be able to be called from any lifecycle phase"
    (let [test-context (atom {})
          service1 (service Service1
                            []
                            (init [this context]
                                  (swap! test-context assoc :init-service-id (svcs/service-id this))
                                  context)
                            (start [this context]
                                   (swap! test-context assoc :start-service-id (svcs/service-id this))
                                   context)
                            (stop [this context]
                                  (swap! test-context assoc :stop-service-id (svcs/service-id this))
                                  context)
                            (service1-fn [this] nil))]
      (bootstrap-testutils/with-app app [service1]
        ;; no-op; we just want the app to start up and shut down
        )
      (is (= :Service1 (:init-service-id @test-context)))
      (is (= :Service1 (:start-service-id @test-context)))
      (is (= :Service1 (:stop-service-id @test-context))))))

(deftest dependencies-test
  (testing "services should be able to call functions in dependency list"
    (let [service1 (service Service1
                            []
                            (service1-fn [this] "FOO!"))
          service2 (service Service2
                            [[:Service1 service1-fn]]
                            (service2-fn [this] (str "HELLO " (service1-fn))))
          app (bootstrap-testutils/bootstrap-services [service1 service2])
          s2  (app/get-service app :Service2)]
      (is (= "HELLO FOO!" (service2-fn s2)))))

  (testing "services should be able to retrieve instances of services that they depend on"
    (let [service1 (service Service1
                            []
                            (service1-fn [this] "FOO!"))
          service2 (service Service2
                            [[:Service1 service1-fn]]
                            (init [this context]
                                  (let [s1 (svcs/get-service this :Service1)]
                                    (assoc context :s1 s1)))
                            (service2-fn [this] ((svcs/service-context this) :s1)))
          app               (bootstrap-testutils/bootstrap-services [service1 service2])
          s2                (app/get-service app :Service2)
          s1                (service2-fn s2)]
      (is (satisfies? Service1 s1))
      (is (= "FOO!" (service1-fn s1)))))

  (testing "an error should be thrown if calling get-service on a non-existent service"
    (let [service1 (service Service1
                            []
                            (service1-fn [this] (svcs/get-service this :NonExistent)))
          app               (bootstrap-testutils/bootstrap-services [service1])
          s1                (app/get-service app :Service1)]
      (is (thrown-with-msg?
            IllegalArgumentException
            #"Call to 'get-service' failed; service ':NonExistent' does not exist."
            (service1-fn s1)))))

  (testing "lifecycle functions should be able to call injected functions"
    (let [service1 (service Service1
                            []
                            (service1-fn [this] "FOO!"))
          service2 (service Service2
                            [[:Service1 service1-fn]]
                            (init [this context] (service1-fn) context)
                            (service2-fn [this] "service2"))
          app (bootstrap-testutils/bootstrap-services [service1 service2])
          s2  (app/get-service app :Service2)]
      (is (= "service2" (service2-fn s2))))))

(defprotocol Service4
  (service4-fn1 [this])
  (service4-fn2 [this]))

(deftest service-this-test
  (testing "should be able to call other functions in same service via 'this'"
    (let [service4  (service Service4
                      []
                      (service4-fn1 [this] "foo!")
                      (service4-fn2 [this] (str (service4-fn1 this) " bar!")))
          app       (bootstrap-testutils/bootstrap-services [service4])
          s4        (app/get-service app :Service4)]
      (is (= "foo! bar!" (service4-fn2 s4))))))

(defservice service1
  Service1
  []
  (init [this context] "hi")
  (service1-fn [this] "hi"))

(defservice service1-alt
  Service1
  []
  (start [this context] "hi")
  (service1-fn [this] "hi"))

(deftest context-test
  (testing "should error if lifecycle function doesn't return context"
    (is (thrown-with-msg?
          IllegalStateException
          (re-pattern (str "Lifecycle function 'init' for service "
                           "'puppetlabs.trapperkeeper.services-test/service1'"
                           " must return a context map \\(got: \"hi\"\\)"))
          (bootstrap-testutils/bootstrap-services [service1]))
        "Unexpected shutdown reason for bootstrap")
    (is (thrown-with-msg?
          IllegalStateException
          (re-pattern (str "Lifecycle function 'start' for service "
                           "'puppetlabs.trapperkeeper.services-test/service1-alt'"
                           " must return a context map "
                           "\\(got: \"hi\"\\)"))
          (bootstrap-testutils/bootstrap-services [service1-alt]))
        "Unexpected shutdown reason for bootstrap"))

  (testing "lifecycle error works if service has no service symbol"
    (let [service1 (service Service1
                            []
                            (init [this context] "hi")
                            (service1-fn [this] "hi"))]
      (is (thrown-with-msg?
            IllegalStateException
            (re-pattern (str "Lifecycle function 'init' for service ':Service1'"
                             " must return a context map \\(got: \"hi\"\\)"))
            (bootstrap-testutils/bootstrap-services [service1]))
          "Unexpected shutdown reason for bootstrap"))
    (let [service1 (service Service1
                            []
                            (start [this context] "hi")
                            (service1-fn [this] "hi"))]

      (is (thrown-with-msg?
            IllegalStateException
            (re-pattern (str "Lifecycle function 'start' for service "
                             "':Service1' must return a context map "
                             "\\(got: \"hi\"\\)"))
            (bootstrap-testutils/bootstrap-services [service1]))
          "Unexpected shutdown reason for bootstrap")))

  (testing "context should be available in subsequent lifecycle functions"
    (let [start-context (atom nil)
          service1 (service Service1
                            []
                            (init [this context] (assoc context :foo :bar))
                            (start [this context] (reset! start-context context))
                            (service1-fn [this] "hi"))]
      (bootstrap-testutils/bootstrap-services [service1])
      (is (= {:foo :bar} @start-context))))

  (testing "context should be accessible in service functions"
    (let [sfn-context (atom nil)
          service1 (service Service1
                            []
                            (init [this context] (assoc context :foo :bar))
                            (service1-fn [this] (reset! sfn-context (svcs/service-context this))))
          app (bootstrap-testutils/bootstrap-services [service1])
          s1  (app/get-service app :Service1)]
      (service1-fn s1)
      (is (= {:foo :bar} @sfn-context))
      (is (= {:foo :bar} (svcs/service-context s1)))))

  (testing "context works correctly in injected functions"
    (let [service1 (service Service1
                            []
                            (init [this context] (assoc context :foo :bar))
                            (service1-fn [this] ((svcs/service-context this) :foo)))
          service2 (service Service2
                            [[:Service1 service1-fn]]
                            (service2-fn [this] (service1-fn)))
          app (bootstrap-testutils/bootstrap-services [service1 service2])
          s2  (app/get-service app :Service2)]
      (is (= :bar (service2-fn s2)))))

  (testing "context works correctly in service functions called by other functions in same service"
    (let [service4 (service Service4
                            []
                            (init [this context] (assoc context :foo :bar))
                            (service4-fn1 [this] ((svcs/service-context this) :foo))
                            (service4-fn2 [this] (service4-fn1 this)))
          app (bootstrap-testutils/bootstrap-services [service4])
          s4  (app/get-service app :Service4)]
      (is (= :bar (service4-fn2 s4)))))

  (testing "context from other services should not be visible"
    (let [s2-context (atom nil)
          service1 (service Service1
                            []
                            (init [this context] (assoc context :foo :bar))
                            (service1-fn [this] "hi"))
          service2 (service Service2
                            [[:Service1 service1-fn]]
                            (start [this context] (reset! s2-context (svcs/service-context this)))
                            (service2-fn [this] "hi"))

          app (bootstrap-testutils/bootstrap-services [service1 service2])]
      (is (= {} @s2-context)))))

(deftest service-symbol-test
  (testing "service defined via `defservice` has a service symbol"
    (bootstrap-testutils/with-app app [hello-service]
      (let [svc (app/get-service app :HelloService)]
        (is (= (symbol "puppetlabs.trapperkeeper.services-test" "hello-service")
               (svcs/service-symbol svc))))))
  (testing "service defined via `service` does not have a service symbol"
    (let [empty-svc (service EmptyService [])]
      (bootstrap-testutils/with-app app [empty-svc]
        (let [svc (app/get-service app :EmptyService)]
          (is (= :EmptyService (svcs/service-id svc)))
          (is (nil? (svcs/service-symbol svc))))))))

(deftest get-services-test
  (testing "get-services should return all services"
    (let [empty-service (service EmptyService [])]
      (bootstrap-testutils/with-app app [empty-service hello-service]
        (let [empty (app/get-service app :EmptyService)
              hello (app/get-service app :HelloService)]
          (doseq [s [empty hello]]
            (let [all-services (svcs/get-services s)]
              (is (= 2 (count all-services)))
              (is (every? #(satisfies? svcs/Service %) all-services))
              (is (= #{:EmptyService :HelloService}
                     (set (map svcs/service-id all-services)))))))))))

(deftest minimal-services-test
  (testing "minimal services can be defined without a protocol"
    (let [call-seq (atom [])
          service0 (service []
                            (init [this context]
                                  (swap! call-seq conj :init)
                                  (assoc context :foo :bar))
                            (start [this context]
                                   (swap! call-seq conj :start)
                                   (is (= context {:foo :bar}))
                                   context))]
      (bootstrap-testutils/bootstrap-services [service0])
      (is (= [:init :start] @call-seq))))

  (testing "minimal services can have dependencies"
    (let [service1 (service Service1
                            []
                            (service1-fn [this] "hi"))
          result   (atom nil)
          service0 (service [[:Service1 service1-fn]]
                            (init [this context]
                                  (reset! result (service1-fn))
                                  context))]
          (bootstrap-testutils/bootstrap-services [service1 service0])
          (is (= "hi" @result)))))

(defprotocol MultiArityService
  (foo [this x] [this x y]))

(deftest test-multi-arity-protocol-fn
  (testing "should support protocols with multi-arity fns"
    (let [ma-service  (service MultiArityService
                               []
                               (foo [this x] x)
                               (foo [this x y] (+ x y)))
          service1    (service Service1
                               [[:MultiArityService foo]]
                               (service1-fn [this]
                                            [(foo 5) (foo 3 6)]))
          app         (bootstrap-testutils/bootstrap-services [ma-service service1])
          mas         (app/get-service app :MultiArityService)
          s1          (app/get-service app :Service1)]
      (is (= 3 (foo mas 3)))
      (is (= 5 (foo mas 4 1)))
      (is (= [5 9] (service1-fn s1))))))

(deftest service-fn-invalid-docstring
  (testing "defining a service function, mistakenly adding a docstring"
    (is (thrown-with-msg?
          Exception
          #"Incorrect macro usage"
          (macroexpand '(puppetlabs.trapperkeeper.services/service
                          puppetlabs.trapperkeeper.services-test/Service1
                          []
                          (service1-fn
                            "This is an example of an invalid docstring"
                            [this] nil)))))))

(deftest required-config-1
  (testing "When TK is booting, if the required configuration for a service is
            missing, an exception is thrown."
    (let [my-service (service []
                              (required-config [this context]
                                               {:some-required-config String}))]
      (try
        (internal/boot-services* [my-service] {})
        (is false "Expected Exception was not thrown.")
        (catch Throwable t
          (is (instance? ExceptionInfo t))
          (let [error-data (:object (.getData t))]
            (is (not (schema/check internal/MissingRequiredConfigError error-data)))
            (is (= 1 (count (:errors error-data))))
            (let [error (first (:errors error-data))]

              (testing "When the service does not implement a protocol,
                    the name of the service is simply the namespace
                    in which it's defined."
                (is (re-matches #"puppetlabs.trapperkeeper.services_test"
                                (:service-name error)))
                (is (= (:detail error) {:some-required-config 'missing-required-key})))

              (testing "A user-friendly error message is generated from the error"
                (is (= (internal/missing-required-config-error->message error-data)
                       (str "The configuration data is insufficient for the service defined in namespace "
                            "'puppetlabs.trapperkeeper.services_test'.\n"
                            ; TODO pretty-print schema?
                            ; TODO (map name keys)
                            ; TODO no fully-qualified classnames
                            ; TODO order-dependent, das no bueno
                            ; TODO UGH!  s/class/
                            "Key 'some-required-config' is missing - the expected value for this key should conform to schema {:some-required-config String}")))))))))))

(deftest required-config-2
  (testing "More complex config schema."
    (let [my-service (service []
                              (required-config [this context]
                                               {:a {:b1 String
                                                    :b2 String}}))]
      (try
        (internal/boot-services* [my-service] {})
        (is false "Expected Exception was not thrown.")
        (catch Throwable t
          (is (instance? ExceptionInfo t) t)
          (let [error-data (:object (.getData t))]
            (is (not (schema/check internal/MissingRequiredConfigError error-data)))
            (is (= 1 (count (:errors error-data))))
            (let [error (first (:errors error-data))]

              (testing "When the service does not implement a protocol,
                       the name of the service is simply the namespace
                       in which it's defined."
                (is (re-matches #"puppetlabs.trapperkeeper.services_test"
                                (:service-name error)))
                (is (= (:detail error) {:a 'missing-required-key})))

              (testing "A user-friendly error message is generated from the error"
                (is (= (internal/missing-required-config-error->message error-data)
                       (str "The configuration data is insufficient for the service defined in namespace "
                            "'puppetlabs.trapperkeeper.services_test'.\n"
                            ; TODO pretty-print schema?
                            ; TODO (map name keys)
                            ; TODO no fully-qualified classnames
                            ; TODO order-dependent, das no bueno
                            "Key 'a' is missing - the expected value for this key should conform to schema {:b2 java.lang.String, :b1 java.lang.String}")))))))))))

(deftest invalid-required-config
  (testing "A service whose 'required-config' value is invalid"
    (let [my-service (service []
                              (required-config [this context]
                                               Object))
          expected-error (str "The 'required-config' schema defined by service .*"
                              " is invalid.  It must conform to the schema " "described by "
                              "#'puppetlabs.trapperkeeper.internal/MetaSchema")]
      (is (thrown-with-msg?
            Exception
            (re-pattern expected-error)
            (internal/boot-services* [my-service] {}))))))