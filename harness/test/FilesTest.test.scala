package harness.run

import munit.CatsEffectSuite

class FilesTest extends CatsEffectSuite:
  test("sha256 of abc"):
    val f = os.temp("abc")
    assertEquals(Files.sha256(f), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

  test("tarGz excludes out dirs, keeps files; untarGz round-trips"):
    val src = os.temp.dir()
    os.write(src / "repo" / "a.txt", "a", createFolders = true)
    os.write(src / "repo" / "out" / "x", "x", createFolders = true)
    os.write(src / "repo" / "out-agent" / "y", "y", createFolders = true)
    val arc = os.temp.dir() / "t.tar.gz"
    val dest = os.temp.dir()
    for
      _ <- Files.tarGz(src, "repo", arc, Seq("repo/out", "repo/out-agent"))
      _ <- Files.untarGz(arc, dest)
    yield
      assert(os.exists(dest / "repo" / "a.txt"))
      assert(!os.exists(dest / "repo" / "out"))
      assert(!os.exists(dest / "repo" / "out-agent"))

  test("copyTree skips out and out-* at top level only"):
    val src = os.temp.dir()
    os.write(src / "keep.txt", "k")
    os.write(src / "out" / "x", "x", createFolders = true)
    os.write(src / "out-agent" / "x", "x", createFolders = true)
    os.write(src / "sub" / "out" / "z", "z", createFolders = true)
    os.symlink(src / "link", os.rel / "keep.txt")
    val dest = os.temp.dir() / "copy"
    Files.copyTree(src, dest, n => n == "out" || n.startsWith("out-"))
    assert(os.exists(dest / "keep.txt"))
    assert(!os.exists(dest / "out")); assert(!os.exists(dest / "out-agent"))
    assert(os.exists(dest / "sub" / "out" / "z"))
    assert(os.isLink(dest / "link"))

  test("gitInitCommit makes one commit with the message; gitArchive exports it"):
    val d = os.temp.dir()
    os.write(d / "f.txt", "hi")
    val out = os.temp.dir()
    for
      _ <- Files.gitInitCommit(d, "base")
      log <- Proc.capture(Seq("git", "log", "--format=%s"), d)
      _ <- Files.gitArchive(d, "HEAD", out)
    yield
      assertEquals(log, (0, "base\n"))
      assertEquals(os.read(out / "f.txt"), "hi")
