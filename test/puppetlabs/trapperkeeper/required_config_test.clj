(ns puppetlabs.trapperkeeper.required-config-test
  (:require [puppetlabs.trapperkeeper.core :refer [defservice]]
            [schema.core :as schema]))

(defservice needy
            []
            (required-config [this context]
                             {:your-moms-required-config schema/Any}))
