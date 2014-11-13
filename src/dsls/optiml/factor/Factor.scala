package ppl.dsl.forge
package dsls.optiml
package factor
 
import core.{ForgeApplication,ForgeApplicationRunner}
 
trait FactorOps extends TableFactorOps with FunctionFactorOps {
  this: OptiMLDSL =>
 
  def importAllFactorOps() {
    importFactorVariableOps()
    importVariableFactorOps()
    importTableFactorOps()
    importFunctionFactorOps()
    importFactorOps()
  }
 
  def importFactorVariableOps() {
    val DenseVector = lookupTpe("DenseVector")
 
    val FVariable = tpe("FactorVariable")
    data(FVariable, ("_id", MInt), ("_isPositive", MBoolean), ("_position", MInt))
 
    static (FVariable) ("apply", Nil, (("id", MInt), ("isPositive", MBoolean), ("position", MInt)) :: FVariable) implements allocates(FVariable, ${$0}, ${$1}, ${$2})
 
    val FVariableOps = withTpe(FVariable)
    FVariableOps {
      infix ("id") (Nil :: MInt) implements getter(0, "_id")
      infix ("isPositive") (Nil :: MBoolean) implements getter(0, "_isPositive")
 
      infix ("position") (Nil :: MInt) implements getter(0, "_position")
    }
  }
 
  def importVariableFactorOps() {
    val DenseVector = lookupTpe("DenseVector")
    val FactorVariable = lookupTpe("FactorVariable")
    val Tup2 = lookupTpe("Tup2")
 
    val VFactor = tpe("VariableFactor")
    data(VFactor, ("_id", MInt), ("_funcId", MInt), ("_nVariables", MInt), ("_iStart", MInt), ("_weightId", MInt))
 
    static (VFactor) ("apply", Nil, (("id", MInt), ("funcId", MInt), ("nVariables", MInt), ("iStart", MInt), ("weightId", MInt)) :: VFactor) implements allocates(VFactor, ${$0}, ${$1}, ${$2}, ${$3}, ${$4})
 
    // compiler (VFactor) ("getValue", Nil, (("isPositive", MBoolean), ("value", MDouble)) :: MDouble) implements composite ${
    //   if (isPositive) 
    //     value
    //   else 
    //     1.0 - value
    // }
 
    // compiler (VFactor) ("trans", Nil, ("isPositive", MBoolean) :: MDouble) implements composite ${
    //   if (isPositive) 
    //     1.0
    //   else 
    //     0.0
    // }
 
    compiler (VFactor) ("variableSample", Nil, (("isPositive", MBoolean), ("sampleValue", MDouble)) :: MDouble) implements composite ${
      if (isPositive) sampleValue
      else 1.0 - sampleValue
    }
 
    compiler (VFactor) ("getValue", Nil, (("variable", FactorVariable), ("variableId", MInt), ("sampleValue", MDouble), ("originValue", MDouble)) :: MDouble) implements composite ${
      if (variable.id == variableId) {
        if (variable.isPositive) sampleValue
        else 1.0 - sampleValue
      }
      else {
        if (variable.isPositive) originValue
        else 1.0 - originValue
      }
      
    }
 
    compiler (VFactor) ("or_factor", Nil, (("factor", VFactor), ("vals", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)) :: MDouble) implements composite ${
      val nVariables = factor.nVariables
      val iStart = factor.iStart
      if (nVariables == 0) 1.0
      else {
        var i = 0
        var flag = true
        while (i < nVariables && flag) {
          val variable = factorsToVariables(iStart + i)
          flag = (getValue(variable, variableId, sampleValue, vals(variable.id)) == 0.0)
          i += 1
        }
        if (flag) {
          0.0
        }
        else {
          1.0
        }
      }
    }
 
    compiler (VFactor) ("and_factor", Nil, (("factor", VFactor), ("vals", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)) :: MDouble) implements composite ${
      val nVariables = factor.nVariables
      val iStart = factor.iStart
      if (nVariables == 0) 1.0
      else {
        var i = 0
        var flag = true
        while (i < nVariables && flag) {
          val variable = factorsToVariables(iStart + i)
          flag = (getValue(variable, variableId, sampleValue, vals(variable.id)) == 1.0)
          i += 1
        }
        if (flag) {
          1.0
        }
        else {
          0.0
        }
      }
    }
 
    compiler (VFactor) ("imply_factor", Nil, (("factor", VFactor), ("vals", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)) :: MDouble) implements composite ${
      val nVariables = factor.nVariables
      val iStart = factor.iStart
      if (nVariables == 1) {
        val variable = factorsToVariables(iStart)
        if (variable.id == variableId) {
          variableSample(variable.isPositive, sampleValue)
        }
        else fatal("cannot evaluate imply: variableId not match")
      }
      else {
        var i = 0
        var flag = true
        while (i < nVariables - 1 && flag) {
          val variable = factorsToVariables(iStart + i)
          flag = (getValue(variable, variableId, sampleValue, vals(variable.id)) == 1.0)
          i += 1
        }
        if (flag) {
          val variable = factorsToVariables(iStart + nVariables - 1)
          getValue(variable, variableId, sampleValue, vals(variable.id))
        }
        else {
          1.0
        }
      }
    }
 
    compiler (VFactor) ("equal_factor", Nil, (("factor", VFactor), ("vals", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)) :: MDouble) implements composite ${
      val nVariables = factor.nVariables
      val iStart = factor.iStart
      if (nVariables == 2) {
        val var1 = factorsToVariables(iStart)
        val var2 = factorsToVariables(iStart + 1)
        val value1 = getValue(var1, variableId, sampleValue, vals(var1.id))
        val value2 = getValue(var2, variableId, sampleValue, vals(var2.id))
        if (value1 == value2) 1.0
        else 0.0
      }
      else {
        fatal("cannot evaluate equality between more than 2 variables")
      }
    }
 
    compiler (VFactor) ("istrue_factor", Nil, (("factor", VFactor), ("vals", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)) :: MDouble) implements composite ${
      val nVariables = factor.nVariables
      val iStart = factor.iStart
      if (nVariables == 1) {
        val variable = factorsToVariables(iStart)
        if (variable.id == variableId) {
          variableSample(variable.isPositive, sampleValue)
        }
        else fatal("cannot evaluate isTrue: variableId not match")
      }
      else fatal("cannot evaluate isTrue for more than 1 variable")
    }
 
 
    compiler (VFactor) ("evaluate_factor", Nil, MethodSignature(List(("factor", VFactor), ("vals", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)), MDouble)) implements composite ${
      // if the conditional is known at staging time, we can inline the exact function
      // and as a consequence, the back-end 'funcId' field in the factor should be DFE'd
      val funcId = factor.funcId
      if (funcId == 0) {
        imply_factor(factor, vals, factorsToVariables, variableId, sampleValue)
      }
      else if (funcId == 1) {
        or_factor(factor, vals, factorsToVariables, variableId, sampleValue)
      }
      else if (funcId == 2) {
        and_factor(factor, vals, factorsToVariables, variableId, sampleValue)
      }
      else if (funcId == 3) {
        equal_factor(factor, vals, factorsToVariables, variableId, sampleValue)
      }
      else if (funcId == 4) {
        istrue_factor(factor, vals, factorsToVariables, variableId, sampleValue)
      }
      else {
        fatal("no factor func with id " + funcId + " found")
      }
    }
 
    
    val VFactorOps = withTpe(VFactor)
    VFactorOps {
      infix ("id") (Nil :: MInt) implements getter(0, "_id")
      infix ("funcId") (Nil :: MInt) implements getter(0, "_funcId")
      infix ("nVariables") (Nil :: MInt) implements getter(0, "_nVariables")
      infix ("iStart") (Nil :: MInt) implements getter(0, "_iStart")
      infix ("weightId") (Nil :: MInt) implements getter(0, "_weightId")
      infix ("evaluate") ((("variableValues", DenseVector(MDouble)), ("factorsToVariables", DenseVector(FactorVariable)), ("variableId", MInt), ("sampleValue", MDouble)) :: MDouble) implements composite ${ evaluate_factor($self, variableValues, factorsToVariables, variableId, sampleValue) }
    }
  }
 
