(ns cider.nrepl.middleware.apropos-test
  (:require [cider.nrepl.middleware.apropos :refer :all]
            [clojure.test :refer :all]
            [cider.nrepl.test-session :as session]
            [clojure.string :as str]))

(def ^{:doc "Test1. Test2. Test3."} public-var [1 2 3])
(def ^:private ^{:doc "Can't. See. Me"} private-var [:a :b :c])

(deftest unit-test-metadata
  (is (= (var-name  #'public-var) "cider.nrepl.middleware.apropos-test/public-var"))
  (is (= (var-doc   #'public-var) "Test1. Test2. Test3."))
  (is (= (var-doc 1 #'public-var) "Test1.")))

(deftest test-namespaces
  (let [ns (-> *ns* ns-name str)]
    (testing "Namespace sort order"
      (is (= (-> (namespaces ns nil) first ns-name str)
             ns)
          "Current namespace should be first.")
      (is (-> (namespaces nil nil) first ns-name str
              (.startsWith "clojure."))
          "Absent a current namespace, clojure.* should be first."))

    (testing "Searched namespace"
      (is (= (namespaces ns ns)
             (namespaces nil ns)
             (list (find-ns (symbol ns))))
          "Should return a list containing only the searched ns."))))

(deftest test-search
  (testing "Search results"
    (is (empty? (find-symbols nil "xxxxxxxx" nil false false false))
        "Failing searches should return empty.")
    (is (= 1 (count (find-symbols nil "handle-apropos" nil false false false)))
        "Search for specific fn should return it."))

  (testing "Symbol vs docstring search"
    ;; Search for the same fn by name and docstring
    (let [x (first (find-symbols nil "handle-apropos" nil false false false))
          y (first (find-symbols nil "Return a sequence of vars whose name matches"
                                 nil true false false))]
      (is (= (dissoc x :doc)
             (dissoc y :doc))
          "Other than docstring, returned attributes should be the same.")
      (is (< (count (:doc x))
             (count (:doc y)))
          "Symbol search should return an abbreviated docstring.")
      (is (= (take 20 (:doc x))
             (take 20 (:doc y)))
          "The abbreviated docstring should be the start of the full docstring."))))

(use-fixtures :each session/session-fixture)
(deftest integration-test
  (testing "Apropos op, typical case"
    (let [response (session/message {:op "apropos" :query "handle-apropos"})
          match    (get-in response [:apropos-matches 0])]
      (is (= (:status response) #{"done"}))
      (is (= (:type match) "function"))
      (is (= (:name match) "cider.nrepl.middleware.apropos/handle-apropos"))
      (is (.startsWith (:doc match) "Return a sequence of vars"))))

  (testing "Apropos op, but specialized cases (invoked with prefix argument)"
    (testing "Fails to get a private var because private? unset"
      (let [response (session/message {:op "apropos" :query "private-var"})
            match    (get-in response [:apropos-matches 0])]
        (is (= (:status response) #{"done"}))
        (is (empty? match))))

    (testing "Gets a private var using a case insensitive query"
      (let [response (session/message {:op "apropos" :query "PrIvAtE-VaR" :privates? "t"})
            match    (get-in response [:apropos-matches 0])]
        (is (= (:status response) #{"done"}))
        (is (= (:type match) "variable"))
        (is (= (:name match) "cider.nrepl.middleware.apropos-test/private-var"))
        (is (= (:doc  match) "Can't."))))

    (testing "Fails to get a private var due to case-mismatch in a case sensitive query"
      (let [response (session/message {:op "apropos"
                                       :query "PrIvAtE-VaR"
                                       :privates? "t"
                                       :case-sensitive? "t"})
            match (get-in response [:apropos-matches 0])]
        (is (= (:status response) #{"done"}))
        (is (empty? match))))

    (testing "Finds a public macro via a case-insensitive search through the docs"
      (let [doc-query "threads the expr through the forms"
            response (session/message {:op "apropos" :query doc-query :docs? "t"})
            match    (get-in response [:apropos-matches 0])]
        (is (= (:status response) #{"done"}))
        (is (= (:type match) "macro"))
        (is (= (:name match) "clojure.core/->"))
        (is (.startsWith (:doc match) (str/capitalize doc-query)))))))

(deftest error-handling-test
  (testing "Handles a fake error done via mocked function"
    (with-redefs [cider.nrepl.middleware.apropos/find-symbols
                  (fn [& args] (throw (Exception. "boom")))]
      (let [response (session/message {:op "apropos" :query "doesn't matter"})]
        (is (= (:status response) #{"apropos-error" "done"}))
        (is (= (:ex response) "class java.lang.Exception"))
        (is (.startsWith (:err response) "java.lang.Exception: boom"))
        (is (:pp-stacktrace response)))))

  (testing "Handles a real error caused by an improper regular expression"
    (let [response (session/message {:op "apropos" :query "*illegal"})]
      (is (= (:status response) #{"apropos-error" "done"}))
      (is (.startsWith (:err response) "java.util.regex.PatternSyntaxException: Dangling"))
      (is (:pp-stacktrace response)))))
