package acai_lab11.Lab1
import scala.io.Source
import chisel3.iotesters.{PeekPokeTester,Driver}
import scala.language.implicitConversions

class cacheMemTest(dut:cacheMem) extends PeekPokeTester(dut){


  val fileName = "src/test/tb/lab11-1.tb"
  val cnt = 0
  val fileSource = Source.fromFile(fileName)
  for(lines<-fileSource.getLines()) {
    val line = lines.split(' ')
    poke(dut.io.addr,line(0).toInt)
    //poke(dut.io.read,line(1).toInt)
    poke(dut.io.dataIn, line(2).toInt)
    poke(dut.io.cpuDRAM, line(3).toInt)
    step(1)
    if (line(1).toInt==1){ // Read

      println("----------- Read -----------")
      //println(" hit : "+peek(dut.io.hit).toString + " dirty : "+peek(dut.io.dirty).toString + "dataOut : "+peek(dut.io.dataOut).toString)
      println(" hit : "+line(4) + " dirty : " + line(5) + " dataOut : "+ line(6))
    }
    else{
      println("----------- Write -----------")
      println(" addr : "+line(0) + " dataIn : "+ line(2) + " cpuDRAM : " + line(3))
    }
  }
  step(3)
}

object cacheMemTest extends App{
  Driver.execute(args,()=> new cacheMem(1024)){
    c => new cacheMemTest(c)
  }
}