  // -- Factor type-class
  // the main issue with this organization is that we cannot store multiple factor types in a single graph
  // unless we store a separate map per factor type. we should look into an interface / struct inheritance model.
 
  object TFactor extends TypeClassSignature {
    def name = "Factor"
    def prefix = "_fact"
    def wrapper = Some("facttype")
  }
 
  def importFactorOps() {
    val T = tpePar("T")
    val DenseVector = lookupTpe("DenseVector")
    val FVariable = lookupTpe("FactorVariable")
 
    val Factor = tpeClass("Factor", TFactor, T)
 
    // Factor interface
    infix (Factor) ("vars", T, T :: DenseVector(FVariable))
    infix (Factor) ("valueOfAssignment", T, (T, DenseVector(MDouble)) :: MDouble)
    infix (Factor) ("weightId", T, T :: MDouble)
 
    // TableFactor impl
    //val TableFactor = lookupTpe("TableFactor")
    //val FactorTableFactor = tpeClassInst("FactorTableFactor", Nil, Factor(TableFactor))
 
    //infix (FactorTableFactor) ("vars", Nil, TableFactor :: DenseVector(FVariable)) implements composite ${ $0.vars }
    //infix (FactorTableFactor) ("valueOfAssignment", Nil, (TableFactor, DenseVector(MDouble)) :: MDouble) implements composite ${
      // this is slow, since we need to figure out the logical assignment from the value assignment
      // is there a better way?
      //val assignment = $0.vars.map(_.domain).zip($1) { (domain, value) => domain.find(_ == value).first }
      //val index = assignmentToIndex(assignment)
      //$0.vals.apply(index)
    //}
    //infix (FactorTableFactor) ("weightId", Nil, TableFactor :: MDouble) implements composite ${ unit(0.0) } // TODO
 
    // FunctionFactor impl
    val FunctionFactor = lookupTpe("FunctionFactor")
    val FactorFunctionFactor = tpeClassInst("FactorFunctionFactor", Nil, Factor(FunctionFactor))
 
    infix (FactorFunctionFactor) ("vars", Nil, FunctionFactor :: DenseVector(FVariable)) implements composite ${ $0.vars }
    infix (FactorFunctionFactor) ("valueOfAssignment", Nil, (FunctionFactor, DenseVector(MDouble)) :: MDouble) implements composite ${
      $0.evaluate($1)
    }
    infix (FactorFunctionFactor) ("weightId", Nil, FunctionFactor :: MDouble) implements composite ${ $0.weightId }
  }
}