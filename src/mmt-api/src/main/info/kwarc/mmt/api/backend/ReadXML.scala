package info.kwarc.mmt.api.backend
import info.kwarc.mmt.api._
import documents._
import frontend._
import metadata._
import modules._
import notations._
import symbols._
import objects._
import patterns._
import utils._
import ontology._
import presentation._
import opaque._

import scala.xml.{Elem,Null,TopScope,Node,NodeSeq,Utility}

/** A Reader parses XML/MMT and calls controller.add(e) on every found content element e
 *  
 *  The XML may contain CURIEs.
 *  However, all namespace prefixes must be declared on the toplevel omdoc element; other bindings are ignored.
 */
class XMLReader(controller: Controller) extends Logger {
   val report = controller.report
   val logPrefix = "reader"
   private val rci = new RuleConstantInterpreter(controller)

   /** calls the continuation function */
   private def add(e : StructuralElement)(implicit cont: StructuralElement => Unit) {
      cont(e)
   }
   /** adds metadata and calls the continuation functions */
   private def add(e : StructuralElement, md: Option[MetaData])(implicit cont: StructuralElement => Unit) {
      md foreach {e.metadata = _}
      cont(e)
   }
   private def addModule(m: Module, md: Option[MetaData], docOpt: Option[Document])(implicit cont: StructuralElement => Unit) {
      add(m, md)
      docOpt foreach {d =>
        val mref = new MRef(d.path, LocalName(m.path), m.path)
        mref.setOrigin(GeneratedMRef)
        add(mref)
      }
   }
   
   /**
    *  parses a document and forwards its declarations to the continuation function 
    *  @param dpath the URI of the document
    *  @param nodeMd the document
    *  
    *  If the document has a base attribute, it is used as the default namespace of modules.
    */
   def readDocument(dpath : DPath, nodeMd : Node)(implicit cont: StructuralElement => Unit) {
      val nsMap = NamespaceMap.fromXML(nodeMd)(dpath)
      val (node, md) = MetaData.parseMetaDataChild(nodeMd, nsMap)
      node match {
        case <omdoc>{modules @ _*}</omdoc> =>
           val base = Path.parseD(xml.attr(node, "base"), nsMap)
           val nsMapB = nsMap(base)
           log("document with URI " + dpath + " found")
           val d = new Document(dpath, root = true, nsMap = nsMapB)
           add(d,md)
           modules foreach {m => readIn(nsMapB, d, m)}
        case _ => throw ParseError("document expected: " + node)
      }
   }
   
   /** entry point for reading in a node if the containing document, theory, view is known */
   def readIn(nsMap: NamespaceMap, se: StructuralElement, node: Node)(implicit cont: StructuralElement => Unit) {
      // base is unchanged for documents, module.path for theories, codomain for views
      se match {
         case d: Document => d.contentAncestor match {
            case Some(ce) =>
               readInModuleAux(ce.path.toMPath, d.path, nsMap, ce, node)
            case None =>
               readInDocument(nsMap, Some(d), node)
         }
         case t: DeclaredTheory => readInModule(t.path, nsMap(t.path), t, node)
         case v: DeclaredView => readInModule(v.path, nsMap(v.to.toMPath), v, node)
         //case s: DeclaredStructure => readInTheory(s.home / s.name, s.path, Some(s), node)
      }
   }
   
