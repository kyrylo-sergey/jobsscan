package cache

class FilteredCache[K, V](cache: Cache[K, V])(p: ((K, V)) => Boolean)
    extends Cache[K, V] {
  override def get(k: K): Option[V] = cache.get(k).filter(v => p((k, v)))
  override def contains(k: K): Boolean = get(k).isDefined
  override def +=(kv: (K, V)): this.type = {
    if (p(kv)) cache += kv
    this
  }
  override def hit(k: K): Option[V] = cache.hit(k) match {
    case opt@Some(v) => if (p((k, v))) opt else None
    case None => None
  }
  override def empty: FilteredCache[K, V] = new FilteredCache(cache.empty)(p)
  override def iterator: Iterator[(K, V)] = cache.iterator.filter(p)
  override def evict(k: K): Option[V] = cache.evict(k)
}
