/*//////////////////////////////////////////////////////////////
Author: Christopher R. Aberger

Description: Parallel input methods.  Output is currently done
serially.
*///////////////////////////////////////////////////////////////
package ppl.dsl.forge
package dsls
package optigraph

import core.{ForgeApplication,ForgeApplicationRunner}

trait IOGraphOps {
  this: OptiGraphDSL =>
  def importIOGraphOps() {
    val IO = grp("GraphIO")

    val T = tpePar("T")
    val K = tpePar("K")
    val V = tpePar("V")
    val Tuple2 = lookupTpe("Tup2")

    val NodeIdView = lookupTpe("NodeIdView")
    val NodeCollection = lookupTpe("NodeCollection")
    val NodeData = lookupTpe("NodeData")
    val GraphBitSet = lookupTpe("GraphBitSet")
    val CSRDirectedGraph = lookupTpe("CSRDirectedGraph")
    val CSRUndirectedGraph = lookupTpe("CSRUndirectedGraph")
    val HABUndirectedGraph = lookupTpe("HABUndirectedGraph")

    direct (IO) ("writeResults", T, (("path",MString),("ids",MArray(MInt)),("data",NodeData(T))) :: MUnit, TNumeric(T), effect = simple) implements single ${
      writeGraphData($path,ids,data.getRawArray,$data.length)
    }
    compiler (IO) ("writeGraphData", T, (("path",MString),("ids",MArray(MInt)),("data",MArray(T)),("length",MInt)) :: MUnit, TNumeric(T), effect = simple) implements codegen($cala, ${
      val xfs = new java.io.BufferedWriter(new java.io.FileWriter($path))
      xfs.write("#node id\\tdata\\n")
      for (i <- 0 until $length) {
        xfs.write($ids(i).toString + "\\t")
        xfs.write($data(i).toString + "\\n")
      }
      xfs.close()
    })

    direct (IO) ("csrPrunedUndirectedGraphFromEdgeList", Nil, MString :: CSRUndirectedGraph) implements composite ${
      val input_edges =
        ForgeFileReader.readLinesFlattened($0)({line =>
          val fields = line.fsplit(" ")
          array_fromfunction(((array_length(fields)-1)*2),{n =>
            if(n==0) pack(fields(0).toInt,fields(1).toInt)
            else pack(fields(1).toInt,fields(0).toInt)
          })
        })

      //contains either duplicate edges or not
      val edge_data = NodeData[Tup2[Int,Int]](input_edges).distinct
      println("Edges: " + edge_data.length)
      val src_groups = edge_data.groupBy(e => e._1, e => e._2)

      //sort by degree, helps with skew for buckets of nodes
      val src_id_degrees = edge_data.groupBy(e => array_buffer_length(fhashmap_get(src_groups,e._1)), e => e._1)
      val distinct_ids = (NodeData(fhashmap_keys(src_id_degrees)).sort).flatMap{e => NodeData(fhashmap_get(src_id_degrees,e)).distinct}

      val numNodes = distinct_ids.length
      val idView = NodeData(array_fromfunction(numNodes,{n => n}))
      val idHashMap = idView.groupByReduce[Int,Int](n => distinct_ids(n), n => n, (a,b) => a)

      val filtered_nbrs = distinct_ids.map(e => NodeData(src_groups(e)).filter(a => 
        fhashmap_get(idHashMap,a)>fhashmap_get(idHashMap,e),n =>fhashmap_get(idHashMap,n)).sort)

      val src_edge_array = idView.flatMap{e => filtered_nbrs(e)}
      val serial_out = assignUndirectedIndicies(numNodes,filtered_nbrs)

      println("finished file I/O. Edges: " + src_edge_array.length)
      CSRUndirectedGraph(numNodes,distinct_ids.getRawArray,serial_out,src_edge_array.getRawArray)
    }
    direct (IO) ("assignUndirectedIndicies", Nil, MethodSignature(List(("numNodes",MInt),("src_groups",NodeData(NodeData(MInt)))),MArray(MInt))) implements single ${
      val src_node_array = NodeData[Int](numNodes)
      var i = 0
      var src_array_index = 0
      while(i < numNodes-1){
        src_node_array(i+1) = src_groups(i).length + src_node_array(i)
        i += 1
      }
      src_node_array.getRawArray
    }

    direct (IO) ("csrDirectedGraphFromEdgeList", Nil, MString :: CSRDirectedGraph) implements composite ${
      val input_edges = ForgeFileReader.readLines($0)({line =>
          val fields = line.fsplit(" ")
          pack(fields(0).toInt,fields(1).toInt) 
      })

      //contains the input tuples
      val edge_data = NodeData[Tup2[Int,Int]](input_edges)

      val src_groups = edge_data.groupBy(e => e._1, e => e._2)
      val dst_groups = edge_data.groupBy(e => e._2, e => e._1)

      //go over src_ids and dst_ids map to degree
      //go over distinct add src_ids and dst_ids degree to hashmap
      //filter hashmap keys to those greater than sqrroot(n)
      val src_ids = NodeData(fhashmap_keys(src_groups))
      val dst_ids = NodeData(fhashmap_keys(dst_groups))
      val concat = NodeData(array_fromfunction(2,{n=>n})).flatMap[Int](e => if(e==0) src_ids else dst_ids)
      val distinct_ids = NodeData(fhashmap_keys(concat.groupBy[Int,Int](e => e, e => e)))

      //set up the ID hash map
      val numNodes = distinct_ids.length
      val idView = NodeData(array_fromfunction(numNodes,{n => n}))
      val idHashMap = idView.groupByReduce[Int,Int](n => distinct_ids(n), n => n, (a,b) => a)

      //must filter down the ids we want to flat map to just the distinct src ids we want
      //gets tricky because order of flatmap must match our internal id order other wise
      //the edge array gets screwed up
      val src_ids_ordered = NodeData(array_sort(array_map[Int,Int](src_ids.getRawArray,e => fhashmap_get(idHashMap,e))))
      val src_edge_array = src_ids_ordered.flatMap{e => NodeData(src_groups(distinct_ids(e))).map(n => fhashmap_get(idHashMap,n))}

      val dst_ids_ordered = NodeData(array_sort(array_map[Int,Int](dst_ids.getRawArray,e => fhashmap_get(idHashMap,e))))
      val dst_edge_array = dst_ids_ordered.flatMap(e => NodeData(dst_groups(distinct_ids(e))).map{n => fhashmap_get(idHashMap,n)})

      val edge_arrays = assignIndiciesSerialDirected(numNodes,distinct_ids,src_groups,src_ids_ordered,dst_groups,dst_ids_ordered)
      
      println("finished file I/O")
      CSRDirectedGraph(numNodes,distinct_ids.getRawArray,edge_arrays._1.getRawArray,src_edge_array.getRawArray,edge_arrays._2.getRawArray,dst_edge_array.getRawArray)
    }
    direct (IO) ("assignIndiciesSerialDirected", Nil, MethodSignature(List(("numNodes",MInt),("distinct_ids",NodeData(MInt)),("src_groups",MHashMap(MInt,MArrayBuffer(MInt))),("src_ids_ordered",NodeData(MInt)),("dst_groups",MHashMap(MInt,MArrayBuffer(MInt))),("dst_ids_ordered",NodeData(MInt))), Tuple2(NodeData(MInt),NodeData(MInt)) )) implements single ${
      val src_node_array = NodeData[Int](numNodes)
      val dst_node_array = NodeData[Int](numNodes)

      var i = 0
      var src_array_index = 0
      var dst_array_index = 0
      while(i < numNodes-1){
        if(src_ids_ordered(src_array_index)==i){
          src_node_array(i+1) = array_buffer_length(fhashmap_get(src_groups,distinct_ids(i))) + src_node_array(i)
          if((src_array_index+1) < src_ids_ordered.length) src_array_index += 1
        }
        else src_node_array(i+1) = src_node_array(i)
        
        if(dst_ids_ordered(dst_array_index)==i){
          dst_node_array(i+1) = array_buffer_length(fhashmap_get(dst_groups,distinct_ids(i))) + dst_node_array(i)
          if((dst_array_index+1) < dst_ids_ordered.length) dst_array_index += 1
        }
        else dst_node_array(i+1) = dst_node_array(i)
        
        i += 1
      }
      pack(src_node_array,dst_node_array)
    }
    direct (IO) ("habPrunedUndirectedGraphFromEdgeList", Nil, MString :: HABUndirectedGraph) implements composite ${
      val input_edges =
        ForgeFileReader.readLinesFlattened($0)({line =>
          val fields = line.fsplit(" ")
          array_fromfunction(((array_length(fields)-1)*2),{n =>
            if(n==0) pack(fields(0).toInt,fields(1).toInt)
            else pack(fields(1).toInt,fields(0).toInt)
          })
        })

      //contains either duplicate edges or not
      val edge_data = NodeData[Tup2[Int,Int]](input_edges).distinct
      println("Edges: " + edge_data.length)
      val src_groups = edge_data.groupBy(e => e._1, e => e._2)

      //sort by degree, helps with skew for buckets of nodes
      val src_id_degrees = edge_data.groupBy(e => array_buffer_length(fhashmap_get(src_groups,e._1)), e => e._1)
      val distinct_ids = (NodeData(fhashmap_keys(src_id_degrees)).sort).flatMap{e => NodeData(fhashmap_get(src_id_degrees,e)).distinct}

      val numNodes = distinct_ids.length
      val idView = NodeData(array_fromfunction(numNodes,{n => n}))
      val idHashMap = idView.groupByReduce[Int,Int](n => distinct_ids(n), n => n, (a,b) => a)

      val filtered_nbrs = distinct_ids.map(e => NodeData(src_groups(e)).filter(a => 
        fhashmap_get(idHashMap,a)>fhashmap_get(idHashMap,e),n =>fhashmap_get(idHashMap,n)).sort)

      val underForHash = 16
      val nodes = idView.map{e => 
        if( filtered_nbrs(e).length*32 >= numNodes ) NodeCollection(GraphBitSet(filtered_nbrs(e).getRawArray))
        else if( filtered_nbrs(e).length < underForHash ) NodeCollection(HashSet(filtered_nbrs(e).getRawArray))
        else NodeCollection(filtered_nbrs(e))
      }
      val numEdges = filtered_nbrs.mapreduce[Int]( e => filtered_nbrs.length, (a,b) => a+b, e => true)
      
      val numHash = filtered_nbrs.mapreduce[Int]( {e => 
        if(e.length*32 < numNodes && e.length < underForHash) 1
        else 0
      }, (a,b) => a+b, e => true)
      val numArray = filtered_nbrs.mapreduce[Int]( {e => 
        if(e.length*32 < numNodes && e.length >= underForHash) 1
        else 0
      }, (a,b) => a+b, e => true)   
      val numBitSet = filtered_nbrs.mapreduce[Int]( {e => 
        if(e.length*32 >= numNodes) 1
        else 0
      }, (a,b) => a+b, e => true)

      println("numHash: " + numHash + " numArray: " + numArray + " numBitSet: " + numBitSet)
      HABUndirectedGraph(numNodes,numEdges,distinct_ids.getRawArray,numHash,numArray,numBitSet,nodes.getRawArray)
    }    
  }
}