   /**
    * reads a child of a document
    * @param nsMap used for relative paths, in particular for the namespace of modules
    * @param docOpt the containing document, if any; if given, XRef's will be generated
    * @param nodeMd the node to parse
    */
   def readInDocument(nsMap: NamespaceMap, docOpt: Option[Document], nodeMd : Node)(implicit cont: StructuralElement => Unit) {
      lazy val doc = docOpt.getOrElse {throw ParseError("document element without containing document")}
      lazy val dname = LocalName.parse(xml.attr(nodeMd,"name"), nsMap)
      val (node, md) = MetaData.parseMetaDataChild(nodeMd, nsMap)
      node match {
         case <omdoc>{mods @_*}</omdoc> =>
            val dpath = doc.path / dname
            val innerdoc = new Document(dpath)
            add(innerdoc, md)
            mods foreach (m => readIn(nsMap, innerdoc, m))
         case <opaque>{ops @_*}</opaque> =>
            val format = xml.attr(node, "format")
            val oi = controller.extman.get(classOf[OpaqueElementInterpreter], format).getOrElse {
               throw ParseError("unknown opaque format: " + format)
            }
            val oe = oi.fromNode(doc.path, nsMap, ops)
            add(oe, md)
         case <dref/> =>
	         val d = xml.attr(node, "target")
	         log("dref to " + d + " found")
	         val r = new DRef(doc.path, dname, Path.parseD(d,nsMap))
	         add(r)
         case <mref/> =>
	         val t = xml.attr(node, "target")
	         log("mref to " + t + " found")
	         val r = new MRef(doc.path, dname, Path.parseM(t,nsMap))
	         add(r)
         case <sref/> =>
            val t = xml.attr(node, "target")
            log("sref to " + t + " found")
            val r = new SRef(doc.path, dname, Path.parseS(t,nsMap))
            add(r)
         case scala.xml.Comment(_) =>
         case m =>
           val namespace = Path.parseD(xml.attr(m,"base"), nsMap)
           val name = LocalName.parse(xml.attr(m,"name"), nsMap(namespace))
           xml.trimOneLevel(m) match {
	         case <theory>{_*}</theory> =>
		         log("theory " + name + " found")
		         val tpath = namespace ? name
             val meta = xml.attr(m, "meta") match {
                case "" => None
                case mt =>
                   log("meta-theory " + mt + " found")
                   Some(Path.parseM(mt, nsMap(namespace)))
             }
		         // parameters and definition are parsed later by readIn (necessary to allow for streaming)
             val bodyNodes = m.child
             val t = Theory.empty(namespace, name, meta)
             addModule(t, md, docOpt)
             logGroup {
                bodyNodes.foreach { d =>
                   readIn(nsMap, t, d)
                }
             }
	         case <view>{_*}</view> =>
	            log("view " + name + " found")
	            val (m2, from) = ReadXML.getTermFromAttributeOrChild(m, "from", nsMap)
	            val (m3, to) = ReadXML.getTermFromAttributeOrChild(m2, "to", nsMap)
	            val isImplicit = parseImplicit(m)
	            m3.child match {
                  case <definition>{d}</definition> :: Nil =>
		               val df = Obj.parseTerm(d, nsMap)
		               val v = DefinedView(namespace, name, from, to, df, isImplicit)
		               addModule(v, md, docOpt)
                  case assignments =>
	 		            val v = new DeclaredView(namespace, name, from, to, isImplicit) // TODO add metamorph?
                     addModule(v, md, docOpt)
                     logGroup {
                        assignments.foreach {d =>
                           readIn(nsMap, v, d)
                        }
                     }
                }
	         case <rel>{_*}</rel> =>
	            //ignoring logical relations, produced by Twelf, but not implemented yet
           case n if Utility.trimProper(n).isEmpty => //whitespace node => nothing to do 
           case _ => throw ParseError("element not allowed in document: " + m)
         }
      }
   }
   
