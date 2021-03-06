(ns cider.nrepl.middleware.util.meta-test
  (:require [cider.nrepl.middleware.util.meta :as m]
            [clojure.repl :as repl]
            [clojure.test :refer :all]))

(deftest merge-meta
  (testing "Always safe and preserves object"
    (are [form] (let [x form]
                  (= x (m/merge-meta x {:random 'meta})))
      0 1 1.0 (float 1) (double 1) 1M 1N 1/2
      'symbol :keyword
      (atom 10) (delay 10)
      [1 2 3] '(1 2 3)
      {1 2} #{1 2 3}))
  (testing "Applies meta"
    (are [form] (-> (m/merge-meta form {:random 'meta})
                    meta :random (= 'meta))
      ;; Keywords and numbers have no metadata.
      ;; Atoms and delays are mutable.
      'symbol
      [1 2 3] '(1 2 3)
      {1 2} #{1 2 3})))

(deftest strip-meta
  (testing "Always safe and preserves object"
    (are [form] (let [x form]
                  (= x (m/strip-meta (m/merge-meta x {:random 'meta}))))
      0 1 1.0 (float 1) (double 1) 1M 1N 1/2
      'symbol :keyword
      (atom 10) (delay 10)
      [1 2 3] '(1 2 3)
      {1 2} #{1 2 3}))
  (testing "Removes meta"
    (are [form] (-> (with-meta form {:random 'meta})
                    m/strip-meta meta :random not)
      ;; Keywords and numbers have no metadata.
      ;; Atoms and delays are mutable.
      'symbol
      [1 2 3] '(1 2 3)
      {1 2} #{1 2 3})))

(defmacro test-macro [& x]
  `(do ~@x))

(deftest macroexpand-all
  (is (->> (m/macroexpand-all '(test-macro ^{:random meta} (hi)))
           second
           meta
           :random
           (= 'meta))))
