package acai_lab11.AXI

import chisel3._
import chisel3.util._

class readOut(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val readAddr = Decoupled(new AXIAddress(addrWidth)) // output address(from read bus) to slave
  val readData = Flipped(Decoupled(new AXIReadData(dataWidth))) // input read data from slave
}
class readIn(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val readAddr = Flipped(Decoupled(new AXIAddress(addrWidth))) // input address from readbus
  val readData = Decoupled(new AXIReadData(dataWidth)) // output read data(from slave) to readbus
}

class AXISlaveReadMux(val nMasters: Int, val addrWidth: Int, val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val out = new readOut(addrWidth, dataWidth)
    val ins = Vec(nMasters, new readIn(addrWidth, dataWidth))
  })

  val arbiter = Module(new RRArbiter(new AXIAddress(addrWidth), nMasters))
  val chosen_reg = RegNext(arbiter.io.chosen.asUInt)
  for (i <- 0 until nMasters) {
    arbiter.io.in(i) <> io.ins(i).readAddr
  }

  // arbiter.io.in <> io.ins.readAddr
  io.out.readAddr <> arbiter.io.out
  for (i <- 0 until nMasters) {
    io.ins(i).readData.bits.data := io.out.readData.bits.data
    io.ins(i).readData.valid := false.B
    io.ins(i).readData.bits.resp := 0.U
    io.ins(i).readData.bits.last := false.B
  }
  io.ins(chosen_reg).readData <> io.out.readData
}