   /**
    * @param home the mpath to use to refer to module (module.path for theories and views)
    * @param nsMap used for relative paths (base is set by readIn)
    * @param body the containing theory, view, or structure
    * @param node the node to parse 
    */
   def readInModule(home: MPath, nsMap: NamespaceMap, body: Body, node: Node)(implicit cont: StructuralElement => Unit) {
      readInModuleAux(home, body.asDocument.path, nsMap, body, node)
   }
   /** additionally keeps track of the document nesting inside the body */
   private def readInModuleAux(home: MPath, docHome: DPath, nsMap: NamespaceMap, body: Body, node: Node)(implicit cont: StructuralElement => Unit) {
      val homeTerm = OMMOD(home)
      val relDocHome = docHome.dropPrefix(home.toDPath).getOrElse {
         throw ImplementationError(s"document home must extend content home")
      } 
      val (symbolWS, md) = MetaData.parseMetaDataChild(node, nsMap)
      /* declarations must only be added through this method */
      def addDeclaration(d: Declaration) {
         d.setDocumentHome(relDocHome)
         add(d, md)
      }
      
      val name = LocalName.parse(xml.attr(node,"name"), nsMap)
      val alias = stringToList(xml.attr(node, "alias")) map {a =>
         LocalName.parse(a)
      }
      val symbol = if (symbolWS.label == "opaque") symbolWS else xml.trimOneLevel(symbolWS)
      symbol match {
         case <omdoc>{dnodes @_*}</omdoc> =>
            val name = xml.attr(symbol, "name")
            val innerDoc = new Document(docHome / name, contentAncestor = Some(body))
            add(innerDoc, md)
            dnodes.foreach {n =>
               readInModuleAux(home, innerDoc.path, nsMap, body, n)
            }
         case <opaque>{ops @_*}</opaque> =>
            val format = xml.attr(node, "format")
            val oi = controller.extman.get(classOf[OpaqueElementInterpreter], format).getOrElse {
               throw ParseError("unknown opaque format: " + format)
            }
            val oe = oi.fromNode(docHome, nsMap, ops)
            add(oe, md)
         case <constant>{comps @_*}</constant> =>
            log("constant " + name.toString + " found")
            var tp: Option[Term] = None
            var df: Option[Term] = None
            var notC: Option[NotationContainer] = None
            
            comps.map(xml.trimOneLevel) foreach {
               case <type>{t}</type> => tp match {
                  case None =>
                     tp = Some(Obj.parseTerm(t, nsMap))
                  case Some(_) =>
                     throw ParseError("multiple types in " + symbol)
               }
               case <definition>{d}</definition> => df match {
                  case None =>
                     df = Some(Obj.parseTerm(d, nsMap))
                  case Some(_) =>
                     throw ParseError("multiple definitions in " + symbol)
               }
               case comp @ (<notations>{_*}</notations>) => notC match {
                  case None =>
                     notC = Some(NotationContainer.parse(comp.child, home ? name))
                  case Some(_) =>
                     throw ParseError("multiple notation children in " + symbol)
               }
               case c => throw ParseError("illegal child in constant " + c)
            }
            val rl = xml.attr(symbol,"role") match {
               case "" => None
               case r => Some(r)
            }
            val c = Constant(homeTerm, name, alias, tp, df, rl, notC.getOrElse(NotationContainer()))
            addDeclaration(c)
         case imp @ <import>{seq @ _*}</import> =>
            log("import " + name + " found")
            val (rest, from) = ReadXML.getTermFromAttributeOrChild(imp, "from", nsMap)
            val adjustedName = if (name.length > 0) name else from match {
               case OMMOD(p) => LocalName(p)
               case _ => throw ParseError("domain of include must be atomic")
            }
            val isImplicit = parseImplicit(symbol) 
            rest.child.map(xml.trimOneLevel) match {
               case <definition>{d}</definition> :: Nil =>
                  val df = Obj.parseTerm(d, nsMap)
                  val s = DefinedStructure(homeTerm, adjustedName, from, df, isImplicit)
                  addDeclaration(s)
               case assignments =>
                  val s = DeclaredStructure(homeTerm, adjustedName, from, isImplicit)
                  addDeclaration(s)
                  assignments foreach {a => readInModule(s.path.toMPath, nsMap, s, a)}
            }
         case <theory>{body @_*}</theory> =>
            val parent = home.parent
            val tname = home.name / name
            val meta = xml.attr(symbol, "meta") match {
               case "" => None
               case mt =>
                  log("meta-theory " + mt + " found")
                  Some(Path.parseM(mt, nsMap))
            }
            val t = Theory.empty(parent, tname, meta) //definition and parameters are parsed by readIn
            addDeclaration(new NestedModule(OMMOD(home), name, t))
            body.foreach {n => 
               logGroup {
                  readIn(nsMap, t, n)
               }
            }
         case m @ <view>{_*}</view> =>
            val parent = home.parent
            val vname = home.name / name
            log("view " + name + " found")
            val (m2, from) = ReadXML.getTermFromAttributeOrChild(m, "from", nsMap)
            val (m3, to) = ReadXML.getTermFromAttributeOrChild(m2, "to", nsMap)
            val isImplicit = parseImplicit(m)
            m3.child match {
               case <definition>{d}</definition> :: Nil =>
                  val df = Obj.parseTerm(d, nsMap)
                  val v = DefinedView(parent, vname, from, to, df, isImplicit)
                  addDeclaration(new NestedModule(OMMOD(home),name,v))
               case assignments =>
                  val v = new DeclaredView(parent, name, from, to, isImplicit) // TODO add metamorph?
                  addDeclaration(new NestedModule(OMMOD(home), name, v))
                  logGroup {
                     assignments.foreach {d =>
                        readIn(nsMap, v, d)
                     }
                  }
            }
         case <ruleconstant><type>{tpN}</type></ruleconstant> =>
            log("found rule constant " + name + ", trying RuleConstantInterpreter")
            val tp = Obj.parseTerm(tpN, nsMap)
            try {
              val rc = rci(home, tp, true)
              addDeclaration(rc)
            } catch {
              case NotApplicable(msg) =>
                throw ParseError("error while reading rule " + name + ": " + msg)
              case b: BackendError =>
                throw ParseError("error while reading rule " + name + ": ").setCausedBy(b)
            }
            
         case ddN @ <derived>{body @_*}</derived> =>
            val feature = xml.attr(symbol, "feature")
            val (body2,tp) = ReadXML.getTermFromAttributeOrChild(ddN, "type", nsMap)
            val tpC = TermContainer(tp)
            val (not,decls) = body2.child match {
              case hd::tl if hd.label == "notations" => (Some(hd),tl)
              case ds => (None, ds)
            }
            val notC = not.map {case node => NotationContainer.parse(node.child, home ? name)}.getOrElse(new NotationContainer())
            val dd = new DerivedDeclaration(homeTerm, name, feature, tpC, notC)
            addDeclaration(dd)
            decls.foreach {d =>
               logGroup {
                  readInModule(home / name, nsMap, dd.module, d)
               }
            }

         // parameters and definition should be parsed much earlier; but when streaming the XML, they are found only now
         case <parameters>{parN}</parameters> =>
            val par = Context.parse(parN, nsMap)
            body match {
               case d: DeclaredTheory => d.paramC.set(par)
               case _ => throw ParseError("parameters outside declared theory")
            }
         case <definition>{dfN}</definition> =>
            val df = Obj.parseTerm(dfN, nsMap)
            body match {
               case d: DeclaredTheory => d.dfC.set(df)
               case _ => throw ParseError("definition outside declared theory")
            }
           
         case scala.xml.Comment(_) =>
         case n if Utility.trimProper(n).isEmpty => //whitespace node => nothing to do 
         case _ => throw ParseError("symbol level element expected: " + symbol)
      }
   }

