package harness.core

class ScaffoldTest extends munit.FunSuite:
  test("writeJsonAtomic then readJson round-trips"):
    val d = os.temp.dir()
    Json.writeJsonAtomic(d / "a.json", ujson.Obj("x" -> 1))
    assertEquals(Json.readJson(d / "a.json"), Some(ujson.Obj("x" -> 1)))
    assert(!os.exists(d / "a.json.tmp"))
  test("readJson is None on invalid json"):
    val d = os.temp.dir(); os.write(d / "b.json", "{")
    assertEquals(Json.readJson(d / "b.json"), None)
  test("readLenient is empty on a missing file"):
    assertEquals(Json.readLenient(os.temp.dir() / "none"), "")
