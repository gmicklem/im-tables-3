(ns im-tables.test-config
  "Which InterMine the unit tests query. The defaults are the live HumanMine (upstream used FlyMine).
  Under shadow-cljs (the :test build) they can be overridden without editing sources:

      IM_TABLES_SERVICE_ROOT=http://localhost:8090/humanmine \\
      IM_TABLES_MINE_NAME=humanmine \\
        npx shadow-cljs compile test && node out/test.js

  shadow-cljs.edn passes the two environment variables in as :closure-defines. The tests
  query anonymously, so no token is needed.")

;; Web service root of the mine, without /service.
(goog-define SERVICE-ROOT "https://www.humanmine.org/humanmine")

;; Mine namespace, only used to build report-page links (:settings :links :vocab).
(goog-define MINE-NAME "humanmine")