   private def parseImplicit(n: Node): Boolean = {
      xml.attr(n, "implicit") match {
         case "true" => true
         case "false" | "" => xml.attr(n, "name") == ""
         case s => throw ParseError("true|false expected in implicit attribute, found " + s)
      }
   }
}

object ReadXML {
  /** parses a term using the attribute or child "component" of "n", returns the remaining node and the term */
  def getTermFromAttributeOrChild(n: Node, component: String, nsMap: NamespaceMap) : (Node, Term) = {
      val (newnode, value) = xml.getAttrOrChild(n, component)
      val thy = value match { 
         case Left(s) => OMMOD(Path.parseM(s, nsMap))
         case Right(c) => 
             val cT = xml.trimOneLevel(c)
             if (cT.child.length == 1) Obj.parseTerm(cT.child(0), nsMap)
             else throw ParseError("ill-formed theory: " + c)
      }
      (newnode, thy)
  }
  
  /** parses a theory using the attribute or child "component" of "n", returns the remaining node and the theory */
  def makeTermAttributeOrChild(t: Term, key: String): (String,Seq[Node]) = { 
     t match {
        case OMMOD(fromPath) => (fromPath.toPath, Nil)
        case _ => (null, Elem(null, key, Null, TopScope, false, t.toNode))
     }
  }
}
