package cache

import java.lang.System.currentTimeMillis

import scala.concurrent.duration.Duration

object Cache {
  def apply[K, V](ttl: Duration, sizeForRemoveCheck: Int = 1000): Cache[K, V] =
    new TTLCache(ttl, new InMemoryCache[K, (Long, V)])(() =>
      currentTimeMillis(), sizeForRemoveCheck)
}

trait Cache[K, V] {

  def get(k: K): Option[V]
  def +=(kv: (K, V)): this.type

  def multiInsert(kvs: Map[K, V]): this.type = {
    kvs.foreach { kv => this.+=(kv) }
    this
  }

  def hit(k: K): Option[V]
  def iterator: Iterator[(K, V)]
  def empty: Cache[K, V]
  def contains(k: K): Boolean = get(k).isDefined
  def getOrElseUpdate(k: K, v: => V): V =
    hit(k).getOrElse {
      val realizedV = v
      this += (k -> realizedV)
      realizedV
    }

  def evict(k: K): Option[V]
  def filter(pred: ((K, V)) => Boolean): Cache[K, V] =
    new FilteredCache(this)(pred)
}
