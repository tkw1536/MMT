package info.kwarc.mmt.api.web

import java.util.Calendar

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.archives._
import info.kwarc.mmt.api.frontend._
import info.kwarc.mmt.api.ontology._
import info.kwarc.mmt.api.utils._
import ServerResponse._
import info.kwarc.mmt.api.objects.Context

/**
 * An MMT extensions that handles certain requests in MMT's HTTP server.
 *
 * It will be called on URIs of the form http://server:port/:CONTEXT/PATH?QUERY
 *
 * @param context the CONTEXT
 */
abstract class ServerExtension(context: String) extends FormatBasedExtension {
  /**
   * @param cont the context of the request
   * @return true if cont is equal to this.context
   */
  def isApplicable(cont: String): Boolean = cont == context

  /**
   * handles a request for this ServerExtension
   *
   * for implementation, the ServerResponse._ methods should be used
   * all errors are caught and displayed to the user when possible
   *
   * @param request The request sent to this ServerExtension
   * @return a response for this request
   */
  def apply(request: ServerRequest): ServerResponse
}

/**
 * interprets the body as MMT content
 */
class PostServer extends ServerExtension("post") {
  def apply(request: ServerRequest): ServerResponse = {
    val wq = request.parsedQuery
    val content = wq.string("body", throw ServerError("found no body in post req"))
    val format = wq.string("format", "mmt")
    val dpathS = wq.string("dpath", throw ServerError("expected dpath"))
    val dpath = DPath(URI(dpathS))
    log("Received content : " + content)
    controller.read(parser.ParsingStream.fromString(content, dpath, format), interpret = true)(ErrorThrower)
    TextResponse("Success")
  }
}

/** interprets the query as an MMT document URI and returns the SVG representation of the theory graph */
class SVGServer extends ServerExtension("svg") with ContextMenuProvider {
  /**
   * request.path the export dimension from which to take the graph, "svg" by if empty
   * request.query the [[Path]] for which to retrieve a graph
   */
  def apply(request: ServerRequest): ServerResponse = {
    // val (nquery,json) = if (query.startsWith("json:")) (query.drop(5),true) else (query,false)
    val path = Path.parse(request.query, controller.getNamespaceMap)
    val key = request.path.headOption.getOrElse("svg")
    lazy val exp = controller.extman.getOrAddExtension(classOf[RelationGraphExporter], key).getOrElse {
      throw LocalError(s"svg file does not exist and exporter $key not available: ${request.query}")
    }
    lazy val se = controller.get(path)
    /* if (json) {
      JsonResponse(exp.asJSON(se))
    } else { */
      val (exportFolder, relPath) = svgPath(path)
      val svgFile = exportFolder / key / relPath
      val node = if (svgFile.exists) {
        utils.File.read(svgFile.setExtension("svg"))
      } else {
        exp.asString(se)
      }
      ServerResponse(node, "image/svg+xml")
    // }
  }
  
  import Javascript._
  import MMTJavascript._
  def getEntries(path: Path) = {
    val (exportFolder, relPath) = svgPath(path)
    val existingKeys = exportFolder.children.collect {
      case f if (f/relPath).exists => f.name
    }
    val exporterKeys = controller.extman.get(classOf[RelationGraphExporter]).filter(_.canHandle(path)).map(_.key)
    (existingKeys ::: exporterKeys).distinct.map {key =>
      ContextMenuEntry("show " + key + " graph", showGraph(key, path.toPath))  
    }
  }
  
