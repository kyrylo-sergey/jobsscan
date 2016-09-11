package cache

import scala.collection.concurrent

class InMemoryCache[K, V] extends Cache[K, V] {
  val m = concurrent.TrieMap[K, V]()

  override def get(k: K): Option[V] = m.get(k)
  override def +=(kv: (K, V)): this.type = { m += kv; this }
  override def hit(k: K): Option[V] = m.get(k)
  override def evict(k: K): Option[V] = {
    val ret = m.get(k)
    m -= k
    ret
  }
  override def empty: InMemoryCache[K, V] = new InMemoryCache
  override def iterator: Iterator[(K, V)] = m.iterator
}
