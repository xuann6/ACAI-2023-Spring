package acai_lab11.Hw1

import chisel3._
import chisel3.util._
import acai_lab11._

class cacheMem(val table_height: Int, val way: Int) extends Module {
  val io = IO(new Bundle {
    // from cache Controller
    val addr = Input(UInt(32.W)) // read-write data
    val wen = Input(Bool()) // Cache Write Enable
    val dataIn = Input(UInt(32.W))
    val Size = Input(UInt(4.W)) 
    val chosenWay = Input(UInt(2.W))

    val cpuDRAM = Input(Bool())
    val dataOut = Output(UInt(32.W))
    val tagOut = Output(Vec(way,UInt(20.W)))
    val dm_read = Output(UInt(256.W))//write back
    val dm_write = Input(UInt(256.W))//write from dm
  })


}