  /** @return (d,f) such that d/key/f is the path to the svg file for path exported by key */ 
  private def svgPath(path: Path): (File, List[String]) = {
    val (inNarr, newPath) = path.dropComp match {
      // narrative
      case dp: DPath => (true, dp)
      // content
      case c: ContentPath => (false, c.module)
    }
    val (arch, relPath) = if (inNarr) {
      val dp = newPath.asInstanceOf[DPath]
      val (arch, inPath) = controller.backend.resolveLogical(dp.uri).getOrElse {
        throw LocalError("illegal path: " + path)
      }
      val inPathFile = Archive.narrationSegmentsAsFile(FilePath(inPath), "omdoc")
      (arch, "narration" :: inPathFile)
    } else {
      val mp = newPath.asInstanceOf[MPath]
      val arch = controller.backend.findOwningArchive(mp).getOrElse {
        throw LocalError("illegal path: " + path)
      }
      val inPathFile = Archive.MMTPathToContentPath(mp)
      (arch, "content" :: inPathFile)
    }
    (arch.root / "export", relPath)
  }
}

/** interprets the body as a QMT [[ontology.Query]] and evaluates it */
class QueryServer extends ServerExtension("query") {
  def apply(request: ServerRequest): ServerResponse = {
    val mmtquery = request.body.asXML
    log("qmt query: " + mmtquery)
    val q = Query.parse(mmtquery)(controller.extman.get(classOf[QueryFunctionExtension]), controller.relman)
    //log("qmt query: " + q.toString)
    QueryChecker.infer(q)(Context.empty) // type checking
    val res = controller.evaluator(q)

    ServerResponse.fromXML(res.toNode)
  }
}

/** HTTP frontend to the [[Search]] class */
class SearchServer extends ServerExtension("search") {
  private lazy val search = new Search(controller)
  private val mmlpres = new presentation.MathMLPresenter

  override def start(args: List[String]) {
    mmlpres.init(controller)
  }

  def apply(request: ServerRequest): ServerResponse = {
    val wq = WebQuery.parse(request.query)
    val base = wq("base")
    val mod = wq("module")
    val name = wq("name")
    val theory = wq("theory")
    val pattern = wq("pattern") orElse request.body.asStringO
    val format = wq.string("format", "mmt")
    val intype = wq.boolean("type")
    val indef = wq.boolean("definition")
    val allcomps = List(TypeComponent, DefComponent)
    val comps = allcomps.zip(List(intype, indef)).filter(_._2).map(_._1)
    val pp = PathPattern(base, mod, name)
    val tp = (theory, pattern) match {
      case (Some(t), Some(p)) => Some(TermPattern.parse(controller, t, p, format))
      case (_, _) => None
    }
    val sq = SearchQuery(pp, comps, tp)
    val res = search(sq, resolveResults = true)
    val htmlres = HTML.build { h =>
      import h._
      div(attributes = List("xmlns" -> xml.namespace("html"), "xmlns:jobad" -> utils.xml.namespace("jobad"))) {
        res.foreach { r =>
          div("result") {
            val CPath(par, comp) = r.cpath
            div {
              text {
                comp.toString + " of "
              }
              span("mmturi", attributes = List("jobad:href" -> par.toPath)) {
                text {
                  par.last
                }
              }
            }
            r match {
              case SearchResult(cp, pos, None) =>
              case SearchResult(cp, pos, Some(term)) =>
                def style(pc: presentation.PresentationContext) = if (pc.pos == pos) "resultmatch" else ""
                div {
                  mmlpres(term, Some(cp), style)(new presentation.HTMLRenderingHandler(h))
                }
            }
          }
        }
      }
    }
    XmlResponse(htmlres)
  }
}

abstract class TEMASearchServer(format : String) extends ServerExtension("tema-" + format) {
  val presenter : presentation.ObjectPresenter
  def process(query : String, settings : Map[String,String]) : objects.Term

  def toHTML(tm : objects.Term) : String = {
    val rh = new presentation.StringBuilder()
    presenter.apply(tm, None)(rh)
    rh.get
  }

  def getSettings(path : List[String], query : String, body : Body) : Map[String, String]

  def apply(request: ServerRequest): ServerResponse = {
    val searchS = request.body.asString
    val settings = getSettings(request.path, request.query, request.body)
    val mathmlS = toHTML(process(searchS, settings))
    val mathml = scala.xml.XML.loadString(mathmlS)
    val resp = postProcessQVars(mathml)
    TextResponse(resp.toString, "html")
  }

