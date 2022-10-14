(ns goose.brokers.redis.api.dead-jobs
  ^:no-doc
  (:refer-clojure :exclude [pop])
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as job]))

(defn size [conn]
  (redis-cmds/sorted-set-size conn d/prefixed-dead-queue))

(defn pop [conn]
  (let [[job _] (redis-cmds/sorted-set-pop-from-head conn d/prefixed-dead-queue)]
    job))

(defn find-by-pattern [conn match? limit]
  (redis-cmds/find-in-sorted-set conn d/prefixed-dead-queue match? limit))

(defn find-by-id [conn id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern conn match? limit))))

(defn replay-job [conn job]
  (let [sorted-set d/prefixed-dead-queue]
    (when (redis-cmds/sorted-set-score conn sorted-set job)
      (redis-cmds/sorted-set->ready-queue conn sorted-set (list job) job/ready-queue))))

(defn replay-n-jobs [conn n]
  (let [sorted-set d/prefixed-dead-queue
        jobs (redis-cmds/sorted-set-peek-jobs conn sorted-set n)]
    (when (< 0 (count jobs))
      (redis-cmds/sorted-set->ready-queue conn sorted-set jobs job/ready-queue))
    (count jobs)))

(defn delete [conn job]
  (= 1 (redis-cmds/del-from-sorted-set conn d/prefixed-dead-queue job)))

(defn delete-older-than [conn epoch-time-ms]
  (< 0 (redis-cmds/del-from-sorted-set-until
         conn d/prefixed-dead-queue epoch-time-ms)))

(defn purge [conn]
  (= 1 (redis-cmds/del-keys conn [d/prefixed-dead-queue])))
