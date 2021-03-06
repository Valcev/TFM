package org.test.streamTest

import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.windowing.time.Time
import org.examples.pollution.{AccumulateWindows, Count}
import org.specs2.{ScalaCheck, Specification}


//Adaptacion de la aplicacion Pollution para que devuelva el stream resultante
object PollutionForTestStream {

  def demoPol(env: StreamExecutionEnvironment): DataStream[(Int, Int)] = {

    //Utiliza processing time
    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime)

    //Obtiene los datos del puerto 9000
    //Un programa Scala inserta valores aleatorios en este puerto
    val text = env.socketTextStream("localhost", 9000, '\n')

    //Transforma los datos leidos a tuplas (polucion, sensor)
    val mapped = text.map { x => (x.split(",")(0).toInt, x.split(",")(1).toInt)}

    //Separa los streams por sensor
    val keyValue = mapped.keyBy(1)

    //Recoge los datos en ventanas de 5 segundos
    val tumblingWindow  = keyValue.timeWindow(Time.seconds(5))

    //Maximo nivel de polucion
    val maxPollution = 20

    //Numero de ventanas a esperar para cambiar el estado de la alarma
    val windows = 3

    //Recogemos los datos en ventanas con capacidad para 3 datos, y contamos el numero de datos que superan la polucion
    //maxima en cada ventana
    //De nuevo separa los flujos por sensor
    val countWindowVal = tumblingWindow.aggregate(new Count(maxPollution)).keyBy(1).countWindow(windows,1)

    //Acumulamos los valores para poder activar y desactivar la alarma en los momentos adecuados,
    //ya que para activarse debe esperar a que se supere el maximo de contaminacion durante
    //'n' ventanas seguidas.
    //Equivalente para desactivarse.
    val accumulateWindow = countWindowVal.aggregate(new AccumulateWindows(windows))

    return accumulateWindow
  }
}
