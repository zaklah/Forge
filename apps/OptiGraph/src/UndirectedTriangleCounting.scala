import optigraph.compiler._
import optigraph.library._
import optigraph.shared._

// This object lets us run the Delite version of the code
object UndirectedTriangleCountingCompiler extends OptiGraphApplicationCompiler with UndirectedTriangleCounting

// This object lets us run the Scala library version of the code
object UndirectedTriangleCountingInterpreter extends OptiGraphApplicationInterpreter with UndirectedTriangleCounting

trait UndirectedTriangleCounting extends OptiGraphApplication {
  def main() = {
    println("UndirectedTriangleCounting")

    if (args.length < 1) printUsage

    tic("input loading")
    val edgeList = loadUndirectedEdgeList(args(0))
    toc("input loading",edgeList)

    tic("creating graph",edgeList)
    val g = csrUndirectedGraphFromEdgeList(edgeList)
    toc("creating graph",g)
    

    println("Directed: " + g.isDirected)
    println("Number of Nodes: " + g.numNodes)
    println("Number of Edges: " + g.numEdges)

    println("performing Traingle Counting: " + g)
    tic("Triangle Counting",g)
    
    val t = g.sumOverNodes{ n =>
      val nbrs = g.neighbors(n)
      sumOverCollection(nbrs){ nbr =>
        if(nbr > n.id) nbrs.intersectInRange(g.neighbors(nbr),n.id)
        else 0l
      }{e => true}
    }

    toc("Triangle Counting",t)
    println("Number of triangles " + t)
  }
  def printUsage = {
    println("Usage: UndirectedTriangleCounting <path to input edge list file>")
    exit(-1)
  }
}
