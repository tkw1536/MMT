package info.kwarc.mmt.stex

import info.kwarc.mmt.api.archives._
import info.kwarc.mmt.api.utils._

import scala.io.{BufferedSource, Codec}
import scala.util.matching.Regex

object STeXUtils {
  val c = java.io.File.pathSeparator

  def mathHubDir(bt: BuildTask): File = bt.archive.root.up.up.up

  def extBase(bt: BuildTask): File = mathHubDir(bt) / "ext"

  def stexStyDir(bt: BuildTask): File = extBase(bt) / "sTeX" / "sty"

  def styPath(bt: BuildTask): File = mathHubDir(bt) / "sty"

  def sysEnv(v: String): String = sys.env.getOrElse(v, "")

  def env(bt: BuildTask): List[(String, String)] = {
    val sty = "STEXSTYDIR"
    val tex = "TEXINPUTS"
    val styEnv = sysEnv(sty)
    List(
      sty -> (if (styEnv.isEmpty) stexStyDir(bt).toString else styEnv),
      tex -> (".//" + c + styPath(bt) + c + stexStyDir(bt) + "//"
        + c + sysEnv(tex)))
  }

  def archString(a: Archive): String = a.archString

  def getLang(f: File): Option[String] = f.stripExtension.getExtension

  def getAmbleFile(preOrPost: String, bt: BuildTask): File = {
    getAmbleFile(preOrPost, bt.archive, getLang(bt.inFile))
  }

  private def getLangAmbleFile(defaultFile: File, lang: Option[String]): File =
    if (lang.isDefined) {
      val langFile = defaultFile.stripExtension.setExtension(lang.get + ".tex")
      if (langFile.exists)
        langFile
      else defaultFile
    }
    else defaultFile

  def groupMetaInf(a: Archive): File = a.root.up / "meta-inf"

  def getAmbleFile(preOrPost: String, a: Archive, lang: Option[String]): File = {
    def ambleFile(root: File): File = (root / "lib" / preOrPost).setExtension("tex")
    val repoFile = getLangAmbleFile(ambleFile(a.root), lang)
    if (repoFile.exists())
      repoFile
    else getLangAmbleFile(ambleFile(groupMetaInf(a)), lang)
  }

  def readSourceRebust(f: File): BufferedSource =
    scala.io.Source.fromFile(f)

  def stripComment(line: String): String = {
    val idx = line.indexOf('%')
    idx match {
      case -1 => line
      case 0 => ""
      case _ =>
        val l = line.substring(0, idx)
        if (l.charAt(idx - 1) == '\\')
          l + "%" + stripComment(line.substring(idx + 1))
        else l
    }
  }

  def langFiles(lang: Option[String], files: List[String]): List[String] =
    files.filter(f => getLang(File(f)) == lang)

  private def mkRegGroup(l: List[String]): String = l.mkString("(", "|", ")")

  private def begin(s: String): String = "begin\\{" + s + "\\}"

  private val space = "\\s*"
  private val opt = "\\[(.*?)\\]"
  private val opt0 = "(" + opt + ")?"
  private val arg = space + "\\{(.*?)\\}"
  private val any = ".*"
  private val arg1 = arg + any
  private val optArg1 = opt0 + arg1
  private val bs = "\\\\"
  private val oStar = "\\*?"
  val input: Regex = (bs + "(lib)?input" + oStar + optArg1).r
  val includeGraphics: Regex = (bs + "includegraphics" + oStar + optArg1).r
  val importOrUseModule: Regex = (bs + "(import|use)Module" + opt + any).r
  val guse: Regex = (bs + "guse" + opt + arg1).r
  val useMhProblem: Regex = (bs + "includemhproblem" + optArg1).r
  val includeMhProblem: Regex = (bs + "includemhproblem" + optArg1).r
  val beginModnl: Regex = (bs + begin("m?h?modnl") + optArg1).r
  val mhinputRef: Regex = (bs + "m?h?inputref" + optArg1).r
  val tikzinput: Regex = (any + bs + "c?m?h?tikzinput" + optArg1).r
  private val smsKeys: List[String] = List("gadopt", "symvariant", "gimport") ++
    List("sym", "abbr", "key", "listkey").map(_ + "def") ++
    List("import", "adopt", "adoptmh").map(_ + "module")
  private val smsTopKeys: List[String] = List("module", "importmodulevia", "importmhmodulevia")
  val smsRegs: Regex = {
    val begins: String = begin(mkRegGroup(smsTopKeys))
    val ends: String = smsTopKeys.mkString("|end\\{(", "|", ")\\}")
    ("^\\\\(" + mkRegGroup(smsKeys) + "|" + begins + ends + ")").r
  }
  val importMhModule: Regex = (bs + "importmhmodule" + opt + "(.*?)").r
  val gimport: Regex = (bs + "gimport" + oStar + optArg1).r

  private def optArg2(s: String): String = bs + begin(s) + opt + arg + arg

  val smsSStruct = optArg2("sstructure").r
  val smsGStruct = (bs + begin("gstructure") + opt0 + arg + arg).r
  val smsMhStruct = optArg2("mhstructure").r
  val smsViewsig = (optArg2("gviewsig") + arg).r
  val smsViewnl = (bs + begin("gviewnl") + opt0 + arg + any).r
  val smsMhView = (optArg2("mhview") + arg).r
  val smsView = optArg2("view").r

  def entryToPath(p: String) = File(p).setExtension("tex").toFilePath

  def noAmble(f: File): Boolean = {
    val source = readSourceRebust(f)
    var res = false
    for (l <- source.getLines(); if !res)
      if (l.trim.startsWith("\\documentclass")) res = true
    res
  }

  def getProfile(a: Archive): Option[String] = {
    val key = "profile"
    var opt = a.properties.get(key)
    if (opt.isEmpty) {
      val gm: File = groupMetaInf(a) / "MANIFEST.MF"
      if (gm.exists && gm.isFile)
        opt = File.readProperties(gm).get(key)
    }
    opt
  }
}
