package org.test.formulaTrigger

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.AggregatingStateDescriptor
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.Window
import org.scalacheck.Prop
import org.test.{NextFormula, Time}


//Trigger que evalua una formula a los datos que va recibiendo y se dispara segun el resultado de dicha formula
class FormulaTrigger[U, W <:Window](formula: NextFormula[U]) extends Trigger[U, W]{

  private val stateDesc = new AggregatingStateDescriptor("formula", new AggregateFormula[U](formula), TypeExtractor.getForClass(classOf[FormulaResult[U]]))

  @throws[Exception] override def onElement(element: U, timestamp: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = {
    val form = ctx.getPartitionedState(stateDesc)
    form.add(element)
    if (!form.get().equals(Prop.Undecided)) {
      form.clear()
      return TriggerResult.FIRE_AND_PURGE
    }
    TriggerResult.CONTINUE
  }

  override def onEventTime(time: Long, window: W, ctx: Trigger.TriggerContext) = TriggerResult.CONTINUE

  @throws[Exception] override def onProcessingTime(time: Long, window: W, ctx: Trigger.TriggerContext) = TriggerResult.CONTINUE

  @throws[Exception] override def clear(window: W, ctx: Trigger.TriggerContext): Unit = {
    ctx.getPartitionedState(stateDesc).clear()
  }

  override def canMerge = false

  @throws[Exception] override def onMerge(window: W, ctx: Trigger.OnMergeContext): Unit = {
    ctx.mergePartitionedState(stateDesc)
  }

  override def toString: String = "FormulaTrigger(" + formula + ")"




  private class FormulaResult[U](f: NextFormula[U], r: Prop.Status){
    var formula = f
    var result = r
  }

  private class AggregateFormula[U](formula: NextFormula[U]) extends AggregateFunction[U, FormulaResult[U], Prop.Status] {

    def createAccumulator(): FormulaResult[U] = {
      new FormulaResult(formula, Prop.Undecided)
    }


    def merge(a: FormulaResult[U], b: FormulaResult[U]): FormulaResult[U] = {
      new FormulaResult(a.formula, a.result)
    }


    def add(data: U, wr: FormulaResult[U]) = {
      if (wr.formula.result.isEmpty) {
        wr.formula = wr.formula.consume(Time(1))(data)
      }

    }

    def getResult(wr: FormulaResult[U]): Prop.Status = {
      wr.result = wr.formula.result.getOrElse(Prop.Undecided)
      wr.result
    }
  }

}
