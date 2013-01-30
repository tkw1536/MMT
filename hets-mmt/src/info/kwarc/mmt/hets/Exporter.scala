package info.kwarc.mmt.hets

import info.kwarc.mmt.api._
import modules._
import patterns._
import utils._
import objects._
import MyList._

/**
 * exports an OMDoc theory (DeclaredTheory in controller lib) 
 * to a pseudo-XML file
 */
class Exporter {
	def insToNode(out : File, ins : Instance) : scala.xml.Node = {
		
	    // get substituting terms
		val args = ins.matches.components map { x => x match {
				case Sub(vName : LocalName,subT : Term) => toNode(subT)
			}
		}
		
		// wrap instance declaration
	  	<instance pattern={ins.pattern.last} name={ins.name.toPath}>
			{  args	}
		</instance>		  
	}
	
	def toNode(t : Term) : scala.xml.Node = { 
	  t match {
			case OMV(n) => <var name={n.last.toPath}/>	
			case OMS(s) => <app name={s.last}/>
			case OMA(f,args) => <app name={f.toMPath.last}> 
									{args map(x => toNode(x))} 
								</app>
			case OMBIND(binder,ctx,bd) => <bind> </bind> 
			case OMBINDC(binder,ctx,cnd,bd) => <bind> </bind>  
	  } 
	  
	}
	
	def compile(outDir : File, parent : DeclaredTheory) {	  
	  val outName = parent.name.toString + ".xml"
	  val instances = parent.components.mapPartial { x => x match {
	    case x : Instance => Some(x)
	    case _ => None
	  	}	     
	  }
	  val out = File(outDir.toJava.getPath() + "/" + outName)
	  if (!outDir.toJava.exists()) {
	    println("creating dirs " + outDir.toJava.getPath())
	    outDir.toJava.mkdirs()
	  }
	  val fw = new java.io.FileWriter(out.toJava.getPath())
	  val ins = parent.getDeclarations.mapPartial {
      	case p: patterns.Instance => Some(p)
      	case _ => None
	  }
	  val nodes = ins map { 
	     insToNode(out,_)
	  }
	  println("writing to file: " + out.toJava.getPath())
	  println((nodes map {x => x.toString()}).mkString)
	  val pp = new scala.xml.PrettyPrinter(100,2)
	  val docNode = pp.format(nodes.first)
	  fw.write((nodes map {x => x.toString}).mkString("\n"))
	  fw.close()
	}
}

object ExporterTest {  
  def main(outDir : String, test : DeclaredTheory) = {
    new Exporter().compile(File(outDir),test)
  }  
}