  def preProcessQVars(n : scala.xml.Node) : scala.xml.Node = n match {
    case <QVAR>{c}</QVAR> =>
      val name = xml.attr(c, "name")
      <OMV name={"qvar_" + name} />
    case n : scala.xml.Elem =>
      val child = n.child.map(preProcessQVars)
      new scala.xml.Elem(n.prefix, n.label, n.attributes, n.scope, false, child : _*)
    case _ => n

  }

  def postProcessQVars(n : scala.xml.Node) : scala.xml.Node = n match {
    case n if n.label == "mi" && n.text.startsWith("qvar_") =>
      <mi class="math-highlight-qvar"> {n.text.substring(5)} </mi>
    case n if n.label == "ci" && n.text.startsWith("qvar_") =>
      <mws:qvar>{n.text.substring(5)}</mws:qvar>
    case n : scala.xml.Elem =>
      val child = n.child.map(postProcessQVars)
      new scala.xml.Elem(n.prefix, n.label, n.attributes, n.scope, false, child : _*)
    case _ => n
  }
}

/** interprets the query as an MMT [[frontend.GetAction]] and returns the result */
class GetActionServer extends ServerExtension("mmt") {
  def apply(request: ServerRequest): ServerResponse = {
    val action = Action.parseAct(request.queryString, controller.getBase, controller.getHome)
    val resp: String = action match {
      case GetAction(a: ToWindow) =>
        a.make(controller)
          <done action={a.toString}/>.toString
      case GetAction(a: Respond) =>
        a.get(controller)
      case _ =>
          <notallowed action={action.toString}/>.toString
    }
    XmlResponse(resp)
  }
}

/** an HTTP interface for processing [[Message]]s */
class MessageHandler extends ServerExtension("content") {
  def apply(request: ServerRequest): ServerResponse = {
     if (request.path.length != 1)
       throw LocalError("path must have length 1")
     val wq = WebQuery.parse(request.query)
     lazy val inFormat = wq.string("inFormat")
     lazy val outFormat = wq.string("outFormat")
     lazy val theory = wq.string("theory")
     lazy val context = objects.Context(Path.parseM(theory, controller.getNamespaceMap))
     lazy val inURI = Path.parse(wq.string("uri"))
     lazy val in = request.body.asString
     val message: Message = request.path.head match {
       case "get"    => GetMessage(inURI, outFormat)
       case "delete" => DeleteMessage(inURI)
       case "add"    => AddMessage(???, inFormat, in)
       case "update" => UpdateMessage(???, inFormat, in)
       case "eval"   => EvaluateMessage(Some(context), inFormat, in, outFormat)
       case "prove"  => ProveMessage(Some(context), inFormat, in, outFormat)
       case "infer"  => InferMessage(Some(context), inFormat, in, outFormat)
       case s => throw LocalError("unknown command: " + s)
     }
     controller.handle(message) match {
       case ObjectResponse(obj, tp) => TextResponse(obj, tp)
       case StructureResponse(id) => errorResponse(id, request)
       case ErrorResponse(msg) => errorResponse(msg, request)
     }
  }
}

/** interprets the query as an MMT [[frontend.Action]] and returns the log output */
class ActionServer extends ServerExtension("action") {
  private lazy val logCache = new RecordingHandler(logPrefix)

  override def start(args: List[String]) {
    report.addHandler(logCache)
  }

  override def destroy {
    report.removeHandler(logPrefix)
  }

  def apply(request: ServerRequest): ServerResponse = {
    val c = request.decodedQuery
    val act = frontend.Action.parseAct(c, controller.getBase, controller.getHome)
    if (act == Exit) {
      // special case for sending a response when exiting
      new Thread {
        override def run {
          Thread.sleep(100)
          controller.handle(act)
        }
      }.start
      return XmlResponse(<exited/>)
    }
    logCache.record
    controller.handle(act)
    val r = logCache.stop
    logCache.clear
    val html = utils.HTML.build { h =>
      import h._
      div {
        r foreach { l => div {
          text {
            l
          }
        }
        }
      }
    }
    XmlResponse(html)
  }
}


