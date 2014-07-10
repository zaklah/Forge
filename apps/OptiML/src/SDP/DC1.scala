import optiml.compiler._
import optiml.library._
import optiml.shared._

object DC1Compiler extends OptiMLApplicationCompiler with DC1
object DC1Interpreter extends OptiMLApplicationInterpreter with DC1

trait DC1 extends OptiMLApplication {
  def printUsage = {
    println("Usage: DC1 <input matrix file> <y0> <n> <alpha> <kmax>")
    exit(-1)
  }

  def main() = {
    if (args.length < 3) printUsage

    val n = args(2).toInt
    val alpha = args(3).toDouble
    val kmax = args(4).toInt
    val a = readSparseMatrixCOO(args(0), n, n)
    val y0 = readVector(args(1)).t

    println("n: " + n)
    println("alpha: " + alpha)
    println("kmax:  " + kmax)

    tic()

    implicit def diffPDIP(t1: Rep[Tup2[DenseVector[Double],Int]],
                          t2: Rep[Tup2[DenseVector[Double],Int]]) = {
      dist(t1._1, t2._1)
    }

    val soln = untilconverged(pack(y0, 0), maxIter = kmax) { cur =>
      val (y, k) = unpack(cur)

      val ak = alpha / (k + 1)

      (1.0 - ak) * y + ak * a * y / (y *:* y)
    }
    val (y_soln, k_soln) = unpack(soln)

    toc(y_soln)
    println("y:")
    y_soln.pprint
  }
}