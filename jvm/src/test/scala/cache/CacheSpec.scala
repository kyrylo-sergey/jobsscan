package cache

import scala.concurrent.duration._

import org.specs2.mutable.Specification

class CacheSpec extends Specification
    with FilteredCacheSpecPart with InMemoryCacheSpecPart {

  "TTL Cache should" >> {
    "drop values after some time" in {
      val ttl = Cache[Int, String](200 millis, 1)

      ttl += (1 -> "string")
      Thread.sleep(200)
      ttl += (2 -> "other")

      ttl.get(1) must_== None
      ttl.get(2) must_== Some("other")
    }
  }

}

trait FilteredCacheSpecPart extends Specification {

  private def getCache = {
    new InMemoryCache[Int, Int].multiInsert(Map(
      1 -> 1,
      2 -> 2,
      3 -> 3
    ))
  }

  "Filtered cache should" >> {
    "filter by keys" in {
      val c = getCache.filter { case (k, v) => k > 1 }

      c.get(1) must_== None
      c.get(2) must_== Some(2)
      c.get(3) must_== Some(3)
    }

    "filter by values" in {
      val c = getCache.filter { case(k, v) => v > 1 }

      c.get(1) must_== None
      c.get(2) must_== Some(2)
      c.get(3) must_== Some(3)
    }

    "filter further" in {
      val c = getCache
        .filter {case (k, v) => k > 1}
        .filter { case (k, v) => v > 2 }

      c.get(1) must_== None
      c.get(2) must_== None
      c.get(3) must_== Some(3)
    }
  }
}

trait InMemoryCacheSpecPart extends Specification {

  "In memory cache should" >> {
    "provide a way to put and get values from cache" in {
      val c = new InMemoryCache[Int, String]()

      c.+=(1 -> "string") get (1) must_== Some("string")
      c.hit(1) must_== Some("string")
    }

    "provide a way to remove a value from cache" in {
      val c = new InMemoryCache[Int, String]()

      c.+=(2 -> "some")
      c.get(2) must_== Some("some")
      c.evict(2)
      c.get(2) must_== None
    }

    "provide a way to inspect all of the cache values" in {
      val c = new InMemoryCache[Int, String]()

      c.+=(1 -> "string")
      c.+=(2 -> "other string")
      c.iterator.size must_== 2
      c.empty.iterator.size must_== 0
    }
  }
}