/**
 * experimental server for submitting comments
 *  
 * Handle the body of the POST request (json format)
 * and store the comment as user+date into the discussions folder
 */
class SubmitCommentServer extends ServerExtension("submit_comment") {
  def apply(request: ServerRequest): ServerResponse = {
    val path = Path.parse(request.queryString, controller.getNamespaceMap)
    var s = request.body.asString
    val date = Calendar.getInstance().getTime.toString
    val end = date.replaceAll("\\s", "")
    //deprecated but will use this until better alternatives come along
    // one possible solution is Argonaut
    val result = scala.util.parsing.json.JSON.parseFull(s)
    var user = ""
    var comment = ""
    result match {
      case Some(map: Map[String@unchecked, String@unchecked]) =>
        user = map.getOrElse("user", null)
        comment = map.getOrElse("comment", null)
      case None => println("Parsing failed")
      case other => println("Unknown data structure: " + other)
    }
    val resp = "<comment>" +
      "<metadata>" +
      "<topic>" + request.query + "</topic>" +
      "<user>" + user + "</user>" +
      "<date>" + date + "</date>" +
      "</metadata>" +
      "<text>" + comment + "</text>" +
      "</comment>"
    val archive = "/archives/meta/inf/config/OAF/discussions/"
    val root = File(System.getProperty("user.dir")).up.up.up.up
    val f = root + archive + user + end + ".xml"
    val folder = File(f)
    try {
      write(folder, resp)
    }
    catch {
      case ex: Exception =>
        println(ex)
    }
    finally {
      XmlResponse("<p>not ok</p>")
    }
    XmlResponse("<p>OK</p>")
  }

  def Writer(f: File) = {
    f.up.toJava.mkdirs
    new StandardPrintWriter(f)
  }

  def write(f: File, s: String) {
    val fw = Writer(f)
    fw.write(s)
    fw.close
  }
}

import symbols._
import modules._
class URIProducer extends BuildTarget {
  def key = "uris"
  
  private def jsonFile(a: Archive) = a / archives.export / key / "uris.json"
  
  def build(a: Archive, up: Update, in: FilePath) {
     val thys = controller.depstore.querySet(DPath(a.narrationBase), Transitive(+Declares) * HasType(IsTheory))
     File.stream(jsonFile(a), "[\n", ",\n", "\n]") {out =>
       thys.foreach {thy =>
         catchErrors("error while processing " + thy) {
           controller.globalLookup.getO(thy) match {
             case Some(d: DeclaredTheory) =>
               catchErrors("error while flattening " + d.path) {
                  controller.simplifier(d)
               }
               d.getDeclarationsElaborated.foreach {
                 case c: Constant =>
                   val tpS = c.tp match {
                     case Some(t) =>
                       catchErrors("error while presenting " + t, "") {
                         controller.presenter.asString(t)
                       }
                     case None => ""
                   }
                   val j = JSONObject("uri" -> JSONString(c.path.toPath), "type" -> JSONString(tpS))
                   out(j.toString)
                 case _ =>
               }
             case _ =>
           }
         }
       }
     }
  }
  def clean(a: Archive, in: FilePath) {
    delete(jsonFile(a))
  }
}

/**
 * serves all constant URIs in an archive or a group of archives
 */
class URIServer extends ServerExtension("uris") {
   def apply(request: ServerRequest): ServerResponse = {
     val archive = controller.backend.getArchive(request.query).getOrElse {
       throw LocalError("archive not found: " + request.query)
     }
     val f = archive / Dim("export") / "uris" / "uris.json"
     val json = File.read(f)
     // TODO: Check if we want to use a ServerResponse.resource
     ServerResponse(json, "application/json")
   }
}
