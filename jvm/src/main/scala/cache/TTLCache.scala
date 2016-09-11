package cache

import scala.concurrent.duration.Duration

class TTLCache[K, V](
  val ttl: Duration,
  protected val backingCache: Cache[K, (Long, V)])(
  val clock: () => Long,
  private val everyNtoRemove: Int = 1000
) extends Cache[K, V] {
  private[this] val putsSincePrune = new java.util.concurrent.atomic.AtomicInteger(1)

  def get(k: K): Option[V] =
    backingCache.get(k).filter { kv =>
      val clockVal = clock()
      kv._1 > clockVal
    }.map(_._2)

  def +=(kv: (K, V)): this.type = {
    if (putsSincePrune.getAndIncrement % everyNtoRemove == 0) {
      removeExpired()
    }
    backingCache += (kv._1 -> ((clock() + ttl.toMillis, kv._2)))
    this
  }

  def hit(k: K): Option[V] = {
    backingCache.get(k).map{case (ts, v) =>
      this += ((k, v))
      v
    }
  }

  override def contains(k: K): Boolean = get(k).isDefined
  override def empty: TTLCache[K, V] = new TTLCache(ttl, backingCache.empty)(clock)
  override def iterator: Iterator[(K, V)] = {
    val iteratorStartTime = clock()
    backingCache.iterator.flatMap{case (k, (ts, v)) =>
      if (ts >= iteratorStartTime) Some((k, v))  else None
    }.toList.iterator
  }

  protected def toRemove: Set[K] = {
    val pruneTime = clock()
    backingCache.iterator.filter(_._2._1 < pruneTime).map(_._1).toSet
  }

  def evict(k: K): Option[V] = {
    backingCache.evict(k).map(_._2)
  }

  def removeExpired(): Unit = {
    val killKeys = toRemove
    killKeys.foreach(backingCache.evict)
  }
}
